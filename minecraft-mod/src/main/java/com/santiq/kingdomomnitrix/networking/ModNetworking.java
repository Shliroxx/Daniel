package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationManager.Result;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Registriert alle Pakete und die serverseitigen Empfaenger.
 * Client-Pakete sind reine Absichten; jede Pruefung passiert im {@link TransformationManager}.
 */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(OpenOmnitrixPayload.ID, OpenOmnitrixPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(TransformRequestPayload.ID, TransformRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RevertRequestPayload.ID, RevertRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AbilityRequestPayload.ID, AbilityRequestPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(TransformRequestPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!canAct(player)) {
				return;
			}
			TransformationManager.select(player, payload.alien());
			report(player, TransformationManager.transform(player, payload.alien(), false));
		});
		ServerPlayNetworking.registerGlobalReceiver(RevertRequestPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (canAct(player)) {
				report(player, TransformationManager.revert(player, false));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(AbilityRequestPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (canAct(player) && payload.slot() >= 0 && payload.slot() < AlienDefinition.MAX_ABILITIES) {
				report(player, TransformationManager.useAbility(player, payload.slot()));
			}
		});
	}

	private static boolean canAct(ServerPlayerEntity player) {
		return player.isAlive() && !player.isSpectator();
	}

	/** Rueckmeldung in der Actionbar fuer Ergebnisse, die der Spieler sonst nicht bemerken wuerde. */
	private static void report(ServerPlayerEntity player, Result result) {
		long now = player.getWorld().getTime();
		Text message = switch (result) {
			case NO_OMNITRIX -> Text.translatable("message.kingdomomnitrix.need_omnitrix");
			case LOCKED -> Text.translatable("message.kingdomomnitrix.locked");
			case UNKNOWN_ALIEN -> Text.translatable("message.kingdomomnitrix.unknown_alien");
			case RECHARGING -> Text.translatable("message.kingdomomnitrix.omnitrix_recharging",
					(TransformationManager.get(player).rechargeRemaining(now) + 19) / 20);
			case ALREADY_TRANSFORMED -> Text.translatable("message.kingdomomnitrix.already_transformed");
			case NO_SPACE -> Text.translatable("message.kingdomomnitrix.need_space");
			case NOT_TRANSFORMED -> Text.translatable("message.kingdomomnitrix.not_transformed");
			default -> null;
		};
		if (message != null) {
			player.sendMessage(message.copy().formatted(Formatting.RED), true);
		}
	}
}
