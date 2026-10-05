package com.santiq.kingdomomnitrix.omnitrix;

import com.santiq.kingdomomnitrix.networking.OpenCalibrationPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Kalibrier-Werkbank: Rechtsklick oeffnet die Kalibrierung des eigenen Omnitrix (Module, Farbe). */
public class CalibrationBenchBlock extends Block {
	public CalibrationBenchBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			ServerPlayNetworking.send(serverPlayer, new OpenCalibrationPayload(pos));
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.4f, 1.4f);
		}
		return ActionResult.success(world.isClient());
	}
}
