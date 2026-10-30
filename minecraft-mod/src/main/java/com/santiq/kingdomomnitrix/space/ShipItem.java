package com.santiq.kingdomomnitrix.space;

import com.santiq.kingdomomnitrix.registry.ModEntities;
import java.util.List;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/** Bausatz der Aphelion: auf einen Block klicken stellt das Schiff auf (braucht Platz: 4 × 2 × 4 Bloecke). */
public class ShipItem extends Item {
	public ShipItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!(context.getWorld() instanceof ServerWorld world)) {
			return ActionResult.SUCCESS;
		}
		BlockPos pos = context.getBlockPos().offset(context.getSide());
		ShipEntity ship = ModEntities.SHIP.create(world, null, pos, SpawnReason.SPAWN_EGG, false, false);
		if (ship == null) {
			return ActionResult.FAIL;
		}
		float yaw = context.getPlayerYaw();
		ship.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
		if (!world.isSpaceEmpty(ship, ship.getBoundingBox())) {
			if (context.getPlayer() != null) {
				context.getPlayer().sendMessage(Text.translatable("message.kingdomomnitrix.ship_no_space").formatted(Formatting.RED), true);
			}
			return ActionResult.FAIL;
		}
		world.spawnEntity(ship);
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.NEUTRAL, 0.8f, 1.4f);
		if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
			context.getStack().decrement(1);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.aphelion.usage").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.aphelion.controls").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
