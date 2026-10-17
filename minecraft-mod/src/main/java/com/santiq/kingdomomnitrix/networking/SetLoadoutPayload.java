package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → Server: neues Loadout (Faehigkeits-Nummern fuer die sechs Tasten) eines Aliens. Der Server prueft die Regeln. */
public record SetLoadoutPayload(Identifier alien, List<Integer> loadout) implements CustomPayload {
	public static final CustomPayload.Id<SetLoadoutPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("set_loadout"));
	public static final PacketCodec<ByteBuf, SetLoadoutPayload> CODEC = PacketCodec.tuple(
			Identifier.PACKET_CODEC, SetLoadoutPayload::alien,
			PacketCodecs.VAR_INT.collect(PacketCodecs.toList(8)), SetLoadoutPayload::loadout,
			SetLoadoutPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
