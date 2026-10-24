package com.santiq.kingdomomnitrix.villain;

import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.EnumSet;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Bioid-Drohne von Vilgax: schwebt kreisend ueber dem Ziel und feuert rote Plasma-Schuesse. Wird von Vilgax gerufen
 * (ab Phase 2); faellt beim Tod funkensprühend auseinander.
 */
public class VilgaxDroneEntity extends VillainEntity {
	private static final int SHOT_INTERVAL = 45;

	public VilgaxDroneEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.moveControl = new FlightMoveControl(this, 20, true);
		this.experiencePoints = 6;
		setNoGravity(true);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 14.0)
				.add(EntityAttributes.GENERIC_ARMOR, 4.0)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation navigation = new BirdNavigation(this, world);
		navigation.setCanPathThroughDoors(false);
		navigation.setCanSwim(true);
		return navigation;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(2, new HoverShootGoal());
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this, VillainEntity.class));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getBodyY(0.5), getZ(), 20, 0.3, 0.3, 0.3, 0.3);
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getBodyY(0.5), getZ(), 6, 0.2, 0.2, 0.2, 0.02);
			sound(world, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.6f, 1.6f);
		}
	}

	/** Kreist 3,5 Bloecke ueber dem Ziel und schiesst im Takt. */
	private class HoverShootGoal extends Goal {
		private float angle;
		private int shotTimer = 20;

		HoverShootGoal() {
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target == null) {
				return;
			}
			angle += 0.05f;
			Vec3d goal = target.getPos().add(Math.cos(angle) * 4.0, 3.5, Math.sin(angle) * 4.0);
			getMoveControl().moveTo(goal.x, goal.y, goal.z, 1.0);
			getLookControl().lookAt(target, 30.0f, 30.0f);
			if (--shotTimer <= 0 && canSee(target) && getWorld() instanceof ServerWorld world) {
				shotTimer = SHOT_INTERVAL + random.nextInt(15);
				play("shoot");
				HeroProjectileEntity.shootAt(world, VilgaxDroneEntity.this, ModItems.PLASMA_SHOT, target, 1.4f, 2.0f).withDamage(4.0f);
				sound(world, SoundEvents.ENTITY_BLAZE_SHOOT, 0.5f, 1.8f);
			}
		}
	}
}
