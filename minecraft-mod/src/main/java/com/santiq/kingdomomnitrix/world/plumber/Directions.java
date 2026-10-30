package com.santiq.kingdomomnitrix.world.plumber;

/** Himmelsrichtung in acht Sektoren (Minecraft: Norden = -z, Osten = +x) als Uebersetzungsschluessel. */
public final class Directions {
	private static final String[] KEYS = {"direction.kingdomomnitrix.n", "direction.kingdomomnitrix.ne", "direction.kingdomomnitrix.e",
			"direction.kingdomomnitrix.se", "direction.kingdomomnitrix.s", "direction.kingdomomnitrix.sw", "direction.kingdomomnitrix.w",
			"direction.kingdomomnitrix.nw"};

	private Directions() {
	}

	/** Richtung des Versatzes (dx, dz) vom Betrachter aus. */
	public static String key(int dx, int dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		return KEYS[(int) Math.floorMod(Math.round(angle / 45.0), 8)];
	}
}
