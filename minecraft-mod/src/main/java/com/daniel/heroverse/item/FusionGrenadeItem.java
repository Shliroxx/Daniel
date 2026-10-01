package com.daniel.heroverse.item;

import com.daniel.heroverse.entity.FusionGrenadeEntity;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class FusionGrenadeItem extends Item {
	private static final int THROW_DELAY_TICKS = 15;

	public FusionGrenadeItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient()) {
			FusionGrenadeEntity grenade = new FusionGrenadeEntity(world, user);
			grenade.setItem(stack.copyWithCount(1));
			grenade.setVelocity(user, user.getPitch(), user.getYaw(), -10.0f, 1.2f, 1.0f);
			world.spawnEntity(grenade);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.6f, 0.6f);
			user.getItemCooldownManager().set(this, THROW_DELAY_TICKS);
		}
		stack.decrementUnlessCreative(1, user);
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.heroverse.fusion_grenade.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
