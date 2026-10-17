package com.santiq.kingdomomnitrix.omnitrix;

/**
 * Sichtbarer Zustand des Geraets (Licht, Zifferblatt, HUD). Der Server kennt die Geraete-Zustaende (Sperre, Ueberhitzung,
 * Nachladen, verwandelt); Auswahl und Verwandlungs-Ablauf ergaenzt der Client aus seiner Bedien-Phase.
 *
 * <p>Jeder Zustand bringt seine Lichtfarbe und seinen Puls mit (Persoenlichkeit des Geraets): ruhiges Glimmen im
 * Ruhezustand, schneller Puls bei Warnung, hartes Blinken bei Ueberhitzung.</p>
 */
public enum OmnitrixStatus {
	/** kein Omnitrix */
	IDLE(0x000000, 0.0f, 0.0f),
	/** bereit, ruhiges gruenes Glimmen */
	READY(0x39FF14, 0.35f, 0.6f),
	/** aktiviert: Arm gehoben, Kern faehrt aus */
	ACTIVE(0x5CFF3A, 0.8f, 0.0f),
	/** Alien-Auswahl offen */
	SELECTING(0x7CFF4F, 0.9f, 1.2f),
	/** Energieaufbau, Schlag, Verwandlung */
	TRANSFORMING(0xC8FFB0, 1.0f, 6.0f),
	/** als Alien unterwegs (Abzeichen) */
	TRANSFORMED(0x39FF14, 0.6f, 0.4f),
	/** Hitze ueber der Warnschwelle */
	WARNING(0xFFC21A, 0.9f, 3.0f),
	/** ueberhitzt: Verwandlung beendet, Geraet gesperrt */
	OVERHEATED(0xFF2A1A, 1.0f, 5.0f),
	/** laedt nach */
	COOLDOWN(0xFF3A2A, 0.45f, 0.8f),
	/** gesperrt (Sicherheitssperre) */
	LOCKED(0x6A7A70, 0.3f, 0.0f),
	/** Master Control aktiv, bereit */
	MASTER_CONTROL(0xB8FF5A, 0.7f, 0.25f);

	private final int color;
	private final float brightness;
	private final float pulseHz;

	OmnitrixStatus(int color, float brightness, float pulseHz) {
		this.color = color;
		this.brightness = brightness;
		this.pulseHz = pulseHz;
	}

	public int color() {
		return color;
	}

	/** Grundhelligkeit des Lichts 0..1 */
	public float brightness() {
		return brightness;
	}

	/** Pulsfrequenz in Hz (0 = gleichmaessig) */
	public float pulseHz() {
		return pulseHz;
	}

	/** Lichtstaerke zur Zeit {@code seconds}: Grundhelligkeit mit Puls. */
	public float light(float seconds) {
		if (pulseHz <= 0.0f) {
			return brightness;
		}
		float wave = 0.5f + 0.5f * (float) Math.sin(seconds * pulseHz * Math.PI * 2.0);
		return brightness * (0.55f + 0.45f * wave);
	}

	/** Bedienbar (Rad oeffnen, Alien waehlen)? */
	public boolean canSelect() {
		return this == READY || this == MASTER_CONTROL || this == COOLDOWN || this == WARNING;
	}

	private static final OmnitrixStatus[] VALUES = values();

	public static OmnitrixStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
	}
}
