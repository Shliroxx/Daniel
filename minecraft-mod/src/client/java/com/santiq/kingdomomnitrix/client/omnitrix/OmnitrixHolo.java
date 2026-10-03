package com.santiq.kingdomomnitrix.client.omnitrix;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Omnitrix-Hologramm-Meldung ueber der Schnellleiste: kurze, animierte Statuszeile des Geraets (Omnitrix OS).
 * Klappt aus der Mitte auf, schreibt den Text mit einer Scanlinie frei, blendet aus. Immer nur eine Meldung — eine neue
 * ersetzt die alte, damit nie ein Stapel Text das Bild verdeckt.
 */
public final class OmnitrixHolo {
	public static final Identifier LAYER_ID = KingdomOmnitrix.id("omnitrix_holo");

	private static final long OPEN_MS = 140L;
	private static final long TYPE_MS = 260L;
	private static final long FADE_MS = 220L;
	private static final int ICON = 20;
	private static final int PAD = 4;
	private static final int BOTTOM_OFFSET = 74;

	/** Eine Meldung: Titel (klein, Akzentfarbe), Hauptzeile, optionale Fusszeile, optional Alien-Symbol. */
	public record Message(Text title, Text body, Optional<Text> footer, Optional<Identifier> alien, int color, long durationMs,
			int priority) {
		/** Meldung mit mittlerem Vorrang (Client-Meldungen wie Smart Choice, Meisterschaft). */
		public Message(Text title, Text body, Optional<Text> footer, Optional<Identifier> alien, int color, long durationMs) {
			this(title, body, footer, alien, color, durationMs, 2);
		}
	}

	/** So lange bleibt eine Meldung mindestens stehen, bevor eine unwichtigere sie ersetzen darf. */
	private static final long MIN_HOLD_MS = 1200L;
	/** Laenger wartende Meldungen sind veraltet und entfallen. */
	private static final long MAX_WAIT_MS = 2000L;

	private static Message current;
	private static long shownAt;
	private static Message pending;
	private static long pendingSince;

	private OmnitrixHolo() {
	}

	public static void register() {
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.santiq.kingdomomnitrix.networking.OmnitrixHoloPayload.ID, (payload, context) -> receive(context.client(), payload));
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.CHAT, IdentifiedLayer.of(LAYER_ID, OmnitrixHolo::render)));
	}

	/** Server-Meldung: als Hologramm oder (Spieler-Einstellung) schlicht in der Aktionsleiste. */
	private static void receive(MinecraftClient client, com.santiq.kingdomomnitrix.networking.OmnitrixHoloPayload payload) {
		if (!OmnitrixFeedback.settings().holoMessages) {
			client.inGameHud.setOverlayMessage(payload.body().copy().withColor(payload.style().color()), false);
			return;
		}
		show(new Message(payload.title(), payload.body(), payload.footer(), payload.alien(), payload.style().color(),
				Math.max(500, payload.style().durationMs()), payload.style().priority()));
	}

	/**
	 * Meldung zeigen. Eine wichtigere, die noch keine {@link #MIN_HOLD_MS} zu sehen war, wird nicht verdraengt — die neue
	 * wartet kurz und erscheint danach (oder entfaellt, wenn sie veraltet ist).
	 */
	public static void show(Message message) {
		long now = System.currentTimeMillis();
		if (current != null && now - shownAt < Math.min(MIN_HOLD_MS, current.durationMs()) && current.priority() > message.priority()) {
			if (pending == null || message.priority() >= pending.priority()) {
				pending = message;
				pendingSince = now;
			}
			return;
		}
		current = message;
		shownAt = now;
	}

	/** Wartende Meldung nachziehen, sobald die aktuelle lange genug stand oder vorbei ist. */
	private static void promotePending(long now) {
		if (pending == null) {
			return;
		}
		if (now - pendingSince > MAX_WAIT_MS) {
			pending = null;
			return;
		}
		boolean currentDone = current == null || now - shownAt >= current.durationMs();
		boolean currentHeld = current != null && now - shownAt >= MIN_HOLD_MS && current.priority() <= pending.priority();
		if (currentDone || currentHeld) {
			current = pending;
			shownAt = now;
			pending = null;
		}
	}

	/** Kurzform ohne Symbol und Fusszeile. */
	public static void show(Text title, Text body, int color, long durationMs) {
		show(new Message(title, body, Optional.empty(), Optional.empty(), color, durationMs));
	}

	/** Meldung sofort ausblenden (z. B. Smart Choice bestaetigt). */
	public static void clear() {
		current = null;
		pending = null;
	}

	/** Aktuell sichtbare Meldung (fuer Bildschirme, die das HUD verdecken, z. B. die Kalibrier-Werkbank). */
	public static Optional<Message> visible() {
		Message message = current;
		return message != null && System.currentTimeMillis() - shownAt < message.durationMs() ? Optional.of(message) : Optional.empty();
	}

	public static boolean isShowing(Message message) {
		return current == message && System.currentTimeMillis() - shownAt < message.durationMs();
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		promotePending(System.currentTimeMillis());
		Message message = current;
		if (message == null || client.player == null || client.options.hudHidden) {
			return;
		}
		long age = System.currentTimeMillis() - shownAt;
		if (age >= message.durationMs()) {
			current = null;
			return;
		}
		TextRenderer font = client.textRenderer;
		boolean icon = message.alien().isPresent();
		int textWidth = Math.max(font.getWidth(message.title()) * 3 / 4, font.getWidth(message.body()));
		if (message.footer().isPresent()) {
			textWidth = Math.max(textWidth, font.getWidth(message.footer().get()) * 3 / 4);
		}
		int width = textWidth + PAD * 2 + (icon ? ICON + PAD : 0);
		int height = PAD + 6 + 2 + font.fontHeight + (message.footer().isPresent() ? 8 : 0) + PAD;
		height = Math.max(height, icon ? ICON + PAD * 2 : 0);
		int cx = context.getScaledWindowWidth() / 2;
		int y = context.getScaledWindowHeight() - BOTTOM_OFFSET - height;

		float open = MathHelper.clamp(age / (float) OPEN_MS, 0.0f, 1.0f);
		float ease = 1.0f - (1.0f - open) * (1.0f - open) * (1.0f - open);
		float fade = MathHelper.clamp((message.durationMs() - age) / (float) FADE_MS, 0.0f, 1.0f);
		int alpha = Math.round(255 * fade);
		if (alpha < 8) {
			return;
		}
		int half = Math.max(2, Math.round(width / 2.0f * ease));
		int x0 = cx - half;
		int x1 = cx + half;
		int rgb = message.color() & 0xFFFFFF;
		// Karte: dunkles Glas, Akzentkante oben/unten, Eck-Klammern
		context.fill(x0, y, x1, y + height, scale(0xD0, fade) << 24 | 0x06120A);
		context.fill(x0, y, x1, y + 1, alpha << 24 | rgb);
		context.fill(x0, y + height - 1, x1, y + height, scale(0x80, fade) << 24 | rgb);
		context.fill(x0, y, x0 + 1, y + 4, alpha << 24 | rgb);
		context.fill(x1 - 1, y, x1, y + 4, alpha << 24 | rgb);
		if (ease < 1.0f) {
			return;
		}
		int left = cx - width / 2 + PAD;
		if (icon) {
			Identifier alien = message.alien().get();
			if (!UiDraw.alienIcon(context, alien, left, y + (height - ICON) / 2, ICON, fade)) {
				UiDraw.alienSilhouette(context, alien, left, y + (height - ICON) / 2, ICON, rgb, fade);
			}
			left += ICON + PAD;
		}
		// Text wird von links freigeschrieben; vor der Scanlinie bleibt er verborgen
		float typed = MathHelper.clamp((age - OPEN_MS) / (float) TYPE_MS, 0.0f, 1.0f);
		int reveal = left + Math.round(textWidth * typed);
		context.enableScissor(left, y, Math.max(left, reveal), y + height);
		context.getMatrices().push();
		context.getMatrices().translate(left, y + PAD, 0.0f);
		context.getMatrices().scale(0.75f, 0.75f, 1.0f);
		context.drawTextWithShadow(font, message.title(), 0, 0, alpha << 24 | rgb);
		context.getMatrices().pop();
		context.drawTextWithShadow(font, message.body(), left, y + PAD + 8, alpha << 24 | 0xFFFFFF);
		if (message.footer().isPresent()) {
			context.getMatrices().push();
			context.getMatrices().translate(left, y + PAD + 8 + font.fontHeight + 1, 0.0f);
			context.getMatrices().scale(0.75f, 0.75f, 1.0f);
			context.drawTextWithShadow(font, message.footer().get(), 0, 0, alpha << 24 | 0xFFD84A);
			context.getMatrices().pop();
		}
		context.disableScissor();
		if (typed < 1.0f) {
			context.fill(reveal, y + 2, reveal + 1, y + height - 2, alpha << 24 | 0xB8FFB0);
		}
	}

	private static int scale(int value, float factor) {
		return Math.round(value * factor);
	}
}
