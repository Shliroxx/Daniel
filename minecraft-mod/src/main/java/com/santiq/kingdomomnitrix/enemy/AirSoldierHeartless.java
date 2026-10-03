package com.santiq.kingdomomnitrix.enemy;

import java.util.EnumSet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Luftsoldat: kreist ueber dem Ziel und stoesst im Sturzflug herab. */
public class AirSoldierHeartless extends FlyingHeartlessEntity {
	private static final int SWOOP_COOLDOWN = 70;

	private int swoopCooldown = 40;

	public AirSoldierHeartless(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 14.0)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new SwoopGoal());
		goalSelector.add(2, new HoverAroundTargetGoal(3.0, 3.5, 1.0));
		goalSelector.add(5, new FlyWanderGoal());
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
	}

	@Override
	public void tick() {
		super.tick();
		if (swoopCooldown > 0) {
			swoopCooldown--;
		}
	}

	/** Sturzflug auf das Ziel; trifft es, gibt es Schaden, danach wieder hoch. */
	private class SwoopGoal extends Goal {
		private int ticks;

		SwoopGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = getTarget();
			return target != null && swoopCooldown <= 0 && squaredDistanceTo(target) < 144.0 && canSee(target);
		}

		@Override
		public boolean shouldContinue() {
			return ticks > 0 && getTarget() != null && getTarget().isAlive();
		}

		@Override
		public void start() {
			ticks = 30;
			swoopCooldown = SWOOP_COOLDOWN;
			triggerAnim(CONTROLLER, "special");
			playSound(SoundEvents.ENTITY_PHANTOM_SWOOP, 0.8f, 1.4f);
		}

		@Override
		public void tick() {
			ticks--;
			LivingEntity target = getTarget();
			Vec3d aim = target.getPos().add(0, target.getHeight() * 0.5, 0);
			getMoveControl().moveTo(aim.x, aim.y, aim.z, 1.8);
			if (getBoundingBox().expand(0.4).intersects(target.getBoundingBox())) {
				tryAttack(target);
				ticks = 0;
			}
		}
	}
}
