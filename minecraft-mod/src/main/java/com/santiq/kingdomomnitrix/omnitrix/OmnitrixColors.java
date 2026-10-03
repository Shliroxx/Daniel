package com.santiq.kingdomomnitrix.omnitrix;

import java.util.List;
import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Farbmodule des Omnitrix (Kalibrier-Werkbank). Die Farbe ist rein optisch: Kern-Leuchten, Hologramm-Rad, Bedien-
 * Oberflaeche, Verwandlungsblitz und Abzeichen nutzen {@link #primary} statt des festen Gruens. Statusfarben (Warnung,
 * Ueberhitzung, Sperre) bleiben unveraendert, damit Gefahr immer gleich aussieht.
 */
public final class OmnitrixColors {
	/** Ein Farbmodul: Hauptfarbe (Kern, Rahmen) und helle Nebenfarbe (Text, Glanz). */
	public record Color(String id, int primary, int light) {
	}

	public static final List<Color> ALL = List.of(
			new Color("green", 0x39FF14, 0xB8FFB0),
			new Color("blue", 0x2FA8FF, 0xB0E4FF),
			new Color("red", 0xFF3A2A, 0xFFB8B0),
			new Color("yellow", 0xFFD21A, 0xFFF0A8),
			new Color("purple", 0xB04AFF, 0xE2BEFF),
			new Color("white", 0xE8F4FF, 0xFFFFFF));

	private OmnitrixColors() {
	}

	public static boolean exists(String id) {
		return byId(id).isPresent();
	}

	public static Optional<Color> byId(String id) {
		return ALL.stream().filter(color -> color.id().equals(id)).findFirst();
	}

	/** Farbmodul des Spielers (unbekannte Werte: Gruen). */
	public static Color of(PlayerEntity player) {
		return byId(OmnitrixCore.calibration(player).color()).orElse(ALL.getFirst());
	}

	public static int primary(PlayerEntity player) {
		return of(player).primary();
	}
}
