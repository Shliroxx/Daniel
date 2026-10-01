package com.santiq.kingdomomnitrix.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

/**
 * Magie-Zustand eines Spielers (gespeichert, an den eigenen Client synchronisiert).
 *
 * <p>MP werden wie die Omnitrix-Energie lazy berechnet: Stand {@code mp} zum Zeitpunkt {@code mpStamp},
 * dazu die Regeneration seitdem. Faellt MP auf 0, beginnt die MP-Ladezeit bis {@code chargeUntil};
 * danach sind die MP voll (wie in Kingdom Hearts II).</p>
 */
public record MagicState(
		float mp,
		long mpStamp,
		long chargeUntil,
		Optional<Identifier> selected,
		Map<Identifier, Integer> levels,
		Map<Identifier, Long> readyAt) {

	public static final float MAX_MP = 100.0f;

	public static final MagicState DEFAULT = new MagicState(MAX_MP, 0L, 0L, Optional.empty(), Map.of(), Map.of());

	public static final Codec<MagicState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.lenientOptionalFieldOf("mp", MAX_MP).forGetter(MagicState::mp),
			Codec.LONG.lenientOptionalFieldOf("mp_stamp", 0L).forGetter(MagicState::mpStamp),
			Codec.LONG.lenientOptionalFieldOf("charge_until", 0L).forGetter(MagicState::chargeUntil),
			Identifier.CODEC.optionalFieldOf("selected").forGetter(MagicState::selected),
			Codec.unboundedMap(Identifier.CODEC, Codec.intRange(1, 10)).lenientOptionalFieldOf("levels", Map.of()).forGetter(MagicState::levels),
			Codec.unboundedMap(Identifier.CODEC, Codec.LONG).lenientOptionalFieldOf("ready_at", Map.of()).forGetter(MagicState::readyAt)
	).apply(instance, MagicState::new));

	public static final PacketCodec<ByteBuf, MagicState> PACKET_CODEC = PacketCodecs.codec(CODEC);

	public MagicState {
		mp = Math.max(0.0f, Math.min(MAX_MP, mp));
		levels = Map.copyOf(levels);
		readyAt = Map.copyOf(readyAt);
	}

	public boolean isCharging(long now) {
		return now < chargeUntil;
	}

	/** Aktuelle MP; {@code regenPerSecond} haengt vom gehaltenen Keyblade ab (MP-Eile). */
	public float currentMp(long now, float regenPerSecond) {
		if (isCharging(now)) {
			return 0.0f;
		}
		if (chargeUntil > 0 && mpStamp < chargeUntil) {
			// Ladezeit vorbei: voll, danach Regeneration ab Ende der Ladezeit (bleibt bei MAX).
			return MAX_MP;
		}
		return Math.min(MAX_MP, mp + regenPerSecond * Math.max(0L, now - mpStamp) / 20.0f);
	}

	/** Fortschritt der MP-Ladezeit (0–1), 1 wenn keine laeuft. */
	public float chargeProgress(long now, int chargeTicks) {
		if (!isCharging(now) || chargeTicks <= 0) {
			return 1.0f;
		}
		return 1.0f - (float) (chargeUntil - now) / chargeTicks;
	}

	public int level(Identifier spell) {
		return levels.getOrDefault(spell, 1);
	}

	public long cooldownRemaining(Identifier spell, long now) {
		return Math.max(0L, readyAt.getOrDefault(spell, 0L) - now);
	}

	public MagicState withMp(float value, long now) {
		return new MagicState(value, now, chargeUntil, selected, levels, readyAt);
	}

	public MagicState startCharge(long now, int chargeTicks) {
		return new MagicState(0.0f, now, now + chargeTicks, selected, levels, readyAt);
	}

	public MagicState withSelected(Identifier spell) {
		return new MagicState(mp, mpStamp, chargeUntil, Optional.of(spell), levels, readyAt);
	}

	public MagicState withLevel(Identifier spell, int level) {
		Map<Identifier, Integer> next = new HashMap<>(levels);
		next.put(spell, level);
		return new MagicState(mp, mpStamp, chargeUntil, selected, next, readyAt);
	}

	public MagicState withCooldown(Identifier spell, long readyTick) {
		Map<Identifier, Long> next = new HashMap<>(readyAt);
		next.put(spell, readyTick);
		return new MagicState(mp, mpStamp, chargeUntil, selected, levels, next);
	}
}
