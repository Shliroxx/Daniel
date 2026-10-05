package com.santiq.kingdomomnitrix.enemy;

import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Dunkelball: schwebender Magiegegner. Schiesst Dunkelkugeln (Schwaeche) und teleportiert sich kurz weg. */
public class DarkballHeartless extends FlyingHeartlessEntity {
	private static final int SHOT_COOLDOWN = 50;
	private static final int BLINK_COOLDOWN = 140;

	private int shotCooldown = 40;
	private int blinkCooldown = 80;

	public DarkballHeartless(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 18.0)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.45)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new ShootGoal());
		goalSelector.add(2, new HoverAroundTargetGoal(2.5, 6.0, 0.8));
		goalSelector.add(5, new FlyWanderGoal());
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
	}

	@Override
	public void tick() {
		super.tick();
		if (shotCooldown > 0) {
			shotCooldown--;
		}
		if (blinkCooldown > 0) {
			blinkCooldown--;
		}
		if (!getWorld().isClient() && hurtTime == 9 && blinkCooldown <= 0) {
			blink();
		}
	}

	/** Kurzer Teleport nach einem Treffer. */
	private void blink() {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		blinkCooldown = BLINK_COOLDOWN;
		world.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getBodyY(0.5), getZ(), 20, 0.3, 0.3, 0.3, 0.1);
		for (int attempt = 0; attempt < 8; attempt++) {
			Vec3d destination = getPos().add(getRandom().nextGaussian() * 5, getRandom().nextDouble() * 3, getRandom().nextGaussian() * 5);
			if (world.isSpaceEmpty(this, getBoundingBox().offset(destination.subtract(getPos())))) {
				refreshPositionAfterTeleport(destination);
				break;
			}
		}
		playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
	}

	private class ShootGoal extends Goal {
		ShootGoal() {
			setControls(EnumSet.noneOf(Control.class));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = getTarget();
			return target != null && shotCooldown <= 0 && squaredDistanceTo(target) < 400.0 && canSee(target);
		}

		@Override
		public boolean shouldContinue() {
			return false;
		}

		@Override
		public void start() {
			LivingEntity target = getTarget();
			shotCooldown = SHOT_COOLDOWN;
			triggerAnim(CONTROLLER, "attack");
			HeroProjectileEntity.shootAt(getWorld(), DarkballHeartless.this, ModItems.DARK_ORB, target, 1.1f, 2.0f)
					.withDamage((float) getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
			playSound(SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 0.7f, 1.4f);
		}
	}
}
