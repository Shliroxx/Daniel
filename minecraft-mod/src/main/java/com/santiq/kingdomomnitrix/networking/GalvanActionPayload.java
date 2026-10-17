package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → Server: Aktion im Galvan-Labor ({@code craft} mit Erfindungs-ID oder {@code hack}). Der Server prueft alles. */
public record GalvanActionPayload(String action, Identifier id) implements CustomPayload {
	public static final CustomPayload.Id<GalvanActionPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("galvan_action"));
	public static final PacketCodec<ByteBuf, GalvanActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.string(16), GalvanActionPayload::action,
			Identifier.PACKET_CODEC, GalvanActionPayload::id,
			GalvanActionPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
