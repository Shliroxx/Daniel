package com.santiq.kingdomomnitrix.party;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** {@code /party} fuer alle Spieler: invite, accept, leave, kick, list. */
public final class PartyCommand {
	private PartyCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("party")
				.then(CommandManager.literal("invite")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(ctx -> report(ctx, PartyManager.invite(ctx.getSource().getPlayerOrThrow(),
										EntityArgumentType.getPlayer(ctx, "player"))))))
				.then(CommandManager.literal("accept")
						.executes(ctx -> report(ctx, PartyManager.accept(ctx.getSource().getPlayerOrThrow()))))
				.then(CommandManager.literal("leave")
						.executes(ctx -> report(ctx, PartyManager.leave(ctx.getSource().getPlayerOrThrow()))))
				.then(CommandManager.literal("kick")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
									return report(ctx, PartyManager.kick(ctx.getSource().getPlayerOrThrow(), target.getUuid(), target.getDisplayName()));
								})))
				.then(CommandManager.literal("list").executes(PartyCommand::list)));
	}

	private static int report(CommandContext<ServerCommandSource> ctx, PartyManager.Result result) {
		if (result == PartyManager.Result.OK) {
			return 1;
		}
		ctx.getSource().sendError(Text.translatable("party.kingdomomnitrix.error." + result.name().toLowerCase(java.util.Locale.ROOT)));
		return 0;
	}

	private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		var party = PartyManager.partyOf(player);
		if (party.isEmpty()) {
			ctx.getSource().sendFeedback(() -> Text.translatable("party.kingdomomnitrix.none").formatted(Formatting.GRAY), false);
			return 0;
		}
		MutableText line = Text.translatable("party.kingdomomnitrix.list", party.get().size(), Party.MAX_SIZE).formatted(Formatting.AQUA);
		for (UUID uuid : party.get().members()) {
			ServerPlayerEntity member = player.getServer().getPlayerManager().getPlayer(uuid);
			String name = member != null ? member.getGameProfile().getName() : "(offline)";
			line.append(Text.literal("\n • " + name + (uuid.equals(party.get().leader()) ? " ★" : "")).formatted(member != null ? Formatting.WHITE : Formatting.GRAY));
		}
		ctx.getSource().sendFeedback(() -> line, false);
		return party.get().size();
	}
}
