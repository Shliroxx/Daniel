package com.santiq.kingdomomnitrix.space;

import com.santiq.kingdomomnitrix.mixin.LivingEntityAccessor;
import com.santiq.kingdomomnitrix.registry.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Die Aphelion: Raumschiff mit KI aus Ratchet & Clank 3. Ein Pilot, ein Mitflieger.
 * Steuerung wie ein Flugzeug: W/S fliegt in Blickrichtung (auch hoch und runter), A/D seitlich, Leertaste steigt.
 * Wie beim Boot rechnet der Client des Piloten die Bewegung; der Server prueft sie (Vanilla-Fahrzeugpakete).
 * Hoch genug geflogen, verlaesst das Schiff die Atmosphaere ({@link SpaceTravel}).
 */
public class ShipEntity extends Entity implements GeoEntity {
	private static final TrackedData<Float> DAMAGE = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Verbleibende Warp-Ticks (0 = kein Warp); auch der Pilot-Client braucht es, er rechnet die Bewegung. */
	private static final TrackedData<Integer> WARP = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation FLIGHT = RawAnimation.begin().thenLoop("flight");

	public static final double MAX_SPEED_ATMOSPHERE = 1.6;
	public static final double MAX_SPEED_SPACE = 2.4;
	private static final double ACCELERATION = 0.09;
	private static final double SPACE_ACCELERATION = 0.13;
	private static final float BREAK_DAMAGE = 60.0f;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	// Interpolation fuer Clients, die das Schiff nur sehen
	private int lerpTicks;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private float lerpYaw;
	private float lerpPitch;

	/** Zuletzt angekuendigter Weltraumriss (nicht gespeichert; verhindert Wiederholungen der KI). */
	@Nullable
	Identifier announcedRoute;

	/** Welt, aus der das Schiff zuletzt ins All gestartet ist (Rueckweg beim Sinkflug). */
	@Nullable
	private Identifier homeWorld;

	/** Ziel des laufenden Warps (Galaxiekarte), nur auf dem Server. */
	@Nullable
	private Identifier warpTarget;

	public ShipEntity(EntityType<?> type, World world) {
		super(type, world);
		this.intersectionChecked = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(DAMAGE, 0.0f);
		builder.add(WARP, 0);
	}

	// --- Mitfliegen -----------------------------------------------------------------------------

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (player.shouldCancelInteraction()) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient()) {
			if (!canAddPassenger(player) || !player.startRiding(this)) {
				return ActionResult.PASS;
			}
			if (player instanceof ServerPlayerEntity serverPlayer && getControllingPassenger() == player) {
				ShipAi.say(serverPlayer, "boarding", serverPlayer.getName());
			}
			getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.NEUTRAL, 0.6f, 1.4f);
		}
		return ActionResult.success(getWorld().isClient());
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().size() < 2;
	}

	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof PlayerEntity pilot ? pilot : null;
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		int seat = getPassengerList().indexOf(passenger);
		double back = seat <= 0 ? -0.9 : 0.1;
		return new Vec3d(0.0, dimensions.height() * 0.3, back).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
	}

	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		Vec3d side = new Vec3d(2.2, 0.0, 0.0).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
		return getPos().add(side).add(0.0, 0.5, 0.0);
	}

	@Override
	public boolean shouldDismountUnderwater() {
		return false;
	}

	// --- Bewegung -------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		tickLerp();
		if (isLogicalSideForUpdatingMovement()) {
			steer();
			move(MovementType.SELF, getVelocity());
		} else {
			setVelocity(Vec3d.ZERO);
		}
		tryCheckBlockCollision();
		if (getWorld() instanceof ServerWorld world) {
			float damage = dataTracker.get(DAMAGE);
			if (damage > 0.0f) {
				dataTracker.set(DAMAGE, Math.max(0.0f, damage - 0.5f));
			}
			if (getControllingPassenger() != null && age % 2 == 0) {
				Vec3d back = new Vec3d(0.0, 0.45, 2.1).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
				world.spawnParticles(ParticleTypes.FLAME, getX() + back.x, getY() + back.y, getZ() + back.z, 2, 0.15, 0.1, 0.15, 0.01);
			}
			if (isWarping()) {
				Galaxy.tickWarp(this, world);
			} else {
				SpaceTravel.tickShip(this, world);
			}
		}
	}

	private void steer() {
		Vec3d velocity = getVelocity();
		boolean space = SpaceTravel.isSpace(getWorld());
		LivingEntity pilot = getControllingPassenger();
		if (isWarping()) {
			// Warp: steil hinauf und immer schneller — der Pilot steuert nicht
			Vec3d look = Vec3d.fromPolar(-35.0f, getYaw());
			setPitch(MathHelper.lerp(0.1f, getPitch(), -35.0f));
			setVelocity(velocity.multiply(0.9).add(look.multiply(0.25)));
			return;
		}
		if (pilot instanceof PlayerEntity player) {
			setYaw(MathHelper.lerpAngleDegrees(0.3f, getYaw(), player.getYaw()));
			setPitch(MathHelper.lerp(0.3f, getPitch(), MathHelper.clamp(player.getPitch(), -70.0f, 70.0f)));
			double acceleration = space ? SPACE_ACCELERATION : ACCELERATION;
			Vec3d look = Vec3d.fromPolar(getPitch(), getYaw());
			Vec3d left = Vec3d.fromPolar(0.0f, getYaw() - 90.0f);
			velocity = velocity.multiply(0.94)
					.add(look.multiply(player.forwardSpeed * acceleration))
					.add(left.multiply(player.sidewaysSpeed * acceleration * 0.6));
			if (((LivingEntityAccessor) player).kingdomomnitrix$isJumping()) {
				velocity = velocity.add(0.0, acceleration * 1.2, 0.0);
			}
			double max = (space ? MAX_SPEED_SPACE : MAX_SPEED_ATMOSPHERE) * ShipLog.get(player).speedFactor();
			if (velocity.lengthSquared() > max * max) {
				velocity = velocity.normalize().multiply(max);
			}
		} else {
			// Ohne Pilot: bremst ab; in der Atmosphaere sinkt es zu Boden, im All treibt es.
			velocity = velocity.multiply(0.9);
			setPitch(MathHelper.lerp(0.1f, getPitch(), 0.0f));
			if (!space && !isOnGround()) {
				velocity = velocity.add(0.0, -0.04, 0.0);
			}
		}
		if (isOnGround() && velocity.y < 0.0) {
			velocity = new Vec3d(velocity.x * 0.7, 0.0, velocity.z * 0.7);
		}
		setVelocity(velocity);
	}

	private void tickLerp() {
		if (isLogicalSideForUpdatingMovement()) {
			lerpTicks = 0;
			updateTrackedPosition(getX(), getY(), getZ());
		}
		if (lerpTicks > 0) {
			double x = getX() + (lerpX - getX()) / lerpTicks;
			double y = getY() + (lerpY - getY()) / lerpTicks;
			double z = getZ() + (lerpZ - getZ()) / lerpTicks;
			float yaw = getYaw() + MathHelper.wrapDegrees(lerpYaw - getYaw()) / lerpTicks;
			float pitch = getPitch() + (lerpPitch - getPitch()) / lerpTicks;
			lerpTicks--;
			setPosition(x, y, z);
			setRotation(yaw, pitch);
		}
	}

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		lerpX = x;
		lerpY = y;
		lerpZ = z;
		lerpYaw = yaw;
		lerpPitch = pitch;
		lerpTicks = 10;
	}

	@Override
	public double getLerpTargetX() {
		return lerpTicks > 0 ? lerpX : getX();
	}

	@Override
	public double getLerpTargetY() {
		return lerpTicks > 0 ? lerpY : getY();
	}

	@Override
	public double getLerpTargetZ() {
		return lerpTicks > 0 ? lerpZ : getZ();
	}

	@Override
	public float getLerpTargetYaw() {
		return lerpTicks > 0 ? lerpYaw : getYaw();
	}

	@Override
	public float getLerpTargetPitch() {
		return lerpTicks > 0 ? lerpPitch : getPitch();
	}

	@Override
	protected double getGravity() {
		return 0.0; // Schwerkraft rechnet steer() selbst
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	// --- Treffer und Abbau ----------------------------------------------------------------------

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getWorld().isClient() || isRemoved() || !(source.getAttacker() instanceof PlayerEntity player)) {
			return false; // nur Spieler koennen das Schiff abbauen; Fall, Explosion, Monster nicht
		}
		if (hasPassenger(player)) {
			return false;
		}
		if (player.isCreative()) {
			discard();
			return true;
		}
		float total = dataTracker.get(DAMAGE) + amount * 10.0f;
		dataTracker.set(DAMAGE, total);
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.NEUTRAL, 0.4f, 1.6f);
		if (total >= BREAK_DAMAGE) {
			removeAllPassengers();
			dropStack(new ItemStack(ModItems.APHELION));
			discard();
		}
		return true;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(ModItems.APHELION);
	}

	// --- Warp (Galaxiekarte) --------------------------------------------------------------------

	public boolean isWarping() {
		return dataTracker.get(WARP) > 0;
	}

	public int warpTicksLeft() {
		return dataTracker.get(WARP);
	}

	@Nullable
	Identifier warpTarget() {
		return warpTarget;
	}

	void startWarp(Identifier target, int ticks) {
		warpTarget = target;
		dataTracker.set(WARP, Math.max(1, ticks));
	}

	void tickWarp() {
		dataTracker.set(WARP, Math.max(0, dataTracker.get(WARP) - 1));
	}

	void stopWarp() {
		warpTarget = null;
		dataTracker.set(WARP, 0);
	}

	// --- Speichern ------------------------------------------------------------------------------

	@Nullable
	public Identifier homeWorld() {
		return homeWorld;
	}

	public void setHomeWorld(@Nullable Identifier world) {
		this.homeWorld = world;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		homeWorld = nbt.contains("Home") ? Identifier.tryParse(nbt.getString("Home")) : null;
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (homeWorld != null) {
			nbt.putString("Home", homeWorld.toString());
		}
	}

	@Override
	public void copyFrom(Entity original) {
		super.copyFrom(original);
		if (original instanceof ShipEntity ship) {
			this.homeWorld = ship.homeWorld;
		}
	}

	// --- GeckoLib -------------------------------------------------------------------------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 5,
				state -> state.setAndContinue(hasPassengers() ? FLIGHT : IDLE)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
