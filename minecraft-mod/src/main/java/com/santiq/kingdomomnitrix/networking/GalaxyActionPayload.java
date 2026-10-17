package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client → Server: Galaxiekarte und Aphelion. {@code action} = {@link com.santiq.kingdomomnitrix.space.Galaxy.Action}
 * (Reisen: Planet-ID, Ausbau: Name des Ausbaus, Feuern: leer). Der Server prueft alles.
 */
public record GalaxyActionPayload(int action, String value) implements CustomPayload {
	public static final CustomPayload.Id<GalaxyActionPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("galaxy_action"));
	public static final PacketCodec<ByteBuf, GalaxyActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, GalaxyActionPayload::action,
			PacketCodecs.string(128), GalaxyActionPayload::value,
			GalaxyActionPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
