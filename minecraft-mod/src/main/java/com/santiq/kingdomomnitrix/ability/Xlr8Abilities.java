package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * XLR8 (Kineceleran). Eigenes Dauer-System „Tempo“ (0–100):
 *
 * <ul>
 *   <li>Rennen baut Tempo auf, Treffer legen nach; Stehen baut es schnell ab.</li>
 *   <li>Ab {@link #BLUR} <b>verschwommen</b>: blaue Nachbilder, jeder Nahkampftreffer macht Zusatzschaden nach Tempo.</li>
 *   <li>Bei {@link #MAX} <b>Schallmauer</b>: Ueberschallknall, beim Sprinten wird alles im Weg umgerannt; die naechste
 *       Faehigkeit verbraucht das ganze Tempo fuer ihre staerkste Form.</li>
 * </ul>
 * Alle Faehigkeiten skalieren mit dem Tempo. Laufende Wirkungen (Schlaghagel, Zyklon, Zeitlupe, Blitzhagel) laufen
 * tickweise; ohne aktive Wirkung kostet nur die Tempo-Pruefung der verwandelten XLR8-Spieler Rechenzeit.
 */
final class Xlr8Abilities {
	static final float MAX = 100.0f;
	static final float BLUR = 50.0f;
	private static final Identifier XLR8 = KingdomOmnitrix.id("xlr8");
	/** Tempo je Pruefintervall beim Rennen / im Stand, je Treffer */
	private static final int INTERVAL = 5;
	private static final float GAIN_RUNNING = 4.0f;
	private static final float LOSS_STANDING = 8.0f;
	private static final float GAIN_PER_HIT = 3.0f;
	/** ab dieser Geschwindigkeit (Bloecke/Tick) gilt XLR8 als rennend */
	private static final double RUN_SPEED = 0.2;
	private static final DustParticleEffect BLUE = new DustParticleEffect(new Vector3f(0.12f, 0.56f, 1.0f), 1.2f);

	private static final Map<UUID, Float> TEMPO = new HashMap<>();
	private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
	private static final Map<UUID, Effect> EFFECTS = new HashMap<>();

	private enum Kind { FLURRY, CYCLONE, SLOW_TIME, BARRAGE }

	private static final class Effect {
		final Kind kind;
		final long until;
		final float damage;
		final double radius;
		final List<LivingEntity> targets = new ArrayList<>();
		int remaining;
		Vec3d home;

		Effect(Kind kind, long until, float damage, double radius) {
			this.kind = kind;
			this.until = until;
			this.damage = damage;
			this.radius = radius;
		}
	}

	private Xlr8Abilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("rapid_strikes"), Xlr8Abilities::rapidStrikes);
		AbilityRegistry.register(KingdomOmnitrix.id("blur_dodge"), Xlr8Abilities::afterimage);
		AbilityRegistry.register(KingdomOmnitrix.id("dash_strike"), Xlr8Abilities::dashStrike);
		AbilityRegistry.register(KingdomOmnitrix.id("cyclone_run"), Xlr8Abilities::cyclone);
		AbilityRegistry.register(KingdomOmnitrix.id("time_slip"), Xlr8Abilities::slowTime);
		AbilityRegistry.register(KingdomOmnitrix.id("lightspeed_barrage"), Xlr8Abilities::barrage);
		ServerTickEvents.END_SERVER_TICK.register(Xlr8Abilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
		// Nahkampf ab „verschwommen“: Zusatzschaden nach Tempo, jeder Treffer legt Tempo nach
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClient && player instanceof ServerPlayerEntity server && entity instanceof LivingEntity target
					&& isXlr8(server) && PartyRules.canHarm(server, target)) {
				float tempo = tempo(server);
				if (tempo >= BLUR) {
					target.timeUntilRegen = 0;
					target.damage(server.getServerWorld().getDamageSources().playerAttack(server), tempo / 25.0f);
					server.getServerWorld().spawnParticles(BLUE, target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
				}
				addTempo(server, GAIN_PER_HIT);
			}
			return ActionResult.PASS;
		});
	}

	// --- Tempo -----------------------------------------------------------------------------------

	static float tempo(ServerPlayerEntity player) {
		return TEMPO.getOrDefault(player.getUuid(), 0.0f);
	}

	private static boolean isXlr8(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(XLR8::equals).isPresent();
	}

	private static void addTempo(ServerPlayerEntity player, float amount) {
		float before = tempo(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		TEMPO.put(player.getUuid(), after);
		if (before < MAX && after >= MAX) {
			ServerWorld world = player.getServerWorld();
			Vec3d behind = player.getPos().add(0, 1.0, 0).subtract(BuiltinAbilities.horizontalLook(player).multiply(1.0));
			world.spawnParticles(ParticleTypes.SONIC_BOOM, behind.x, behind.y, behind.z, 1, 0.0, 0.0, 0.0, 0.0);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.6f, 1.9f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.xlr8_sound_barrier").formatted(Formatting.AQUA), true);
		}
	}

	/** Faktor 1..2 nach Tempo; bei voller Schallmauer wird das Tempo verbraucht ({@code full}). */
	private static Boost boost(ServerPlayerEntity player) {
		float tempo = tempo(player);
		if (tempo >= MAX) {
			TEMPO.put(player.getUuid(), 0.0f);
			return new Boost(2.0f, true);
		}
		return new Boost(1.0f + tempo / (MAX * 1.5f), false);
	}

	private record Boost(float factor, boolean full) {
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Schlaghagel: viele schnelle Schlaege auf das Ziel vorn — Anzahl nach Tempo; volle Schallmauer schleudert es hoch. */
	private static boolean rapidStrikes(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Box area = player.getBoundingBox().stretch(look.multiply(ctx.param("range", 3.0))).expand(1.0, 0.5, 1.0);
		List<LivingEntity> targets = ctx.world().getEntitiesByClass(LivingEntity.class, area,
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e));
		if (targets.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		Boost boost = boost(player);
		// radius 1 markiert den Schallmauer-Schlaghagel (Abschluss-Aufwaertshaken)
		Effect flurry = new Effect(Kind.FLURRY, ctx.world().getTime() + 40, (float) ctx.param("damage", 9.0) / 4.0f, boost.full() ? 1.0 : 0.0);
		flurry.targets.addAll(targets);
		flurry.remaining = boost.full() ? 12 : 4 + (int) (tempo(player) / 20.0f);
		EFFECTS.put(player.getUuid(), flurry);
		BuiltinAbilities.sound(ctx, ModSounds.COMBAT_SWING, 1.0f, 1.6f);
		return true;
	}

	/** Nachbild: Ausweichsprung, Gegner verlieren XLR8 aus den Augen und greifen das Nachbild an. */
	private static boolean afterimage(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Vec3d spot = player.getPos();
		double power = ctx.param("power", 1.6) * boost(player).factor();
		BuiltinAbilities.launch(player, -look.x * power, 0.25, -look.z * power);
		ctx.grantInvulnerability((int) ctx.param("invulnerable_ticks", 12));
		// Nachbild aus blauen Partikeln in Koerperform
		for (int i = 0; i < 24; i++) {
			world.spawnParticles(BLUE, spot.x, spot.y + (i % 12) * 0.16, spot.z, 1, 0.18, 0.02, 0.18, 0.0);
		}
		double radius = ctx.param("radius", 12.0);
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(radius),
				m -> m.getTarget() == player)) {
			mob.setTarget(null);
			mob.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 1.0);
		}
		addTempo(player, (float) ctx.param("tempo", 20.0));
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_DASH, 0.8f, 1.4f);
		return true;
	}

	/** Sturmangriff: Weite und Schaden nach Tempo, trifft alles auf dem Weg. */
	private static boolean dashStrike(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Boost boost = boost(player);
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		double distance = ctx.param("distance", 8.0) * boost.factor();
		float damage = (float) ctx.param("damage", 5.0) * boost.factor();
		Box path = player.getBoundingBox().stretch(look.multiply(distance)).expand(boost.full() ? 1.4 : 0.6);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, path, e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().playerAttack(player), damage);
			target.takeKnockback(boost.full() ? 1.4 : 0.6, -look.x, -look.z);
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.5), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
		for (int i = 0; i < (int) distance * 2; i++) {
			Vec3d p = player.getPos().add(look.multiply(i * 0.5)).add(0, 0.9, 0);
			world.spawnParticles(BLUE, p.x, p.y, p.z, 1, 0.1, 0.3, 0.1, 0.0);
		}
		BuiltinAbilities.launch(player, look.x * ctx.param("speed", 2.6) * boost.factor(), 0.1, look.z * ctx.param("speed", 2.6) * boost.factor());
		if (boost.full()) {
			world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
		addTempo(player, (float) ctx.param("tempo", 10.0));
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_DASH, 0.9f, 1.1f);
		return true;
	}

	/** Zyklonlauf: XLR8 rennt im Kreis, ein Wirbelsturm zieht Gegner hinein und hebt sie an. */
	private static boolean cyclone(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Boost boost = boost(player);
		Effect cyclone = new Effect(Kind.CYCLONE, ctx.world().getTime() + (long) (ctx.param("seconds", 3.0) * 20 * boost.factor()),
				(float) ctx.param("damage", 5.0) * boost.factor(), ctx.param("radius", 4.5) * (boost.full() ? 1.5 : 1.0));
		cyclone.home = player.getPos();
		EFFECTS.put(player.getUuid(), cyclone);
		ctx.grantInvulnerability(20);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 1.2f, 0.8f);
		return true;
	}

	/** Zeitlupe: im Umkreis laeuft alles fast stehend — Gegner, ihre Geschosse —, XLR8 bleibt schnell. */
	private static boolean slowTime(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Boost boost = boost(player);
		long ticks = (long) (ctx.param("seconds", 6.0) * 20 * boost.factor());
		Effect slow = new Effect(Kind.SLOW_TIME, ctx.world().getTime() + ticks, 0.0f, ctx.param("radius", 8.0) * boost.factor());
		EFFECTS.put(player.getUuid(), slow);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, (int) ticks, 2, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, (int) ticks, 1, false, false));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.5f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.5f);
		return true;
	}

	/** Lichtgeschwindigkeits-Hagel: blitzt von Ziel zu Ziel (Anzahl nach Tempo), Schallmauer: jedes Ziel doppelt. */
	private static boolean barrage(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		List<LivingEntity> targets = new ArrayList<>(BuiltinAbilities.livingAround(ctx, ctx.param("radius", 12.0)));
		if (targets.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		Boost boost = boost(player);
		targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
		int max = (int) ctx.param("targets", 6.0) + (int) (tempo(player) / 20.0f) + (boost.full() ? 4 : 0);
		Effect barrage = new Effect(Kind.BARRAGE, ctx.world().getTime() + 80, (float) ctx.param("damage", 6.0) * boost.factor(), 0.0);
		List<LivingEntity> chosen = targets.subList(0, Math.min(max, targets.size()));
		barrage.targets.addAll(chosen);
		if (boost.full()) {
			barrage.targets.addAll(chosen);
		}
		barrage.home = player.getPos();
		EFFECTS.put(player.getUuid(), barrage);
		ctx.grantInvulnerability((int) ctx.param("invulnerable_ticks", 20) + barrage.targets.size() * 2);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_SHOOT, 1.0f, 1.6f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!isXlr8(player)) {
				if (TEMPO.containsKey(player.getUuid()) || EFFECTS.containsKey(player.getUuid())) {
					AlienMeterSync.clear(player, AlienMeterPayload.TEMPO);
					forget(player.getUuid());
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			if (now % INTERVAL == 0) {
				tickTempo(world, player, now);
			}
			if (tempo(player) >= BLUR && now % 2 == 0) {
				world.spawnParticles(BLUE, player.prevX, player.getY() + 0.9, player.prevZ, 2, 0.15, 0.5, 0.15, 0.0);
			}
			if (tempo(player) >= MAX && player.isSprinting()) {
				trample(world, player, now);
			}
			Effect effect = EFFECTS.get(player.getUuid());
			if (effect != null && !tickEffect(world, player, effect, now)) {
				EFFECTS.remove(player.getUuid());
			}
		}
	}

	private static void tickTempo(ServerWorld world, ServerPlayerEntity player, long now) {
		double[] last = LAST_POS.computeIfAbsent(player.getUuid(), id -> new double[]{player.getX(), player.getZ()});
		double dx = player.getX() - last[0];
		double dz = player.getZ() - last[1];
		last[0] = player.getX();
		last[1] = player.getZ();
		double speed = Math.sqrt(dx * dx + dz * dz) / INTERVAL;
		if (speed > RUN_SPEED) {
			addTempo(player, GAIN_RUNNING * (float) Math.min(2.0, speed / RUN_SPEED));
		} else if (tempo(player) > 0.0f) {
			addTempo(player, speed < 0.05 ? -LOSS_STANDING : -LOSS_STANDING / 4.0f);
		}
		// Tacho im HUD und Aura am Koerper (Client) — Netzpaket nur bei spuerbarer Aenderung
		AlienMeterSync.update(player, AlienMeterPayload.TEMPO, tempo(player));
	}

	/** Schallmauer beim Sprinten: was im Weg steht, wird umgerannt. */
	private static void trample(ServerWorld world, ServerPlayerEntity player, long now) {
		if (now % 4 != 0) {
			return;
		}
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(0.6).stretch(look.multiply(1.2)),
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
			target.damage(world.getDamageSources().playerAttack(player), 4.0f);
			target.takeKnockback(1.0, -look.x, -look.z);
			target.addVelocity(0.0, 0.3, 0.0);
			world.spawnParticles(ParticleTypes.CLOUD, target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
		}
	}

	/** @return false, wenn die Wirkung vorbei ist */
	private static boolean tickEffect(ServerWorld world, ServerPlayerEntity player, Effect effect, long now) {
		if (now >= effect.until) {
			finish(world, player, effect);
			return false;
		}
		return switch (effect.kind) {
			case FLURRY -> tickFlurry(world, player, effect);
			case CYCLONE -> tickCyclone(world, player, effect, now);
			case SLOW_TIME -> tickSlowTime(world, player, effect, now);
			case BARRAGE -> tickBarrage(world, player, effect);
		};
	}

	private static boolean tickFlurry(ServerWorld world, ServerPlayerEntity player, Effect effect) {
		effect.targets.removeIf(t -> !t.isAlive());
		if (effect.targets.isEmpty() || effect.remaining <= 0) {
			finish(world, player, effect);
			return false;
		}
		effect.remaining--;
		for (LivingEntity target : effect.targets) {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().playerAttack(player), effect.damage);
			world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 4, 0.3, 0.3, 0.3, 0.2);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.6f, 1.6f + effect.remaining * 0.03f);
		addTempo(player, 1.0f);
		return true;
	}

	private static boolean tickCyclone(ServerWorld world, ServerPlayerEntity player, Effect effect, long now) {
		// XLR8 rennt im Kreis um den Startpunkt
		double angle = now * 0.9;
		Vec3d around = effect.home.add(Math.cos(angle) * effect.radius * 0.6, 0, Math.sin(angle) * effect.radius * 0.6);
		Vec3d to = around.subtract(player.getPos());
		BuiltinAbilities.launch(player, to.x * 0.8, Math.min(player.getVelocity().y, 0.1), to.z * 0.8);
		for (int i = 0; i < 6; i++) {
			double a = angle + i * MathHelper.TAU / 6;
			double h = (now % 20) / 20.0 * 3.0;
			world.spawnParticles(ParticleTypes.CLOUD, effect.home.x + Math.cos(a) * effect.radius * 0.5, effect.home.y + h,
					effect.home.z + Math.sin(a) * effect.radius * 0.5, 1, 0.0, 0.0, 0.0, 0.0);
		}
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, Box.of(effect.home, effect.radius * 2, 6, effect.radius * 2),
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && e.getPos().squaredDistanceTo(effect.home) <= effect.radius * effect.radius)) {
			Vec3d in = effect.home.subtract(target.getPos()).multiply(1, 0, 1);
			Vec3d swirl = new Vec3d(-in.z, 0, in.x).normalize().multiply(0.25);
			target.setVelocity(in.multiply(0.12).add(swirl).add(0, 0.08, 0));
			target.velocityModified = true;
			if (now % 10 == 0) {
				target.damage(world.getDamageSources().playerAttack(player), effect.damage);
			}
		}
		return true;
	}

	private static boolean tickSlowTime(ServerWorld world, ServerPlayerEntity player, Effect effect, long now) {
		Box box = player.getBoundingBox().expand(effect.radius);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && e.squaredDistanceTo(player) <= effect.radius * effect.radius)) {
			target.setVelocity(target.getVelocity().multiply(0.2, 1.0, 0.2));
			target.velocityModified = true;
			if (now % 20 == 0) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 4, false, true), player);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 30, 2, false, true), player);
			}
		}
		// Geschosse kriechen nur noch
		for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, box, p -> p.getOwner() != player)) {
			projectile.setVelocity(projectile.getVelocity().multiply(0.6));
			projectile.velocityModified = true;
		}
		if (now % 5 == 0) {
			for (int i = 0; i < 24; i++) {
				double a = i * MathHelper.TAU / 24 + now * 0.05;
				world.spawnParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(a) * effect.radius, player.getY() + 0.5,
						player.getZ() + Math.sin(a) * effect.radius, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		return true;
	}

	/** Je Tick ein Ziel: hinblitzen, treffen, weiter. */
	private static boolean tickBarrage(ServerWorld world, ServerPlayerEntity player, Effect effect) {
		effect.targets.removeIf(t -> !t.isAlive());
		if (effect.targets.isEmpty()) {
			finish(world, player, effect);
			return false;
		}
		LivingEntity target = effect.targets.remove(0);
		Vec3d from = player.getPos().add(0, 1.0, 0);
		Vec3d to = target.getPos().add(0.0, target.getHeight() * 0.5, 0.0);
		for (int i = 1; i <= 8; i++) {
			Vec3d p = from.lerp(to, i / 8.0);
			world.spawnParticles(BLUE, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		Vec3d beside = target.getPos().add(target.getPos().subtract(player.getPos()).multiply(1, 0, 1).normalize().multiply(-1.2));
		player.teleport(world, beside.x, target.getY(), beside.z, player.getYaw(), player.getPitch());
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().playerAttack(player), effect.damage);
		world.spawnParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.8f, 1.5f);
		return true;
	}

	private static void finish(ServerWorld world, ServerPlayerEntity player, Effect effect) {
		switch (effect.kind) {
			case FLURRY -> {
				if (effect.radius > 0.0) {
					// Schallmauer-Schlaghagel: Abschluss-Aufwaertshaken
					for (LivingEntity target : effect.targets) {
						target.addVelocity(0.0, 1.0, 0.0);
						target.velocityModified = true;
					}
					world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0, 0, 0, 0);
				}
			}
			case CYCLONE -> {
				for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, Box.of(effect.home, effect.radius * 2, 6, effect.radius * 2),
						e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
					target.setVelocity(0.0, 0.9, 0.0);
					target.velocityModified = true;
				}
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS, 1.2f, 0.6f);
			}
			case BARRAGE -> {
				if (effect.home != null) {
					player.teleport(world, effect.home.x, effect.home.y, effect.home.z, player.getYaw(), player.getPitch());
					world.spawnParticles(ParticleTypes.SONIC_BOOM, effect.home.x, effect.home.y + 1.0, effect.home.z, 1, 0, 0, 0, 0);
				}
			}
			case SLOW_TIME -> {
			}
		}
	}

	private static void forget(UUID id) {
		TEMPO.remove(id);
		AlienMeterSync.forget(id);
		LAST_POS.remove(id);
		EFFECTS.remove(id);
	}
}
