package com.santiq.kingdomomnitrix.enemy;

import java.util.EnumSet;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Basis fliegender Herzloser: schwebt, kein Fallschaden, kreist ueber dem Ziel. */
public abstract class FlyingHeartlessEntity extends HeartlessEntity {
	protected FlyingHeartlessEntity(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
		this.moveControl = new FlightMoveControl(this, 20, true);
		setNoGravity(true);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation navigation = new BirdNavigation(this, world);
		navigation.setCanPathThroughDoors(false);
		navigation.setCanSwim(true);
		navigation.setCanEnterOpenDoors(true);
		return navigation;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	/** Haelt Abstand: schwebt {@code height} Bloecke ueber dem Ziel und kreist darum. */
	protected class HoverAroundTargetGoal extends Goal {
		private final double height;
		private final double radius;
		private final double speed;
		private float angle;

		protected HoverAroundTargetGoal(double height, double radius, double speed) {
			this.height = height;
			this.radius = radius;
			this.speed = speed;
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return getTarget() != null && getTarget().isAlive();
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target == null) {
				return;
			}
			angle += 0.06f;
			Vec3d goal = target.getPos().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
			getMoveControl().moveTo(goal.x, goal.y, goal.z, speed);
			getLookControl().lookAt(target, 30.0f, 30.0f);
		}
	}

	/** Schwebt ohne Ziel zu zufaelligen Punkten in der Naehe. */
	protected class FlyWanderGoal extends Goal {
		FlyWanderGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return getTarget() == null && getNavigation().isIdle() && getRandom().nextInt(40) == 0;
		}

		@Override
		public boolean shouldContinue() {
			return false;
		}

		@Override
		public void start() {
			Vec3d goal = getPos().add(getRandom().nextGaussian() * 6, getRandom().nextGaussian() * 2, getRandom().nextGaussian() * 6);
			getMoveControl().moveTo(goal.x, Math.max(getWorld().getBottomY() + 2, goal.y), goal.z, 0.6);
		}
	}
}
