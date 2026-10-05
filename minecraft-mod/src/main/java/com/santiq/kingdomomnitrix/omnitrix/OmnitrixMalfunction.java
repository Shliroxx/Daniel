package com.santiq.kingdomomnitrix.omnitrix;

import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

/**
 * Fehlfunktionen bei hoher Hitze ({@link Malfunctions}): falsches Alien beim Verwandeln, Zeitdrift waehrend der
 * Verwandlung. Wuerfelt nur auf dem Server; jede Fehlfunktion meldet sich als Hologramm mit Grund und Gegenmittel.
 */
public final class OmnitrixMalfunction {
	public static final GameRules.Key<GameRules.BooleanRule> ENABLED = GameRuleRegistry.register(
			"kingdomomnitrixOmnitrixMalfunctions", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
	/** Mindest-Restzeit, die eine Zeitdrift uebrig laesst. */
	private static final long MIN_REMAINING = 100L;

	private OmnitrixMalfunction() {
	}

	/** Klasse laden (Spielregel registrieren). */
	static void register() {
	}

	private static boolean active(ServerPlayerEntity player) {
		return player.getServerWorld().getGameRules().getBoolean(ENABLED);
	}

	private static int mastery(ServerPlayerEntity player, Identifier alien) {
		return AlienMasteryManager.get(player).level(alien);
	}

	/**
	 * Wuerfelt beim Verwandeln: liefert ein anderes freigeschaltetes Alien, wenn die DNA-Auswahl gestoert ist.
	 * Leer = alles normal.
	 */
	public static Optional<Identifier> wrongAlien(ServerPlayerEntity player, Identifier requested) {
		if (!active(player)) {
			return Optional.empty();
		}
		OmnitrixProfile profile = OmnitrixCore.profile(player);
		Malfunctions rules = profile.malfunctions();
		float chance = rules.chance(rules.wrongAlienChance(), OmnitrixCore.heat(player), mastery(player, requested),
				OmnitrixCore.state(player).masterControl());
		if (chance <= 0.0f || player.getRandom().nextFloat() >= chance) {
			return Optional.empty();
		}
		List<Identifier> others = new ArrayList<>();
		for (Identifier id : HeroDataAccess.get(player).unlockedAliens()) {
			// nur Aliens, in die das Geraet jetzt auch regulaer verwandeln koennte (sonst Ablehnung statt Fehlgriff)
			if (!id.equals(requested) && AlienRegistry.get(player.getServerWorld().getRegistryManager(), id).isPresent()
					&& OmnitrixCore.checkTransform(player, id).isEmpty()) {
				others.add(id);
			}
		}
		if (others.isEmpty()) {
			return Optional.empty();
		}
		others.sort(null);
		return Optional.of(others.get(player.getRandom().nextInt(others.size())));
	}

	/** Meldung nach einem Fehlgriff: „FEHLFUNKTION · DNA-Auswahl gestoert: A → B · Hitze senken“. */
	public static void announceWrongAlien(ServerPlayerEntity player, Identifier requested, Identifier got) {
		holo(player, Text.translatable("holo.kingdomomnitrix.malfunction.wrong_alien",
						TransformationManager.alienName(requested), TransformationManager.alienName(got).formatted(Formatting.BOLD)),
				Optional.of(got));
	}

	/**
	 * Zeitdrift: alle {@code drift_interval_seconds} ein Wurf fuer das aktive Alien. Liefert die verlorenen Ticks
	 * (0 = nichts).
	 */
	public static long rollDrift(ServerPlayerEntity player, Identifier alien, long now) {
		if (!active(player)) {
			return 0L;
		}
		Malfunctions rules = OmnitrixCore.profile(player).malfunctions();
		if (now % (rules.driftIntervalSeconds() * 20L) != 0L) {
			return 0L;
		}
		float chance = rules.chance(rules.driftChance(), OmnitrixCore.heat(player), mastery(player, alien),
				OmnitrixCore.state(player).masterControl());
		if (chance <= 0.0f || player.getRandom().nextFloat() >= chance) {
			return 0L;
		}
		return rules.driftSeconds() * 20L;
	}

	/** Meldung nach einer Zeitdrift. */
	public static void announceDrift(ServerPlayerEntity player, long lostTicks, Identifier alien) {
		holo(player, Text.translatable("holo.kingdomomnitrix.malfunction.drift", lostTicks / 20), Optional.of(alien));
	}

	/** Mindest-Restzeit nach einer Zeitdrift (Ticks). */
	public static long minRemaining() {
		return MIN_REMAINING;
	}

	private static void holo(ServerPlayerEntity player, Text body, Optional<Identifier> alien) {
		OmnitrixOs.send(player, OmnitrixOs.Event.MALFUNCTION, body, Optional.of(Text.translatable("holo.kingdomomnitrix.malfunction.hint")), alien);
		OmnitrixCore.cue(player, OmnitrixCue.MALFUNCTION);
	}
}
