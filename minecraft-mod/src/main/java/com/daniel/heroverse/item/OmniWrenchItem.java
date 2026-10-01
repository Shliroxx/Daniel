package com.daniel.heroverse.item;

import com.daniel.heroverse.entity.HeroProjectileEntity;
import com.daniel.heroverse.registry.ModItems;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** OmniWrench 8000: Nahkampf-Schraubenschluessel, Rechtsklick wirft eine Kopie als Geschoss. */
public class OmniWrenchItem extends SwordItem {
	private static final int THROW_DELAY_TICKS = 30;

	public OmniWrenchItem(ToolMaterial toolMaterial, Settings settings) {
		super(toolMaterial, settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient()) {
			HeroProjectileEntity.shoot(world, user, ModItems.OMNIWRENCH, 1.6f, 0.0f);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.8f, 0.5f);
			stack.damage(1, user, LivingEntity.getSlotForHand(hand));
			user.getItemCooldownManager().set(this, THROW_DELAY_TICKS);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public boolean canRepair(ItemStack stack, ItemStack ingredient) {
		return ingredient.isOf(ModItems.BOLT) || super.canRepair(stack, ingredient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.heroverse.omniwrench.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
