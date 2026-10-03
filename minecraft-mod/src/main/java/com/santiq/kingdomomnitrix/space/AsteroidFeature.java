package com.santiq.kingdomomnitrix.space;

import com.mojang.serialization.Codec;
import com.santiq.kingdomomnitrix.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Asteroid im All: unregelmaessige, abgeflachte Gesteinskugel (Radius 3–8) in fuenf Arten — Gestein, Eis,
 * Kristall (Amethyst-Drusen nach aussen), glutfluessig (Magma-Adern, gluehender Kern) und Metall (Eisen, Kupfer,
 * mehr Raritanium) — mit Kruste, Kern und Erzadern. Groessere Asteroiden tragen Einschlagkrater.
 * Jeder Asteroid sieht anders aus (Zufall aus dem Weltseed).
 */
public class AsteroidFeature extends Feature<DefaultFeatureConfig> {
	/** Art des Asteroiden mit Gewicht (Haeufigkeit). */
	enum Kind {
		ROCK(40), ICE(18), CRYSTAL(14), MOLTEN(14), METAL(14);

		final int weight;

		Kind(int weight) {
			this.weight = weight;
		}

		static Kind pick(Random random) {
			int total = 0;
			for (Kind kind : values()) {
				total += kind.weight;
			}
			int roll = random.nextInt(total);
			for (Kind kind : values()) {
				roll -= kind.weight;
				if (roll < 0) {
					return kind;
				}
			}
			return ROCK;
		}
	}

	public AsteroidFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();
		Kind kind = Kind.pick(random);
		int radius = 3 + random.nextInt(6);
		double squash = 0.7 + random.nextDouble() * 0.6;
		long salt = random.nextLong();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		List<BlockPos> surface = new ArrayList<>();
		boolean placed = false;
		for (int dx = -radius - 2; dx <= radius + 2; dx++) {
			for (int dy = -radius - 2; dy <= radius + 2; dy++) {
				for (int dz = -radius - 2; dz <= radius + 2; dz++) {
					double wobble = noise(dx, dy, dz, salt) * 1.6;
					double distance = Math.sqrt(dx * dx + (dy / squash) * (dy / squash) + dz * dz) + wobble;
					if (distance > radius) {
						continue;
					}
					pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					boolean crust = distance > radius - 1.3;
					world.setBlockState(pos, crust ? crust(kind, random) : core(kind, random, distance / radius), Block.NOTIFY_LISTENERS);
					if (distance > radius - 0.9) {
						surface.add(pos.toImmutable());
					}
					placed = true;
				}
			}
		}
		if (!placed) {
			return false;
		}
		if (radius >= 5) {
			carveCraters(world, origin, radius, random);
		}
		if (kind == Kind.CRYSTAL) {
			growClusters(world, origin, surface, random);
		}
		return true;
	}

	// --- Kruste und Kern je Art -------------------------------------------------------------------

	private static BlockState crust(Kind kind, Random random) {
		int roll = random.nextInt(100);
		return switch (kind) {
			case ROCK -> pick(random, Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.COBBLESTONE, Blocks.BASALT);
			case ICE -> roll < 15 ? Blocks.SNOW_BLOCK.getDefaultState()
					: roll < 30 ? Blocks.BLUE_ICE.getDefaultState() : Blocks.PACKED_ICE.getDefaultState();
			case CRYSTAL -> roll < 55 ? Blocks.SMOOTH_BASALT.getDefaultState()
					: roll < 85 ? Blocks.CALCITE.getDefaultState() : Blocks.AMETHYST_BLOCK.getDefaultState();
			case MOLTEN -> roll < 18 ? Blocks.MAGMA_BLOCK.getDefaultState()
					: roll < 55 ? Blocks.BLACKSTONE.getDefaultState() : Blocks.BASALT.getDefaultState();
			case METAL -> roll < 20 ? Blocks.RAW_IRON_BLOCK.getDefaultState()
					: roll < 32 ? Blocks.OXIDIZED_COPPER.getDefaultState()
					: roll < 40 ? Blocks.WEATHERED_COPPER.getDefaultState() : Blocks.TUFF.getDefaultState();
		};
	}

	/** Kern; {@code depth} = Abstand zur Mitte relativ zum Radius (0 = Mitte). */
	private static BlockState core(Kind kind, Random random, double depth) {
		int roll = random.nextInt(100);
		return switch (kind) {
			case ROCK -> roll < 5 ? ModBlocks.RARITANIUM_ORE.getDefaultState()
					: roll < 9 ? Blocks.IRON_ORE.getDefaultState()
					: roll < 13 ? Blocks.COPPER_ORE.getDefaultState()
					: roll < 15 ? Blocks.GOLD_ORE.getDefaultState()
					: roll < 16 ? Blocks.DIAMOND_ORE.getDefaultState()
					: random.nextInt(4) == 0 ? Blocks.SMOOTH_BASALT.getDefaultState() : Blocks.STONE.getDefaultState();
			case ICE -> roll < 3 ? Blocks.DIAMOND_ORE.getDefaultState()
					: roll < 8 ? Blocks.PRISMARINE.getDefaultState()
					: roll < 40 ? Blocks.BLUE_ICE.getDefaultState() : Blocks.PACKED_ICE.getDefaultState();
			case CRYSTAL -> depth < 0.45 ? (roll < 30 ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK).getDefaultState()
					: roll < 6 ? ModBlocks.RARITANIUM_ORE.getDefaultState() : Blocks.CALCITE.getDefaultState();
			case MOLTEN -> depth < 0.4 ? (roll < 60 ? Blocks.MAGMA_BLOCK : Blocks.SHROOMLIGHT).getDefaultState()
					: roll < 8 ? Blocks.GILDED_BLACKSTONE.getDefaultState()
					: roll < 14 ? Blocks.NETHER_GOLD_ORE.getDefaultState()
					: roll < 30 ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.BLACKSTONE.getDefaultState();
			case METAL -> roll < 12 ? ModBlocks.RARITANIUM_ORE.getDefaultState()
					: roll < 30 ? Blocks.IRON_ORE.getDefaultState()
					: roll < 45 ? Blocks.COPPER_ORE.getDefaultState()
					: roll < 52 ? Blocks.RAW_COPPER_BLOCK.getDefaultState() : Blocks.DEEPSLATE.getDefaultState();
		};
	}

	private static BlockState pick(Random random, Block... blocks) {
		return blocks[random.nextInt(blocks.length)].getDefaultState();
	}

	// --- Oberflaeche ------------------------------------------------------------------------------

	/** 1–3 Einschlagkrater: Kugeln an der Oberflaeche ausgehoehlt, Boden mit dunklem Auswurf. */
	private static void carveCraters(StructureWorldAccess world, BlockPos origin, int radius, Random random) {
		int craters = 1 + random.nextInt(3);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int c = 0; c < craters; c++) {
			double theta = random.nextDouble() * Math.PI * 2;
			double phi = Math.acos(random.nextDouble() * 2 - 1);
			double cx = origin.getX() + Math.sin(phi) * Math.cos(theta) * radius;
			double cy = origin.getY() + Math.cos(phi) * radius * 0.85;
			double cz = origin.getZ() + Math.sin(phi) * Math.sin(theta) * radius;
			double size = 1.5 + random.nextDouble() * Math.min(2.5, radius / 3.0);
			int r = (int) Math.ceil(size) + 1;
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
						pos.set(cx + dx, cy + dy, cz + dz);
						if (world.getBlockState(pos).isAir()) {
							continue;
						}
						if (d <= size) {
							world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
						} else if (d <= size + 1.0 && random.nextInt(3) == 0) {
							world.setBlockState(pos, Blocks.COBBLED_DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
						}
					}
				}
			}
		}
	}

	/** Kristall-Asteroiden: Amethyst-Drusen wachsen aus der Oberflaeche nach aussen (leuchten schwach). */
	private static void growClusters(StructureWorldAccess world, BlockPos origin, List<BlockPos> surface, Random random) {
		for (BlockPos base : surface) {
			if (random.nextInt(5) != 0 || world.getBlockState(base).isAir()) {
				continue;
			}
			Direction outward = Direction.getFacing(base.getX() - origin.getX(), base.getY() - origin.getY(), base.getZ() - origin.getZ());
			BlockPos tip = base.offset(outward);
			if (world.getBlockState(tip).isAir()) {
				Block cluster = random.nextInt(3) == 0 ? Blocks.LARGE_AMETHYST_BUD : Blocks.AMETHYST_CLUSTER;
				world.setBlockState(tip, cluster.getDefaultState().with(AmethystClusterBlock.FACING, outward), Block.NOTIFY_LISTENERS);
			}
		}
	}

	/** Gleichmaessiges Rauschen in [-0.5, 0.5] aus Koordinaten und Salz, damit die Oberflaeche zerkluftet wirkt. */
	private static double noise(int x, int y, int z, long salt) {
		long h = salt ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return ((h & 0xFFFF) / 65535.0) - 0.5;
	}
}
