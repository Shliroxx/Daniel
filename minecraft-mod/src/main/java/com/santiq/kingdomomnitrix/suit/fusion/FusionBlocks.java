package com.santiq.kingdomomnitrix.suit.fusion;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Bloecke des Fusionsaltars: Kern (Mitte) und Injektor (Saeule mit schwebender Platte). */
public final class FusionBlocks {
	private FusionBlocks() {
	}

	/** Fusionskern. Rechtsklick legt ein/nimmt heraus, Shift-Rechtsklick startet die Fusion. */
	public static class Core extends BlockWithEntity {
		private static final VoxelShape SHAPE = VoxelShapes.union(createCuboidShape(0, 0, 0, 16, 6, 16), createCuboidShape(3, 6, 3, 13, 12, 13));

		public Core(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends BlockWithEntity> getCodec() {
			return createCodec(Core::new);
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
			return new FusionCoreBlockEntity(pos, state);
		}

		@Nullable
		@Override
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
			return world.isClient() ? validateTicker(type, SuitFusion.CORE_ENTITY, (w, p, s, core) -> core.clientTick())
					: validateTicker(type, SuitFusion.CORE_ENTITY, FusionCoreBlockEntity::tick);
		}

		@Override
		protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
				BlockHitResult hit) {
			if (player.isSneaking() || !(world.getBlockEntity(pos) instanceof FusionCoreBlockEntity core)) {
				return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			}
			if (world.isClient()) {
				return ItemActionResult.SUCCESS;
			}
			return core.insert(stack) ? ItemActionResult.SUCCESS : ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		@Override
		protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
			if (world.isClient() || !(world.getBlockEntity(pos) instanceof FusionCoreBlockEntity core)) {
				return ActionResult.SUCCESS;
			}
			if (player.isSneaking()) {
				core.start(player);
			} else {
				ItemStack out = core.take();
				if (!out.isEmpty() && !player.giveItemStack(out)) {
					player.dropItem(out, false);
				}
			}
			return ActionResult.SUCCESS;
		}

		@Override
		protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
			if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof FusionCoreBlockEntity core) {
				ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), core.center());
				ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), core.cores());
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	/** Injektor: Rechtsklick legt einen Gegenstand auf, leere Hand nimmt ihn wieder. */
	public static class Injector extends BlockWithEntity {
		private static final VoxelShape SHAPE = VoxelShapes.union(createCuboidShape(2, 0, 2, 14, 3, 14), createCuboidShape(5, 3, 5, 11, 12, 11),
				createCuboidShape(3, 12, 3, 13, 14, 13));

		public Injector(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends BlockWithEntity> getCodec() {
			return createCodec(Injector::new);
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
			return new FusionInjectorBlockEntity(pos, state);
		}

		@Override
		protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
				BlockHitResult hit) {
			if (!(world.getBlockEntity(pos) instanceof FusionInjectorBlockEntity injector) || !injector.stack().isEmpty()) {
				return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			}
			if (!world.isClient()) {
				injector.setStack(stack.split(1));
			}
			return ItemActionResult.SUCCESS;
		}

		@Override
		protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
			if (!world.isClient() && world.getBlockEntity(pos) instanceof FusionInjectorBlockEntity injector && !injector.stack().isEmpty()) {
				ItemStack out = injector.stack();
				injector.setStack(ItemStack.EMPTY);
				if (!player.giveItemStack(out)) {
					player.dropItem(out, false);
				}
			}
			return ActionResult.SUCCESS;
		}

		@Override
		protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
			if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof FusionInjectorBlockEntity injector) {
				ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), injector.stack());
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}
}
