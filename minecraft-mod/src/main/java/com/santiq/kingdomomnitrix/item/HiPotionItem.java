package com.santiq.kingdomomnitrix.item;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Hi-Potion: heilt sofort 4 Herzen. Bei voller Gesundheit wird nichts verbraucht. */
public class HiPotionItem extends Item {
	private static final float HEAL_AMOUNT = 8.0f;
	private static final int USE_DELAY_TICKS = 20;

	public HiPotionItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.getHealth() >= user.getMaxHealth()) {
			if (!world.isClient()) {
				user.sendMessage(Text.translatable("message.kingdomomnitrix.cure_full").formatted(Formatting.GRAY), true);
			}
			return TypedActionResult.fail(stack);
		}
		if (user instanceof ServerPlayerEntity player) {
			heal(player);
			user.getItemCooldownManager().set(this, USE_DELAY_TICKS);
		}
		stack.decrementUnlessCreative(1, user);
		return TypedActionResult.success(stack, world.isClient());
	}

	/** Heilwirkung mit Partikeln und Klang (auch aus dem Kommandomenue). */
	public static void heal(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		player.heal(HEAL_AMOUNT);
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getBodyY(0.6), player.getZ(), 10, 0.4, 0.5, 0.4, 0.0);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITCH_DRINK, SoundCategory.PLAYERS, 1.0f, 1.2f);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.hi_potion.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
