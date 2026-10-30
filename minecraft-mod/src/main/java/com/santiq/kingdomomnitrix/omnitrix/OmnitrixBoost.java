package com.santiq.kingdomomnitrix.omnitrix;

import java.util.Optional;

/**
 * Boost-Module fuer die Steckplaetze des Omnitrix (ein Platz je Omnitrix-Stufe). Gleiche Module stapeln sich.
 * POWER, GUARD und SWIFT wirken als Attribute, solange der Spieler verwandelt ist ({@link OmnitrixBoosts});
 * DURATION, COOLING und RECHARGE rechnet {@link OmnitrixCalibration#apply} in das Geraete-Profil ein.
 */
public enum OmnitrixBoost {
	POWER("power", 0xFF5A3A, "boost.kingdomomnitrix.power", "boost.kingdomomnitrix.power.desc"),
	GUARD("guard", 0x5AA0FF, "boost.kingdomomnitrix.guard", "boost.kingdomomnitrix.guard.desc"),
	SWIFT("swift", 0xFFE04A, "boost.kingdomomnitrix.swift", "boost.kingdomomnitrix.swift.desc"),
	DURATION("duration", 0x3AFF6A, "boost.kingdomomnitrix.duration", "boost.kingdomomnitrix.duration.desc"),
	COOLING("cooling", 0x8AE8FF, "boost.kingdomomnitrix.cooling", "boost.kingdomomnitrix.cooling.desc"),
	RECHARGE("recharge", 0xC05AFF, "boost.kingdomomnitrix.recharge", "boost.kingdomomnitrix.recharge.desc");

	/** Wirkung je eingesetztem Modul */
	public static final float POWER_DAMAGE = 0.15f;
	public static final float GUARD_ARMOR = 3.0f;
	public static final float SWIFT_SPEED = 0.08f;
	public static final float DURATION_BONUS = 0.20f;
	public static final float COOLING_HEAT = 0.15f;
	public static final float RECHARGE_COOLDOWN = 0.15f;

	private final String id;
	private final int color;
	private final String nameKey;
	private final String descKey;

	OmnitrixBoost(String id, int color, String nameKey, String descKey) {
		this.id = id;
		this.color = color;
		this.nameKey = nameKey;
		this.descKey = descKey;
	}

	public String id() {
		return id;
	}

	public int color() {
		return color;
	}

	public String nameKey() {
		return nameKey;
	}

	public String descKey() {
		return descKey;
	}

	public static Optional<OmnitrixBoost> byId(String id) {
		for (OmnitrixBoost boost : values()) {
			if (boost.id.equals(id)) {
				return Optional.of(boost);
			}
		}
		return Optional.empty();
	}
}
