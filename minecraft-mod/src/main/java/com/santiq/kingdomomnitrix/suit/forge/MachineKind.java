package com.santiq.kingdomomnitrix.suit.forge;

/**
 * Die drei Maschinen der Ruestungs-Kette. Slots: Eingaben, Brennstoff, Ausgabe (in dieser Reihenfolge).
 */
public enum MachineKind {
	/** Molekular-Zerkleinerer: Rohstoff -> doppelter Staub */
	CRUSHER("molecular_crusher", 1, 0xFF3AFF8A),
	/** Plasmaschmelze: Staub -> veredeltes Material */
	SMELTER("plasma_smelter", 1, 0xFFFF7A2A),
	/** Legierungsschmiede: drei Zutaten -> Panzerplatte */
	FORGE("alloy_forge", 3, 0xFF4AB8FF);

	private final String id;
	private final int inputs;
	private final int accent;

	MachineKind(String id, int inputs, int accent) {
		this.id = id;
		this.inputs = inputs;
		this.accent = accent;
	}

	public String id() {
		return id;
	}

	public int inputs() {
		return inputs;
	}

	public int fuelSlot() {
		return inputs;
	}

	public int outputSlot() {
		return inputs + 1;
	}

	public int size() {
		return inputs + 2;
	}

	/** Leuchtfarbe der Maschine (Menue-Raender, Fortschritt). */
	public int accent() {
		return accent;
	}
}
