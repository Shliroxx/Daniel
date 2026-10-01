package com.santiq.kingdomomnitrix.boss;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.world.TraverseTown;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Erster Boss: Dr. Nefarious im Kampf-Mech (Ratchet & Clank).
 *
 * <p>Ablauf: Phase 1 (Laser, Raketen, Stampfer) → ab 60 % Phase 2 (zusaetzlich Stromfelder in der Arena)
 * → ab 25 % Wut (alles schneller, Ueberladung). Jeder Angriff wird angekuendigt (Ziellinie, Zielkreise,
 * Funken, Aufladen), damit man ausweichen kann. Schwachstelle ist der gluehende Kern im Ruecken:
 * von hinten doppelter, von vorn halber Schaden. Nach dem Laser ueberhitzt der Mech und ist betaeubt;
 * eine Ueberladung laesst sich mit genug Treffern in den Kern abbrechen.</p>
 */
public class NefariousEntity extends HostileEntity implements GeoEntity {
	public static final String CONTROLLER = "main";
	/** Story-Flag: Spieler hat Nefarious schon einmal besiegt (Erstbelohnung nur einmal). */
	public static final String DEFEATED_FLAG = "boss_nefarious_defeated";
	public static final int ARENA_RADIUS = 14;

	private static final TrackedData<Boolean> STUNNED = DataTracker.registerData(NefariousEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation STUNNED_LOOP = RawAnimation.begin().thenLoop("stunned");

	private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.15f, 0.1f), 1.4f);
	private static final DustParticleEffect YELLOW = new DustParticleEffect(new Vector3f(1.0f, 0.9f, 0.2f), 1.2f);
	private static final DustParticleEffect ORANGE = new DustParticleEffect(new Vector3f(1.0f, 0.5f, 0.1f), 1.6f);
	private static final double BASE_HEALTH = 400.0;

	private enum Attack { NONE, LASER, ROCKETS, STOMP, OVERLOAD, PHASE, STUNNED }

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final ServerBossBar bossBar = new ServerBossBar(Text.translatable("entity.kingdomomnitrix.nefarious"),
			BossBar.Color.GREEN, BossBar.Style.NOTCHED_10);
	private final Set<UUID> fighters = new HashSet<>();

	private BlockPos home;
	private int phase = 1;
	private boolean scaled;
	private Attack attack = Attack.NONE;
	private int attackTicks;
	private int cooldown = 60;
	private int overloadCooldown = 200;
	private float overloadInterrupt;
	private int lonelyTicks;
	// Laser
	private Vec3d laserTarget;
	private final Set<UUID> laserHit = new HashSet<>();
	// Raketen
	private final List<Vec3d> rocketTargets = new ArrayList<>();
	// Stromfelder
	private int hazardTicks = -1;
	private int hazardCooldown = 120;
	private final List<Integer> hazardSectors = new ArrayList<>();

	public NefariousEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 250;
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, BASE_HEALTH)
				.add(EntityAttributes.GENERIC_ARMOR, 6.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.5);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 32.0f));
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(STUNNED, false);
	}

	/** Mitte der Kampfflaeche (Spawnpunkt); der Mech entfernt sich nicht weiter als der Arena-Radius. */
	public void setHome(BlockPos home) {
		this.home = home.toImmutable();
	}

	// --- Ablauf ---------------------------------------------------------------------------------

	@Override
	protected void mobTick() {
		super.mobTick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (home == null) {
			home = getBlockPos();
		}
		updateFighters(world);
		if (!scaled && age > 5) {
			scaleHealth();
		}
		if (fighters.isEmpty()) {
			if (++lonelyTicks > 400) {
				discard(); // niemand kaempft mehr: Nefarious zieht ab
			}
			return;
		}
		lonelyTicks = 0;
		checkPhase(world);
		tickHazard(world);
		PlayerEntity target = nearestFighter(world);
		if (target != null) {
			setTarget(target);
			getLookControl().lookAt(target, 20.0f, 20.0f);
		}
		attackTicks++;
		switch (attack) {
			case NONE -> chooseAttack(world, target);
			case LASER -> tickLaser(world, target);
			case ROCKETS -> tickRockets(world);
			case STOMP -> tickStomp(world);
			case OVERLOAD -> tickOverload(world);
			case PHASE -> {
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getBodyY(0.6), getZ(), 6, 1.2, 1.5, 1.2, 0.2);
				if (attackTicks >= 40) {
					endAttack(40);
				}
			}
			case STUNNED -> {
				if (attackTicks % 5 == 0) {
					Vec3d core = corePosition();
					world.spawnParticles(ParticleTypes.LAVA, core.x, core.y, core.z, 1, 0.2, 0.2, 0.2, 0.0);
					world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + getHeight(), getZ(), 4, 0.6, 0.2, 0.6, 0.02);
				}
				if (attackTicks >= stunDuration) {
					dataTracker.set(STUNNED, false);
					endAttack(30);
				}
			}
		}
		bossBar.setPercent(getHealth() / getMaxHealth());
	}

	private int stunDuration = 60;

	private void updateFighters(ServerWorld world) {
		fighters.clear();
		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean near = player.isAlive() && !player.isSpectator() && player.squaredDistanceTo(this) < 48 * 48;
			if (near) {
				fighters.add(player.getUuid());
				bossBar.addPlayer(player);
			} else {
				bossBar.removePlayer(player);
			}
		}
	}

	/** Mehr Spieler → mehr Leben (+50 % je weiterem Spieler), einmal beim Start. */
	private void scaleHealth() {
		scaled = true;
		EntityAttributeInstance health = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(BASE_HEALTH * (1.0 + 0.5 * Math.max(0, fighters.size() - 1)));
			setHealth(getMaxHealth());
		}
		taunt("spawn");
	}

	@Nullable
	private PlayerEntity nearestFighter(ServerWorld world) {
		PlayerEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (UUID uuid : fighters) {
			PlayerEntity player = world.getPlayerByUuid(uuid);
			if (player != null && !player.isCreative()) {
				double distance = player.squaredDistanceTo(this);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = player;
				}
			}
		}
		return best;
	}

	private void checkPhase(ServerWorld world) {
		float fraction = getHealth() / getMaxHealth();
		int next = fraction <= 0.25f ? 3 : fraction <= 0.6f ? 2 : 1;
		if (next <= phase) {
			return;
		}
		phase = next;
		dataTracker.set(STUNNED, false);
		startAttack(Attack.PHASE);
		triggerAnim(CONTROLLER, "phase");
		bossBar.setColor(phase == 3 ? BossBar.Color.RED : BossBar.Color.YELLOW);
		bossBar.setName(Text.translatable(phase == 3 ? "entity.kingdomomnitrix.nefarious.enraged" : "entity.kingdomomnitrix.nefarious.phase2"));
		sound(world, SoundEvents.ENTITY_WITHER_SPAWN, 0.7f, 1.4f);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getBodyY(0.5), getZ(), 1, 0, 0, 0, 0);
		taunt(phase == 3 ? "enrage" : "phase2");
		EntityAttributeInstance speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(phase == 3 ? 0.3 : 0.25);
		}
	}

	private int cooldownForPhase() {
		return phase == 3 ? 25 : phase == 2 ? 40 : 55;
	}

	private void startAttack(Attack next) {
		attack = next;
		attackTicks = 0;
		getNavigation().stop();
	}

	private void endAttack(int pause) {
		attack = Attack.NONE;
		attackTicks = 0;
		cooldown = pause;
	}

	private void chooseAttack(ServerWorld world, @Nullable PlayerEntity target) {
		if (target == null) {
			return;
		}
		double distance = distanceTo(target);
		boolean farFromHome = home != null && home.getSquaredDistance(getPos()) > ARENA_RADIUS * ARENA_RADIUS;
		if (farFromHome) {
			getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
		} else if (distance > 10) {
			getNavigation().startMovingTo(target, 1.0);
		} else {
			getNavigation().stop();
		}
		if (--cooldown > 0) {
			return;
		}
		if (phase == 3 && --overloadCooldown <= 0) {
			overloadCooldown = 500;
			overloadInterrupt = 0;
			startAttack(Attack.OVERLOAD);
			triggerAnim(CONTROLLER, "overload");
			taunt("overload");
			sound(world, ModSounds.BOSS_OVERLOAD, 1.5f, 1.0f);
			return;
		}
		if (distance < 5) {
			startAttack(Attack.STOMP);
			triggerAnim(CONTROLLER, "stomp");
			sound(world, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.3f);
		} else if (random.nextBoolean()) {
			startAttack(Attack.LASER);
			laserTarget = target.getEyePos();
			laserHit.clear();
			triggerAnim(CONTROLLER, "laser");
			sound(world, ModSounds.BOSS_LASER_CHARGE, 1.3f, 1.0f);
		} else {
			startAttack(Attack.ROCKETS);
			prepareRockets(world);
			triggerAnim(CONTROLLER, "rockets");
			sound(world, ModSounds.BOSS_ROCKET, 1.0f, 0.8f);
		}
	}

	// --- Laser ----------------------------------------------------------------------------------

	private Vec3d cannonPosition() {
		Vec3d left = Vec3d.fromPolar(0.0f, getBodyYaw() - 90.0f).multiply(1.05);
		return getPos().add(0.0, 1.55, 0.0).add(left).add(getRotationVector().multiply(0.9));
	}

	private void tickLaser(ServerWorld world, @Nullable PlayerEntity target) {
		int charge = phase == 3 ? 22 : 30;
		if (attackTicks < charge - 8 && target != null) {
			laserTarget = target.getEyePos(); // Ziel folgt, kurz vor dem Schuss wird es festgehalten
		}
		Vec3d from = cannonPosition();
		Vec3d to = beamEnd(world, from, laserTarget);
		if (attackTicks < charge) {
			if (attackTicks % 2 == 0) {
				line(world, from, to, RED, 0.6);
			}
			return;
		}
		if (attackTicks == charge) {
			sound(world, ModSounds.BOSS_LASER_FIRE, 1.5f, 1.0f);
		}
		line(world, from, to, ORANGE, 0.25);
		world.spawnParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 4, 0.2, 0.2, 0.2, 0.05);
		for (UUID uuid : fighters) {
			PlayerEntity player = world.getPlayerByUuid(uuid);
			if (player != null && !laserHit.contains(uuid) && distanceToSegment(player.getBoundingBox().getCenter(), from, to) < 1.3) {
				laserHit.add(uuid);
				player.damage(getDamageSources().mobAttack(this), phase == 1 ? 9.0f : 12.0f);
				player.setOnFireFor(3.0f);
			}
		}
		if (attackTicks >= charge + 12) {
			// Ueberhitzung: der Kern liegt frei
			startAttack(Attack.STUNNED);
			stunDuration = phase == 3 ? 40 : 60;
			dataTracker.set(STUNNED, true);
			sound(world, SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.6f);
			taunt("overheat");
		}
	}

	private Vec3d beamEnd(ServerWorld world, Vec3d from, Vec3d aim) {
		Vec3d end = from.add(aim.subtract(from).normalize().multiply(28.0));
		BlockHitResult hit = world.raycast(new RaycastContext(from, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getPos() : end;
	}

	private static double distanceToSegment(Vec3d point, Vec3d a, Vec3d b) {
		Vec3d ab = b.subtract(a);
		double t = MathHelper.clamp(point.subtract(a).dotProduct(ab) / Math.max(1.0e-6, ab.lengthSquared()), 0.0, 1.0);
		return point.distanceTo(a.add(ab.multiply(t)));
	}

	// --- Raketen --------------------------------------------------------------------------------

	private void prepareRockets(ServerWorld world) {
		rocketTargets.clear();
		for (UUID uuid : fighters) {
			PlayerEntity player = world.getPlayerByUuid(uuid);
			if (player != null) {
				rocketTargets.add(player.getPos());
				rocketTargets.add(player.getPos().add(random.nextGaussian() * 3, 0, random.nextGaussian() * 3));
			}
		}
		int extra = phase == 3 ? 5 : phase == 2 ? 3 : 2;
		BlockPos center = home != null ? home : getBlockPos();
		for (int i = 0; i < extra; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = random.nextDouble() * ARENA_RADIUS;
			rocketTargets.add(new Vec3d(center.getX() + 0.5 + Math.cos(angle) * r, center.getY(), center.getZ() + 0.5 + Math.sin(angle) * r));
		}
	}

	private void tickRockets(ServerWorld world) {
		int impact = 32;
		if (attackTicks < impact) {
			if (attackTicks % 4 == 0) {
				for (Vec3d target : rocketTargets) {
					ring(world, target, 2.5, RED);
				}
			}
			if (attackTicks == impact - 12) {
				sound(world, ModSounds.BOSS_ROCKET, 1.2f, 1.0f);
			}
			return;
		}
		if (attackTicks == impact) {
			for (Vec3d target : rocketTargets) {
				world.spawnParticles(ParticleTypes.FIREWORK, target.x, target.y + 6, target.z, 6, 0.1, 2.0, 0.1, 0.05);
				world.createExplosion(this, target.x, target.y + 0.2, target.z, 2.2f, false, World.ExplosionSourceType.NONE);
			}
			rocketTargets.clear();
		}
		if (attackTicks >= impact + 10) {
			endAttack(cooldownForPhase());
		}
	}

	@Override
	public boolean isImmuneToExplosion(Explosion explosion) {
		return true;
	}

	// --- Stampfer -------------------------------------------------------------------------------

	private void tickStomp(ServerWorld world) {
		int impact = 16;
		if (attackTicks < impact) {
			if (attackTicks % 3 == 0) {
				ring(world, getPos(), 5.0, YELLOW);
			}
			return;
		}
		if (attackTicks == impact) {
			sound(world, ModSounds.BOSS_STOMP, 1.6f, 1.0f);
			world.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 6, 2.5, 0.1, 2.5, 0.0);
			for (UUID uuid : fighters) {
				PlayerEntity player = world.getPlayerByUuid(uuid);
				if (player != null && player.squaredDistanceTo(this) < 5.5 * 5.5 && player.isOnGround()) {
					player.damage(getDamageSources().mobAttack(this), phase == 1 ? 8.0f : 11.0f);
					Vec3d push = player.getPos().subtract(getPos()).normalize().multiply(1.4);
					player.addVelocity(push.x, 0.6, push.z);
					player.velocityModified = true;
				}
			}
		}
		if (attackTicks >= impact + 8) {
			endAttack(cooldownForPhase());
		}
	}

	// --- Ueberladung (Wut) ----------------------------------------------------------------------

	private void tickOverload(ServerWorld world) {
		int blast = 70;
		Vec3d core = corePosition();
		if (attackTicks < blast) {
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, core.x, core.y, core.z, 3, 0.3, 0.3, 0.3, 0.1);
			if (attackTicks % 4 == 0) {
				ring(world, getPos(), 9.0 * attackTicks / blast + 1, ORANGE);
			}
			if (attackTicks % 20 == 0) {
				sound(world, ModSounds.BOSS_LASER_CHARGE, 1.6f, 0.6f + attackTicks / 100.0f);
			}
			return;
		}
		sound(world, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 2.0f, 0.5f);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getBodyY(0.5), getZ(), 3, 3, 1, 3, 0);
		for (UUID uuid : fighters) {
			PlayerEntity player = world.getPlayerByUuid(uuid);
			if (player != null && player.squaredDistanceTo(this) < 10 * 10) {
				player.damage(getDamageSources().mobAttack(this), 18.0f);
				Vec3d push = player.getPos().subtract(getPos()).normalize().multiply(2.0);
				player.addVelocity(push.x, 0.8, push.z);
				player.velocityModified = true;
			}
		}
		endAttack(40);
	}

	// --- Stromfelder (ab Phase 2) ---------------------------------------------------------------

	private void tickHazard(ServerWorld world) {
		if (phase < 2 || home == null) {
			return;
		}
		if (hazardTicks < 0) {
			if (--hazardCooldown <= 0) {
				hazardTicks = 0;
				hazardSectors.clear();
				List<Integer> all = new ArrayList<>(List.of(0, 1, 2, 3));
				java.util.Collections.shuffle(all, new java.util.Random(random.nextLong()));
				hazardSectors.addAll(all.subList(0, phase == 3 ? 3 : 2));
				sound(world, ModSounds.BOSS_OVERLOAD, 1.0f, 1.3f);
			}
			return;
		}
		hazardTicks++;
		boolean warning = hazardTicks < 40;
		if (hazardTicks % (warning ? 4 : 2) == 0) {
			for (int sector : hazardSectors) {
				for (int i = 0; i < (warning ? 6 : 14); i++) {
					double angle = (sector + random.nextDouble()) * Math.PI / 2;
					double r = 2 + random.nextDouble() * (ARENA_RADIUS - 2);
					ParticleEffect effect = warning ? YELLOW : ParticleTypes.ELECTRIC_SPARK;
					world.spawnParticles(effect, home.getX() + 0.5 + Math.cos(angle) * r, home.getY() + 0.2, home.getZ() + 0.5 + Math.sin(angle) * r,
							1, 0.1, 0.05, 0.1, 0.0);
				}
			}
		}
		if (!warning && hazardTicks % 10 == 0) {
			sound(world, SoundEvents.BLOCK_BEEHIVE_WORK, 1.0f, 2.0f);
			for (UUID uuid : fighters) {
				PlayerEntity player = world.getPlayerByUuid(uuid);
				if (player != null && player.isOnGround() && hazardSectors.contains(sectorOf(player.getPos()))
						&& home.getSquaredDistance(player.getPos()) < ARENA_RADIUS * ARENA_RADIUS) {
					player.damage(getDamageSources().lightningBolt(), 3.0f);
				}
			}
		}
		if (hazardTicks >= 80) {
			hazardTicks = -1;
			hazardCooldown = phase == 3 ? 120 : 200;
		}
	}

	private int sectorOf(Vec3d pos) {
		double angle = Math.atan2(pos.z - (home.getZ() + 0.5), pos.x - (home.getX() + 0.5));
		if (angle < 0) {
			angle += Math.PI * 2;
		}
		return Math.min(3, (int) (angle / (Math.PI / 2)));
	}

	// --- Schaden und Schwachstelle --------------------------------------------------------------

	private Vec3d corePosition() {
		return getPos().add(0.0, 2.3, 0.0).subtract(getRotationVector().multiply(0.7));
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
			return super.damage(source, amount);
		}
		if (attack == Attack.PHASE) {
			return false;
		}
		Vec3d from = source.getPosition();
		float multiplier = 1.0f;
		boolean core = false;
		if (from != null) {
			Vec3d toAttacker = from.subtract(getPos()).multiply(1, 0, 1).normalize();
			Vec3d facing = Vec3d.fromPolar(0.0f, getBodyYaw());
			double dot = toAttacker.dotProduct(facing);
			if (dot < -0.35) {
				multiplier = 2.0f;
				core = true;
			} else if (dot > 0.35) {
				multiplier = 0.5f;
			}
		}
		if (dataTracker.get(STUNNED)) {
			multiplier *= 2.5f;
			core = true;
		}
		if (getWorld() instanceof ServerWorld world) {
			Vec3d hit = corePosition();
			if (core) {
				world.spawnParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 10, 0.3, 0.3, 0.3, 0.3);
				sound(world, ModSounds.BOSS_HURT, 1.0f, 1.3f);
			} else if (multiplier < 1.0f) {
				sound(world, SoundEvents.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
			}
			if (attack == Attack.OVERLOAD && core) {
				overloadInterrupt += amount * multiplier;
				if (overloadInterrupt >= 30.0f) {
					startAttack(Attack.STUNNED);
					stunDuration = 100;
					dataTracker.set(STUNNED, true);
					sound(world, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.5f, 0.5f);
					taunt("interrupted");
				}
			}
		}
		return super.damage(source, amount * multiplier);
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		updateFighters(world); // aktueller Stand, auch wenn die KI zuletzt nicht lief
		triggerAnim(CONTROLLER, "death");
		taunt("defeat");
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getBodyY(0.5), getZ(), 4, 1.5, 1.5, 1.5, 0);
		for (UUID uuid : fighters) {
			if (world.getPlayerByUuid(uuid) instanceof ServerPlayerEntity player) {
				reward(player);
			}
		}
		bossBar.clearPlayers();
	}

	/** Erster Sieg: Omega-Schluessel + grosse Belohnung; jeder weitere: Bolts und Material. */
	private void reward(ServerPlayerEntity player) {
		boolean first = !HeroDataAccess.get(player).hasFlag(DEFEATED_FLAG);
		int bolts = first ? 800 : 300;
		int experience = first ? 300 : 120;
		HeroDataAccess.update(player, data -> data.addBolts(bolts).withFlag(DEFEATED_FLAG, true));
		HeroDataAccess.grantExperience(player, experience);
		if (first) {
			player.getInventory().offerOrDrop(new ItemStack(ModItems.OMEGA_KEY));
			player.getInventory().offerOrDrop(new ItemStack(ModItems.ORICHALCUM, 2));
		}
		player.getInventory().offerOrDrop(new ItemStack(ModItems.RARITANIUM, first ? 5 : 3));
		player.sendMessage(Text.translatable(first ? "message.kingdomomnitrix.boss_first_win" : "message.kingdomomnitrix.boss_win",
				Text.translatable("entity.kingdomomnitrix.nefarious"), bolts, experience).formatted(Formatting.GOLD), false);
	}

	private void taunt(String key) {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		Text line = Text.translatable("entity.kingdomomnitrix.nefarious.short").formatted(Formatting.GREEN, Formatting.BOLD)
				.append(Text.literal(" ")).append(Text.translatable("boss.kingdomomnitrix.nefarious." + key).formatted(Formatting.WHITE));
		for (UUID uuid : fighters) {
			PlayerEntity player = world.getPlayerByUuid(uuid);
			if (player != null) {
				player.sendMessage(line, false);
			}
		}
	}

	// --- Effekte --------------------------------------------------------------------------------

	private static void line(ServerWorld world, Vec3d from, Vec3d to, ParticleEffect effect, double step) {
		Vec3d delta = to.subtract(from);
		int steps = (int) Math.ceil(delta.length() / step);
		for (int i = 0; i <= steps; i++) {
			Vec3d point = from.add(delta.multiply((double) i / Math.max(1, steps)));
			world.spawnParticles(effect, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private static void ring(ServerWorld world, Vec3d center, double radius, ParticleEffect effect) {
		int points = (int) Math.max(12, radius * 6);
		for (int i = 0; i < points; i++) {
			double angle = i * Math.PI * 2 / points;
			world.spawnParticles(effect, center.x + Math.cos(angle) * radius, center.y + 0.15, center.z + Math.sin(angle) * radius,
					1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private void sound(ServerWorld world, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, getX(), getY(), getZ(), sound, SoundCategory.HOSTILE, volume, pitch);
	}

	// --- Sonstiges ------------------------------------------------------------------------------

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		bossBar.clearPlayers();
		super.remove(reason);
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isFireImmune() {
		return true;
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.BOSS_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.BOSS_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, net.minecraft.block.BlockState state) {
		playSound(ModSounds.BOSS_STOMP, 0.35f, 1.6f);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (home != null) {
			nbt.putLong("Home", home.asLong());
		}
		nbt.putInt("Phase", phase);
		nbt.putBoolean("Scaled", scaled);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Home")) {
			home = BlockPos.fromLong(nbt.getLong("Home"));
		}
		phase = Math.max(1, nbt.getInt("Phase"));
		scaled = nbt.getBoolean("Scaled");
		if (hasCustomName()) {
			bossBar.setName(getDisplayName());
		}
	}

	/** Darf in der Stadt Traverse Town erscheinen (sonst entfernt der Stadtschutz Monster). */
	public void allowInTown() {
		addCommandTag(TraverseTown.ALLOWED_TAG);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, CONTROLLER, 5, state -> {
			if (dataTracker.get(STUNNED)) {
				return state.setAndContinue(STUNNED_LOOP);
			}
			return state.setAndContinue(state.isMoving() ? WALK : IDLE);
		}).triggerableAnim("laser", RawAnimation.begin().thenPlay("laser"))
				.triggerableAnim("rockets", RawAnimation.begin().thenPlay("rockets"))
				.triggerableAnim("stomp", RawAnimation.begin().thenPlay("stomp"))
				.triggerableAnim("phase", RawAnimation.begin().thenPlay("phase"))
				.triggerableAnim("overload", RawAnimation.begin().thenPlay("overload"))
				.triggerableAnim("death", RawAnimation.begin().thenPlay("death")));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
