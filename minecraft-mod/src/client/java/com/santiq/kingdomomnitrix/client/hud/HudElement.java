package com.santiq.kingdomomnitrix.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Ein verschiebbares HUD-Element. Es zeichnet sich bei (0, 0) in seiner aktuellen Groesse; Position und
 * Skalierung setzt der {@link HudManager} nach der Spieler-Einstellung ({@link HudLayout}).
 */
public interface HudElement {
	Identifier id();

	/** Name im HUD-Editor. */
	Text name();

	HudAnchor defaultAnchor();

	/** Standard-Versatz vom Bezugspunkt (nach rechts/unten positiv). */
	int defaultX();

	int defaultY();

	/** Ob das Element gerade etwas zu zeigen hat (z. B. nur mit Keyblade in der Hand). */
	boolean isActive(MinecraftClient client);

	int width(MinecraftClient client);

	int height(MinecraftClient client);

	void render(DrawContext context, MinecraftClient client, float tickDelta);

	/** Groesse im Editor, wenn das Element gerade nichts zeigt. */
	default int previewWidth() {
		return 100;
	}

	default int previewHeight() {
		return 24;
	}
}
