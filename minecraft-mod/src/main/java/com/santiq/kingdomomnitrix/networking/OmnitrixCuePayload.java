package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server → Client: Omnitrix-Rueckmeldung ausloesen (Klang, Licht, Kamera; siehe {@link OmnitrixCue}). */
public record OmnitrixCuePayload(OmnitrixCue cue) implements CustomPayload {
	public static final CustomPayload.Id<OmnitrixCuePayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("omnitrix_cue"));
	public static final PacketCodec<ByteBuf, OmnitrixCuePayload> CODEC = PacketCodecs.VAR_INT
			.xmap(i -> new OmnitrixCuePayload(OmnitrixCue.byOrdinal(i)), p -> p.cue().ordinal());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
