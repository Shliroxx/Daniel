package com.santiq.kingdomomnitrix.world.structure;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.registry.ModStructures;
import com.santiq.kingdomomnitrix.world.content.PlanetBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * Ein Teil der Petrosapien-Siedlung: Platz (mit Obelisk und Laternen), Kuppelhuette oder Kiesweg. Alles wird aus den
 * gespeicherten Werten (Mitte, Radius, Saat) berechnet, damit jeder Chunk denselben Bau sieht; Zufall pro Block kommt
 * aus einem Hash der Position, nicht aus dem Chunk-Zufall.
 */
public class PetrosapienPiece extends StructurePiece {
	private static final int FLAGS = Block.NOTIFY_LISTENERS;
	private static final RegistryKey<LootTable> HUT_LOOT = RegistryKey.of(RegistryKeys.LOOT_TABLE, KingdomOmnitrix.id("chests/petrosapien_hut"));
	private static final RegistryKey<LootTable> RUIN_LOOT = RegistryKey.of(RegistryKeys.LOOT_TABLE, KingdomOmnitrix.id("chests/petrosapien_ruin"));

	private enum Kind { PLAZA, HUT, PATH }

	private final Kind kind;
	private final BlockPos origin;
	private final BlockPos target;
	private final int radius;
	private final boolean ruin;
	private final long seed;

	private PetrosapienPiece(Kind kind, BlockPos origin, BlockPos target, int radius, boolean ruin, long seed, BlockBox box) {
		super(ModStructures.PETROSAPIEN_PIECE, 0, box);
		this.kind = kind;
		this.origin = origin;
		this.target = target;
		this.radius = radius;
		this.ruin = ruin;
		this.seed = seed;
	}

	public PetrosapienPiece(NbtCompound nbt) {
		super(ModStructures.PETROSAPIEN_PIECE, nbt);
		this.kind = Kind.values()[MathHelper.clamp(nbt.getInt("Kind"), 0, Kind.values().length - 1)];
		this.origin = BlockPos.fromLong(nbt.getLong("Origin"));
		this.target = BlockPos.fromLong(nbt.getLong("Target"));
		this.radius = nbt.getInt("Radius");
		this.ruin = nbt.getBoolean("Ruin");
		this.seed = nbt.getLong("Seed");
	}

	static PetrosapienPiece plaza(BlockPos center, boolean ruin, long seed) {
		BlockBox box = new BlockBox(center.getX() - 10, center.getY() - 1, center.getZ() - 10, center.getX() + 10, center.getY() + 14,
				center.getZ() + 10);
		return new PetrosapienPiece(Kind.PLAZA, center, center, 9, ruin, seed, box);
	}

	static PetrosapienPiece hut(BlockPos pos, int radius, BlockPos plaza, boolean ruin, long seed) {
		int r = radius + 1;
		BlockBox box = new BlockBox(pos.getX() - r, pos.getY(), pos.getZ() - r, pos.getX() + r, pos.getY() + radius + 6, pos.getZ() + r);
		return new PetrosapienPiece(Kind.HUT, pos, plaza, radius, ruin, seed, box);
	}

	static PetrosapienPiece path(BlockPos plaza, BlockPos hut, int hutRadius, long seed) {
		BlockBox box = new BlockBox(Math.min(plaza.getX(), hut.getX()) - 1, Math.min(plaza.getY(), hut.getY()) - 3,
				Math.min(plaza.getZ(), hut.getZ()) - 1, Math.max(plaza.getX(), hut.getX()) + 1, Math.max(plaza.getY(), hut.getY()) + 3,
				Math.max(plaza.getZ(), hut.getZ()) + 1);
		return new PetrosapienPiece(Kind.PATH, plaza, hut, hutRadius, false, seed, box);
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		nbt.putInt("Kind", kind.ordinal());
		nbt.putLong("Origin", origin.asLong());
		nbt.putLong("Target", target.asLong());
		nbt.putInt("Radius", radius);
		nbt.putBoolean("Ruin", ruin);
		nbt.putLong("Seed", seed);
	}

	@Override
	public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random,
			BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		switch (kind) {
			case PLAZA -> plaza(world, chunkBox);
			case HUT -> hut(world, chunkBox);
			case PATH -> path(world, chunkBox);
		}
	}

	// --- Platz ----------------------------------------------------------------------------------------------------

	private void plaza(StructureWorldAccess world, BlockBox chunk) {
		int y = origin.getY();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius + 0.5) {
					continue;
				}
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				BlockState floor = d <= 6.0 ? smooth() : hash(x, y, z, 1) < 0.7 ? gravel() : smooth();
				if (ruin && hash(x, y, z, 2) < 0.25) {
					floor = lichen();
				}
				set(world, chunk, x, y, z, floor);
				foundation(world, chunk, x, y - 1, z);
				clearAbove(world, chunk, x, y + 1, z, 6);
			}
		}
		obelisk(world, chunk, y + 1);
		for (int i = 0; i < 4; i++) {
			int lx = origin.getX() + (i < 2 ? 6 : -6);
			int lz = origin.getZ() + (i % 2 == 0 ? 6 : -6);
			if (ruin && hash(lx, y, lz, 3) < 0.5) {
				set(world, chunk, lx, y + 1, lz, rock());   // umgestuerzte Laterne: nur der Stumpf
				continue;
			}
			set(world, chunk, lx, y + 1, lz, smooth());
			set(world, chunk, lx, y + 2, lz, smooth());
			set(world, chunk, lx, y + 3, lz, crystal());
			set(world, chunk, lx, y + 4, lz, cluster());
		}
	}

	/** Kristall-Obelisk: Sockel aus glattem Fels, spitz zulaufender Riesenkristall. Als Ruine abgebrochen und umgestuerzt. */
	private void obelisk(StructureWorldAccess world, BlockBox chunk, int y) {
		int x0 = origin.getX();
		int z0 = origin.getZ();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				set(world, chunk, x0 + dx, y, z0 + dz, smooth());
			}
		}
		int height = ruin ? 4 : 11;
		for (int h = 1; h <= height; h++) {
			double r = 1.6 * (1.0 - (double) h / 13.0);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx * dx + dz * dz <= r * r) {
						set(world, chunk, x0 + dx, y + h, z0 + dz, crystal());
					}
				}
			}
		}
		if (ruin) {
			// abgebrochene Spitze liegt quer ueber dem Platz
			for (int i = 2; i <= 8; i++) {
				set(world, chunk, x0 + i, y, z0, crystal());
				if (i < 6) {
					set(world, chunk, x0 + i, y, z0 + 1, crystal());
				}
			}
		} else {
			set(world, chunk, x0, y + height + 1, z0, cluster());
		}
	}

	// --- Huette ---------------------------------------------------------------------------------------------------

	private void hut(StructureWorldAccess world, BlockBox chunk) {
		int x0 = origin.getX();
		int y0 = origin.getY();
		int z0 = origin.getZ();
		double toPlazaX = target.getX() - x0;
		double toPlazaZ = target.getZ() - z0;
		double len = Math.max(1.0, Math.sqrt(toPlazaX * toPlazaX + toPlazaZ * toPlazaZ));
		double doorX = toPlazaX / len;
		double doorZ = toPlazaZ / len;
		int wallTop = y0 + 3;
		int r = radius + 1;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius + 0.5) {
					continue;
				}
				int x = x0 + dx;
				int z = z0 + dz;
				set(world, chunk, x, y0, z, ruin && hash(x, y0, z, 4) < 0.3 ? gravel() : smooth());
				foundation(world, chunk, x, y0 - 1, z);
				boolean wall = d > radius - 0.5;
				double along = dx * doorX + dz * doorZ;
				double across = Math.abs(-dx * doorZ + dz * doorX);
				boolean door = wall && along > 0 && across <= 0.7;
				for (int y = y0 + 1; y <= wallTop; y++) {
					BlockState state = Blocks.AIR.getDefaultState();
					if (wall && !(door && y <= y0 + 2)) {
						int sector = Math.floorMod((int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI / 4)), 8);
						state = y == y0 + 2 && sector % 2 == 0 ? crystal() : smooth();
					}
					if (ruin && !state.isAir() && y > y0 + 1 && hash(x, y, z, 5) < 0.45) {
						state = Blocks.AIR.getDefaultState();
					}
					set(world, chunk, x, y, z, state);
				}
				// Kuppel aus Riesenkristall
				for (int k = 1; k <= radius; k++) {
					int y = wallTop + k;
					double ring = radius * Math.sqrt(1.0 - Math.pow((double) k / (radius + 1), 2));
					BlockState state = Blocks.AIR.getDefaultState();
					if (d <= ring + 0.5 && (d > ring - 0.7 || k == radius)) {
						state = crystal();
					}
					if (ruin && !state.isAir() && hash(x, y, z, 6) < 0.7) {
						state = Blocks.AIR.getDefaultState();
					}
					set(world, chunk, x, y, z, state);
				}
			}
		}
		if (!ruin) {
			set(world, chunk, x0, wallTop + radius + 1, z0, crystal());
			set(world, chunk, x0, wallTop + radius + 2, z0, cluster());
		}
		// Einrichtung: Truhe gegenueber der Tuer, Kristall-Lampe, Steintisch
		int cx = x0 - (int) Math.round(doorX * (radius - 1));
		int cz = z0 - (int) Math.round(doorZ * (radius - 1));
		if (ruin || hash(x0, y0, z0, 7) < 0.6) {
			Direction facing = Direction.getFacing((float) doorX, 0.0f, (float) doorZ);
			if (set(world, chunk, cx, y0 + 1, cz, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing))
					&& world.getBlockEntity(new BlockPos(cx, y0 + 1, cz)) instanceof ChestBlockEntity chest) {
				chest.setLootTable(ruin ? RUIN_LOOT : HUT_LOOT, seed);
			}
		}
		int sx = x0 + (int) Math.round(-doorZ * (radius - 1));
		int sz = z0 + (int) Math.round(doorX * (radius - 1));
		set(world, chunk, sx, y0 + 1, sz, smooth());
		set(world, chunk, sx, y0 + 2, sz, cluster());
		if (ruin) {
			for (int i = 0; i < 6; i++) {
				int rx = x0 + Math.round((float) (hash(x0, i, z0, 8) * 2 - 1) * (radius + 2));
				int rz = z0 + Math.round((float) (hash(z0, i, x0, 9) * 2 - 1) * (radius + 2));
				int ry = surface(world, chunk, rx, rz, y0 + 3, y0 - 3);
				if (ry != Integer.MIN_VALUE) {
					set(world, chunk, rx, ry + 1, rz, i % 2 == 0 ? smooth() : crystal());
				}
			}
		}
	}

	// --- Weg ------------------------------------------------------------------------------------------------------

	private void path(StructureWorldAccess world, BlockBox chunk) {
		double dx = target.getX() - origin.getX();
		double dz = target.getZ() - origin.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0) {
			return;
		}
		int top = Math.max(origin.getY(), target.getY()) + 3;
		int bottom = Math.min(origin.getY(), target.getY()) - 3;
		for (double t = 9.5; t <= len - radius - 0.5; t += 0.5) {
			for (int side = -1; side <= 1; side++) {
				int x = MathHelper.floor(origin.getX() + 0.5 + dx / len * t - dz / len * side * 0.6);
				int z = MathHelper.floor(origin.getZ() + 0.5 + dz / len * t + dx / len * side * 0.6);
				int y = surface(world, chunk, x, z, top, bottom);
				if (y != Integer.MIN_VALUE && hash(x, y, z, 10) < 0.85) {
					set(world, chunk, x, y, z, hash(x, y, z, 11) < 0.75 ? gravel() : smooth());
				}
			}
		}
	}

	// --- Hilfen ---------------------------------------------------------------------------------------------------

	/** Oberster fester Block in der Spalte zwischen top und bottom; Pflanzen und Kristallbueschel darueber werden entfernt. */
	private static int surface(StructureWorldAccess world, BlockBox chunk, int x, int z, int top, int bottom) {
		if (!chunk.contains(x, chunk.getMinY(), z)) {
			return Integer.MIN_VALUE;
		}
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = top; y >= bottom; y--) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			if (state.isAir()) {
				continue;
			}
			if (state.isReplaceable() || !state.isOpaqueFullCube(world, pos)) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), FLAGS);
				continue;
			}
			return y;
		}
		return Integer.MIN_VALUE;
	}

	/** Fundament: Fels nach unten, bis fester Boden kommt (hoechstens acht Bloecke). */
	private static void foundation(StructureWorldAccess world, BlockBox chunk, int x, int y, int z) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int i = 0; i < 8; i++) {
			BlockState state = world.getBlockState(pos.set(x, y - i, z));
			if (!state.isAir() && !state.isReplaceable() && state.getFluidState().isEmpty()) {
				return;
			}
			set(world, chunk, x, y - i, z, PlanetBlocks.PETROPIA_ROCK.getDefaultState());
		}
	}

	private static void clearAbove(StructureWorldAccess world, BlockBox chunk, int x, int y, int z, int height) {
		for (int i = 0; i < height; i++) {
			if (!world.getBlockState(new BlockPos(x, y + i, z)).isAir()) {
				set(world, chunk, x, y + i, z, Blocks.AIR.getDefaultState());
			}
		}
	}

	private static boolean set(StructureWorldAccess world, BlockBox chunk, int x, int y, int z, BlockState state) {
		BlockPos pos = new BlockPos(x, y, z);
		if (!chunk.contains(pos)) {
			return false;
		}
		world.setBlockState(pos, state, FLAGS);
		return true;
	}

	/** Wert 0..1 aus Position, Saat und Kanal — gleich in jedem Chunk. */
	private double hash(int x, int y, int z, int channel) {
		long h = MathHelper.hashCode(x, y * 31 + channel, z) ^ seed;
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return ((h ^ (h >>> 33)) >>> 11) * 0x1.0p-53;
	}

	private static BlockState smooth() {
		return PlanetBlocks.PETROPIA_SMOOTH_ROCK.getDefaultState();
	}

	private static BlockState rock() {
		return PlanetBlocks.PETROPIA_ROCK.getDefaultState();
	}

	private static BlockState gravel() {
		return PlanetBlocks.PETROPIA_GRAVEL.getDefaultState();
	}

	private static BlockState lichen() {
		return PlanetBlocks.PETROPIA_LICHEN.getDefaultState();
	}

	private static BlockState crystal() {
		return PlanetBlocks.PETROPIA_GIANT_CRYSTAL.getDefaultState();
	}

	private static BlockState cluster() {
		return PlanetBlocks.PETROPIA_CLUSTER.getDefaultState();
	}
}
