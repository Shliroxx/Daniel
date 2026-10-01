package com.santiq.kingdomomnitrix.client.combat;

import com.santiq.kingdomomnitrix.networking.CombatAnimationPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/**
 * Spielt Kampfanimationen anderer und eigener Spieler ab. Phase 4a: Armschwung als Rueckfall;
 * Phase 4b ersetzt das durch playerAnimator-Animationen.
 */
public final class CombatAnimations {
	private CombatAnimations() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CombatAnimationPayload.ID, (payload, context) ->
				play(context.client(), payload.entityId(), payload.animation()));
	}

	private static void play(MinecraftClient client, int entityId, String animation) {
		if (client.world == null) {
			return;
		}
		Entity entity = client.world.getEntityById(entityId);
		if (!(entity instanceof PlayerEntity player)) {
			return;
		}
		if (animation.startsWith("combo_") || animation.startsWith("air_") || animation.equals("heavy")) {
			player.swingHand(Hand.MAIN_HAND);
		}
	}
}
