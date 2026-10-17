package com.santiq.kingdomomnitrix.world;

import com.mojang.serialization.Codec;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Kristallnadel der Kristallfelder: Sockel aus Calcit, nach oben spitz zulaufender Amethyst-Turm
 * (4–11 Bloecke, leicht geneigt), Amethyst-Knospen an den Seiten. Jede Nadel ist anders.
 */
public class CrystalSpireFeature extends Feature<DefaultFeatureConfig> {
	public CrystalSpireFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();
		if (!world.getBlockState(origin.down()).isIn(BlockTags.DIRT) && !world.getBlockState(origin.down()).isOf(Blocks.CALCITE)) {
			return false;
		}
		int height = 4 + random.nextInt(8);
		double leanX = (random.nextDouble() - 0.5) * 0.35;
		double leanZ = (random.nextDouble() - 0.5) * 0.35;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = -1; y < height; y++) {
			double radius = Math.max(0.5, 2.2 * (1.0 - (double) y / height));
			int cx = origin.getX() + (int) Math.round(leanX * y);
			int cz = origin.getZ() + (int) Math.round(leanZ * y);
			int r = (int) Math.ceil(radius);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz > radius * radius) {
						continue;
					}
					pos.set(cx + dx, origin.getY() + y, cz + dz);
					BlockState state = y <= 0 ? Blocks.CALCITE.getDefaultState()
							: random.nextInt(6) == 0 ? Blocks.BUDDING_AMETHYST.getDefaultState() : Blocks.AMETHYST_BLOCK.getDefaultState();
					world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
				}
			}
			// Knospen an einer zufaelligen Seite
			if (y > 1 && random.nextInt(3) == 0) {
				Direction side = Direction.Type.HORIZONTAL.random(random);
				BlockPos bud = new BlockPos(cx, origin.getY() + y, cz).offset(side, r + 1);
				if (world.getBlockState(bud).isAir()) {
					world.setBlockState(bud, Blocks.AMETHYST_CLUSTER.getDefaultState().with(AmethystClusterBlock.FACING, side), Block.NOTIFY_LISTENERS);
				}
			}
		}
		return true;
	}
}
