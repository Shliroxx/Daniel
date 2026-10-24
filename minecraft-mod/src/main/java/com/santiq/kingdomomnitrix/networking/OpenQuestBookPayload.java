package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server → Client: Quest-Buch oeffnen. */
public record OpenQuestBookPayload() implements CustomPayload {
	public static final CustomPayload.Id<OpenQuestBookPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("open_quest_book"));
	public static final PacketCodec<ByteBuf, OpenQuestBookPayload> CODEC = PacketCodec.unit(new OpenQuestBookPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
