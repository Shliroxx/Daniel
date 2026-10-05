package com.santiq.kingdomomnitrix.progression;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

/**
 * Meisterschaft pro Alien: jedes Alien sammelt eigene Erfahrung, solange man es benutzt, und steigt
 * unabhaengig von der Heldenstufe auf (1–{@link #MAX_LEVEL}).
 */
public record AlienMastery(Map<Identifier, Integer> experience) {
	public static final int MAX_LEVEL = 10;
	/** Je Meisterschaftsstufe ueber 1: Verwandlung +5 % Dauer, Faehigkeiten −3 % Abklingzeit. */
	public static final float DURATION_PER_LEVEL = 0.05f;
	public static final float COOLDOWN_PER_LEVEL = 0.03f;

	public static final AlienMastery EMPTY = new AlienMastery(Map.of());

	public static final Codec<AlienMastery> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.intRange(0, Integer.MAX_VALUE))
			.xmap(AlienMastery::new, AlienMastery::experience);

	public static final PacketCodec<ByteBuf, AlienMastery> PACKET_CODEC = PacketCodecs.codec(CODEC);

	public AlienMastery {
		experience = Map.copyOf(experience);
	}

	/** Gesamt-Erfahrung, ab der eine Stufe erreicht ist: 150, 450, 900 … 6750 fuer Stufe 10. */
	public static int experienceFor(int level) {
		return 75 * (level - 1) * level;
	}

	public static int levelFor(int experience) {
		int level = 1;
		while (level < MAX_LEVEL && experience >= experienceFor(level + 1)) {
			level++;
		}
		return level;
	}

	public int experience(Identifier alien) {
		return experience.getOrDefault(alien, 0);
	}

	public int level(Identifier alien) {
		return levelFor(experience(alien));
	}

	// --- Meisterschafts-Boni (Phase G) -------------------------------------------------------------
	// Stufe 1–10 je Alien. Faehigkeiten 4–6 schalten sich ueber unlock_level im Datenpaket frei (Standard 3/5/8).
	/** Ab dieser Stufe kosten Faehigkeiten weniger Energie. */
	public static final int LEVEL_ENERGY_DISCOUNT = 4;
	public static final float ENERGY_DISCOUNT = 0.15f;
	/** Ab dieser Stufe erzeugt das Verwandeln in dieses Alien weniger Hitze. */
	public static final int LEVEL_TRANSFORM_HEAT = 6;
	public static final float TRANSFORM_HEAT_FACTOR = 0.7f;
	/** Ab dieser Stufe hat das Alien mehr Lebenspunkte. */
	public static final int LEVEL_HEALTH = 7;
	public static final float HEALTH_BONUS = 4.0f;
	/** Ab dieser Stufe steigt die Hitze waehrend der Verwandlung nur halb so schnell. */
	public static final int LEVEL_ACTIVE_HEAT = 9;
	public static final float ACTIVE_HEAT_FACTOR = 0.5f;
	/** Gemeistert: dieses Alien erzeugt keine Hitze mehr (Master Control fuer genau dieses Alien). */
	public static final int LEVEL_MASTERED = MAX_LEVEL;

	/** Energiekosten einer Faehigkeit bei dieser Meisterschaftsstufe. */
	public static float energyCost(float base, int level) {
		return level >= LEVEL_ENERGY_DISCOUNT ? base * (1.0f - ENERGY_DISCOUNT) : base;
	}

	/** Hitze-Faktor beim Verwandeln in das Alien. */
	public static float transformHeatFactor(int level) {
		return level >= LEVEL_MASTERED ? 0.0f : level >= LEVEL_TRANSFORM_HEAT ? TRANSFORM_HEAT_FACTOR : 1.0f;
	}

	/** Hitze-Faktor, solange das Alien aktiv ist. */
	public static float activeHeatFactor(int level) {
		return level >= LEVEL_MASTERED ? 0.0f : level >= LEVEL_ACTIVE_HEAT ? ACTIVE_HEAT_FACTOR : 1.0f;
	}

	/** Zusaetzliche Lebenspunkte des Aliens. */
	public static float healthBonus(int level) {
		return level >= LEVEL_HEALTH ? HEALTH_BONUS : 0.0f;
	}

	public float durationBonus(Identifier alien) {
		return DURATION_PER_LEVEL * (level(alien) - 1);
	}

	public float cooldownReduction(Identifier alien) {
		return COOLDOWN_PER_LEVEL * (level(alien) - 1);
	}

	public AlienMastery add(Identifier alien, int amount) {
		if (amount <= 0 || level(alien) >= MAX_LEVEL) {
			return this;
		}
		Map<Identifier, Integer> next = new HashMap<>(experience);
		long total = (long) experience(alien) + amount;
		next.put(alien, (int) Math.min(total, experienceFor(MAX_LEVEL)));
		return new AlienMastery(next);
	}
}
