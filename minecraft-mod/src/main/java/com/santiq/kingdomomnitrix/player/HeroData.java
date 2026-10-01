package com.santiq.kingdomomnitrix.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Dauerhafter Spielerzustand: Fortschritt, Bolt-Konto, Freischaltungen und Story-Flags.
 *
 * <p>Unveraenderlich: Jede Aenderung erzeugt ein neues Objekt und wird ueber
 * {@link HeroDataAccess#update} gesetzt. Nur so erkennt die Attachment-API die Aenderung,
 * speichert sie und synchronisiert sie an den Client.</p>
 *
 * <p>Ungueltige Werte in Speicherstaenden (z. B. von Hand editiert) fallen auf die Standardwerte
 * zurueck, statt den Spieler-Ladevorgang abzubrechen.</p>
 */
public record HeroData(int level, int experience, int bolts, Set<Identifier> unlockedAliens, Set<String> storyFlags) {
	public static final int MIN_LEVEL = 1;
	public static final int MAX_LEVEL = 99;
	public static final int MAX_BOLTS = 9_999_999;

	public static final HeroData DEFAULT = new HeroData(MIN_LEVEL, 0, 0, Set.of(), Set.of());

	public static final Codec<HeroData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.intRange(MIN_LEVEL, MAX_LEVEL).lenientOptionalFieldOf("level", MIN_LEVEL).forGetter(HeroData::level),
			Codec.intRange(0, Integer.MAX_VALUE).lenientOptionalFieldOf("experience", 0).forGetter(HeroData::experience),
			Codec.intRange(0, MAX_BOLTS).lenientOptionalFieldOf("bolts", 0).forGetter(HeroData::bolts),
			Identifier.CODEC.listOf().<Set<Identifier>>xmap(Set::copyOf, List::copyOf)
					.lenientOptionalFieldOf("unlocked_aliens", Set.of()).forGetter(HeroData::unlockedAliens),
			Codec.STRING.listOf().<Set<String>>xmap(Set::copyOf, List::copyOf)
					.lenientOptionalFieldOf("story_flags", Set.of()).forGetter(HeroData::storyFlags)
	).apply(instance, HeroData::new));

	public static final PacketCodec<ByteBuf, HeroData> PACKET_CODEC = PacketCodecs.codec(CODEC);

	public HeroData {
		level = MathHelper.clamp(level, MIN_LEVEL, MAX_LEVEL);
		experience = Math.max(0, experience);
		bolts = MathHelper.clamp(bolts, 0, MAX_BOLTS);
		unlockedAliens = Set.copyOf(unlockedAliens);
		storyFlags = Set.copyOf(storyFlags);
	}

	/** Erfahrung, die fuer den Schritt von {@code level} auf {@code level + 1} noetig ist. */
	public static int experienceToNext(int level) {
		return 100 + (level - 1) * 50;
	}

	public boolean isMaxLevel() {
		return level >= MAX_LEVEL;
	}

	public HeroData withLevel(int newLevel) {
		return new HeroData(newLevel, 0, bolts, unlockedAliens, storyFlags);
	}

	/** Fuegt Erfahrung hinzu und steigt dabei so viele Stufen auf wie noetig. */
	public HeroData addExperience(int amount) {
		if (amount <= 0 || isMaxLevel()) {
			return this;
		}
		int newLevel = level;
		long xp = (long) experience + amount;
		while (newLevel < MAX_LEVEL && xp >= experienceToNext(newLevel)) {
			xp -= experienceToNext(newLevel);
			newLevel++;
		}
		int keptXp = newLevel >= MAX_LEVEL ? 0 : (int) Math.min(xp, Integer.MAX_VALUE);
		return new HeroData(newLevel, keptXp, bolts, unlockedAliens, storyFlags);
	}

	public HeroData withBolts(int amount) {
		return new HeroData(level, experience, amount, unlockedAliens, storyFlags);
	}

	/** Bucht Bolts auf das Konto (negativ = abbuchen). Ergebnis bleibt zwischen 0 und {@link #MAX_BOLTS}. */
	public HeroData addBolts(int delta) {
		long result = (long) bolts + delta;
		return withBolts((int) Math.max(0, Math.min(MAX_BOLTS, result)));
	}

	public boolean canAfford(int cost) {
		return cost >= 0 && bolts >= cost;
	}

	public boolean hasAlien(Identifier alienId) {
		return unlockedAliens.contains(alienId);
	}

	public HeroData unlockAlien(Identifier alienId) {
		if (hasAlien(alienId)) {
			return this;
		}
		Set<Identifier> next = new HashSet<>(unlockedAliens);
		next.add(alienId);
		return new HeroData(level, experience, bolts, next, storyFlags);
	}

	public HeroData lockAlien(Identifier alienId) {
		if (!hasAlien(alienId)) {
			return this;
		}
		Set<Identifier> next = new HashSet<>(unlockedAliens);
		next.remove(alienId);
		return new HeroData(level, experience, bolts, next, storyFlags);
	}

	public boolean hasFlag(String flag) {
		return storyFlags.contains(flag);
	}

	public HeroData withFlag(String flag, boolean present) {
		if (hasFlag(flag) == present) {
			return this;
		}
		Set<String> next = new HashSet<>(storyFlags);
		if (present) {
			next.add(flag);
		} else {
			next.remove(flag);
		}
		return new HeroData(level, experience, bolts, unlockedAliens, next);
	}
}
