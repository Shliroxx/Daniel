package com.santiq.kingdomomnitrix.enemy;

import java.util.EnumSet;
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
import net.minecraft.world.World;

/**
 * Schatten: klein, schnell, taucht als Schatten in den Boden ab (unsichtbar, unverwundbar, schneller)
 * und springt am Ziel wieder heraus.
 */
public class ShadowHeartless extends HeartlessEntity {
	private static final int SINK_TICKS = 40;
	private static final int SINK_COOLDOWN = 120;

	private int sunkTicks;
	private int sinkCooldown = 60;

	public ShadowHeartless(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 12.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(1, new SinkGoal());
		goalSelector.add(2, new MeleeAttackGoal(this, 1.2, false));
		goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	public boolean isSunk() {
		return sunkTicks > 0;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (isSunk() && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld() instanceof ServerWorld world) {
			if (sinkCooldown > 0) {
				sinkCooldown--;
			}
			if (sunkTicks > 0) {
				sunkTicks--;
				if (age % 3 == 0) {
					world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.05, getZ(), 3, 0.25, 0.0, 0.25, 0.0);
				}
				if (sunkTicks == 0) {
					emerge(world);
				}
			}
		}
	}

	private void sink() {
		sunkTicks = SINK_TICKS;
		sinkCooldown = SINK_COOLDOWN;
		setInvisible(true);
		playSound(SoundEvents.BLOCK_SCULK_SPREAD, 0.8f, 0.7f);
	}

	private void emerge(ServerWorld world) {
		setInvisible(false);
		triggerAnim(CONTROLLER, "emerge");
		world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.1, getZ(), 15, 0.3, 0.05, 0.3, 0.05);
		LivingEntity target = getTarget();
		if (target != null && squaredDistanceTo(target) < 9.0) {
			setVelocity(getVelocity().add(0, 0.45, 0));
			tryAttack(target);
		}
	}

	/** Taucht ab, wenn ein Ziel 4–16 Bloecke entfernt ist, und rast unsichtbar darauf zu. */
	private class SinkGoal extends Goal {
		SinkGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = getTarget();
			if (target == null || sinkCooldown > 0 || isSunk()) {
				return false;
			}
			double distance = squaredDistanceTo(target);
			return distance > 16.0 && distance < 256.0;
		}

		@Override
		public boolean shouldContinue() {
			return isSunk() && getTarget() != null;
		}

		@Override
		public void start() {
			sink();
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target != null) {
				getNavigation().startMovingTo(target, 1.8);
				if (squaredDistanceTo(target) < 4.0) {
					sunkTicks = 1; // im naechsten Tick auftauchen und zuschlagen
				}
			}
		}
	}
}
