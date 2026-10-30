package com.santiq.kingdomomnitrix.suit.power;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client → Server: Schubduesen an/aus. Die Bewegung steuert der Client selbst (wie jede Spielerbewegung); der
 * Server prueft den Anzug, setzt die Fallhoehe zurueck und zeigt anderen Spielern die Flammen.
 */
public record JetpackPayload(boolean thrusting) implements CustomPayload {
	public static final CustomPayload.Id<JetpackPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("jetpack"));
	public static final PacketCodec<ByteBuf, JetpackPayload> CODEC = PacketCodecs.BOOL.xmap(JetpackPayload::new, JetpackPayload::thrusting);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
