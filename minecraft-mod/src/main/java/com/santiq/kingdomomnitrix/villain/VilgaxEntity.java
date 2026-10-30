package com.santiq.kingdomomnitrix.villain;

import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Vilgax, Erzfeind aus Ben 10: will das Omnitrix. Drei Phasen (60 % und 25 % Leben):
 * <ul>
 * <li>Faustschlag aus der Naehe, Bodenstampfer mit Schockwelle, rote Augenstrahlen auf Distanz.</li>
 * <li>Ab Phase 2 Sprungangriff (landet mit Schockwelle) und Bioid-Drohnen als Verstaerkung.</li>
 * <li>Phase 3: schneller, kuerzere Pausen, Strahl laenger.</li>
 * </ul>
 * Jeder Phasenwechsel ist ein kurzer Brueller, waehrend dem er nicht getroffen werden kann.
 */
public class VilgaxEntity extends VillainBossEntity {
	public static final String DEFEATED_FLAG = "defeated_vilgax";
	private static final double BASE_HEALTH = 500.0;
	private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.15f, 0.1f), 1.4f);
	private static final DustParticleEffect WARN = new DustParticleEffect(new Vector3f(1.0f, 0.3f, 0.2f), 0.7f);
	private static final double BEAM_RANGE = 28.0;
	private static final int MAX_DRONES = 4;

	private enum Attack { NONE, PUNCH, SLAM, LEAP, BEAM, SUMMON, ROAR }

	private Attack attack = Attack.NONE;
	private int attackTicks;
	private int cooldown = 40;
	private int summonCooldown = 160;
	private Vec3d beamTarget = Vec3d.ZERO;
	private final List<LivingEntity> beamHits = new ArrayList<>();

	public VilgaxEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world, BossBar.Color.RED);
		this.experiencePoints = 300;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, BASE_HEALTH)
				.add(EntityAttributes.GENERIC_ARMOR, 10.0)
				.add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 4.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 14.0)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.5);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 32.0f));
	}

	@Override
	protected double baseHealth() {
		return BASE_HEALTH;
	}

	@Override
	protected float[] phaseFractions() {
		return new float[] {0.6f, 0.25f};
	}

	@Override
	protected String defeatedFlag() {
		return DEFEATED_FLAG;
	}

	@Override
	protected Text speaker() {
		return Text.translatable("entity.kingdomomnitrix.vilgax.short");
	}

	@Override
	protected Reward reward(boolean first) {
		return first
				? new Reward(1500, 600, List.of(new ItemStack(ModItems.ORICHALCUM, 3), new ItemStack(ModItems.RARITANIUM, 6)))
				: new Reward(500, 250, List.of(new ItemStack(ModItems.RARITANIUM, 3)));
	}

	@Override
	protected void onPhase(ServerWorld world, int phase) {
		start(Attack.ROAR);
		play("roar");
		bossBar.setColor(phase >= 3 ? BossBar.Color.PURPLE : BossBar.Color.YELLOW);
		bossBar.setName(Text.translatable(phase >= 3 ? "entity.kingdomomnitrix.vilgax.enraged" : "entity.kingdomomnitrix.vilgax.phase2"));
		say(phase >= 3 ? "boss.kingdomomnitrix.vilgax.enrage" : "boss.kingdomomnitrix.vilgax.phase2");
		sound(world, SoundEvents.ENTITY_RAVAGER_ROAR, 2.0f, 0.6f);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getBodyY(0.5), getZ(), 1, 0, 0, 0, 0);
		Fx.shake(world, getPos(), 0.5f, 20, 24);
		EntityAttributeInstance speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(phase >= 3 ? 0.34 : 0.3);
		}
	}

	private int pause() {
		return phase >= 3 ? 18 : phase == 2 ? 28 : 40;
	}

	private void start(Attack next) {
		attack = next;
		attackTicks = 0;
		getNavigation().stop();
	}

	private void finish() {
		attack = Attack.NONE;
		attackTicks = 0;
		cooldown = pause();
	}

	@Override
	protected void fightTick(ServerWorld world, @Nullable PlayerEntity target) {
		if (age == 40) {
			say("boss.kingdomomnitrix.vilgax.spawn");
		}
		if (target != null) {
			getLookControl().lookAt(target, 30.0f, 30.0f);
		}
		if (phase >= 2 && summonCooldown > 0) {
			summonCooldown--;
		}
		attackTicks++;
		switch (attack) {
			case NONE -> choose(world, target);
			case PUNCH -> tickPunch(world, target);
			case SLAM -> tickSlam(world);
			case LEAP -> tickLeap(world, target);
			case BEAM -> tickBeam(world, target);
			case SUMMON -> {
				if (attackTicks == 15) {
					summonDrones(world);
				}
				if (attackTicks >= 30) {
					finish();
				}
			}
			case ROAR -> {
				if (attackTicks % 4 == 0) {
					ring(world, getPos(), 1.5 + attackTicks * 0.15, ParticleTypes.CLOUD);
				}
				if (attackTicks >= 32) {
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
		if (distance > 3.0) {
			getNavigation().startMovingTo(target, 1.0);
		} else {
			getNavigation().stop();
		}
		if (--cooldown > 0) {
			return;
		}
		if (phase >= 2 && summonCooldown <= 0 && drones(world) < MAX_DRONES) {
			summonCooldown = phase >= 3 ? 300 : 450;
			start(Attack.SUMMON);
			play("roar");
			say("boss.kingdomomnitrix.vilgax.drones");
			sound(world, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 0.7f);
			return;
		}
		if (distance < 3.6) {
			start(Attack.PUNCH);
			play("attack");
		} else if (distance < 7.0) {
			start(Attack.SLAM);
			play("slam");
			sound(world, SoundEvents.ENTITY_RAVAGER_ATTACK, 1.2f, 0.6f);
		} else if (phase >= 2 && distance < 20 && random.nextBoolean()) {
			start(Attack.LEAP);
			play("slam");
			Vec3d jump = target.getPos().subtract(getPos()).multiply(0.11, 0, 0.11);
			setVelocity(jump.x, 1.0, jump.z);
			velocityModified = true;
			sound(world, SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 1.0f);
		} else {
			start(Attack.BEAM);
			play("shoot");
			beamTarget = target.getEyePos();
			beamHits.clear();
			sound(world, ModSounds.BOSS_LASER_CHARGE, 1.3f, 0.8f);
		}
	}

	private void tickPunch(ServerWorld world, @Nullable PlayerEntity target) {
		if (attackTicks == 6 && target != null && distanceTo(target) < 4.2 && canHit(target)) {
			if (target.damage(getDamageSources().mobAttack(this), scaled(14.0f))) {
				Vec3d push = target.getPos().subtract(getPos()).multiply(1, 0, 1).normalize().multiply(1.05);
				target.addVelocity(push.x, 0.38, push.z);
				target.velocityModified = true;
			}
			sound(world, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.6f);
		}
		if (attackTicks >= 16) {
			finish();
		}
	}

	private void tickSlam(ServerWorld world) {
		if (attackTicks == 12) {
			slamAt(world, getPos(), 6.0, 11.0f);
		}
		if (attackTicks >= 22) {
			finish();
		}
	}

	private void slamAt(ServerWorld world, Vec3d center, double radius, float damage) {
		shockwave(world, center, radius, scaled(damage), 0.85, 0.42);
		for (double r = 1.0; r <= radius; r += 1.5) {
			ring(world, center, r, ParticleTypes.POOF);
		}
		world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, 3, 1.0, 0.1, 1.0, 0.0);
		Fx.shake(world, center, 0.45f, 12, 18);
		sound(world, ModSounds.BOSS_STOMP, 1.8f, 0.7f);
	}

	private void tickLeap(ServerWorld world, @Nullable PlayerEntity target) {
		if (attackTicks > 6 && isOnGround()) {
			slamAt(world, getPos(), 7.0, 13.0f);
			finish();
			return;
		}
		if (attackTicks > 6 && target != null && attackTicks < 30) {
			// in der Luft leicht nachsteuern
			Vec3d toward = target.getPos().subtract(getPos()).multiply(1, 0, 1);
			if (toward.lengthSquared() > 1.0) {
				Vec3d steer = toward.normalize().multiply(0.04);
				addVelocity(steer.x, 0.0, steer.z);
			}
		}
		if (attackTicks > 60) {
			finish();
		}
	}

	private Vec3d eyes() {
		return getPos().add(0, getHeight() * 0.86, 0).add(getRotationVector().multiply(0.6));
	}

	private void tickBeam(ServerWorld world, @Nullable PlayerEntity target) {
		int charge = phase >= 3 ? 18 : 26;
		int length = phase >= 3 ? 30 : 20;
		if (attackTicks < charge - 6 && target != null) {
			beamTarget = target.getEyePos();
		}
		Vec3d from = eyes();
		Vec3d to = beamEnd(world, from, beamTarget, BEAM_RANGE);
		if (attackTicks < charge) {
			if (attackTicks % 2 == 0) {
				line(world, from, to, WARN, 0.8);
			}
			return;
		}
		if (attackTicks == charge) {
			sound(world, ModSounds.BOSS_LASER_FIRE, 1.6f, 0.7f);
		}
		line(world, from, to, RED, 0.3);
		world.spawnParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 3, 0.2, 0.2, 0.2, 0.02);
		if ((attackTicks - charge) % 5 == 0) {
			beamHits.clear(); // alle 5 Ticks darf der Strahl erneut treffen
		}
		beamDamage(world, from, to, 0.6, scaled(6.0f), beamHits);
		if (attackTicks >= charge + length) {
			finish();
		}
	}

	private int drones(ServerWorld world) {
		return world.getEntitiesByClass(VilgaxDroneEntity.class, getBoundingBox().expand(40), LivingEntity::isAlive).size();
	}

	private void summonDrones(ServerWorld world) {
		int count = phase >= 3 ? 3 : 2;
		for (int i = 0; i < count; i++) {
			VilgaxDroneEntity drone = ModEntities.VILGAX_DRONE.create(world);
			if (drone == null) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2;
			BlockPos at = BlockPos.ofFloored(getX() + Math.cos(angle) * 3, getY() + 3, getZ() + Math.sin(angle) * 3);
			drone.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
			drone.initialize(world, world.getLocalDifficulty(at), SpawnReason.MOB_SUMMONED, null);
			drone.setTarget(getTarget());
			world.spawnEntity(drone);
			world.spawnParticles(ParticleTypes.PORTAL, drone.getX(), drone.getY(), drone.getZ(), 20, 0.4, 0.4, 0.4, 0.4);
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (attack == Attack.ROAR && !source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL)
				&& !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		if (getWorld() instanceof ServerWorld world) {
			say("boss.kingdomomnitrix.vilgax.defeat");
			world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getBodyY(0.5), getZ(), 3, 1.0, 1.0, 1.0, 0);
			for (VilgaxDroneEntity drone : world.getEntitiesByClass(VilgaxDroneEntity.class, getBoundingBox().expand(48), LivingEntity::isAlive)) {
				drone.kill();
			}
		}
		super.onDeath(damageSource);
	}

	@Override
	public boolean isPushable() {
		return false;
	}
}
