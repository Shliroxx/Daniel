package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.Optional;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/**
 * Server → Client: Omnitrix-Hologramm-Meldung (Omnitrix OS) mit Titel, Hauptzeile, optionaler Fusszeile und Alien-Symbol.
 * Fuer Ereignisse, die nur der Server kennt (Fehlfunktion, Notfall-Verwandlung, Sperre …).
 */
public record OmnitrixHoloPayload(Text title, Text body, Optional<Text> footer, Optional<Identifier> alien, int color, int durationMs)
		implements CustomPayload {
	public static final CustomPayload.Id<OmnitrixHoloPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("omnitrix_holo"));
	public static final PacketCodec<RegistryByteBuf, OmnitrixHoloPayload> CODEC = PacketCodec.tuple(
			TextCodecs.REGISTRY_PACKET_CODEC, OmnitrixHoloPayload::title,
			TextCodecs.REGISTRY_PACKET_CODEC, OmnitrixHoloPayload::body,
			PacketCodecs.optional(TextCodecs.REGISTRY_PACKET_CODEC), OmnitrixHoloPayload::footer,
			PacketCodecs.optional(Identifier.PACKET_CODEC), OmnitrixHoloPayload::alien,
			PacketCodecs.INTEGER, OmnitrixHoloPayload::color,
			PacketCodecs.VAR_INT, OmnitrixHoloPayload::durationMs,
			OmnitrixHoloPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
