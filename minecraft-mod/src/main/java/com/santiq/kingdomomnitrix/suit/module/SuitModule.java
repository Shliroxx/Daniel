package com.santiq.kingdomomnitrix.suit.module;

/**
 * Upgrade-Module der Endgame-Anzuege (nach dem Vorbild der Draconic-Evolution-Module). Jedes Modul steckt in einem
 * Ruestungsteil; wirksam werden sie nur im vollstaendigen Anzug, je Art hoechstens {@link #MAX_PER_SUIT}.
 */
public enum SuitModule {
	/** +25 % Schildkapazitaet je Modul */
	SHIELD_CAPACITY("shield_capacity", 0x3AE8FF, "item.kingdomomnitrix.module_shield_capacity.effect"),
	/** +30 % Nachladen, -15 % Pause je Modul */
	SHIELD_RECHARGE("shield_recharge", 0x7AA8FF, "item.kingdomomnitrix.module_shield_recharge.effect"),
	/** +20 % Fluggeschwindigkeit bzw. +40 % Treibstoff je Modul */
	MOBILITY("mobility", 0xFFB02A, "item.kingdomomnitrix.module_mobility.effect"),
	/** +5 % Lauftempo je Modul */
	SPEED("speed", 0x4BFF6A, "item.kingdomomnitrix.module_speed.effect"),
	/** +1,5 Angriffsschaden je Modul */
	STRENGTH("strength", 0xFF4A3A, "item.kingdomomnitrix.module_strength.effect"),
	/** +1 Herz je Modul */
	VITALITY("vitality", 0xFF6AB0, "item.kingdomomnitrix.module_vitality.effect");

	public static final int MAX_PER_SUIT = 4;

	private final String id;
	private final int color;
	private final String effectKey;

	SuitModule(String id, int color, String effectKey) {
		this.id = id;
		this.color = color;
		this.effectKey = effectKey;
	}

	public String id() {
		return id;
	}

	public int color() {
		return color;
	}

	/** Uebersetzungsschluessel der Wirkung (Tooltip) */
	public String effectKey() {
		return effectKey;
	}

	public static SuitModule byId(String id) {
		for (SuitModule module : values()) {
			if (module.id.equals(id)) {
				return module;
			}
		}
		return null;
	}
}
