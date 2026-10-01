package com.santiq.kingdomomnitrix.client.menu;

import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import java.util.Locale;

/** Reiter der Menue-Leiste (links am Bildschirmrand im Inventar und in allen Mod-Menues). */
public enum MenuTab {
	INVENTORY(null, UiTheme.HERO),
	HERO("hero", UiTheme.HERO),
	ALIENS("aliens", UiTheme.OMNITRIX),
	QUESTS("quests", UiTheme.KEYBLADE),
	MAP("map", UiTheme.EXPLORATION),
	HUD("hud", UiTheme.TECH);

	private final String icon;
	private final UiTheme theme;

	MenuTab(String icon, UiTheme theme) {
		this.icon = icon;
		this.theme = theme;
	}

	/** Symbol unter {@code textures/gui/icon/menu/}; das Inventar zeigt stattdessen eine Truhe. */
	public String icon() {
		return icon;
	}

	public UiTheme theme() {
		return theme;
	}

	public String translationKey() {
		return "menu.kingdomomnitrix.tab." + name().toLowerCase(Locale.ROOT);
	}
}
