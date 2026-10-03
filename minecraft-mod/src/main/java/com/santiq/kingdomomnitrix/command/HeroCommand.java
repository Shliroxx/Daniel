package com.santiq.kingdomomnitrix.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
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
import com.santiq.kingdomomnitrix.progression.AlienMastery;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import com.santiq.kingdomomnitrix.progression.HeroAbilityDefinition;
import com.santiq.kingdomomnitrix.progression.HeroAbilityRegistry;
import com.santiq.kingdomomnitrix.progression.ProgressionManager;
import com.santiq.kingdomomnitrix.quest.QuestManager;
import com.santiq.kingdomomnitrix.npc.NpcDefinition;
import com.santiq.kingdomomnitrix.npc.NpcRegistry;
import com.santiq.kingdomomnitrix.npc.NpcSpawnItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import com.santiq.kingdomomnitrix.world.TraverseTown;
import com.santiq.kingdomomnitrix.quest.QuestRegistry;
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
	private static final SuggestionProvider<ServerCommandSource> EVENT_SUGGESTIONS = (ctx, builder) ->
			net.minecraft.command.CommandSource.suggestIdentifiers(
					com.santiq.kingdomomnitrix.worldevent.WorldEventRegistry.ids(ctx.getSource().getRegistryManager()), builder);
	private static final SuggestionProvider<ServerCommandSource> SPELL_SUGGESTIONS = (ctx, builder) ->
			CommandSource.suggestIdentifiers(SpellRegistry.sortedIds(ctx.getSource().getRegistryManager()), builder);
	private static final SuggestionProvider<ServerCommandSource> QUEST_SUGGESTIONS = (ctx, builder) ->
			CommandSource.suggestIdentifiers(QuestRegistry.sortedIds(ctx.getSource().getRegistryManager()), builder);
	private static final SuggestionProvider<ServerCommandSource> NPC_SUGGESTIONS = (ctx, builder) ->
			CommandSource.suggestIdentifiers(NpcRegistry.sortedIds(ctx.getSource().getRegistryManager()), builder);
	private static final SuggestionProvider<ServerCommandSource> HERO_ABILITY_SUGGESTIONS = (ctx, builder) ->
			CommandSource.suggestIdentifiers(HeroAbilityRegistry.sortedIds(ctx.getSource().getRegistryManager()), builder);
	private static final SuggestionProvider<ServerCommandSource> ALIEN_SUGGESTIONS = (ctx, builder) ->
			CommandSource.suggestIdentifiers(AlienRegistry.sortedIds(ctx.getSource().getRegistryManager()), builder);

	private static final com.mojang.brigadier.exceptions.DynamicCommandExceptionType UNKNOWN_ALIEN =
			new com.mojang.brigadier.exceptions.DynamicCommandExceptionType(id -> Text.translatable("commands.kingdomomnitrix.unknown_alien", id));

	/**
	 * Alien-Id aus dem Argument {@code alien}. Ohne Namensraum ("xlr8") liest Minecraft {@code minecraft:xlr8} — dann gilt
	 * der Mod-Namensraum. Unbekannte Aliens werden abgelehnt, statt als ungueltige Freischaltung gespeichert zu werden.
	 */
	static Identifier alienId(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		Identifier raw = IdentifierArgumentType.getIdentifier(ctx, "alien");
		var registries = ctx.getSource().getRegistryManager();
		if (AlienRegistry.get(registries, raw).isPresent()) {
			return raw;
		}
		Identifier own = com.santiq.kingdomomnitrix.KingdomOmnitrix.id(raw.getPath());
		if (raw.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) && AlienRegistry.get(registries, own).isPresent()) {
			return own;
		}
		throw UNKNOWN_ALIEN.create(raw.toString());
	}

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
										(ctx, target) -> {
											// wie im Spiel: Aufstiegsmeldung, Heilung, neue Faehigkeiten
											HeroData after = HeroDataAccess.grantExperience(target, IntegerArgumentType.getInteger(ctx, "amount"));
											ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.level",
													target.getDisplayName(), after.level()), true);
											return 1;
										}))))
				.then(CommandManager.literal("alien")
						.then(CommandManager.literal("unlock")
								.then(targeted(CommandManager.argument("alien", IdentifierArgumentType.identifier()).suggests(ALIEN_SUGGESTIONS),
										(ctx, target) -> {
											Identifier alien = alienId(ctx);
											return change(ctx, target, data -> data.unlockAlien(alien), "commands.kingdomomnitrix.alien");
										})))
						.then(CommandManager.literal("lock")
								.then(targeted(CommandManager.argument("alien", IdentifierArgumentType.identifier()).suggests(ALIEN_SUGGESTIONS),
										(ctx, target) -> {
											Identifier alien = alienId(ctx);
											return change(ctx, target, data -> data.lockAlien(alien), "commands.kingdomomnitrix.alien");
										}))))
				.then(CommandManager.literal("transform")
						.then(targeted(CommandManager.argument("alien", IdentifierArgumentType.identifier()).suggests(ALIEN_SUGGESTIONS),
								(ctx, target) -> transformResult(ctx, target, TransformationManager.transform(target,
										alienId(ctx), true)))))
				.then(targeted(CommandManager.literal("revert"),
						(ctx, target) -> transformResult(ctx, target, TransformationManager.revert(target, false))))
				.then(CommandManager.literal("omnitrix")
						.then(targeted(CommandManager.literal("status"), HeroCommand::omnitrixStatus))
						.then(CommandManager.literal("use")
								.then(targeted(CommandManager.argument("alien", IdentifierArgumentType.identifier()).suggests(ALIEN_SUGGESTIONS),
										(ctx, target) -> transformResult(ctx, target, TransformationManager.transform(target,
												alienId(ctx), false)))))
						// Code am Omnitrix eingeben (wie die Tastatur)
						.then(CommandManager.literal("code")
								.then(targeted(CommandManager.argument("code", com.mojang.brigadier.arguments.StringArgumentType.word()), (ctx, target) -> {
									com.santiq.kingdomomnitrix.omnitrix.OmnitrixCodes.enter(target,
											com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "code"));
									return 1;
								})))
						// Faehigkeit direkt ausloesen (Test/Admin): meldet das genaue Ergebnis
						.then(CommandManager.literal("ability")
								.then(targeted(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6)), (ctx, target) -> {
									int slot = IntegerArgumentType.getInteger(ctx, "slot");
									TransformationManager.Result result = TransformationManager.useAbility(target, slot - 1);
									ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.ability_result",
											target.getDisplayName(), slot, result.name()), false);
									return result == TransformationManager.Result.SUCCESS ? 1 : 0;
								})))
						.then(CommandManager.literal("heat")
								.then(targeted(CommandManager.argument("value", FloatArgumentType.floatArg(0.0f, 1.0f)), (ctx, target) -> {
									OmnitrixCore.setHeat(target, FloatArgumentType.getFloat(ctx, "value"));
									return omnitrixStatus(ctx, target);
								})))
						.then(CommandManager.literal("lock")
								.then(targeted(CommandManager.argument("seconds", IntegerArgumentType.integer(0, 3600)), (ctx, target) -> {
									OmnitrixCore.lock(target, IntegerArgumentType.getInteger(ctx, "seconds") * 20L);
									return omnitrixStatus(ctx, target);
								})))
						.then(CommandManager.literal("master_control")
								.then(targeted(CommandManager.argument("enabled", BoolArgumentType.bool()), (ctx, target) -> {
									OmnitrixCore.setMasterControl(target, BoolArgumentType.getBool(ctx, "enabled"));
									return omnitrixStatus(ctx, target);
								})))
						.then(CommandManager.literal("profile")
								.then(targeted(CommandManager.argument("profile", IdentifierArgumentType.identifier())
										.suggests((ctx, builder) -> CommandSource.suggestIdentifiers(ctx.getSource().getRegistryManager()
												.getOptional(OmnitrixCore.PROFILES).map(r -> r.getIds()).orElse(java.util.Set.of()), builder)),
										(ctx, target) -> {
											Identifier id = IdentifierArgumentType.getIdentifier(ctx, "profile");
											if (!OmnitrixCore.hasProfile(ctx.getSource().getRegistryManager(), id)) {
												ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.omnitrix.no_profile", id.toString()));
												return 0;
											}
											OmnitrixCore.setProfile(target, id);
											return omnitrixStatus(ctx, target);
										}))))
				.then(CommandManager.literal("dna")
						.then(targeted(CommandManager.argument("alien", IdentifierArgumentType.identifier()).suggests(ALIEN_SUGGESTIONS),
								(ctx, target) -> giveDna(ctx, target, alienId(ctx)))))
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
				.then(CommandManager.literal("dungeon")
						.then(targeted(CommandManager.literal("tp"), (ctx, target) -> {
							ServerWorld town = ctx.getSource().getServer().getWorld(TraverseTown.WORLD);
							if (town == null) {
								ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.world_missing", TraverseTown.WORLD.getValue().toString()));
								return 0;
							}
							TraverseTown.ensure(town);
							BlockPos origin = com.santiq.kingdomomnitrix.dungeon.WaterwayDungeon.origin(town).orElseThrow();
							BlockPos up = com.santiq.kingdomomnitrix.dungeon.WaterwayDungeon.surfaceEntrance(origin);
							target.teleport(town, up.getX() + 0.5, up.getY(), up.getZ() + 0.5, 180.0f, 0.0f);
							return 1;
						}))
						.then(CommandManager.literal("reset").executes(ctx -> {
							ServerWorld town = ctx.getSource().getServer().getWorld(TraverseTown.WORLD);
							if (town == null || !com.santiq.kingdomomnitrix.dungeon.WaterwayDungeon.resetNow(town)) {
								ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.dungeon.missing"));
								return 0;
							}
							ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.dungeon.reset"), true);
							return 1;
						}))
						.then(CommandManager.literal("status").executes(ctx -> {
							ServerWorld town = ctx.getSource().getServer().getWorld(TraverseTown.WORLD);
							var origin = town == null ? java.util.Optional.<BlockPos>empty() : com.santiq.kingdomomnitrix.dungeon.WaterwayDungeon.origin(town);
							if (origin.isEmpty()) {
								ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.dungeon.missing"));
								return 0;
							}
							ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.dungeon.status",
									com.santiq.kingdomomnitrix.dungeon.WaterwayDungeon.stage().name().toLowerCase(java.util.Locale.ROOT),
									origin.get().getX(), origin.get().getY(), origin.get().getZ()), false);
							return 1;
						})))
				.then(CommandManager.literal("event")
						.then(CommandManager.literal("start")
								.executes(ctx -> startEvent(ctx, Optional.empty()))
								.then(CommandManager.argument("event", IdentifierArgumentType.identifier()).suggests(EVENT_SUGGESTIONS)
										.executes(ctx -> startEvent(ctx, Optional.of(IdentifierArgumentType.getIdentifier(ctx, "event"))))))
						.then(CommandManager.literal("stop").executes(HeroCommand::stopEvent))
						.then(CommandManager.literal("status").executes(HeroCommand::eventStatus)))
				.then(CommandManager.literal("rift")
						.executes(ctx -> openRift(ctx, Optional.empty()))
						.then(CommandManager.argument("rift", IdentifierArgumentType.identifier())
								.executes(ctx -> openRift(ctx, Optional.of(IdentifierArgumentType.getIdentifier(ctx, "rift"))))))
				.then(CommandManager.literal("world")
						.then(targeted(CommandManager.literal("traverse_town"), (ctx, target) -> {
							ServerWorld town = ctx.getSource().getServer().getWorld(TraverseTown.WORLD);
							if (town == null) {
								ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.world_missing", TraverseTown.WORLD.getValue().toString()));
								return 0;
							}
							BlockPos center = TraverseTown.ensure(town);
							target.teleport(town, center.getX() + 0.5, center.getY(), center.getZ() + 3.5, 180.0f, 0.0f);
							return 1;
						}).then(CommandManager.literal("rebuild").executes(ctx -> {
							// Neubau nach einem Update der Stadt: ueberschreibt das Stadtgebiet (auch Spielerbauten)
							ServerWorld town = ctx.getSource().getServer().getWorld(TraverseTown.WORLD);
							BlockPos center = town != null ? TraverseTown.rebuild(town) : null;
							if (center == null) {
								ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.town.not_built"));
								return 0;
							}
							ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.town.rebuilt",
									center.getX(), center.getY(), center.getZ()), true);
							return 1;
						}))))
				.then(CommandManager.literal("npc")
						.then(CommandManager.literal("spawn")
								.then(CommandManager.argument("npc", IdentifierArgumentType.identifier()).suggests(NPC_SUGGESTIONS)
										.executes(HeroCommand::spawnNpc))))
				.then(CommandManager.literal("quest")
						.then(CommandManager.literal("start")
								.then(targeted(CommandManager.argument("quest", IdentifierArgumentType.identifier()).suggests(QUEST_SUGGESTIONS),
										(ctx, target) -> questResult(ctx, target, QuestManager.accept(target, IdentifierArgumentType.getIdentifier(ctx, "quest"))))))
						.then(CommandManager.literal("complete")
								.then(targeted(CommandManager.argument("quest", IdentifierArgumentType.identifier()).suggests(QUEST_SUGGESTIONS),
										(ctx, target) -> questResult(ctx, target, QuestManager.forceComplete(target, IdentifierArgumentType.getIdentifier(ctx, "quest"))))))
						.then(CommandManager.literal("reset")
								.then(targeted(CommandManager.literal("all"), (ctx, target) -> {
									QuestManager.resetAll(target);
									ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.quest_reset_all", target.getDisplayName()), true);
									return 1;
								}))
								.then(targeted(CommandManager.argument("quest", IdentifierArgumentType.identifier()).suggests(QUEST_SUGGESTIONS),
										(ctx, target) -> {
											Identifier quest = IdentifierArgumentType.getIdentifier(ctx, "quest");
											QuestManager.reset(target, quest);
											ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.quest_reset", quest.toString(), target.getDisplayName()), true);
											return 1;
										})))
						.then(targeted(CommandManager.literal("list"), HeroCommand::listQuests)))
				.then(CommandManager.literal("ability")
						.then(CommandManager.literal("toggle")
								.then(targeted(CommandManager.argument("ability", IdentifierArgumentType.identifier()).suggests(HERO_ABILITY_SUGGESTIONS),
										(ctx, target) -> toggleAbility(ctx, target, IdentifierArgumentType.getIdentifier(ctx, "ability")))))
						.then(targeted(CommandManager.literal("clear"), (ctx, target) -> {
							ProgressionManager.unequipAll(target);
							ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.ability_clear", target.getDisplayName()), true);
							return 1;
						})))
				.then(CommandManager.literal("mastery")
						.then(CommandManager.argument("alien", IdentifierArgumentType.identifier()).suggests(ALIEN_SUGGESTIONS)
								.then(targeted(CommandManager.argument("level", IntegerArgumentType.integer(1, AlienMastery.MAX_LEVEL)),
										(ctx, target) -> {
											Identifier alien = alienId(ctx);
											int level = IntegerArgumentType.getInteger(ctx, "level");
											AlienMasteryManager.setLevel(target, alien, level);
											ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.mastery",
													TransformationManager.alienName(alien), level, target.getDisplayName()), true);
											return 1;
										}))))
				// Grey Matter: Forschungszeit fuer den Omnitrix-Hack setzen (Minuten), z. B. zum Testen
				.then(CommandManager.literal("galvan")
						.then(CommandManager.literal("research")
								.then(targeted(CommandManager.argument("minutes", IntegerArgumentType.integer(0, 600)),
										(ctx, target) -> {
											int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
											com.santiq.kingdomomnitrix.galvan.GalvanHack.State state = com.santiq.kingdomomnitrix.galvan.GalvanHack.state(target);
											target.setAttached(com.santiq.kingdomomnitrix.galvan.GalvanHack.STATE,
													new com.santiq.kingdomomnitrix.galvan.GalvanHack.State(minutes * 1200, state.level()));
											ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.galvan_research",
													target.getDisplayName(), minutes), true);
											return 1;
										}))))
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

	private static int toggleAbility(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target, Identifier ability) {
		ProgressionManager.Result result = ProgressionManager.toggle(target, ability);
		switch (result) {
			case EQUIPPED, UNEQUIPPED -> {
				ctx.getSource().sendFeedback(() -> Text.translatable(result == ProgressionManager.Result.EQUIPPED
								? "commands.kingdomomnitrix.ability_on" : "commands.kingdomomnitrix.ability_off",
						HeroAbilityDefinition.name(ability), target.getDisplayName()), true);
				return 1;
			}
			case LOCKED -> ctx.getSource().sendError(Text.translatable("message.kingdomomnitrix.ability_locked"));
			case NO_AP -> ctx.getSource().sendError(Text.translatable("message.kingdomomnitrix.ability_no_ap"));
			default -> ctx.getSource().sendError(Text.translatable("message.kingdomomnitrix.ability_unknown"));
		}
		return 0;
	}

	private static int spawnNpc(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		Identifier id = IdentifierArgumentType.getIdentifier(ctx, "npc");
		ServerCommandSource source = ctx.getSource();
		if (NpcRegistry.get(source.getRegistryManager(), id).isEmpty()) {
			source.sendError(Text.translatable("message.kingdomomnitrix.npc_unknown", id.toString()));
			return 0;
		}
		BlockPos pos = BlockPos.ofFloored(source.getPosition());
		float yaw = source.getRotation().y + 180.0f;
		if (NpcSpawnItem.spawn(source.getWorld(), id, pos, yaw) == null) {
			source.sendError(Text.translatable("message.kingdomomnitrix.npc_unknown", id.toString()));
			return 0;
		}
		source.sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.npc_spawned", NpcDefinition.name(id)), true);
		return 1;
	}

	private static int questResult(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target, QuestManager.Result result) {
		if (result == QuestManager.Result.SUCCESS) {
			ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.quest_done", target.getDisplayName()), true);
			return 1;
		}
		ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.quest_failed", result.name()));
		return 0;
	}

	private static int listQuests(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
		var registries = ctx.getSource().getRegistryManager();
		for (Identifier id : QuestRegistry.sortedIds(registries)) {
			QuestRegistry.get(registries, id).ifPresent(quest -> {
				QuestManager.Status status = QuestManager.status(target, id, quest);
				ctx.getSource().sendFeedback(() -> Text.literal(status.name() + "  ").formatted(Formatting.GRAY)
						.append(Text.literal(id.toString()).formatted(Formatting.WHITE)), false);
			});
		}
		return 1;
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

	private static int omnitrixStatus(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
		var device = OmnitrixCore.state(target);
		ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.omnitrix.status", target.getDisplayName(),
				OmnitrixCore.status(target).name().toLowerCase(java.util.Locale.ROOT), Math.round(OmnitrixCore.heat(target) * 100),
				device.profile().toString(), device.masterControl() ? "on" : "off"), false);
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

	private static int startEvent(CommandContext<ServerCommandSource> ctx, Optional<Identifier> eventId) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		var event = com.santiq.kingdomomnitrix.worldevent.WorldEvents.start(player, eventId);
		if (event.isEmpty()) {
			ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.event.failed"));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.event.started", event.get().name(),
				event.get().pos().getX(), event.get().pos().getY(), event.get().pos().getZ()), true);
		return 1;
	}

	private static int stopEvent(CommandContext<ServerCommandSource> ctx) {
		if (!com.santiq.kingdomomnitrix.worldevent.WorldEvents.stop()) {
			ctx.getSource().sendError(Text.translatable("commands.kingdomomnitrix.event.none"));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.event.stopped"), true);
		return 1;
	}

	private static int eventStatus(CommandContext<ServerCommandSource> ctx) {
		var active = com.santiq.kingdomomnitrix.worldevent.WorldEvents.active();
		if (active.isPresent()) {
			var event = active.get();
			ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.event.status_active", event.name(),
					event.pos().getX(), event.pos().getY(), event.pos().getZ(), event.remainingTicks() / 20), false);
		} else {
			long ticks = com.santiq.kingdomomnitrix.worldevent.WorldEvents.ticksUntilNext(ctx.getSource().getServer());
			ctx.getSource().sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.event.status_idle", ticks < 0 ? "?" : String.valueOf(ticks / 1200)), false);
		}
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
		source.sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.debug.abilities",
				ProgressionManager.usedAp(target), ProgressionManager.totalAp(target), joinIds(ProgressionManager.get(target).equipped())), false);
		AlienMastery mastery = AlienMasteryManager.get(target);
		String masteryText = mastery.experience().isEmpty() ? "-" : mastery.experience().keySet().stream().sorted()
				.map(alien -> alien.getPath() + " " + mastery.level(alien)).collect(Collectors.joining(", "));
		source.sendFeedback(() -> Text.translatable("commands.kingdomomnitrix.debug.mastery", masteryText), false);
		return 1;
	}

	private static String joinIds(Collection<Identifier> ids) {
		return ids.isEmpty() ? "-" : ids.stream().map(Identifier::toString).sorted().collect(Collectors.joining(", "));
	}

	private static String joinSorted(Collection<String> values) {
		return values.isEmpty() ? "-" : values.stream().sorted().collect(Collectors.joining(", "));
	}
}
