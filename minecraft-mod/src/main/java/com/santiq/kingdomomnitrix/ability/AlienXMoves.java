package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Alien X, Kraefte 2.0: Sternenlanze, Realitaetsanker, Leerenriss, Zurueckspulen, Sternbild, Genesis; Kombos
 * Kometenschlag, Schwerkraftsturz, Stasisberuehrung, Sternensturz, Ausloeschen; Passive Himmlischer Einklang,
 * Celestialsapien-Koerper und Serenas Gnade. Jede Tasten-Kraft stimmt ueber {@link AlienXAbilities#vote} ab (Serena
 * oder Bellicus) — die Kombos sind reine Koerperkraft und stimmen nicht ab.
 */
final class AlienXMoves {
	private static final Identifier ALIEN_X = KingdomOmnitrix.id("alien_x");
	private static final int STAR = 0xFFFFFF;
	private static final int NEBULA = 0x9FD8FF;
	private static final int GOLD = 0xFFE27A;
	/** Zurueckspulen: Positionen und Leben der letzten Sekunden (alle 10 Ticks ein Eintrag) */
	private static final Map<UUID, Deque<Object[]>> HISTORY = new HashMap<>();
	private static final int HISTORY_SIZE = 7;
	/** Serenas Gnade: naechstmoeglicher Zeitpunkt */
	private static final Map<UUID, Long> GRACE = new HashMap<>();

	private AlienXMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("star_lance"), AlienXMoves::starLance);
		AbilityRegistry.register(KingdomOmnitrix.id("reality_anchor"), AlienXMoves::realityAnchor);
		AbilityRegistry.register(KingdomOmnitrix.id("void_rift"), AlienXMoves::voidRift);
		AbilityRegistry.register(KingdomOmnitrix.id("rewind"), AlienXMoves::rewind);
		AbilityRegistry.register(KingdomOmnitrix.id("constellation"), AlienXMoves::constellation);
		AbilityRegistry.register(KingdomOmnitrix.id("genesis"), AlienXMoves::genesis);
		AbilityRegistry.register(KingdomOmnitrix.id("comet_strike"), AlienXMoves::cometStrike);
		AbilityRegistry.register(KingdomOmnitrix.id("gravity_slam"), AlienXMoves::gravitySlam);
		AbilityRegistry.register(KingdomOmnitrix.id("stasis_touch"), AlienXMoves::stasisTouch);
		AbilityRegistry.register(KingdomOmnitrix.id("starfall_dive"), AlienXMoves::starfallDive);
		AbilityRegistry.register(KingdomOmnitrix.id("erase"), AlienXMoves::erase);
		ServerTickEvents.END_SERVER_TICK.register(AlienXMoves::recordHistory);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			HISTORY.remove(handler.getPlayer().getUuid());
			GRACE.remove(handler.getPlayer().getUuid());
		});
		PassiveRegistry.register(KingdomOmnitrix.id("celestial_harmony"), new AlienPassive() {
			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				// je einiger die Stimmen, desto staerker jeder Treffer
				return amount * (1.0f + (float) ctx.param("per_harmony", 0.1) * AlienXAbilities.harmony(ctx.player()));
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("celestialsapien_body"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Magie und Explosionen aus diesem Universum beruehren ihn kaum
				return source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isIn(DamageTypeTags.WITCH_RESISTANT_TO)
						? amount * (float) ctx.param("reduction", 0.6) : amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("serenas_grace"), new AlienPassive() {
			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				// faellt Alien X unter 30 % Leben, greift Serena ein — einmal pro Minute
				ServerPlayerEntity x = ctx.player();
				long now = ctx.now();
				if (x.getHealth() > x.getMaxHealth() * ctx.param("threshold", 0.3) || now < GRACE.getOrDefault(x.getUuid(), 0L)) {
					return;
				}
				GRACE.put(x.getUuid(), now + (long) ctx.param("cooldown_ticks", 1200.0));
				x.heal(x.getMaxHealth() * (float) ctx.param("heal", 0.3));
				Fx.flash(ctx.world(), AlienKit.body(x), GOLD, 2.0f);
				Fx.sparks(ctx.world(), AlienKit.body(x), GOLD, 0.4f, 20);
				ctx.world().playSound(null, x.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.8f);
			}
		});
	}

	private static void recordHistory(MinecraftServer server) {
		if (server.getTicks() % 10 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (TransformationManager.get(player).activeAlien().filter(ALIEN_X::equals).isEmpty()) {
				HISTORY.remove(player.getUuid());
				continue;
			}
			Deque<Object[]> history = HISTORY.computeIfAbsent(player.getUuid(), id -> new ArrayDeque<>());
			history.addLast(new Object[]{player.getPos(), player.getHealth(), player.getServerWorld().getRegistryKey()});
			while (history.size() > HISTORY_SIZE) {
				history.removeFirst();
			}
		}
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Sternenlanze (Bellicus): eine Lanze aus Sternenlicht faellt vom Himmel auf den Zielpunkt. */
	private static boolean starLance(AbilityContext ctx) {
		float power = AlienXAbilities.vote(ctx, AlienXAbilities.BELLICUS);
		if (power == 0.0f) {
			return true;
		}
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d at = AlienKit.ground(world, AlienKit.aim(world, x, ctx.param("range", 32.0)), 10.0);
		Fx.beam(world, at.add(0, 30, 0), at, NEBULA, 1.4f * power, 8);
		Fx.beam(world, at.add(0, 30, 0), at, STAR, 0.5f * power, 8);
		AlienKit.blast(world, x, at.add(0, 0.5, 0), ctx.param("radius", 2.5) * power, (float) ctx.param("damage", 8.0) * power, 0.8, 0.5);
		Fx.explosion(world, at.add(0, 0.5, 0), NEBULA, 1.6f * power);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.2f, 1.6f);
		return true;
	}

	/** Realitaetsanker (Serena): alle Gegner im Umkreis werden an ihrer Stelle festgeschrieben — 3 s keine Bewegung. */
	private static boolean realityAnchor(AbilityContext ctx) {
		float power = AlienXAbilities.vote(ctx, AlienXAbilities.SERENA);
		if (power == 0.0f) {
			return true;
		}
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 3.0) * 20 * power);
		for (LivingEntity target : AlienKit.around(world, x, x.getPos(), ctx.param("radius", 10.0))) {
			AlienKit.hold(world, target, ticks, AlienXAbilities.STAR, false);
			Fx.beam(world, target.getPos().add(0, 3.0, 0), target.getPos(), NEBULA, 0.2f, ticks);
		}
		Fx.shockwave(world, x.getPos().add(0, 0.2, 0), NEBULA, (float) ctx.param("radius", 10.0), 14);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.2f, 1.2f);
		return true;
	}

	/** Leerenriss (Bellicus): ein Riss im All am Zielpunkt saugt 3 s lang alles an — dann implodiert er. */
	private static boolean voidRift(AbilityContext ctx) {
		float power = AlienXAbilities.vote(ctx, AlienXAbilities.BELLICUS);
		if (power == 0.0f) {
			return true;
		}
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d center = AlienKit.ground(world, AlienKit.aim(world, x, ctx.param("range", 20.0)), 8.0).add(0, 1.5, 0);
		double radius = ctx.param("radius", 7.0) * power;
		float damage = (float) ctx.param("damage", 2.0) * power;
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		PowerTasks.start(x, ticks + 1, 2, (w, p, age) -> {
			for (int i = 0; i < 4; i++) {
				double a = age * 0.3 + i * MathHelper.TAU / 4;
				Fx.sparks(w, center.add(Math.cos(a) * 1.2, Math.sin(a * 0.5) * 0.5, Math.sin(a) * 1.2), NEBULA, 0.02f, 1);
			}
			for (LivingEntity target : AlienKit.around(w, p, center, radius)) {
				Vec3d pull = center.subtract(target.getPos()).normalize().multiply(0.25);
				target.addVelocity(pull.x, pull.y * 0.5, pull.z);
				target.velocityModified = true;
				if (age % 20 == 0) {
					AlienKit.magic(w, p, target, damage);
				}
			}
			if (age < ticks) {
				return true;
			}
			AlienKit.blast(w, p, center, 3.0 * power, (float) ctx.param("implode_damage", 10.0) * power, 1.4, 0.6);
			Fx.flash(w, center, STAR, 4.0f);
			Fx.shockwave(w, center, NEBULA, (float) (3.0 * power), 10);
			w.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.2f, 0.6f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.6f);
		return true;
	}

	/** Zurueckspulen (Serena): Alien X kehrt an den Ort und zum Leben von vor 3 Sekunden zurueck. */
	private static boolean rewind(AbilityContext ctx) {
		ServerPlayerEntity x = ctx.player();
		Deque<Object[]> history = HISTORY.get(x.getUuid());
		if (history == null || history.isEmpty() || history.peekFirst()[2] != x.getServerWorld().getRegistryKey()) {
			x.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.alien_x_no_past").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		float power = AlienXAbilities.vote(ctx, AlienXAbilities.SERENA);
		if (power == 0.0f) {
			return true;
		}
		Object[] past = history.peekFirst();
		Vec3d from = x.getPos();
		Vec3d to = (Vec3d) past[0];
		AlienKit.line(ctx.world(), from.add(0, 1, 0), to.add(0, 1, 0), AlienXAbilities.NEBULA, 2.0);
		x.requestTeleport(to.x, to.y, to.z);
		x.fallDistance = 0.0f;
		x.setHealth(Math.max(x.getHealth(), Math.min(x.getMaxHealth(), (float) past[1] * (power > 1.0f ? 1.5f : 1.0f))));
		history.clear();
		Fx.flash(ctx.world(), to.add(0, 1, 0), NEBULA, 2.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_PORTAL_TRAVEL, 0.3f, 2.0f);
		return true;
	}

	/** Sternbild (Bellicus): verbindet bis zu 5 Gegner zu einem Sternbild — jeder wird getroffen, leuchtet und wird langsam. */
	private static boolean constellation(AbilityContext ctx) {
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		List<LivingEntity> targets = new ArrayList<>(AlienKit.around(world, x, x.getPos(), ctx.param("radius", 16.0)));
		if (targets.isEmpty()) {
			x.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		float power = AlienXAbilities.vote(ctx, AlienXAbilities.BELLICUS);
		if (power == 0.0f) {
			return true;
		}
		targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(x)));
		List<LivingEntity> stars = targets.subList(0, Math.min(targets.size(), (int) ctx.param("targets", 5.0)));
		float damage = (float) ctx.param("damage", 6.0) * power;
		Vec3d prev = AlienKit.body(x);
		for (LivingEntity target : stars) {
			Vec3d at = AlienKit.body(target);
			AlienKit.line(world, prev, at, AlienXAbilities.STAR, 2.5);
			AlienKit.magic(world, x, target, damage);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0, false, false), x);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1, false, true), x);
			Fx.flash(world, at, STAR, 1.2f);
			prev = at;
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.6f);
		return true;
	}

	/**
	 * Genesis (Serena, Ultimate): ein Stueck neue Wirklichkeit um Alien X (8 s) — Verbuendete darin regenerieren und
	 * sind geschuetzt, Gegner langsam und schwach; zum Schluss heilt sie alle Verbuendeten vollstaendig.
	 */
	private static boolean genesis(AbilityContext ctx) {
		float power = AlienXAbilities.vote(ctx, AlienXAbilities.SERENA);
		if (power == 0.0f) {
			return true;
		}
		ServerPlayerEntity x = ctx.player();
		Vec3d center = x.getPos();
		double radius = ctx.param("radius", 10.0) * (power > 1.0f ? 1.5 : 1.0);
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		PowerTasks.start(x, ticks + 1, 10, (world, p, age) -> {
			for (int i = 0; i < 16; i++) {
				double a = i * MathHelper.TAU / 16 + age * 0.02;
				Fx.sparks(world, center.add(Math.cos(a) * radius, 0.3 + (i % 4) * 0.5, Math.sin(a) * radius), i % 2 == 0 ? GOLD : NEBULA, 0.02f, 1);
			}
			List<ServerPlayerEntity> allies = new ArrayList<>(world.getPlayers(o -> o.getPos().distanceTo(center) <= radius && (o == p || PartyRules.isAlly(p, o))));
			for (ServerPlayerEntity ally : allies) {
				ally.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 30, 1, false, false));
				ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30, 1, false, false));
				if (age >= ticks) {
					ally.setHealth(ally.getMaxHealth());
				}
			}
			for (LivingEntity target : AlienKit.around(world, p, center, radius)) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 2, false, true), p);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 30, 1, false, true), p);
			}
			if (age >= ticks) {
				Fx.flash(world, center.add(0, 1, 0), GOLD, 6.0f);
				world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 1.2f);
			}
			return true;
		});
		Fx.shockwave(ctx.world(), center.add(0, 0.2, 0), GOLD, (float) radius, 16);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.5f, 1.0f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Kometenschlag (Sprint + Schlag): Treffer mit Sternenschweif — das Ziel fliegt weit. */
	private static boolean cometStrike(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity x = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(x);
		AlienKit.melee(ctx.world(), x, target, (float) ctx.param("damage", 7.0));
		target.setVelocity(dir.x * 1.6, 0.5, dir.z * 1.6);
		target.velocityModified = true;
		AlienKit.line(ctx.world(), AlienKit.body(x).subtract(dir.multiply(3.0)), AlienKit.body(target), AlienXAbilities.STAR, 2.0);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 1.4f, 0.6f);
		return true;
	}

	/** Schwerkraftsturz (Schlag in der Luft): verdoppelt die Schwerkraft des Ziels — es kracht zu Boden. */
	private static boolean gravitySlam(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity x = ctx.player();
		AlienKit.melee(ctx.world(), x, target, (float) ctx.param("damage", 7.0));
		target.setVelocity(0.0, -2.0, 0.0);
		target.velocityModified = true;
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2, false, true), x);
		Fx.shockwave(ctx.world(), target.getPos().add(0, 0.2, 0), NEBULA, 2.5f, 8);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.8f);
		return true;
	}

	/** Stasisberuehrung (Schleichen + Schlag): haelt die Zeit des Ziels an — 2 s regungslos. */
	private static boolean stasisTouch(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity x = ctx.player();
		AlienKit.melee(ctx.world(), x, target, (float) ctx.param("damage", 3.0));
		AlienKit.hold(ctx.world(), target, (int) ctx.param("hold_ticks", 40.0), AlienXAbilities.STAR, false);
		Fx.flash(ctx.world(), AlienKit.body(target), NEBULA, 1.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.6f);
		return true;
	}

	/** Sternensturz (hoch in der Luft schleichen): faellt als Stern — Einschlag nach Fallhoehe. */
	private static boolean starfallDive(AbilityContext ctx) {
		ServerPlayerEntity x = ctx.player();
		double startY = x.getY() + x.fallDistance;
		float damage = (float) ctx.param("damage", 7.0);
		double radius = ctx.param("radius", 3.5);
		BuiltinAbilities.launch(x, x.getVelocity().x * 0.2, -ctx.param("dive", 2.2), x.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(x, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				Fx.sparks(world, p.getPos().add(0, 1.0, 0), STAR, 0.1f, 2);
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			AlienKit.blast(world, p, p.getPos().add(0, 0.5, 0), radius * boost, damage * boost, 0.9, 0.5);
			Fx.explosion(world, p.getPos().add(0, 0.5, 0), NEBULA, (float) (radius * boost * 0.6));
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 1.4f);
			return false;
		});
		return true;
	}

	/** Ausloeschen (Schlag gegen geschwaechtes Ziel): schreibt das Ziel aus der Wirklichkeit — Schaden nach fehlendem Leben. */
	private static boolean erase(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d at = AlienKit.body(target);
		float damage = (float) (ctx.param("damage", 8.0) + (target.getMaxHealth() - target.getHealth()) * ctx.param("missing_factor", 0.4));
		AlienKit.magic(world, x, target, damage);
		for (int i = 0; i < 12; i++) {
			double a = i * MathHelper.TAU / 12;
			Fx.sparks(world, at.add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5), STAR, 0.0f, 2);
		}
		Fx.flash(world, at, STAR, 3.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_END_PORTAL_SPAWN, 0.5f, 2.0f);
		return true;
	}
}
