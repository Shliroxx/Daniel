package com.santiq.kingdomomnitrix.weapon;

import net.minecraft.server.network.ServerPlayerEntity;

import com.santiq.kingdomomnitrix.party.PartyRules;

import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import com.santiq.kingdomomnitrix.registry.ModParticles;
import com.santiq.kingdomomnitrix.vfx.Vfx;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * Ein Geschoss fuer alle Faehigkeiten. Welche Art es ist, bestimmt das angezeigte Item:
 * Feuer-Kugel, Eis-Kugel, Plasma-Schuss, Kristallsplitter oder der geworfene OmniWrench.
 */
public class HeroProjectileEntity extends ThrownItemEntity {
	private static final int MAX_AGE_TICKS = 80;

	/** Schaden statt des Standardwerts der Art (z. B. Zauberstufe × Magiekraft); nur serverseitig, &lt; 0 = Standard. */
	private float damageOverride = -1.0f;
	/** Sprengkraft beim Aufprall (0 = keine); Explosion ohne Blockschaden. Nur serverseitig. */
	private float explosionPower;
	/** Morph-O-Ray: Gegner bis zu dieser Maximalgesundheit werden zum Huhn (0 = aus). Nur serverseitig. */
	private float morphMaxHealth;
	/** Devastator: Ziel, auf das die Rakete nachlenkt (null = geradeaus). Nur serverseitig. */
	@org.jetbrains.annotations.Nullable
	private LivingEntity homingTarget;
	/** Blitz Gun: jede Kugel einer Ladung zaehlt (sonst schluckt die Unverwundbarkeit nach dem ersten Treffer den Rest). */
	private boolean pellet;
	/** Zusatzwirkung beim Aufprall (z. B. Splitter von Heatblasts Plasmakern); nur serverseitig, nicht gespeichert. */
	@org.jetbrains.annotations.Nullable
	private java.util.function.Consumer<HeroProjectileEntity> onImpact;
	/** Anteil, um den die Flugrichtung je Tick zum Ziel dreht */
	private static final double HOMING_TURN = 0.18;

	public enum Kind {
		FIRE(5.0f, true, ModParticles.FIRE_EMBER),
		ICE(4.0f, true, ModParticles.ICE_SHARD),
		PLASMA(6.0f, false, ModParticles.PLASMA),
		CRYSTAL(5.0f, false, ParticleTypes.END_ROD),
		WRENCH(8.0f, false, ParticleTypes.CRIT),
		DARK(4.0f, true, ParticleTypes.SQUID_INK),
		// Stinkfly-Schleim: verlangsamt stark, leichte Vergiftung
		SLIME(3.0f, false, ParticleTypes.ITEM_SLIME),
		// Wildmutt-Stachel: schnell, durchdringend wenig Schaden, schwaecht
		QUILL(4.0f, false, ParticleTypes.CRIT);

		public final float damage;
		public final boolean magic;
		public final ParticleEffect particle;

		Kind(float damage, boolean magic, ParticleEffect particle) {
			this.damage = damage;
			this.magic = magic;
			this.particle = particle;
		}
	}

	public HeroProjectileEntity(EntityType<? extends HeroProjectileEntity> entityType, World world) {
		super(entityType, world);
	}

	public HeroProjectileEntity(World world, LivingEntity owner) {
		super(ModEntities.HERO_PROJECTILE, owner, world);
	}

	/** Erzeugt und feuert ein Geschoss in Blickrichtung des Schuetzen. */
	public static HeroProjectileEntity shoot(World world, LivingEntity owner, Item display, float speed, float divergence) {
		HeroProjectileEntity projectile = new HeroProjectileEntity(world, owner);
		projectile.setItem(new ItemStack(display));
		projectile.setVelocity(owner, owner.getPitch(), owner.getYaw(), 0.0f, speed, divergence);
		world.spawnEntity(projectile);
		return projectile;
	}

	/** Feuert vom Schuetzen aus auf ein Ziel (fuer Gegner, die nicht ueber Blickrichtung zielen). */
	public static HeroProjectileEntity shootAt(World world, LivingEntity owner, Item display, Entity target, float speed, float divergence) {
		HeroProjectileEntity projectile = new HeroProjectileEntity(world, owner);
		projectile.setItem(new ItemStack(display));
		double dx = target.getX() - owner.getX();
		double dy = target.getBodyY(0.5) - projectile.getY();
		double dz = target.getZ() - owner.getZ();
		projectile.setVelocity(dx, dy, dz, speed, divergence);
		world.spawnEntity(projectile);
		return projectile;
	}

	public HeroProjectileEntity withExplosion(float power) {
		this.explosionPower = Math.max(0.0f, power);
		return this;
	}

	public HeroProjectileEntity withMorph(float maxHealth) {
		this.morphMaxHealth = Math.max(0.0f, maxHealth);
		return this;
	}

	public HeroProjectileEntity asPellet() {
		this.pellet = true;
		return this;
	}

	public HeroProjectileEntity withHoming(@org.jetbrains.annotations.Nullable LivingEntity target) {
		this.homingTarget = target;
		return this;
	}

	public HeroProjectileEntity withImpact(java.util.function.Consumer<HeroProjectileEntity> impact) {
		this.onImpact = impact;
		return this;
	}

	/** Schaden dieses Geschosses (Wert der Art, wenn nicht gesetzt). */
	public float damage() {
		return damageOverride >= 0.0f ? damageOverride : getKind().damage;
	}

	public HeroProjectileEntity withDamage(float damage) {
		this.damageOverride = damage;
		return this;
	}

	public Kind getKind() {
		ItemStack stack = getStack();
		if (stack.isOf(ModItems.FIRE_ORB)) {
			return Kind.FIRE;
		}
		if (stack.isOf(ModItems.ICE_ORB)) {
			return Kind.ICE;
		}
		if (stack.isOf(ModItems.CRYSTAL_SHARD)) {
			return Kind.CRYSTAL;
		}
		if (stack.isOf(ModItems.OMNIWRENCH)) {
			return Kind.WRENCH;
		}
		if (stack.isOf(ModItems.DARK_ORB)) {
			return Kind.DARK;
		}
		if (stack.isOf(Items.SLIME_BALL)) {
			return Kind.SLIME;
		}
		if (stack.isOf(Items.ARROW)) {
			return Kind.QUILL;
		}
		return Kind.PLASMA;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.PLASMA_SHOT;
	}

	@Override
	protected double getGravity() {
		Kind kind = getKind();
		return kind == Kind.WRENCH || kind == Kind.SLIME ? 0.02 : 0.0;
	}

	private static final int TRAIL_DELAY_TICKS = 2;
	private static final double TRAIL_MIN_DISTANCE_SQ = 2.25;

	@Override
	public void tick() {
		super.tick();
		World world = getWorld();
		if (world.isClient()) {
			// erst nach 2 Ticks: sonst entsteht die Spur direkt vor der Kamera des Schuetzen und verdeckt die Sicht
			if (age > TRAIL_DELAY_TICKS && age % 2 == 0 && (getOwner() == null || getOwner().squaredDistanceTo(this) > TRAIL_MIN_DISTANCE_SQ)) {
				world.addParticle(getKind().particle, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
			}
		} else if (age > MAX_AGE_TICKS) {
			discard();
		} else if (homingTarget != null) {
			if (!homingTarget.isAlive() || homingTarget.isRemoved()) {
				homingTarget = null;
			} else {
				// Nachlenken mit gleichbleibendem Tempo (Rakete des Devastators)
				Vec3d velocity = getVelocity();
				double speed = velocity.length();
				Vec3d wanted = homingTarget.getPos().add(0.0, homingTarget.getHeight() * 0.5, 0.0).subtract(getPos()).normalize().multiply(speed);
				setVelocity(velocity.add(wanted.subtract(velocity).multiply(HOMING_TURN)).normalize().multiply(speed));
				velocityModified = true;
			}
		}
	}

	/** Nicht das eigene Fahrzeug treffen (Bordkanone der Aphelion) und keine Mitfahrer darin. */
	@Override
	protected boolean canHit(net.minecraft.entity.Entity entity) {
		net.minecraft.entity.Entity owner = getOwner();
		if (owner != null && owner.hasVehicle() && (entity == owner.getRootVehicle() || entity.getRootVehicle() == owner.getRootVehicle())) {
			return false;
		}
		return super.canHit(entity);
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		Entity target = entityHitResult.getEntity();
		if (getOwner() instanceof ServerPlayerEntity shooter && !PartyRules.canHarm(shooter, target)) {
			return; // Mitspieler (Gruppe oder PvP aus): kein Schaden, keine Wirkung
		}
		Kind kind = getKind();
		DamageSource source = kind.magic
				? getDamageSources().indirectMagic(this, getOwner())
				: getDamageSources().thrown(this, getOwner());
		if (pellet) {
			target.timeUntilRegen = 0;
		}
		target.damage(source, damageOverride >= 0.0f ? damageOverride : kind.damage);
		if (morphMaxHealth > 0.0f) {
			morph(target);
		}
		if (kind == Kind.FIRE) {
			target.setOnFireFor(5.0f);
		}
		if (kind == Kind.DARK && target instanceof LivingEntity living) {
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 0), getOwner());
		}
		if (kind == Kind.SLIME && target instanceof LivingEntity living) {
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 3), getOwner());
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0), getOwner());
		}
		if (kind == Kind.QUILL && target instanceof LivingEntity living) {
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0), getOwner());
		}
		if (kind == Kind.ICE && target instanceof LivingEntity living) {
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2), getOwner());
			living.setFrozenTicks(Math.max(living.getFrozenTicks(), 160));
		}
	}

	/** Morph-O-Ray: verwandelt einen Gegner (kein Spieler, kein Boss, nicht zu stark) in ein Huhn. */
	private void morph(Entity target) {
		if (!(getWorld() instanceof ServerWorld world) || !(target instanceof net.minecraft.entity.mob.MobEntity mob) || !mob.isAlive()
				|| mob instanceof net.minecraft.entity.passive.ChickenEntity || mob.getMaxHealth() > morphMaxHealth) {
			return;
		}
		net.minecraft.entity.passive.ChickenEntity chicken = net.minecraft.entity.EntityType.CHICKEN.create(world);
		if (chicken == null) {
			return;
		}
		chicken.refreshPositionAndAngles(mob.getX(), mob.getY(), mob.getZ(), mob.getYaw(), mob.getPitch());
		if (mob.hasCustomName()) {
			chicken.setCustomName(mob.getCustomName());
		}
		mob.discard();
		world.spawnEntity(chicken);
		com.santiq.kingdomomnitrix.vfx.Fx.flash(world, chicken.getPos().add(0, 0.4, 0), 0xD85AFF, 1.6f);
		world.spawnParticles(ParticleTypes.CLOUD, chicken.getX(), chicken.getY() + 0.4, chicken.getZ(), 14, 0.3, 0.3, 0.3, 0.05);
		world.playSound(null, chicken.getX(), chicken.getY(), chicken.getZ(), net.minecraft.sound.SoundEvents.ENTITY_CHICKEN_HURT,
				net.minecraft.sound.SoundCategory.NEUTRAL, 1.0f, 1.2f);
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (getWorld() instanceof ServerWorld serverWorld) {
			if (explosionPower > 0.0f) {
				serverWorld.createExplosion(this, getX(), getY(), getZ(), explosionPower, World.ExplosionSourceType.NONE);
			}
			impact(serverWorld, getKind(), getPos(), explosionPower);
			if (onImpact != null) {
				java.util.function.Consumer<HeroProjectileEntity> impact = onImpact;
				onImpact = null;
				impact.accept(this);
			}
			// Geschosse starker Aliens (Feuerbaelle, Raketenfinger) schlagen Krater
			if (getOwner() instanceof net.minecraft.server.network.ServerPlayerEntity owner) {
				float strength = com.santiq.kingdomomnitrix.power.AlienStrength.of(owner);
				if (strength > 0.0f && (explosionPower > 0.0f || getKind() == Kind.FIRE)) {
					double radius = 0.9 + explosionPower * 0.6;
					com.santiq.kingdomomnitrix.power.Destruction.crater(serverWorld, owner, getPos(), radius, strength, 0.0, 0.4);
					if (getKind() == Kind.FIRE) {
						com.santiq.kingdomomnitrix.power.Destruction.ignite(serverWorld, owner, getPos(), radius + 0.5, 0.25);
					}
				}
			}
			discard();
		}
	}

	/** Leuchtfarbe der Art fuer die FX-Engine (Kugel, Schweif, Aufprall). */
	public static int fxColor(Kind kind) {
		return switch (kind) {
			case FIRE -> 0xFF7A1A;
			case ICE -> 0x8AE8FF;
			case PLASMA -> 0x5AF0FF;
			case CRYSTAL -> 0x9AFFD8;
			case DARK -> 0x9A40FF;
			case SLIME -> 0x9AF040;
			case WRENCH, QUILL -> 0xFFFFFF;
		};
	}

	/** Aufprall je Art: Feuerball-Explosion mit Brandfleck, Eissplitter, Plasmablitz, Kristallsplitter, dunkler Rauch, Schleim. */
	private static void impact(ServerWorld world, Kind kind, Vec3d pos, float explosionPower) {
		int color = fxColor(kind);
		float boost = 1.0f + explosionPower * 0.5f;
		switch (kind) {
			case FIRE -> {
				com.santiq.kingdomomnitrix.vfx.Fx.explosion(world, pos, color, 0.9f * boost);
				com.santiq.kingdomomnitrix.vfx.Fx.scorch(world, pos.add(0, -0.4, 0), 0.9f * boost, 120);
			}
			case ICE -> {
				com.santiq.kingdomomnitrix.vfx.Fx.flash(world, pos, color, 1.4f * boost);
				com.santiq.kingdomomnitrix.vfx.Fx.shards(world, pos, color, 0.35f, 12);
			}
			case CRYSTAL -> {
				com.santiq.kingdomomnitrix.vfx.Fx.flash(world, pos, color, 1.0f * boost);
				com.santiq.kingdomomnitrix.vfx.Fx.shards(world, pos, color, 0.3f, 10);
			}
			case DARK -> {
				com.santiq.kingdomomnitrix.vfx.Fx.flash(world, pos, color, 1.2f * boost);
				com.santiq.kingdomomnitrix.vfx.Fx.smoke(world, pos, 0x2A1040, 0.7f, 6);
			}
			case SLIME -> {
				com.santiq.kingdomomnitrix.vfx.Fx.flash(world, pos, color, 0.9f);
				com.santiq.kingdomomnitrix.vfx.Fx.shards(world, pos, color, 0.25f, 10);
			}
			case PLASMA -> {
				com.santiq.kingdomomnitrix.vfx.Fx.flash(world, pos, color, 1.3f * boost);
				com.santiq.kingdomomnitrix.vfx.Fx.sparks(world, pos, color, 0.4f, 14);
			}
			case WRENCH, QUILL -> {
				Vfx.directed(world, ModParticles.HIT_SPARK, pos, Vec3d.ZERO);
				com.santiq.kingdomomnitrix.vfx.Fx.sparks(world, pos, 0xFFE8A0, 0.3f, 6);
			}
		}
		if (explosionPower > 0.0f) {
			com.santiq.kingdomomnitrix.vfx.Fx.shake(world, pos, 0.3f, 8, 12);
		}
	}
}
