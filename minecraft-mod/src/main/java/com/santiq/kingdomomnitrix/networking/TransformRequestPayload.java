package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → Server: im Alien-Rad gewaehltes Alien; der Server prueft alles selbst. */
public record TransformRequestPayload(Identifier alien) implements CustomPayload {
	public static final CustomPayload.Id<TransformRequestPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("transform"));
	public static final PacketCodec<ByteBuf, TransformRequestPayload> CODEC =
			Identifier.PACKET_CODEC.xmap(TransformRequestPayload::new, TransformRequestPayload::alien);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
