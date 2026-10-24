package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server → Client: Dialog mit einem NPC oeffnen. */
public record OpenNpcDialogPayload(int entityId, Identifier npc) implements CustomPayload {
	public static final CustomPayload.Id<OpenNpcDialogPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("open_npc_dialog"));
	public static final PacketCodec<ByteBuf, OpenNpcDialogPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, OpenNpcDialogPayload::entityId,
			Identifier.PACKET_CODEC, OpenNpcDialogPayload::npc,
			OpenNpcDialogPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
