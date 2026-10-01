package com.santiq.kingdomomnitrix.client.combat;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.networking.CombatAnimationPayload;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/**
 * Spielt Kampfanimationen (playerAnimator) fuer alle Spieler ab, wenn der Server eine Kampfaktion meldet.
 * Animationen: {@code assets/kingdomomnitrix/player_animations/combat.json}. Fehlt eine Animation,
 * faellt die Darstellung auf den normalen Armschwung zurueck.
 */
public final class CombatAnimations {
	private static final Identifier LAYER_ID = KingdomOmnitrix.id("combat");
	private static final int LAYER_PRIORITY = 1000;
	private static final int FADE_TICKS = 2;
	private static final String STOP_GUARD = "guard_end";

	private CombatAnimations() {
	}

	public static void register() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, LAYER_PRIORITY, player -> new ModifierLayer<>());
		ClientPlayNetworking.registerGlobalReceiver(CombatAnimationPayload.ID, (payload, context) ->
				play(context.client(), payload.entityId(), payload.animation()));
	}

	@SuppressWarnings("unchecked")
	private static void play(MinecraftClient client, int entityId, String animation) {
		if (client.world == null) {
			return;
		}
		Entity entity = client.world.getEntityById(entityId);
		if (!(entity instanceof AbstractClientPlayerEntity player)) {
			return;
		}
		IAnimation stored = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
		if (!(stored instanceof ModifierLayer<?> rawLayer)) {
			fallback(player, animation);
			return;
		}
		ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) rawLayer;
		AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE);
		if (STOP_GUARD.equals(animation)) {
			layer.replaceAnimationWithFade(fade, null);
			return;
		}
		var playable = PlayerAnimationRegistry.getAnimationOptional(KingdomOmnitrix.id(animation));
		if (playable.isEmpty()) {
			fallback(player, animation);
			return;
		}
		layer.replaceAnimationWithFade(fade, playable.get().playAnimation());
	}

	private static void fallback(AbstractClientPlayerEntity player, String animation) {
		if (animation.startsWith("combo_") || animation.startsWith("air_") || animation.equals("heavy")) {
			player.swingHand(Hand.MAIN_HAND);
		}
	}
}
