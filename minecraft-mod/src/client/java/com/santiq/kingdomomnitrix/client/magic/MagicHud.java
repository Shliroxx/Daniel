package com.santiq.kingdomomnitrix.client.magic;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.hud.HeroStatusHud;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.keyblade.KeybladeItem;
import com.santiq.kingdomomnitrix.magic.MagicManager;
import com.santiq.kingdomomnitrix.magic.MagicState;
import com.santiq.kingdomomnitrix.magic.SpellDefinition;
import com.santiq.kingdomomnitrix.magic.SpellRegistry;
import java.util.List;
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
import net.minecraft.util.Identifier;

/**
 * Zauberleiste oben links unter dem Status-Panel (nur mit Keyblade in der Hand): MP-Leiste bzw. MP-Ladezeit, alle Zauber mit Stufe,
 * MP-Kosten und Abklingzeit; der aktive Zauber ist hervorgehoben.
 */
public final class MagicHud {
	public static final Identifier LAYER_ID = KingdomOmnitrix.id("magic");

	private static final int MARGIN = 4;
	private static final int WIDTH = 120;
	private static final int PADDING = 4;
	private static final int LINE = 11;
	private static final int BAR_HEIGHT = 4;
	private static final int MP_COLOR = 0xFF3D7BFF;
	private static final int CHARGE_COLOR = 0xFFE040FB;
	private static final int FLASH_TICKS = 30;

	private static Identifier flashed;
	private static long flashUntil;

	private MagicHud() {
	}

	public static void register() {
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.HOTBAR_AND_BARS, IdentifiedLayer.of(LAYER_ID, MagicHud::render)));
	}

	static void flashSelection(Identifier spell) {
		MinecraftClient client = MinecraftClient.getInstance();
		flashed = spell;
		flashUntil = client.world != null ? client.world.getTime() + FLASH_TICKS : 0;
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.options.hudHidden || client.inGameHud.getDebugHud().shouldShowDebugHud()) {
			return;
		}
		if (!(player.getMainHandStack().getItem() instanceof KeybladeItem)) {
			return;
		}
		var manager = client.world.getRegistryManager();
		List<Identifier> spells = SpellRegistry.sortedIds(manager);
		TextRenderer font = client.textRenderer;
		long now = client.world.getTime();
		MagicState state = MagicManager.get(player);
		Optional<Identifier> selected = MagicManager.selectedSpell(player);
		if (flashed != null && now < flashUntil) {
			selected = Optional.of(flashed);
		}

		int height = PADDING + BAR_HEIGHT + 3 + spells.size() * LINE + font.fontHeight + PADDING;
		int x = MARGIN;
		// Unter dem Status-Panel oben links (unten links liegt der Chat)
		int y = HeroStatusHud.bottom(font) + 4;
		context.fill(x, y, x + WIDTH, y + height, 0xA0101420);

		int cy = y + PADDING;
		int barWidth = WIDTH - PADDING * 2;
		context.fill(x + PADDING, cy, x + PADDING + barWidth, cy + BAR_HEIGHT, 0xFF2A2F3A);
		boolean charging = state.isCharging(now);
		float fraction = charging
				? state.chargeProgress(now, MagicManager.chargeTicks(player))
				: state.currentMp(now, MagicManager.regenPerSecond(player)) / MagicState.MAX_MP;
		int filled = Math.round(barWidth * Math.max(0.0f, Math.min(1.0f, fraction)));
		if (filled > 0) {
			context.fill(x + PADDING, cy, x + PADDING + filled, cy + BAR_HEIGHT, charging ? CHARGE_COLOR : MP_COLOR);
		}
		cy += BAR_HEIGHT + 3;

		for (Identifier spellId : spells) {
			Optional<SpellDefinition> spell = SpellRegistry.get(manager, spellId);
			if (spell.isEmpty()) {
				continue;
			}
			int level = Math.min(state.level(spellId), spell.get().maxLevel());
			boolean isSelected = selected.map(spellId::equals).orElse(false);
			int color = 0xFF000000 | spell.get().color();
			if (isSelected) {
				context.fill(x + 2, cy - 1, x + WIDTH - 2, cy + LINE - 1, 0x60FFFFFF);
			}
			context.drawTextWithShadow(font, Text.translatable(SpellDefinition.translationKey(spellId, level)), x + PADDING + 2, cy, color);
			long cooldown = state.cooldownRemaining(spellId, now);
			String right = cooldown > 0 ? String.format("%.1fs", cooldown / 20.0f)
					: (int) spell.get().level(level).mpCost() + " MP";
			context.drawTextWithShadow(font, right, x + WIDTH - PADDING - font.getWidth(right), cy, cooldown > 0 ? 0xFF888888 : 0xFFBBDDFF);
			cy += LINE;
		}
		Text hint = Text.translatable(charging ? "hud.kingdomomnitrix.mp_charge" : "hud.kingdomomnitrix.magic_hint",
				KeyBindingHelper.getBoundKeyOf(ModKeyBindings.MAGIC).getLocalizedText());
		context.drawTextWithShadow(font, hint, x + PADDING, cy + 1, charging ? CHARGE_COLOR : 0xFF888888);
	}
}
