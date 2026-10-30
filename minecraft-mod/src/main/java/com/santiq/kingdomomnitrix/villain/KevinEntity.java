package com.santiq.kingdomomnitrix.villain;

import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Kevin 11 (Ben 10): Mini-Boss mit Stahl-Arm. Schlaegt mit der Keulenfaust zu, wirft Energiesalven und rennt das Ziel
 * um. Trifft ihn ein verwandelter Spieler, absorbiert Kevin die Alien-Energie (Staerke und Widerstand fuer kurze Zeit,
 * hoechstens alle 15 Sekunden); ab Phase 2 (50 % Leben) wird er schneller und wirft dreifach.
 */
public class KevinEntity extends VillainBossEntity {
	public static final String DEFEATED_FLAG = "defeated_kevin";
	private static final double BASE_HEALTH = 180.0;
	private static final int ABSORB_COOLDOWN = 300;

	private enum Attack { NONE, PUNCH, BLAST, CHARGE }

	private Attack attack = Attack.NONE;
	private int attackTicks;
	private int cooldown = 30;
	private int absorbCooldown;

	public KevinEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world, BossBar.Color.PURPLE);
		this.experiencePoints = 120;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, BASE_HEALTH)
				.add(EntityAttributes.GENERIC_ARMOR, 8.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.7)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 9.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
	}

	@Override
	protected double baseHealth() {
		return BASE_HEALTH;
	}

	@Override
	protected float[] phaseFractions() {
		return new float[] {0.5f};
	}

	@Override
	protected String defeatedFlag() {
		return DEFEATED_FLAG;
	}

	@Override
	protected Text speaker() {
		return Text.translatable("entity.kingdomomnitrix.kevin.short");
	}

	@Override
	protected Reward reward(boolean first) {
		return first
				? new Reward(600, 250, List.of(new ItemStack(ModItems.RARITANIUM, 3), new ItemStack(ModItems.ORICHALCUM, 1)))
				: new Reward(200, 100, List.of(new ItemStack(ModItems.RARITANIUM, 1)));
	}

	@Override
	protected void onPhase(ServerWorld world, int phase) {
		play("roar");
		bossBar.setColor(BossBar.Color.RED);
		say("boss.kingdomomnitrix.kevin.phase2");
		sound(world, SoundEvents.ENTITY_PILLAGER_CELEBRATE, 1.4f, 0.6f);
		var speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(0.36);
		}
	}

	@Override
	protected void fightTick(ServerWorld world, @Nullable PlayerEntity target) {
		if (age == 30) {
			say("boss.kingdomomnitrix.kevin.spawn");
		}
		if (absorbCooldown > 0) {
			absorbCooldown--;
		}
		if (target != null) {
			getLookControl().lookAt(target, 30.0f, 30.0f);
		}
		attackTicks++;
		switch (attack) {
			case NONE -> choose(world, target);
			case PUNCH -> {
				if (attackTicks == 5 && target != null && distanceTo(target) < 3.6 && canHit(target)) {
					if (target.damage(getDamageSources().mobAttack(this), scaled(10.0f))) {
						Vec3d push = target.getPos().subtract(getPos()).multiply(1, 0, 1).normalize().multiply(1.1);
						target.addVelocity(push.x, 0.35, push.z);
						target.velocityModified = true;
					}
					sound(world, SoundEvents.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
				}
				if (attackTicks >= 14) {
					finish();
				}
			}
			case BLAST -> {
				int shots = phase >= 2 ? 3 : 1;
				if (target != null && attackTicks >= 8 && attackTicks < 8 + shots * 6 && (attackTicks - 8) % 6 == 0) {
					HeroProjectileEntity.shootAt(world, this, ModItems.DARK_ORB, target, 1.5f, 3.0f).withDamage(scaled(6.0f));
					sound(world, SoundEvents.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.5f);
				}
				if (attackTicks >= 10 + shots * 6) {
					finish();
				}
			}
			case CHARGE -> {
				if (target != null && attackTicks < 18) {
					Vec3d toward = target.getPos().subtract(getPos()).multiply(1, 0, 1);
					if (toward.lengthSquared() > 1.0) {
						Vec3d dash = toward.normalize().multiply(0.65);
						setVelocity(dash.x, getVelocity().y, dash.z);
						velocityModified = true;
					}
					if (distanceTo(target) < 1.8 && canHit(target)) {
						target.damage(getDamageSources().mobAttack(this), scaled(8.0f));
						target.addVelocity(getVelocity().x * 1.5, 0.5, getVelocity().z * 1.5);
						target.velocityModified = true;
						attackTicks = 18;
					}
					world.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 2, 0.2, 0.0, 0.2, 0.0);
				}
				if (attackTicks >= 24) {
					finish();
				}
			}
		}
	}

	private void choose(ServerWorld world, @Nullable PlayerEntity target) {
		if (target == null) {
			return;
		}
		double distance = distanceTo(target);
		if (distance > 2.5) {
			getNavigation().startMovingTo(target, 1.0);
		}
		if (--cooldown > 0) {
			return;
		}
		if (distance < 3.2) {
			start(Attack.PUNCH);
			play("attack");
		} else if (distance > 8.0 && random.nextInt(3) == 0) {
			start(Attack.CHARGE);
			play("roar");
			say("boss.kingdomomnitrix.kevin.charge");
			sound(world, SoundEvents.ENTITY_RAVAGER_ROAR, 0.8f, 1.5f);
		} else if (distance > 4.0) {
			start(Attack.BLAST);
			play("shoot");
		}
	}

	private void start(Attack next) {
		attack = next;
		attackTicks = 0;
		getNavigation().stop();
	}

	private void finish() {
		attack = Attack.NONE;
		attackTicks = 0;
		cooldown = phase >= 2 ? 16 : 26;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (hurt && absorbCooldown <= 0 && source.getAttacker() instanceof ServerPlayerEntity player
				&& TransformationManager.get(player).isTransformed() && getWorld() instanceof ServerWorld world) {
			absorbCooldown = ABSORB_COOLDOWN;
			addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 1));
			addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1));
			world.spawnParticles(ParticleTypes.WITCH, getX(), getBodyY(0.5), getZ(), 30, 0.5, 0.8, 0.5, 0.1);
			Vec3d from = player.getPos().add(0, player.getHeight() * 0.5, 0);
			line(world, from, getPos().add(0, getHeight() * 0.5, 0), ParticleTypes.ENCHANTED_HIT, 0.4);
			sound(world, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.6f);
			say("boss.kingdomomnitrix.kevin.absorb");
		}
		return hurt;
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		say("boss.kingdomomnitrix.kevin.defeat");
		super.onDeath(damageSource);
	}
}
