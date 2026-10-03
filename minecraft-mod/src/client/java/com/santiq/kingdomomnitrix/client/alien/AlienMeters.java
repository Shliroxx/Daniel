package com.santiq.kingdomomnitrix.client.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Alien-Anzeigen auf dem Client (XLR8-Tempo, Heatblast-Kernhitze) je Spieler, aus {@link AlienMeterPayload}. Der Server
 * schickt nur spuerbare Aenderungen; hier wird weich nachgezogen, damit Tacho-Nadel und Aura fliessend laufen.
 */
public final class AlienMeters {
	/** welches Alien welche Anzeige hat */
	private static final Map<Identifier, Integer> METER_OF = Map.of(
			KingdomOmnitrix.id("xlr8"), AlienMeterPayload.TEMPO,
			KingdomOmnitrix.id("heatblast"), AlienMeterPayload.HEAT);
	/** Zielwert und angezeigter Wert (aktueller/vorheriger Tick) je Spieler und Anzeige */
	private static final Map<Integer, float[][]> VALUES = new HashMap<>();
	/** Annaeherung je Tick an den Zielwert */
	private static final float FOLLOW = 0.35f;
	private static final Random RANDOM = Random.create();

	private AlienMeters() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AlienMeterPayload.ID, (payload, context) -> {
			if (payload.meter() < 0 || payload.meter() >= AlienMeterPayload.COUNT) {
				return;
			}
			values(payload.entityId())[payload.meter()][0] = MathHelper.clamp(payload.value(), 0.0f, 100.0f);
		});
		ClientTickEvents.END_CLIENT_TICK.register(AlienMeters::tick);
	}

	private static float[][] values(int entityId) {
		return VALUES.computeIfAbsent(entityId, id -> new float[AlienMeterPayload.COUNT][3]);
	}

	private static void tick(MinecraftClient client) {
		if (client.world == null) {
			VALUES.clear();
			return;
		}
		Set<Integer> seen = new HashSet<>();
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			seen.add(player.getId());
			float[][] meters = VALUES.get(player.getId());
			if (meters == null) {
				continue;
			}
			Optional<Integer> own = meterOf(player);
			for (int m = 0; m < meters.length; m++) {
				float[] v = meters[m];
				if (own.isEmpty() || own.get() != m) {
					v[0] = 0.0f; // zurueckverwandelt: Anzeige faellt ab
				}
				v[2] = v[1];
				v[1] = MathHelper.lerp(FOLLOW, v[1], v[0]);
			}
			// Funken um XLR8 ab hohem Tempo (nur Optik, nur auf dem Client)
			float tempo = meters[AlienMeterPayload.TEMPO][1];
			if (tempo >= 75.0f && RANDOM.nextFloat() < (tempo - 60.0f) / 60.0f) {
				client.world.addParticle(ParticleTypes.ELECTRIC_SPARK,
						player.getX() + (RANDOM.nextDouble() - 0.5) * 1.0, player.getY() + RANDOM.nextDouble() * 1.9,
						player.getZ() + (RANDOM.nextDouble() - 0.5) * 1.0, 0.0, 0.02, 0.0);
			}
		}
		VALUES.keySet().retainAll(seen);
	}

	/** Anzeige des aktiven Aliens dieses Spielers, falls es eine hat. */
	public static Optional<Integer> meterOf(PlayerEntity player) {
		return TransformationManager.get(player).activeAlien().map(METER_OF::get);
	}

	/** Weicher Wert 0–100 fuer das aktuelle Bild. */
	public static float value(int entityId, int meter, float tickDelta) {
		float[][] meters = VALUES.get(entityId);
		if (meters == null) {
			return 0.0f;
		}
		return MathHelper.lerp(tickDelta, meters[meter][2], meters[meter][1]);
	}
}
