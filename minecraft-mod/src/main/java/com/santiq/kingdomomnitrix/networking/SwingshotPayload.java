package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

/** Client → Server: Swingshot verankern (mit Ankerpunkt) oder loesen. */
public record SwingshotPayload(boolean active, double x, double y, double z) implements CustomPayload {
	public static final CustomPayload.Id<SwingshotPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("swingshot"));
	public static final PacketCodec<ByteBuf, SwingshotPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL, SwingshotPayload::active,
			PacketCodecs.DOUBLE, SwingshotPayload::x,
			PacketCodecs.DOUBLE, SwingshotPayload::y,
			PacketCodecs.DOUBLE, SwingshotPayload::z,
			SwingshotPayload::new);

	public Vec3d anchor() {
		return new Vec3d(x, y, z);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
