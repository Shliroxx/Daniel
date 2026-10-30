package com.santiq.kingdomomnitrix.dungeon;

import com.santiq.kingdomomnitrix.dungeon.WaterwayLayout.Room;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * Baut die Geheime Wasserstrasse Block fuer Block (kein Strukturdatei-Format noetig). Stil wie in Kingdom Hearts:
 * Steinziegel mit Moos und Rissen, blaugruenes Prismarin-Mosaik, Seelaternen; Kanal mit Wasser, Leiterschacht nach oben.
 */
final class WaterwayBuilder {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	private static final int DEPTH = 24;

	private WaterwayBuilder() {
	}

	private static void set(ServerWorld world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state, FLAGS);
	}

	private static void fill(ServerWorld world, BlockPos o, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(world, o.add(x, y, z), state);
				}
			}
		}
	}

	/** Wandstein mit Abwechslung (Moos, Risse) — fest nach Lage, damit ein Neuaufbau gleich aussieht. */
	private static BlockState wall(int x, int y, int z) {
		int h = Math.floorMod(x * 73856093 ^ y * 19349663 ^ z * 83492791, 10);
		return h < 2 ? Blocks.MOSSY_STONE_BRICKS.getDefaultState() : h < 3 ? Blocks.CRACKED_STONE_BRICKS.getDefaultState()
				: Blocks.STONE_BRICKS.getDefaultState();
	}

	private static void loadChunks(ServerWorld world, BlockPos o) {
		for (int x = o.getX() - 32; x <= o.getX() + 32; x += 16) {
			for (int z = o.getZ() - 140; z <= o.getZ() + 16; z += 16) {
				world.getChunk(x >> 4, z >> 4);
			}
		}
	}

	static void build(ServerWorld world, BlockPos o) {
		loadChunks(world, o);
		for (Room room : WaterwayLayout.ROOMS) {
			shell(world, o, room);
		}
		for (Room room : WaterwayLayout.ROOMS) {
			if (room != WaterwayLayout.HALL) {
				fill(world, o, -1, 0, room.startZ(), 1, 2, room.startZ(), Blocks.AIR.getDefaultState());
			}
		}
		hall(world, o);
		canal(world, o);
		puzzle(world, o);
		arena(world, o);
		guardian(world, o);
		bridge(world, o);
		treasure(world, o);
		boss(world, o);
	}

	/** Raum: Waende/Boden/Decke aus Stein, innen Luft, Boden aus poliertem Andesit mit Mosaik-Mittelstreifen, Licht. */
	private static void shell(ServerWorld world, BlockPos o, Room room) {
		int hw = room.halfWidth();
		for (int x = -hw - 1; x <= hw + 1; x++) {
			for (int y = -1; y <= room.height(); y++) {
				for (int z = room.endZ() - 1; z <= room.startZ(); z++) {
					boolean inside = x >= -hw && x <= hw && y >= 0 && y < room.height() && z >= room.endZ() && z < room.startZ();
					BlockState state;
					if (inside) {
						state = Blocks.AIR.getDefaultState();
					} else if (y == -1) {
						state = Math.abs(x) <= 1 && x == 0 ? Blocks.PRISMARINE_BRICKS.getDefaultState()
								: (x + z) % 2 == 0 ? Blocks.POLISHED_ANDESITE.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState();
					} else {
						state = wall(x, y, z);
					}
					set(world, o.add(x, y, z), state);
				}
			}
		}
		// Mosaikband an den Langwaenden und Licht in der Decke
		for (int z = room.endZ(); z < room.startZ(); z++) {
			BlockState band = Math.floorMod(z, 4) == 0 ? Blocks.DARK_PRISMARINE.getDefaultState() : Blocks.PRISMARINE_BRICKS.getDefaultState();
			set(world, o.add(-hw - 1, 2, z), band);
			set(world, o.add(hw + 1, 2, z), band);
			if (Math.floorMod(z, 4) == 1) {
				set(world, o.add(0, room.height(), z), Blocks.SEA_LANTERN.getDefaultState());
				set(world, o.add(-hw - 1, room.height() - 2, z), Blocks.SEA_LANTERN.getDefaultState());
				set(world, o.add(hw + 1, room.height() - 2, z), Blocks.SEA_LANTERN.getDefaultState());
			}
		}
	}

	private static void hall(ServerWorld world, BlockPos o) {
		Room hall = WaterwayLayout.HALL;
		// Leiterschacht: von der Oberflaeche bis zum Boden der Halle, Leiter an einer Saeule
		int top = DEPTH;
		fill(world, o, -1, hall.height(), -4, 1, top - 1, -1, Blocks.STONE_BRICKS.getDefaultState());
		fill(world, o, 0, hall.height(), -2, 0, top + 4, -2, Blocks.AIR.getDefaultState());
		fill(world, o, 0, 0, -3, 0, hall.height() - 1, -3, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
		for (int y = 0; y < top; y++) {
			set(world, o.add(0, y, -2), Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.SOUTH));
		}
		// Kanalhaeuschen an der Oberflaeche (Plattform, Waende, Dach, Oeffnung nach Sueden)
		fill(world, o, -4, top - 1, -6, 4, top - 1, 8, Blocks.STONE_BRICKS.getDefaultState());
		fill(world, o, -4, top, -6, 4, top + 5, 8, Blocks.AIR.getDefaultState());
		fill(world, o, -2, top, -4, 2, top + 3, 0, Blocks.STONE_BRICKS.getDefaultState());
		fill(world, o, -1, top, -3, 1, top + 2, -1, Blocks.AIR.getDefaultState());
		fill(world, o, -1, top, 0, 1, top + 2, 0, Blocks.AIR.getDefaultState());
		fill(world, o, -2, top + 4, -4, 2, top + 4, 0, Blocks.STONE_BRICK_SLAB.getDefaultState());
		set(world, o.add(0, top - 1, -2), Blocks.AIR.getDefaultState());
		set(world, o.add(0, top - 1, -2), Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.SOUTH));
		set(world, o.add(0, top + 3, -2), Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
		set(world, o.add(-1, top + 3, 0), Blocks.DARK_PRISMARINE.getDefaultState());
		set(world, o.add(1, top + 3, 0), Blocks.DARK_PRISMARINE.getDefaultState());
		// Halle: Wandbild-Nische, Banner-Saeulen
		fill(world, o, -3, 1, hall.endZ(), 3, 4, hall.endZ(), Blocks.AIR.getDefaultState());
		for (int x : new int[]{-4, 4}) {
			fill(world, o, x, 0, -6, x, hall.height() - 1, -6, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
		}
		set(world, o.add(0, 0, -7), Blocks.LECTERN.getDefaultState());
	}

	private static void canal(ServerWorld world, BlockPos o) {
		Room canal = WaterwayLayout.CANAL;
		for (int z = canal.endZ(); z < canal.startZ() - 1; z++) {
			set(world, o.add(0, -1, z), Blocks.WATER.getDefaultState());
			set(world, o.add(0, -2, z), Blocks.PRISMARINE.getDefaultState());
			set(world, o.add(-1, -1, z), Blocks.PRISMARINE_BRICK_SLAB.getDefaultState());
			set(world, o.add(1, -1, z), Blocks.PRISMARINE_BRICK_SLAB.getDefaultState());
		}
		// Seitennischen mit Faessern
		for (int z : new int[]{-18, -28}) {
			for (int side : new int[]{-1, 1}) {
				int x = side * (canal.halfWidth() + 1);
				fill(world, o, x, 0, z, x, 1, z, Blocks.AIR.getDefaultState());
				set(world, o.add(x, 0, z), Blocks.BARREL.getDefaultState());
			}
		}
	}

	static BlockPos leverPos(BlockPos o, int index) {
		Room puzzle = WaterwayLayout.PUZZLE;
		return o.add(-3 + 2 * index, 1, (puzzle.startZ() + puzzle.endZ()) / 2);
	}

	private static BlockPos muralPos(BlockPos o, int index) {
		return o.add(-3 + 2 * index, 4, WaterwayLayout.ARENA.startZ());
	}

	private static void puzzle(ServerWorld world, BlockPos o) {
		for (int i = 0; i < 4; i++) {
			BlockPos lever = leverPos(o, i);
			set(world, lever.down(), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
			set(world, lever, Blocks.LEVER.getDefaultState().with(LeverBlock.FACE, BlockFace.FLOOR).with(LeverBlock.FACING, Direction.NORTH));
		}
		// Rahmen um das Wandbild (Lichter darin setzt die Runde)
		fill(world, o, -4, 3, WaterwayLayout.ARENA.startZ(), 4, 5, WaterwayLayout.ARENA.startZ(), Blocks.DARK_PRISMARINE.getDefaultState());
	}

	private static void arena(ServerWorld world, BlockPos o) {
		Room arena = WaterwayLayout.ARENA;
		for (int x : new int[]{-5, 5}) {
			for (int z : new int[]{arena.startZ() - 4, arena.endZ() + 3}) {
				fill(world, o, x, 0, z, x, arena.height() - 1, z, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
			}
		}
		// Geheimkammer im Osten
		int z0 = WaterwayLayout.SECRET_WALL_Z;
		fill(world, o, WaterwayLayout.SECRET_X0 - 1, -1, z0 - 3, WaterwayLayout.SECRET_X1 + 1, 4, z0 + 3, Blocks.STONE_BRICKS.getDefaultState());
		fill(world, o, WaterwayLayout.SECRET_X0, 0, z0 - 2, WaterwayLayout.SECRET_X1, 3, z0 + 2, Blocks.AIR.getDefaultState());
		set(world, o.add(WaterwayLayout.SECRET_X1, 3, z0), Blocks.SEA_LANTERN.getDefaultState());
		set(world, o.add(WaterwayLayout.SECRET_X1, 0, z0), Blocks.CHEST.getDefaultState()
				.with(net.minecraft.block.ChestBlock.FACING, Direction.WEST));
		fill(world, o, arena.halfWidth() + 1, 0, z0, WaterwayLayout.SECRET_X0 - 1, 1, z0, Blocks.AIR.getDefaultState());
	}

	private static void guardian(ServerWorld world, BlockPos o) {
		Room room = WaterwayLayout.GUARDIAN;
		for (int x : new int[]{-4, 4}) {
			fill(world, o, x, 0, room.endZ() + 2, x, room.height() - 1, room.endZ() + 2, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
		}
	}

	private static void bridge(ServerWorld world, BlockPos o) {
		Room room = WaterwayLayout.BRIDGE;
		int hw = room.halfWidth();
		int bottom = -WaterwayLayout.PIT_DEPTH;
		// Schacht: Waende nach unten verlaengern, Boden ausheben, unten Wasser
		fill(world, o, -hw - 1, bottom - 1, room.endZ() - 1, hw + 1, -1, room.startZ(), Blocks.STONE_BRICKS.getDefaultState());
		fill(world, o, -hw, bottom, room.endZ(), hw, -1, room.startZ() - 1, Blocks.AIR.getDefaultState());
		fill(world, o, -hw, bottom, room.endZ(), hw, bottom + 1, room.startZ() - 1, Blocks.WATER.getDefaultState());
		fill(world, o, -hw, bottom - 1, room.endZ(), hw, bottom - 1, room.startZ() - 1, Blocks.PRISMARINE.getDefaultState());
		// Bruecke 3 breit
		fill(world, o, -1, -1, room.endZ(), 1, -1, room.startZ() - 1, Blocks.STONE_BRICKS.getDefaultState());
		for (int z = room.endZ(); z < room.startZ(); z += 3) {
			set(world, o.add(-2, 0, z), Blocks.STONE_BRICK_WALL.getDefaultState());
			set(world, o.add(2, 0, z), Blocks.STONE_BRICK_WALL.getDefaultState());
		}
		// Leiter zurueck nach oben (Suedwand)
		for (int y = bottom; y <= 0; y++) {
			set(world, o.add(hw, y, room.startZ() - 1), Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
		}
		for (int z = room.endZ() + 2; z < room.startZ(); z += 5) {
			set(world, o.add(-hw, bottom - 1, z), Blocks.SEA_LANTERN.getDefaultState());
			set(world, o.add(hw, bottom - 1, z), Blocks.SEA_LANTERN.getDefaultState());
		}
	}

	static BlockPos[] treasureChests(BlockPos o) {
		int z = WaterwayLayout.TREASURE.endZ() + 1;
		return new BlockPos[]{o.add(-2, 0, z), o.add(0, 0, z), o.add(2, 0, z)};
	}

	private static void treasure(ServerWorld world, BlockPos o) {
		for (BlockPos chest : treasureChests(o)) {
			set(world, chest, Blocks.CHEST.getDefaultState().with(net.minecraft.block.ChestBlock.FACING, Direction.SOUTH));
			set(world, chest.down(), Blocks.GOLD_BLOCK.getDefaultState());
		}
	}

	private static void boss(ServerWorld world, BlockPos o) {
		Room room = WaterwayLayout.BOSS;
		for (int x : new int[]{-8, 8}) {
			for (int z = room.startZ() - 5; z >= room.endZ() + 3; z -= 7) {
				fill(world, o, x, 0, z, x, room.height() - 1, z, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
				set(world, o.add(x, 3, z + 1), Blocks.SEA_LANTERN.getDefaultState());
			}
		}
		// Herzlosen-Emblem im Boden
		int cz = (room.startZ() + room.endZ()) / 2;
		fill(world, o, -3, -1, cz - 3, 3, -1, cz + 3, Blocks.CRIMSON_NYLIUM.getDefaultState());
		fill(world, o, -2, -1, cz - 2, 2, -1, cz + 2, Blocks.BLACKSTONE.getDefaultState());
		set(world, o.add(0, -1, cz), Blocks.REDSTONE_BLOCK.getDefaultState());
	}

	static void openDoor(ServerWorld world, BlockPos o, int z) {
		fill(world, o, -1, 0, z, 1, 2, z, Blocks.AIR.getDefaultState());
	}

	static void closeDoor(ServerWorld world, BlockPos o, int z) {
		fill(world, o, -1, 0, z, 1, 2, z, Blocks.IRON_BARS.getDefaultState());
	}

	/** Neue Runde: Tueren, Hebel, Wandbild, Geheimwand, Faesser und Truhen. */
	static void resetRound(ServerWorld world, BlockPos o, boolean[] pattern, Random random) {
		closeDoor(world, o, WaterwayLayout.ARENA.startZ());
		closeDoor(world, o, WaterwayLayout.GUARDIAN.startZ());
		closeDoor(world, o, WaterwayLayout.BRIDGE.startZ());
		openDoor(world, o, WaterwayLayout.BOSS.startZ());
		for (int i = 0; i < 4; i++) {
			BlockPos lever = leverPos(o, i);
			set(world, lever, Blocks.LEVER.getDefaultState().with(LeverBlock.FACE, BlockFace.FLOOR).with(LeverBlock.FACING, Direction.NORTH));
			set(world, muralPos(o, i), pattern[i] ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.POLISHED_BLACKSTONE.getDefaultState());
		}
		int z0 = WaterwayLayout.SECRET_WALL_Z;
		int wallX = WaterwayLayout.ARENA.halfWidth() + 1;
		set(world, o.add(wallX, 0, z0), Blocks.CRACKED_STONE_BRICKS.getDefaultState());
		set(world, o.add(wallX, 1, z0), Blocks.CRACKED_STONE_BRICKS.getDefaultState());
		set(world, o.add(wallX - 1, 2, z0), Blocks.AIR.getDefaultState());
		set(world, o.add(wallX, 2, z0), Blocks.CRACKED_STONE_BRICKS.getDefaultState());
		set(world, o.add(wallX - 1, 2, z0), Blocks.GLOW_LICHEN.getDefaultState()
				.with(net.minecraft.block.MultifaceGrowthBlock.getProperty(Direction.EAST), true));
		WaterwayDungeon.fill(world, o.add(WaterwayLayout.SECRET_X1, 0, z0), WaterwayDungeon.treasure(world, random, true));
		for (int z : new int[]{-18, -28}) {
			for (int side : new int[]{-1, 1}) {
				BlockPos barrel = o.add(side * (WaterwayLayout.CANAL.halfWidth() + 1), 0, z);
				if (!world.getBlockState(barrel).isOf(Blocks.BARREL)) {
					set(world, barrel, Blocks.BARREL.getDefaultState());
				}
				WaterwayDungeon.fill(world, barrel, java.util.List.of(new net.minecraft.item.ItemStack(
						com.santiq.kingdomomnitrix.registry.ModItems.BOLT, 8 + random.nextInt(16))));
			}
		}
		BlockPos[] chests = treasureChests(o);
		for (int i = 0; i < chests.length; i++) {
			if (!world.getBlockState(chests[i]).isOf(Blocks.CHEST)) {
				set(world, chests[i], Blocks.CHEST.getDefaultState().with(net.minecraft.block.ChestBlock.FACING, Direction.SOUTH));
			}
			WaterwayDungeon.fill(world, chests[i], WaterwayDungeon.treasure(world, random, i == 1));
		}
	}
}
