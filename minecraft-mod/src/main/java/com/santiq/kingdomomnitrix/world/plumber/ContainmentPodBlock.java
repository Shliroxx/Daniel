package com.santiq.kingdomomnitrix.world.plumber;

import com.mojang.serialization.MapCodec;
import com.santiq.kingdomomnitrix.registry.ModBlockEntities;
import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Containment-Kapsel im Klempner-Wrack (Galvan-Pod): verbeulte Metallkugel mit dem kaputten Omnitrix (Huelle) bzw. der
 * Konsole. Rechtsklick startet die Oeffnung ({@link ContainmentPodBlockEntity}) — beim ersten Mal fliegt das Teil dem
 * Spieler an den Arm — jeder Spieler bekommt sein Teil genau einmal (Mehrspieler: jeder baut sein eigenes
 * Omnitrix). Unzerstoerbar, damit die Story im Wrack bleibt.
 */
public class ContainmentPodBlock extends BlockWithEntity {
	public static final MapCodec<ContainmentPodBlock> CODEC = createCodec(settings -> new ContainmentPodBlock(settings, Part.SHELL));

	/** Welches Teil die Kapsel haelt. */
	public enum Part {
		SHELL, CONSOLE;

		public Item item() {
			return this == SHELL ? Ben10Items.OMNITRIX_SHELL : Ben10Items.OMNITRIX_CONSOLE;
		}
	}

	private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
	private final Part part;

	public ContainmentPodBlock(Settings settings, Part part) {
		super(settings);
		this.part = part;
	}

	public Part part() {
		return part;
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	/** Gezeichnet wird die Kapsel vom Block-Entity-Renderer (3D, Kuppel sprengt in vier Segmenten auf). */
	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.ENTITYBLOCK_ANIMATED;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ContainmentPodBlockEntity(pos, state);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient() || !(world.getBlockEntity(pos) instanceof ContainmentPodBlockEntity pod)) {
			return ActionResult.success(world.isClient());
		}
		if (pod.claimedBy(player)) {
			// eigenes Teil schon geholt (oder gerade unterwegs): kein neuer Ablauf
			if (!(pod.busy() && pod.openedBy(player))) {
				player.sendMessage(Text.translatable("message.kingdomomnitrix.pod.empty").formatted(Formatting.GRAY), true);
			}
			return ActionResult.CONSUME;
		}
		if (pod.busy()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.pod.busy").formatted(Formatting.GRAY), true);
			return ActionResult.CONSUME;
		}
		pod.open(player);
		return ActionResult.CONSUME;
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient() ? null : validateTicker(type, ModBlockEntities.CONTAINMENT_POD, ContainmentPodBlockEntity::tick);
	}
}
