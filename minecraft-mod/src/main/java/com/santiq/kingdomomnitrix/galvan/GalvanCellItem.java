package com.santiq.kingdomomnitrix.galvan;

import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Galvan-Energiezelle (Erfindung): laedt das Omnitrix sofort voll — die laufende Nachladezeit endet. */
public class GalvanCellItem extends Item {
	public GalvanCellItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.success(stack, world.isClient);
		}
		if (!OmnitrixItem.hasOmnitrix(player) || !TransformationManager.finishRecharge(player)) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.galvan_cell_full").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 1.8f);
		stack.decrementUnlessCreative(1, player);
		player.getItemCooldownManager().set(this, 40);
		return TypedActionResult.consume(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.kingdomomnitrix.galvan_cell.tooltip").formatted(Formatting.GRAY));
	}
}
