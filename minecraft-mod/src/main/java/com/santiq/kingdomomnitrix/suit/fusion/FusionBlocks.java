package com.santiq.kingdomomnitrix.suit.fusion;

import com.mojang.serialization.MapCodec;
import com.santiq.kingdomomnitrix.suit.forge.ForgeMaterials;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
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

	/** Fusionskern. Rechtsklick legt ein/nimmt heraus (waehrend der Fusion: Kryo-Zelle kuehlt), Shift-Rechtsklick startet. */
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
			return core.insert(stack, player) ? ItemActionResult.SUCCESS : ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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

	/**
	 * Injektor in drei Stufen (Grund, Galvan, Sternen): Rechtsklick legt einen Gegenstand auf, leere Hand nimmt ihn
	 * wieder. Ein Rezept braucht Injektoren mindestens seiner Stufe. Instabilitaet kann einen Injektor beschaedigen;
	 * dann leuchtet er rot und muss mit einem Raritanium-Barren repariert werden.
	 */
	public static class Injector extends BlockWithEntity {
		public static final BooleanProperty DAMAGED = BooleanProperty.of("damaged");
		private static final VoxelShape SHAPE = VoxelShapes.union(createCuboidShape(2, 0, 2, 14, 3, 14), createCuboidShape(5, 3, 5, 11, 12, 11),
				createCuboidShape(3, 12, 3, 13, 14, 13));
		private final int tier;

		public Injector(Settings settings, int tier) {
			super(settings);
			this.tier = tier;
			setDefaultState(getStateManager().getDefaultState().with(DAMAGED, false));
		}

		public int tier() {
			return tier;
		}

		@Override
		protected MapCodec<? extends BlockWithEntity> getCodec() {
			return createCodec(s -> new Injector(s, tier));
		}

		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
			builder.add(DAMAGED);
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
			if (state.get(DAMAGED) && stack.isOf(ForgeMaterials.RARITANIUM_INGOT)) {
				if (!world.isClient()) {
					world.setBlockState(pos, state.with(DAMAGED, false));
					stack.decrementUnlessCreative(1, player);
					world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 1.4f);
					player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.repaired").formatted(Formatting.GREEN), true);
				}
				return ItemActionResult.SUCCESS;
			}
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

	/** Bauteil des Altars mit eigener Form (Rune, Saeule, Kronen, Fokus, Kuehler). */
	public static class Part extends Block {
		private final VoxelShape shape;

		public Part(Settings settings, VoxelShape shape) {
			super(settings);
			this.shape = shape;
		}

		@Override
		protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return shape;
		}
	}

	public static final VoxelShape PYLON_SHAPE = Block.createCuboidShape(3, 0, 3, 13, 16, 13);
	public static final VoxelShape CAPSTONE_SHAPE = VoxelShapes.union(Block.createCuboidShape(2, 0, 2, 14, 4, 14),
			Block.createCuboidShape(5, 4, 5, 11, 15, 11));
	public static final VoxelShape FOCUS_SHAPE = Block.createCuboidShape(3, 3, 3, 13, 13, 13);
}
