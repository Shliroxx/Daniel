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
	private enum Kind { DASH, BOUNCE, RICOCHET, CRUISE, GUARD, CANNONADE }

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
		/** Schwung 0..1: baut sich beim Rollen auf, macht schneller und haerter (Kern des Kugel-Systems) */
		float momentum;
		/** verbleibende Wand-Abpraller (Kanonenkugel) */
		int wallBounces;
		/** Rollmodus: Ticks am Stueck in voller Fahrt, aktuelle Tempo-Stufe */
		int fastTicks;
		int speedLevel;
		int baseSpeedLevel;
		/** Kanonade: hoechster Punkt (Sturzhoehe), Abpraller-Kette: Treffer bisher */
		double peakY;
		int chain;

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
	/** Rollmodus: so viele Ticks volle Fahrt fuer die naechste Tempo-Stufe (bis +2) */
	private static final int CRUISE_GEAR_TICKS = 40;
	/** Panzerkugel: Nahkaempfer in diesem Abstand werden weggeschleudert */
	private static final double GUARD_REPEL = 2.0;

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
		Roll roll = new Roll(Kind.DASH, (float) ctx.param("damage", 9.0), ctx.param("speed", 1.15), 1.4,
				ctx.world().getTime() + (long) ctx.param("ticks", 26.0), BuiltinAbilities.horizontalLook(player));
		roll.wallBounces = (int) ctx.param("wall_bounces", 3.0);
		start(ctx, roll);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.6f);
		return true;
	}

	private static boolean shellGuard(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) ctx.param("ticks", 60.0);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, (int) ctx.param("resistance", 2.0), false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 3, false, false));
		knockbackImmunity(player, true);
		// waehrend der ganzen Dauer: Geschosse fliegen zum Schuetzen zurueck, Nahkaempfer werden weggeschleudert
		start(ctx, new Roll(Kind.GUARD, (float) ctx.param("damage", 4.0), ctx.param("reflect_speed", 1.5), ctx.param("radius", 4.0),
				ctx.world().getTime() + ticks, Vec3d.ZERO));
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
		Roll roll = new Roll(Kind.CRUISE, (float) ctx.param("damage", 5.0), 0.0, 1.3, ctx.world().getTime() + ticks, Vec3d.ZERO);
		roll.baseSpeedLevel = (int) ctx.param("speed_level", 2.0);
		roll.speedLevel = roll.baseSpeedLevel;
		start(ctx, roll);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_PISTON_EXTEND, 1.0f, 0.6f);
		return true;
	}

	private static boolean cannonade(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		BuiltinAbilities.launch(player, 0.0, ctx.param("up", 1.9), 0.0);
		Roll roll = new Roll(Kind.CANNONADE, (float) ctx.param("damage", 16.0), ctx.param("dive", 2.6), ctx.param("radius", 8.5),
				ctx.world().getTime() + 120, Vec3d.ZERO);
		roll.airborne = true;
		roll.peakY = player.getY();
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
				case GUARD -> tickGuard(world, player, roll, now);
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
		// Schwung: startet bei 60 % Tempo und steigert sich bis 160 %
		roll.momentum = Math.min(1.0f, roll.momentum + 0.09f);
		double speed = roll.speed * (0.6 + roll.momentum);
		BuiltinAbilities.launch(player, roll.direction.x * speed, Math.min(player.getVelocity().y, 0.0) - 0.04,
				roll.direction.z * speed);
		trail(world, player);
		if (roll.momentum >= 1.0f) {
			world.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.6, player.getZ(), 3, 0.4, 0.4, 0.4, 0.1);
		}
		ram(world, player, roll, false);
		if (player.horizontalCollision) {
			// Aufprall an der Wand: Stoss je nach Schwung; solange Abpraller uebrig sind, springt die Kugel zurueck
			shockwave(world, player, roll.damage * (0.4f + 0.6f * roll.momentum), 2.5 + 2.0 * roll.momentum, 0.6);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.PLAYERS, 1.0f, 0.6f);
			if (roll.wallBounces-- <= 0) {
				BuiltinAbilities.launch(player, -roll.direction.x * 0.4, 0.35, -roll.direction.z * 0.4);
				return true;
			}
			roll.direction = reflect(player, roll.direction);
			roll.hit.clear();
			roll.momentum = Math.max(0.5f, roll.momentum * 0.85f);
			BuiltinAbilities.launch(player, roll.direction.x * speed, 0.25, roll.direction.z * speed);
		}
		return false;
	}

	/** Sprung und Kanonade: warten bis zur Landung, dann Druckwelle. Kanonade stuerzt ab dem Scheitelpunkt herab. */
	private static boolean tickLanding(ServerWorld world, ServerPlayerEntity player, Roll roll, long now, boolean dive) {
		player.fallDistance = 0.0f;
		if (now >= roll.until) {
			return true;
		}
		roll.peakY = Math.max(roll.peakY, player.getY());
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
			// Sturzhoehe verstaerkt den Einschlag (je 10 Bloecke +50 %, hoechstens doppelt)
			double height = Math.max(0.0, roll.peakY - player.getY());
			float boost = (float) Math.min(2.0, 1.0 + height / 20.0);
			shockwave(world, player, roll.damage * boost, roll.radius * Math.min(1.5, 0.75 + height / 40.0), dive ? 1.4 : 0.8);
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
				// Kette: jeder weitere Treffer +25 %; der letzte schlaegt mit Druckwelle ein
				hurt(world, player, target, roll.damage * (1.0f + 0.25f * roll.chain++), to.normalize(), 1.0);
				if (roll.targets.size() == 1) {
					shockwave(world, player, roll.damage * 0.8f, 4.0, 0.8);
				}
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
				roll.fastTicks++;
				roll.momentum = Math.min(1.0f, roll.fastTicks / (float) (CRUISE_GEAR_TICKS * 2));
				int gear = roll.baseSpeedLevel + Math.min(2, roll.fastTicks / CRUISE_GEAR_TICKS);
				if (gear != roll.speedLevel) {
					// naechster Gang: schneller, Funkenregen, Hinweis in der Aktionsleiste
					roll.speedLevel = gear;
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, (int) (roll.until - now), gear, false, false));
					world.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.5, 0.3, 0.5, 0.3);
					world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.PLAYERS, 0.8f, 0.8f + 0.2f * gear);
					player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.cannonbolt_gear", gear - roll.baseSpeedLevel)
							.formatted(net.minecraft.util.Formatting.GOLD), true);
				}
				ram(world, player, roll, true);
				if (now % 2 == 0) {
					trail(world, player);
				}
			} else if (roll.fastTicks > 0) {
				// angehalten: Schwung und Gaenge sind weg
				roll.fastTicks = 0;
				roll.momentum = 0.0f;
				if (roll.speedLevel != roll.baseSpeedLevel) {
					roll.speedLevel = roll.baseSpeedLevel;
					player.removeStatusEffect(StatusEffects.SPEED);
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, (int) (roll.until - now), roll.baseSpeedLevel, false, false));
				}
			}
		}
		return false;
	}

	/** Panzerkugel: Geschosse zum Schuetzen zurueck (schneller), Nahkaempfer wegschleudern. */
	private static boolean tickGuard(ServerWorld world, ServerPlayerEntity player, Roll roll, long now) {
		if (now >= roll.until) {
			return true;
		}
		for (var projectile : world.getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class,
				player.getBoundingBox().expand(roll.radius), p -> p.getOwner() != player)) {
			Vec3d toPlayer = player.getPos().subtract(projectile.getPos());
			if (projectile.getVelocity().dotProduct(toPlayer) <= 0.0) {
				continue; // fliegt schon weg
			}
			var owner = projectile.getOwner();
			Vec3d back = owner != null && owner.isAlive()
					? owner.getEyePos().subtract(projectile.getPos()).normalize()
					: projectile.getVelocity().multiply(-1.0).normalize();
			double speed = Math.max(1.0, projectile.getVelocity().length()) * roll.speed;
			projectile.setVelocity(back.multiply(speed));
			projectile.setOwner(player);
			projectile.velocityModified = true;
			world.spawnParticles(ParticleTypes.CRIT, projectile.getX(), projectile.getY(), projectile.getZ(), 6, 0.1, 0.1, 0.1, 0.2);
			world.playSound(null, projectile.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.3f);
		}
		if (now % 10 == 0) {
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(GUARD_REPEL),
					e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= GUARD_REPEL * GUARD_REPEL && PartyRules.canHarm(player, e))) {
				Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
				hurt(world, player, target, roll.damage, away.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : away.normalize(), 1.6);
			}
		}
		return false;
	}

	/** Neue Richtung nach einem Wandtreffer: die blockierte Achse wird gespiegelt. */
	private static Vec3d reflect(ServerPlayerEntity player, Vec3d direction) {
		Box box = player.getBoundingBox();
		boolean blockedX = !player.getWorld().isSpaceEmpty(player, box.offset(Math.signum(direction.x) * 0.3, 0.05, 0.0));
		boolean blockedZ = !player.getWorld().isSpaceEmpty(player, box.offset(0.0, 0.05, Math.signum(direction.z) * 0.3));
		double x = blockedX || !blockedZ && Math.abs(direction.x) >= Math.abs(direction.z) ? -direction.x : direction.x;
		double z = blockedZ || !blockedX && Math.abs(direction.z) > Math.abs(direction.x) ? -direction.z : direction.z;
		return new Vec3d(x, 0.0, z).normalize();
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
			hurt(world, player, target, roll.damage * (0.6f + roll.momentum), roll.direction, 0.8 + roll.momentum);
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
