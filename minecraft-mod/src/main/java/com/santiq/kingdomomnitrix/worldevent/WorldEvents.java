package com.santiq.kingdomomnitrix.worldevent;

import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * Welt-Ereignisse (AAA-Vorgabe „Dynamic World Events“): selten, angekuendigt, mit Belohnung. Immer nur eines zugleich.
 * Planung: im Mittel alle {@code kingdomomnitrixWorldEventMinutes} Minuten (±40 %), nur mit Spielern im Ueberleben/
 * Abenteuer; das Ereignis wird passend zur Heldenstufe eines zufaelligen Spielers gewaehlt und 40–70 Bloecke von ihm
 * entfernt auf trockenem Boden platziert. Abschaltbar ueber {@code kingdomomnitrixWorldEvents}.
 */
public final class WorldEvents {
	public static final GameRules.Key<GameRules.BooleanRule> ENABLED = GameRuleRegistry.register(
			"kingdomomnitrixWorldEvents", GameRules.Category.SPAWNING, GameRuleFactory.createBooleanRule(true));
	public static final GameRules.Key<GameRules.IntRule> INTERVAL_MINUTES = GameRuleRegistry.register(
			"kingdomomnitrixWorldEventMinutes", GameRules.Category.SPAWNING, GameRuleFactory.createIntRule(40, 5, 600));

	private static final int CHECK_TICKS = 200;
	private static final double MIN_DISTANCE = 40.0;
	private static final double MAX_DISTANCE = 70.0;

	private static ActiveWorldEvent active;
	private static long nextAt = -1L;

	private WorldEvents() {
	}

	public static void register() {
		WorldEventRegistry.register();
		ServerTickEvents.END_SERVER_TICK.register(WorldEvents::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
	}

	public static Optional<ActiveWorldEvent> active() {
		return Optional.ofNullable(active);
	}

	/** Restzeit bis zur naechsten Planung (Ticks, Ueberwelt-Zeit); −1 = noch nicht geplant. */
	public static long ticksUntilNext(MinecraftServer server) {
		return nextAt < 0 ? -1L : Math.max(0L, nextAt - server.getOverworld().getTime());
	}

	private static void tick(MinecraftServer server) {
		if (active != null) {
			active.tick();
			if (active.outcome() != ActiveWorldEvent.Outcome.RUNNING) {
				active = null;
				schedule(server);
			}
			return;
		}
		ServerWorld overworld = server.getOverworld();
		long now = overworld.getTime();
		if (now % CHECK_TICKS != 0 || !overworld.getGameRules().getBoolean(ENABLED)) {
			return;
		}
		if (nextAt < 0) {
			schedule(server);
			return;
		}
		if (now < nextAt) {
			return;
		}
		List<ServerPlayerEntity> candidates = server.getPlayerManager().getPlayerList().stream()
				.filter(p -> !p.isSpectator() && !p.isCreative() && p.isAlive()).toList();
		if (candidates.isEmpty()) {
			return;
		}
		ServerPlayerEntity player = candidates.get(overworld.getRandom().nextInt(candidates.size()));
		if (start(player, Optional.empty()).isEmpty()) {
			// kein passender Platz/kein Ereignis: in einer Minute neu versuchen
			nextAt = now + 1200L;
		}
	}

	private static void schedule(MinecraftServer server) {
		ServerWorld overworld = server.getOverworld();
		int minutes = overworld.getGameRules().getInt(INTERVAL_MINUTES);
		float spread = 0.6f + overworld.getRandom().nextFloat() * 0.8f;
		nextAt = overworld.getTime() + Math.round(minutes * 1200L * spread);
	}

	/**
	 * Ereignis nahe einem Spieler starten (Befehl oder Planung). Ohne {@code eventId} wird passend zu Stufe und Welt
	 * gewaehlt. Leer, wenn schon eines laeuft, keines passt oder kein Platz gefunden wurde.
	 */
	public static Optional<ActiveWorldEvent> start(ServerPlayerEntity player, Optional<Identifier> eventId) {
		if (active != null) {
			return Optional.empty();
		}
		ServerWorld world = player.getServerWorld();
		int level = HeroDataAccess.get(player).level();
		Optional<Identifier> chosen = eventId.isPresent() ? eventId
				: WorldEventRegistry.pick(world.getRegistryManager(), level, world.getRegistryKey(),
						world.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL, world.getRandom());
		if (chosen.isEmpty()) {
			return Optional.empty();
		}
		Optional<WorldEventDefinition> definition = WorldEventRegistry.get(world.getRegistryManager(), chosen.get());
		if (definition.isEmpty()) {
			return Optional.empty();
		}
		if (world.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL && definition.get().type().needsMonsters()) {
			return Optional.empty();
		}
		Optional<BlockPos> pos = findSpot(world, player);
		if (pos.isEmpty()) {
			return Optional.empty();
		}
		ActiveWorldEvent event = new ActiveWorldEvent(chosen.get(), definition.get(), world, pos.get(), level);
		if (!event.begin()) {
			return Optional.empty();
		}
		active = event;
		announce(event);
		return Optional.of(event);
	}

	/** Laufendes Ereignis abbrechen (Befehl, Server-Stopp). */
	public static boolean stop() {
		if (active == null) {
			return false;
		}
		MinecraftServer server = active.world().getServer();
		active.finish(ActiveWorldEvent.Outcome.CANCELLED);
		active = null;
		if (server.isRunning()) {
			schedule(server);
		}
		return true;
	}

	/** Trockener, geladener, unbebauter Boden 40–70 Bloecke vom Spieler; bis zu 24 Versuche. */
	private static Optional<BlockPos> findSpot(ServerWorld world, ServerPlayerEntity player) {
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = world.getRandom().nextDouble() * Math.PI * 2;
			double distance = MathHelper.lerp(world.getRandom().nextDouble(), MIN_DISTANCE, MAX_DISTANCE);
			int x = MathHelper.floor(player.getX() + Math.cos(angle) * distance);
			int z = MathHelper.floor(player.getZ() + Math.sin(angle) * distance);
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			BlockPos ground = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			boolean dry = !world.getFluidState(ground).isOf(Fluids.WATER) && !world.getFluidState(ground.down()).isOf(Fluids.WATER)
					&& !world.getFluidState(ground.down()).isOf(Fluids.LAVA);
			if (dry && ground.getY() > world.getBottomY() + 1 && Math.abs(ground.getY() - player.getY()) <= 24 && untouched(world, ground)) {
				return Optional.of(ground);
			}
		}
		return Optional.empty();
	}

	/** Keine Bauwerke in der Naehe (Doerfer, Spielerbauten): 11 × 11 Bloecke um den Ort nur natuerlicher Boden. */
	private static boolean untouched(ServerWorld world, BlockPos ground) {
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				for (int dy = -2; dy <= 3; dy++) {
					var state = world.getBlockState(ground.add(dx, dy, dz));
					if (!ActiveWorldEvent.natural(state) && !state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
							&& !state.isIn(net.minecraft.registry.tag.BlockTags.LOGS) && !state.isOf(net.minecraft.block.Blocks.WATER)
							&& !state.isOf(net.minecraft.block.Blocks.BEDROCK)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Ankuendigung an alle Spieler derselben Welt: Name, Ort, Entfernung; Glocke. */
	private static void announce(ActiveWorldEvent event) {
		BlockPos pos = event.pos();
		for (ServerPlayerEntity player : event.world().getPlayers()) {
			int distance = (int) Math.sqrt(player.getBlockPos().getSquaredDistance(pos));
			player.sendMessage(Text.translatable("message.kingdomomnitrix.world_event.start", event.name(), pos.getX(), pos.getZ(), distance)
					.formatted(Formatting.GOLD), false);
			player.sendMessage(Text.translatable("world_event." + event.id().getNamespace() + "." + event.id().getPath() + ".hint")
					.formatted(Formatting.YELLOW), true);
			player.playSoundToPlayer(SoundEvents.BLOCK_BELL_USE, SoundCategory.AMBIENT, 1.0f, 0.7f);
		}
	}
}
