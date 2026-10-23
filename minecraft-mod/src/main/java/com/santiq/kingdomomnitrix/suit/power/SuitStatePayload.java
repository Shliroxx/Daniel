package com.santiq.kingdomomnitrix.suit.power;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server → Client: Zustand des getragenen Anzugs. set = Ordinal von {@link com.santiq.kingdomomnitrix.suit.SuitSet}
 * oder -1 (kein vollstaendiger Anzug), toggles = Bits aus {@link SuitToggles}.
 */
public record SuitStatePayload(int set, float shield, float maxShield, int toggles) implements CustomPayload {
	public static final CustomPayload.Id<SuitStatePayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("suit_state"));
	public static final PacketCodec<ByteBuf, SuitStatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SuitStatePayload::set, PacketCodecs.FLOAT, SuitStatePayload::shield,
			PacketCodecs.FLOAT, SuitStatePayload::maxShield, PacketCodecs.VAR_INT, SuitStatePayload::toggles, SuitStatePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
