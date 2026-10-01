package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client → Server: Aktion am Waffen-Terminal (0 = kaufen, 1 = aufwerten, 2 = Munition auffuellen). */
public record TerminalActionPayload(int action, Identifier weapon, BlockPos terminal) implements CustomPayload {
	public static final CustomPayload.Id<TerminalActionPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("terminal_action"));
	public static final PacketCodec<ByteBuf, TerminalActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, TerminalActionPayload::action,
			Identifier.PACKET_CODEC, TerminalActionPayload::weapon,
			BlockPos.PACKET_CODEC, TerminalActionPayload::terminal,
			TerminalActionPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
