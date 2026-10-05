package com.santiq.kingdomomnitrix.arena;

import com.mojang.serialization.MapCodec;
import com.santiq.kingdomomnitrix.networking.OpenArenaPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Arena-Terminal: Rechtsklick oeffnet die Auswahl der Herausforderungen. */
public class ArenaTerminalBlock extends BlockWithEntity {
	public static final MapCodec<ArenaTerminalBlock> CODEC = createCodec(ArenaTerminalBlock::new);

	public ArenaTerminalBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	@Nullable
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ArenaTerminalBlockEntity(pos, state);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			ServerPlayNetworking.send(serverPlayer, new OpenArenaPayload(pos, ArenaManager.isRunning(serverPlayer.getServerWorld(), pos)));
		}
		return ActionResult.success(world.isClient());
	}
}
