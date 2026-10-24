package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client → Server: Arena-Herausforderung an diesem Terminal starten. */
public record ArenaStartPayload(BlockPos terminal, Identifier challenge) implements CustomPayload {
	public static final CustomPayload.Id<ArenaStartPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("arena_start"));
	public static final PacketCodec<ByteBuf, ArenaStartPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, ArenaStartPayload::terminal,
			Identifier.PACKET_CODEC, ArenaStartPayload::challenge,
			ArenaStartPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
