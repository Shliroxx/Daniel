package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

/** Server → Clients: Seil eines Spielers anzeigen (Anker) oder entfernen. */
public record SwingshotStatePayload(int entityId, boolean active, double x, double y, double z) implements CustomPayload {
	public static final CustomPayload.Id<SwingshotStatePayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("swingshot_state"));
	public static final PacketCodec<ByteBuf, SwingshotStatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SwingshotStatePayload::entityId,
			PacketCodecs.BOOL, SwingshotStatePayload::active,
			PacketCodecs.DOUBLE, SwingshotStatePayload::x,
			PacketCodecs.DOUBLE, SwingshotStatePayload::y,
			PacketCodecs.DOUBLE, SwingshotStatePayload::z,
			SwingshotStatePayload::new);

	public Vec3d anchor() {
		return new Vec3d(x, y, z);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
