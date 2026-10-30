package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Vierarms geworfener Felsbrocken (Modell und Textur aus Alien Evolution, {@code geo/entity/boulder.geo.json}): fliegt
 * flach mit wenig Schwerkraft wie AEs „boulder_projectile“, ueberschlaegt sich im Flug und zerschellt am ersten Block
 * oder Gegner ({@link FourArmsAbilities#boulderImpact}). Nach {@link #MAX_AGE} Ticks zerfaellt er von selbst.
 */
public class BoulderEntity extends ThrownEntity implements GeoEntity {
	public static final int MAX_AGE = 100;
	private static final TrackedData<Float> SIZE = DataTracker.registerData(BoulderEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("spin");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private float damage;
	private float strength = 1.0f;

	public BoulderEntity(EntityType<? extends BoulderEntity> type, World world) {
		super(type, world);
	}

	public BoulderEntity(ServerWorld world, ServerPlayerEntity owner, float size, float damage, float strength) {
		super(ModEntities.BOULDER, owner, world);
		this.damage = damage;
		this.strength = strength;
		getDataTracker().set(SIZE, size);
	}

	/** Darstellungsgroesse in Bloecken (Kantenlaenge des Brockens). */
	public float size() {
		return getDataTracker().get(SIZE);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(SIZE, 1.5f);
	}

	@Override
	protected double getGravity() {
		return 0.02;
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient() && age > MAX_AGE) {
			impact(null);
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		return super.canHit(entity) && (!(getOwner() instanceof ServerPlayerEntity owner) || PartyRules.canHarm(owner, entity));
	}

	@Override
	protected void onCollision(HitResult hit) {
		super.onCollision(hit);
		if (!getWorld().isClient()) {
			impact(hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null);
		}
	}

	private void impact(Entity direct) {
		if (isRemoved()) {
			return;
		}
		if (getWorld() instanceof ServerWorld world && getOwner() instanceof ServerPlayerEntity owner) {
			FourArmsAbilities.boulderImpact(world, owner, getPos(), direct, damage, size(), strength);
		}
		discard();
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putFloat("Damage", damage);
		nbt.putFloat("Strength", strength);
		nbt.putFloat("Size", size());
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		damage = nbt.getFloat("Damage");
		strength = nbt.contains("Strength") ? nbt.getFloat("Strength") : 1.0f;
		if (nbt.contains("Size")) {
			getDataTracker().set(SIZE, nbt.getFloat("Size"));
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
