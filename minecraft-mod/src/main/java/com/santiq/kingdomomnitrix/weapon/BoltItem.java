package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.player.HeroData;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * Bolts: Sobald sie im Inventar eines Spielers landen (Aufsammeln, /give, Crafting), werden sie
 * aufs Bolt-Konto gebucht und verschwinden aus dem Inventar — wie in Ratchet & Clank.
 */
public class BoltItem extends Item {
	public BoltItem(Settings settings) {
		super(settings);
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient() || !(entity instanceof ServerPlayerEntity player) || stack.isEmpty()) {
			return;
		}
		int amount = stack.getCount();
		HeroData before = HeroDataAccess.get(player);
		int space = HeroData.MAX_BOLTS - before.bolts();
		if (space <= 0) {
			return; // Konto voll: Bolts bleiben als Items erhalten
		}
		int deposited = Math.min(space, amount);
		HeroDataAccess.update(player, data -> data.addBolts(deposited));
		stack.decrement(deposited);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
				SoundCategory.PLAYERS, 0.4f, 1.6f + world.getRandom().nextFloat() * 0.3f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.bolts_gained", deposited, before.bolts() + deposited)
				.formatted(Formatting.GOLD), true);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.bolt").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
