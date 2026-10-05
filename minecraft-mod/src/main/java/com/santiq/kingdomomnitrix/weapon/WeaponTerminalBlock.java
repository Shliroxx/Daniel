package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.networking.OpenTerminalPayload;
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

/** Waffen-Terminal (Gadgetron-Stil): Rechtsklick oeffnet den Laden fuer Waffen, Stufen und Munition. */
public class WeaponTerminalBlock extends Block {
	public WeaponTerminalBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			ServerPlayNetworking.send(serverPlayer, new OpenTerminalPayload(pos));
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.4f, 1.8f);
		}
		return ActionResult.success(world.isClient());
	}
}
