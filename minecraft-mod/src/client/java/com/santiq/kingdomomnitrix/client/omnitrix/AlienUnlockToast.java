package com.santiq.kingdomomnitrix.client.omnitrix;

import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Freischalt-Meldung: Omnitrix-Hologramm-Karte mit Alien-Symbol, das aus der Silhouette „einscannt“. Ausgeloest vom
 * synchronisierten Heldenzustand (neues Alien in {@code unlocked_aliens}), nicht von einem eigenen Paket — sieht damit
 * genau, was der Server gespeichert hat.
 */
public final class AlienUnlockToast implements Toast {
	private static final long DURATION = 5000L;
	private static final long SCAN = 900L;
	private static final int WIDTH = 160;
	private static final int HEIGHT = 32;

	private static Set<Identifier> known;

	private final Identifier alien;
	private final int color;

	private AlienUnlockToast(Identifier alien, int color) {
		this.alien = alien;
		this.color = color;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AlienUnlockToast::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			known = null;
			return;
		}
		Set<Identifier> now = HeroDataAccess.get(client.player).unlockedAliens();
		if (known == null) {
			// erster Tick nach dem Betreten: vorhandene Aliens sind nicht neu
			known = new HashSet<>(now);
			return;
		}
		if (known.equals(now)) {
			return;
		}
		for (Identifier id : now) {
			if (!known.contains(id)) {
				int color = AlienRegistry.get(client.world.getRegistryManager(), id).map(def -> def.color()).orElse(0x39FF14);
				client.getToastManager().add(new AlienUnlockToast(id, color));
			}
		}
		known = new HashSet<>(now);
	}

	@Override
	public Visibility draw(DrawContext context, ToastManager manager, long startTime) {
		var font = manager.getClient().textRenderer;
		// Hologramm-Karte: dunkel, gruene Kante, Leuchtstreifen links
		context.fill(0, 0, WIDTH, HEIGHT, 0xE00A140C);
		context.fill(0, 0, WIDTH, 1, 0xFF39FF14);
		context.fill(0, HEIGHT - 1, WIDTH, HEIGHT, 0xFF1C7A10);
		context.fill(0, 0, 2, HEIGHT, 0xFF000000 | color);
		float scan = Math.min(1.0f, startTime / (float) SCAN);
		// erst die gruene Silhouette, dann blendet das farbige Symbol ein; Scanlinie laeuft von oben nach unten
		UiDraw.alienSilhouette(context, alien, 4, 2, 28, 0x39FF14, 1.0f - scan * 0.8f);
		UiDraw.alienIcon(context, alien, 4, 2, 28, scan);
		if (scan < 1.0f) {
			int line = 2 + Math.round(28 * scan);
			context.fill(3, line, 33, line + 1, 0xC0B8FFB0);
		}
		context.drawTextWithShadow(font, Text.translatable("toast.kingdomomnitrix.dna_unlocked").formatted(Formatting.GREEN), 36, 7, 0xFFFFFFFF);
		context.drawTextWithShadow(font, TransformationManager.alienName(alien).withColor(color).formatted(Formatting.BOLD), 36, 18, 0xFFFFFFFF);
		return startTime >= DURATION * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public int getWidth() {
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}
}
