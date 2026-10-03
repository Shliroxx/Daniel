package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.alien.AlienUniforms;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationManager.Result;
import com.santiq.kingdomomnitrix.combat.CombatManager;
import com.santiq.kingdomomnitrix.arena.ArenaManager;
import com.santiq.kingdomomnitrix.gadget.GadgetManager;
import com.santiq.kingdomomnitrix.quest.QuestManager;
import com.santiq.kingdomomnitrix.item.CommandItems;
import com.santiq.kingdomomnitrix.magic.MagicManager;
import com.santiq.kingdomomnitrix.progression.ProgressionManager;
import com.santiq.kingdomomnitrix.weapon.TerminalService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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
		PayloadTypeRegistry.playS2C().register(OmnitrixCuePayload.ID, OmnitrixCuePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DamageFeedbackPayload.ID, DamageFeedbackPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(AlienMeterPayload.ID, AlienMeterPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OmnitrixHoloPayload.ID, OmnitrixHoloPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(TransformRequestPayload.ID, TransformRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FavoritePayload.ID, FavoritePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(OmnitrixPhasePayload.ID, OmnitrixPhasePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SetUniformPayload.ID, SetUniformPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OmnitrixPhaseSyncPayload.ID, OmnitrixPhaseSyncPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RevertRequestPayload.ID, RevertRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AbilityRequestPayload.ID, AbilityRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(OmnitrixCodePayload.ID, OmnitrixCodePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ComboAttackPayload.ID, ComboAttackPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DodgePayload.ID, DodgePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GuardPayload.ID, GuardPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(LockOnPayload.ID, LockOnPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CombatAnimationPayload.ID, CombatAnimationPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SelectSpellPayload.ID, SelectSpellPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenTerminalPayload.ID, OpenTerminalPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenCalibrationPayload.ID, OpenCalibrationPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(TerminalActionPayload.ID, TerminalActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CalibrationActionPayload.ID, CalibrationActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GadgetActionPayload.ID, GadgetActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenQuestBookPayload.ID, OpenQuestBookPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenNpcDialogPayload.ID, OpenNpcDialogPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenArenaPayload.ID, OpenArenaPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ArenaStartPayload.ID, ArenaStartPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ToggleHeroAbilityPayload.ID, ToggleHeroAbilityPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CommandActionPayload.ID, CommandActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PartySyncPayload.ID, PartySyncPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(CommandActionPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!canAct(player)) {
				return;
			}
			String key = null;
			if (payload.action() == CommandActionPayload.CAST_SPELL) {
				MagicManager.select(player, payload.target());
				key = switch (MagicManager.castSelected(player)) {
					case NO_KEYBLADE -> "message.kingdomomnitrix.command_need_keyblade";
					case UNKNOWN_SPELL, NO_SPELLS -> "message.kingdomomnitrix.command_unknown";
					default -> null; // MP, Ladezeit und Abklingzeit meldet der MagicManager selbst
				};
			} else if (payload.action() == CommandActionPayload.USE_ITEM) {
				key = switch (CommandItems.use(player, payload.target())) {
					case MISSING -> "message.kingdomomnitrix.command_item_missing";
					case NOT_ALLOWED -> "message.kingdomomnitrix.command_unknown";
					default -> null;
				};
			}
			if (key != null) {
				player.sendMessage(Text.translatable(key).formatted(Formatting.RED), true);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(ToggleHeroAbilityPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!player.isAlive()) {
				return;
			}
			String key = switch (ProgressionManager.toggle(player, payload.ability())) {
				case LOCKED -> "message.kingdomomnitrix.ability_locked";
				case NO_AP -> "message.kingdomomnitrix.ability_no_ap";
				case UNKNOWN -> "message.kingdomomnitrix.ability_unknown";
				default -> null;
			};
			if (key != null) {
				player.sendMessage(Text.translatable(key).formatted(Formatting.RED), true);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(ArenaStartPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!canAct(player)) {
				return;
			}
			ArenaManager.Result result = ArenaManager.start(player, payload.terminal(), payload.challenge());
			String key = switch (result) {
				case RUNNING -> "arena.kingdomomnitrix.busy";
				case LEVEL_TOO_LOW -> "arena.kingdomomnitrix.level_too_low";
				case NO_TERMINAL, NOT_IN_ARENA -> "arena.kingdomomnitrix.too_far";
				case UNKNOWN -> "message.kingdomomnitrix.quest_unknown";
				default -> null;
			};
			if (key != null) {
				player.sendMessage(Text.translatable(key).formatted(Formatting.RED), true);
			}
		});
		PayloadTypeRegistry.playC2S().register(QuestActionPayload.ID, QuestActionPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(QuestActionPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!canAct(player)) {
				return;
			}
			QuestManager.Result result = switch (payload.action()) {
				case QuestActionPayload.ACCEPT -> QuestManager.accept(player, payload.quest());
				case QuestActionPayload.ABANDON -> QuestManager.abandon(player, payload.quest());
				case QuestActionPayload.TURN_IN -> QuestManager.turnIn(player, payload.quest());
				default -> QuestManager.Result.UNKNOWN;
			};
			reportQuest(player, result);
		});
		PayloadTypeRegistry.playC2S().register(SwingshotPayload.ID, SwingshotPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SwingshotStatePayload.ID, SwingshotStatePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(GadgetActionPayload.ID, (payload, context) -> {
			GadgetManager.Action[] actions = GadgetManager.Action.values();
			if (payload.action() >= 0 && payload.action() < actions.length && canAct(context.player())) {
				GadgetManager.handle(context.player(), actions[payload.action()]);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SwingshotPayload.ID, (payload, context) -> {
			if (payload.active() && canAct(context.player())) {
				GadgetManager.startSwing(context.player(), payload.anchor());
			} else if (!payload.active()) {
				GadgetManager.stopSwing(context.player(), true);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(TerminalActionPayload.ID, (payload, context) -> {
			TerminalService.Action[] actions = TerminalService.Action.values();
			if (payload.action() >= 0 && payload.action() < actions.length && canAct(context.player())) {
				TerminalService.handle(context.player(), actions[payload.action()], payload.weapon(), payload.terminal());
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(CalibrationActionPayload.ID, (payload, context) ->
				com.santiq.kingdomomnitrix.omnitrix.OmnitrixCalibrations.handle(context.player(), payload.bench(), payload.module(), payload.value()));

		ServerPlayNetworking.registerGlobalReceiver(SelectSpellPayload.ID, (payload, context) ->
				MagicManager.select(context.player(), payload.spell()));

		ServerPlayNetworking.registerGlobalReceiver(ComboAttackPayload.ID, (payload, context) ->
				CombatManager.attack(context.player(), payload.heavy() ? CombatManager.Attack.HEAVY : CombatManager.Attack.LIGHT));
		ServerPlayNetworking.registerGlobalReceiver(DodgePayload.ID, (payload, context) ->
				CombatManager.dodge(context.player(), payload.directionX(), payload.directionZ()));
		ServerPlayNetworking.registerGlobalReceiver(GuardPayload.ID, (payload, context) ->
				CombatManager.setGuard(context.player(), payload.active()));
		ServerPlayNetworking.registerGlobalReceiver(LockOnPayload.ID, (payload, context) ->
				CombatManager.setLockTarget(context.player(), payload.entityId()));

		ServerPlayNetworking.registerGlobalReceiver(TransformRequestPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!canAct(player)) {
				return;
			}
			// verwandelt; als Alien: Schnellwechsel in ein anderes Alien bzw. zurueck bei gleichem Alien
			if (!TransformationManager.get(player).isTransformed()) {
				TransformationManager.select(player, payload.alien());
			}
			report(player, TransformationManager.quickChange(player, payload.alien()));
		});
		ServerPlayNetworking.registerGlobalReceiver(FavoritePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (payload.alien().isPresent()) {
				com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore.toggleFavorite(player, payload.alien().get());
			} else {
				com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore.setActiveSet(player, payload.set());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(OmnitrixPhasePayload.ID, (payload, context) -> {
			// nur sichtbare Zustaende weitergeben; reine Darstellung, keine Spielwirkung
			ServerPlayerEntity player = context.player();
			if (!payload.phase().isShared()) {
				return;
			}
			OmnitrixPhaseSyncPayload sync = new OmnitrixPhaseSyncPayload(player.getId(), payload.phase());
			for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
				if (watcher != player) {
					ServerPlayNetworking.send(watcher, sync);
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SetUniformPayload.ID, (payload, context) -> {
			// nur fuer freigeschaltete Aliens; die Uniform ist reine Optik
			ServerPlayerEntity player = context.player();
			if (HeroDataAccess.get(player).hasAlien(payload.alien())) {
				AlienUniforms.set(player, payload.alien(), payload.uniform());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(RevertRequestPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (canAct(player)) {
				report(player, TransformationManager.revert(player, false));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(OmnitrixCodePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			String code = payload.code();
			// nur Ziffern in erlaubter Laenge; alles andere zaehlt wie ein falscher Code (kein Umgehen der Sperre)
			boolean wellFormed = code.length() <= com.santiq.kingdomomnitrix.omnitrix.OmnitrixCode.MAX_LENGTH
					&& code.chars().allMatch(Character::isDigit);
			if (canAct(player)) {
				com.santiq.kingdomomnitrix.omnitrix.OmnitrixCodes.enter(player, wellFormed ? code : "");
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

	private static void reportQuest(ServerPlayerEntity player, QuestManager.Result result) {
		String key = switch (result) {
			case LOCKED -> "message.kingdomomnitrix.quest_locked";
			case TOO_MANY -> "message.kingdomomnitrix.quest_too_many";
			case NOT_READY -> "message.kingdomomnitrix.quest_not_ready";
			case UNKNOWN -> "message.kingdomomnitrix.quest_unknown";
			default -> null;
		};
		if (key != null) {
			player.sendMessage(Text.translatable(key, QuestManager.MAX_ACTIVE).formatted(Formatting.RED), true);
		}
	}

	/** Rueckmeldung in der Actionbar fuer Ergebnisse, die der Spieler sonst nicht bemerken wuerde. */
	private static void report(ServerPlayerEntity player, Result result) {
		long now = player.getWorld().getTime();
		// Geraete-Zustaende ueber Omnitrix OS (Hologramm), der Rest bleibt in der Aktionsleiste
		if (result == Result.RECHARGING) {
			com.santiq.kingdomomnitrix.omnitrix.OmnitrixOs.send(player, com.santiq.kingdomomnitrix.omnitrix.OmnitrixOs.Event.RECHARGING,
					Text.translatable("holo.kingdomomnitrix.os.recharge_in", (TransformationManager.get(player).rechargeRemaining(now) + 19) / 20));
			return;
		}
		if (result == Result.NO_SPACE) {
			com.santiq.kingdomomnitrix.omnitrix.OmnitrixOs.send(player, com.santiq.kingdomomnitrix.omnitrix.OmnitrixOs.Event.NEED_SPACE,
					Text.translatable("message.kingdomomnitrix.need_space"));
			return;
		}
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
