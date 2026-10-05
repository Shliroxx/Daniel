package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server → Clients: Spieler {@code entityId} fuehrt eine Kampfaktion aus (z. B. {@code combo_2}, {@code dodge}).
 * Der Client spielt dazu Animation und Effekte ab.
 */
public record CombatAnimationPayload(int entityId, String animation) implements CustomPayload {
	public static final CustomPayload.Id<CombatAnimationPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("combat_animation"));
	public static final PacketCodec<ByteBuf, CombatAnimationPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, CombatAnimationPayload::entityId,
			PacketCodecs.string(32), CombatAnimationPayload::animation,
			CombatAnimationPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
