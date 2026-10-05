package com.santiq.kingdomomnitrix.world.content;

import com.mojang.serialization.MapCodec;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Pflanze der Planeten: waechst nur auf Planetenboden ({@code #kingdomomnitrix:alien_soil}), ohne Kollision. */
public class AlienPlantBlock extends PlantBlock {
	public static final MapCodec<AlienPlantBlock> CODEC = createCodec(AlienPlantBlock::new);
	public static final TagKey<net.minecraft.block.Block> SOIL = TagKey.of(RegistryKeys.BLOCK, KingdomOmnitrix.id("alien_soil"));
	private static final VoxelShape SHAPE = net.minecraft.block.Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

	public AlienPlantBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends PlantBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
		return floor.isIn(SOIL);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		Vec3d offset = state.getModelOffset(world, pos);
		return SHAPE.offset(offset.x, offset.y, offset.z);
	}
}
