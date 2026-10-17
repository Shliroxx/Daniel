package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: Blocken beginnen (true) oder beenden (false). */
public record GuardPayload(boolean active) implements CustomPayload {
	public static final CustomPayload.Id<GuardPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("guard"));
	public static final PacketCodec<ByteBuf, GuardPayload> CODEC = PacketCodecs.BOOL.xmap(GuardPayload::new, GuardPayload::active);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
