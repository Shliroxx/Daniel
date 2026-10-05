package com.santiq.kingdomomnitrix.client.hud;

import java.util.Locale;

/** Bezugspunkt eines HUD-Elements; der Versatz zaehlt von dort, so bleibt die Anordnung bei jeder Fenstergroesse gleich. */
public enum HudAnchor {
	TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
	MIDDLE_LEFT(0, 1), MIDDLE_RIGHT(2, 1),
	BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);

	private final int column;
	private final int row;

	HudAnchor(int column, int row) {
		this.column = column;
		this.row = row;
	}

	/** Linke bzw. obere Kante des Elements ohne Versatz. */
	public int baseX(int screenWidth, int width) {
		return column == 0 ? 0 : column == 1 ? (screenWidth - width) / 2 : screenWidth - width;
	}

	public int baseY(int screenHeight, int height) {
		return row == 0 ? 0 : row == 1 ? (screenHeight - height) / 2 : screenHeight - height;
	}

	/** Naechster Bezugspunkt fuer eine Elementmitte (beim Verschieben im Editor). */
	public static HudAnchor nearest(double centerX, double centerY, int screenWidth, int screenHeight) {
		int column = centerX < screenWidth / 3.0 ? 0 : centerX > screenWidth * 2 / 3.0 ? 2 : 1;
		int row = centerY < screenHeight / 3.0 ? 0 : centerY > screenHeight * 2 / 3.0 ? 2 : 1;
		if (row == 1 && column == 1) {
			row = centerY < screenHeight / 2.0 ? 0 : 2; // keine Mitte-Mitte: dort liegt das Fadenkreuz
		}
		for (HudAnchor anchor : values()) {
			if (anchor.column == column && anchor.row == row) {
				return anchor;
			}
		}
		return TOP_LEFT;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static HudAnchor byKey(String key, HudAnchor fallback) {
		for (HudAnchor anchor : values()) {
			if (anchor.key().equals(key)) {
				return anchor;
			}
		}
		return fallback;
	}
}
