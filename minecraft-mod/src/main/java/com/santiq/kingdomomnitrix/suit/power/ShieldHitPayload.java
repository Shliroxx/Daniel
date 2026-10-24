package com.santiq.kingdomomnitrix.suit.power;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server → Client: der Energieschild eines Spielers hat einen Treffer abgefangen (broke = Schild zerbrochen). */
public record ShieldHitPayload(int entity, int color, boolean broke) implements CustomPayload {
	public static final CustomPayload.Id<ShieldHitPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("shield_hit"));
	public static final PacketCodec<ByteBuf, ShieldHitPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, ShieldHitPayload::entity, PacketCodecs.INTEGER, ShieldHitPayload::color, PacketCodecs.BOOL,
			ShieldHitPayload::broke, ShieldHitPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
