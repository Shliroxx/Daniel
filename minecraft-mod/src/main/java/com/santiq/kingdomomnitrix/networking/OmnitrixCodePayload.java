package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: am Omnitrix eingegebener Code (hoechstens 16 Zeichen; der Server prueft Ziffern und Laenge). */
public record OmnitrixCodePayload(String code) implements CustomPayload {
	public static final CustomPayload.Id<OmnitrixCodePayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("omnitrix_code"));
	public static final PacketCodec<ByteBuf, OmnitrixCodePayload> CODEC = PacketCodecs.string(16)
			.xmap(OmnitrixCodePayload::new, OmnitrixCodePayload::code);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
