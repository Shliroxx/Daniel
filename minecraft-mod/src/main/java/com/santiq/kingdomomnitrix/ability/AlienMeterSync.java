package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Schickt Alien-Anzeigen (Tempo, Kernhitze) an den Spieler und an alle Beobachter — nur bei spuerbarer Aenderung
 * ({@link #STEP}) oder beim Erreichen von 0/100, damit der Netzverkehr klein bleibt; der Client glaettet dazwischen.
 */
final class AlienMeterSync {
	private static final float STEP = 1.0f;
	private static final Map<UUID, float[]> SENT = new HashMap<>();

	private AlienMeterSync() {
	}

	static void update(ServerPlayerEntity player, int meter, float value) {
		float[] sent = SENT.computeIfAbsent(player.getUuid(), id -> {
			float[] fresh = new float[AlienMeterPayload.COUNT];
			java.util.Arrays.fill(fresh, -1.0f);
			return fresh;
		});
		float last = sent[meter];
		boolean edge = (value <= 0.0f || value >= 100.0f) && value != last;
		if (!edge && Math.abs(value - last) < STEP) {
			return;
		}
		sent[meter] = value;
		AlienMeterPayload payload = new AlienMeterPayload(player.getId(), meter, value);
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
			if (watcher != player) {
				ServerPlayNetworking.send(watcher, payload);
			}
		}
	}

	/** Anzeige auf 0 (Rueckverwandlung) und Gedaechtnis loeschen. */
	static void clear(ServerPlayerEntity player, int meter) {
		update(player, meter, 0.0f);
	}

	static void forget(UUID player) {
		SENT.remove(player);
	}
}
