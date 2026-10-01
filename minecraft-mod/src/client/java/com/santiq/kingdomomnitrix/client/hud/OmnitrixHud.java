package com.santiq.kingdomomnitrix.client.hud;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AbilitySlot;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
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
	private static final int WIDTH = 114;
	private static final int BAR_HEIGHT = 4;
	private static final int SLOT_SIZE = 22;
	private static final int SLOT_GAP = 4;
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
		return HudAnchor.BOTTOM_RIGHT;
	}

	@Override
	public int defaultX() {
		return -4;
	}

	@Override
	public int defaultY() {
		return -4;
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

	@Override
	public int height(MinecraftClient client) {
		int font = client.textRenderer.fontHeight;
		return transformed(client)
				? PADDING + font + 3 + BAR_HEIGHT + 2 + BAR_HEIGHT + 4 + SLOT_SIZE + PADDING
				: PADDING + font * 2 + 2 + PADDING;
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
			renderIdle(context, client.textRenderer, state, shownId, alien, now);
		}
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
		context.drawTextWithShadow(font, name, cx, cy, UiTheme.TEXT);
		String seconds = remaining / 20 + "s";
		context.drawTextWithShadow(font, seconds, WIDTH - PADDING - font.getWidth(seconds), cy,
				remaining < WARNING_TICKS ? TIMER_WARNING_COLOR : 0xFFDDDDDD);
		cy += font.fontHeight + 3;

		UiDraw.bar(context, cx, cy, innerWidth, BAR_HEIGHT, (float) remaining / state.totalTicks(),
				remaining < WARNING_TICKS ? TIMER_WARNING_COLOR : UiTheme.OMNITRIX.accent());
		cy += BAR_HEIGHT + 2;
		float energy = state.currentEnergy(alien, now);
		UiDraw.bar(context, cx, cy, innerWidth, BAR_HEIGHT, energy / alien.maxEnergy(), ENERGY_COLOR);
		cy += BAR_HEIGHT + 4;

		for (int i = 0; i < alien.abilities().size(); i++) {
			drawSlot(context, font, cx + i * (SLOT_SIZE + SLOT_GAP), cy, i, alien.abilities().get(i), state, energy, now);
		}
	}

	private static void drawSlot(DrawContext context, TextRenderer font, int x, int y, int index, AbilitySlot slot,
			TransformationState state, float energy, long now) {
		boolean affordable = energy >= slot.energy();
		long cooldown = state.cooldownRemaining(index, now);
		context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, affordable ? 0xFF1E2A1E : 0xFF4A1E1E);
		context.drawBorder(x, y, SLOT_SIZE, SLOT_SIZE, cooldown > 0 ? 0xFF555555 : UiTheme.OMNITRIX.border());
		UiDraw.icon(context, Icons.alienAbility(slot.type()), Icons.command("omnitrix"), x + 3, y + 1, UiDraw.ICON_SIZE);
		if (cooldown > 0 && slot.cooldown() > 0) {
			int covered = (int) Math.ceil(SLOT_SIZE * (double) cooldown / slot.cooldown());
			context.fill(x, y + SLOT_SIZE - covered, x + SLOT_SIZE, y + SLOT_SIZE, 0xA0000000);
		}
		Text key = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.ABILITIES[index]).getLocalizedText();
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 200);
		context.drawTextWithShadow(font, key, x + SLOT_SIZE - font.getWidth(key) - 1, y + SLOT_SIZE - font.fontHeight + 1, 0xFFFFD84A);
		context.getMatrices().pop();
	}

	private static void renderIdle(DrawContext context, TextRenderer font, TransformationState state,
			Optional<Identifier> selectedId, Optional<AlienDefinition> alien, long now) {
		Text selected = selectedId.isPresent() && alien.isPresent()
				? TransformationManager.alienName(selectedId.get()).withColor(alien.get().color())
				: Text.translatable("hud.kingdomomnitrix.omnitrix").formatted(Formatting.GREEN);
		context.drawTextWithShadow(font, selected, PADDING + 2, PADDING, UiTheme.TEXT);

		long recharge = state.rechargeRemaining(now);
		Text status = recharge > 0
				? Text.translatable("hud.kingdomomnitrix.recharging", (recharge + 19) / 20).formatted(Formatting.RED)
				: Text.translatable("hud.kingdomomnitrix.ready", KeyBindingHelper.getBoundKeyOf(ModKeyBindings.OPEN_OMNITRIX).getLocalizedText())
						.formatted(Formatting.GREEN);
		context.drawTextWithShadow(font, status, PADDING + 2, PADDING + font.fontHeight + 2, UiTheme.TEXT);
	}
}
