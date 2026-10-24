package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Server → Client: Waffen-Terminal an Position {@code pos} oeffnen. */
public record OpenTerminalPayload(BlockPos pos) implements CustomPayload {
	public static final CustomPayload.Id<OpenTerminalPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("open_terminal"));
	public static final PacketCodec<ByteBuf, OpenTerminalPayload> CODEC = BlockPos.PACKET_CODEC.xmap(OpenTerminalPayload::new, OpenTerminalPayload::pos);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
