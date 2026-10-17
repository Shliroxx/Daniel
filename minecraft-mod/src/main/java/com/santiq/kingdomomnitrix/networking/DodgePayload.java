package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: Ausweichen in Weltrichtung (x, z); (0, 0) = nach hinten. */
public record DodgePayload(float directionX, float directionZ) implements CustomPayload {
	public static final CustomPayload.Id<DodgePayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("dodge"));
	public static final PacketCodec<ByteBuf, DodgePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, DodgePayload::directionX,
			PacketCodecs.FLOAT, DodgePayload::directionZ,
			DodgePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
