package com.santiq.kingdomomnitrix.client.space;

import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.math.Vec3d;

/** Darstellung des Alls: keine Wolken, kein Vanilla-Himmel (eigener Sternenhimmel), schwarzer Nebel, kein Dunst. */
public class SpaceDimensionEffects extends DimensionEffects {
	public SpaceDimensionEffects() {
		super(Float.NaN, false, SkyType.NONE, false, false);
	}

	@Override
	public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
		return Vec3d.ZERO;
	}

	@Override
	public boolean useThickFog(int camX, int camY) {
		return false;
	}
}
