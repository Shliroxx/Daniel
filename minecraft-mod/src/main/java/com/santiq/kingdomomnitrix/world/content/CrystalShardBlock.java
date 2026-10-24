package com.santiq.kingdomomnitrix.world.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Splitterbuendel der Kristallwelten in Diamondhead-Optik: schraege, durchscheinende Kristallsplitter auf festem Boden.
 * Anders als {@link CrystalSpikeBlock} sticht es nicht; ohne Boden darunter bricht es ab.
 */
public class CrystalShardBlock extends Block {
	public static final MapCodec<CrystalShardBlock> CODEC = createCodec(CrystalShardBlock::new);
	private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 14, 13);

	public CrystalShardBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
		return true;
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
			BlockPos pos, BlockPos neighborPos) {
		return direction == Direction.DOWN && !canPlaceAt(state, world, pos) ? Blocks.AIR.getDefaultState()
				: super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}
}
