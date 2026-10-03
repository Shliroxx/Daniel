package com.santiq.kingdomomnitrix.client.ui;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.render.alien.BadgeTint;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

/**
 * Omnitrix-Symbole im Farbmodul: Faehigkeits-Symbole der Aliens, Kommando „Omnitrix“ und Menue-Reiter „Aliens“. Gruene
 * Pixel werden wie beim Alien-Abzeichen umgefaerbt (Helligkeit bleibt, Farbton vom Modul); je Symbol und Farbe einmal
 * als Laufzeit-Textur erzeugt. Klassisch Gruen und andere Symbole bleiben unveraendert.
 */
public final class IconTint {
	private record Key(Identifier texture, int rgb) {
	}

	private static final Map<Key, Identifier> CACHE = new HashMap<>();

	private IconTint() {
	}

	/** Gehoert das Symbol zum Omnitrix (und folgt damit dem Farbmodul)? */
	static boolean themed(Identifier texture) {
		String path = texture.getPath();
		return path.contains("/icon/alien_ability/") || path.endsWith("/icon/command/omnitrix.png") || path.endsWith("/icon/menu/aliens.png");
	}

	/** Symbol in der Modulfarbe des eigenen Spielers (oder unveraendert). */
	public static Identifier apply(Identifier texture) {
		var player = MinecraftClient.getInstance().player;
		if (player == null || !themed(texture)) {
			return texture;
		}
		OmnitrixColors.Color color = OmnitrixColors.of(player);
		if ("green".equals(color.id())) {
			return texture;
		}
		return CACHE.computeIfAbsent(new Key(texture, color.primary()), IconTint::create);
	}

	public static void clear() {
		MinecraftClient client = MinecraftClient.getInstance();
		CACHE.forEach((key, id) -> {
			if (!id.equals(key.texture())) {
				client.getTextureManager().destroyTexture(id);
			}
		});
		CACHE.clear();
	}

	private static Identifier create(Key key) {
		Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(key.texture());
		if (resource.isEmpty()) {
			return key.texture();
		}
		try (InputStream in = resource.get().getInputStream(); NativeImage source = NativeImage.read(in)) {
			NativeImage tinted = new NativeImage(source.getWidth(), source.getHeight(), false);
			tinted.copyFrom(source);
			for (int y = 0; y < source.getHeight(); y++) {
				for (int x = 0; x < source.getWidth(); x++) {
					int pixel = source.getColor(x, y);
					if (((pixel >>> 24) & 0xFF) != 0 && BadgeTint.isBadgeGreen(pixel)) {
						tinted.setColor(x, y, BadgeTint.recolor(pixel, key.rgb()));
					}
				}
			}
			String path = key.texture().getPath().replace(".png", "");
			Identifier id = KingdomOmnitrix.id("icon_tint/" + key.texture().getNamespace() + "/" + path + "_" + String.format("%06x", key.rgb()));
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(tinted));
			return id;
		} catch (IOException e) {
			KingdomOmnitrix.LOGGER.warn("Symbol {} nicht umgefaerbt: {}", key.texture(), e.getMessage());
			return key.texture();
		}
	}
}
