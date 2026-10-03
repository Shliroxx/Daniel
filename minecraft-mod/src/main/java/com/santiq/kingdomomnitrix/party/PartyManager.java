package com.santiq.kingdomomnitrix.party;

import com.santiq.kingdomomnitrix.networking.PartySyncPayload;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Gruppen (Party) auf dem Server: Einladen, Annehmen, Verlassen, Hinauswerfen. Gruppen bestehen, solange der Server
 * laeuft; wer offline geht, bleibt Mitglied. Mitglieder teilen Helden-EP und Quest-Fortschritt bei Kills in der Naehe,
 * verletzen sich nicht gegenseitig und machen Gegner staerker (siehe {@link PartyRules}).
 */
public final class PartyManager {
	/** Reichweite fuer geteilte EP, geteilten Quest-Fortschritt und Gegner-Skalierung. */
	public static final double SHARE_RADIUS = 48.0;
	private static final int INVITE_TICKS = 20 * 60;
	private static final int SYNC_INTERVAL_TICKS = 10;

	private static final Map<UUID, Party> BY_PLAYER = new HashMap<>();
	/** Einladung: eingeladener Spieler -> (Gruppe, Ablauf-Tick) */
	private static final Map<UUID, Invite> INVITES = new HashMap<>();

	private record Invite(Party party, UUID from, long expires) {
	}

	public enum Result { OK, SELF, ALREADY_IN_PARTY, NOT_LEADER, FULL, NO_INVITE, NOT_IN_PARTY, NOT_MEMBER }

	private PartyManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PartyManager::tick);
		// Kein Eigenbeschuss in der Gruppe – auch nicht mit Vanilla-Waffen, Pfeilen oder Explosionen eines Mitglieds
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayerEntity target && source.getAttacker() instanceof ServerPlayerEntity attacker
						&& attacker != target && sameParty(attacker, target)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BY_PLAYER.clear();
			INVITES.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(server));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> sync(server)));
	}

	// --- Abfragen -------------------------------------------------------------------------------

	public static Optional<Party> partyOf(PlayerEntity player) {
		return Optional.ofNullable(BY_PLAYER.get(player.getUuid()));
	}

	public static boolean sameParty(PlayerEntity a, PlayerEntity b) {
		Party party = BY_PLAYER.get(a.getUuid());
		return party != null && party.contains(b.getUuid());
	}

	/** Der Spieler selbst und alle Gruppenmitglieder in derselben Welt in Reichweite. */
	public static List<ServerPlayerEntity> nearbyMembers(ServerPlayerEntity player, double radius) {
		List<ServerPlayerEntity> result = new ArrayList<>();
		result.add(player);
		Party party = BY_PLAYER.get(player.getUuid());
		if (party == null) {
			return result;
		}
		for (UUID uuid : party.members()) {
			if (uuid.equals(player.getUuid())) {
				continue;
			}
			ServerPlayerEntity member = player.getServer().getPlayerManager().getPlayer(uuid);
			if (member != null && member.isAlive() && member.getServerWorld() == player.getServerWorld()
					&& member.squaredDistanceTo(player) <= radius * radius) {
				result.add(member);
			}
		}
		return result;
	}

	// --- Aktionen -------------------------------------------------------------------------------

	public static Result invite(ServerPlayerEntity from, ServerPlayerEntity target) {
		if (from == target) {
			return Result.SELF;
		}
		if (BY_PLAYER.containsKey(target.getUuid())) {
			return Result.ALREADY_IN_PARTY;
		}
		Party party = BY_PLAYER.computeIfAbsent(from.getUuid(), uuid -> new Party(uuid));
		if (!party.leader().equals(from.getUuid())) {
			return Result.NOT_LEADER;
		}
		if (party.isFull()) {
			return Result.FULL;
		}
		INVITES.put(target.getUuid(), new Invite(party, from.getUuid(), from.getServerWorld().getTime() + INVITE_TICKS));
		Text accept = Text.translatable("party.kingdomomnitrix.accept_button").setStyle(Style.EMPTY.withColor(Formatting.GREEN)
				.withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept"))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("/party accept"))));
		target.sendMessage(Text.translatable("party.kingdomomnitrix.invited", from.getDisplayName()).formatted(Formatting.AQUA)
				.append(Text.literal(" ")).append(accept), false);
		from.sendMessage(Text.translatable("party.kingdomomnitrix.invite_sent", target.getDisplayName()).formatted(Formatting.GRAY), false);
		sync(from.getServer());
		return Result.OK;
	}

	public static Result accept(ServerPlayerEntity player) {
		Invite invite = INVITES.remove(player.getUuid());
		if (invite == null || invite.expires() < player.getServerWorld().getTime() || BY_PLAYER.get(invite.from()) != invite.party()) {
			return Result.NO_INVITE;
		}
		if (BY_PLAYER.containsKey(player.getUuid())) {
			return Result.ALREADY_IN_PARTY;
		}
		if (invite.party().isFull()) {
			return Result.FULL;
		}
		invite.party().add(player.getUuid());
		BY_PLAYER.put(player.getUuid(), invite.party());
		broadcast(player.getServer(), invite.party(), Text.translatable("party.kingdomomnitrix.joined", player.getDisplayName()));
		sync(player.getServer());
		return Result.OK;
	}

	public static Result leave(ServerPlayerEntity player) {
		Party party = BY_PLAYER.get(player.getUuid());
		if (party == null) {
			return Result.NOT_IN_PARTY;
		}
		removeMember(player.getServer(), party, player.getUuid());
		player.sendMessage(Text.translatable("party.kingdomomnitrix.left_self").formatted(Formatting.GRAY), false);
		broadcast(player.getServer(), party, Text.translatable("party.kingdomomnitrix.left", player.getDisplayName()));
		return Result.OK;
	}

	public static Result kick(ServerPlayerEntity leader, UUID target, Text targetName) {
		Party party = BY_PLAYER.get(leader.getUuid());
		if (party == null) {
			return Result.NOT_IN_PARTY;
		}
		if (!party.leader().equals(leader.getUuid())) {
			return Result.NOT_LEADER;
		}
		if (!party.contains(target) || target.equals(leader.getUuid())) {
			return Result.NOT_MEMBER;
		}
		removeMember(leader.getServer(), party, target);
		ServerPlayerEntity kicked = leader.getServer().getPlayerManager().getPlayer(target);
		if (kicked != null) {
			kicked.sendMessage(Text.translatable("party.kingdomomnitrix.kicked_self").formatted(Formatting.RED), false);
		}
		broadcast(leader.getServer(), party, Text.translatable("party.kingdomomnitrix.kicked", targetName));
		return Result.OK;
	}

	private static void removeMember(MinecraftServer server, Party party, UUID player) {
		party.remove(player);
		BY_PLAYER.remove(player);
		// allein ist keine Gruppe mehr
		if (party.size() <= 1) {
			party.members().forEach(BY_PLAYER::remove);
		}
		INVITES.values().removeIf(invite -> invite.party() == party && party.size() <= 1);
		sync(server);
	}

	private static void broadcast(MinecraftServer server, Party party, Text message) {
		for (UUID uuid : party.members()) {
			ServerPlayerEntity member = server.getPlayerManager().getPlayer(uuid);
			if (member != null) {
				member.sendMessage(message.copy().formatted(Formatting.AQUA), false);
			}
		}
	}

	// --- Abgleich mit den Clients ---------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % SYNC_INTERVAL_TICKS != 0) {
			return;
		}
		long now = server.getOverworld().getTime();
		INVITES.values().removeIf(invite -> invite.expires() < now);
		// Einladung abgelaufen und niemand beigetreten: die Ein-Personen-Gruppe aufloesen
		BY_PLAYER.values().removeIf(party -> party.size() <= 1
				&& INVITES.values().stream().noneMatch(invite -> invite.party() == party));
		if (!BY_PLAYER.isEmpty()) {
			sync(server);
		}
	}

	/** Sendet jedem Spieler den Stand seiner Gruppe (Leben, Stufe, online); Spieler ohne Gruppe bekommen eine leere Liste. */
	private static void sync(MinecraftServer server) {
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			Party party = BY_PLAYER.get(viewer.getUuid());
			List<PartySyncPayload.Member> members = new ArrayList<>();
			if (party != null) {
				for (UUID uuid : party.members()) {
					ServerPlayerEntity member = server.getPlayerManager().getPlayer(uuid);
					if (member == null) {
						members.add(new PartySyncPayload.Member(uuid, nameOf(server, uuid), 0, 0, 0, false, uuid.equals(party.leader())));
					} else {
						members.add(new PartySyncPayload.Member(uuid, member.getGameProfile().getName(), member.getHealth(), member.getMaxHealth(),
								HeroDataAccess.get(member).level(), true, uuid.equals(party.leader())));
					}
				}
			}
			ServerPlayNetworking.send(viewer, new PartySyncPayload(members));
		}
	}

	private static String nameOf(MinecraftServer server, UUID uuid) {
		return server.getUserCache() == null ? "?" : server.getUserCache().getByUuid(uuid).map(profile -> profile.getName()).orElse("?");
	}
}
