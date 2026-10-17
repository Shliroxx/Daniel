package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → Server: Uniform fuer ein Alien waehlen (im Omnitrix). */
public record SetUniformPayload(Identifier alien, String uniform) implements CustomPayload {
	public static final CustomPayload.Id<SetUniformPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("set_uniform"));
	public static final PacketCodec<ByteBuf, SetUniformPayload> CODEC = PacketCodec.tuple(
			Identifier.PACKET_CODEC, SetUniformPayload::alien,
			PacketCodecs.string(16), SetUniformPayload::uniform,
			SetUniformPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
