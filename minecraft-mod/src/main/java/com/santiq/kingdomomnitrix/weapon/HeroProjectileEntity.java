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
			if (age > TRAIL_DELAY_TICKS && (getOwner() == null || getOwner().squaredDistanceTo(this) > TRAIL_MIN_DISTANCE_SQ)) {
				world.addParticle(getKind().particle, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
			}
		} else if (age > MAX_AGE_TICKS) {
			discard();
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
		target.damage(source, damageOverride >= 0.0f ? damageOverride : kind.damage);
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

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (getWorld() instanceof ServerWorld serverWorld) {
			if (explosionPower > 0.0f) {
				serverWorld.createExplosion(this, getX(), getY(), getZ(), explosionPower, World.ExplosionSourceType.NONE);
			}
			serverWorld.spawnParticles(getKind().particle, getX(), getY(), getZ(), 8, 0.15, 0.15, 0.15, 0.05);
			Vfx.directed(serverWorld, ModParticles.HIT_SPARK, getPos(), Vec3d.ZERO);
			discard();
		}
	}
}
