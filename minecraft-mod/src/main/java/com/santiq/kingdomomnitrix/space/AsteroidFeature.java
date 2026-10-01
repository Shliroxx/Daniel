package com.santiq.kingdomomnitrix.space;

import com.mojang.serialization.Codec;
import com.santiq.kingdomomnitrix.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Asteroid im All: unregelmaessige Gesteinskugel (Radius 3–8) mit Kruste aus verschiedenen Steinen
 * und Erzadern im Inneren. Jeder Asteroid sieht anders aus (Zufall aus dem Weltseed).
 */
public class AsteroidFeature extends Feature<DefaultFeatureConfig> {
	private static final BlockState[] CRUST = {
			Blocks.STONE.getDefaultState(), Blocks.ANDESITE.getDefaultState(), Blocks.TUFF.getDefaultState(),
			Blocks.COBBLESTONE.getDefaultState(), Blocks.BASALT.getDefaultState()};

	public AsteroidFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();
		int radius = 3 + random.nextInt(6);
		double squash = 0.7 + random.nextDouble() * 0.6;
		long salt = random.nextLong();
		BlockPos.Mutable pos = new BlockPos.Mutable();
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
					BlockState state = distance > radius - 1.3 ? CRUST[random.nextInt(CRUST.length)] : core(random);
					world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
					placed = true;
				}
			}
		}
		return placed;
	}

	private static BlockState core(Random random) {
		int roll = random.nextInt(100);
		if (roll < 5) {
			return ModBlocks.RARITANIUM_ORE.getDefaultState();
		}
		if (roll < 9) {
			return Blocks.IRON_ORE.getDefaultState();
		}
		if (roll < 13) {
			return Blocks.COPPER_ORE.getDefaultState();
		}
		if (roll < 15) {
			return Blocks.GOLD_ORE.getDefaultState();
		}
		if (roll < 16) {
			return Blocks.DIAMOND_ORE.getDefaultState();
		}
		return random.nextInt(4) == 0 ? Blocks.SMOOTH_BASALT.getDefaultState() : Blocks.STONE.getDefaultState();
	}

	/** Gleichmaessiges Rauschen in [-0.5, 0.5] aus Koordinaten und Salz, damit die Oberflaeche zerkluftet wirkt. */
	private static double noise(int x, int y, int z, long salt) {
		long h = salt ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return ((h & 0xFFFF) / 65535.0) - 0.5;
	}
}
