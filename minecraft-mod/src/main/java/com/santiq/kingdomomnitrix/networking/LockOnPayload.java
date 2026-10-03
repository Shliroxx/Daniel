package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: Lock-On auf Entity-ID setzen; -1 = loesen. Der Server prueft Reichweite und Gueltigkeit. */
public record LockOnPayload(int entityId) implements CustomPayload {
	public static final CustomPayload.Id<LockOnPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("lock_on"));
	public static final PacketCodec<ByteBuf, LockOnPayload> CODEC = PacketCodecs.VAR_INT.xmap(LockOnPayload::new, LockOnPayload::entityId);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
