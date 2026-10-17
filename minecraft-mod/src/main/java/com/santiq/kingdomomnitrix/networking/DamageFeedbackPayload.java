package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server → Client: ein eigener Treffer ist angekommen (Schadenszahl über dem Ziel, Treffer-Markierung am Fadenkreuz).
 * {@code kind} ist die Ordinalzahl von {@link com.santiq.kingdomomnitrix.combat.CombatFeedback.Kind}.
 */
public record DamageFeedbackPayload(double x, double y, double z, float amount, int kind, boolean lethal) implements CustomPayload {
	public static final CustomPayload.Id<DamageFeedbackPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("damage_feedback"));
	public static final PacketCodec<ByteBuf, DamageFeedbackPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.DOUBLE, DamageFeedbackPayload::x,
			PacketCodecs.DOUBLE, DamageFeedbackPayload::y,
			PacketCodecs.DOUBLE, DamageFeedbackPayload::z,
			PacketCodecs.FLOAT, DamageFeedbackPayload::amount,
			PacketCodecs.VAR_INT, DamageFeedbackPayload::kind,
			PacketCodecs.BOOL, DamageFeedbackPayload::lethal,
			DamageFeedbackPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
