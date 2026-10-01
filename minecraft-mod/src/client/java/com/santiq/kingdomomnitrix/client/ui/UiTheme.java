package com.santiq.kingdomomnitrix.client.ui;

/**
 * Farben je System (Entscheidung SANTIQ „je nach System“): Held im Kingdom-Hearts-Blau mit Gold, Keyblade gold,
 * Omnitrix gruen-schwarz, Technik im Ratchet-&-Clank-Blau mit Orange, Erkunden violett, Kampf rot.
 * Dieselben Farben benutzt {@code tools/generate_icons.py} fuer die Symbole.
 */
public enum UiTheme {
	HERO(0xD8101830, 0xFF3A5A8C, 0xFF4AB8FF, 0xFFFFC94A),
	KEYBLADE(0xD8181408, 0xFF8C6A2A, 0xFFFFC94A, 0xFF8FD8FF),
	OMNITRIX(0xD80A140C, 0xFF2E7A2E, 0xFF5CE65C, 0xFFD8FFD0),
	TECH(0xD80E1620, 0xFF2A5A8C, 0xFF4AB8FF, 0xFFFF9A3C),
	EXPLORATION(0xD8140E1E, 0xFF5A3A8C, 0xFFC48BFF, 0xFFF0E0FF),
	COMBAT(0xD81A0E0E, 0xFF8C3A32, 0xFFFF6B5A, 0xFFFFD0C8);

	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_SOFT = 0xFFB0B8C4;
	public static final int TEXT_DISABLED = 0xFF5E6672;
	public static final int BAR_BACK = 0xFF262C38;

	private final int panel;
	private final int border;
	private final int accent;
	private final int highlight;

	UiTheme(int panel, int border, int accent, int highlight) {
		this.panel = panel;
		this.border = border;
		this.accent = accent;
		this.highlight = highlight;
	}

	/** Hintergrund (halbtransparent). */
	public int panel() {
		return panel;
	}

	public int border() {
		return border;
	}

	/** Hauptfarbe des Systems (Balken, Titel). */
	public int accent() {
		return accent;
	}

	/** Zweitfarbe (Hervorhebungen, Werte). */
	public int highlight() {
		return highlight;
	}

	/** Akzentfarbe mit eigener Deckkraft (0–255), z. B. fuer Auswahl-Hintergruende. */
	public int accent(int alpha) {
		return (alpha << 24) | (accent & 0x00FFFFFF);
	}
}
