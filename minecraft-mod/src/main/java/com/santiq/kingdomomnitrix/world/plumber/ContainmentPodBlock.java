package com.santiq.kingdomomnitrix.world.plumber;

import com.mojang.serialization.MapCodec;
import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
 * Containment-Kapsel im Klempner-Wrack: gepanzerter Kasten mit dem kaputten Omnitrix (Huelle) bzw. der Konsole.
 * Rechtsklick klappt den Deckel auf — jeder Spieler bekommt sein Teil genau einmal (Mehrspieler: jeder baut sein eigenes
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

	private static final VoxelShape SHAPE = Block.createCuboidShape(1.0, 0.0, 1.0, 15.0, 12.0, 15.0);
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

	/** Gezeichnet wird die Kapsel vom Block-Entity-Renderer (3D, Deckel klappt auf). */
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
		pod.open();
		if (!pod.claim(player.getUuid())) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.pod.empty").formatted(Formatting.GRAY), true);
			world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.6f, 1.4f);
			return ActionResult.CONSUME;
		}
		player.getInventory().offerOrDrop(new ItemStack(part.item()));
		player.sendMessage(Text.translatable(part == Part.SHELL ? "message.kingdomomnitrix.pod.shell" : "message.kingdomomnitrix.pod.console")
				.formatted(Formatting.GREEN), false);
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.4f);
		world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 0.8f, 0.8f);
		if (world instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 25, 0.35, 0.35, 0.35, 0.05);
			server.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 15, 0.2, 0.3, 0.2, 0.03);
		}
		return ActionResult.CONSUME;
	}
}
