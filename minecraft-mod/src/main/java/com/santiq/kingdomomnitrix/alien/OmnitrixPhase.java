package com.santiq.kingdomomnitrix.alien;

/**
 * Zustaende des Omnitrix (Omniverse-Ablauf). Die Abfolge steuert Arm, Geraet, Hologramm-Rad, Licht und Klang.
 *
 * <pre>
 * IDLE → EQUIPPED → ACTIVATING → OPENING → SELECTING ⇄ ROTATING → ALIEN_SELECTED → CONFIRMING → IMPACT
 *      → TRANSFORMATION → ACTIVE_ALIEN → REVERT → COOLDOWN → EQUIPPED
 * </pre>
 *
 * Der Zustand lebt auf dem Client des Spielers. Die fuer andere sichtbaren Zustaende ({@link #isShared()}) schickt der
 * Client an den Server, der sie an Spieler in Sichtweite weitergibt; Verwandlung und Abklingzeit selbst kommen
 * weiterhin aus dem synchronisierten {@link TransformationState}.
 */
public enum OmnitrixPhase {
	/** kein Omnitrix */
	IDLE,
	/** Omnitrix getragen, Grundglimmen */
	EQUIPPED,
	/** linker Arm hebt sich vor die Kamera */
	ACTIVATING,
	/** Gehaeuse oeffnet sich, Kern faehrt heraus, Energie baut sich auf */
	OPENING,
	/** Hologramm-Rad aktiv, wartet auf Eingabe */
	SELECTING,
	/** Rad dreht sich zum naechsten Alien */
	ROTATING,
	/** Rad steht auf einem Alien */
	ALIEN_SELECTED,
	/** Auswahl bestaetigt: Hologramm pulsiert, Energieaufbau */
	CONFIRMING,
	/** Schlag aufs Zifferblatt */
	IMPACT,
	/** Verwandlung laeuft (bis der Server sie bestaetigt) */
	TRANSFORMATION,
	/** als Alien unterwegs */
	ACTIVE_ALIEN,
	/** Omnitrix laedt nach (rot) */
	COOLDOWN,
	/** Rueckverwandlung */
	REVERT;

	private static final OmnitrixPhase[] VALUES = values();

	public static OmnitrixPhase byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
	}

	/** Linker Arm vor dem Koerper (Ego- und Third-Person). */
	public boolean isArmRaised() {
		return switch (this) {
			case ACTIVATING, OPENING, SELECTING, ROTATING, ALIEN_SELECTED, CONFIRMING, IMPACT -> true;
			default -> false;
		};
	}

	/** Kern ausgefahren (Gehaeuse offen). */
	public boolean isOpen() {
		return switch (this) {
			case OPENING, SELECTING, ROTATING, ALIEN_SELECTED, CONFIRMING -> true;
			default -> false;
		};
	}

	/** Hologramm-Rad sichtbar. */
	public boolean showsWheel() {
		return switch (this) {
			case SELECTING, ROTATING, ALIEN_SELECTED, CONFIRMING -> true;
			default -> false;
		};
	}

	/** Zustaende, die andere Spieler sehen sollen (Arm, offenes Geraet, Energieaufbau, Schlag). */
	public boolean isShared() {
		return switch (this) {
			case IDLE, EQUIPPED, ACTIVATING, OPENING, SELECTING, CONFIRMING, IMPACT -> true;
			default -> false;
		};
	}
}
