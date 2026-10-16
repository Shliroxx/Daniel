package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client → Server: Faehigkeit in Slot {@code slot} (0-basiert) gedrueckt ({@code release = false}) oder losgelassen
 * ({@code release = true}). Loslassen zaehlt nur fuer aufladbare Faehigkeiten (Halten = staerker).
 */
public record AbilityRequestPayload(int slot, boolean release) implements CustomPayload {
	public static final CustomPayload.Id<AbilityRequestPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("ability"));
	public static final PacketCodec<ByteBuf, AbilityRequestPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, AbilityRequestPayload::slot,
			PacketCodecs.BOOL, AbilityRequestPayload::release,
			AbilityRequestPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
