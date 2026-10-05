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
 * Server → Client: Omnitrix-Hologramm-Meldung (Omnitrix OS) mit Titel, Hauptzeile, optionaler Fusszeile, Alien-Symbol
 * und Darstellung ({@link Style}). Fuer Ereignisse, die nur der Server kennt (Verwandlung, Hitze, Fehlfunktion …).
 */
public record OmnitrixHoloPayload(Text title, Text body, Optional<Text> footer, Optional<Identifier> alien, Style style)
		implements CustomPayload {
	public static final CustomPayload.Id<OmnitrixHoloPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("omnitrix_holo"));

	/**
	 * Darstellung: Akzentfarbe, Anzeigedauer und Vorrang (hoeher verdraengt niedriger; eine niedrigere Meldung wartet,
	 * bis die wichtigere eine Weile zu sehen war).
	 */
	public record Style(int color, int durationMs, int priority) {
		public static final PacketCodec<RegistryByteBuf, Style> CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER, Style::color,
				PacketCodecs.VAR_INT, Style::durationMs,
				PacketCodecs.VAR_INT, Style::priority,
				Style::new);
	}

	public static final PacketCodec<RegistryByteBuf, OmnitrixHoloPayload> CODEC = PacketCodec.tuple(
			TextCodecs.REGISTRY_PACKET_CODEC, OmnitrixHoloPayload::title,
			TextCodecs.REGISTRY_PACKET_CODEC, OmnitrixHoloPayload::body,
			PacketCodecs.optional(TextCodecs.REGISTRY_PACKET_CODEC), OmnitrixHoloPayload::footer,
			PacketCodecs.optional(Identifier.PACKET_CODEC), OmnitrixHoloPayload::alien,
			Style.CODEC, OmnitrixHoloPayload::style,
			OmnitrixHoloPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
