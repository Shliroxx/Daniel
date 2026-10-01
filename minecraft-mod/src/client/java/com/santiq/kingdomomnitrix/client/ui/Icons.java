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

	public static Identifier command(String name) {
		return texture(KingdomOmnitrix.MOD_ID, "command/" + name);
	}

	public static Identifier menu(String name) {
		return texture(KingdomOmnitrix.MOD_ID, "menu/" + name);
	}
}
