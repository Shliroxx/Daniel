package com.santiq.kingdomomnitrix.client.menu;

import com.santiq.kingdomomnitrix.alien.AbilitySlot;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.DnaScan;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.client.ui.Icons;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.progression.AlienMastery;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Alien-Uebersicht (Reiter „Aliens“): links alle Aliens mit Status, rechts Meisterschaft, Verwandlungsdauer mit
 * allen Boni, Nachladezeit, Faehigkeiten und – fuer noch gesperrte Aliens – wo es DNA gibt.
 */
public class AlienScreen extends Screen {
	private static final int ROW = 20;
	private static final int LIST_WIDTH = 120;
	private static final int DETAIL_ICON = 44;

	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private Identifier selected;
	/** Knopf „Loadout & Kraefte“: x, y, Breite, Hoehe (null = nicht sichtbar) */
	private int[] loadoutButton;

	public AlienScreen() {
		super(Text.translatable("menu.kingdomomnitrix.tab.aliens"));
	}

	@Override
	protected void init() {
		panelWidth = Math.min(width - MenuTabs.RESERVED - 8, 360);
		panelHeight = Math.min(height - 12, 224);
		left = MenuTabs.RESERVED + (width - MenuTabs.RESERVED - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		MenuTabs.addTo(this, MenuTab.ALIENS, this::addDrawableChild);
		if (selected == null) {
			List<Identifier> ids = aliens();
			selected = ids.isEmpty() ? null : ids.get(0);
		}
	}

	private DynamicRegistryManager registries() {
		return client != null && client.world != null ? client.world.getRegistryManager() : null;
	}

	private List<Identifier> aliens() {
		DynamicRegistryManager registries = registries();
		return registries == null ? List.of() : AlienRegistry.sortedIds(registries);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		ClientPlayerEntity player = client != null ? client.player : null;
		DynamicRegistryManager registries = registries();
		if (player == null || registries == null) {
			return;
		}
		UiDraw.panel(context, left, top, panelWidth, panelHeight, UiTheme.OMNITRIX);
		context.drawTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), left + 8, top + 6, UiTheme.OMNITRIX.accent());
		AlienMastery mastery = AlienMasteryManager.get(player);
		// Kopfzeile rechts: Weg zu Master Control (oder Status, wenn freigeschaltet)
		var progress = com.santiq.kingdomomnitrix.omnitrix.MasterControlProgress.progress(player);
		Text mc = com.santiq.kingdomomnitrix.omnitrix.MasterControlProgress.unlocked(player)
				? Text.translatable("screen.kingdomomnitrix.aliens.mc_unlocked")
				: Text.translatable("screen.kingdomomnitrix.aliens.mc_progress", Math.min(progress.reached(), progress.needed()),
						progress.needed(), progress.level());
		context.drawTextWithShadow(textRenderer, mc, left + panelWidth - 8 - textRenderer.getWidth(mc), top + 6,
				progress.done() ? 0xFFFFC94A : UiTheme.TEXT_SOFT);

		List<Identifier> ids = aliens();
		for (int i = 0; i < ids.size(); i++) {
			Identifier id = ids.get(i);
			Optional<AlienDefinition> alien = AlienRegistry.get(registries, id);
			if (alien.isEmpty()) {
				continue;
			}
			int y = top + 20 + i * ROW;
			if (y + ROW > top + panelHeight) {
				break;
			}
			boolean unlocked = HeroDataAccess.get(player).hasAlien(id);
			boolean hovered = mouseX >= left + 4 && mouseX < left + 4 + LIST_WIDTH && mouseY >= y && mouseY < y + ROW - 2;
			int background = id.equals(selected) ? UiTheme.OMNITRIX.accent(0x60) : hovered ? 0x30FFFFFF : 0x40000000;
			context.fill(left + 4, y, left + 4 + LIST_WIDTH, y + ROW - 2, background);
			context.fill(left + 4, y, left + 6, y + ROW - 2, unlocked ? 0xFF000000 | alien.get().color() : UiTheme.TEXT_DISABLED);
			Text name = unlocked ? TransformationManager.alienName(id).withColor(alien.get().color()) : Text.literal("???");
			// Symbol: freigeschaltet farbig, gesperrt nur dunkle Silhouette (Umriss verraet die Form, nicht das Alien)
			boolean icon = unlocked ? UiDraw.alienIcon(context, id, left + 8, y + 1, ROW - 4, 1.0f)
					: UiDraw.alienSilhouette(context, id, left + 8, y + 1, ROW - 4, 0x2A2A2A, 0.9f);
			int textX = icon ? left + 8 + ROW - 2 : left + 10;
			context.drawTextWithShadow(textRenderer, name, textX, y + 5, unlocked ? UiTheme.TEXT : UiTheme.TEXT_DISABLED);
			if (unlocked) {
				String star = "★" + mastery.level(id);
				context.drawTextWithShadow(textRenderer, star, left + 4 + LIST_WIDTH - 4 - textRenderer.getWidth(star), y + 5, 0xFFFFC94A);
			}
		}
		loadoutButton = null;
		if (selected != null) {
			AlienRegistry.get(registries, selected).ifPresent(alien -> renderDetail(context, player, selected, alien, mastery));
		}
	}

	private void renderDetail(DrawContext context, ClientPlayerEntity player, Identifier id, AlienDefinition alien, AlienMastery mastery) {
		int x = left + LIST_WIDTH + 14;
		int y = top + 20;
		int width = panelWidth - LIST_WIDTH - 22;
		boolean unlocked = HeroDataAccess.get(player).hasAlien(id);
		Text name = unlocked ? TransformationManager.alienName(id).withColor(alien.color()).formatted(Formatting.BOLD)
				: Text.translatable("screen.kingdomomnitrix.aliens.unknown").formatted(Formatting.GRAY, Formatting.BOLD);
		context.drawTextWithShadow(textRenderer, name, x, y, UiTheme.TEXT);
		// grosses Symbol unten rechts im Detailbereich (frei; oben laeuft die Meisterschaftsleiste ueber die volle Breite)
		int iconX = x + width - DETAIL_ICON;
		int iconY = top + panelHeight - DETAIL_ICON - 6;
		if (unlocked) {
			UiDraw.alienIcon(context, id, iconX, iconY, DETAIL_ICON, 1.0f);
		} else {
			UiDraw.alienSilhouette(context, id, iconX, iconY, DETAIL_ICON, 0x2A2A2A, 0.9f);
		}
		y += 13;

		if (!unlocked) {
			context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.aliens.dna"), x, y, UiTheme.TEXT_SOFT);
			y += 11;
			AlienDefinition.DnaInfo dna = alien.dna();
			for (AlienDefinition.DnaSource source : dna.sources()) {
				Optional<EntityType<?>> type = Registries.ENTITY_TYPE.getOrEmpty(source.entity());
				Text entity = type.map(EntityType::getName).orElse(Text.literal(source.entity().toString()));
				Text where = source.dimension().map(DnaScan::worldName).orElse(Text.translatable("screen.kingdomomnitrix.aliens.dna_anywhere"));
				MutableText line = Text.translatable("screen.kingdomomnitrix.aliens.dna_source", entity, where);
				context.drawTextWithShadow(textRenderer, UiDraw.trim(textRenderer, line, width), x + 4, y, UiTheme.TEXT);
				y += 10;
			}
			if (dna.sources().isEmpty()) {
				context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.aliens.dna_none"), x + 4, y, UiTheme.TEXT_DISABLED);
			} else {
				// Proben-Fortschritt
				y += 3;
				int have = DnaScan.progress(player, id);
				context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.aliens.dna_progress", have, dna.samples()),
						x, y, 0xFF7DFF5A);
				y += 10;
				UiDraw.bar(context, x, y, width, 3, have / (float) dna.samples(), 0xFF55FF3A);
				y += 8;
				context.drawTextWithShadow(textRenderer, UiDraw.trim(textRenderer, Text.translatable("screen.kingdomomnitrix.aliens.dna_how"), width),
						x, y, UiTheme.TEXT_DISABLED);
			}
			return;
		}

		// Meisterschaft mit Fortschritt zur naechsten Stufe
		int level = mastery.level(id);
		int xp = mastery.experience(id);
		int from = AlienMastery.experienceFor(level);
		int to = AlienMastery.experienceFor(Math.min(AlienMastery.MAX_LEVEL, level + 1));
		Text masteryText = Text.translatable("screen.kingdomomnitrix.aliens.mastery", level, AlienMastery.MAX_LEVEL);
		context.drawTextWithShadow(textRenderer, masteryText, x, y, 0xFFFFC94A);
		String progress = level >= AlienMastery.MAX_LEVEL ? "MAX" : (xp - from) + " / " + (to - from);
		context.drawTextWithShadow(textRenderer, progress, x + width - textRenderer.getWidth(progress), y, UiTheme.TEXT_SOFT);
		y += 10;
		UiDraw.bar(context, x, y, width, 3, level >= AlienMastery.MAX_LEVEL ? 1.0f : (float) (xp - from) / Math.max(1, to - from),
				UiTheme.OMNITRIX.accent());
		y += 8;

		int duration = TransformationManager.durationTicks(player, id, alien);
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.aliens.duration", duration / 20,
				alien.durationTicks() / 20), x, y, UiTheme.TEXT);
		y += 10;
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.aliens.recharge", alien.rechargeTicks() / 20,
				Math.round(mastery.cooldownReduction(id) * 100)), x, y, UiTheme.TEXT_SOFT);
		y += 14;

		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.aliens.abilities"), x, y, UiTheme.OMNITRIX.accent());
		y += 11;
		// Kurzuebersicht: ausgeruestete sechs Tasten, Anzahl im Pool, Kombos und Passive; Details im Loadout-Fenster
		boolean masterControl = client != null && client.player != null
				&& com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore.state(client.player).masterControl();
		List<Integer> loadout = com.santiq.kingdomomnitrix.alien.LoadoutManager.get(player).resolve(id, alien);
		int top = y;
		for (int i = 0; i < loadout.size(); i++) {
			AbilitySlot slot = alien.abilities().get(loadout.get(i));
			int ax = x + i * 22;
			boolean open = slot.unlocked(level) || masterControl;
			UiDraw.art(context, Icons.alienAbilityArt(slot.type()), Icons.alienAbility(slot.type()), ax, y, 20, open ? 0xFFFFFF : 0x4A4E54, 1.0f);
		}
		y += 24;
		Text counts = Text.translatable("screen.kingdomomnitrix.aliens.power_counts", alien.keyIndices().size(), alien.comboIndices().size(),
				alien.traits().passives().size());
		context.drawTextWithShadow(textRenderer, UiDraw.trim(textRenderer, counts, width), x, y, UiTheme.TEXT_SOFT);
		y += 12;
		Text button = Text.translatable("screen.kingdomomnitrix.aliens.open_loadout");
		loadoutButton = new int[] {x, y, textRenderer.getWidth(button) + 12, 14};
		com.santiq.kingdomomnitrix.client.ui.HudGfx.rounded(context, x, y, loadoutButton[2], 14, 3, 0xC0000000 | (alien.color() & 0x7F7F7F));
		com.santiq.kingdomomnitrix.client.ui.HudGfx.roundedBorder(context, x, y, loadoutButton[2], 14, 0xFF000000 | alien.color());
		context.drawTextWithShadow(textRenderer, button, x + 6, y + 3, UiTheme.TEXT);
		y = top + 3 * 22;
		if (alien.prototype()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.aliens.prototype"), x, y, UiTheme.TEXT_DISABLED);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button == 0 && loadoutButton != null && selected != null && client != null && mouseX >= loadoutButton[0]
				&& mouseX < loadoutButton[0] + loadoutButton[2] && mouseY >= loadoutButton[1] && mouseY < loadoutButton[1] + loadoutButton[3]) {
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.3f));
			client.setScreen(new LoadoutScreen(this, selected));
			return true;
		}
		List<Identifier> ids = aliens();
		if (button == 0 && mouseX >= left + 4 && mouseX < left + 4 + LIST_WIDTH) {
			int index = (int) ((mouseY - top - 20) / ROW);
			if (mouseY >= top + 20 && index >= 0 && index < ids.size() && client != null) {
				selected = ids.get(index);
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.3f));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
