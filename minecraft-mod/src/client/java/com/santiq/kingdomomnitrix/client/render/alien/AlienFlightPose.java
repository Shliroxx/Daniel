package com.santiq.kingdomomnitrix.client.render.alien;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Flughaltung eines Aliens (Jetray: Fluegel auf, Arme nach hinten, Beine angezogen) — Endpose der AE-Fluganimation,
 * vom Importer in {@code alien_render/<alien>.json} unter {@code flight_pose} geschrieben:
 * <pre>
 * "flight_pose": { "&lt;knochen&gt;": { "rotation": [x, y, z], "position": [x, y, z], "scale": [x, y, z], "add": true } }
 * </pre>
 * Drehung in Grad wie Bedrock (GeckoLib: x und y gespiegelt), Lage in Pixeln. {@code add}: Knochen wird jedes Bild von
 * einer Daueranimation gesetzt, die Pose kommt obendrauf (Schwanz wedelt weiter). {@code blend}: Knochen haelt am Boden
 * eine AE-Ruhehaltung aus der Daueranimation (Big Chill: Umhang zu, Fluegel weg) — am Boden bleibt der Animationswert,
 * in der Luft wird zu Ruhelage + Pose uebergeblendet. Sonst Ruhelage + Pose.
 *
 * <p>Darueber liegt ein eigenes Flugmodell (nicht aus AE): Kurvenlage aus der Drehgeschwindigkeit, Vorlage des
 * ganzen Koerpers mit dem Tempo, Fluegelschlag beim Steigen (Knochen mit „wing“ im Namen) und ruhiges Gleiten beim
 * Sinken. Alles aus Positionsaenderungen, also auch fuer fremde Spieler ohne eigenes Netzwerkpaket.</p>
 *
 * <p>Wann geflogen wird, weiss der Client allein: eigener Spieler ueber {@code abilities.flying}, fremde Spieler ueber
 * „laenger in der Luft“; dazu Schwimmen. Der Uebergang dauert {@link #BLEND_TICKS} Ticks (AE: 0,58 s).</p>
 */
final class AlienFlightPose {
	/** Ticks fuer den Uebergang Stand ↔ Flug */
	private static final float BLEND_TICKS = 11.0f;
	/** fremde Spieler gelten nach so vielen Ticks ohne Boden als fliegend (Spruenge bleiben Spruenge) */
	private static final int AIR_TICKS = 10;
	/** Ueberblendung je Spieler: aktueller und vorheriger Tick */
	private static final Map<Integer, float[]> BLEND = new HashMap<>();
	/** Flugdynamik je Spieler: [Kurvenlage, Vorlage, Steigen, letzte X, letzte Y, letzte Z, letzter Koerper-Yaw] */
	private static final Map<Integer, float[]> DYNAMICS = new HashMap<>();
	/** groesste Kurvenlage und Vorlage (Bogenmass) */
	private static final float MAX_BANK = 0.75f;
	private static final float MAX_LEAN = 0.6f;
	/** Ticks ohne Boden je Spieler */
	private static final Map<Integer, Integer> AIRBORNE = new HashMap<>();

	record Bone(float[] rotation, float[] position, float @Nullable [] scale, boolean additive, boolean blend) {
	}

	private AlienFlightPose() {
	}

	/** {@code flight_pose} lesen; fehlt das Feld: leer. Kaputte Eintraege werden uebersprungen. */
	static Map<String, Bone> parse(JsonObject json) {
		if (!json.has("flight_pose") || !json.get("flight_pose").isJsonObject()) {
			return Map.of();
		}
		Map<String, Bone> bones = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("flight_pose").entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject bone = entry.getValue().getAsJsonObject();
			float[] rotation = vector(bone, "rotation", 0.0f);
			// Bedrock → GeckoLib: x und y gespiegelt, Grad → Bogenmass
			rotation = new float[]{-rotation[0] * MathHelper.RADIANS_PER_DEGREE, -rotation[1] * MathHelper.RADIANS_PER_DEGREE,
					rotation[2] * MathHelper.RADIANS_PER_DEGREE};
			bones.put(entry.getKey(), new Bone(rotation, vector(bone, "position", 0.0f),
					bone.has("scale") ? vector(bone, "scale", 1.0f) : null,
					bone.has("add") && bone.get("add").getAsBoolean(),
					bone.has("blend") && bone.get("blend").getAsBoolean()));
		}
		return Map.copyOf(bones);
	}

	private static float[] vector(JsonObject bone, String key, float fallback) {
		float[] out = {fallback, fallback, fallback};
		if (bone.has(key) && bone.get(key).isJsonArray()) {
			JsonArray array = bone.getAsJsonArray(key);
			for (int i = 0; i < Math.min(3, array.size()); i++) {
				out[i] = array.get(i).getAsFloat();
			}
		}
		return out;
	}

	/** Je Tick: Flugzustand je Spieler bestimmen und die Ueberblendung nachziehen. */
	static void tick(AbstractClientPlayerEntity player) {
		int air = player.isOnGround() || player.isClimbing() || player.hasVehicle() ? 0 : AIRBORNE.getOrDefault(player.getId(), 0) + 1;
		AIRBORNE.put(player.getId(), air);
		boolean flying = player instanceof ClientPlayerEntity self ? self.getAbilities().flying || player.isFallFlying()
				: air > AIR_TICKS;
		flying |= player.isSwimming() || (player.isTouchingWater() && !player.isOnGround());
		float[] dyn = DYNAMICS.computeIfAbsent(player.getId(), id -> new float[]{0, 0, 0,
				(float) player.getX(), (float) player.getY(), (float) player.getZ(), player.bodyYaw});
		float dx = (float) player.getX() - dyn[3];
		float dy = (float) player.getY() - dyn[4];
		float dz = (float) player.getZ() - dyn[5];
		float turn = MathHelper.wrapDegrees(player.bodyYaw - dyn[6]);
		dyn[3] = (float) player.getX();
		dyn[4] = (float) player.getY();
		dyn[5] = (float) player.getZ();
		dyn[6] = player.bodyYaw;
		float speed = MathHelper.sqrt(dx * dx + dz * dz);
		// weich nachziehen: Kurvenlage folgt der Drehung, Vorlage dem Tempo, Steigen der Hoehenaenderung
		dyn[0] = MathHelper.lerp(0.2f, dyn[0], MathHelper.clamp(-turn * 0.06f * Math.min(1.0f, speed * 2.5f), -MAX_BANK, MAX_BANK));
		dyn[1] = MathHelper.lerp(0.15f, dyn[1], MathHelper.clamp(speed * 0.9f, 0.0f, MAX_LEAN));
		dyn[2] = MathHelper.lerp(0.25f, dyn[2], MathHelper.clamp(dy * 4.0f, -1.0f, 1.0f));
		float[] blend = BLEND.computeIfAbsent(player.getId(), id -> new float[2]);
		blend[1] = blend[0];
		blend[0] = MathHelper.clamp(blend[0] + (flying ? 1.0f : -1.0f) / BLEND_TICKS, 0.0f, 1.0f);
	}

	static void retain(Set<Integer> players) {
		BLEND.keySet().retainAll(players);
		DYNAMICS.keySet().retainAll(players);
		AIRBORNE.keySet().retainAll(players);
	}

	/** Ueberblendung 0 (Stand) … 1 (Flug), weich ein- und ausgeblendet. */
	static float progress(AbstractClientPlayerEntity player, float tickDelta) {
		float[] blend = BLEND.get(player.getId());
		if (blend == null) {
			return 0.0f;
		}
		float t = MathHelper.lerp(tickDelta, blend[1], blend[0]);
		return t * t * (3.0f - 2.0f * t);
	}

	/**
	 * Flugmodell obendrauf: Kurvenlage und Vorlage am ganzen Koerper (root), Fluegelschlag an den Fluegel-Knochen —
	 * schnell beim Steigen, langsam und flach beim Gleiten.
	 */
	static void applyDynamics(AnimationProcessor<?> processor, Map<String, Bone> pose, AbstractClientPlayerEntity player, float tickDelta,
			float k) {
		float[] dyn = DYNAMICS.get(player.getId());
		if (dyn == null || k <= 0.0f) {
			return;
		}
		GeoBone root = processor.getBone("root");
		if (root != null) {
			root.updateRotation(root.getRotX() - dyn[1] * k, root.getRotY(), root.getRotZ() + dyn[0] * k);
		}
		float age = player.age + tickDelta;
		float climb = Math.max(0.0f, dyn[2]);
		float rate = 0.35f + climb * 0.5f;
		float amplitude = (0.12f + climb * 0.35f) * k;
		float flap = MathHelper.sin(age * rate) * amplitude;
		for (String name : pose.keySet()) {
			if (!name.contains("wing")) {
				continue;
			}
			GeoBone bone = processor.getBone(name);
			if (bone != null) {
				float side = name.contains("left") ? -1.0f : 1.0f;
				bone.updateRotation(bone.getRotX(), bone.getRotY(), bone.getRotZ() + flap * side);
			}
		}
	}

	/**
	 * Pose anwenden. Wird jedes Bild gerufen, auch bei {@code k == 0}: GeckoLib setzt selbst gesetzte Knochen nicht
	 * zurueck, also muessen sie wieder auf die Ruhelage.
	 */
	static void apply(AnimationProcessor<?> processor, Map<String, Bone> pose, float k) {
		for (Map.Entry<String, Bone> entry : pose.entrySet()) {
			GeoBone bone = processor.getBone(entry.getKey());
			if (bone == null) {
				continue;
			}
			Bone target = entry.getValue();
			boolean main = target.additive() || AlienPose.isPlayerDriven(entry.getKey());
			BoneSnapshot rest = bone.getInitialSnapshot();
			if (rest == null) {
				bone.saveInitialSnapshot();
				rest = bone.getInitialSnapshot();
			}
			if (target.blend()) {
				// Animationswert (Ruhehaltung) → Ruhelage + Flugpose
				bone.updateRotation(MathHelper.lerp(k, bone.getRotX(), rest.getRotX() + target.rotation()[0]),
						MathHelper.lerp(k, bone.getRotY(), rest.getRotY() + target.rotation()[1]),
						MathHelper.lerp(k, bone.getRotZ(), rest.getRotZ() + target.rotation()[2]));
				bone.updatePosition(MathHelper.lerp(k, bone.getPosX(), rest.getOffsetX() + target.position()[0]),
						MathHelper.lerp(k, bone.getPosY(), rest.getOffsetY() + target.position()[1]),
						MathHelper.lerp(k, bone.getPosZ(), rest.getOffsetZ() + target.position()[2]));
				float[] scale = target.scale() != null ? target.scale() : new float[]{1.0f, 1.0f, 1.0f};
				bone.updateScale(MathHelper.lerp(k, bone.getScaleX(), scale[0]), MathHelper.lerp(k, bone.getScaleY(), scale[1]),
						MathHelper.lerp(k, bone.getScaleZ(), scale[2]));
				continue;
			}
			float rx = main ? bone.getRotX() : rest.getRotX();
			float ry = main ? bone.getRotY() : rest.getRotY();
			float rz = main ? bone.getRotZ() : rest.getRotZ();
			bone.updateRotation(rx + target.rotation()[0] * k, ry + target.rotation()[1] * k, rz + target.rotation()[2] * k);
			float px = main ? bone.getPosX() : rest.getOffsetX();
			float py = main ? bone.getPosY() : rest.getOffsetY();
			float pz = main ? bone.getPosZ() : rest.getOffsetZ();
			bone.updatePosition(px + target.position()[0] * k, py + target.position()[1] * k, pz + target.position()[2] * k);
			if (target.scale() != null) {
				bone.updateScale(MathHelper.lerp(k, 1.0f, target.scale()[0]), MathHelper.lerp(k, 1.0f, target.scale()[1]),
						MathHelper.lerp(k, 1.0f, target.scale()[2]));
			}
		}
	}
}
