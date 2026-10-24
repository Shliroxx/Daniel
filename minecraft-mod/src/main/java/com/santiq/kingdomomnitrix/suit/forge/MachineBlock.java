package com.santiq.kingdomomnitrix.suit.forge;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Block einer Ruestungs-Maschine: dreht sich zum Spieler, leuchtet beim Arbeiten, oeffnet das Menue. */
public class MachineBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
	public static final BooleanProperty LIT = Properties.LIT;

	private final MachineKind kind;

	public MachineBlock(MachineKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LIT, false));
	}

	public MachineKind kind() {
		return kind;
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(settings -> new MachineBlock(kind, settings));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MachineBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, SuitForge.MACHINE_ENTITY, MachineBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && world.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			player.openHandledScreen(machine);
		}
		return ActionResult.success(world.isClient());
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			ItemScatterer.spawn(world, pos, machine);
			world.updateComparators(pos, this);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!state.get(LIT)) {
			return;
		}
		Direction facing = state.get(FACING);
		double x = pos.getX() + 0.5 + facing.getOffsetX() * 0.52 + (random.nextDouble() - 0.5) * 0.5;
		double y = pos.getY() + 0.3 + random.nextDouble() * 0.5;
		double z = pos.getZ() + 0.5 + facing.getOffsetZ() * 0.52 + (random.nextDouble() - 0.5) * 0.5;
		switch (kind) {
			case CRUSHER -> world.addParticle(ParticleTypes.CRIT, x, y, z, 0, 0.02, 0);
			case SMELTER -> world.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.01, 0);
			case FORGE -> world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0.03, 0);
		}
	}
}
