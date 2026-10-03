package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Cannonbolt (Arburian Pelarota): rollt sich zur Kanonenkugel zusammen. Eigenes Dauer-System „Rollen“: aktive Rollen
 * laufen hier tickweise (Geschwindigkeit, Treffer unterwegs, Landung), sonst kostet nichts Rechenzeit — der Tick kehrt
 * bei leerer Liste sofort zurueck. Die Kugel-Darstellung leitet der Client aus {@code ball_ticks} der Faehigkeit ab.
 *
 * <ol>
 *   <li>{@code cannonball} — Rollangriff geradeaus, trifft alles im Weg; an einer Wand: Aufprall-Stoss</li>
 *   <li>{@code shell_guard} — Panzerkugel: stark verringerter Schaden, kein Rueckstoss, Geschosse prallen ab</li>
 *   <li>{@code ball_bounce} — Kugelsprung, Landung mit Druckwelle</li>
 *   <li>{@code ricochet} (★3) — springt nacheinander zwischen bis zu 4 Gegnern hin und her</li>
 *   <li>{@code rolling_mode} (★5) — Rollmodus: schnelle Kugel, steigt Stufen hoch, rammt Gegner unterwegs</li>
 *   <li>{@code cannonade} (★8) — Kanonade: hoch hinauf, Sturzflug, gewaltige Druckwelle</li>
 * </ol>
 */
final class CannonboltAbilities {
	private enum Kind { DASH, BOUNCE, RICOCHET, CRUISE, CANNONADE }

	/** Laufende Rolle eines Spielers. */
	private static final class Roll {
		final Kind kind;
		final float damage;
		final double speed;
		final double radius;
		final long until;
		Vec3d direction;
		final Set<UUID> hit = new HashSet<>();
		final Map<UUID, Long> lastHit = new HashMap<>();
		final List<LivingEntity> targets = new ArrayList<>();
		long segmentStart;
		boolean airborne;
		boolean diving;

		Roll(Kind kind, float damage, double speed, double radius, long until, Vec3d direction) {
			this.kind = kind;
			this.damage = damage;
			this.speed = speed;
			this.radius = radius;
			this.until = until;
			this.direction = direction;
		}
	}

	private static final Map<UUID, Roll> ROLLS = new HashMap<>();
	private static final Identifier ROLL_STEP = KingdomOmnitrix.id("cannonbolt_roll_step");
	private static final Identifier ROLL_KNOCKBACK = KingdomOmnitrix.id("cannonbolt_roll_knockback");
	/** Rollmodus: dieselben Gegner hoechstens alle 10 Ticks rammen */
	private static final int CRUISE_REHIT = 10;
	/** Rollmodus: ab dieser Geschwindigkeit (Bloecke/Tick) rammt die Kugel */
	private static final double CRUISE_RAM_SPEED = 0.28;

	private CannonboltAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("cannonball"), CannonboltAbilities::cannonball);
		AbilityRegistry.register(KingdomOmnitrix.id("shell_guard"), CannonboltAbilities::shellGuard);
		AbilityRegistry.register(KingdomOmnitrix.id("ball_bounce"), CannonboltAbilities::ballBounce);
		AbilityRegistry.register(KingdomOmnitrix.id("ricochet"), CannonboltAbilities::ricochet);
		AbilityRegistry.register(KingdomOmnitrix.id("rolling_mode"), CannonboltAbilities::rollingMode);
		AbilityRegistry.register(KingdomOmnitrix.id("cannonade"), CannonboltAbilities::cannonade);
		ServerTickEvents.END_SERVER_TICK.register(CannonboltAbilities::tick);
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	private static boolean cannonball(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		start(ctx, new Roll(Kind.DASH, (float) ctx.param("damage", 9.0), ctx.param("speed", 1.15), 1.4,
				ctx.world().getTime() + (long) ctx.param("ticks", 26.0), BuiltinAbilities.horizontalLook(player)));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.6f);
		return true;
	}

	private static boolean shellGuard(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) ctx.param("ticks", 60.0);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, (int) ctx.param("resistance", 2.0), false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 3, false, false));
		knockbackImmunity(player, true);
		// Geschosse in der Naehe prallen von der Panzerkugel ab
		for (var projectile : ctx.world().getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class,
				player.getBoundingBox().expand(4.0), p -> p.getOwner() != player)) {
			projectile.setVelocity(projectile.getVelocity().multiply(-0.8));
			projectile.velocityModified = true;
		}
		// Ende des Schutzes: der Tick nimmt die Rueckstoss-Immunitaet wieder weg
		start(ctx, new Roll(Kind.CRUISE, 0.0f, 0.0, 0.0, ctx.world().getTime() + ticks, Vec3d.ZERO));
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.7f);
		return true;
	}

	private static boolean ballBounce(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		double forward = ctx.param("forward", 0.8);
		BuiltinAbilities.launch(player, look.x * forward, ctx.param("up", 1.25), look.z * forward);
		Roll roll = new Roll(Kind.BOUNCE, (float) ctx.param("damage", 7.0), 0.0, ctx.param("radius", 4.5),
				ctx.world().getTime() + 80, look);
		roll.airborne = true;
		start(ctx, roll);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_JUMP, 1.0f, 0.5f);
		return true;
	}

	private static boolean ricochet(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double range = ctx.param("range", 10.0);
		List<LivingEntity> targets = new ArrayList<>(BuiltinAbilities.livingAround(ctx, range));
		if (targets.isEmpty()) {
			return false;
		}
		targets.sort((a, b) -> Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player)));
		int max = (int) ctx.param("bounces", 4.0);
		Roll roll = new Roll(Kind.RICOCHET, (float) ctx.param("damage", 8.0), ctx.param("speed", 1.6), 1.6,
				ctx.world().getTime() + 100, Vec3d.ZERO);
		roll.targets.addAll(targets.subList(0, Math.min(max, targets.size())));
		roll.segmentStart = ctx.world().getTime();
		start(ctx, roll);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.9f);
		return true;
	}

	private static boolean rollingMode(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) ctx.param("ticks", 200.0);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, (int) ctx.param("speed_level", 2.0), false, false));
		stepHeight(player, true);
		start(ctx, new Roll(Kind.CRUISE, (float) ctx.param("damage", 5.0), 0.0, 1.3, ctx.world().getTime() + ticks, Vec3d.ZERO));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_PISTON_EXTEND, 1.0f, 0.6f);
		return true;
	}

	private static boolean cannonade(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		BuiltinAbilities.launch(player, 0.0, ctx.param("up", 1.9), 0.0);
		Roll roll = new Roll(Kind.CANNONADE, (float) ctx.param("damage", 16.0), ctx.param("dive", 2.6), ctx.param("radius", 8.5),
				ctx.world().getTime() + 120, Vec3d.ZERO);
		roll.airborne = true;
		start(ctx, roll);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.2f);
		return true;
	}

	// --- Rollen ----------------------------------------------------------------------------------

	private static void start(AbilityContext ctx, Roll roll) {
		Roll previous = ROLLS.put(ctx.player().getUuid(), roll);
		if (previous != null && previous.kind == Kind.CRUISE && roll.kind != Kind.CRUISE) {
			cleanup(ctx.player());
		}
	}

	private static void tick(MinecraftServer server) {
		if (ROLLS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Roll>> it = ROLLS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Roll> entry = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			Roll roll = entry.getValue();
			if (player == null || !player.isAlive() || !TransformationManager.get(player).isTransformed()) {
				if (player != null) {
					cleanup(player);
				}
				it.remove();
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			boolean done = switch (roll.kind) {
				case DASH -> tickDash(world, player, roll, now);
				case BOUNCE -> tickLanding(world, player, roll, now, false);
				case RICOCHET -> tickRicochet(world, player, roll, now);
				case CRUISE -> tickCruise(world, player, roll, now);
				case CANNONADE -> tickLanding(world, player, roll, now, true);
			};
			if (done) {
				cleanup(player);
				it.remove();
			}
		}
	}

	private static boolean tickDash(ServerWorld world, ServerPlayerEntity player, Roll roll, long now) {
		if (now >= roll.until) {
			return true;
		}
		BuiltinAbilities.launch(player, roll.direction.x * roll.speed, Math.min(player.getVelocity().y, 0.0) - 0.04,
				roll.direction.z * roll.speed);
		trail(world, player);
		ram(world, player, roll, false);
		if (player.horizontalCollision) {
			// Aufprall an der Wand: Stoss in die Umgebung, Rueckprall
			shockwave(world, player, roll.damage * 0.6f, 3.0, 0.6);
			BuiltinAbilities.launch(player, -roll.direction.x * 0.4, 0.35, -roll.direction.z * 0.4);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.PLAYERS, 1.0f, 0.6f);
			return true;
		}
		return false;
	}

	/** Sprung und Kanonade: warten bis zur Landung, dann Druckwelle. Kanonade stuerzt ab dem Scheitelpunkt herab. */
	private static boolean tickLanding(ServerWorld world, ServerPlayerEntity player, Roll roll, long now, boolean dive) {
		player.fallDistance = 0.0f;
		if (now >= roll.until) {
			return true;
		}
		if (dive && !roll.diving && player.getVelocity().y < 0.05) {
			roll.diving = true;
			BuiltinAbilities.launch(player, 0.0, -roll.speed, 0.0);
			world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.PLAYERS, 1.2f, 0.6f);
		}
		if (dive && roll.diving) {
			world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.8, player.getZ(), 4, 0.4, 0.4, 0.4, 0.01);
		}
		// erst nach dem Abheben auf Landung pruefen (der erste Tick steht noch am Boden)
		if (roll.airborne && now - roll.until + 120 > 3 && player.isOnGround()) {
			shockwave(world, player, roll.damage, roll.radius, dive ? 1.4 : 0.8);
			if (dive) {
				world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5f, 0.7f);
			} else {
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.2f, 0.6f);
			}
			return true;
		}
		return false;
	}

	private static boolean tickRicochet(ServerWorld world, ServerPlayerEntity player, Roll roll, long now) {
		roll.targets.removeIf(t -> !t.isAlive() || t.getWorld() != world);
		if (roll.targets.isEmpty() || now >= roll.until) {
			BuiltinAbilities.launch(player, 0.0, 0.3, 0.0);
			return true;
		}
		LivingEntity target = roll.targets.get(0);
		Vec3d to = target.getPos().add(0.0, target.getHeight() * 0.4, 0.0).subtract(player.getPos());
		if (to.lengthSquared() < 2.6 * 2.6 || now - roll.segmentStart > 20) {
			if (to.lengthSquared() < 2.6 * 2.6) {
				hurt(world, player, target, roll.damage, to.normalize(), 1.0);
				world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.0f, 1.3f);
			}
			roll.targets.remove(0);
			roll.segmentStart = now;
			// Abprall: kurz nach oben, damit die Bahn zum naechsten Ziel als Bogen wirkt
			BuiltinAbilities.launch(player, -to.normalize().x * 0.3, 0.45, -to.normalize().z * 0.3);
			return false;
		}
		Vec3d velocity = to.normalize().multiply(roll.speed);
		BuiltinAbilities.launch(player, velocity.x, Math.max(-0.4, Math.min(0.6, velocity.y + 0.1)), velocity.z);
		trail(world, player);
		return false;
	}

	/** Rollmodus und Panzerkugel: Dauer abwarten; im Rollmodus Gegner bei hoher Geschwindigkeit rammen. */
	private static boolean tickCruise(ServerWorld world, ServerPlayerEntity player, Roll roll, long now) {
		player.fallDistance = 0.0f;
		if (now >= roll.until) {
			return true;
		}
		if (roll.damage > 0.0f) {
			Vec3d velocity = player.getVelocity();
			double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
			if (horizontal > CRUISE_RAM_SPEED) {
				roll.direction = new Vec3d(velocity.x, 0.0, velocity.z).normalize();
				ram(world, player, roll, true);
				if (now % 2 == 0) {
					trail(world, player);
				}
			}
		}
		return false;
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	/** Gegner im Weg der Kugel treffen (einmal je Rolle bzw. im Rollmodus mit Pause). */
	private static void ram(ServerWorld world, ServerPlayerEntity player, Roll roll, boolean repeat) {
		Box box = player.getBoundingBox().expand(roll.radius * 0.5, 0.3, roll.radius * 0.5).stretch(roll.direction.multiply(0.8));
		long now = world.getTime();
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
			if (repeat) {
				Long last = roll.lastHit.get(target.getUuid());
				if (last != null && now - last < CRUISE_REHIT) {
					continue;
				}
				roll.lastHit.put(target.getUuid(), now);
			} else if (!roll.hit.add(target.getUuid())) {
				continue;
			}
			hurt(world, player, target, roll.damage, roll.direction, 1.2);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0f, 0.7f);
		}
	}

	private static void shockwave(ServerWorld world, ServerPlayerEntity player, float damage, double radius, double lift) {
		double radiusSq = radius * radius;
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius, 2.0, radius),
				e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= radiusSq && PartyRules.canHarm(player, e))) {
			double distance = Math.sqrt(target.squaredDistanceTo(player));
			float scaled = (float) (damage * (1.0 - 0.5 * distance / radius));
			Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			hurt(world, player, target, scaled, away.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : away.normalize(), 1.0);
			target.addVelocity(0.0, lift * (1.0 - distance / radius) + 0.2, 0.0);
			target.velocityModified = true;
		}
		BlockPos below = player.getBlockPos().down();
		var ground = world.getBlockState(below);
		int count = (int) (radius * 14);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground.isAir() ? net.minecraft.block.Blocks.STONE.getDefaultState() : ground),
				player.getX(), player.getY() + 0.1, player.getZ(), count, radius * 0.4, 0.1, radius * 0.4, 0.15);
		world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), count / 3, radius * 0.4, 0.05, radius * 0.4, 0.05);
	}

	private static void hurt(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float damage, Vec3d direction, double knockback) {
		target.damage(world.getDamageSources().playerAttack(player), damage);
		if (direction.lengthSquared() > 1.0E-4) {
			target.takeKnockback(knockback, -direction.x, -direction.z);
		}
		world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
	}

	private static void trail(ServerWorld world, ServerPlayerEntity player) {
		var ground = world.getBlockState(player.getBlockPos().down());
		if (!ground.isAir()) {
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.05, player.getZ(),
					4, 0.4, 0.0, 0.4, 0.1);
		}
		world.spawnParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.3, player.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
	}

	private static void knockbackImmunity(ServerPlayerEntity player, boolean on) {
		EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
		if (attribute == null) {
			return;
		}
		attribute.removeModifier(ROLL_KNOCKBACK);
		if (on) {
			attribute.addTemporaryModifier(new EntityAttributeModifier(ROLL_KNOCKBACK, 1.0, EntityAttributeModifier.Operation.ADD_VALUE));
		}
	}

	private static void stepHeight(ServerPlayerEntity player, boolean on) {
		EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
		if (attribute == null) {
			return;
		}
		attribute.removeModifier(ROLL_STEP);
		if (on) {
			attribute.addTemporaryModifier(new EntityAttributeModifier(ROLL_STEP, 0.9, EntityAttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Zeitweise Attribute wieder entfernen (Rollmodus-Stufenhoehe, Panzer-Rueckstoss-Immunitaet). */
	private static void cleanup(ServerPlayerEntity player) {
		stepHeight(player, false);
		knockbackImmunity(player, false);
	}
}
