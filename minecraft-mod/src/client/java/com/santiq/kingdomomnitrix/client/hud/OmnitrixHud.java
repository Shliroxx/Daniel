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

/**
 * Omnitrix-Anzeige (Standard unten rechts): aktuelles Alien mit Meisterschaft, Restzeit, Energie und die
 * Faehigkeiten mit Symbol, Taste und Abklingzeit. Ohne Verwandlung: gewaehltes Alien und Nachladezeit.
 */
public final class OmnitrixHud implements HudElement {
	public static final OmnitrixHud INSTANCE = new OmnitrixHud();
	private static final Identifier ID = KingdomOmnitrix.id("omnitrix");

	private static final int PADDING = 4;
	/** Abstand von oben: unter den zwei Reihen Effekt-Symbolen, damit die Ego-Hand unten rechts frei bleibt */
	public static final int TOP_OFFSET = 54;
	private static final int WIDTH = 114;
	private static final int BAR_HEIGHT = 4;
	private static final int SLOT_SIZE = 22;
	private static final int ICON = 11;
	private static final int SLOT_GAP = 4;
	private static final int PER_ROW = 3;
	private static final int TIMER_WARNING_COLOR = 0xFFFF5555;
	private static final int ENERGY_COLOR = 0xFF4FC3FF;
	private static final int WARNING_TICKS = 200;

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
				? PADDING + font + 3 + BAR_HEIGHT + 2 + BAR_HEIGHT + 4 + rows * SLOT_SIZE + (rows - 1) * SLOT_GAP + PADDING
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
		UiDraw.hudPanel(context, 0, 0, WIDTH, height(client), UiTheme.OMNITRIX);
		if (state.isTransformed() && alien.isPresent()) {
			renderTransformed(context, client, state, shownId.get(), alien.get(), now);
		} else {
			renderIdle(context, client.textRenderer, player, state, shownId, alien, now);
		}
		renderHeat(context, player, height(client));
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
			color = 0xFF39FF14;
		}
		UiDraw.bar(context, x, y, width, HEAT_HEIGHT, status == OmnitrixStatus.OVERHEATED ? 1.0f : heat, color);
		// Markierung der Warnschwelle
		int mark = x + Math.round(width * warning);
		context.fill(mark, y - 1, mark + 1, y + HEAT_HEIGHT + 1, 0xC0FFFFFF);
	}

	private static void renderTransformed(DrawContext context, MinecraftClient client, TransformationState state,
			Identifier alienId, AlienDefinition alien, long now) {
		TextRenderer font = client.textRenderer;
		int cx = PADDING + 2;
		int innerWidth = WIDTH - cx - PADDING;
		int cy = PADDING;
		long remaining = state.remainingTicks(now);
		int mastery = client.player != null ? AlienMasteryManager.get(client.player).level(alienId) : 1;
		Text name = TransformationManager.alienName(alienId).withColor(alien.color()).formatted(Formatting.BOLD)
				.append(Text.literal(" ★" + mastery).formatted(Formatting.GOLD));
		// kleines Symbol vor dem Namen
		int nameX = UiDraw.alienIcon(context, alienId, cx - 1, cy - 2, ICON, 1.0f) ? cx + ICON + 1 : cx;
		String seconds = remaining / 20 + "s";
		int secondsX = WIDTH - PADDING - font.getWidth(seconds);
		// lange Namen (Four Arms ★10) schmaler zeichnen statt in die Restzeit zu laufen
		int room = secondsX - 3 - nameX;
		int nameWidth = font.getWidth(name);
		if (nameWidth > room && room > 0) {
			context.getMatrices().push();
			context.getMatrices().translate(nameX, cy, 0.0f);
			context.getMatrices().scale(room / (float) nameWidth, 1.0f, 1.0f);
			context.drawTextWithShadow(font, name, 0, 0, UiTheme.TEXT);
			context.getMatrices().pop();
		} else {
			context.drawTextWithShadow(font, name, nameX, cy, UiTheme.TEXT);
		}
		context.drawTextWithShadow(font, seconds, secondsX, cy,
				remaining < WARNING_TICKS ? TIMER_WARNING_COLOR : 0xFFDDDDDD);
		cy += font.fontHeight + 3;

		UiDraw.bar(context, cx, cy, innerWidth, BAR_HEIGHT, (float) remaining / state.totalTicks(),
				remaining < WARNING_TICKS ? TIMER_WARNING_COLOR : UiTheme.OMNITRIX.accent());
		cy += BAR_HEIGHT + 2;
		float energy = state.currentEnergy(alien, now);
		UiDraw.bar(context, cx, cy, innerWidth, BAR_HEIGHT, energy / alien.maxEnergy(), ENERGY_COLOR);
		cy += BAR_HEIGHT + 4;


		for (int i = 0; i < alien.abilities().size(); i++) {
			int col = i % PER_ROW;
			int row = i / PER_ROW;
			drawSlot(context, font, cx + col * (SLOT_SIZE + SLOT_GAP), cy + row * (SLOT_SIZE + SLOT_GAP), i,
					alien.abilities().get(i), state, energy, now, mastery);
		}
	}

	/** Master Control gibt alle Faehigkeiten frei, unabhaengig von der Meisterschaft. */
	private static boolean masterControl() {
		var player = net.minecraft.client.MinecraftClient.getInstance().player;
		return player != null && OmnitrixCore.state(player).masterControl();
	}

	private static void drawSlot(DrawContext context, TextRenderer font, int x, int y, int index, AbilitySlot slot,
			TransformationState state, float energy, long now, int mastery) {
		if (!slot.unlocked(mastery) && !masterControl()) {
			// gesperrt: dunkles Feld, blasses Symbol, benoetigte Meisterschaftsstufe
			context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF151815);
			context.drawBorder(x, y, SLOT_SIZE, SLOT_SIZE, 0xFF3A3A3A);
			UiDraw.icon(context, Icons.alienAbility(slot.type()), Icons.command("omnitrix"), x + 3, y + 1, UiDraw.ICON_SIZE);
			context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xB0101010);
			String need = "★" + slot.unlockLevel();
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 200);
			context.drawTextWithShadow(font, need, x + (SLOT_SIZE - font.getWidth(need)) / 2, y + (SLOT_SIZE - font.fontHeight) / 2 + 1, 0xFFB08A2E);
			context.getMatrices().pop();
			return;
		}
		boolean affordable = energy >= com.santiq.kingdomomnitrix.progression.AlienMastery.energyCost(slot.energy(), mastery);
		long cooldown = state.cooldownRemaining(index, now);
		context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, affordable ? 0xFF1E2A1E : 0xFF4A1E1E);
		context.drawBorder(x, y, SLOT_SIZE, SLOT_SIZE, cooldown > 0 ? 0xFF555555 : UiTheme.OMNITRIX.border());
		UiDraw.icon(context, Icons.alienAbility(slot.type()), Icons.command("omnitrix"), x + 3, y + 1, UiDraw.ICON_SIZE);
		if (cooldown > 0 && slot.cooldown() > 0) {
			int covered = (int) Math.ceil(SLOT_SIZE * (double) cooldown / slot.cooldown());
			context.fill(x, y + SLOT_SIZE - covered, x + SLOT_SIZE, y + SLOT_SIZE, 0xA0000000);
		}
		Text base = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.ABILITIES[index % PER_ROW]).getLocalizedText();
		Text key = index < PER_ROW ? base : Text.literal("⇧").append(base);
		if (slot.role() == AbilitySlot.Role.ULTIMATE) {
			// Ultimate: goldener Rahmen
			context.drawBorder(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, 0xFFFFC94A);
		}
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 200);
		context.drawTextWithShadow(font, key, x + SLOT_SIZE - font.getWidth(key) - 1, y + SLOT_SIZE - font.fontHeight + 1, 0xFFFFD84A);
		context.getMatrices().pop();
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
