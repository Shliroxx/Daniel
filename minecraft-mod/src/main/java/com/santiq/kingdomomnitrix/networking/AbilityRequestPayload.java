package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: Faehigkeit in Slot {@code slot} (0-basiert) ausloesen. */
public record AbilityRequestPayload(int slot) implements CustomPayload {
	public static final CustomPayload.Id<AbilityRequestPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("ability"));
	public static final PacketCodec<ByteBuf, AbilityRequestPayload> CODEC =
			PacketCodecs.VAR_INT.xmap(AbilityRequestPayload::new, AbilityRequestPayload::slot);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
