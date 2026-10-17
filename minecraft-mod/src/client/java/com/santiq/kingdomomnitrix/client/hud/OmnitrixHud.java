package com.santiq.kingdomomnitrix.client.hud;

import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixClientState;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AbilitySlot;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixStatus;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.client.ui.HudGfx;
import com.santiq.kingdomomnitrix.client.ui.Icons;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import java.util.Optional;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Omnitrix-Anzeige (Standard unten rechts): aktuelles Alien mit Meisterschaft, Restzeit, Energie und die
 * Faehigkeiten mit Symbol, Taste und Abklingzeit. Ohne Verwandlung: gewaehltes Alien und Nachladezeit.
 */
public final class OmnitrixHud implements HudElement {
	public static final OmnitrixHud INSTANCE = new OmnitrixHud();
	private static final Identifier ID = KingdomOmnitrix.id("omnitrix");

	private static final int PADDING = 5;
	/** Abstand von oben: unter den zwei Reihen Effekt-Symbolen, damit die Ego-Hand unten rechts frei bleibt */
	public static final int TOP_OFFSET = 54;
	private static final int WIDTH = 132;
	/** Abzeichen links oben: Alien-Symbol im Ring, der Ring zeigt die Restzeit */
	private static final int BADGE = 28;
	private static final int ENERGY_HEIGHT = 5;
	private static final int SLOT_SIZE = 30;
	private static final int SLOT_GAP = 6;
	private static final int ROW_GAP = 10;
	/** Tasten-Chip ragt unten aus dem Feld */
	private static final int CHIP_OVERHANG = 5;
	private static final int ICON = 11;
	private static final int ICON_IN_SLOT = 20;
	private static final int PER_ROW = 3;
	private static final int TIMER_WARNING_COLOR = 0xFFFF5555;
	private static final int ENERGY_COLOR = 0x4FC3FF;
	private static final int GOLD = 0xFFC94A;
	private static final int WARNING_TICKS = 200;
	private static final int FLASH_TICKS = 10;
	/** je Slot: Weltzeit, zu der die Abklingzeit endete (Bereit-Blitz), und ob sie zuletzt lief */
	private static final long[] READY_AT = new long[6];
	private static final boolean[] COOLING = new boolean[6];
	/** nachlaufende Energieanzeige (zeigt verbrauchte Energie kurz blass an) */
	private static float energyGhost;

	private OmnitrixHud() {
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public Text name() {
		return Text.translatable("hud.kingdomomnitrix.element.omnitrix");
	}

	@Override
	public HudAnchor defaultAnchor() {
		return HudAnchor.TOP_RIGHT;
	}

	private static final int HEAT_HEIGHT = 2;

	@Override
	public int defaultX() {
		return -4;
	}

	@Override
	public int defaultY() {
		return TOP_OFFSET;
	}

	@Override
	public boolean isActive(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		return player != null && client.world != null
				&& (TransformationManager.get(player).isTransformed() || OmnitrixItem.hasOmnitrix(player));
	}

	private static Optional<AlienDefinition> shown(MinecraftClient client, TransformationState state) {
		return state.activeAlien().or(state::selectedAlien).flatMap(id -> AlienRegistry.get(client.world.getRegistryManager(), id));
	}

	private static boolean transformed(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return false;
		}
		TransformationState state = TransformationManager.get(client.player);
		return state.isTransformed() && shown(client, state).isPresent();
	}

	@Override
	public int width(MinecraftClient client) {
		return WIDTH;
	}

	/** Reihen der Faehigkeits-Slots (je 3: R/V/B, darunter Shift + R/V/B). */
	private static int slotRows(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return 1;
		}
		int count = shown(client, TransformationManager.get(client.player)).map(a -> a.abilities().size()).orElse(1);
		return Math.max(1, (count + PER_ROW - 1) / PER_ROW);
	}

	@Override
	public int height(MinecraftClient client) {
		int font = client.textRenderer.fontHeight;
		int rows = slotRows(client);
		return (transformed(client)
				? PADDING + BADGE + 6 + ENERGY_HEIGHT + 8 + rows * SLOT_SIZE + (rows - 1) * ROW_GAP + CHIP_OVERHANG + 4 + PADDING
				: PADDING + font * 2 + 2 + PADDING) + HEAT_HEIGHT + 2;
	}

	@Override
	public int previewHeight() {
		return 54;
	}

	@Override
	public int previewWidth() {
		return WIDTH;
	}

	@Override
	public void render(DrawContext context, MinecraftClient client, float tickDelta) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		TransformationState state = TransformationManager.get(player);
		long now = client.world.getTime();
		Optional<Identifier> shownId = state.activeAlien().or(state::selectedAlien);
		Optional<AlienDefinition> alien = shown(client, state);
		int accent = state.isTransformed() && alien.isPresent() ? alien.get().color() & 0xFFFFFF : UiTheme.OMNITRIX.accent() & 0xFFFFFF;
		panel(context, WIDTH, height(client), accent);
		if (state.isTransformed() && alien.isPresent()) {
			renderTransformed(context, client, state, shownId.get(), alien.get(), now);
		} else {
			renderIdle(context, client.textRenderer, player, state, shownId, alien, now);
		}
		renderHeat(context, player, height(client));
	}

	/** Rahmen: dunkler Glasverlauf, Rand in Akzentfarbe, leuchtende Kante oben, Eckwinkel. */
	private static void panel(DrawContext context, int w, int h, int accent) {
		HudGfx.roundedGradient(context, 0, 0, w, h, 3, 0xD8141A20, 0xE0080A0C);
		HudGfx.roundedBorder(context, 0, 0, w, h, HudGfx.withAlpha(HudGfx.mix(accent, 0x000000, 0.35f), 200));
		context.fillGradient(3, 1, w - 3, 2, HudGfx.withAlpha(accent, 220), HudGfx.withAlpha(accent, 220));
		context.fillGradient(3, 2, w - 3, 8, HudGfx.withAlpha(accent, 50), HudGfx.withAlpha(accent, 0));
		int corner = HudGfx.withAlpha(HudGfx.mix(accent, 0xFFFFFF, 0.3f), 255);
		context.fill(1, h - 6, 2, h - 2, corner);
		context.fill(1, h - 2, 6, h - 1, corner);
		context.fill(w - 2, h - 6, w - 1, h - 2, corner);
		context.fill(w - 6, h - 2, w - 1, h - 1, corner);
	}

	/** Hitze-Leiste am unteren Rand: gruen → gelb (Warnschwelle) → rot; blinkt bei Warnung und Ueberhitzung. */
	private static void renderHeat(DrawContext context, ClientPlayerEntity player, int panelHeight) {
		float heat = OmnitrixClientState.heat(player);
		OmnitrixStatus status = OmnitrixClientState.status(player);
		float warning = OmnitrixClientState.profile(player).heatWarning();
		int x = PADDING + 2;
		int width = WIDTH - x - PADDING;
		int y = panelHeight - PADDING - HEAT_HEIGHT;
		int color;
		if (status == OmnitrixStatus.OVERHEATED || heat >= warning) {
			boolean on = (System.currentTimeMillis() / 250) % 2 == 0;
			color = on ? 0xFFFF2A1A : 0xFF8A1A10;
		} else if (heat >= warning * 0.6f) {
			color = 0xFFFFC21A;
		} else {
			color = 0xFF000000 | com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors.primary(player);
		}
		UiDraw.bar(context, x, y, width, HEAT_HEIGHT, status == OmnitrixStatus.OVERHEATED ? 1.0f : heat, color);
		// Markierung der Warnschwelle
		int mark = x + Math.round(width * warning);
		context.fill(mark, y - 1, mark + 1, y + HEAT_HEIGHT + 1, 0xC0FFFFFF);
	}

	private static void renderTransformed(DrawContext context, MinecraftClient client, TransformationState state,
			Identifier alienId, AlienDefinition alien, long now) {
		TextRenderer font = client.textRenderer;
		int rgb = alien.color() & 0xFFFFFF;
		int cx = PADDING + 2;
		int cy = PADDING;
		long remaining = state.remainingTicks(now);
		boolean warning = remaining < WARNING_TICKS;
		int mastery = client.player != null ? AlienMasteryManager.get(client.player).level(alienId) : 1;

		// Abzeichen: dunkle Scheibe, Alien-Symbol, Ring = Restzeit (laeuft im Uhrzeigersinn leer)
		float bx = cx + BADGE / 2.0f;
		float by = cy + BADGE / 2.0f;
		float time = state.totalTicks() > 0 ? (float) remaining / state.totalTicks() : 0.0f;
		int ringColor = warning && (now / 5) % 2 == 0 ? TIMER_WARNING_COLOR : 0xFF000000 | HudGfx.mix(rgb, 0xFFFFFF, 0.2f);
		HudGfx.disc(context, bx, by, BADGE / 2.0f, 0xE0080A0C);
		HudGfx.ring(context, bx, by, BADGE / 2.0f, BADGE / 2.0f - 2.5f, 0xFF20262C);
		HudGfx.arc(context, bx, by, BADGE / 2.0f, BADGE / 2.0f - 2.5f, 0.0f, MathHelper.TAU * time, ringColor);
		HudGfx.disc(context, bx, by, BADGE / 2.0f - 4.0f, 0xFF000000 | HudGfx.mul(rgb, 0.22f));
		int iconSize = BADGE - 10;
		if (!UiDraw.alienIcon(context, alienId, (int) (bx - iconSize / 2.0f), (int) (by - iconSize / 2.0f), iconSize, 1.0f)) {
			UiDraw.icon(context, Icons.command("omnitrix"), Icons.command("omnitrix"), (int) bx - 8, (int) by - 8, UiDraw.ICON_SIZE);
		}

		// Name, Meisterschaft, Restzeit
		int textX = cx + BADGE + 5;
		int textRight = WIDTH - PADDING - 1;
		Text name = TransformationManager.alienName(alienId).withColor(rgb).formatted(Formatting.BOLD);
		drawFitted(context, font, name, textX, cy + 3, textRight - textX);
		String stars = "★" + mastery;
		context.drawTextWithShadow(font, stars, textX, cy + 15, 0xFF000000 | GOLD);
		String seconds = formatTime(remaining);
		context.drawTextWithShadow(font, seconds, textRight - font.getWidth(seconds), cy + 15, warning ? TIMER_WARNING_COLOR : 0xFFE8EEF4);
		cy += BADGE + 6;

		// Energie: segmentierter Balken mit nachlaufendem Rest
		float energy = state.currentEnergy(alien, now);
		float fraction = alien.maxEnergy() > 0 ? energy / alien.maxEnergy() : 0.0f;
		energyGhost = Math.max(fraction, energyGhost - 0.012f);
		HudGfx.segmentedBar(context, cx, cy, WIDTH - cx - PADDING, ENERGY_HEIGHT, fraction, energyGhost, ENERGY_COLOR, 10);
		cy += ENERGY_HEIGHT + 8;

		int rowWidth = PER_ROW * SLOT_SIZE + (PER_ROW - 1) * SLOT_GAP;
		int left = (WIDTH - rowWidth) / 2 + 1;
		for (int i = 0; i < alien.abilities().size(); i++) {
			int col = i % PER_ROW;
			int row = i / PER_ROW;
			drawSlot(context, font, left + col * (SLOT_SIZE + SLOT_GAP), cy + row * (SLOT_SIZE + ROW_GAP), i,
					alien.abilities().get(i), state, energy, now, mastery, rgb);
		}
	}

	private static String formatTime(long ticks) {
		long s = Math.max(0, ticks / 20);
		return s >= 60 ? s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60 : s + "s";
	}

	/** Text auf Breite stauchen statt abschneiden (lange Namen wie „Humungousaur“). */
	private static void drawFitted(DrawContext context, TextRenderer font, Text text, int x, int y, int room) {
		int width = font.getWidth(text);
		if (width > room && room > 0) {
			context.getMatrices().push();
			context.getMatrices().translate(x, y, 0.0f);
			context.getMatrices().scale(room / (float) width, 1.0f, 1.0f);
			context.drawTextWithShadow(font, text, 0, 0, UiTheme.TEXT);
			context.getMatrices().pop();
		} else {
			context.drawTextWithShadow(font, text, x, y, UiTheme.TEXT);
		}
	}

	/** Master Control gibt alle Faehigkeiten frei, unabhaengig von der Meisterschaft. */
	private static boolean masterControl() {
		var player = net.minecraft.client.MinecraftClient.getInstance().player;
		return player != null && OmnitrixCore.state(player).masterControl();
	}

	private static void drawSlot(DrawContext context, TextRenderer font, int x, int y, int index, AbilitySlot slot,
			TransformationState state, float energy, long now, int mastery, int rgb) {
		int s = SLOT_SIZE;
		int iconX = x + (s - ICON_IN_SLOT) / 2;
		int iconY = y + (s - ICON_IN_SLOT) / 2 - 1;
		Text base = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.ABILITIES[index % PER_ROW]).getLocalizedText();
		Text key = index < PER_ROW ? base : Text.literal("⇧").append(base);
		if (!slot.unlocked(mastery) && !masterControl()) {
			// gesperrt: dunkles Feld, blasses Symbol, Schloss mit benoetigter Meisterschaft
			HudGfx.roundedGradient(context, x, y, s, s, 3, 0xF0161A1E, 0xF00A0C0E);
			HudGfx.roundedBorder(context, x, y, s, s, 0xFF30363C);
			UiDraw.glyph(context, Icons.alienAbilityGlyph(slot.type()), Icons.alienAbility(slot.type()), iconX, iconY, ICON_IN_SLOT,
					0x5A6068, 1.0f);
			String need = "★" + slot.unlockLevel();
			drawCentered(context, font, need, x + s / 2, y + (s - font.fontHeight) / 2 + 1, 0xFFB08A2E);
			chip(context, font, key, x + s / 2, y + s - 4, 0xFF30363C, 0xFF6E767E);
			return;
		}
		boolean affordable = energy >= com.santiq.kingdomomnitrix.progression.AlienMastery.energyCost(slot.energy(), mastery);
		long cooldown = state.cooldownRemaining(index, now);
		boolean cooling = cooldown > 0;
		int slotIndex = Math.min(index, READY_AT.length - 1);
		if (COOLING[slotIndex] && !cooling) {
			READY_AT[slotIndex] = now;
		}
		COOLING[slotIndex] = cooling;
		boolean ultimate = slot.role() == AbilitySlot.Role.ULTIMATE;
		int frame = ultimate ? GOLD : HudGfx.mix(rgb, 0xFFFFFF, 0.25f);

		// Untergrund: Alienfarbe oben, fast schwarz unten; abklingend grau, zu wenig Energie rot
		int top = cooling ? 0xF0262A30 : !affordable ? 0xF0461A1A : 0xF0000000 | HudGfx.mul(rgb, 0.45f);
		HudGfx.roundedGradient(context, x, y, s, s, 3, top, 0xF0080A0C);
		context.fill(x + 2, y + 1, x + s - 2, y + 2, 0x30FFFFFF);

		// bereit: weiche Leuchtkante, pulsiert leicht; Ultimate golden
		if (!cooling && affordable) {
			float pulse = 0.55f + 0.25f * MathHelper.sin(now * 0.15f + index);
			HudGfx.glow(context, x, y, s, s, frame, ultimate ? pulse + 0.2f : pulse * 0.6f, ultimate ? 3 : 2);
		}
		int glyphColor = cooling ? 0x8A9098 : !affordable ? 0xE08080 : HudGfx.mix(frame, 0xFFFFFF, 0.55f);
		UiDraw.glyph(context, Icons.alienAbilityGlyph(slot.type()), Icons.alienAbility(slot.type()), iconX, iconY, ICON_IN_SLOT,
				glyphColor, 1.0f);

		// Abklingzeit: dunkler Kreissektor, der im Uhrzeigersinn zurueckweicht, dazu die Sekunden
		if (cooling && slot.cooldown() > 0) {
			float left = (float) cooldown / slot.cooldown();
			context.enableScissor(x + 1, y + 1, x + s - 1, y + s - 1);
			HudGfx.arc(context, x + s / 2.0f, y + s / 2.0f, s * 0.75f, 0.0f, MathHelper.TAU * (1.0f - left), MathHelper.TAU * left, 0xC0000000);
			context.disableScissor();
			String secs = cooldown >= 20 ? String.valueOf((cooldown + 19) / 20) : String.format(java.util.Locale.ROOT, "%.1f", cooldown / 20.0f);
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 200);
			int tw = font.getWidth(secs);
			HudGfx.rounded(context, x + (s - tw) / 2 - 2, y + (s - font.fontHeight) / 2 - 2, tw + 4, font.fontHeight + 2, 2, 0xC0000000);
			drawCentered(context, font, secs, x + s / 2, y + (s - font.fontHeight) / 2, 0xFFFFFFFF);
			context.getMatrices().pop();
		} else if (!affordable) {
			context.fill(x + 1, y + 1, x + s - 1, y + s - 1, 0x40FF2020);
		}

		// Rand: Alienfarbe (bereit), grau (abklingend), rot (zu wenig Energie), gold (Ultimate)
		int border = cooling ? 0xFF4A5058 : !affordable ? 0xFFC03030 : 0xFF000000 | frame;
		HudGfx.roundedBorder(context, x, y, s, s, border);
		if (ultimate) {
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 200);
			context.drawTextWithShadow(font, "✦", x + s - 7, y + 1, 0xFF000000 | GOLD);
			context.getMatrices().pop();
		}

		// Bereit-Blitz: kurzer heller Rand, wenn die Abklingzeit gerade endete
		long since = now - READY_AT[slotIndex];
		if (!cooling && since >= 0 && since < FLASH_TICKS) {
			float f = 1.0f - since / (float) FLASH_TICKS;
			context.fill(x + 1, y + 1, x + s - 1, y + s - 1, HudGfx.withAlpha(0xFFFFFF, (int) (f * 110)));
			HudGfx.glow(context, x, y, s, s, 0xFFFFFF, f, 4);
		}
		chip(context, font, key, x + s / 2, y + s - 4, cooling ? 0xFF4A5058 : 0xFF000000 | frame, 0xFFFFFFFF);
	}

	/** Tasten-Chip: kleines abgerundetes Schild, mittig am unteren Rand des Feldes. */
	private static void chip(DrawContext context, TextRenderer font, Text key, int centerX, int y, int border, int textColor) {
		int w = Math.max(11, font.getWidth(key) + 6);
		int x = centerX - w / 2;
		int h = 10;
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 210);
		HudGfx.rounded(context, x, y, w, h, 2, 0xF00C1014);
		HudGfx.roundedBorder(context, x, y, w, h, border);
		context.drawTextWithShadow(font, key, centerX - font.getWidth(key) / 2, y + 1, textColor);
		context.getMatrices().pop();
	}

	private static void drawCentered(DrawContext context, TextRenderer font, String text, int centerX, int y, int color) {
		context.drawTextWithShadow(font, text, centerX - font.getWidth(text) / 2, y, color);
	}

	private static void renderIdle(DrawContext context, TextRenderer font, ClientPlayerEntity player, TransformationState state,
			Optional<Identifier> selectedId, Optional<AlienDefinition> alien, long now) {
		Text selected = selectedId.isPresent() && alien.isPresent()
				? TransformationManager.alienName(selectedId.get()).withColor(alien.get().color())
				: Text.translatable("hud.kingdomomnitrix.omnitrix").formatted(Formatting.GREEN);
		context.drawTextWithShadow(font, selected, PADDING + 2, PADDING, UiTheme.TEXT);
		// gewaehltes Alien als Silhouette rechts, eingefaerbt nach Geraete-Zustand (wie das Zifferblatt)
		selectedId.ifPresent(id -> {
			OmnitrixStatus shown = OmnitrixClientState.status(player);
			int tint = com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors.status(player, shown);
			int size = font.fontHeight * 2 + 2;
			UiDraw.alienSilhouette(context, id, WIDTH - PADDING - size, PADDING - 1, size, tint, 0.85f);
		});

		long recharge = state.rechargeRemaining(now);
		OmnitrixStatus device = OmnitrixClientState.status(player);
		var core = OmnitrixCore.state(player);
		Text status = device == OmnitrixStatus.LOCKED
				? Text.translatable("hud.kingdomomnitrix.omnitrix_locked", (core.lockedUntil() - now + 19) / 20).formatted(Formatting.GRAY)
				: device == OmnitrixStatus.OVERHEATED
				? Text.translatable("hud.kingdomomnitrix.omnitrix_overheated", (core.overheatedUntil() - now + 19) / 20).formatted(Formatting.RED)
				: recharge > 0
				? Text.translatable("hud.kingdomomnitrix.recharging", (recharge + 19) / 20).formatted(Formatting.RED)
				: Text.translatable("hud.kingdomomnitrix.ready", KeyBindingHelper.getBoundKeyOf(ModKeyBindings.OPEN_OMNITRIX).getLocalizedText())
						.formatted(Formatting.GREEN);
		context.drawTextWithShadow(font, status, PADDING + 2, PADDING + font.fontHeight + 2, UiTheme.TEXT);
	}
}
