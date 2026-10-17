package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.OmnitrixPhase;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: sichtbarer Omnitrix-Zustand des Spielers (Arm hoch, Geraet offen, Energieaufbau, Schlag). */
public record OmnitrixPhasePayload(OmnitrixPhase phase) implements CustomPayload {
	public static final CustomPayload.Id<OmnitrixPhasePayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("omnitrix_phase"));
	public static final PacketCodec<ByteBuf, OmnitrixPhasePayload> CODEC = PacketCodecs.VAR_INT.xmap(
			ordinal -> new OmnitrixPhasePayload(OmnitrixPhase.byOrdinal(ordinal)), payload -> payload.phase().ordinal());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
