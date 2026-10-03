package com.santiq.kingdomomnitrix.client.menu;

import com.santiq.kingdomomnitrix.client.hero.HeroScreen;
import com.santiq.kingdomomnitrix.client.quest.QuestBookScreen;
import com.santiq.kingdomomnitrix.client.ui.Icons;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Menue-Reiter wie die Tabs in Kingdom Hearts: eine Spalte am linken Rand im Inventar und in jedem Mod-Menue.
 * Das Inventar selbst bleibt unveraendert; die Reiter liegen neben ihm.
 */
public final class MenuTabs {
	public static final int SIZE = 20;
	public static final int X = 2;
	/** So viel Platz brauchen die Reiter am linken Rand. */
	public static final int RESERVED = X + SIZE + 4;

	private MenuTabs() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen) {
				addTo(screen, MenuTab.INVENTORY, widget -> Screens.getButtons(screen).add(widget));
			}
		});
	}

	/** Fuegt die Reiter-Spalte hinzu; {@code current} ist hervorgehoben. */
	public static void addTo(Screen screen, MenuTab current, Consumer<ClickableWidget> adder) {
		MenuTab[] tabs = MenuTab.values();
		int top = Math.max(2, (screen.height - tabs.length * (SIZE + 2)) / 2);
		for (int i = 0; i < tabs.length; i++) {
			adder.accept(new TabButton(X, top + i * (SIZE + 2), tabs[i], tabs[i] == current));
		}
	}

	public static void open(MinecraftClient client, MenuTab tab) {
		if (client.player == null) {
			return;
		}
		Screen next = switch (tab) {
			case INVENTORY -> new InventoryScreen(client.player); // wechselt im Kreativmodus selbst zum Kreativ-Inventar
			case HERO -> new HeroScreen();
			case ALIENS -> new AlienScreen();
			case QUESTS -> new QuestBookScreen();
			case MAP -> new WorldMapScreen();
			case HUD -> new HudEditorScreen();
		};
		client.setScreen(next);
	}

	/** Quadratischer Reiter mit Symbol und Namen als Tooltip. */
	private static final class TabButton extends ClickableWidget {
		private static final ItemStack CHEST = new ItemStack(Items.CHEST);
		private final MenuTab tab;
		private final boolean current;

		TabButton(int x, int y, MenuTab tab, boolean current) {
			super(x, y, SIZE, SIZE, Text.translatable(tab.translationKey()));
			this.tab = tab;
			this.current = current;
			setTooltip(Tooltip.of(Text.translatable(tab.translationKey())));
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			UiDraw.panel(context, getX(), getY(), SIZE, SIZE, tab.theme());
			if (current) {
				context.fill(getX() + 1, getY() + 1, getX() + SIZE - 1, getY() + SIZE - 1, tab.theme().accent(0x80));
			} else if (isHovered()) {
				context.fill(getX() + 1, getY() + 1, getX() + SIZE - 1, getY() + SIZE - 1, 0x30FFFFFF);
			}
			if (tab.icon() == null) {
				context.drawItem(CHEST, getX() + 2, getY() + 2);
			} else {
				UiDraw.icon(context, Icons.menu(tab.icon()), Icons.FALLBACK, getX() + 2, getY() + 2, UiDraw.ICON_SIZE);
			}
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			if (!current) {
				MinecraftClient client = MinecraftClient.getInstance();
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
				open(client, tab);
			}
		}

		@Override
		public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) {
			// Klang kommt aus onClick (nur wenn der Reiter wechselt)
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}
}
