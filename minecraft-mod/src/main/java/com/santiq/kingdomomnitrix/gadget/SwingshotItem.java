package com.santiq.kingdomomnitrix.gadget;

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

/**
 * Swingshot: Greifhaken, der sich an jedem festen Block verankert und den Spieler hinzieht.
 * Wirkt aus dem Werkzeug-Platz des Gadget-Guertels per Gadget-Taste.
 */
public class SwingshotItem extends Item implements Gadget {
	/** Reichweite in Bloecken. */
	public static final double RANGE = 24.0;
	/** Laengste Zugdauer in Ticks, danach loest der Haken. */
	public static final int MAX_PULL_TICKS = 60;
	/** Laengste Zeit am Haken haengend in Ticks (danach loest er). */
	public static final int MAX_HANG_TICKS = 200;
	/** Abklingzeit nach dem Loesen in Ticks. */
	public static final int COOLDOWN_TICKS = 10;

	public SwingshotItem(Settings settings) {
		super(settings);
	}

	@Override
	public GadgetSlot gadgetSlot() {
		return GadgetSlot.TOOL;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity player) {
			GadgetManager.equipFromHand(player, hand);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.swingshot.usage").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.gadget.equip").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
