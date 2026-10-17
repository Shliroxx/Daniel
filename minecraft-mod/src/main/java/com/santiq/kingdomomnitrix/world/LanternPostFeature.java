package com.santiq.kingdomomnitrix.world;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Strassenlaterne wie in Traverse Town: dunkler Pfahl (2–3 Bloecke) mit haengender Laterne.
 * Steht nur auf Gras/Erde mit freiem Platz darueber. Haelt nachts kleine Lichtinseln in der Landschaft.
 */
public class LanternPostFeature extends Feature<DefaultFeatureConfig> {
	public LanternPostFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos base = context.getOrigin();
		Random random = context.getRandom();
		if (!world.getBlockState(base.down()).isIn(BlockTags.DIRT)) {
			return false;
		}
		int height = 2 + random.nextInt(2);
		for (int i = 0; i <= height + 1; i++) {
			if (!world.getBlockState(base.up(i)).isAir()) {
				return false;
			}
		}
		BlockState post = Blocks.DARK_OAK_FENCE.getDefaultState();
		for (int i = 0; i < height; i++) {
			world.setBlockState(base.up(i), post, Block.NOTIFY_LISTENERS);
		}
		// Kopfstueck mit Laterne darauf
		world.setBlockState(base.up(height), Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);
		world.setBlockState(base.up(height + 1), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), Block.NOTIFY_LISTENERS);
		return true;
	}
}
