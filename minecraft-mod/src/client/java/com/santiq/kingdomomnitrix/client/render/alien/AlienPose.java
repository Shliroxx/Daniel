package com.santiq.kingdomomnitrix.client.render.alien;

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
 * <p>Positionen wie {@code GeoArmorRenderer}: Abweichung der Spielerteile von ihrer Grundlage (Schleichen senkt Kopf
 * und Arme), Drehungen mit GeckoLibs Vorzeichen ({@link RenderUtil#matchModelPartRot}).</p>
 */
final class AlienPose {
	private static PlayerEntityModel<AbstractClientPlayerEntity> model;

	private AlienPose() {
	}

	static void reload(EntityRendererFactory.Context context) {
		model = new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false);
	}

	/** Spielerpose berechnen (wie {@code LivingEntityRenderer.render}) und auf die Hauptknochen uebertragen. */
	static void apply(AnimationProcessor<?> bones, AbstractClientPlayerEntity player, float tickDelta, float armSwing, float legSwing) {
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
			BipedEntityModel.ArmPose using = BipedEntityModel.ArmPose.ITEM;
			if (player.getActiveHand() == Hand.MAIN_HAND) {
				main = using;
			} else {
				off = using;
			}
		}
		boolean rightMain = player.getMainArm() == Arm.RIGHT;
		model.rightArmPose = rightMain ? main : off;
		model.leftArmPose = rightMain ? off : main;
		model.animateModel(player, limbPos, limbSpeed, tickDelta);
		model.setAngles(player, limbPos, limbSpeed, player.age + tickDelta, headYaw, pitch);

		follow(bones.getBone("head"), model.head, 0.0f, 0.0f, 1.0f);
		follow(bones.getBone("body"), model.body, 0.0f, 0.0f, 1.0f);
		follow(bones.getBone("right_arm"), model.rightArm, 5.0f, 2.0f, armSwing);
		follow(bones.getBone("left_arm"), model.leftArm, -5.0f, 2.0f, armSwing);
		// Vierarm: unteres Armpaar ist in AE eine zweite Ebene an denselben Spielerarmen
		follow(bones.getBone("right_lower_arm"), model.rightArm, 5.0f, 2.0f, armSwing);
		follow(bones.getBone("left_lower_arm"), model.leftArm, -5.0f, 2.0f, armSwing);
		follow(bones.getBone("right_leg"), model.rightLeg, 2.0f, 12.0f, legSwing);
		follow(bones.getBone("left_leg"), model.leftLeg, -2.0f, 12.0f, legSwing);
	}

	/**
	 * @param baseX Grundlage des Spielerteils (x) — Verschiebung = aktuelle Lage minus Grundlage
	 * @param baseY Grundlage (y)
	 * @param swing Anteil der Vorwaerts-/Rueckwaertsdrehung (x), die das Alien uebernimmt
	 */
	private static void follow(GeoBone bone, ModelPart part, float baseX, float baseY, float swing) {
		if (bone == null) {
			return;
		}
		RenderUtil.matchModelPartRot(part, bone);
		bone.setRotX(-part.pitch * swing);
		bone.updatePosition(part.pivotX + baseX, baseY - part.pivotY, part.pivotZ);
	}
}
