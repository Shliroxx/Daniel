package com.santiq.kingdomomnitrix.world.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Kristall-/Eisstachel der Alien-Faehigkeiten: gestufte Spitze, sticht wer hineinlaeuft (ausser dem Erschaffer). */
public class CrystalSpikeBlock extends Block {
	public static final MapCodec<CrystalSpikeBlock> CODEC = createCodec(CrystalSpikeBlock::new);
	private static final VoxelShape SHAPE = VoxelShapes.union(Block.createCuboidShape(2, 0, 2, 14, 5, 14),
			Block.createCuboidShape(4, 5, 4, 12, 9, 12), Block.createCuboidShape(5.5, 9, 5.5, 10.5, 13, 10.5),
			Block.createCuboidShape(7, 13, 7, 9, 16, 9));

	public CrystalSpikeBlock(Settings settings) {
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
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (!world.isClient && entity instanceof LivingEntity living && !TempBlocks.isOwner(world, pos, living)
				&& world.getTime() % 10 == 0) {
			living.damage(world.getDamageSources().cactus(), 3.0f);
		}
	}
}
