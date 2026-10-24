package com.santiq.kingdomomnitrix.gadget;

import com.santiq.kingdomomnitrix.gear.GeoGear;
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
 * Clanks Rucksack mit vier Modi ({@link PackMode}), umschaltbar per Taste: Heli (Doppelsprung, Gleiten), Thruster
 * (Schub nach vorn, Sinkflug), Gleiter (weite Gleitfluege, Aufwind ueber Hitze) und Jetpack (Steigflug mit Treibstoff).
 * Wirkt nur im Ruecken-Platz des Gadget-Guertels. Die Bewegung rechnet der Client (Spielerbewegung ist clientseitig),
 * der Server prueft Ausruestung und Modus, setzt die Fallhoehe zurueck und spielt Effekte ab.
 */
public class HeliPackItem extends Item implements Gadget, GeoGear {
	public HeliPackItem(Settings settings) {
		super(settings);
	}

	public static PackMode mode(ItemStack stack) {
		PackMode mode = stack.get(ModComponents.PACK_MODE);
		if (mode != null) {
			return mode;
		}
		return Boolean.TRUE.equals(stack.get(ModComponents.JET_MODE)) ? PackMode.THRUSTER : PackMode.HELI;
	}

	/** Stack im angegebenen Modus; der alte Jet-Schalter faellt dabei weg. */
	public static ItemStack withMode(ItemStack stack, PackMode mode) {
		ItemStack changed = stack.copy();
		changed.remove(ModComponents.JET_MODE);
		changed.set(ModComponents.PACK_MODE, mode);
		return changed;
	}

	@Override
	public GadgetSlot gadgetSlot() {
		return GadgetSlot.BACK;
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.translatable(mode(stack).nameKey());
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
		tooltip.add(Text.translatable(mode(stack).usageKey()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.heli_pack.mode").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.gadget.equip").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
