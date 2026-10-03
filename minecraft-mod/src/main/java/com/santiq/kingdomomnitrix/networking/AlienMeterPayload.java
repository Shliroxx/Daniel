package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server → Client: Stand einer Alien-Anzeige (XLR8-Tempo, Heatblast-Kernhitze, Vierarm-Wut, Diamondhead-Resonanz, Wildmutt-Blutrausch) eines Spielers, 0–100. Geht an den
 * Spieler selbst (Tacho im HUD) und an alle, die ihn sehen (Aura am Koerper). {@code meter}: {@link #TEMPO},
 * {@link #HEAT}, {@link #RAGE}, {@link #RESONANCE}, {@link #FRENZY}.
 */
public record AlienMeterPayload(int entityId, int meter, float value) implements CustomPayload {
	public static final int TEMPO = 0;
	public static final int HEAT = 1;
	public static final int RAGE = 2;
	public static final int RESONANCE = 3;
	public static final int FRENZY = 4;
	public static final int COUNT = 5;
	public static final CustomPayload.Id<AlienMeterPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("alien_meter"));
	public static final PacketCodec<ByteBuf, AlienMeterPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, AlienMeterPayload::entityId,
			PacketCodecs.VAR_INT, AlienMeterPayload::meter,
			PacketCodecs.FLOAT, AlienMeterPayload::value,
			AlienMeterPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
