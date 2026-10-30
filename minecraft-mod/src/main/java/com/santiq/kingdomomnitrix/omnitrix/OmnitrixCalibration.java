package com.santiq.kingdomomnitrix.omnitrix;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.MathHelper;

/**
 * Kalibrierung des Omnitrix an der Kalibrier-Werkbank (pro Spieler, persistent, an alle synchronisiert). Drei Module mit je
 * {@link #MAX_LEVEL} Stufen teilen sich {@link #CAPACITY} Kalibrierpunkte — alles auf Maximum geht nicht, jede Wahl hat
 * einen Preis:
 *
 * <ul>
 *   <li>{@link Module#COOLING Kuehlung}: weniger Hitze als Alien, schnellere Abkuehlung.</li>
 *   <li>{@link Module#CORE Kern}: laengere Verwandlung, aber mehr Hitze pro Verwandlung.</li>
 *   <li>{@link Module#BANDWIDTH Bandbreite}: kuerzeres Nachladen, aber instabiler (mehr Fehlfunktionen).</li>
 * </ul>
 *
 * Die Farbe ({@link #color}) waehlt das Farbmodul (Phase M); sie wirkt nicht auf die Werte.
 * Omnitrix-Upgrades: die Stufe ({@link #tier}, 1 bis {@link #MAX_TIER}) bringt je Stufe einen Kalibrierpunkt mehr, etwas
 * kuerzeres Nachladen, laengere Verwandlung und einen Steckplatz fuer {@link OmnitrixBoost Boost-Module} ({@link #boosts}).
 * {@link #apply(OmnitrixProfile)} rechnet die Kalibrierung in das Geraete-Profil ein — alle Systeme lesen das Ergebnis.
 */
public record OmnitrixCalibration(int cooling, int core, int bandwidth, String color, int tier, List<String> boosts) {
	public static final int MAX_LEVEL = 3;
	public static final int CAPACITY = 5;
	public static final String DEFAULT_COLOR = "green";
	public static final int MAX_TIER = 5;
	public static final OmnitrixCalibration DEFAULT = new OmnitrixCalibration(0, 0, 0, DEFAULT_COLOR);
	/** Je Omnitrix-Stufe ueber 1: Nachladen und Verwandlungsdauer */
	public static final float TIER_RECHARGE = 0.04f;
	public static final float TIER_DURATION = 0.05f;

	// Wirkung je Stufe
	public static final float COOLING_ACTIVE_HEAT = 0.12f;
	public static final float COOLING_DECAY = 0.20f;
	public static final float CORE_DURATION = 0.15f;
	public static final float CORE_TRANSFORM_HEAT = 0.08f;
	public static final float BANDWIDTH_RECHARGE = 0.12f;
	public static final float BANDWIDTH_MALFUNCTION = 0.25f;

	public static final Codec<OmnitrixCalibration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.intRange(0, MAX_LEVEL).optionalFieldOf("cooling", 0).forGetter(OmnitrixCalibration::cooling),
			Codec.intRange(0, MAX_LEVEL).optionalFieldOf("core", 0).forGetter(OmnitrixCalibration::core),
			Codec.intRange(0, MAX_LEVEL).optionalFieldOf("bandwidth", 0).forGetter(OmnitrixCalibration::bandwidth),
			Codec.STRING.optionalFieldOf("color", DEFAULT_COLOR).forGetter(OmnitrixCalibration::color),
			Codec.intRange(1, MAX_TIER).optionalFieldOf("tier", 1).forGetter(OmnitrixCalibration::tier),
			Codec.STRING.listOf().optionalFieldOf("boosts", List.of()).forGetter(OmnitrixCalibration::boosts)
	).apply(instance, OmnitrixCalibration::new));

	public static final PacketCodec<ByteBuf, OmnitrixCalibration> PACKET_CODEC = PacketCodecs.codec(CODEC);

	public enum Module {
		COOLING, CORE, BANDWIDTH;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Optional<Module> byKey(String key) {
			for (Module module : values()) {
				if (module.key().equals(key)) {
					return Optional.of(module);
				}
			}
			return Optional.empty();
		}
	}

	/** Kosten einer Stufe: Bolts und Materialien (Raritanium, Mythril-Splitter, Orichalcum). */
	public record Cost(int bolts, int raritanium, int mythril, int orichalcum) {
	}

	public OmnitrixCalibration {
		cooling = MathHelper.clamp(cooling, 0, MAX_LEVEL);
		core = MathHelper.clamp(core, 0, MAX_LEVEL);
		bandwidth = MathHelper.clamp(bandwidth, 0, MAX_LEVEL);
		color = color == null || color.isBlank() ? DEFAULT_COLOR : color;
		tier = MathHelper.clamp(tier, 1, MAX_TIER);
		boosts = boosts == null ? List.of() : List.copyOf(boosts.stream().filter(id -> OmnitrixBoost.byId(id).isPresent()).limit(tier).toList());
	}

	public OmnitrixCalibration(int cooling, int core, int bandwidth, String color) {
		this(cooling, core, bandwidth, color, 1, List.of());
	}

	/** Kosten, um das Omnitrix auf {@code tier} (2..5) zu heben. */
	public static Cost tierCost(int tier) {
		return switch (tier) {
			case 2 -> new Cost(1500, 4, 2, 0);
			case 3 -> new Cost(3000, 6, 4, 1);
			case 4 -> new Cost(6000, 8, 6, 2);
			default -> new Cost(12000, 10, 8, 4);
		};
	}

	/** Kalibrierpunkte insgesamt: Grundwert plus einer je Stufe ueber 1. */
	public int capacity() {
		return CAPACITY + tier - 1;
	}

	/** Steckplaetze fuer Boost-Module (einer je Stufe). */
	public int slots() {
		return tier;
	}

	public int count(OmnitrixBoost boost) {
		return (int) boosts.stream().filter(boost.id()::equals).count();
	}

	public OmnitrixCalibration withTier(int value) {
		return new OmnitrixCalibration(cooling, core, bandwidth, color, value, boosts);
	}

	public OmnitrixCalibration withBoosts(List<String> value) {
		return new OmnitrixCalibration(cooling, core, bandwidth, color, tier, value);
	}

	/** Kosten, um ein Modul auf {@code level} (1..3) zu heben. */
	public static Cost cost(int level) {
		return switch (level) {
			case 1 -> new Cost(300, 2, 0, 0);
			case 2 -> new Cost(700, 4, 3, 0);
			default -> new Cost(1500, 6, 4, 1);
		};
	}

	public int level(Module module) {
		return switch (module) {
			case COOLING -> cooling;
			case CORE -> core;
			case BANDWIDTH -> bandwidth;
		};
	}

	/** Belegte Kalibrierpunkte. */
	public int used() {
		return cooling + core + bandwidth;
	}

	public int free() {
		return capacity() - used();
	}

	/** Kann dieses Modul eine Stufe hoeher? (Hoechststufe, freie Punkte) */
	public boolean canRaise(Module module) {
		return level(module) < MAX_LEVEL && free() > 0;
	}

	public OmnitrixCalibration withLevel(Module module, int level) {
		return switch (module) {
			case COOLING -> new OmnitrixCalibration(level, core, bandwidth, color, tier, boosts);
			case CORE -> new OmnitrixCalibration(cooling, level, bandwidth, color, tier, boosts);
			case BANDWIDTH -> new OmnitrixCalibration(cooling, core, level, color, tier, boosts);
		};
	}

	public OmnitrixCalibration withColor(String value) {
		return new OmnitrixCalibration(cooling, core, bandwidth, value, tier, boosts);
	}

	/** Profil mit eingerechneter Kalibrierung (unveraendert, wenn nichts kalibriert ist). */
	public OmnitrixProfile apply(OmnitrixProfile profile) {
		if (used() == 0 && tier == 1 && boosts.isEmpty()) {
			return profile;
		}
		int above = tier - 1;
		float boostDuration = 1.0f + OmnitrixBoost.DURATION_BONUS * count(OmnitrixBoost.DURATION) + TIER_DURATION * above;
		float boostHeat = Math.max(0.1f, 1.0f - OmnitrixBoost.COOLING_HEAT * count(OmnitrixBoost.COOLING));
		float boostRecharge = Math.max(0.1f, 1.0f - OmnitrixBoost.RECHARGE_COOLDOWN * count(OmnitrixBoost.RECHARGE) - TIER_RECHARGE * above);
		Malfunctions m = profile.malfunctions();
		float unstable = 1.0f + BANDWIDTH_MALFUNCTION * bandwidth;
		Malfunctions malfunctions = new Malfunctions(m.enabled(), m.startHeat(), Math.min(1.0f, m.wrongAlienChance() * unstable),
				Math.min(1.0f, m.driftChance() * unstable), m.driftSeconds(), m.driftIntervalSeconds());
		return new OmnitrixProfile(
				MathHelper.clamp(profile.heatPerTransform() * (1.0f + CORE_TRANSFORM_HEAT * core) * boostHeat, 0.0f, 1.0f),
				profile.heatPerSecondActive() * (1.0f - COOLING_ACTIVE_HEAT * cooling) * boostHeat,
				profile.heatDecayPerSecond() * (1.0f + COOLING_DECAY * cooling),
				profile.heatWarning(),
				profile.overheatLockSeconds(),
				profile.cooldownMultiplier() * (1.0f - BANDWIDTH_RECHARGE * bandwidth) * boostRecharge,
				profile.durationMultiplier() * (1.0f + CORE_DURATION * core) * boostDuration,
				profile.manualRevertCooldown(),
				profile.confirmSeconds(),
				profile.masterControl(),
				profile.quickChangeHeat(),
				profile.quickChangeKeep(),
				profile.failsafeCooldownSeconds(),
				profile.failsafeHeat(),
				malfunctions);
	}
}
