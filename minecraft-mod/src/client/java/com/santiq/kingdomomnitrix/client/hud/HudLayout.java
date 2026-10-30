package com.santiq.kingdomomnitrix.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

/**
 * Spieler-Einstellung der HUD-Anordnung in {@code config/kingdomomnitrix-hud.json}: pro Element Bezugspunkt,
 * Versatz, Groesse und ob es ausgeblendet ist. Fehlt ein Eintrag, gilt der Standard des Elements.
 */
public final class HudLayout {
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2.0f;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
	private static boolean loaded;

	/** Einstellung eines Elements. */
	public record Entry(HudAnchor anchor, int x, int y, float scale, boolean hidden) {
		public Entry {
			scale = MathHelper.clamp(scale, MIN_SCALE, MAX_SCALE);
		}

		public Entry withPosition(HudAnchor newAnchor, int newX, int newY) {
			return new Entry(newAnchor, newX, newY, scale, hidden);
		}

		public Entry withScale(float newScale) {
			return new Entry(anchor, x, y, Math.round(newScale * 10.0f) / 10.0f, hidden);
		}

		public Entry withHidden(boolean newHidden) {
			return new Entry(anchor, x, y, scale, newHidden);
		}
	}

	private HudLayout() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kingdomomnitrix-hud.json");
	}

	public static Entry get(HudElement element) {
		load();
		return Optional.ofNullable(ENTRIES.get(element.id().toString()))
				.orElseGet(() -> defaults(element));
	}

	public static Entry defaults(HudElement element) {
		return new Entry(element.defaultAnchor(), element.defaultX(), element.defaultY(), 1.0f, false);
	}

	public static void set(HudElement element, Entry entry) {
		load();
		ENTRIES.put(element.id().toString(), entry);
	}

	public static void reset(HudElement element) {
		load();
		ENTRIES.remove(element.id().toString());
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = file();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				return;
			}
			for (Map.Entry<String, JsonElement> element : root.getAsJsonObject().entrySet()) {
				if (!element.getValue().isJsonObject()) {
					continue;
				}
				JsonObject json = element.getValue().getAsJsonObject();
				HudAnchor anchor = HudAnchor.byKey(json.has("anchor") ? json.get("anchor").getAsString() : "", HudAnchor.TOP_LEFT);
				ENTRIES.put(element.getKey(), new Entry(anchor,
						json.has("x") ? json.get("x").getAsInt() : 0,
						json.has("y") ? json.get("y").getAsInt() : 0,
						json.has("scale") ? json.get("scale").getAsFloat() : 1.0f,
						json.has("hidden") && json.get("hidden").getAsBoolean()));
			}
		} catch (IOException | RuntimeException e) {
			// kaputte Datei: Standard-Anordnung benutzen, Datei beim naechsten Speichern ersetzen
			KingdomOmnitrix.LOGGER.warn("HUD-Einstellungen {} unlesbar, benutze Standard: {}", path, e.getMessage());
			ENTRIES.clear();
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, Entry> entry : ENTRIES.entrySet()) {
			JsonObject json = new JsonObject();
			json.addProperty("anchor", entry.getValue().anchor().key());
			json.addProperty("x", entry.getValue().x());
			json.addProperty("y", entry.getValue().y());
			json.addProperty("scale", entry.getValue().scale());
			json.addProperty("hidden", entry.getValue().hidden());
			root.add(entry.getKey(), json);
		}
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			KingdomOmnitrix.LOGGER.error("HUD-Einstellungen konnten nicht gespeichert werden: {}", path, e);
		}
	}
}
