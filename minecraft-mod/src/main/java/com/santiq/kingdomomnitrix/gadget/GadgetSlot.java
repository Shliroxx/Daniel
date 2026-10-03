package com.santiq.kingdomomnitrix.gadget;

/** Plaetze im Gadget-Guertel: Ruecken (Heli-Pack/Heli-Jet) und Werkzeug (Swingshot). */
public enum GadgetSlot {
	BACK,
	TOOL;

	public String translationKey() {
		return "gadget.kingdomomnitrix.slot." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
