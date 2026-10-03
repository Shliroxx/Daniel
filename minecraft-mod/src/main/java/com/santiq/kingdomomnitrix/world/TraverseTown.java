package com.santiq.kingdomomnitrix.world;

import com.mojang.datafixers.util.Pair;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.arena.ArenaTerminalBlockEntity;
import com.santiq.kingdomomnitrix.npc.NpcSpawnItem;
import com.santiq.kingdomomnitrix.registry.ModBlocks;
import com.santiq.kingdomomnitrix.space.SpaceTravel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.mob.Monster;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Traverse Town: wird beim ersten Besuch einmal pro Welt auf festem Land im Stadtrand-Biom errichtet.
 * Aufbau aus dem Weltseed, also in jeder Welt anders: Platz mit Brunnen, vier Strassen mit Laternen,
 * bunte Haeuser mit Stufendaechern, Waffenladen (Terminal + Clank), Schmiede (Keyblade-Schmiede), Arena im Sueden.
 * Danach ist alles normale Minecraft-Welt: abbauen und bauen erlaubt.
 */
public final class TraverseTown {
	public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, KingdomOmnitrix.id("traverse_town"));
	private static final RegistryKey<Biome> DISTRICT = RegistryKey.of(RegistryKeys.BIOME, KingdomOmnitrix.id("traverse_town/district"));
	/** Halbe Kantenlaenge des Stadtgebiets. */
	public static final int RADIUS = 40;
	private static final int PLAZA = 9;
	private static final int ARENA_RADIUS = 10;
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private static final BlockState[] WALLS = {
			Blocks.ORANGE_TERRACOTTA.getDefaultState(), Blocks.YELLOW_TERRACOTTA.getDefaultState(),
			Blocks.LIGHT_BLUE_TERRACOTTA.getDefaultState(), Blocks.WHITE_TERRACOTTA.getDefaultState(),
			Blocks.RED_TERRACOTTA.getDefaultState(), Blocks.BRICKS.getDefaultState()};
	private static final BlockState[] ROOFS = {
			Blocks.BLUE_TERRACOTTA.getDefaultState(), Blocks.RED_NETHER_BRICKS.getDefaultState(),
			Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.PURPLE_TERRACOTTA.getDefaultState()};

	private TraverseTown() {
	}

	/** Markiert Gegner, die absichtlich in der Stadt erscheinen (Arena), damit der Schutz sie nicht entfernt. */
	public static final String ALLOWED_TAG = "kingdomomnitrix_town_allowed";

	public static void register() {
		SpaceTravel.registerArrival(WORLD, TraverseTown::ensure);
		// Die Stadt ist ein sicherer Ort: Monster entstehen dort nicht (Arena-Gegner sind markiert und erlaubt).
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof Monster && world.getRegistryKey().equals(WORLD) && !entity.getCommandTags().contains(ALLOWED_TAG)) {
				BlockPos center = center(world);
				if (center != null && Math.abs(entity.getBlockX() - center.getX()) <= RADIUS
						&& Math.abs(entity.getBlockZ() - center.getZ()) <= RADIUS) {
					entity.discard();
				}
			}
		});
	}

	/** Mitte des Stadtplatzes, falls die Stadt schon steht (baut nichts). */
	public static BlockPos center(ServerWorld world) {
		State state = world.getPersistentStateManager().get(TYPE, "kingdomomnitrix_traverse_town");
		return state != null ? state.center : null;
	}

	// --- Gespeicherter Ort ----------------------------------------------------------------------

	private static final class State extends PersistentState {
		private BlockPos center;

		static State fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			State state = new State();
			if (nbt.contains("X")) {
				state.center = new BlockPos(nbt.getInt("X"), nbt.getInt("Y"), nbt.getInt("Z"));
			}
			return state;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			if (center != null) {
				nbt.putInt("X", center.getX());
				nbt.putInt("Y", center.getY());
				nbt.putInt("Z", center.getZ());
			}
			return nbt;
		}
	}

	private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::fromNbt, null);

	/** Mitte des Stadtplatzes; baut die Stadt beim ersten Aufruf. */
	public static BlockPos ensure(ServerWorld world) {
		State state = world.getPersistentStateManager().getOrCreate(TYPE, "kingdomomnitrix_traverse_town");
		if (state.center != null) {
			// Welten von vor dem Dungeon bekommen ihn beim naechsten Besuch nachgebaut
			com.santiq.kingdomomnitrix.dungeon.WaterwayDungeon.ensure(world, state.center);
			return state.center;
		}
		long started = System.currentTimeMillis();
		BlockPos center = findSite(world);
		build(world, center, Random.create(world.getSeed() ^ center.asLong()));
		state.center = center;
		state.markDirty();
		com.santiq.kingdomomnitrix.dungeon.WaterwayDungeon.ensure(world, center);
		KingdomOmnitrix.LOGGER.info("Traverse Town bei {} errichtet ({} ms)", center, System.currentTimeMillis() - started);
		return center;
	}

	private static BlockPos findSite(ServerWorld world) {
		Pair<BlockPos, RegistryEntry<Biome>> found = world.locateBiome(entry -> entry.matchesKey(DISTRICT), BlockPos.ORIGIN, 2400, 32, 64);
		BlockPos base = found != null ? found.getFirst() : BlockPos.ORIGIN;
		loadArea(world, base);
		// Hoehe: Mittel aus Stichproben, nicht unter dem Meeresspiegel
		int sum = 0;
		int samples = 0;
		for (int dx = -24; dx <= 24; dx += 12) {
			for (int dz = -24; dz <= 24; dz += 12) {
				sum += world.getTopY(Heightmap.Type.OCEAN_FLOOR, base.getX() + dx, base.getZ() + dz);
				samples++;
			}
		}
		int y = MathHelper.clamp(sum / samples, world.getSeaLevel() + 2, world.getSeaLevel() + 50);
		return new BlockPos(base.getX(), y, base.getZ());
	}

	private static void loadArea(ServerWorld world, BlockPos center) {
		for (int x = center.getX() - RADIUS - 16; x <= center.getX() + RADIUS + 16; x += 16) {
			for (int z = center.getZ() - RADIUS - 16; z <= center.getZ() + RADIUS + 16; z += 16) {
				world.getChunk(x >> 4, z >> 4);
			}
		}
	}

	// --- Aufbau ---------------------------------------------------------------------------------

	private static void build(ServerWorld world, BlockPos center, Random random) {
		loadArea(world, center);
		int cx = center.getX();
		int cz = center.getZ();
		int y0 = center.getY();
		levelTerrain(world, cx, cz, y0);
		buildPlaza(world, cx, cz, y0);
		for (Direction direction : Direction.Type.HORIZONTAL) {
			buildStreet(world, cx, cz, y0, direction);
		}
		int arenaZ = cz + RADIUS - ARENA_RADIUS - 3;
		buildArena(world, cx, arenaZ, y0);
		boolean shop = false;
		boolean forge = false;
		for (Direction direction : Direction.Type.HORIZONTAL) {
			for (int side : new int[]{1, -1}) {
				int along = 13;
				int limit = direction == Direction.SOUTH ? RADIUS - 2 * ARENA_RADIUS - 8 : RADIUS - 4;
				while (true) {
					int width = 7 + random.nextInt(3);
					int depth = 7 + random.nextInt(3);
					if (along + width > limit) {
						break;
					}
					Role role = Role.HOME;
					if (!shop && direction == Direction.EAST) {
						role = Role.WEAPON_SHOP;
						shop = true;
					} else if (!forge && direction == Direction.WEST) {
						role = Role.FORGE;
						forge = true;
					}
					House house = new House(world, cx, cz, y0, direction, side, along, width, depth, 5 + random.nextInt(2), role);
					house.build(random);
					along += width + 2 + random.nextInt(2);
				}
			}
		}
		NpcSpawnItem.spawn(world, KingdomOmnitrix.id("yen_sid"), new BlockPos(cx, y0, cz - 6), 0.0f);
		NpcSpawnItem.spawn(world, KingdomOmnitrix.id("max"), new BlockPos(cx + 6, y0, cz), 90.0f);
		scatterCrates(world, cx, cz, y0, random);
	}

	private static void set(ServerWorld world, int x, int y, int z, BlockState state) {
		world.setBlockState(new BlockPos(x, y, z), state, FLAGS);
	}

	/** Ebnet das Gebiet auf Platzhoehe; am Rand weicher Uebergang in die Landschaft. */
	private static void levelTerrain(ServerWorld world, int cx, int cz, int y0) {
		int blend = 8;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				int ground = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
				int distance = Math.max(Math.abs(dx), Math.abs(dz));
				int target = y0 - 1;
				if (distance > RADIUS - blend) {
					float t = (distance - (RADIUS - blend)) / (float) blend;
					target = Math.round(MathHelper.lerp(t, y0 - 1, ground));
				}
				for (int y = Math.min(ground, target) - 1; y <= target; y++) {
					BlockState state = y == target ? Blocks.GRASS_BLOCK.getDefaultState()
							: y > target - 4 ? Blocks.DIRT.getDefaultState() : Blocks.STONE.getDefaultState();
					set(world, x, y, z, state);
				}
				int top = Math.max(ground + 1, y0 + 26);
				for (int y = target + 1; y <= top; y++) {
					if (!world.getBlockState(new BlockPos(x, y, z)).isAir()) {
						set(world, x, y, z, Blocks.AIR.getDefaultState());
					}
				}
			}
		}
	}

	private static void buildPlaza(ServerWorld world, int cx, int cz, int y0) {
		for (int dx = -PLAZA; dx <= PLAZA; dx++) {
			for (int dz = -PLAZA; dz <= PLAZA; dz++) {
				int ring = Math.max(Math.abs(dx), Math.abs(dz));
				BlockState floor = ring == PLAZA ? Blocks.STONE_BRICKS.getDefaultState()
						: (dx + dz) % 2 == 0 ? Blocks.POLISHED_ANDESITE.getDefaultState() : Blocks.POLISHED_DIORITE.getDefaultState();
				set(world, cx + dx, y0 - 1, cz + dz, floor);
			}
		}
		// Brunnen: Becken mit Wasser, Saeule mit Laterne
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int ring = Math.max(Math.abs(dx), Math.abs(dz));
				set(world, cx + dx, y0 - 2, cz + dz, Blocks.STONE_BRICKS.getDefaultState());
				if (ring == 2) {
					set(world, cx + dx, y0 - 1, cz + dz, Blocks.STONE_BRICKS.getDefaultState());
					set(world, cx + dx, y0, cz + dz, Blocks.STONE_BRICK_WALL.getDefaultState());
				} else {
					set(world, cx + dx, y0 - 1, cz + dz, Blocks.WATER.getDefaultState());
				}
			}
		}
		for (int y = y0 - 1; y <= y0 + 2; y++) {
			set(world, cx, y, cz, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
		}
		set(world, cx, y0 + 3, cz, Blocks.SEA_LANTERN.getDefaultState());
		for (int sx : new int[]{-PLAZA + 1, PLAZA - 1}) {
			for (int sz : new int[]{-PLAZA + 1, PLAZA - 1}) {
				lamp(world, cx + sx, y0, cz + sz, 3);
			}
		}
	}

	private static void buildStreet(ServerWorld world, int cx, int cz, int y0, Direction direction) {
		int ax = direction.getOffsetX();
		int az = direction.getOffsetZ();
		int px = -az;
		int pz = ax;
		// Die Suedstrasse endet am Arena-Eingang.
		int end = direction == Direction.SOUTH ? RADIUS - 2 * ARENA_RADIUS - 4 : RADIUS - 2;
		for (int along = PLAZA + 1; along <= end; along++) {
			for (int w = -2; w <= 2; w++) {
				BlockState floor = Math.abs(w) == 2 ? Blocks.STONE_BRICKS.getDefaultState()
						: (along + w) % 3 == 0 ? Blocks.COBBLESTONE.getDefaultState() : Blocks.POLISHED_ANDESITE.getDefaultState();
				set(world, cx + ax * along + px * w, y0 - 1, cz + az * along + pz * w, floor);
			}
			if (along % 8 == 0) {
				for (int side : new int[]{-3, 3}) {
					lamp(world, cx + ax * along + px * side, y0, cz + az * along + pz * side, 3);
				}
			}
		}
	}

	private static void lamp(ServerWorld world, int x, int y, int z, int height) {
		for (int i = 0; i < height; i++) {
			set(world, x, y + i, z, Blocks.DARK_OAK_FENCE.getDefaultState());
		}
		set(world, x, y + height, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false));
	}

	/** Runde Arena mit Mauer, Saeulen mit Laternen, Eingang nach Norden und Arena-Terminal am Eingang. */
	private static void buildArena(ServerWorld world, int ax, int az, int y0) {
		for (int dx = -ARENA_RADIUS - 2; dx <= ARENA_RADIUS + 2; dx++) {
			for (int dz = -ARENA_RADIUS - 2; dz <= ARENA_RADIUS + 2; dz++) {
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (distance <= ARENA_RADIUS + 0.5) {
					BlockState floor = distance < 2.5 ? Blocks.CHISELED_STONE_BRICKS.getDefaultState()
							: (int) distance % 3 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState() : Blocks.SMOOTH_STONE.getDefaultState();
					set(world, ax + dx, y0 - 1, az + dz, floor);
				} else if (distance <= ARENA_RADIUS + 1.6) {
					boolean entrance = dz < 0 && Math.abs(dx) <= 1;
					set(world, ax + dx, y0 - 1, az + dz, Blocks.STONE_BRICKS.getDefaultState());
					if (!entrance) {
						for (int y = y0; y <= y0 + 2; y++) {
							set(world, ax + dx, y, az + dz, Blocks.STONE_BRICKS.getDefaultState());
						}
						set(world, ax + dx, y0 + 3, az + dz, Blocks.STONE_BRICK_WALL.getDefaultState());
					}
				}
			}
		}
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI / 8 + i * Math.PI / 4;
			int x = ax + (int) Math.round(Math.cos(angle) * (ARENA_RADIUS + 1));
			int z = az + (int) Math.round(Math.sin(angle) * (ARENA_RADIUS + 1));
			for (int y = y0; y <= y0 + 3; y++) {
				set(world, x, y, z, Blocks.POLISHED_DEEPSLATE.getDefaultState());
			}
			set(world, x, y0 + 4, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false));
		}
		BlockPos terminal = new BlockPos(ax + 2, y0, az - ARENA_RADIUS + 1);
		world.setBlockState(terminal, ModBlocks.ARENA_TERMINAL.getDefaultState(), Block.NOTIFY_ALL);
		if (world.getBlockEntity(terminal) instanceof ArenaTerminalBlockEntity entity) {
			entity.setArena(new BlockPos(ax, y0, az), ARENA_RADIUS);
		}
	}

	private static void scatterCrates(ServerWorld world, int cx, int cz, int y0, Random random) {
		for (int i = 0; i < 10; i++) {
			int x = cx + random.nextBetween(-RADIUS + 6, RADIUS - 6);
			int z = cz + random.nextBetween(-RADIUS + 6, RADIUS - 6);
			BlockPos pos = new BlockPos(x, y0, z);
			if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
				set(world, x, y0, z, ModBlocks.BOLT_CRATE.getDefaultState());
			}
		}
	}

	// --- Haeuser --------------------------------------------------------------------------------

	private enum Role { HOME, WEAPON_SHOP, FORGE }

	/**
	 * Ein Haus an einer Strasse. Lokale Koordinaten: u entlang der Strasse, v von der Strasse weg, y nach oben.
	 * Die Haustuer zeigt immer zur Strasse.
	 */
	private record House(ServerWorld world, int cx, int cz, int y0, Direction street, int side, int along,
			int width, int depth, int height, Role role) {
		private int worldX(int u, int v) {
			int px = -street.getOffsetZ() * side;
			return cx + street.getOffsetX() * (along + u) + px * (4 + v);
		}

		private int worldZ(int u, int v) {
			int pz = street.getOffsetX() * side;
			return cz + street.getOffsetZ() * (along + u) + pz * (4 + v);
		}

		private void put(int u, int v, int y, BlockState state) {
			set(world, worldX(u, v), y, worldZ(u, v), state);
		}

		/** Richtung von der Haustuer zur Strasse. */
		private Direction towardStreet() {
			return Direction.fromVector(street.getOffsetZ() * side, 0, -street.getOffsetX() * side);
		}

		void build(Random random) {
			BlockState wall = WALLS[random.nextInt(WALLS.length)];
			BlockState roof = ROOFS[random.nextInt(ROOFS.length)];
			BlockState frame = Blocks.DARK_OAK_LOG.getDefaultState();
			for (int u = 0; u < width; u++) {
				for (int v = 0; v < depth; v++) {
					put(u, v, y0 - 1, Blocks.SPRUCE_PLANKS.getDefaultState());
					boolean edgeU = u == 0 || u == width - 1;
					boolean edgeV = v == 0 || v == depth - 1;
					for (int y = y0; y < y0 + height; y++) {
						if (!edgeU && !edgeV) {
							continue;
						}
						BlockState state = edgeU && edgeV ? frame : wall;
						int level = y - y0;
						boolean window = !(edgeU && edgeV) && (level == 2 || level == height - 2 && height > 5)
								&& ((edgeV ? u : v) % 2 == 1);
						put(u, v, y, window ? Blocks.GLASS.getDefaultState() : state);
					}
					put(u, v, y0 + height, Blocks.SPRUCE_PLANKS.getDefaultState());
				}
			}
			// Stufendach
			for (int layer = 0; ; layer++) {
				int u0 = layer - 1;
				int u1 = width - layer;
				int v0 = layer - 1;
				int v1 = depth - layer;
				if (u1 - u0 < 1 || v1 - v0 < 1) {
					break;
				}
				for (int u = u0; u <= u1; u++) {
					for (int v = v0; v <= v1; v++) {
						put(u, v, y0 + height + 1 + layer, roof);
					}
				}
			}
			// Tuer zur Strasse
			int doorU = width / 2;
			Direction facing = towardStreet();
			put(doorU, 0, y0, Blocks.DARK_OAK_DOOR.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			put(doorU, 0, y0 + 1, Blocks.DARK_OAK_DOOR.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
			put(doorU, -1, y0 - 1, Blocks.POLISHED_ANDESITE.getDefaultState());
			// Einrichtung
			put(1, depth - 2, y0, Blocks.LANTERN.getDefaultState());
			put(width - 2, 1, y0, Blocks.LANTERN.getDefaultState());
			switch (role) {
				case WEAPON_SHOP -> {
					put(width / 2, depth - 2, y0, ModBlocks.WEAPON_TERMINAL.getDefaultState());
					put(1, 1, y0, ModBlocks.BOLT_CRATE.getDefaultState());
					NpcSpawnItem.spawn(world, KingdomOmnitrix.id("clank"), new BlockPos(worldX(width / 2, 2), y0, worldZ(width / 2, 2)),
							facing.asRotation());
				}
				case FORGE -> {
					put(width / 2, depth - 2, y0, ModBlocks.KEYBLADE_FORGE.getDefaultState());
					put(width / 2 - 1, depth - 2, y0, Blocks.ANVIL.getDefaultState());
					put(width / 2 + 1, depth - 2, y0, Blocks.BLAST_FURNACE.getDefaultState());
				}
				default -> {
					put(width - 2, depth - 2, y0, Blocks.BARREL.getDefaultState());
					put(1, 1, y0, Blocks.CRAFTING_TABLE.getDefaultState());
				}
			}
		}
	}
}
