package com.santiq.kingdomomnitrix.client.ui;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.util.Identifier;

/**
 * Pfade der Symbole (erzeugt von {@code tools/generate_icons.py}). Datenpakete koennen eigene Symbole unter
 * {@code textures/gui/icon/<art>/<pfad>.png} ihres Namensraums mitbringen; sonst greift das Ersatzsymbol.
 */
public final class Icons {
	public static final Identifier FALLBACK = texture(KingdomOmnitrix.MOD_ID, "menu/hero");

	private Icons() {
	}

	private static Identifier texture(String namespace, String path) {
		return Identifier.of(namespace, "textures/gui/icon/" + path + ".png");
	}

	public static Identifier heroAbility(Identifier id) {
		return texture(id.getNamespace(), "hero_ability/" + id.getPath());
	}

	public static Identifier spell(Identifier id) {
		return texture(id.getNamespace(), "spell/" + id.getPath());
	}

	public static Identifier alienAbility(Identifier type) {
		return texture(type.getNamespace(), "alien_ability/" + type.getPath());
	}

	/** Farbiges Alien-Symbol ({@code textures/gui/alien/<alien>.png}, erzeugt von {@code tools/generate_alien_icons.py}). */
	public static Identifier alien(Identifier alien) {
		return Identifier.of(alien.getNamespace(), "textures/gui/alien/" + alien.getPath() + ".png");
	}

	/** Weisse, einfaerbbare Alien-Silhouette ({@code textures/gui/alien/<alien>_silhouette.png}). */
	public static Identifier alienSilhouette(Identifier alien) {
		return Identifier.of(alien.getNamespace(), "textures/gui/alien/" + alien.getPath() + "_silhouette.png");
	}

	public static Identifier command(String name) {
		return texture(KingdomOmnitrix.MOD_ID, "command/" + name);
	}

	public static Identifier menu(String name) {
		return texture(KingdomOmnitrix.MOD_ID, "menu/" + name);
	}
}
