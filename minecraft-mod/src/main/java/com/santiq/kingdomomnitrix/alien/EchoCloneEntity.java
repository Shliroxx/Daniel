package com.santiq.kingdomomnitrix.alien;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Echo-Echo-Klon: kleiner Sonorosianer mit dem AE-Modell. Folgt seinem Echo Echo, zieht die Aufmerksamkeit von
 * Monstern auf sich und schreit Gegner in kurzer Reichweite an. Lebt nur, solange sein Echo Echo verwandelt und online
 * ist, und wird nie gespeichert (kein Klon bleibt in der Welt haengen).
 */
public class EchoCloneEntity extends PathAwareEntity implements GeoEntity {
	/** Schrei-Haken: (Klon, Ziel) → Schaden/Resonanz rechnet die Faehigkeitsklasse (Echo Echo) */
	private static BiConsumer<EchoCloneEntity, LivingEntity> screamHandler = (clone, target) -> {
	};
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final TrackedData<Integer> LIFE = DataTracker.registerData(EchoCloneEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final net.minecraft.util.Identifier ECHO_ECHO = com.santiq.kingdomomnitrix.KingdomOmnitrix.id("echo_echo");
	private static final int SCREAM_INTERVAL = 30;
	private static final double SCREAM_RANGE = 5.0;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	@Nullable
	private UUID owner;
	private int screamCooldown;

	public EchoCloneEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 6.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	/** Schrei-Logik setzen (Echo-Echo-Faehigkeiten); Standard: nichts. */
	public static void onScream(BiConsumer<EchoCloneEntity, LivingEntity> handler) {
		screamHandler = handler;
	}

	public void setOwner(ServerPlayerEntity player, int lifeTicks) {
		owner = player.getUuid();
		dataTracker.set(LIFE, lifeTicks);
	}

	@Nullable
	public UUID ownerId() {
		return owner;
	}

	@Nullable
	public ServerPlayerEntity ownerPlayer() {
		return owner != null && getWorld() instanceof ServerWorld world ? world.getServer().getPlayerManager().getPlayer(owner) : null;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(LIFE, 600);
	}

	/** Klone werden nie gespeichert. */
	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	protected void initGoals() {
		// Bewegung steuert tick() direkt (Folgen/Angreifen), keine Vanilla-Ziele
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		// der eigene Echo Echo und dessen Faehigkeiten treffen den Klon nicht
		if (source.getAttacker() != null && source.getAttacker().getUuid().equals(owner)) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		int life = dataTracker.get(LIFE) - 1;
		dataTracker.set(LIFE, life);
		ServerPlayerEntity player = ownerPlayer();
		if (life <= 0 || player == null || !player.isAlive() || player.getWorld() != world || distanceTo(player) > 48.0f
				|| TransformationManager.get(player).activeAlien().filter(ECHO_ECHO::equals).isEmpty()) {
			vanish(world);
			return;
		}
		if (screamCooldown > 0) {
			screamCooldown--;
		}
		Optional<LivingEntity> target = target(world, player);
		if (target.isPresent()) {
			LivingEntity t = target.get();
			getLookControl().lookAt(t, 30.0f, 30.0f);
			if (distanceTo(t) > SCREAM_RANGE - 1.0) {
				getNavigation().startMovingTo(t, 1.2);
			} else {
				getNavigation().stop();
			}
			if (screamCooldown <= 0 && distanceTo(t) <= SCREAM_RANGE) {
				screamCooldown = SCREAM_INTERVAL;
				screamHandler.accept(this, t);
			}
		} else if (distanceTo(player) > 4.0f) {
			getNavigation().startMovingTo(player, 1.1);
		} else {
			getNavigation().stop();
			getLookControl().lookAt(player, 30.0f, 30.0f);
		}
	}

	/** Naechstes Monster um den Klon, das den Echo Echo oder Klone angreift oder einfach feindlich ist. */
	private Optional<LivingEntity> target(ServerWorld world, ServerPlayerEntity player) {
		return world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(12.0),
						e -> e.isAlive() && e != player && !(e instanceof EchoCloneEntity) && e instanceof Monster
								&& com.santiq.kingdomomnitrix.party.PartyRules.canHarm(player, e))
				.stream().min(Comparator.comparingDouble(this::squaredDistanceTo));
	}

	/** Verklingen: kurzer Schallpuff, dann weg. */
	public void vanish(ServerWorld world) {
		world.spawnParticles(ParticleTypes.NOTE, getX(), getBodyY(0.8), getZ(), 4, 0.2, 0.2, 0.2, 0.5);
		world.spawnParticles(ParticleTypes.POOF, getX(), getBodyY(0.5), getZ(), 6, 0.15, 0.2, 0.15, 0.02);
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.5f, 1.8f);
		discard();
	}

	public int life() {
		return dataTracker.get(LIFE);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(IDLE)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	protected void dropLoot(DamageSource source, boolean causedByPlayer) {
		// Klone hinterlassen nichts
	}

	@Override
	protected int getXpToDrop() {
		return 0;
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.NOTE, getX(), getBodyY(0.8), getZ(), 8, 0.3, 0.3, 0.3, 0.5);
		}
	}

	/** Klon in Sichtweite eines Mobs: Monster, die den Echo Echo jagen, wechseln auf den Klon (Koeder). */
	public void taunt(ServerWorld world, ServerPlayerEntity player, double radius) {
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, getBoundingBox().expand(radius), m -> m.getTarget() == player)) {
			if (world.random.nextBoolean()) {
				mob.setTarget(this);
			}
		}
	}
}
