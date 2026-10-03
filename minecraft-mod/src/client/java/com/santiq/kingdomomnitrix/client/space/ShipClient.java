package com.santiq.kingdomomnitrix.client.space;

import com.santiq.kingdomomnitrix.space.ShipEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Schiff auf dem Client: Kamera beim Einsteigen in die Verfolgeransicht (beim Aussteigen zurueck)
 * (die Cockpit-Anzeige ist {@link ShipHud}).
 */
public final class ShipClient {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private static boolean wasPiloting;
	private static Perspective previousPerspective;

	private ShipClient() {
	}

	public static void tick(MinecraftClient client) {
		boolean inShip = client.player != null && client.player.getVehicle() instanceof ShipEntity;
		if (inShip && !wasPiloting) {
			previousPerspective = client.options.getPerspective();
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		} else if (!inShip && wasPiloting && previousPerspective != null) {
			client.options.setPerspective(previousPerspective);
			previousPerspective = null;
		}
		wasPiloting = inShip;
	}

	public static void reset() {
		wasPiloting = false;
		previousPerspective = null;
	}

	/** Pfeil relativ zur Blickrichtung (8 Richtungen). */
	static String arrow(float playerYaw, Vec3d to) {
		double targetYaw = Math.toDegrees(Math.atan2(-to.x, to.z));
		double relative = MathHelper.wrapDegrees(targetYaw - playerYaw);
		int index = (int) Math.floorMod(Math.round(relative / 45.0), 8);
		return ARROWS[index];
	}
}
