package com.daniel.heroverse.item;

import com.daniel.heroverse.entity.HeroProjectileEntity;
import com.daniel.heroverse.registry.ModEffects;
import com.daniel.heroverse.registry.ModItems;
import com.daniel.heroverse.util.Targeting;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/** Die Aliens im Omnitrix, jeweils mit eigener Spezialfaehigkeit. */
public enum Alien {
	HEATBLAST("heatblast", Formatting.GOLD, 15),
	XLR8("xlr8", Formatting.AQUA, 25),
	FOUR_ARMS("four_arms", Formatting.RED, 60),
	DIAMONDHEAD("diamondhead", Formatting.GREEN, 30),
	GREY_MATTER("grey_matter", Formatting.GRAY, 20);

	private static final double SMASH_RADIUS = 5.0;
	private static final float SMASH_DAMAGE = 9.0f;
	private static final double DASH_SPEED = 2.4;
	private static final double SCAN_RANGE = 24.0;

	private final String id;
	private final Formatting color;
	private final int abilityCooldownTicks;

	Alien(String id, Formatting color, int abilityCooldownTicks) {
		this.id = id;
		this.color = color;
		this.abilityCooldownTicks = abilityCooldownTicks;
	}

	public String id() {
		return id;
	}

	public int abilityCooldownTicks() {
		return abilityCooldownTicks;
	}

	public RegistryEntry<StatusEffect> effect() {
		return ModEffects.forAlien(this);
	}

	public Text displayName() {
		return Text.translatable("alien.heroverse." + id).formatted(color, Formatting.BOLD);
	}

	public Alien next() {
		Alien[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static Alien byIndex(int index) {
		Alien[] all = values();
		return all[Math.floorMod(index, all.length)];
	}

	/** Die Alien-Form, in der das Lebewesen gerade steckt, oder null. */
	public static Alien activeOn(LivingEntity entity) {
		for (Alien alien : values()) {
			if (entity.hasStatusEffect(alien.effect())) {
				return alien;
			}
		}
		return null;
	}

	/** Spezialfaehigkeit der Alien-Form. Liefert false, wenn nichts passiert ist. */
	public boolean useAbility(ServerWorld world, PlayerEntity user) {
		return switch (this) {
			case HEATBLAST -> {
				HeroProjectileEntity.shoot(world, user, ModItems.FIRE_ORB, 2.2f, 0.0f);
				world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.8f);
				yield true;
			}
			case XLR8 -> {
				Vec3d look = user.getRotationVec(1.0f);
				user.setVelocity(look.x * DASH_SPEED, Math.max(look.y * DASH_SPEED * 0.5, 0.25), look.z * DASH_SPEED);
				user.velocityModified = true;
				user.fallDistance = 0.0f;
				world.spawnParticles(ParticleTypes.CLOUD, user.getX(), user.getY() + 0.2, user.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
				world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.6f);
				yield true;
			}
			case FOUR_ARMS -> {
				for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, user.getBoundingBox().expand(SMASH_RADIUS),
						e -> e != user && e.isAlive() && e.squaredDistanceTo(user) <= SMASH_RADIUS * SMASH_RADIUS)) {
					target.damage(world.getDamageSources().playerAttack(user), SMASH_DAMAGE);
					Vec3d push = target.getPos().subtract(user.getPos());
					if (push.horizontalLengthSquared() > 1.0E-4) {
						target.takeKnockback(1.4, -push.x, -push.z);
					}
					target.addVelocity(0.0, 0.5, 0.0);
				}
				world.spawnParticles(ParticleTypes.EXPLOSION, user.getX(), user.getY(), user.getZ(), 6, 2.0, 0.2, 2.0, 0.0);
				world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.2f, 0.6f);
				yield true;
			}
			case DIAMONDHEAD -> {
				for (int i = 0; i < 5; i++) {
					HeroProjectileEntity.shoot(world, user, ModItems.CRYSTAL_SHARD, 2.0f, 6.0f);
				}
				world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.0f, 1.2f);
				yield true;
			}
			case GREY_MATTER -> scan(user);
		};
	}

	private static boolean scan(PlayerEntity user) {
		Optional<LivingEntity> target = Targeting.findLivingTarget(user, SCAN_RANGE);
		if (target.isEmpty()) {
			user.sendMessage(Text.translatable("message.heroverse.scan_none").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity entity = target.get();
		user.sendMessage(Text.translatable("message.heroverse.scan",
				entity.getDisplayName(),
				String.format("%.1f", entity.getHealth()),
				String.format("%.1f", entity.getMaxHealth()),
				String.format("%.0f", entity.getAttributeValue(EntityAttributes.GENERIC_ARMOR)),
				String.format("%.1f", attackDamage(entity))
		).formatted(Formatting.GRAY), false);
		return true;
	}

	private static double attackDamage(LivingEntity entity) {
		return entity.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE)
				? entity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
				: 0.0;
	}
}
