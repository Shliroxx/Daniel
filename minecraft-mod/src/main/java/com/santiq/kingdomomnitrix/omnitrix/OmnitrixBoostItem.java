package com.santiq.kingdomomnitrix.omnitrix;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Boost-Modul: Rechtsklick setzt es in einen freien Steckplatz des Omnitrix (siehe {@link OmnitrixBoosts}). */
public class OmnitrixBoostItem extends Item {
	private final OmnitrixBoost boost;

	public OmnitrixBoostItem(OmnitrixBoost boost, Settings settings) {
		super(settings);
		this.boost = boost;
	}

	public OmnitrixBoost boost() {
		return boost;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity player) {
			OmnitrixBoosts.Result result = OmnitrixBoosts.install(player, boost, stack);
			return result == OmnitrixBoosts.Result.INSTALLED ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable(boost.descKey()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.boost.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
