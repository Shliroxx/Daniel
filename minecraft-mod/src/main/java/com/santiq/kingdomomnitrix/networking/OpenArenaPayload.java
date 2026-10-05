package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Server → Client: Arena-Terminal oeffnen (laeuft dort gerade ein Kampf?). */
public record OpenArenaPayload(BlockPos terminal, boolean running) implements CustomPayload {
	public static final CustomPayload.Id<OpenArenaPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("open_arena"));
	public static final PacketCodec<ByteBuf, OpenArenaPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, OpenArenaPayload::terminal,
			PacketCodecs.BOOL, OpenArenaPayload::running,
			OpenArenaPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
