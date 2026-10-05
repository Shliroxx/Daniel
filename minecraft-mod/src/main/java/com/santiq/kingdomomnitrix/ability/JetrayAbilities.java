package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

/**
 * Jetray (Aerophibian): Duesenflieger mit Neuroschock-Strahlen aus Augen und Schwanz. Eigenes Dauer-System „Flug-
 * Manoever“: Tiefflug-Angriff, Windschatten und Neuroschock-Sturm laufen hier tickweise; ohne aktive Manoever kehrt
 * der Tick sofort zurueck. Neuroschock laehmt (starke Langsamkeit + Schwaeche) statt nur Schaden zu machen.
 *
 * <p>Eigenes System „Ueberladung“: jeder Neuroschock-Treffer laedt das Ziel auf (Ladungen verfallen nach
 * {@link #CHARGE_TICKS} Ticks). Bei {@link #OVERLOAD_CHARGES} Ladungen entlaedt es sich: Explosion aus Funken
 * (ohne Blockschaden), Zusatzschaden am Ziel und in der Naehe, lange Laehmung. Alle sechs Faehigkeiten laden auf —
 * Kombinationen sind der Kern von Jetrays Kampfstil.</p>
 *
 * <ol>
 *   <li>{@code neuroshock} — Augenstrahl geradeaus, laehmt das Ziel</li>
 *   <li>{@code tail_shock} — Schwanzblitz springt auf bis zu 3 Gegner ueber</li>
 *   <li>{@code jet_burst} — Duesenstoss in Blickrichtung (auch nach oben), kurz unverwundbar</li>
 *   <li>{@code strafing_run} (★3) — Tiefflug geradeaus, feuert unterwegs auf Gegner in Reichweite</li>
 *   <li>{@code slipstream} (★5) — Windschatten: doppeltes Fluggefuehl, Gruppe in der Naehe wird schneller</li>
 *   <li>{@code neuroshock_storm} (★8) — steigt auf und laesst Neuroschock auf alle Gegner im Umkreis regnen</li>
 * </ol>
 */
final class JetrayAbilities {
	private enum Kind { STRAFE, SLIPSTREAM, STORM, BURST }

	/** Normale Fluggeschwindigkeit (Vanilla) und Windschatten-Wert — nur genau dieser Wert wird beim Betreten zurueckgesetzt. */
	static final float BASE_FLY_SPEED = 0.05f;
	static final float SLIPSTREAM_FLY_SPEED = 0.105f;
	private static final DustParticleEffect SHOCK = new DustParticleEffect(new Vector3f(0.55f, 1.0f, 0.25f), 1.1f);
	private static final DustParticleEffect JET = new DustParticleEffect(new Vector3f(0.95f, 0.2f, 0.18f), 1.4f);

	/** Laufendes Manoever eines Spielers; Zahlen aus den Faehigkeits-Parametern beim Start. */
	private static final class Run {
		final Kind kind;
		final long until;
		final float damage;
		final int stunTicks;
		final double range;
		final double speed;
		final int interval;
		final int targets;
		Vec3d direction;
		/** bereits getroffene Ziele (Ueberschall-Flugweg trifft jeden nur einmal) */
		final java.util.Set<UUID> struck = new java.util.HashSet<>();

		Run(Kind kind, long until, float damage, int stunTicks, double range, double speed, int interval, int targets, Vec3d direction) {
			this.kind = kind;
			this.until = until;
			this.damage = damage;
			this.stunTicks = stunTicks;
			this.range = range;
			this.speed = speed;
			this.interval = interval;
			this.targets = targets;
			this.direction = direction;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	/** Neuroschock-Ladungen je Ziel: Anzahl und Verfallszeit */
	private static final Map<UUID, long[]> CHARGES = new HashMap<>();
	static final int OVERLOAD_CHARGES = 3;
	static final int CHARGE_TICKS = 100;
	/** Ueberladung: Faktor auf den ausloesenden Treffer, Umkreis der Entladung */
	private static final float OVERLOAD_FACTOR = 1.5f;
	private static final double OVERLOAD_RADIUS = 3.5;

	private JetrayAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("neuroshock"), JetrayAbilities::neuroshock);
		AbilityRegistry.register(KingdomOmnitrix.id("tail_shock"), JetrayAbilities::tailShock);
		AbilityRegistry.register(KingdomOmnitrix.id("jet_burst"), JetrayAbilities::jetBurst);
		AbilityRegistry.register(KingdomOmnitrix.id("strafing_run"), JetrayAbilities::strafingRun);
		AbilityRegistry.register(KingdomOmnitrix.id("slipstream"), JetrayAbilities::slipstream);
		AbilityRegistry.register(KingdomOmnitrix.id("neuroshock_storm"), JetrayAbilities::neuroshockStorm);
		ServerTickEvents.END_SERVER_TICK.register(JetrayAbilities::tick);
		// Windschatten-Tempo darf nicht im Spielstand haengen bleiben (Server beendet waehrend des Manoevers)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> resetFlySpeed(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (RUNS.remove(handler.getPlayer().getUuid()) != null) {
				resetFlySpeed(handler.getPlayer());
			}
		});
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	private static boolean neuroshock(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 24.0);
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		Optional<LivingEntity> hit = Targeting.findLivingTarget(player, eye.distanceTo(stop));
		if (hit.isPresent() && PartyRules.canHarm(player, hit.get())) {
			stop = center(hit.get());
			shock(world, player, hit.get(), (float) ctx.param("damage", 6.0), (int) ctx.param("stun_ticks", 30.0));
		}
		// zwei parallele Strahlen aus den Augen
		Vec3d side = sideways(player).multiply(0.12);
		beam(world, eye.add(side), stop, SHOCK);
		beam(world, eye.subtract(side), stop, SHOCK);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, stop.x, stop.y, stop.z, 10, 0.15, 0.15, 0.15, 0.2);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GUARDIAN_ATTACK, 0.7f, 1.9f);
		return true;
	}

	private static boolean tailShock(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		List<LivingEntity> near = new ArrayList<>(BuiltinAbilities.livingAround(ctx, ctx.param("range", 10.0)));
		if (near.isEmpty()) {
			return false;
		}
		near.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
		int jumps = (int) ctx.param("jumps", 3.0);
		float damage = (float) ctx.param("damage", 5.0);
		int stun = (int) ctx.param("stun_ticks", 20.0);
		// Schwanzspitze: hinter und ueber dem Ruecken
		Vec3d from = player.getPos().add(BuiltinAbilities.horizontalLook(player).multiply(-0.6)).add(0.0, player.getHeight() * 0.9, 0.0);
		LivingEntity current = null;
		List<LivingEntity> struck = new ArrayList<>();
		for (int i = 0; i < jumps; i++) {
			LivingEntity next = nearestUnstruck(near, current == null ? player.getPos() : current.getPos(), struck, 6.0, current == null);
			if (next == null || !visible(world, player, from, center(next))) {
				break;
			}
			beam(world, from, center(next), SHOCK);
			shock(world, player, next, damage * (1.0f - 0.2f * i), stun);
			struck.add(next);
			current = next;
			from = center(next);
		}
		if (struck.isEmpty()) {
			return false;
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_SHOOT, 0.8f, 1.8f);
		return true;
	}

	private static boolean jetBurst(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = player.getRotationVec(1.0f);
		double power = ctx.param("power", 2.2);
		BuiltinAbilities.launch(player, look.x * power, Math.max(look.y * power, 0.25), look.z * power);
		ctx.grantInvulnerability((int) ctx.param("invulnerable_ticks", 8));
		ServerWorld world = ctx.world();
		for (int i = 0; i < 8; i++) {
			Vec3d p = player.getPos().add(0.0, 0.8, 0.0).subtract(look.multiply(i * 0.35));
			world.spawnParticles(JET, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.0);
		}
		// Ueberschall: Knall-Ring hinter Jetray, wer im Flugweg steht, wird mitgerissen und aufgeladen
		Vec3d behind = player.getPos().add(0.0, 0.9, 0.0).subtract(look.multiply(0.8));
		world.spawnParticles(ParticleTypes.SONIC_BOOM, behind.x, behind.y, behind.z, 1, 0.0, 0.0, 0.0, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.8f);
		start(player, new Run(Kind.BURST, world.getTime() + (long) ctx.param("ram_ticks", 8.0), (float) ctx.param("damage", 5.0),
				(int) ctx.param("stun_ticks", 15.0), 1.4, 0.0, 1, 0, look));
		world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 10, 0.3, 0.2, 0.3, 0.05);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 1.0f, 1.3f);
		return true;
	}

	private static boolean strafingRun(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		long now = ctx.world().getTime();
		Vec3d look = player.getRotationVec(1.0f);
		// flacher Anflug: nicht steiler als 30 Grad
		Vec3d direction = new Vec3d(look.x, MathHelper.clamp(look.y, -0.5, 0.5), look.z).normalize();
		start(player, new Run(Kind.STRAFE, now + (long) ctx.param("ticks", 40.0), (float) ctx.param("damage", 5.0),
				(int) ctx.param("stun_ticks", 20.0), ctx.param("range", 10.0), ctx.param("speed", 1.3),
				(int) ctx.param("interval", 5.0), (int) ctx.param("targets", 2.0), direction));
		ctx.grantInvulnerability(10);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_SWOOP, 1.0f, 1.2f);
		return true;
	}

	private static boolean slipstream(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) ctx.param("ticks", 200.0);
		start(player, new Run(Kind.SLIPSTREAM, ctx.world().getTime() + ticks, 0.0f, 0, ctx.param("radius", 8.0), 0.0,
				(int) ctx.param("interval", 20.0), 0, Vec3d.ZERO));
		player.getAbilities().setFlySpeed(SLIPSTREAM_FLY_SPEED);
		player.sendAbilitiesUpdate();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, (int) ctx.param("speed_level", 2.0), false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, ticks, 0, false, false));
		ctx.world().spawnParticles(ParticleTypes.GUST, player.getX(), player.getBodyY(0.5), player.getZ(), 3, 0.6, 0.3, 0.6, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_CHARGE, 1.0f, 1.0f);
		return true;
	}

	private static boolean neuroshockStorm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		long now = ctx.world().getTime();
		BuiltinAbilities.launch(player, 0.0, ctx.param("lift", 1.1), 0.0);
		start(player, new Run(Kind.STORM, now + (long) ctx.param("ticks", 60.0), (float) ctx.param("damage", 7.0),
				(int) ctx.param("stun_ticks", 40.0), ctx.param("radius", 16.0), 0.0, (int) ctx.param("interval", 4.0),
				(int) ctx.param("targets", 3.0), Vec3d.ZERO));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, (int) ctx.param("ticks", 60.0) + 20, 0, false, false));
		ctx.grantInvulnerability(20);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.5f);
		return true;
	}

	// --- Manoever-Ticks --------------------------------------------------------------------------

	private static void start(ServerPlayerEntity player, Run run) {
		Run previous = RUNS.put(player.getUuid(), run);
		if (previous != null && previous.kind == Kind.SLIPSTREAM && run.kind != Kind.SLIPSTREAM) {
			resetFlySpeed(player);
		}
	}

	private static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Run> entry = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			Run run = entry.getValue();
			if (player == null || !player.isAlive() || !TransformationManager.get(player).isTransformed()) {
				if (player != null) {
					resetFlySpeed(player);
				}
				it.remove();
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			if (now >= run.until) {
				finish(world, player, run);
				it.remove();
				continue;
			}
			switch (run.kind) {
				case STRAFE -> tickStrafe(world, player, run, now);
				case SLIPSTREAM -> tickSlipstream(world, player, run, now);
				case STORM -> tickStorm(world, player, run, now);
				case BURST -> tickBurst(world, player, run);
			}
		}
	}

	private static void tickStrafe(ServerWorld world, ServerPlayerEntity player, Run run, long now) {
		BuiltinAbilities.launch(player, run.direction.x * run.speed, run.direction.y * run.speed, run.direction.z * run.speed);
		Vec3d back = player.getPos().add(0.0, 0.7, 0.0).subtract(run.direction.multiply(0.8));
		world.spawnParticles(JET, back.x, back.y, back.z, 3, 0.1, 0.1, 0.1, 0.0);
		if (now % run.interval != 0) {
			return;
		}
		// mehrere Ziele je Salve, naechste zuerst
		Vec3d eye = player.getEyePos();
		List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(run.range),
						e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= run.range * run.range && PartyRules.canHarm(player, e))
				.stream()
				.filter(e -> visible(world, player, eye, center(e)))
				.sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
				.limit(Math.max(1, run.targets))
				.toList();
		for (LivingEntity target : targets) {
			beam(world, eye, center(target), SHOCK);
			shock(world, player, target, run.damage, run.stunTicks);
		}
		if (!targets.isEmpty()) {
			play(world, player, SoundEvents.ENTITY_GUARDIAN_ATTACK, 0.5f, 2.0f);
		}
	}

	private static void tickSlipstream(ServerWorld world, ServerPlayerEntity player, Run run, long now) {
		if (player.getAbilities().flying) {
			world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.6, player.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
		}
		if (now % run.interval != 0) {
			return;
		}
		// Gruppe im Windschatten: kurze Tempo-Schuebe, solange sie in der Naehe bleibt
		for (ServerPlayerEntity other : world.getPlayers(p -> p != player && p.squaredDistanceTo(player) <= run.range * run.range
				&& PartyRules.isAlly(player, p))) {
			other.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, run.interval + 10, 1, false, true));
			world.spawnParticles(ParticleTypes.GUST, other.getX(), other.getBodyY(0.5), other.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
		}
	}

	private static void tickStorm(ServerWorld world, ServerPlayerEntity player, Run run, long now) {
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getBodyY(0.5), player.getZ(), 4, 0.5, 0.4, 0.5, 0.1);
		if (now % run.interval != 0) {
			return;
		}
		List<LivingEntity> targets = new ArrayList<>(world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(run.range),
				e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= run.range * run.range && PartyRules.canHarm(player, e)));
		if (targets.isEmpty()) {
			return;
		}
		// abwechselnd verschiedene Ziele: zufaellige Auswahl je Salve
		java.util.Collections.shuffle(targets, new java.util.Random(now ^ player.getUuid().getLeastSignificantBits()));
		Vec3d from = player.getEyePos();
		int fired = 0;
		for (LivingEntity target : targets) {
			if (fired >= run.targets) {
				break;
			}
			if (!visible(world, player, from, center(target))) {
				continue;
			}
			beam(world, from, center(target), SHOCK);
			shock(world, player, target, run.damage, run.stunTicks);
			fired++;
		}
		if (fired > 0) {
			play(world, player, SoundEvents.ENTITY_GUARDIAN_ATTACK, 0.7f, 1.7f);
		}
	}

	/** Ueberschall-Flugweg: alles in Reichweite einmal treffen und mitreissen. */
	private static void tickBurst(ServerWorld world, ServerPlayerEntity player, Run run) {
		Vec3d velocity = player.getVelocity();
		net.minecraft.util.math.Box box = player.getBoundingBox().expand(run.range * 0.5).stretch(velocity);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && !run.struck.contains(e.getUuid()))) {
			run.struck.add(target.getUuid());
			shock(world, player, target, run.damage, run.stunTicks);
			target.takeKnockback(1.2, -velocity.x, -velocity.z);
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.5), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
		world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.6, player.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
	}

	private static void finish(ServerWorld world, ServerPlayerEntity player, Run run) {
		switch (run.kind) {
			case SLIPSTREAM -> resetFlySpeed(player);
			case STORM -> {
				// Schlussentladung: Ring aus Funken, alle Gegner im halben Umkreis werden noch einmal gelaehmt
				for (int i = 0; i < 32; i++) {
					double angle = i * MathHelper.TAU / 32;
					world.spawnParticles(SHOCK, player.getX() + Math.cos(angle) * 3.0, player.getBodyY(0.5), player.getZ() + Math.sin(angle) * 3.0,
							1, 0.0, 0.0, 0.0, 0.0);
				}
				double half = run.range * 0.5;
				for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(half),
						e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= half * half && PartyRules.canHarm(player, e))) {
					shock(world, player, target, run.damage * 0.5f, run.stunTicks);
				}
				play(world, player, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.6f);
			}
			case STRAFE -> {
				BuiltinAbilities.launch(player, run.direction.x * 0.3, 0.1, run.direction.z * 0.3);
			}
			case BURST -> {
			}
		}
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	/** Neuroschock: Schaden und Laehmung (starke Langsamkeit, Schwaeche), Funken am Ziel, eine Ladung mehr. */
	private static void shock(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float damage, int stunTicks) {
		int charges = charge(world, target);
		if (charges >= OVERLOAD_CHARGES) {
			CHARGES.remove(target.getUuid());
			overload(world, player, target, damage, stunTicks);
			return;
		}
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().indirectMagic(player, player), damage);
		if (stunTicks > 0) {
			stun(target, player, stunTicks);
		}
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.3, 0.4, 0.3, 0.15);
	}

	/** Ladung am Ziel erhoehen (abgelaufene verfallen); liefert die neue Anzahl. */
	private static int charge(ServerWorld world, LivingEntity target) {
		long now = world.getTime();
		if (CHARGES.size() > 64) {
			CHARGES.values().removeIf(c -> c[1] < now);
		}
		long[] entry = CHARGES.get(target.getUuid());
		if (entry == null || entry[1] < now) {
			entry = new long[]{0, 0};
			CHARGES.put(target.getUuid(), entry);
		}
		entry[0]++;
		entry[1] = now + CHARGE_TICKS;
		// sichtbare Ladung: je Stufe mehr Funken, die um das Ziel kreisen
		for (int i = 0; i < entry[0] * 4; i++) {
			double angle = i * MathHelper.TAU / (entry[0] * 4) + now * 0.3;
			world.spawnParticles(SHOCK, target.getX() + Math.cos(angle) * 0.6, target.getBodyY(0.5 + 0.15 * (i % 3)),
					target.getZ() + Math.sin(angle) * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
		}
		return (int) entry[0];
	}

	/** Ueberladung: Entladung am Ziel, Teilschaden im Umkreis, lange Laehmung — ohne Blockschaden. */
	private static void overload(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float damage, int stunTicks) {
		Vec3d at = center(target);
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().indirectMagic(player, player), damage * OVERLOAD_FACTOR);
		stun(target, player, stunTicks * 2);
		for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(OVERLOAD_RADIUS),
				e -> e != player && e != target && e.isAlive() && e.squaredDistanceTo(target) <= OVERLOAD_RADIUS * OVERLOAD_RADIUS
						&& PartyRules.canHarm(player, e))) {
			near.timeUntilRegen = 0;
			near.damage(world.getDamageSources().indirectMagic(player, player), damage * 0.6f);
			stun(near, player, stunTicks);
			beam(world, at, center(near), SHOCK);
		}
		world.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 60, 0.6, 0.6, 0.6, 0.6);
		world.spawnParticles(SHOCK, at.x, at.y, at.z, 40, 1.2, 0.8, 1.2, 0.0);
		world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 1.6f);
		world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.5f, 1.8f);
		player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.jetray_overload")
				.formatted(net.minecraft.util.Formatting.GREEN), true);
	}

	private static void stun(LivingEntity target, ServerPlayerEntity player, int ticks) {
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 4, false, true), player);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1, false, true), player);
	}

	private static void beam(ServerWorld world, Vec3d from, Vec3d to, DustParticleEffect dust) {
		Vec3d step = to.subtract(from);
		int points = Math.max(4, (int) (step.length() * 3));
		for (int i = 1; i <= points; i++) {
			Vec3d p = from.add(step.multiply(i / (double) points));
			world.spawnParticles(dust, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private static boolean visible(ServerWorld world, ServerPlayerEntity player, Vec3d from, Vec3d to) {
		return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player))
				.getType() == HitResult.Type.MISS;
	}

	private static LivingEntity nearestUnstruck(List<LivingEntity> pool, Vec3d from, List<LivingEntity> struck, double hop, boolean first) {
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : pool) {
			if (struck.contains(e) || !e.isAlive()) {
				continue;
			}
			double d = e.getPos().squaredDistanceTo(from);
			if ((first || d <= hop * hop) && d < bestDist) {
				best = e;
				bestDist = d;
			}
		}
		return best;
	}

	private static Vec3d center(LivingEntity entity) {
		return entity.getPos().add(0.0, entity.getHeight() * 0.5, 0.0);
	}

	private static Vec3d sideways(ServerPlayerEntity player) {
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		return new Vec3d(-look.z, 0.0, look.x);
	}

	private static void play(ServerWorld world, ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}

	/** Windschatten-Tempo zuruecknehmen — nur den eigenen Wert, fremde Aenderungen (andere Mods) bleiben. */
	static void resetFlySpeed(ServerPlayerEntity player) {
		if (Math.abs(player.getAbilities().getFlySpeed() - SLIPSTREAM_FLY_SPEED) < 1.0e-4f) {
			player.getAbilities().setFlySpeed(BASE_FLY_SPEED);
			player.sendAbilitiesUpdate();
		}
	}
}
