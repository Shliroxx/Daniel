package com.daniel.heroverse.item;

import com.daniel.heroverse.registry.ModItems;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Schluesselschwert "Koenigsschluessel".
 * Rechtsklick wirkt den gewaehlten Zauber, Schleichen + Rechtsklick wechselt ihn.
 */
public class KeybladeItem extends SwordItem {
	private static final String SPELL_KEY = "spell";

	public KeybladeItem(ToolMaterial toolMaterial, Settings settings) {
		super(toolMaterial, settings);
	}

	public static Spell getSpell(ItemStack stack) {
		return Spell.byIndex(ItemData.getInt(stack, SPELL_KEY));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(world instanceof ServerWorld serverWorld)) {
			return TypedActionResult.success(stack, true);
		}

		if (user.isSneaking()) {
			Spell next = getSpell(stack).next();
			ItemData.putInt(stack, SPELL_KEY, next.ordinal());
			user.sendMessage(Text.translatable("message.heroverse.spell_selected", next.displayName()), true);
			return TypedActionResult.success(stack, false);
		}

		Spell spell = getSpell(stack);
		if (!spell.cast(serverWorld, user)) {
			return TypedActionResult.fail(stack);
		}
		user.getItemCooldownManager().set(this, spell.cooldownTicks());
		return TypedActionResult.success(stack, false);
	}

	@Override
	public boolean canRepair(ItemStack stack, ItemStack ingredient) {
		return ingredient.isOf(ModItems.HEART) || super.canRepair(stack, ingredient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.heroverse.spell", getSpell(stack).displayName()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.heroverse.kingdom_key.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
