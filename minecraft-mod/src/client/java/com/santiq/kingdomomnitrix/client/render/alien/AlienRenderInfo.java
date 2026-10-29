package com.santiq.kingdomomnitrix.client.render.alien;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienUniforms;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Darstellungs-Angaben eines Alien-Modells aus {@code assets/<ns>/alien_render/<alien>.json}, einmal pro Modell und
 * Ressourcen-Neuladen gelesen ({@link AlienBodyRenderers} haelt den Zwischenspeicher).
 *
 * <pre>
 * scale          Darstellungsgroesse (0,05..5, Standard 1)
 * uniforms       Uniform-IDs, erste = Grundtextur (Standard [classic])
 * uniform_models je Uniform eigene Geometrie
 * vanilla_pose   Hauptknochen folgen der Spielerpose (mit arm_swing, leg_swing, ability_poses, mode_poses)
 * glow_frames    Glut-Frames (_f&lt;i&gt;), warn_textures: Abzeichen-Warnung (_warn)
 * flight_pose    Flughaltung je Knochen ({@link AlienFlightPose})
 * mode_forms     {modus: {"form": kuerzel, "scale": groesse}} — Modus zeigt ein eigenes Ganzkoerper-Modell
 *                (&lt;geo&gt;_&lt;kuerzel&gt;.geo.json, Textur &lt;textur&gt;_&lt;kuerzel&gt;.png), z. B. Upgrades Blitzableiter
 * </pre>
 * Fehlende Datei: Standardwerte. Kaputte Datei oder Felder: Fehler im Log, Standardwerte — kein Absturz.
 *
 * @param pose Pose-Angaben, {@code null} = Modell nutzt eigene Animationen fuer alle Knochen
 */
public record AlienRenderInfo(float scale, List<String> uniforms, boolean uniformModels, @Nullable AlienPose.Info pose,
		int glowFrames, boolean warnTextures, Map<String, AlienFlightPose.Bone> flightPose, Map<String, ModeForm> modeForms) {

	/** Ganzkoerper-Modell eines Modus: Datei-Kuerzel und Groesse relativ zum Alien-Koerper. */
	public record ModeForm(String form, float scale) {
	}

	public static final AlienRenderInfo DEFAULT = new AlienRenderInfo(1.0f, List.of(AlienUniforms.CLASSIC), false, null, 1, false, Map.of(), Map.of());

	public boolean hasUniform(String uniform) {
		return uniforms.contains(uniform);
	}

	static AlienRenderInfo load(Identifier model) {
		Identifier file = Identifier.of(model.getNamespace(), "alien_render/" + model.getPath() + ".json");
		var resource = MinecraftClient.getInstance().getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return DEFAULT;
		}
		try (Reader reader = resource.get().getReader()) {
			return parse(JsonParser.parseReader(reader).getAsJsonObject());
		} catch (IOException | RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("Darstellungs-Angaben {} unlesbar, nutze Standardwerte", file, e);
			return DEFAULT;
		}
	}

	static AlienRenderInfo parse(JsonObject json) {
		float scale = json.has("scale") ? json.get("scale").getAsFloat() : 1.0f;
		if (!(scale > 0.05f && scale < 5.0f)) {
			scale = 1.0f;
		}
		List<String> uniforms = new ArrayList<>();
		if (json.has("uniforms")) {
			for (JsonElement element : json.getAsJsonArray("uniforms")) {
				uniforms.add(element.getAsString());
			}
		}
		if (uniforms.isEmpty()) {
			uniforms.add(AlienUniforms.CLASSIC);
		}
		AlienPose.Info pose = null;
		if (json.has("vanilla_pose") && json.get("vanilla_pose").getAsBoolean()) {
			float arms = json.has("arm_swing") ? json.get("arm_swing").getAsFloat() : 1.0f;
			float legs = json.has("leg_swing") ? json.get("leg_swing").getAsFloat() : 1.0f;
			pose = new AlienPose.Info(MathHelper.clamp(arms, 0.0f, 2.0f), MathHelper.clamp(legs, 0.0f, 2.0f), AlienPose.parseAbilities(json), AlienPose.parseModes(json));
		}
		int frames = json.has("glow_frames") ? MathHelper.clamp(json.get("glow_frames").getAsInt(), 1, 64) : 1;
		return new AlienRenderInfo(scale, List.copyOf(uniforms), json.has("uniform_models") && json.get("uniform_models").getAsBoolean(),
				pose, frames, json.has("warn_textures") && json.get("warn_textures").getAsBoolean(), AlienFlightPose.parse(json),
				parseModeForms(json));
	}

	private static Map<String, ModeForm> parseModeForms(JsonObject json) {
		if (!json.has("mode_forms") || !json.get("mode_forms").isJsonObject()) {
			return Map.of();
		}
		Map<String, ModeForm> forms = new java.util.HashMap<>();
		for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("mode_forms").entrySet()) {
			JsonObject form = entry.getValue().getAsJsonObject();
			float scale = form.has("scale") ? MathHelper.clamp(form.get("scale").getAsFloat(), 0.1f, 4.0f) : 1.0f;
			forms.put(entry.getKey(), new ModeForm(form.get("form").getAsString(), scale));
		}
		return Map.copyOf(forms);
	}
}
