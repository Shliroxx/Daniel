package com.santiq.kingdomomnitrix.suit.power;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: eine Anzugfunktion umschalten (Bit aus {@link SuitToggles}). */
public record SuitTogglePayload(int bit) implements CustomPayload {
	public static final CustomPayload.Id<SuitTogglePayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("suit_toggle"));
	public static final PacketCodec<ByteBuf, SuitTogglePayload> CODEC = PacketCodecs.VAR_INT.xmap(SuitTogglePayload::new, SuitTogglePayload::bit);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
