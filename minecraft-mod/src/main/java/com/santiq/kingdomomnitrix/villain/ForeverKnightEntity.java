package com.santiq.kingdomomnitrix.villain;

import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.EnumSet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * Forever Knight (Ben 10): Ritter in Ruestung mit Laser-Lanze. Kaempft im Nahkampf mit der Lanze und feuert auf
 * mittlere Distanz rote Laserstoesse; tritt nachts in der Oberwelt in kleinen Trupps auf.
 */
public class ForeverKnightEntity extends VillainEntity {
	private static final int LANCE_INTERVAL = 70;

	public ForeverKnightEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 12;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 36.0)
				.add(EntityAttributes.GENERIC_ARMOR, 10.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.4)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(2, new LanceShotGoal());
		goalSelector.add(3, new MeleeAttackGoal(this, 1.1, false));
		goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
		goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
		goalSelector.add(9, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this, VillainEntity.class));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit) {
			play("attack");
		}
		return hit;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_IRON_GOLEM_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
		return SoundEvents.ITEM_ARMOR_EQUIP_IRON.value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
	}

	/** Laserstoss der Lanze auf 6 bis 18 Bloecke, nur mit freier Sicht. */
	private class LanceShotGoal extends Goal {
		private int timer = 40;

		LanceShotGoal() {
			setControls(EnumSet.of(Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = getTarget();
			if (target == null || !target.isAlive()) {
				return false;
			}
			double distance = distanceTo(target);
			return distance > 6.0 && distance < 18.0;
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target == null) {
				return;
			}
			getLookControl().lookAt(target, 30.0f, 30.0f);
			if (--timer <= 0 && canSee(target) && getWorld() instanceof ServerWorld world) {
				timer = LANCE_INTERVAL + random.nextInt(20);
				play("shoot");
				HeroProjectileEntity.shootAt(world, ForeverKnightEntity.this, ModItems.FIRE_ORB, target, 1.6f, 1.5f).withDamage(5.0f);
				sound(world, SoundEvents.ENTITY_BLAZE_SHOOT, 0.7f, 1.3f);
			}
		}
	}
}
