package com.santiq.kingdomomnitrix.omnitrix;

/**
 * Rueckmeldungen des Omnitrix. Jede Bedienung und jeder Geraete-Wechsel loest genau einen Cue aus; was er bewirkt
 * (Klang, Licht-Puls, Kamera, Bildschirm-Effekt), steht zentral in {@code assets/<ns>/omnitrix/feedback.json} und wird
 * vom Client ausgefuehrt. Der Server schickt Cues fuer Ereignisse, die nur er kennt (Warnung, Ueberhitzung, Sperre,
 * Freischaltung, Ende der Nachladezeit).
 */
public enum OmnitrixCue {
	ACTIVATE, OPEN, NAVIGATE, SELECT, CONFIRM, CANCEL, TRANSFORM, DETRANSFORM, ERROR, WARNING, COOLDOWN, READY,
	OVERHEAT, UNLOCK, LOCK, MASTER_CONTROL, QUICK_CHANGE, EMERGENCY, DNA_SHOCK, FAVORITE, MALFUNCTION;

	private static final OmnitrixCue[] VALUES = values();

	public static OmnitrixCue byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : ERROR;
	}

	/** Schluessel in feedback.json */
	public String key() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
