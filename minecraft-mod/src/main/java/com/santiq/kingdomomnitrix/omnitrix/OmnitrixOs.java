package com.santiq.kingdomomnitrix.omnitrix;

import com.santiq.kingdomomnitrix.networking.OmnitrixHoloPayload;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Omnitrix OS: alle Meldungen des Geraets an seinen Traeger als kurze Hologramm-Karte (Titel, Zeile, Hinweis, Symbol).
 * Ein Katalog statt verstreuter Texte — Farbe, Dauer und Vorrang je Ereignis stehen hier. Ohne Mod-Client (oder
 * mit abgeschalteten Hologrammen) erscheint die Zeile in der Aktionsleiste.
 */
public final class OmnitrixOs {
	/** Ereignis: Titel-Schluessel {@code holo.kingdomomnitrix.os.<name>}, Farbe, Dauer, Vorrang, Aktionsleisten-Farbe. */
	public enum Event {
		TRANSFORMED(0x39FF14, 1600, 1, Formatting.GREEN),
		QUICK_CHANGE(0x7DFF9C, 1600, 1, Formatting.GREEN),
		REVERTED(0x9ACFA8, 1400, 1, Formatting.GREEN),
		TIMEOUT(0xFF5555, 2200, 2, Formatting.RED),
		READY(0x39FF14, 1500, 0, Formatting.GREEN),
		COOLED(0x4FC3FF, 1800, 1, Formatting.AQUA),
		RECHARGING(0xFF8A3C, 1400, 1, Formatting.RED),
		REFUSED(0xFF5555, 1800, 2, Formatting.RED),
		NEED_SPACE(0xFF8A3C, 1500, 2, Formatting.RED),
		WARNING(0xFFC21A, 2600, 3, Formatting.GOLD),
		OVERHEAT(0xFF2A1A, 3200, 4, Formatting.RED),
		LOCKED(0x9A9A9A, 2200, 3, Formatting.GRAY),
		UNLOCKED(0x39FF14, 1500, 1, Formatting.GREEN),
		MASTER_CONTROL(0xFFC94A, 3000, 3, Formatting.GOLD),
		DNA_SHOCK(0xB8FFA0, 3000, 4, Formatting.RED),
		EMERGENCY(0xFF4A2A, 3200, 5, Formatting.GOLD),
		MALFUNCTION(0xFF5A3C, 4200, 4, Formatting.RED),
		DIAGNOSTICS(0x4FC3FF, 5000, 2, Formatting.AQUA),
		RECALIBRATED(0x7DFF9C, 2500, 2, Formatting.GREEN),
		SELF_DESTRUCT(0xFF2A1A, 1100, 5, Formatting.RED),
		DETONATED(0xFF2A1A, 5000, 5, Formatting.RED);

		private final int color;
		private final int durationMs;
		private final int priority;
		private final Formatting fallback;

		Event(int color, int durationMs, int priority, Formatting fallback) {
			this.color = color;
			this.durationMs = durationMs;
			this.priority = priority;
			this.fallback = fallback;
		}

		public String titleKey() {
			return "holo.kingdomomnitrix.os." + name().toLowerCase(Locale.ROOT);
		}

		public int color() {
			return color;
		}

		public int priority() {
			return priority;
		}
	}

	private OmnitrixOs() {
	}

	/** Meldung ohne Symbol und Hinweis. */
	public static void send(ServerPlayerEntity player, Event event, Text body) {
		send(player, event, body, Optional.empty(), Optional.empty());
	}

	/** Meldung mit Alien-Symbol. */
	public static void send(ServerPlayerEntity player, Event event, Text body, Identifier alien) {
		send(player, event, body, Optional.empty(), Optional.of(alien));
	}

	public static void send(ServerPlayerEntity player, Event event, Text body, Optional<Text> footer, Optional<Identifier> alien) {
		if (ServerPlayNetworking.canSend(player, OmnitrixHoloPayload.ID)) {
			ServerPlayNetworking.send(player, new OmnitrixHoloPayload(Text.translatable(event.titleKey()), body, footer, alien,
					new OmnitrixHoloPayload.Style(event.color, event.durationMs, event.priority)));
		} else {
			player.sendMessage(body.copy().formatted(event.fallback), true);
		}
	}
}
