package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
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
 * Farbmodul am Alien: faerbt das Omnitrix-Abzeichen um. Umgefaerbt werden nur Pixel, die in der Leuchtmaske
 * ({@code _glowmask}) liegen UND gruen sind — das Abzeichen. Eigene Farben des Aliens (Heatblasts Glut, Upgrades
 * Schaltkreise ausserhalb der Maske) bleiben. Ergebnis: Grundtextur und Leuchtmaske als Laufzeit-Texturen, je Textur und
 * Farbe einmal erzeugt (Cache wird beim Ressourcen-Neuladen geleert).
 */
public final class BadgeTint {
	public static final int CLASSIC = 0x39FF14;
	/** Rand um die Leuchtmaske, in dem gruene Pixel der Grundtextur noch zum Abzeichen zaehlen. */
	private static final int RIM = 2;
	private static final int BADGE_MIN = 24;
	private static final int BADGE_MAX = 40;

	private record Key(Identifier texture, int rgb) {
	}

	/** Umgefaerbte Grundtextur und Leuchtmaske; leer, wenn die Textur keine Maske hat. */
	private record Tinted(Identifier base, Identifier glow) {
	}

	private static final Map<Key, Optional<Tinted>> CACHE = new HashMap<>();
	private static final Map<Identifier, Identifier> GLOW_OF = new HashMap<>();

	private BadgeTint() {
	}

	/** Textur fuer dieses Farbmodul (Klassisch Gruen oder ohne Maske: unveraendert). */
	public static Identifier tint(Identifier texture, int rgb) {
		if ((rgb & 0xFFFFFF) == CLASSIC) {
			return texture;
		}
		return CACHE.computeIfAbsent(new Key(texture, rgb & 0xFFFFFF), BadgeTint::create).map(Tinted::base).orElse(texture);
	}

	/** Leuchtmaske zu einer umgefaerbten Textur, sonst leer (dann gilt die normale Maske). */
	public static Optional<Identifier> glowOf(Identifier tinted) {
		return Optional.ofNullable(GLOW_OF.get(tinted));
	}

	public static void clear() {
		MinecraftClient client = MinecraftClient.getInstance();
		for (Optional<Tinted> tinted : CACHE.values()) {
			tinted.ifPresent(t -> {
				client.getTextureManager().destroyTexture(t.base());
				client.getTextureManager().destroyTexture(t.glow());
			});
		}
		CACHE.clear();
		GLOW_OF.clear();
	}

	private static Optional<Tinted> create(Key key) {
		MinecraftClient client = MinecraftClient.getInstance();
		Identifier texture = key.texture();
		String path = texture.getPath();
		Identifier maskId = Identifier.of(texture.getNamespace(), path.substring(0, path.length() - 4) + "_glowmask.png");
		try (NativeImage base = read(texture); NativeImage mask = read(maskId)) {
			if (base == null || mask == null || base.getWidth() != mask.getWidth() || base.getHeight() != mask.getHeight()) {
				return Optional.empty();
			}
			NativeImage tintedBase = copy(base);
			NativeImage tintedMask = copy(mask);
			int width = base.getWidth();
			int height = base.getHeight();
			boolean[] badge = badgePixels(mask);
			// Abzeichen-Wuerfel in der Grundtextur: Vorderseite (= Maske) plus Rand, und rechts daneben Rueck- und
			// Seitenflaechen (Box-UV: der dunkle Rand und die Rueckseite liegen ausserhalb der Maske)
			boolean[] near = new boolean[width * height];
			int minX = width;
			int minY = height;
			int maxX = -1;
			int maxY = -1;
			for (int i = 0; i < badge.length; i++) {
				if (badge[i]) {
					minX = Math.min(minX, i % width);
					maxX = Math.max(maxX, i % width);
					minY = Math.min(minY, i / width);
					maxY = Math.max(maxY, i / width);
				}
			}
			if (maxX >= 0) {
				int faceWidth = maxX - minX + 1;
				for (int y = Math.max(0, minY - RIM); y <= Math.min(height - 1, maxY + RIM); y++) {
					for (int x = Math.max(0, minX - RIM); x <= Math.min(width - 1, maxX + faceWidth + RIM + 1); x++) {
						near[y * width + x] = true;
					}
				}
			}
			int changed = 0;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int m = mask.getColor(x, y);
					if (badge[y * width + x]) {
						tintedMask.setColor(x, y, recolor(m, key.rgb()));
						changed++;
					}
					int b = base.getColor(x, y);
					if (near[y * width + x] && ((b >>> 24) & 0xFF) != 0 && isBadgeGreen(b)) {
						tintedBase.setColor(x, y, recolor(b, key.rgb()));
					}
				}
			}
			if (changed == 0) {
				tintedBase.close();
				tintedMask.close();
				return Optional.empty();
			}
			String suffix = String.format("%06x", key.rgb());
			Identifier baseId = KingdomOmnitrix.id("badge_tint/" + texture.getNamespace() + "/" + path.replace(".png", "") + "_" + suffix);
			Identifier glowId = KingdomOmnitrix.id("badge_tint/" + texture.getNamespace() + "/" + path.replace(".png", "") + "_" + suffix + "_glow");
			client.getTextureManager().registerTexture(baseId, new NativeImageBackedTexture(tintedBase));
			client.getTextureManager().registerTexture(glowId, new NativeImageBackedTexture(tintedMask));
			GLOW_OF.put(baseId, glowId);
			return Optional.of(new Tinted(baseId, glowId));
		} catch (IOException e) {
			KingdomOmnitrix.LOGGER.warn("Abzeichen-Farbe fuer {} nicht erzeugt: {}", texture, e.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Abzeichen-Pixel der Leuchtmaske: zusammenhaengende gruene Flaechen in Abzeichen-Groesse ({@link #BADGE_MIN} bis
	 * {@link #BADGE_MAX} Pixel; das Alien-Evolution-Abzeichen hat 31). Groessere oder kleinere gruene Leuchtflaechen
	 * gehoeren dem Alien selbst (Upgrades Schaltkreise, Ripjaws' Leuchtpunkte) und bleiben.
	 */
	static boolean[] badgePixels(NativeImage mask) {
		int width = mask.getWidth();
		int height = mask.getHeight();
		boolean[] green = new boolean[width * height];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int m = mask.getColor(x, y);
				green[y * width + x] = ((m >>> 24) & 0xFF) != 0 && isBadgeGreen(m);
			}
		}
		boolean[] badge = new boolean[width * height];
		boolean[] seen = new boolean[width * height];
		java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
		java.util.List<Integer> component = new java.util.ArrayList<>();
		for (int start = 0; start < green.length; start++) {
			if (!green[start] || seen[start]) {
				continue;
			}
			component.clear();
			stack.push(start);
			seen[start] = true;
			while (!stack.isEmpty()) {
				int index = stack.pop();
				component.add(index);
				int cx = index % width;
				int cy = index / width;
				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						int nx = cx + dx;
						int ny = cy + dy;
						int next = ny * width + nx;
						if (nx >= 0 && ny >= 0 && nx < width && ny < height && green[next] && !seen[next]) {
							seen[next] = true;
							stack.push(next);
						}
					}
				}
			}
			if (component.size() >= BADGE_MIN && component.size() <= BADGE_MAX) {
				for (int index : component) {
					badge[index] = true;
				}
			}
		}
		return badge;
	}

	private static NativeImage read(Identifier id) throws IOException {
		Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream in = resource.get().getInputStream()) {
			return NativeImage.read(in);
		}
	}

	private static NativeImage copy(NativeImage source) {
		NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), false);
		copy.copyFrom(source);
		return copy;
	}

	/** NativeImage speichert ABGR: Kanaele herausloesen. */
	private static int red(int abgr) {
		return abgr & 0xFF;
	}

	private static int green(int abgr) {
		return (abgr >> 8) & 0xFF;
	}

	private static int blue(int abgr) {
		return (abgr >> 16) & 0xFF;
	}

	/** Gruen dominiert deutlich (Omnitrix-Gruen von dunkel bis fast weiss). */
	static boolean isBadgeGreen(int abgr) {
		int r = red(abgr);
		int g = green(abgr);
		int b = blue(abgr);
		return g >= 60 && g > r + 25 && g > b + 25;
	}

	/** Helligkeit behalten, Farbton und Saettigung vom Farbmodul. */
	static int recolor(int abgr, int rgb) {
		float value = green(abgr) / 255.0f;
		float whiteness = Math.min(red(abgr), blue(abgr)) / 255.0f;
		int tr = (rgb >> 16) & 0xFF;
		int tg = (rgb >> 8) & 0xFF;
		int tb = rgb & 0xFF;
		int r = Math.round(Math.min(255, (tr * value) + (255 - tr) * whiteness));
		int g = Math.round(Math.min(255, (tg * value) + (255 - tg) * whiteness));
		int b = Math.round(Math.min(255, (tb * value) + (255 - tb) * whiteness));
		return (abgr & 0xFF000000) | (b << 16) | (g << 8) | r;
	}
}
