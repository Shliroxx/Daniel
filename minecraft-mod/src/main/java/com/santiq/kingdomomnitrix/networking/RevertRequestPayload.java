package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: zurueckverwandeln. */
public record RevertRequestPayload() implements CustomPayload {
	public static final RevertRequestPayload INSTANCE = new RevertRequestPayload();
	public static final CustomPayload.Id<RevertRequestPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("revert"));
	public static final PacketCodec<ByteBuf, RevertRequestPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
