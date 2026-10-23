package com.santiq.kingdomomnitrix.suit.module;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Anzug-Konfigurator: Holo-Tisch, Rechtsklick oeffnet das Konfigurationsmenue. */
public class ConfiguratorBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
	private static final VoxelShape SHAPE = VoxelShapes.union(createCuboidShape(1, 0, 1, 15, 4, 15), createCuboidShape(4, 4, 4, 12, 10, 12),
			createCuboidShape(2, 10, 2, 14, 13, 14));

	public ConfiguratorBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(ConfiguratorBlock::new);
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ConfiguratorBlockEntity(pos, state);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && world.getBlockEntity(pos) instanceof ConfiguratorBlockEntity configurator) {
			player.openHandledScreen(configurator);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof ConfiguratorBlockEntity configurator) {
			ItemScatterer.spawn(world, pos, configurator);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
