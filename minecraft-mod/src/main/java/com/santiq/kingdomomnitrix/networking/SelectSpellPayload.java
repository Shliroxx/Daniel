package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → Server: aktiven Zauber waehlen. */
public record SelectSpellPayload(Identifier spell) implements CustomPayload {
	public static final CustomPayload.Id<SelectSpellPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("select_spell"));
	public static final PacketCodec<ByteBuf, SelectSpellPayload> CODEC = Identifier.PACKET_CODEC.xmap(SelectSpellPayload::new, SelectSpellPayload::spell);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
