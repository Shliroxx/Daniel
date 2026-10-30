package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Modell, das eine Kraft an einen Gegner haengt, wie Alien Evolution es tut: Diamondheads Kristallkuppel und
 * -stachel, Swampfires Wurzeln und Ranken, Big Chills Eisstacheln. Folgt dem Ziel, spielt die AE-Animation der Art
 * ({@code animations/entity/prop/<art>.animation.json}, „play“) und verschwindet nach Ablauf oder mit dem Ziel.
 * Rein sichtbar — die Wirkung (Festhalten, Schaden) macht die Kraft selbst.
 */
public class AttachedPropEntity extends Entity implements GeoEntity {
	private static final TrackedData<String> KIND = DataTracker.registerData(AttachedPropEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<Integer> TARGET = DataTracker.registerData(AttachedPropEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final RawAnimation PLAY = RawAnimation.begin().then("play", Animation.LoopType.DEFAULT);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int lifetime = 60;

	public AttachedPropEntity(EntityType<? extends AttachedPropEntity> type, World world) {
		super(type, world);
		noClip = true;
	}

	/**
	 * Modell an ein Ziel haengen; haengt dieselbe Art schon dort, verlaengert sie nur.
	 *
	 * @param kind  Art (Dateiname unter geo/entity/prop)
	 * @param ticks Dauer
	 */
	public static void attach(ServerWorld world, String kind, LivingEntity target, int ticks) {
		for (AttachedPropEntity existing : world.getEntitiesByClass(AttachedPropEntity.class, target.getBoundingBox().expand(1.0),
				e -> e.targetId() == target.getId() && kind.equals(e.kind()))) {
			existing.lifetime = Math.max(existing.lifetime, existing.age + ticks);
			return;
		}
		AttachedPropEntity prop = new AttachedPropEntity(ModEntities.ATTACHED_PROP, world);
		prop.getDataTracker().set(KIND, kind);
		prop.getDataTracker().set(TARGET, target.getId());
		prop.lifetime = ticks;
		prop.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ(), target.getBodyYaw(), 0.0f);
		world.spawnEntity(prop);
	}

	public String kind() {
		return getDataTracker().get(KIND);
	}

	public int targetId() {
		return getDataTracker().get(TARGET);
	}

	/** Ziel in dieser Welt (Client und Server), sonst null. */
	public LivingEntity target() {
		return getWorld().getEntityById(targetId()) instanceof LivingEntity living ? living : null;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(KIND, "");
		builder.add(TARGET, -1);
	}

	@Override
	public void tick() {
		super.tick();
		LivingEntity target = target();
		if (target != null) {
			setPosition(target.getX(), target.getY(), target.getZ());
		}
		if (!getWorld().isClient() && (age >= lifetime || target == null || !target.isAlive())) {
			discard();
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		// nicht gespeichert: haengt an einem Ziel, das nach dem Laden eine andere ID hat
		discard();
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "play", 0, state -> state.setAndContinue(PLAY)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	/** Groesse wie das Ziel (AE-Modelle sind fuer Spielergroesse gebaut). */
	public float targetScale() {
		LivingEntity target = target();
		return target == null ? 1.0f : Math.max(0.4f, target.getHeight() / 1.8f);
	}
}
