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

	/** Klassisches Omnitrix-Gruen (Ausgangsfarbe aller Meldungen, Lichter und Effekte). */
	public static final int CLASSIC = 0x39FF14;

	/**
	 * Lichtfarbe eines Geraete-Zustands mit Farbmodul: Bereit/verwandelt in der Modulfarbe, Aktiv/Auswahl/Verwandlung
	 * zunehmend heller. Warnung, Ueberhitzung, Nachladen, Sperre und Master Control behalten ihre Farbe.
	 */
	public static int status(PlayerEntity player, OmnitrixStatus status) {
		Color color = of(player);
		return switch (status) {
			case IDLE, READY, TRANSFORMED -> color.primary();
			case ACTIVE -> mix(color.primary(), 0xFFFFFF, 0.15f);
			case SELECTING -> mix(color.primary(), 0xFFFFFF, 0.25f);
			case TRANSFORMING -> mix(color.primary(), 0xFFFFFF, 0.65f);
			default -> status.color();
		};
	}

	/** Klassisches Gruen gegen die Modulfarbe tauschen, alle anderen Farben unveraendert lassen. */
	public static int themed(PlayerEntity player, int rgb) {
		return (rgb & 0xFFFFFF) == CLASSIC && player != null ? primary(player) : rgb;
	}

	public static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
