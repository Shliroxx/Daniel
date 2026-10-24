package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Bevoelkert die Heimatwelten mit ihren wilden Aliens: alle {@value #INTERVAL} Ticks je Spieler auf einer Heimatwelt
 * eine Gruppe einer Art, die in der Naehe ({@value #RADIUS} Bloecke) unter ihrem Hoechstwert liegt — ausserhalb der
 * Sicht (24–40 Bloecke entfernt), auf festem Boden bzw. im Wasser (Wasserarten). Eigener Spawner statt Biom-Spawns:
 * unabhaengig von Licht, Spawnboden-Tags und Mob-Obergrenzen, und die Art steht beim Erscheinen fest.
 */
public final class WildAlienSpawner {
	private static final int INTERVAL = 60;
	private static final double RADIUS = 64.0;
	private static final int ATTEMPTS = 6;

	private WildAlienSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % INTERVAL == 0) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		Optional<Registry<AlienDefinition>> registry = AlienRegistry.registry(server.getRegistryManager());
		if (registry.isEmpty()) {
			return;
		}
		for (ServerWorld world : server.getWorlds()) {
			if (world.getPlayers().isEmpty()) {
				continue;
			}
			List<Identifier> natives = natives(registry.get(), world.getRegistryKey().getValue());
			if (natives.isEmpty()) {
				continue;
			}
			for (ServerPlayerEntity player : world.getPlayers(p -> !p.isSpectator())) {
				populate(world, player, registry.get(), natives);
			}
		}
	}

	/** Arten mit dieser Welt als Heimat. */
	public static List<Identifier> natives(Registry<AlienDefinition> registry, Identifier world) {
		List<Identifier> list = new ArrayList<>();
		for (Identifier id : registry.getIds()) {
			AlienDefinition alien = registry.get(id);
			if (alien != null && alien.dna().species().map(s -> s.home().equals(world)).orElse(false)) {
				list.add(id);
			}
		}
		return list;
	}

	private static void populate(ServerWorld world, ServerPlayerEntity player, Registry<AlienDefinition> registry, List<Identifier> natives) {
		Random random = world.getRandom();
		List<WildAlienEntity> nearby = world.getEntitiesByType(ModEntities.WILD_ALIEN, player.getBoundingBox().expand(RADIUS), e -> true);
		List<Identifier> wanted = new ArrayList<>();
		for (Identifier id : natives) {
			AlienDefinition.Species species = registry.get(id).dna().species().orElseThrow();
			long count = nearby.stream().filter(e -> e.alien().map(id::equals).orElse(false)).count();
			if (count < species.max()) {
				wanted.add(id);
			}
		}
		if (wanted.isEmpty()) {
			return;
		}
		Identifier alien = wanted.get(random.nextInt(wanted.size()));
		AlienDefinition.Species species = registry.get(alien).dna().species().orElseThrow();
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = 24 + random.nextDouble() * 16;
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			BlockPos spot = spot(world, x, z, species.aquatic());
			if (spot == null) {
				continue;
			}
			int size = species.group().get(0) + random.nextInt(Math.max(1, species.group().get(1) - species.group().get(0) + 1));
			for (int i = 0; i < size; i++) {
				BlockPos at = i == 0 ? spot : spot(world, spot.getX() + random.nextInt(7) - 3, spot.getZ() + random.nextInt(7) - 3, species.aquatic());
				if (at == null) {
					continue;
				}
				WildAlienEntity entity = ModEntities.WILD_ALIEN.create(world);
				if (entity == null) {
					return;
				}
				entity.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
				entity.setAlien(alien);
				entity.initialize(world, world.getLocalDifficulty(at), SpawnReason.NATURAL, null);
				if (world.isSpaceEmpty(entity)) {
					world.spawnEntity(entity);
				} else {
					entity.discard();
				}
			}
			return;
		}
	}

	/** Spawnstelle in Spalte x/z: auf festem Boden (Landarten) bzw. im Wasser (Wasserarten); null, wenn ungeeignet. */
	private static BlockPos spot(ServerWorld world, int x, int z, boolean aquatic) {
		if (world.getChunkManager().getWorldChunk(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z)) == null) {
			return null;
		}
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		BlockState below = world.getBlockState(top.down());
		boolean water = below.getFluidState().isIn(FluidTags.WATER);
		if (aquatic) {
			return water ? top.down(2) : null;
		}
		if (water || !below.isSolidBlock(world, top.down()) || below.getFluidState().isIn(FluidTags.LAVA)) {
			return null;
		}
		return top;
	}
}
