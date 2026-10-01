package com.santiq.kingdomomnitrix.client.hud;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AbilitySlot;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import java.util.Optional;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Omnitrix-HUD unten rechts: aktuelles Alien, Restzeit, Energie und die drei Faehigkeiten-Slots
 * mit Taste und Abklingzeit. Ohne Verwandlung: gewaehltes Alien und Nachladezeit.
 */
public final class OmnitrixHud {
	public static final Identifier LAYER_ID = KingdomOmnitrix.id("omnitrix");

	private static final int MARGIN = 4;
	private static final int PADDING = 4;
	private static final int WIDTH = 110;
	private static final int BAR_HEIGHT = 4;
	private static final int SLOT_SIZE = 22;
	private static final int SLOT_GAP = 4;
	private static final int PANEL_COLOR = 0xA0101420;
	private static final int BAR_BACKGROUND = 0xFF2A2F3A;
	private static final int TIMER_COLOR = 0xFF39FF14;
	private static final int TIMER_WARNING_COLOR = 0xFFFF5555;
	private static final int ENERGY_COLOR = 0xFF4FC3FF;
	private static final int WARNING_TICKS = 200;

	private OmnitrixHud() {
	}

	public static void register() {
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.HOTBAR_AND_BARS, IdentifiedLayer.of(LAYER_ID, OmnitrixHud::render)));
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.options.hudHidden || client.inGameHud.getDebugHud().shouldShowDebugHud()) {
			return;
		}
		TransformationState state = TransformationManager.get(player);
		if (!state.isTransformed() && !OmnitrixItem.hasOmnitrix(player)) {
			return;
		}
		long now = client.world.getTime();
		TextRenderer font = client.textRenderer;
		Optional<Identifier> shownId = state.activeAlien().or(state::selectedAlien);
		Optional<AlienDefinition> alien = shownId.flatMap(id -> AlienRegistry.get(client.world.getRegistryManager(), id));

		if (state.isTransformed() && alien.isPresent()) {
			renderTransformed(context, font, state, shownId.get(), alien.get(), now);
		} else {
			renderIdle(context, font, state, shownId, alien, now);
		}
	}

	private static void renderTransformed(DrawContext context, TextRenderer font, TransformationState state,
			Identifier alienId, AlienDefinition alien, long now) {
		int height = PADDING + font.fontHeight + 3 + BAR_HEIGHT + 2 + BAR_HEIGHT + 4 + SLOT_SIZE + PADDING;
		int x = context.getScaledWindowWidth() - WIDTH - MARGIN;
		int y = context.getScaledWindowHeight() - height - MARGIN;
		context.fill(x, y, x + WIDTH, y + height, PANEL_COLOR);
		context.fill(x, y, x + 2, y + height, 0xFF000000 | alien.color());

		int cx = x + PADDING + 2;
		int innerWidth = WIDTH - PADDING * 2 - 2;
		int cy = y + PADDING;
		long remaining = state.remainingTicks(now);
		Text name = TransformationManager.alienName(alienId).withColor(alien.color()).formatted(Formatting.BOLD);
		context.drawTextWithShadow(font, name, cx, cy, 0xFFFFFFFF);
		String seconds = remaining / 20 + "s";
		context.drawTextWithShadow(font, seconds, x + WIDTH - PADDING - font.getWidth(seconds), cy,
				remaining < WARNING_TICKS ? TIMER_WARNING_COLOR : 0xFFDDDDDD);
		cy += font.fontHeight + 3;

		float timeFraction = (float) remaining / Math.max(1, alien.durationTicks());
		bar(context, cx, cy, innerWidth, timeFraction, remaining < WARNING_TICKS ? TIMER_WARNING_COLOR : TIMER_COLOR);
		cy += BAR_HEIGHT + 2;
		float energy = state.currentEnergy(alien, now);
		bar(context, cx, cy, innerWidth, energy / alien.maxEnergy(), ENERGY_COLOR);
		cy += BAR_HEIGHT + 4;

		for (int i = 0; i < alien.abilities().size(); i++) {
			AbilitySlot slot = alien.abilities().get(i);
			int sx = cx + i * (SLOT_SIZE + SLOT_GAP);
			drawSlot(context, font, sx, cy, i, slot, state, energy, now);
		}
	}

	private static void drawSlot(DrawContext context, TextRenderer font, int x, int y, int index, AbilitySlot slot,
			TransformationState state, float energy, long now) {
		boolean affordable = energy >= slot.energy();
		long cooldown = state.cooldownRemaining(index, now);
		context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, affordable ? 0xFF2E3440 : 0xFF4A1E1E);
		context.drawBorder(x, y, SLOT_SIZE, SLOT_SIZE, cooldown > 0 ? 0xFF555555 : 0xFFBBBBBB);
		String abbreviation = Text.translatable(slot.translationKey()).getString();
		abbreviation = abbreviation.length() > 2 ? abbreviation.substring(0, 2) : abbreviation;
		context.drawCenteredTextWithShadow(font, abbreviation, x + SLOT_SIZE / 2, y + 4, 0xFFFFFFFF);
		Text key = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.ABILITIES[index]).getLocalizedText();
		context.drawCenteredTextWithShadow(font, key, x + SLOT_SIZE / 2, y + SLOT_SIZE - font.fontHeight, 0xFFFFD84A);
		if (cooldown > 0 && slot.cooldown() > 0) {
			int covered = (int) Math.ceil(SLOT_SIZE * (double) cooldown / slot.cooldown());
			context.fill(x, y + SLOT_SIZE - covered, x + SLOT_SIZE, y + SLOT_SIZE, 0xA0000000);
		}
	}

	private static void renderIdle(DrawContext context, TextRenderer font, TransformationState state,
			Optional<Identifier> selectedId, Optional<AlienDefinition> alien, long now) {
		int height = PADDING + font.fontHeight * 2 + 2 + PADDING;
		int x = context.getScaledWindowWidth() - WIDTH - MARGIN;
		int y = context.getScaledWindowHeight() - height - MARGIN;
		context.fill(x, y, x + WIDTH, y + height, PANEL_COLOR);
		context.fill(x, y, x + 2, y + height, TIMER_COLOR);

		Text selected = selectedId.isPresent() && alien.isPresent()
				? TransformationManager.alienName(selectedId.get()).withColor(alien.get().color())
				: Text.translatable("hud.kingdomomnitrix.omnitrix").formatted(Formatting.GREEN);
		context.drawTextWithShadow(font, selected, x + PADDING + 2, y + PADDING, 0xFFFFFFFF);

		long recharge = state.rechargeRemaining(now);
		Text status = recharge > 0
				? Text.translatable("hud.kingdomomnitrix.recharging", (recharge + 19) / 20).formatted(Formatting.RED)
				: Text.translatable("hud.kingdomomnitrix.ready", KeyBindingHelper.getBoundKeyOf(ModKeyBindings.OPEN_OMNITRIX).getLocalizedText())
						.formatted(Formatting.GREEN);
		context.drawTextWithShadow(font, status, x + PADDING + 2, y + PADDING + font.fontHeight + 2, 0xFFFFFFFF);
	}

	private static void bar(DrawContext context, int x, int y, int width, float fraction, int color) {
		context.fill(x, y, x + width, y + BAR_HEIGHT, BAR_BACKGROUND);
		int filled = Math.round(width * Math.max(0.0f, Math.min(1.0f, fraction)));
		if (filled > 0) {
			context.fill(x, y, x + filled, y + BAR_HEIGHT, color);
		}
	}
}
