package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: Evolve-Taste (Ultimate-Form). Der Server prueft alles. */
public record EvolvePayload() implements CustomPayload {
	public static final EvolvePayload INSTANCE = new EvolvePayload();
	public static final CustomPayload.Id<EvolvePayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("evolve"));
	public static final PacketCodec<ByteBuf, EvolvePayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
