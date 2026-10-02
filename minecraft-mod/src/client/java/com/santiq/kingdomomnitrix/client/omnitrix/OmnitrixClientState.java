package com.santiq.kingdomomnitrix.client.omnitrix;

import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixProfile;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixStatus;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Omnitrix-Werte pro Client-Tick zwischengespeichert. Status (inkl. Inventar-Pruefung), Hitze und Profil aendern sich
 * hoechstens pro Tick, wurden aber pro Bild mehrfach gebraucht (HUD, Handgelenk, Zifferblatt, Rueckmeldungen).
 * Eigener Spieler und andere Spieler werden beim ersten Zugriff im Tick berechnet; mit jedem Tick verfaellt alles.
 */
public final class OmnitrixClientState {
	private record Snapshot(OmnitrixStatus status, float heat, OmnitrixProfile profile) {
	}

	private static final Map<Integer, Snapshot> CACHE = new HashMap<>();

	private OmnitrixClientState() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> CACHE.clear());
	}

	public static OmnitrixStatus status(PlayerEntity player) {
		return snapshot(player).status();
	}

	public static float heat(PlayerEntity player) {
		return snapshot(player).heat();
	}

	public static OmnitrixProfile profile(PlayerEntity player) {
		return snapshot(player).profile();
	}

	private static Snapshot snapshot(PlayerEntity player) {
		if (!MinecraftClient.getInstance().isOnThread()) {
			// ausserhalb des Render-Threads nie zwischenspeichern (HashMap ist nicht threadsicher)
			return compute(player);
		}
		return CACHE.computeIfAbsent(player.getId(), id -> compute(player));
	}

	private static Snapshot compute(PlayerEntity player) {
		return new Snapshot(OmnitrixCore.status(player), OmnitrixCore.heat(player), OmnitrixCore.profile(player));
	}
}
