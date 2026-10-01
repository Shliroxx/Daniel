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
