package com.santiq.kingdomomnitrix.client.render.alien;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Alien-Koerper wie in Alien Evolution bewegen: Kopf, Rumpf, Arme und Beine folgen dem Spielermodell (Gliedmaßen-
 * Schwung, Blickrichtung, Schlag, Schleichen) — genau wie Palladium seine GeckoLib-Ebenen an die Spielerteile haengt.
 * Pro Alien daempfen {@code arm_swing}/{@code leg_swing} aus {@code alien_render/<alien>.json} den Schwung
 * (AE-Skripte: Heatblast 0,8/0,6, Diamondhead und Grey Matter 0,6/0,6).
 *
 * <p>Faehigkeits-Posen ({@code ability_poses}, je Slot) stammen aus den AE-Animationsskripten und wirken wie in
 * Palladium: Lage/Drehung der Spielerteile setzen oder verschieben, dann mit der AE-Kurve von der normalen Pose zur
 * Zielpose blenden. Ablauf je Benutzung: {@link #RISE} Ticks hinein, halten, ab {@link #HOLD} wieder heraus.</p>
 *
 * <p>Positionen wie {@code GeoArmorRenderer}: Abweichung der Spielerteile von ihrer Grundlage (Schleichen senkt Kopf
 * und Arme), Drehungen mit GeckoLibs Vorzeichen ({@link RenderUtil#matchModelPartRot}).</p>
 */
final class AlienPose {
	static final int RISE = 4;
	static final int HOLD = 11;
	static final int END = 17;
	private static PlayerEntityModel<AbstractClientPlayerEntity> model;

	/** eine Palladium-Operation: Lage setzen/verschieben oder Drehung setzen/addieren, Achse 0..2, Wert (Pixel/Grad) */
	record Op(Kind kind, int axis, float value) {
	}

	enum Kind {SET_POS, MOVE_POS, SET_ROT, ADD_ROT}

	record AbilityPose(String ease, Map<String, List<Op>> parts) {
	}

	/** Pose-Angaben eines Alien-Modells aus {@code alien_render}. */
	record Info(float armSwing, float legSwing, List<AbilityPose> abilities) {
	}

	private AlienPose() {
	}

	static void reload(EntityRendererFactory.Context context) {
		model = new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false);
	}

	/** {@code ability_poses}: Liste je Slot, Eintrag {@code null} oder {"ease": …, "parts": {teil: [[op, achse, wert]]}}. */
	static List<AbilityPose> parseAbilities(JsonObject json) {
		List<AbilityPose> result = new ArrayList<>();
		if (!json.has("ability_poses") || !json.get("ability_poses").isJsonArray()) {
			return result;
		}
		for (JsonElement entry : json.getAsJsonArray("ability_poses")) {
			if (entry == null || !entry.isJsonObject()) {
				result.add(null);
				continue;
			}
			JsonObject pose = entry.getAsJsonObject();
			Map<String, List<Op>> parts = new HashMap<>();
			for (Map.Entry<String, JsonElement> part : pose.getAsJsonObject("parts").entrySet()) {
				List<Op> ops = new ArrayList<>();
				for (JsonElement opElement : part.getValue().getAsJsonArray()) {
					JsonArray op = opElement.getAsJsonArray();
					Kind kind = switch (op.get(0).getAsString()) {
						case "set_pos" -> Kind.SET_POS;
						case "move_pos" -> Kind.MOVE_POS;
						case "set_rot" -> Kind.SET_ROT;
						case "add_rot" -> Kind.ADD_ROT;
						default -> throw new IllegalArgumentException("unbekannte Pose-Operation " + op.get(0));
					};
					int axis = switch (op.get(1).getAsString()) {
						case "x" -> 0;
						case "y" -> 1;
						case "z" -> 2;
						default -> throw new IllegalArgumentException("unbekannte Achse " + op.get(1));
					};
					ops.add(new Op(kind, axis, op.get(2).getAsFloat()));
				}
				parts.put(part.getKey(), List.copyOf(ops));
			}
			result.add(new AbilityPose(pose.has("ease") ? pose.get("ease").getAsString() : "in_out_cubic", Map.copyOf(parts)));
		}
		return result;
	}

	/** Spielerpose berechnen (wie {@code LivingEntityRenderer.render}) und auf die Hauptknochen uebertragen. */
	static void apply(AnimationProcessor<?> bones, AbstractClientPlayerEntity player, float tickDelta, Info info) {
		if (model == null) {
			return;
		}
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
		float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw) - bodyYaw;
		float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
		float limbPos = 0.0f;
		float limbSpeed = 0.0f;
		if (!player.hasVehicle() && player.isAlive()) {
			limbSpeed = Math.min(1.0f, player.limbAnimator.getSpeed(tickDelta));
			limbPos = player.limbAnimator.getPos(tickDelta);
		}
		model.handSwingProgress = player.getHandSwingProgress(tickDelta);
		model.riding = player.hasVehicle();
		model.child = false;
		model.sneaking = player.isInSneakingPose();
		BipedEntityModel.ArmPose main = player.getMainHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
		BipedEntityModel.ArmPose off = player.getOffHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
		if (player.isUsingItem()) {
			if (player.getActiveHand() == Hand.MAIN_HAND) {
				main = BipedEntityModel.ArmPose.ITEM;
			} else {
				off = BipedEntityModel.ArmPose.ITEM;
			}
		}
		boolean rightMain = player.getMainArm() == Arm.RIGHT;
		model.rightArmPose = rightMain ? main : off;
		model.leftArmPose = rightMain ? off : main;
		model.animateModel(player, limbPos, limbSpeed, tickDelta);
		model.setAngles(player, limbPos, limbSpeed, player.age + tickDelta, headYaw, pitch);

		// Schwung-Daempfung vor der Faehigkeits-Pose (die setzt eigene Winkel)
		model.rightArm.pitch *= info.armSwing();
		model.leftArm.pitch *= info.armSwing();
		model.rightLeg.pitch *= info.legSwing();
		model.leftLeg.pitch *= info.legSwing();

		float[] root = new float[6];
		applyAbility(player, tickDelta, info, root);

		follow(bones.getBone("head"), model.head, 0.0f, 0.0f);
		follow(bones.getBone("body"), model.body, 0.0f, 0.0f);
		follow(bones.getBone("right_arm"), model.rightArm, 5.0f, 2.0f);
		follow(bones.getBone("left_arm"), model.leftArm, -5.0f, 2.0f);
		// Vierarm: unteres Armpaar ist in AE eine zweite Ebene an denselben Spielerarmen
		follow(bones.getBone("right_lower_arm"), model.rightArm, 5.0f, 2.0f);
		follow(bones.getBone("left_lower_arm"), model.leftArm, -5.0f, 2.0f);
		follow(bones.getBone("right_leg"), model.rightLeg, 2.0f, 12.0f);
		follow(bones.getBone("left_leg"), model.leftLeg, -2.0f, 12.0f);
		// Ganzkoerper (Palladium „body“): Drehung um die Fuesse; jedes Bild gesetzt, auch auf 0 (GeckoLib setzt
		// selbst gesetzte Knochen nicht zurueck)
		GeoBone rootBone = bones.getBone("root");
		if (rootBone != null) {
			rootBone.updateRotation(-root[3], -root[4], root[5]);
			rootBone.updatePosition(root[0], root[1], root[2]);
		}
	}

	/** AE-Pose der zuletzt benutzten Faehigkeit einblenden; {@code root} erhaelt Lage (0..2) und Drehung (3..5). */
	private static void applyAbility(AbstractClientPlayerEntity player, float tickDelta, Info info, float[] root) {
		if (info.abilities().isEmpty()) {
			return;
		}
		TransformationState state = TransformationManager.get(player);
		long now = player.getWorld().getTime();
		int slot = AlienBodyAnimatable.recentAbility(state, now, END);
		if (slot < 0 || slot >= info.abilities().size() || info.abilities().get(slot) == null) {
			return;
		}
		AbilityPose pose = info.abilities().get(slot);
		float age = now - state.energyStamp() + tickDelta;
		float t = age < RISE ? age / RISE : (age < HOLD ? 1.0f : 1.0f - (age - HOLD) / (END - HOLD));
		float k = ease(pose.ease(), MathHelper.clamp(t, 0.0f, 1.0f));
		if (k <= 0.0f) {
			return;
		}
		for (Map.Entry<String, List<Op>> entry : pose.parts().entrySet()) {
			ModelPart part = switch (entry.getKey()) {
				case "head" -> model.head;
				case "chest" -> model.body;
				case "right_arm" -> model.rightArm;
				case "left_arm" -> model.leftArm;
				case "right_leg" -> model.rightLeg;
				case "left_leg" -> model.leftLeg;
				default -> null;
			};
			float[] current = part == null ? new float[6]
					: new float[] {part.pivotX, part.pivotY, part.pivotZ, part.pitch, part.yaw, part.roll};
			float[] target = current.clone();
			for (Op op : entry.getValue()) {
				switch (op.kind()) {
					case SET_POS -> target[op.axis()] = op.value();
					case MOVE_POS -> target[op.axis()] += op.value();
					case SET_ROT -> target[3 + op.axis()] = op.value() * MathHelper.RADIANS_PER_DEGREE;
					case ADD_ROT -> target[3 + op.axis()] += op.value() * MathHelper.RADIANS_PER_DEGREE;
				}
			}
			float[] blended = new float[6];
			for (int i = 0; i < 6; i++) {
				blended[i] = MathHelper.lerp(k, current[i], target[i]);
			}
			if (part == null) {
				// Ganzkoerper: Lage in Pixeln (y nach oben, wie AEs moveY(-8) zum Hinknien), Drehung im Bogenmass
				System.arraycopy(blended, 0, root, 0, 6);
			} else {
				part.pivotX = blended[0];
				part.pivotY = blended[1];
				part.pivotZ = blended[2];
				part.pitch = blended[3];
				part.yaw = blended[4];
				part.roll = blended[5];
			}
		}
	}

	/** Kurven wie Palladium ({@code animate('InOutCubic', t)} usw.). */
	static float ease(String name, float t) {
		return switch (name) {
			case "in_out_cubic" -> t < 0.5f ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3) / 2.0f;
			case "out_cubic" -> 1.0f - (float) Math.pow(1.0f - t, 3);
			case "in_cubic" -> t * t * t;
			case "out_sine" -> MathHelper.sin(t * MathHelper.PI / 2.0f);
			case "in_out_sine" -> -(MathHelper.cos(MathHelper.PI * t) - 1.0f) / 2.0f;
			case "out_quad" -> 1.0f - (1.0f - t) * (1.0f - t);
			case "in_out_quad" -> t < 0.5f ? 2.0f * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 2) / 2.0f;
			default -> t;
		};
	}

	/**
	 * @param baseX Grundlage des Spielerteils (x) — Verschiebung = aktuelle Lage minus Grundlage
	 * @param baseY Grundlage (y)
	 */
	private static void follow(GeoBone bone, ModelPart part, float baseX, float baseY) {
		if (bone == null) {
			return;
		}
		RenderUtil.matchModelPartRot(part, bone);
		bone.updatePosition(part.pivotX + baseX, baseY - part.pivotY, part.pivotZ);
	}
}
