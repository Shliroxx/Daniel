package com.santiq.kingdomomnitrix.omnitrix.workbench;

import com.mojang.serialization.MapCodec;
import com.santiq.kingdomomnitrix.registry.ModBlockEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Omnitrix-Werkbank: Rechtsklick oeffnet das Menue; Zusammenbau und Umfaerben laufen in der Block-Entity. */
public class OmnitrixWorkbenchBlock extends BlockWithEntity {
	public static final MapCodec<OmnitrixWorkbenchBlock> CODEC = createCodec(OmnitrixWorkbenchBlock::new);

	public OmnitrixWorkbenchBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new OmnitrixWorkbenchBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, ModBlockEntities.OMNITRIX_WORKBENCH, OmnitrixWorkbenchBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && world.getBlockEntity(pos) instanceof OmnitrixWorkbenchBlockEntity bench) {
			player.openHandledScreen(bench);
		}
		return ActionResult.success(world.isClient());
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof OmnitrixWorkbenchBlockEntity bench) {
			ItemScatterer.spawn(world, pos, bench.items());
			world.updateComparators(pos, this);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
