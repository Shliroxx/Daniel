package com.santiq.kingdomomnitrix.client.vfx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * Kurzer Kamera-Stoss (nur lokale Ansicht, keine Wirkung auf die Blickrichtung des Spielers): abklingendes Zittern
 * aus zwei ueberlagerten Schwingungen. Ausgeloest z. B. beim Aufprall nach der Verwandlung.
 */
public final class CameraShake {
	private static final float MAX_DEGREES = 2.2f;

	private static float intensity;
	private static long start = Long.MIN_VALUE / 2;
	private static int duration = 1;

	private CameraShake() {
	}

	/**
	 * @param strength 0–1 (1 = staerkster Stoss)
	 * @param ticks    Dauer des Abklingens
	 * @param delay    Ticks bis zum Beginn (z. B. Aufprall nach einer Animation)
	 */
	public static void start(float strength, int ticks, int delay) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		long now = client.world.getTime() + delay;
		// ein laufender staerkerer Stoss wird nicht abgeschwaecht
		if (current(client.world.getTime(), 0.0f) > strength) {
			return;
		}
		intensity = MathHelper.clamp(strength, 0.0f, 1.0f);
		start = now;
		duration = Math.max(1, ticks);
	}

	private static float current(long time, float tickDelta) {
		float age = time - start + tickDelta;
		if (age < 0.0f || age > duration) {
			return 0.0f;
		}
		float fade = 1.0f - age / duration;
		return intensity * fade * fade;
	}

	/** @return {yaw, pitch} Versatz in Grad fuer diesen Frame */
	public static float[] offset(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return new float[] {0.0f, 0.0f};
		}
		float strength = current(client.world.getTime(), tickDelta);
		if (strength <= 0.0f) {
			return new float[] {0.0f, 0.0f};
		}
		float t = (client.world.getTime() + tickDelta) * 1.9f;
		float yaw = (MathHelper.sin(t * 2.3f) * 0.6f + MathHelper.sin(t * 5.1f) * 0.4f) * strength * MAX_DEGREES;
		float pitch = (MathHelper.cos(t * 2.9f) * 0.6f + MathHelper.sin(t * 4.3f) * 0.4f) * strength * MAX_DEGREES;
		return new float[] {yaw, pitch};
	}
}
