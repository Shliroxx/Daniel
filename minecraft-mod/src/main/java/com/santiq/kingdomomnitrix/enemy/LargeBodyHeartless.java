package com.santiq.kingdomomnitrix.enemy;

import java.util.EnumSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Grosskoerper: Tank. Treffer von vorne prallen am Bauch ab (85 % weniger Schaden) — von hinten angreifen!
 * Rammt Ziele auf mittlere Distanz.
 */
public class LargeBodyHeartless extends HeartlessEntity {
	private static final float FRONT_REDUCTION = 0.85f;
	private static final double FRONT_DOT = 0.3;
	private static final int CHARGE_TICKS = 30;
	private static final int CHARGE_COOLDOWN = 120;

	private int chargeCooldown = 60;

	public LargeBodyHeartless(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8)
				.add(EntityAttributes.GENERIC_ARMOR, 4.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(1, new ChargeGoal());
		goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.add(5, new WanderAroundFarGoal(this, 0.7));
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		Vec3d from = source.getPosition();
		if (from != null && !source.isIn(DamageTypeTags.BYPASSES_SHIELD) && isFrontal(from)) {
			if (getWorld() instanceof ServerWorld world) {
				world.spawnParticles(ParticleTypes.CRIT, getX(), getBodyY(0.5), getZ(), 6, 0.4, 0.4, 0.4, 0.1);
				playSound(SoundEvents.ITEM_SHIELD_BLOCK, 0.8f, 0.6f);
			}
			amount *= 1.0f - FRONT_REDUCTION;
		}
		return super.damage(source, amount);
	}

	private boolean isFrontal(Vec3d from) {
		float yaw = getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d facing = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
		Vec3d toSource = new Vec3d(from.x - getX(), 0, from.z - getZ());
		return toSource.lengthSquared() > 1.0E-4 && toSource.normalize().dotProduct(facing) > FRONT_DOT;
	}

	@Override
	public void tick() {
		super.tick();
		if (chargeCooldown > 0) {
			chargeCooldown--;
		}
	}

	/** Rammt das Ziel in gerader Linie, wenn es 5–14 Bloecke entfernt ist. */
	private class ChargeGoal extends Goal {
		private int ticks;
		private Vec3d direction = Vec3d.ZERO;

		ChargeGoal() {
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = getTarget();
			if (target == null || chargeCooldown > 0 || !isOnGround()) {
				return false;
			}
			double distance = squaredDistanceTo(target);
			return distance > 25.0 && distance < 196.0 && canSee(target);
		}

		@Override
		public boolean shouldContinue() {
			return ticks > 0;
		}

		@Override
		public void start() {
			LivingEntity target = getTarget();
			ticks = CHARGE_TICKS;
			chargeCooldown = CHARGE_COOLDOWN;
			Vec3d delta = target.getPos().subtract(getPos());
			direction = new Vec3d(delta.x, 0, delta.z).normalize();
			triggerAnim(CONTROLLER, "special");
			playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 0.8f, 1.3f);
			getNavigation().stop();
		}

		@Override
		public void tick() {
			ticks--;
			setVelocity(direction.x * 0.55, getVelocity().y, direction.z * 0.55);
			getLookControl().lookAt(getX() + direction.x, getEyeY(), getZ() + direction.z);
			for (Entity hit : getWorld().getOtherEntities(LargeBodyHeartless.this, getBoundingBox().expand(0.3),
					e -> e instanceof LivingEntity && !(e instanceof HeartlessEntity))) {
				tryAttack(hit);
				((LivingEntity) hit).takeKnockback(1.6, -direction.x, -direction.z);
				ticks = 0;
			}
			if (horizontalCollision) {
				ticks = 0;
			}
		}
	}
}
