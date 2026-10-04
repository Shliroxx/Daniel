package com.santiq.kingdomomnitrix.space;

import com.santiq.kingdomomnitrix.world.PlanetHubs;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;

/**
 * Landeplatz der Aphelion in einer Welt (Ratchet & Clank: runde Metallplattform mit gelbem Ring und Lichtern).
 * Wird beim ersten Anflug ueber die Galaxiekarte gebaut und je Welt gespeichert.
 */
public final class LandingPad {
	private static final String KEY = "kingdomomnitrix_landing_pad";
	private static final int RADIUS = 6;
	private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::fromNbt, null);

	private LandingPad() {
	}

	/** Mitte des Landeplatzes (Standflaeche); baut ihn beim ersten Aufruf an (x, z). */
	public static BlockPos ensure(ServerWorld world, int x, int z) {
		State state = world.getPersistentStateManager().getOrCreate(TYPE, KEY);
		if (state.center != null) {
			return state.center;
		}
		int[] site = PlanetHubs.site(world, x, z);
		x = site[0];
		z = site[1];
		world.getChunk(x >> 4, z >> 4);
		int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		// zu tief (Leere, z. B. Raumstation) → auf fester Hoehe bauen
		int y = ground <= world.getBottomY() + 1 ? 64 : PlanetHubs.prepare(world, x, z, ground);
		build(world, new BlockPos(x, y, z));
		PlanetHubs.build(world, new BlockPos(x, y, z));
		state.center = new BlockPos(x, y + 1, z);
		state.markDirty();
		return state.center;
	}

	private static void build(ServerWorld world, BlockPos base) {
		BlockState metal = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
		BlockState plate = Blocks.SMOOTH_STONE.getDefaultState();
		BlockState ring = Blocks.YELLOW_CONCRETE.getDefaultState();
		BlockState stripe = Blocks.BLACK_CONCRETE.getDefaultState();
		for (int dx = -RADIUS - 2; dx <= RADIUS + 2; dx++) {
			for (int dz = -RADIUS - 2; dz <= RADIUS + 2; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > RADIUS + 1.5) {
					continue;
				}
				BlockPos top = base.add(dx, 0, dz);
				// Luftraum frei machen
				for (int up = 1; up <= 12; up++) {
					world.setBlockState(top.up(up), Blocks.AIR.getDefaultState(), 2);
				}
				if (r > RADIUS + 0.5) {
					continue;
				}
				// Fundament bis zum Boden (hoechstens 8 tief)
				for (int down = 1; down <= 8; down++) {
					BlockPos below = top.down(down);
					if (!world.getBlockState(below).isReplaceable() && world.getBlockState(below).isSolidBlock(world, below)) {
						break;
					}
					world.setBlockState(below, plate, 2);
				}
				BlockState surface;
				if (r > RADIUS - 0.5) {
					surface = ((dx + dz) & 1) == 0 ? ring : stripe;   // Warnstreifen am Rand
				} else if (r > RADIUS - 1.5) {
					surface = ring;
				} else if (Math.abs(dx) <= 1 && Math.abs(dz) <= 2 && !(dz == 0 && dx == 0) && (Math.abs(dx) == 1 || dz == 0)) {
					surface = ring;   // „H“ in der Mitte
				} else {
					surface = (dx * dx + dz * dz) % 7 == 0 ? plate : metal;
				}
				world.setBlockState(top, surface, 2);
			}
		}
		// Lichter: vier Masten mit Laternen, Seelaternen im Boden
		for (int[] d : new int[][] {{RADIUS + 1, 0}, {-RADIUS - 1, 0}, {0, RADIUS + 1}, {0, -RADIUS - 1}}) {
			BlockPos foot = base.add(d[0], 0, d[1]);
			world.setBlockState(foot, Blocks.POLISHED_ANDESITE.getDefaultState(), 2);
			world.setBlockState(foot.up(), Blocks.IRON_BARS.getDefaultState(), 2);
			world.setBlockState(foot.up(2), Blocks.IRON_BARS.getDefaultState(), 2);
			world.setBlockState(foot.up(3), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), 2);
		}
		for (int[] d : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
			world.setBlockState(base.add(d[0], 0, d[1]), Blocks.SEA_LANTERN.getDefaultState(), 2);
		}
	}

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
}
