package com.santiq.kingdomomnitrix.enemy;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Basis aller Herzlosen: GeckoLib-Animationen (idle, walk, attack, special, emerge), Auftauchen aus der
 * Dunkelheit, Staerke-Skalierung mit der Spielerstufe, Elite-Variante, kein Verbrennen im Tageslicht.
 */
public abstract class HeartlessEntity extends HostileEntity implements GeoEntity {
	public static final String CONTROLLER = "main";
	protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	protected static final RawAnimation MOVE = RawAnimation.begin().thenLoop("walk");
	protected static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
	protected static final RawAnimation SPECIAL = RawAnimation.begin().thenPlay("special");
	protected static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("emerge");

	private static final TrackedData<Boolean> ELITE = DataTracker.registerData(HeartlessEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final Identifier SCALE_HEALTH = KingdomOmnitrix.id("heartless_level_health");
	private static final Identifier SCALE_DAMAGE = KingdomOmnitrix.id("heartless_level_damage");
	private static final Identifier ELITE_HEALTH = KingdomOmnitrix.id("heartless_elite_health");
	private static final Identifier ELITE_DAMAGE = KingdomOmnitrix.id("heartless_elite_damage");
	/** Leben pro Spielerstufe ueber 1 (8 %) und Schaden (5 %). */
	private static final double HEALTH_PER_LEVEL = 0.08;
	private static final double DAMAGE_PER_LEVEL = 0.05;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private boolean scaled;

	protected HeartlessEntity(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
		this.experiencePoints = 6;
	}

	// --- Skalierung -----------------------------------------------------------------------------

	/** Passt Leben und Schaden an die Spielerstufe an; einmal pro Herzlosem. */
	public void applyScaling(int playerLevel, boolean elite) {
		if (scaled) {
			return;
		}
		scaled = true;
		int levelsAbove = Math.max(0, playerLevel - 1);
		addModifier(EntityAttributes.GENERIC_MAX_HEALTH, SCALE_HEALTH, levelsAbove * HEALTH_PER_LEVEL);
		addModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, SCALE_DAMAGE, levelsAbove * DAMAGE_PER_LEVEL);
		if (elite) {
			dataTracker.set(ELITE, true);
			addModifier(EntityAttributes.GENERIC_MAX_HEALTH, ELITE_HEALTH, 1.0);
			addModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, ELITE_DAMAGE, 0.5);
			setCustomName(Text.translatable("entity.kingdomomnitrix.elite", getType().getName()).formatted(Formatting.GOLD));
			this.experiencePoints *= 3;
		}
		setHealth(getMaxHealth());
	}

	private void addModifier(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
			Identifier id, double amount) {
		EntityAttributeInstance instance = getAttributeInstance(attribute);
		if (instance != null && amount > 0 && !instance.hasModifier(id)) {
			instance.addPersistentModifier(new EntityAttributeModifier(id, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	public boolean isElite() {
		return dataTracker.get(ELITE);
	}

	@Override
	@Nullable
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
		EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
		// Spawn-Ei, /summon, Mod-Dimensionen: Staerke nach dem naechsten Spieler. Risse (EVENT) skalieren selbst.
		PlayerEntity nearest = spawnReason == SpawnReason.EVENT ? null : world.getClosestPlayer(this, 64);
		if (nearest != null) {
			applyScaling(com.santiq.kingdomomnitrix.player.HeroDataAccess.get(nearest).level(), false);
		}
		return data;
	}

	// --- Daten ----------------------------------------------------------------------------------

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ELITE, false);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Elite", isElite());
		nbt.putBoolean("Scaled", scaled);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		dataTracker.set(ELITE, nbt.getBoolean("Elite"));
		scaled = nbt.getBoolean("Scaled");
	}

	// --- Verhalten ------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (getWorld() instanceof ServerWorld world) {
			if (age == 1) {
				triggerAnim(CONTROLLER, "emerge");
				world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.1, getZ(), 20, getWidth() * 0.6, 0.05, getWidth() * 0.6, 0.02);
				playSound(ModSounds.HEARTLESS_SPAWN, 0.8f, 1.0f);
			}
			if (isElite() && age % 10 == 0) {
				world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getBodyY(0.5), getZ(), 1, getWidth() * 0.4, getHeight() * 0.3, getWidth() * 0.4, 0.0);
			}
		}
	}

	@Override
	public boolean tryAttack(net.minecraft.entity.Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit) {
			triggerAnim(CONTROLLER, "attack");
		}
		return hit;
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (getWorld() instanceof ServerWorld world) {
			// Das befreite Herz steigt auf
			world.spawnParticles(ParticleTypes.HEART, getX(), getBodyY(0.6), getZ(), 1, 0, 0, 0, 0);
			world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getBodyY(0.5), getZ(), 25, getWidth() * 0.5, getHeight() * 0.4, getWidth() * 0.5, 0.05);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.HEARTLESS_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.HEARTLESS_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.HEARTLESS_DEATH;
	}

	// --- GeckoLib -------------------------------------------------------------------------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, CONTROLLER, 4,
				state -> state.setAndContinue(state.isMoving() ? MOVE : IDLE))
				.triggerableAnim("attack", ATTACK)
				.triggerableAnim("special", SPECIAL)
				.triggerableAnim("emerge", EMERGE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
