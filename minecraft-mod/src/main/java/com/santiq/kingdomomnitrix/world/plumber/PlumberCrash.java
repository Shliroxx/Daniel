package com.santiq.kingdomomnitrix.world.plumber;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Story-Ereignis „Absturz der PROCTOR“: einmal pro Welt stuerzt ein Klempner-Kreuzer in der Naehe eines Spielers ab.
 *
 * <ol>
 *   <li><b>Warten</b>: nach {@code kingdomomnitrixPlumberCrashMinutes} Minuten Spielzeit in der Oberwelt (mit
 *       mindestens einem Spieler) beginnt der Anflug. Abschaltbar ueber {@code kingdomomnitrixPlumberCrash} (z. B. fuer
 *       Server, die das Ereignis selbst ausloesen wollen: {@code /hero plumber crash}).</li>
 *   <li><b>Anflug</b> ({@value #INCOMING_TICKS} Ticks): Warnung als Titel, Feuerschweif quer ueber den Himmel,
 *       Grollen.</li>
 *   <li><b>Einschlag</b>: das Wrack wird gebaut ({@link PlumberShipBuilder}), Explosion und Donner; jeder Spieler bekommt
 *       einen Klempner-Peilsender (Kompass auf die Kapsel der Huelle) — auch wer spaeter dazukommt.</li>
 * </ol>
 */
public final class PlumberCrash {
	public static final GameRules.Key<GameRules.BooleanRule> ENABLED = GameRuleRegistry.register(
			"kingdomomnitrixPlumberCrash", GameRules.Category.SPAWNING, GameRuleFactory.createBooleanRule(true));
	public static final GameRules.Key<GameRules.IntRule> DELAY_MINUTES = GameRuleRegistry.register(
			"kingdomomnitrixPlumberCrashMinutes", GameRules.Category.SPAWNING, GameRuleFactory.createIntRule(10, 0, 1440));

	static final int INCOMING_TICKS = 160;
	private static final int RETRY_TICKS = 600;
	/** so lange wartet der Einschlag hoechstens zusaetzlich auf vorgeladene Chunks (Ticks) */
	private static final int MAX_WAIT_TICKS = 400;
	/** Ticket zum Vorladen der Chunks unter dem Wrack waehrend des Anflugs; laeuft nach 1200 Ticks ab */
	private static final ChunkTicketType<ChunkPos> PRELOAD = ChunkTicketType.create("kingdomomnitrix_plumber_crash",
			java.util.Comparator.comparingLong(ChunkPos::toLong), 1200);
	private static final int MAX_ATTEMPTS = 40;
	private static final long SEARCH_BUDGET_MS = 40L;
	/** Versuche der laufenden, ueber Ticks verteilten Ortssuche */
	private static int attemptsDone;
	/** Ablehnungsgruende der laufenden Suche (fuers Log) */
	private static int rejectedWater;
	private static int rejectedSpan;
	private static int rejectedLow;
	/** groesste Hoehenspanne des Gelaendes unter dem Wrack (Bug sinkt ein, Heck wird unterfuettert) */
	private static final int MAX_SPAN = 20;
	/** so viele Pruefpunkte duerfen nass sein (Bach, Pfuetze) */
	private static final int MAX_WET = 6;
	/** abgeschlossene, erfolglose Suchrunden (jede weitet den Suchring) */
	private static int failedRounds;
	private static final String STATE_KEY = "kingdomomnitrix_plumber_crash";
	private static final int MIN_DISTANCE = 110;
	private static final int MAX_DISTANCE = 170;

	public enum Phase {
		WAITING, INCOMING, CRASHED
	}

	private static final class State extends PersistentState {
		private Phase phase = Phase.WAITING;
		private long waited;
		private int incoming;
		/** Ticks, die der Einschlag auf vorgeladene Chunks gewartet hat (nur Speicher) */
		private int waitingForChunks;
		private BlockPos nose;
		private Direction forward = Direction.NORTH;
		private BlockPos shellPod;
		private BlockPos consolePod;
		private final Set<UUID> beacons = new HashSet<>();

		static State fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			State state = new State();
			state.phase = Phase.values()[MathHelper.clamp(nbt.getInt("Phase"), 0, Phase.values().length - 1)];
			state.waited = nbt.getLong("Waited");
			state.incoming = nbt.getInt("Incoming");
			state.nose = NbtHelper.toBlockPos(nbt, "Nose").orElse(null);
			state.shellPod = NbtHelper.toBlockPos(nbt, "ShellPod").orElse(null);
			state.consolePod = NbtHelper.toBlockPos(nbt, "ConsolePod").orElse(null);
			state.forward = Direction.fromHorizontal(nbt.getInt("Forward"));
			for (NbtElement element : nbt.getList("Beacons", NbtElement.INT_ARRAY_TYPE)) {
				state.beacons.add(NbtHelper.toUuid(element));
			}
			if (state.phase == Phase.INCOMING && state.nose == null) {
				state.phase = Phase.WAITING;
			}
			return state;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			nbt.putInt("Phase", phase.ordinal());
			nbt.putLong("Waited", waited);
			nbt.putInt("Incoming", incoming);
			nbt.putInt("Forward", forward.getHorizontal());
			if (nose != null) {
				nbt.put("Nose", NbtHelper.fromBlockPos(nose));
			}
			if (shellPod != null) {
				nbt.put("ShellPod", NbtHelper.fromBlockPos(shellPod));
			}
			if (consolePod != null) {
				nbt.put("ConsolePod", NbtHelper.fromBlockPos(consolePod));
			}
			NbtList list = new NbtList();
			for (UUID id : beacons) {
				list.add(NbtHelper.fromUuid(id));
			}
			nbt.put("Beacons", list);
			return nbt;
		}
	}

	private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::fromNbt, null);

	private PlumberCrash() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PlumberCrash::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			State state = state(server);
			if (state.phase == Phase.CRASHED) {
				giveBeacon(state, handler.getPlayer());
			}
		});
	}

	private static State state(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, STATE_KEY);
	}

	public static Phase phase(MinecraftServer server) {
		return state(server).phase;
	}

	/** Lage des Wracks (Nase), falls abgestuerzt oder im Anflug. */
	public static Optional<BlockPos> site(MinecraftServer server) {
		return Optional.ofNullable(state(server).nose);
	}

	/** Restzeit bis zum Anflug (Ticks); -1 wenn nicht mehr wartend oder abgeschaltet. */
	public static long ticksUntilIncoming(MinecraftServer server) {
		State state = state(server);
		if (state.phase != Phase.WAITING || !server.getOverworld().getGameRules().getBoolean(ENABLED)) {
			return -1L;
		}
		return Math.max(0L, server.getOverworld().getGameRules().getInt(DELAY_MINUTES) * 1200L - state.waited);
	}

	private static void tick(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		State state = state(server);
		switch (state.phase) {
			case WAITING -> {
				if (!world.getGameRules().getBoolean(ENABLED) || world.getPlayers(p -> !p.isSpectator()).isEmpty()) {
					return;
				}
				state.waited++;
				if (state.waited % 200 == 0) {
					state.markDirty();
				}
				if (state.waited >= world.getGameRules().getInt(DELAY_MINUTES) * 1200L) {
					List<ServerPlayerEntity> players = world.getPlayers(p -> !p.isSpectator());
					ServerPlayerEntity target = players.get(world.getRandom().nextInt(players.size()));
					// Suche ueber mehrere Ticks verteilt (hoechstens SEARCH_BUDGET_MS je Tick), damit der Server nicht stockt
					Optional<net.minecraft.util.Pair<BlockPos, Direction>> site = findSite(world, target, SEARCH_BUDGET_MS);
					if (site.isPresent()) {
						attemptsDone = 0;
						failedRounds = 0;
						begin(world, state, site.get().getLeft(), site.get().getRight());
					} else if (attemptsDone >= MAX_ATTEMPTS) {
						// kein passender Ort (Wasser, Gebirge): in 30 s bei einem (vielleicht anderen) Spieler erneut suchen
						KingdomOmnitrix.LOGGER.info("Kein Absturzort nach {} Versuchen (Wasser {}, zu steil {}, zu tief {}), neuer Anlauf in {} s",
								attemptsDone, rejectedWater, rejectedSpan, rejectedLow, RETRY_TICKS / 20);
						attemptsDone = 0;
						failedRounds++;
						rejectedWater = 0;
						rejectedSpan = 0;
						rejectedLow = 0;
						state.waited -= RETRY_TICKS;
						state.markDirty();
					}
				}
			}
			case INCOMING -> incoming(world, state);
			case CRASHED -> {
			}
		}
	}

	/** Anflug sofort starten (nur Oberwelt): am Ort {@code nose} (Nasenspitze, Bodenhoehe wird bestimmt) in Flugrichtung {@code forward}. */
	public static void begin(ServerWorld overworld, BlockPos nose, Direction forward) {
		begin(overworld, state(overworld.getServer()), nose, forward);
	}

	/** Anflug in der Naehe eines Spielers starten (zufaelliger, geeigneter Ort); false, wenn kein Ort passt. */
	public static boolean beginNear(ServerPlayerEntity player) {
		attemptsDone = 0;
		Optional<net.minecraft.util.Pair<BlockPos, Direction>> site = findSite(player.getServerWorld(), player, Long.MAX_VALUE);
		attemptsDone = 0;
		site.ifPresent(s -> begin(player.getServerWorld(), s.getLeft(), s.getRight()));
		return site.isPresent();
	}

	private static void begin(ServerWorld world, State state, BlockPos nose, Direction forward) {
		state.phase = Phase.INCOMING;
		state.incoming = 0;
		state.waitingForChunks = 0;
		state.nose = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, nose);
		state.forward = forward;
		// neuer Absturz: jeder bekommt einen neuen Peilsender auf das neue Wrack
		state.beacons.clear();
		state.markDirty();
		for (ServerPlayerEntity player : world.getPlayers()) {
			player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
			player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("title.kingdomomnitrix.plumber_crash.subtitle")
					.formatted(Formatting.GREEN)));
			player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("title.kingdomomnitrix.plumber_crash.title")
					.formatted(Formatting.RED, Formatting.BOLD)));
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 1.0f, 0.5f);
		}
		KingdomOmnitrix.LOGGER.info("Klempner-Kreuzer im Anflug auf {}", state.nose);
	}

	/** Ort fuer das Wrack: 110–170 Bloecke vom Spieler, trockener Boden, Gelaende entlang des Schiffs halbwegs eben. */
	/** Bis zu {@value #MAX_ATTEMPTS} Versuche (zaehlt ueber Aufrufe weiter), hoechstens {@code budgetMs} Millisekunden je Aufruf. */
	private static Optional<net.minecraft.util.Pair<BlockPos, Direction>> findSite(ServerWorld world, ServerPlayerEntity player, long budgetMs) {
		Random random = world.getRandom();
		long started = System.currentTimeMillis();
		while (attemptsDone < MAX_ATTEMPTS && System.currentTimeMillis() - started < budgetMs) {
			attemptsDone++;
			double angle = random.nextDouble() * Math.PI * 2;
			// jeder Versuch weitet den Ring um 2 Bloecke, jede erfolglose Runde um 30 (hoechstens bis 260)
			int widen = Math.min(failedRounds * 30 + attemptsDone * 2, 90);
			double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE + widen - MIN_DISTANCE);
			BlockPos column = BlockPos.ofFloored(player.getX() + Math.cos(angle) * distance, 0, player.getZ() + Math.sin(angle) * distance);
			Direction forward = Direction.fromHorizontal(random.nextInt(4));
			if (suitable(world, column, forward)) {
				KingdomOmnitrix.LOGGER.info("Absturzort nach {} Versuchen gefunden ({} ms in diesem Aufruf)", attemptsDone,
						System.currentTimeMillis() - started);
				return Optional.of(new net.minecraft.util.Pair<>(column, forward));
			}
		}
		return Optional.empty();
	}

	/**
	 * Gelaende fuer das Wrack: 21 Punkte (Mittellinie alle 20 Bloecke, dazu seitlich auf Hoehe von Bug, Bruch und Heck)
	 * mit hoechstens {@value #MAX_WET} nassen Punkten (Gewaessergrund unter Meereshoehe: Fluss, See, Meer — ein Bach wird
	 * zugeschuettet) und Hoehenspanne hoechstens {@value #MAX_SPAN}. Die Hoehen kommen direkt aus dem Gelaenderauschen
	 * des Generators (wie bei der Strukturplatzierung, ohne einen Chunk zu laden); eine Abfrage je Punkt, Abbruch beim
	 * ersten Ausschlussgrund — ein Versuch kostet so hoechstens einige zehn Millisekunden.
	 */
	private static boolean suitable(ServerWorld world, BlockPos column, Direction forward) {
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		Direction side = forward.rotateYClockwise();
		int sea = world.getSeaLevel();
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int wet = 0;
		for (int[] uv : PROBES) {
			BlockPos probe = column.offset(forward, -uv[0]).offset(side, uv[1]);
			int floor = generator.getHeight(probe.getX(), probe.getZ(), Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
			if (floor <= sea && ++wet > MAX_WET) {
				rejectedWater++;
				return false;
			}
			min = Math.min(min, floor);
			max = Math.max(max, floor);
			if (max - min > MAX_SPAN) {
				rejectedSpan++;
				return false;
			}
		}
		if (min <= sea - 4) {
			rejectedLow++;
			return false;
		}
		return true;
	}

	/** Pruefpunkte (u entlang des Schiffs, v seitlich): zuerst grob ueber die ganze Laenge, dann die Breite */
	private static final int[][] PROBES = probes();

	private static int[][] probes() {
		java.util.List<int[]> list = new java.util.ArrayList<>();
		for (int u = 0; u <= 220; u += 40) {
			list.add(new int[]{u, 0});
		}
		for (int u = 20; u <= 220; u += 40) {
			list.add(new int[]{u, 0});
		}
		for (int u : new int[]{0, 110, 220}) {
			list.add(new int[]{u, -14});
			list.add(new int[]{u, 14});
		}
		list.add(new int[]{-10, 0});
		list.add(new int[]{230, 0});
		list.add(new int[]{60, -14});
		return list.toArray(new int[0][]);
	}

	private static void incoming(ServerWorld world, State state) {
		state.incoming++;
		if (state.incoming == 1 || state.waitingForChunks == 1) {
			// auch nach einem Serverneustart mitten im Anflug (Tickets sind nicht gespeichert)
			preload(world, state);
		}
		// Einschlag erst, wenn alle Chunks unter dem Wrack bereit sind (sonst wuerde der Bau sie auf einmal erzeugen);
		// bis dahin haengt der Schweif kurz vor dem Ziel, hoechstens MAX_WAIT_TICKS lang
		if (state.incoming >= INCOMING_TICKS && state.waitingForChunks < MAX_WAIT_TICKS && !footprintLoaded(world, state)) {
			state.incoming = INCOMING_TICKS - 1;
			state.waitingForChunks++;
		}
		float t = state.incoming / (float) INCOMING_TICKS;
		Vec3d target = Vec3d.ofCenter(state.nose);
		Vec3d back = Vec3d.of(state.forward.getVector()).multiply(-1);
		Vec3d start = target.add(back.multiply(260)).add(0, 150, 0);
		Vec3d head = start.lerp(target, Math.pow(t, 1.35));
		// Schweif: Kopf aus Feuer und Explosionen, dahinter eine lange, langsam verwehende Rauchspur
		Vec3d tail = start.lerp(target, Math.pow(Math.max(0.0f, t - 0.03f), 1.35));
		for (ServerPlayerEntity player : world.getPlayers()) {
			world.spawnParticles(player, ParticleTypes.EXPLOSION, true, head.x, head.y, head.z, 4, 2.5, 2.5, 2.5, 0.0);
			world.spawnParticles(player, ParticleTypes.FLAME, true, head.x, head.y, head.z, 60, 3.0, 3.0, 3.0, 0.08);
			world.spawnParticles(player, ParticleTypes.LAVA, true, head.x, head.y, head.z, 8, 2.0, 2.0, 2.0, 0.0);
			for (int i = 0; i < 6; i++) {
				Vec3d p = tail.lerp(head, i / 6.0);
				world.spawnParticles(player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, p.x, p.y, p.z, 3, 1.5, 1.5, 1.5, 0.005);
				world.spawnParticles(player, ParticleTypes.LARGE_SMOKE, true, p.x, p.y, p.z, 4, 2.0, 2.0, 2.0, 0.01);
			}
			if (state.incoming % 20 == 0) {
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.AMBIENT, 0.8f + t, 0.3f + t * 0.3f);
				world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.AMBIENT, 0.6f + t, 0.4f);
			}
		}
		if (state.incoming % 20 == 0) {
			state.markDirty();
		}
		if (state.incoming >= INCOMING_TICKS) {
			KingdomOmnitrix.LOGGER.info("Einschlag (auf Chunks gewartet: {} Ticks)", state.waitingForChunks);
			impact(world, state);
		}
	}

	/** Chunks unter dem Wrack im Hintergrund erzeugen lassen (Ticket laeuft nach dem Einschlag von selbst ab). */
	private static void preload(ServerWorld world, State state) {
		for (ChunkPos chunk : PlumberShipBuilder.footprint(state.nose, state.forward)) {
			world.getChunkManager().addTicket(PRELOAD, chunk, 1, chunk);
		}
	}

	private static boolean footprintLoaded(ServerWorld world, State state) {
		for (ChunkPos chunk : PlumberShipBuilder.footprint(state.nose, state.forward)) {
			if (!world.isChunkLoaded(chunk.x, chunk.z)) {
				return false;
			}
		}
		return true;
	}

	private static void impact(ServerWorld world, State state) {
		PlumberShipBuilder.Site site = PlumberShipBuilder.build(world, state.nose, state.forward, world.getRandom());
		state.phase = Phase.CRASHED;
		state.shellPod = site.shellPod();
		state.consolePod = site.consolePod();
		state.markDirty();
		Vec3d at = Vec3d.ofCenter(state.nose).add(0, 3, 0);
		for (ServerPlayerEntity player : world.getPlayers()) {
			world.spawnParticles(player, ParticleTypes.EXPLOSION_EMITTER, true, at.x, at.y, at.z, 6, 6.0, 3.0, 6.0, 0.0);
			world.spawnParticles(player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, at.x, at.y, at.z, 80, 8.0, 4.0, 8.0, 0.05);
			world.spawnParticles(player, ParticleTypes.LAVA, true, at.x, at.y, at.z, 60, 6.0, 2.0, 6.0, 0.5);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.AMBIENT, 2.0f, 0.5f);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 1.5f, 0.6f);
			giveBeacon(state, player);
		}
		world.playSound(null, state.nose, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 8.0f, 0.6f);
	}

	/** Peilsender (einmal je Spieler): Kompass auf die Kapsel der Huelle, dazu Nachricht mit den Koordinaten. */
	private static void giveBeacon(State state, ServerPlayerEntity player) {
		if (state.nose == null || !state.beacons.add(player.getUuid())) {
			return;
		}
		state.markDirty();
		BlockPos target = state.shellPod != null ? state.shellPod : state.nose;
		ItemStack compass = new ItemStack(Items.COMPASS);
		compass.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(Optional.of(GlobalPos.create(World.OVERWORLD, target)), false));
		compass.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.kingdomomnitrix.plumber_beacon").formatted(Formatting.GREEN));
		compass.set(DataComponentTypes.LORE, new LoreComponent(List.of(
				Text.translatable("item.kingdomomnitrix.plumber_beacon.tooltip").formatted(Formatting.GRAY))));
		player.getInventory().offerOrDrop(compass);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.plumber_crash.signal", target.getX(), target.getY(), target.getZ())
				.formatted(Formatting.GREEN), false);
	}

	/** Zuruecksetzen (Befehl): wartet wieder; das gebaute Wrack bleibt stehen. */
	public static void reset(MinecraftServer server) {
		State state = state(server);
		state.phase = Phase.WAITING;
		state.waited = 0;
		state.incoming = 0;
		state.beacons.clear();
		state.markDirty();
	}
}
