package com.santiq.kingdomomnitrix.client.hero;

import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.client.menu.MenuTab;
import com.santiq.kingdomomnitrix.client.menu.MenuTabs;
import com.santiq.kingdomomnitrix.client.ui.Icons;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.networking.ToggleHeroAbilityPayload;
import com.santiq.kingdomomnitrix.player.HeroData;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.progression.AlienMastery;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import com.santiq.kingdomomnitrix.progression.HeroAbilities;
import com.santiq.kingdomomnitrix.progression.HeroAbilityDefinition;
import com.santiq.kingdomomnitrix.progression.HeroAbilityRegistry;
import com.santiq.kingdomomnitrix.progression.ProgressionManager;
import com.santiq.kingdomomnitrix.progression.ProgressionStats;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Heldenmenue (Taste K) im Stil der Kingdom-Hearts-Faehigkeitenliste: oben Stufe, AP und die Werte, die mit
 * der Stufe wachsen; darunter alle Faehigkeiten (Klick = an/ab). Die Daten kommen aus synchronisierten
 * Attachments und der Faehigkeiten-Registry; der Server prueft jede Aenderung.
 */
public class HeroScreen extends Screen {
	private static final int ROW_BACK = 0x80202838;
	private static final int ROW_HOVER = 0xA0303C58;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_SOFT = 0xFFB0B8C4;
	private static final int TEXT_LOCKED = 0xFF5E6672;
	private static final int AP_FILL = 0xFF4AB8FF;
	private static final int AP_BACK = 0xFF26303C;
	private static final int GOLD = 0xFFFFC94A;
	private static final int ROW = 18;
	private static final int HEADER = 50;
	private static final int FOOTER = 24;

	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int scroll;

	public HeroScreen() {
		super(Text.translatable("screen.kingdomomnitrix.hero"));
	}

	public static void open(MinecraftClient client) {
		client.setScreen(new HeroScreen());
	}

	@Override
	protected void init() {
		panelWidth = Math.min(width - MenuTabs.RESERVED - 8, 320);
		panelHeight = Math.min(height - 12, 232);
		left = MenuTabs.RESERVED + (width - MenuTabs.RESERVED - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		MenuTabs.addTo(this, MenuTab.HERO, this::addDrawableChild);
	}

	private DynamicRegistryManager registries() {
		return client != null && client.world != null ? client.world.getRegistryManager() : null;
	}

	private List<Identifier> abilities() {
		DynamicRegistryManager registries = registries();
		return registries == null ? List.of() : HeroAbilityRegistry.sortedIds(registries);
	}

	private int listTop() {
		return top + HEADER;
	}

	private int visibleRows() {
		return Math.max(1, (panelHeight - HEADER - FOOTER) / ROW);
	}

	private int maxScroll() {
		return Math.max(0, abilities().size() - visibleRows());
	}

	/** Zeile unter der Maus, oder -1. */
	private int rowAt(double mouseX, double mouseY) {
		if (mouseX < left + 6 || mouseX > left + panelWidth - 6 || mouseY < listTop()) {
			return -1;
		}
		int row = (int) ((mouseY - listTop()) / ROW);
		if (row >= visibleRows()) {
			return -1;
		}
		int index = row + scroll;
		return index < abilities().size() ? index : -1;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		ClientPlayerEntity player = client != null ? client.player : null;
		DynamicRegistryManager registries = registries();
		if (player == null || registries == null) {
			return;
		}
		UiDraw.panel(context, left, top, panelWidth, panelHeight, UiTheme.HERO);

		HeroData data = HeroDataAccess.get(player);
		int level = data.level();
		int used = ProgressionManager.usedAp(player);
		int total = ProgressionManager.totalAp(player);

		// Kopf: Stufe, EP, AP-Leiste
		Text title = Text.translatable("screen.kingdomomnitrix.hero.level", level).formatted(Formatting.BOLD);
		context.drawTextWithShadow(textRenderer, title, left + 8, top + 7, GOLD);
		String xp = data.isMaxLevel() ? "MAX" : data.experience() + " / " + HeroData.experienceToNext(level) + " EP";
		context.drawTextWithShadow(textRenderer, xp, left + 14 + textRenderer.getWidth(title), top + 7, TEXT_SOFT);
		Text ap = Text.translatable("screen.kingdomomnitrix.hero.ap", used, total);
		int barWidth = 70;
		int barX = left + panelWidth - 8 - barWidth;
		context.drawTextWithShadow(textRenderer, ap, barX - 6 - textRenderer.getWidth(ap), top + 7, AP_FILL);
		context.fill(barX, top + 8, barX + barWidth, top + 14, AP_BACK);
		context.fill(barX, top + 8, barX + Math.round(barWidth * Math.min(1.0f, (float) used / Math.max(1, total))), top + 14, AP_FILL);

		// Werte, die mit der Stufe wachsen
		Text stats = Text.translatable("screen.kingdomomnitrix.hero.stats",
				ProgressionStats.bonusHealth(level) / 2,
				String.format(Locale.ROOT, "%.1f", ProgressionStats.bonusAttack(level)),
				Math.round(ProgressionStats.maxMp(level)),
				Math.round(ProgressionStats.omnitrixDurationBonus(level) * 100));
		context.drawTextWithShadow(textRenderer, stats, left + 8, top + 21, TEXT_SOFT);

		// Alien-Meisterschaft in einer Zeile
		AlienMastery mastery = AlienMasteryManager.get(player);
		MutableText masteryLine = Text.translatable("screen.kingdomomnitrix.hero.mastery").append(": ").formatted(Formatting.GREEN);
		if (mastery.experience().isEmpty()) {
			masteryLine.append(Text.translatable("screen.kingdomomnitrix.hero.mastery_none").formatted(Formatting.GRAY));
		} else {
			boolean first = true;
			for (Identifier alien : mastery.experience().keySet().stream().sorted().toList()) {
				if (!first) {
					masteryLine.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
				}
				first = false;
				masteryLine.append(Text.translatable("screen.kingdomomnitrix.hero.mastery_entry", TransformationManager.alienName(alien),
						"★" + mastery.level(alien)).formatted(Formatting.WHITE));
			}
		}
		context.drawTextWithShadow(textRenderer, trim(masteryLine, panelWidth - 16), left + 8, top + 33, TEXT);

		// Faehigkeitenliste
		List<Identifier> ids = abilities();
		HeroAbilities equipped = ProgressionManager.get(player);
		int hovered = rowAt(mouseX, mouseY);
		scroll = Math.min(scroll, maxScroll());
		for (int row = 0; row < visibleRows() && row + scroll < ids.size(); row++) {
			Identifier id = ids.get(row + scroll);
			Optional<HeroAbilityDefinition> found = HeroAbilityRegistry.get(registries, id);
			if (found.isEmpty()) {
				continue;
			}
			HeroAbilityDefinition ability = found.get();
			int y = listTop() + row * ROW;
			boolean unlocked = ability.unlockLevel() <= level;
			boolean on = equipped.isEquipped(id);
			context.fill(left + 6, y, left + panelWidth - 6, y + ROW - 1, row + scroll == hovered ? ROW_HOVER : ROW_BACK);
			context.fill(left + 6, y, left + 8, y + ROW - 1, unlocked ? ability.category().color() : TEXT_LOCKED);
			// Kaestchen wie im KH-Menue, daneben das Symbol (gesperrt abgedunkelt)
			int box = left + 12;
			context.drawBorder(box, y + 4, 8, 8, unlocked ? 0xFFB0B8C4 : TEXT_LOCKED);
			if (on) {
				context.fill(box + 2, y + 6, box + 6, y + 10, ability.category().color());
			}
			UiDraw.icon(context, Icons.heroAbility(id), Icons.FALLBACK, left + 24, y, UiDraw.ICON_SIZE);
			if (!unlocked) {
				context.fill(left + 24, y, left + 40, y + 16, 0xA0101420);
			}
			context.drawTextWithShadow(textRenderer, HeroAbilityDefinition.name(id), left + 44, y + 5, unlocked ? (on ? TEXT : TEXT_SOFT) : TEXT_LOCKED);
			Text right = unlocked
					? Text.literal(ability.apCost() + " AP")
					: Text.translatable("screen.kingdomomnitrix.hero.locked", ability.unlockLevel());
			int rightX = left + panelWidth - 10 - textRenderer.getWidth(right);
			context.drawTextWithShadow(textRenderer, right, rightX, y + 5, unlocked ? (on ? AP_FILL : TEXT_SOFT) : TEXT_LOCKED);
			// Kategorie in fester Spalte vor der rechten Angabe; laengere Angaben (gesperrt) schieben sie nach links
			Text category = Text.translatable(ability.category().translationKey());
			int categoryX = Math.min(left + panelWidth - 10 - 52, rightX - 8) - textRenderer.getWidth(category);
			context.drawTextWithShadow(textRenderer, category, categoryX, y + 5, unlocked ? ability.category().color() & 0x9FFFFFFF : TEXT_LOCKED);
		}
		if (maxScroll() > 0) {
			int trackTop = listTop();
			int trackHeight = visibleRows() * ROW;
			int thumb = Math.max(10, trackHeight * visibleRows() / ids.size());
			int thumbY = trackTop + (trackHeight - thumb) * scroll / maxScroll();
			context.fill(left + panelWidth - 4, trackTop, left + panelWidth - 2, trackTop + trackHeight, 0x40FFFFFF);
			context.fill(left + panelWidth - 4, thumbY, left + panelWidth - 2, thumbY + thumb, 0xC0FFFFFF);
		}

		// Fusszeile: Beschreibung der Zeile unter der Maus, sonst Hinweis
		int footerY = top + panelHeight - FOOTER + 4;
		Text footer = hovered >= 0 ? HeroAbilityDefinition.description(ids.get(hovered))
				: Text.translatable("screen.kingdomomnitrix.hero.hint").formatted(Formatting.GRAY);
		context.drawTextWrapped(textRenderer, footer, left + 8, footerY, panelWidth - 16, TEXT_SOFT);
	}

	private String trim(Text text, int maxWidth) {
		String value = text.getString();
		if (textRenderer.getWidth(value) <= maxWidth) {
			return value;
		}
		return textRenderer.trimToWidth(value, maxWidth - textRenderer.getWidth("…")) + "…";
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int index = rowAt(mouseX, mouseY);
		if (button == 0 && index >= 0 && client != null) {
			ClientPlayNetworking.send(new ToggleHeroAbilityPayload(abilities().get(index)));
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2f));
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount)));
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (client != null && com.santiq.kingdomomnitrix.client.input.ModKeyBindings.HERO_MENU.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
