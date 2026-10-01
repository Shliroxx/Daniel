package com.daniel.heroverse.item;

import com.daniel.heroverse.entity.HeroProjectileEntity;
import com.daniel.heroverse.registry.ModItems;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
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

/**
 * Combuster: Plasma-Blaster. Die Haltbarkeit ist das Magazin; ist es leer,
 * wird automatisch mit einem Bolt aus dem Inventar nachgeladen.
 */
public class CombusterItem extends Item {
	public static final int MAGAZINE = 40;
	public static final int AMMO_PER_BOLT = 8;
	private static final int FIRE_DELAY_TICKS = 5;

	public CombusterItem(Settings settings) {
		super(settings);
	}

	private static int ammo(ItemStack stack) {
		return stack.getMaxDamage() - stack.getDamage();
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		boolean creative = user.getAbilities().creativeMode;

		// Der letzte Haltbarkeitspunkt bleibt immer stehen, damit die Waffe nie zerbricht.
		if (!creative && ammo(stack) <= 1 && !reload(user, stack)) {
			if (!world.isClient()) {
				user.sendMessage(Text.translatable("message.heroverse.no_ammo").formatted(Formatting.RED), true);
				world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8f, 1.2f);
			}
			return TypedActionResult.fail(stack);
		}

		if (!world.isClient()) {
			HeroProjectileEntity.shoot(world, user, ModItems.PLASMA_SHOT, 2.6f, 1.0f);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.7f, 1.8f);
			if (!creative) {
				stack.setDamage(stack.getDamage() + 1);
			}
			user.getItemCooldownManager().set(this, FIRE_DELAY_TICKS);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	/** Verbraucht einen Bolt aus dem Inventar und fuellt das Magazin auf. */
	private static boolean reload(PlayerEntity user, ItemStack weapon) {
		PlayerInventory inventory = user.getInventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack candidate = inventory.getStack(slot);
			if (candidate.isOf(ModItems.BOLT)) {
				if (!user.getWorld().isClient()) {
					candidate.decrement(1);
					weapon.setDamage(Math.max(0, weapon.getDamage() - AMMO_PER_BOLT));
					user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.PLAYERS, 0.6f, 1.6f);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean canRepair(ItemStack stack, ItemStack ingredient) {
		return ingredient.isOf(ModItems.BOLT);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.heroverse.ammo", Math.max(0, ammo(stack) - 1), MAGAZINE - 1).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.heroverse.combuster.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
