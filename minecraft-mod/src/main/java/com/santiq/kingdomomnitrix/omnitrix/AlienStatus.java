package com.santiq.kingdomomnitrix.omnitrix;

/**
 * Status eines Aliens im Omnitrix (Rad, Alien-Seite, HUD). {@code TRANSFORMING} setzt der Client waehrend des
 * Verwandlungs-Ablaufs; alle anderen liefert {@link OmnitrixCore#alienStatus}.
 */
public enum AlienStatus {
	/** keine DNA */
	LOCKED,
	/** DNA vorhanden */
	UNLOCKED,
	/** im Omnitrix eingestellt (zuletzt gewaehlt) */
	SELECTED,
	/** Verwandlung laeuft */
	TRANSFORMING,
	/** gerade aktiv */
	ACTIVE,
	/** zuletzt benutzt, Omnitrix laedt nach */
	COOLDOWN,
	/** volle Meisterschaft */
	MASTERED;

	public boolean usable() {
		return this != LOCKED;
	}
}
