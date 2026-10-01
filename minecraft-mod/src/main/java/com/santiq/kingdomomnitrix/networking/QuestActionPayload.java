package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → Server: Quest annehmen (0), aufgeben (1) oder abgeben (2). */
public record QuestActionPayload(int action, Identifier quest) implements CustomPayload {
	public static final int ACCEPT = 0;
	public static final int ABANDON = 1;
	public static final int TURN_IN = 2;

	public static final CustomPayload.Id<QuestActionPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("quest_action"));
	public static final PacketCodec<ByteBuf, QuestActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, QuestActionPayload::action,
			Identifier.PACKET_CODEC, QuestActionPayload::quest,
			QuestActionPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
