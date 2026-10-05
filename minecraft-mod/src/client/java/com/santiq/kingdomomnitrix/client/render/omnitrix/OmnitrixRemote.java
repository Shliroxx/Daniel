package com.santiq.kingdomomnitrix.client.render.omnitrix;

import com.santiq.kingdomomnitrix.alien.OmnitrixPhase;
import com.santiq.kingdomomnitrix.networking.OmnitrixPhaseSyncPayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/** Omnitrix-Zustaende anderer Spieler (vom Server weitergereicht) und daraus abgeleitete Arm-/Kern-Werte. */
public final class OmnitrixRemote {
	private record Seen(OmnitrixPhase phase, OmnitrixPhase previous, long since) {
	}

	private static final Map<Integer, Seen> PHASES = new HashMap<>();
	private static final float BLEND = 0.25f;

	private OmnitrixRemote() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(OmnitrixPhaseSyncPayload.ID, (payload, context) -> {
			Seen old = PHASES.get(payload.entityId());
			PHASES.put(payload.entityId(), new Seen(payload.phase(), old == null ? OmnitrixPhase.IDLE : old.phase(), System.nanoTime()));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PHASES.clear());
	}

	private static float blend(PlayerEntity player, java.util.function.Predicate<OmnitrixPhase> on) {
		Seen seen = PHASES.get(player.getId());
		if (seen == null) {
			return 0.0f;
		}
		float t = MathHelper.clamp((System.nanoTime() - seen.since()) / 1.0e9f / BLEND, 0.0f, 1.0f);
		float from = on.test(seen.previous()) ? 1.0f : 0.0f;
		float to = on.test(seen.phase()) ? 1.0f : 0.0f;
		return MathHelper.lerp(t, from, to);
	}

	/** Arm-Anhebung 0..1: eigener Spieler aus dem Controller, andere aus dem weitergereichten Zustand */
	public static float raise(PlayerEntity player) {
		return player == MinecraftClient.getInstance().player ? OmnitrixController.raise() : blend(player, OmnitrixPhase::isArmRaised);
	}

	public static float lift(PlayerEntity player) {
		return player == MinecraftClient.getInstance().player ? OmnitrixController.lift() : blend(player, OmnitrixPhase::isOpen);
	}

	/** Leuchtstaerke 0..1 der Sanduhr */
	public static float energy(PlayerEntity player) {
		if (player == MinecraftClient.getInstance().player) {
			return OmnitrixController.energy();
		}
		Seen seen = PHASES.get(player.getId());
		if (seen == null) {
			return 0.0f;
		}
		return switch (seen.phase()) {
			case OPENING, SELECTING -> 0.7f;
			case CONFIRMING, IMPACT -> 1.0f;
			default -> 0.0f;
		};
	}
}
