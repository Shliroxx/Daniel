package com.santiq.kingdomomnitrix.gadget;

import com.santiq.kingdomomnitrix.registry.ModComponents;
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
 * Clanks Rucken-Gadget mit zwei Modi, umschaltbar per Taste:
 * Heli-Pack (Doppelsprung, Gleiten bei gehaltener Sprungtaste) und Heli-Jet (Schub-Sprints in der Luft, schnellerer Sinkflug).
 * Wirkt nur im Ruecken-Platz des Gadget-Guertels. Die Bewegung rechnet der Client (Spielerbewegung ist clientseitig),
 * der Server prueft Ausruestung und Modus, setzt die Fallhoehe zurueck und spielt Effekte ab.
 */
public class HeliPackItem extends Item implements Gadget {
	public HeliPackItem(Settings settings) {
		super(settings);
	}

	public static boolean isJet(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(ModComponents.JET_MODE));
	}

	@Override
	public GadgetSlot gadgetSlot() {
		return GadgetSlot.BACK;
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.translatable(isJet(stack) ? "item.kingdomomnitrix.heli_jet" : getTranslationKey());
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
		tooltip.add(Text.translatable(isJet(stack) ? "tooltip.kingdomomnitrix.heli_jet.usage" : "tooltip.kingdomomnitrix.heli_pack.usage")
				.formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.heli_pack.mode").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.gadget.equip").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
