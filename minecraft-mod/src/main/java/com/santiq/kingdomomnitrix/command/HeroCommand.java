package com.santiq.kingdomomnitrix.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.DnaSampleItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import com.santiq.kingdomomnitrix.enemy.RiftSpawner;
import com.santiq.kingdomomnitrix.magic.MagicManager;
import com.santiq.kingdomomnitrix.magic.SpellDefinition;
import com.santiq.kingdomomnitrix.magic.SpellRegistry;
import com.santiq.kingdomomnitrix.player.HeroData;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.Collection;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Entwickler- und Admin-Befehle unter {@code /hero}. Nur ab Berechtigungsstufe 2 (OP).
 * Jeder Unterbefehl wirkt auf den Ausfuehrenden oder auf einen optional angegebenen Spieler.
 *
 * <p>Weitere Unterbefehle (weapon, keyblade, quest, boss, world) kommen mit der jeweiligen Phase hinzu.</p>
 */
public final class HeroCommand {
	private static final int PERMISSION_LEVEL = 2;
	private static final String TARGET = "target";
	private static final SuggestionProvider<ServerCommandSource> SPELL_SUGGESTIONS = (ctx, builder) ->
			CommandSource.suggestIdentifiers(SpellRegistry.sortedIds(ctx.getSource().getRegistryManager()), builder);
	private static final SuggestionProvider<ServerCommandSource> ALIEN_SUGGESTIONS = (ctx, builder) ->
			CommandSource.suggestIdentifiers(AlienRegistry.sortedIds(ctx.getSource().getRegistryManager()), builder);

	@FunctionalInterface
	private interface PlayerAction {
		int run(CommandContext<ServerCommandSource> context, ServerPlayerEntity target) throws CommandSyntaxException;
	}

	private HeroCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("hero")
				.requires(source -> source.hasPermissionLevel(PERMISSION_LEVEL))
				.then(targeted(CommandManager.literal("debug"), HeroCommand::debug))
				.then(targeted(CommandManager.literal("reset"), (ctx, target) ->
						change(ctx, target, data -> HeroData.DEFAULT, "commands.kingdomomnitrix.reset")))
				.then(CommandManager.literal("bolts")
						.then(CommandManager.literal("add")
								.then(targeted(CommandManager.argument("amount", IntegerArgumentType.integer(-HeroData.MAX_BOLTS, HeroData.MAX_BOLTS)),
										(ctx, target) -> change(ctx, target,
												data -> data.addBolts(IntegerArgumentType.getInteger(ctx, "amount")),
												"commands.kingdomomnitrix.bolts"))))
						.then(CommandManager.literal("set")
								.then(targeted(CommandManager.argument("amount", IntegerArgumentType.integer(0, HeroData.MAX_BOLTS)),
										(ctx, target) -> change(ctx, target,
												data -> data.withBolts(IntegerArgumentType.getInteger(ctx, "amount")),
												"commands.kingdomomnitrix.bolts")))))
				.then(CommandManager.literal("level")
						.then(CommandManager.literal("set")
								.then(targeted(CommandManager.argument("level", IntegerArgumentType.integer(HeroData.MIN_LEVEL, HeroData.MAX_LEVEL)),
										(ctx, target) -> change(ctx, target,
												data -> data.withLevel(IntegerArgumentType.getInteger(ctx, "level")),
												"commands.kingdomomnitrix.level")))))
				.then(CommandManager.literal("xp")
						.then(CommandManager.literal("add")
								.then(targeted(CommandManager.argument("amount", IntegerArgumentType.integer(1)),
										(ctx, target) -> change(ctx, target,
												data -> data.addExperience(IntegerArgumentType.getInteger(ctx, "amount")),
												"commands.kingdomomnitrix.level")))))
				.then(CommandManager.literal("alien")
						.then(CommandManager.literal("unlock")
								.then(targeted(CommandManager.argument("alien", IdentifierArgumentType.identifier()).suggests(ALIEN_SUGGESTIONS),
										(ctx, target) -> change(ctx, target,
												data -> data.unlockAlien(IdentifierArgumentType.getIdentifier(ctx, "alien")),
												"commands.kingdomomnitrix.alien"))))
						.then(CommandManager.literal("lock")
								.then(targeted(CommandManager.argument("alien", IdentifierArgumentType.identifier()).suggests(ALIEN_SUGGESTIONS),
										(ctx, target) -> change(ctx, target,
												data -> data.lockAlien(IdentifierArgumentType.getIdentifier(ctx, "alien")),
												"commands.kingdomomnitrix.alien")))))
				.then(CommandManager.literal("transform")
						.then(targeted(CommandManager.argument("alien", IdentifierArgumentType.identifier()).suggests(ALIEN_SUGGESTIONS),
								(ctx, target) -> transformResult(ctx, target, TransformationManager.transform(target,
										IdentifierArgumentType.getIdentifier(ctx, "alien"), true)))))
				.then(targeted(CommandManager.literal("revert"),
						(ctx, target) -> transformResult(ctx, target, TransformationManager.revert(target, false))))
				.then(CommandManager.literal("dna")
						.then(targeted(CommandManager.argument("alien", IdentifierArgumentType.identifier()).suggests(ALIEN_SUGGESTIONS),
								(ctx, target) -> giveDna(ctx, target, IdentifierArgumentType.getIdentifier(ctx, "alien")))))
				.then(CommandManager.literal("spell")
						.then(CommandManager.argument("spell", IdentifierArgumentType.identifier()).suggests(SPELL_SUGGESTIONS)
								.then(targeted(CommandManager.argument("level", IntegerArgumentType.integer(1, 10)),
										(ctx, target) -> spellLevel(ctx, target, IdentifierArgumentType.getIdentifier(ctx, "spell"),
												IntegerArgumentType.getInteger(ctx, "level"))))))
				.then(targeted(CommandManager.literal("mp"), (ctx, target) -> {
					MagicManager.refill(target);
					ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.mp", target.getDisplayName()), true);
					return 1;
				}))
				.then(CommandManager.literal("rift")
						.executes(ctx -> openRift(ctx, Optional.empty()))
						.then(CommandManager.argument("rift", IdentifierArgumentType.identifier())
								.executes(ctx -> openRift(ctx, Optional.of(IdentifierArgumentType.getIdentifier(ctx, "rift"))))))
				.then(CommandManager.literal("flag")
						.then(CommandManager.literal("set")
								.then(targeted(CommandManager.argument("flag", StringArgumentType.word()),
										(ctx, target) -> change(ctx, target,
												data -> data.withFlag(StringArgumentType.getString(ctx, "flag"), true),
												"commands.kingdomomnitrix.flag"))))
						.then(CommandManager.literal("clear")
								.then(targeted(CommandManager.argument("flag", StringArgumentType.word()),
										(ctx, target) -> change(ctx, target,
												data -> data.withFlag(StringArgumentType.getString(ctx, "flag"), false),
												"commands.kingdomomnitrix.flag"))))));
	}

	/** Haengt an einen Knoten zwei Ausfuehrungen: fuer sich selbst und fuer {@code <target>}. */
	private static <T extends ArgumentBuilder<ServerCommandSource, T>> T targeted(T builder, PlayerAction action) {
		return builder
				.executes(ctx -> action.run(ctx, ctx.getSource().getPlayerOrThrow()))
				.then(CommandManager.argument(TARGET, EntityArgumentType.player())
						.executes(ctx -> action.run(ctx, EntityArgumentType.getPlayer(ctx, TARGET))));
	}

	private static int change(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target,
			UnaryOperator<HeroData> operation, String feedbackKey) {
		HeroData after = HeroDataAccess.update(target, operation);
		ctx.getSource().sendFeedback(() -> Text.translatable(feedbackKey, target.getDisplayName(),
				after.level(), after.bolts(), after.unlockedAliens().size(), after.storyFlags().size()), true);
		return 1;
	}

	private static int transformResult(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target,
			TransformationManager.Result result) {
		if (result == TransformationManager.Result.SUCCESS) {
			ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.transform.success", target.getDisplayName()), true);
			return 1;
		}
		ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.transform.failed",
				target.getDisplayName(), result.name().toLowerCase(java.util.Locale.ROOT)));
		return 0;
	}

	private static int spellLevel(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target, Identifier spellId, int level) {
		if (!MagicManager.setLevel(target, spellId, level)) {
			ctx.getSource().sendError(Text.literal("Unbekannter Zauber: " + spellId));
			return 0;
		}
		int actual = MagicManager.get(target).level(spellId);
		ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.spell", target.getDisplayName(),
				Text.translatable(SpellDefinition.translationKey(spellId, actual)), actual), true);
		return 1;
	}

	private static int openRift(CommandContext<ServerCommandSource> ctx, Optional<Identifier> riftId) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		if (RiftSpawner.openNear(player.getServerWorld(), player, riftId).isEmpty()) {
			ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.rift.failed"));
			return 0;
		}
		return 1;
	}

	private static int giveDna(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target, Identifier alienId) {
		if (AlienRegistry.get(ctx.getSource().getRegistryManager(), alienId).isEmpty()) {
			ctx.getSource().sendError(Text.translatable("message.kingdomomnitrix.unknown_alien"));
			return 0;
		}
		ItemStack stack = DnaSampleItem.create(alienId);
		if (!target.getInventory().insertStack(stack)) {
			target.dropItem(stack, false);
		}
		ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.dna", target.getDisplayName(),
				TransformationManager.alienName(alienId)), true);
		return 1;
	}

	private static int debug(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
		HeroData data = HeroDataAccess.get(target);
		String nextXp = data.isMaxLevel() ? "MAX" : String.valueOf(HeroData.experienceToNext(data.level()));
		ServerCommandSource source = ctx.getSource();
		source.sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.debug.header", target.getDisplayName())
				.formatted(Formatting.GOLD), false);
		source.sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.debug.progress",
				data.level(), data.experience(), nextXp, data.bolts()), false);
		source.sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.debug.aliens", joinIds(data.unlockedAliens())), false);
		source.sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.debug.flags", joinSorted(data.storyFlags())), false);
		TransformationState state = TransformationManager.get(target);
		long now = target.getWorld().getTime();
		source.sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.debug.omnitrix",
				state.activeAlien().map(Identifier::toString).orElse("-"),
				state.remainingTicks(now) / 20, state.rechargeRemaining(now) / 20), false);
		return 1;
	}

	private static String joinIds(Collection<Identifier> ids) {
		return ids.isEmpty() ? "-" : ids.stream().map(Identifier::toString).sorted().collect(Collectors.joining(", "));
	}

	private static String joinSorted(Collection<String> values) {
		return values.isEmpty() ? "-" : values.stream().sorted().collect(Collectors.joining(", "));
	}
}
