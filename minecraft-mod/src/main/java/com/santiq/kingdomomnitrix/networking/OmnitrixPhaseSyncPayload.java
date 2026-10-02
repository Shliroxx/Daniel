package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.OmnitrixPhase;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server → Client: Omnitrix-Zustand eines anderen Spielers in Sichtweite. */
public record OmnitrixPhaseSyncPayload(int entityId, OmnitrixPhase phase) implements CustomPayload {
	public static final CustomPayload.Id<OmnitrixPhaseSyncPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("omnitrix_phase_sync"));
	public static final PacketCodec<ByteBuf, OmnitrixPhaseSyncPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, OmnitrixPhaseSyncPayload::entityId,
			PacketCodecs.VAR_INT.xmap(OmnitrixPhase::byOrdinal, OmnitrixPhase::ordinal), OmnitrixPhaseSyncPayload::phase,
			OmnitrixPhaseSyncPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
