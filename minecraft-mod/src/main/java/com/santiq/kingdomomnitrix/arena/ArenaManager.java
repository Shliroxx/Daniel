package com.santiq.kingdomomnitrix.arena;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.boss.NefariousEntity;
import com.santiq.kingdomomnitrix.enemy.HeartlessEntity;
import com.santiq.kingdomomnitrix.enemy.RiftDefinition;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.quest.QuestDefinition;
import com.santiq.kingdomomnitrix.world.TraverseTown;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Arena-Kaempfe wie in Ratchet & Clank: Wellen in einem Kreis um die Arena-Mitte, Countdown, Rundenansagen,
 * Bossleiste, Zeitlimit. Alle Spieler im Kreis kaempfen mit; wer stirbt oder den Kreis verlaesst, scheidet aus.
 * Sind alle ausgeschieden, ist die Herausforderung verloren. Sieg: Belohnung fuer jeden und Bestzeit.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ArenaManager {
	public static final AttachmentType<ArenaRecords> RECORDS = AttachmentRegistry.create(KingdomOmnitrix.id("arena_records"), builder -> builder
			.persistent(ArenaRecords.CODEC)
			.initializer(() -> ArenaRecords.EMPTY)
			.copyOnDeath()
			.syncWith(ArenaRecords.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	public enum Result { STARTED, UNKNOWN, NO_TERMINAL, RUNNING, LEVEL_TOO_LOW, NOT_IN_ARENA }

	private static final int COUNTDOWN_TICKS = 60;
	private static final int PAUSE_TICKS = 50;
	/** So weit darf man ueber den Arena-Rand hinaus, bevor man ausscheidet. */
	private static final int LEAVE_MARGIN = 8;

	private record Key(RegistryKey<World> world, BlockPos terminal) {
	}

	private static final class Session {
		final Identifier id;
		final ArenaChallenge challenge;
		final BlockPos center;
		final int radius;
		final Set<UUID> participants = new HashSet<>();
		/** Alle, die je mitgekaempft haben (fuer die Schlussmeldung, auch nach dem Ausscheiden). */
		final Set<UUID> joined = new HashSet<>();
		final Set<UUID> alive = new HashSet<>();
		final ServerBossBar bar;
		int wave = -1;
		int countdown = COUNTDOWN_TICKS;
		int pause;
		long startTick;

		Session(Identifier id, ArenaChallenge challenge, BlockPos center, int radius) {
			this.id = id;
			this.challenge = challenge;
			this.center = center;
			this.radius = radius;
			this.bar = new ServerBossBar(ArenaChallenge.name(id), BossBar.Color.YELLOW, BossBar.Style.NOTCHED_10);
		}
	}

	private static final Map<Key, Session> SESSIONS = new HashMap<>();

	private ArenaManager() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(ArenaManager::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Map.Entry<Key, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
				ServerWorld world = server.getWorld(entry.getKey().world());
				if (world != null) {
					cleanup(world, entry.getValue());
				}
			}
			SESSIONS.clear();
		});
	}

	public static ArenaRecords records(PlayerEntity player) {
		ArenaRecords records = player.getAttached(RECORDS);
		return records != null ? records : ArenaRecords.EMPTY;
	}

	private static void updateRecords(ServerPlayerEntity player, UnaryOperator<ArenaRecords> change) {
		ArenaRecords before = records(player);
		ArenaRecords after = change.apply(before);
		if (!after.equals(before)) {
			player.setAttached(RECORDS, after);
		}
	}

	public static boolean isRunning(ServerWorld world, BlockPos terminal) {
		return SESSIONS.containsKey(new Key(world.getRegistryKey(), terminal));
	}

	// --- Start ----------------------------------------------------------------------------------

	public static Result start(ServerPlayerEntity player, BlockPos terminal, Identifier challengeId) {
		ServerWorld world = player.getServerWorld();
		if (!(world.getBlockEntity(terminal) instanceof ArenaTerminalBlockEntity arena) || player.squaredDistanceTo(terminal.toCenterPos()) > 64) {
			return Result.NO_TERMINAL;
		}
		Key key = new Key(world.getRegistryKey(), terminal.toImmutable());
		if (SESSIONS.containsKey(key)) {
			return Result.RUNNING;
		}
		Optional<ArenaChallenge> challenge = ArenaRegistry.get(world.getRegistryManager(), challengeId);
		if (challenge.isEmpty()) {
			return Result.UNKNOWN;
		}
		if (HeroDataAccess.get(player).level() < challenge.get().minLevel()) {
			return Result.LEVEL_TOO_LOW;
		}
		Session session = new Session(challengeId, challenge.get(), arena.center(), arena.radius());
		for (ServerPlayerEntity candidate : world.getPlayers()) {
			if (inArena(session, candidate, 4) && candidate.isAlive() && !candidate.isSpectator()) {
				session.participants.add(candidate.getUuid());
				session.bar.addPlayer(candidate);
			}
		}
		if (!session.participants.contains(player.getUuid())) {
			// Das Terminal steht am Rand: wer es bedient, kaempft immer mit.
			session.participants.add(player.getUuid());
			session.bar.addPlayer(player);
		}
		session.joined.addAll(session.participants);
		SESSIONS.put(key, session);
		session.startTick = world.getTime();
		announce(world, session.participants, ArenaChallenge.name(challengeId).copy().formatted(Formatting.GOLD),
				Text.translatable("arena.kingdomomnitrix.get_ready").formatted(Formatting.YELLOW), ModSounds.ARENA_ROUND);
		return Result.STARTED;
	}

	// --- Ablauf ---------------------------------------------------------------------------------

	private static void tick(ServerWorld world) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		for (Map.Entry<Key, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
			if (!entry.getKey().world().equals(world.getRegistryKey())) {
				continue;
			}
			Session session = entry.getValue();
			updateParticipants(world, session);
			if (session.participants.isEmpty()) {
				finish(world, entry.getKey(), session, false);
				continue;
			}
			int limit = session.challenge.timeLimit() * 20;
			if (limit > 0 && world.getTime() - session.startTick > limit + COUNTDOWN_TICKS) {
				finish(world, entry.getKey(), session, false);
				continue;
			}
			if (session.countdown > 0) {
				session.countdown--;
				if (session.countdown % 20 == 0 && session.countdown > 0) {
					announce(world, session.participants, Text.literal(String.valueOf(session.countdown / 20)).formatted(Formatting.YELLOW), Text.empty(),
							SoundEvents.BLOCK_NOTE_BLOCK_PLING.value());
				}
				if (session.countdown == 0) {
					startWave(world, session, 0);
				}
				continue;
			}
			session.alive.removeIf(uuid -> {
				Entity entity = world.getEntity(uuid);
				return entity == null || !entity.isAlive();
			});
			if (session.pause > 0) {
				if (--session.pause == 0) {
					startWave(world, session, session.wave + 1);
				}
			} else if (session.alive.isEmpty()) {
				if (session.wave + 1 >= session.challenge.waves().size()) {
					finish(world, entry.getKey(), session, true);
					continue;
				}
				session.pause = PAUSE_TICKS;
			}
			updateBar(world, session);
		}
	}

	private static void updateParticipants(ServerWorld world, Session session) {
		for (UUID uuid : new ArrayList<>(session.participants)) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
			if (player == null || !player.isAlive() || player.isSpectator() || player.getServerWorld() != world
					|| !inArena(session, player, LEAVE_MARGIN)) {
				session.participants.remove(uuid);
				if (player != null) {
					session.bar.removePlayer(player);
					player.sendMessage(Text.translatable("arena.kingdomomnitrix.out").formatted(Formatting.RED), true);
				}
			}
		}
	}

	private static boolean inArena(Session session, ServerPlayerEntity player, int margin) {
		double dx = player.getX() - (session.center.getX() + 0.5);
		double dz = player.getZ() - (session.center.getZ() + 0.5);
		double limit = session.radius + margin;
		return dx * dx + dz * dz <= limit * limit && Math.abs(player.getY() - session.center.getY()) < 16;
	}

	private static void startWave(ServerWorld world, Session session, int wave) {
		session.wave = wave;
		Random random = world.getRandom();
		int level = 1;
		for (UUID uuid : session.participants) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
			if (player != null) {
				level = Math.max(level, HeroDataAccess.get(player).level());
			}
		}
		for (RiftDefinition.Group group : session.challenge.waves().get(wave)) {
			Optional<EntityType<?>> type = Registries.ENTITY_TYPE.getOrEmpty(group.entity());
			if (type.isEmpty()) {
				KingdomOmnitrix.LOGGER.warn("Arena {}: unbekannter Gegner {}", session.id, group.entity());
				continue;
			}
			for (int i = 0; i < group.count(); i++) {
				spawn(world, session, type.get(), level, random);
			}
		}
		Text title = wave + 1 >= session.challenge.waves().size()
				? Text.translatable("arena.kingdomomnitrix.final_round").formatted(Formatting.RED)
				: Text.translatable("arena.kingdomomnitrix.round", wave + 1).formatted(Formatting.GOLD);
		announce(world, session.participants, title, enemies(session.alive.size()).formatted(Formatting.GRAY),
				ModSounds.ARENA_ROUND);
	}

	private static void spawn(ServerWorld world, Session session, EntityType<?> type, int level, Random random) {
		Entity entity = type.create(world);
		if (entity == null) {
			return;
		}
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = session.radius * (0.55 + random.nextDouble() * 0.3);
		int x = MathHelper.floor(session.center.getX() + 0.5 + Math.cos(angle) * distance);
		int z = MathHelper.floor(session.center.getZ() + 0.5 + Math.sin(angle) * distance);
		int y = session.center.getY();
		if (!world.getBlockState(new BlockPos(x, y, z)).isAir()) {
			y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		}
		entity.refreshPositionAndAngles(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0f, 0.0f);
		if (entity instanceof MobEntity mob) {
			mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.EVENT, null);
			if (mob instanceof NefariousEntity boss) {
				boss.setHome(session.center);
			}
			if (mob instanceof HeartlessEntity heartless) {
				heartless.applyScaling(level, random.nextFloat() < session.challenge.eliteChance());
			}
			session.participants.stream().findAny()
					.map(uuid -> world.getServer().getPlayerManager().getPlayer(uuid))
					.ifPresent(mob::setTarget);
		}
		entity.addCommandTag(TraverseTown.ALLOWED_TAG);
		world.spawnEntity(entity);
		world.spawnParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 0.5, entity.getZ(), 20, 0.4, 0.6, 0.4, 0.3);
		session.alive.add(entity.getUuid());
	}

	private static void updateBar(ServerWorld world, Session session) {
		int total = session.challenge.waves().size();
		int wave = Math.max(0, session.wave);
		int waveSize = session.challenge.waves().get(wave).stream().mapToInt(RiftDefinition.Group::count).sum();
		float inWave = waveSize == 0 ? 1.0f : 1.0f - (float) session.alive.size() / waveSize;
		session.bar.setPercent(MathHelper.clamp((wave + (session.pause > 0 ? 1.0f : inWave)) / total, 0.0f, 1.0f));
		int limit = session.challenge.timeLimit() * 20;
		Text name = Text.translatable("arena.kingdomomnitrix.bar", ArenaChallenge.name(session.id), wave + 1, total, enemies(session.alive.size()));
		if (limit > 0) {
			long left = Math.max(0, limit + COUNTDOWN_TICKS - (world.getTime() - session.startTick)) / 20;
			name = name.copy().append(Text.literal(String.format("  ⏱ %d:%02d", left / 60, left % 60)));
		}
		session.bar.setName(name);
	}

	private static void finish(ServerWorld world, Key key, Session session, boolean won) {
		SESSIONS.remove(key);
		int ticks = (int) Math.max(0, world.getTime() - session.startTick - COUNTDOWN_TICKS);
		if (won) {
			QuestDefinition.Rewards rewards = session.challenge.rewards();
			for (UUID uuid : session.participants) {
				ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
				if (player == null) {
					continue;
				}
				ArenaRecords before = records(player);
				Integer best = before.bestTicks().get(session.id);
				updateRecords(player, records -> records.withTime(session.id, ticks));
				if (rewards.bolts() > 0) {
					HeroDataAccess.update(player, data -> data.addBolts(rewards.bolts()));
				}
				if (rewards.experience() > 0) {
					HeroDataAccess.grantExperience(player, rewards.experience());
				}
				for (ItemStack item : rewards.items()) {
					player.getInventory().offerOrDrop(item.copy());
				}
				player.sendMessage(Text.translatable("arena.kingdomomnitrix.won", ArenaChallenge.name(session.id), formatTime(ticks),
						rewards.bolts(), rewards.experience()).formatted(Formatting.GOLD), false);
				if (best == null || ticks < best) {
					player.sendMessage(Text.translatable("arena.kingdomomnitrix.record").formatted(Formatting.AQUA, Formatting.BOLD), false);
				}
			}
			announce(world, session.joined, Text.translatable("arena.kingdomomnitrix.victory").formatted(Formatting.GOLD),
					Text.literal(formatTime(ticks)).formatted(Formatting.YELLOW), ModSounds.ARENA_VICTORY);
		} else {
			announce(world, session.joined, Text.translatable("arena.kingdomomnitrix.defeat").formatted(Formatting.RED),
					Text.translatable("arena.kingdomomnitrix.defeat_hint").formatted(Formatting.GRAY), SoundEvents.ENTITY_WITHER_DEATH);
		}
		cleanup(world, session);
	}

	private static void cleanup(ServerWorld world, Session session) {
		for (UUID uuid : session.alive) {
			Entity entity = world.getEntity(uuid);
			if (entity != null) {
				world.spawnParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 0.5, entity.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
				entity.discard();
			}
		}
		session.alive.clear();
		session.bar.clearPlayers();
	}

	private static MutableText enemies(int count) {
		return Text.translatable(count == 1 ? "arena.kingdomomnitrix.enemy" : "arena.kingdomomnitrix.enemies", count);
	}

	public static String formatTime(int ticks) {
		int seconds = ticks / 20;
		return String.format("%d:%02d.%d", seconds / 60, seconds % 60, (ticks % 20) / 2);
	}

	/** Titel, Untertitel und Klang fuer alle Teilnehmer. */
	private static void announce(ServerWorld world, Set<UUID> audience, Text title, Text subtitle, SoundEvent sound) {
		List<ServerPlayerEntity> players = new ArrayList<>();
		for (UUID uuid : audience) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
			if (player != null) {
				players.add(player);
			}
		}
		for (ServerPlayerEntity player : players) {
			player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 30, 10));
			player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
			player.networkHandler.sendPacket(new TitleS2CPacket(title));
			player.playSoundToPlayer(sound, SoundCategory.PLAYERS, 0.8f, 1.0f);
		}
	}
}
