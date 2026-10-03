package com.santiq.kingdomomnitrix.arena;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

/** Bestzeiten eines Spielers je Arena-Herausforderung (in Ticks). Vorhanden = mindestens einmal geschafft. */
public record ArenaRecords(Map<Identifier, Integer> bestTicks) {
	public static final ArenaRecords EMPTY = new ArenaRecords(Map.of());
	public static final Codec<ArenaRecords> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT).xmap(ArenaRecords::new, ArenaRecords::bestTicks);
	public static final PacketCodec<ByteBuf, ArenaRecords> PACKET_CODEC = PacketCodecs.codec(CODEC);

	public ArenaRecords {
		bestTicks = Map.copyOf(bestTicks);
	}

	public boolean cleared(Identifier challenge) {
		return bestTicks.containsKey(challenge);
	}

	/** Neue Zeit eintragen, falls sie besser ist (oder die erste). */
	public ArenaRecords withTime(Identifier challenge, int ticks) {
		Integer old = bestTicks.get(challenge);
		if (old != null && old <= ticks) {
			return this;
		}
		Map<Identifier, Integer> next = new HashMap<>(bestTicks);
		next.put(challenge, ticks);
		return new ArenaRecords(next);
	}
}
