package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → Server: Aktion aus dem Kommandomenue — Zauber wirken (0) oder Item benutzen (1). */
public record CommandActionPayload(int action, Identifier target) implements CustomPayload {
	public static final int CAST_SPELL = 0;
	public static final int USE_ITEM = 1;

	public static final CustomPayload.Id<CommandActionPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("command_action"));
	public static final PacketCodec<ByteBuf, CommandActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, CommandActionPayload::action,
			Identifier.PACKET_CODEC, CommandActionPayload::target,
			CommandActionPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
