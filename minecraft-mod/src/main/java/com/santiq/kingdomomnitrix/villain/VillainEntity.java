package com.santiq.kingdomomnitrix.villain;

import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Basis der Ben-10-Schurken: GeckoLib-Modell unter entity/villain/&lt;name&gt; mit idle/walk und den ausloesbaren
 * Angriffen attack, shoot, slam und roar (tools/generate_villain_models.py), dazu Hilfen fuer Strahlen, Schockwellen
 * und Flaechenschaden; Schurken treffen sich nicht gegenseitig.
 */
public abstract class VillainEntity extends HostileEntity implements GeoEntity {
	public static final String CONTROLLER = "main";
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	protected VillainEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	protected void play(String animation) {
		triggerAnim(CONTROLLER, animation);
	}

	protected void sound(ServerWorld world, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, getX(), getY(), getZ(), sound, SoundCategory.HOSTILE, volume, pitch);
	}

	/** Darf dieser Schurke das Ziel treffen? (Keine Schurken untereinander; Spieler immer.) */
	protected boolean canHit(LivingEntity target) {
		return target != this && target.isAlive() && !(target instanceof VillainEntity) && !(target instanceof PlayerEntity player && player.isCreative())
				&& !target.isSpectator();
	}

	/** Schaden im Umkreis mit Rueckstoss nach aussen (und oben); liefert die Zahl der Getroffenen. */
	protected int shockwave(ServerWorld world, Vec3d center, double radius, float damage, double knockback, double lift) {
		int hits = 0;
		DamageSource source = getDamageSources().mobAttack(this);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(radius, 2.5, radius), this::canHit)) {
			Vec3d away = target.getPos().subtract(center).multiply(1, 0, 1);
			if (away.length() > radius) {
				continue;
			}
			if (target.damage(source, damage)) {
				hits++;
			}
			Vec3d push = away.lengthSquared() < 1.0e-4 ? Vec3d.ZERO : away.normalize().multiply(knockback);
			target.addVelocity(push.x, lift, push.z);
			target.velocityModified = true;
			if (target instanceof ServerPlayerEntity player) {
				player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
			}
		}
		return hits;
	}

	/** Endpunkt eines Strahls: bis zum ersten festen Block, hoechstens {@code range}. */
	protected Vec3d beamEnd(ServerWorld world, Vec3d from, Vec3d aim, double range) {
		Vec3d direction = aim.subtract(from).normalize();
		Vec3d to = from.add(direction.multiply(range));
		BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getPos() : to;
	}

	/** Strahlschaden: alle Ziele, deren Koerper dem Strahl naeher als {@code width} kommt. */
	protected void beamDamage(ServerWorld world, Vec3d from, Vec3d to, double width, float damage, List<LivingEntity> alreadyHit) {
		Box area = new Box(from, to).expand(width + 1.0);
		DamageSource source = getDamageSources().mobAttack(this);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, this::canHit)) {
			if (alreadyHit.contains(target)) {
				continue;
			}
			Vec3d center = target.getPos().add(0, target.getHeight() * 0.5, 0);
			if (distanceToSegment(center, from, to) <= width + target.getWidth() * 0.5) {
				target.damage(source, damage);
				target.setOnFireFor(2.0f);
				alreadyHit.add(target);
			}
		}
	}

	protected static double distanceToSegment(Vec3d point, Vec3d a, Vec3d b) {
		Vec3d ab = b.subtract(a);
		double t = ab.lengthSquared() < 1.0e-6 ? 0.0 : Math.max(0.0, Math.min(1.0, point.subtract(a).dotProduct(ab) / ab.lengthSquared()));
		return point.distanceTo(a.add(ab.multiply(t)));
	}

	protected static void line(ServerWorld world, Vec3d from, Vec3d to, ParticleEffect effect, double step) {
		Vec3d delta = to.subtract(from);
		int steps = (int) Math.ceil(delta.length() / step);
		for (int i = 0; i <= steps; i++) {
			Vec3d point = from.add(delta.multiply((double) i / Math.max(1, steps)));
			world.spawnParticles(effect, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	protected static void ring(ServerWorld world, Vec3d center, double radius, ParticleEffect effect) {
		int points = (int) Math.max(12, radius * 6);
		for (int i = 0; i < points; i++) {
			double angle = i * Math.PI * 2 / points;
			world.spawnParticles(effect, center.x + Math.cos(angle) * radius, center.y + 0.15, center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, CONTROLLER, 4, state -> state.setAndContinue(state.isMoving() ? WALK : IDLE))
				.triggerableAnim("attack", RawAnimation.begin().thenPlay("attack"))
				.triggerableAnim("shoot", RawAnimation.begin().thenPlay("shoot"))
				.triggerableAnim("slam", RawAnimation.begin().thenPlay("slam"))
				.triggerableAnim("roar", RawAnimation.begin().thenPlay("roar")));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
