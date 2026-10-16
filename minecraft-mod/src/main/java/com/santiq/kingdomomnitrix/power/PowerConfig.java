package com.santiq.kingdomomnitrix.power;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;

/**
 * Einstellungen fuer Zerstoerung durch Alien-Kraefte. Datei {@code config/kingdomomnitrix-power.json} (wird beim ersten
 * Start mit Standardwerten angelegt). Zusaetzlich abschaltbar per Gamerule {@code kingdomomnitrixAlienGriefing} und
 * per Vanilla-Gamerule {@code mobGriefing}.
 */
public final class PowerConfig {
	public static final GameRules.Key<GameRules.BooleanRule> GRIEFING = GameRuleRegistry.register(
			"kingdomomnitrixAlienGriefing", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kingdomomnitrix-power.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Values values = new Values();

	/** Werte der Datei; Felder mit Standardwert, damit fehlende Eintraege in alten Dateien funktionieren. */
	public static final class Values {
		/** Hauptschalter: Aliens zerstoeren Bloecke */
		public boolean blockDamage = true;
		/** zerstoerte Bloecke lassen ihre Drops fallen (wie eine Explosion: nur ein Teil) */
		public boolean dropBlocks = true;
		/** Anteil der zerstoerten Bloecke, die etwas fallen lassen (0..1) */
		public double dropChance = 0.35;
		/** Vanilla-Gamerule mobGriefing beachten */
		public boolean respectMobGriefing = true;
		/** Faktor auf die Staerke aller Aliens (0,5 = nur weiche Bloecke, 2 = fast alles) */
		public double strengthScale = 1.0;
		/** hoechstens so viele Bloecke pro Tick (verteilt grosse Krater auf mehrere Ticks) */
		public int blocksPerTick = 500;
		/** Anteil zerstoerter Bloecke, die als fliegende Truemmer zu sehen sind (0..1) */
		public double debrisChance = 0.18;
		/** Obergrenze gleichzeitiger Truemmer-Stuecke */
		public int maxDebris = 260;
	}

	private PowerConfig() {
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				Values read = GSON.fromJson(reader, Values.class);
				if (read != null) {
					values = read;
				}
			} catch (IOException | JsonParseException e) {
				KingdomOmnitrix.LOGGER.error("{} ist fehlerhaft, Standardwerte werden genutzt", FILE, e);
				values = new Values();
				return;
			}
		}
		values.dropChance = clamp(values.dropChance, 0.0, 1.0);
		values.debrisChance = clamp(values.debrisChance, 0.0, 1.0);
		values.strengthScale = clamp(values.strengthScale, 0.0, 10.0);
		values.blocksPerTick = Math.max(16, values.blocksPerTick);
		values.maxDebris = Math.max(0, values.maxDebris);
		try (Writer writer = Files.newBufferedWriter(FILE)) {
			GSON.toJson(values, writer);
		} catch (IOException e) {
			KingdomOmnitrix.LOGGER.warn("{} konnte nicht geschrieben werden", FILE, e);
		}
	}

	public static Values get() {
		return values;
	}

	/** Darf in dieser Welt gerade zerstoert werden? */
	public static boolean griefingAllowed(ServerWorld world) {
		if (!values.blockDamage || !world.getGameRules().getBoolean(GRIEFING)) {
			return false;
		}
		return !values.respectMobGriefing || world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
	}

	private static double clamp(double v, double min, double max) {
		return Double.isNaN(v) ? min : Math.max(min, Math.min(max, v));
	}
}
