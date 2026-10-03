package com.santiq.kingdomomnitrix.item;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.Optional;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Items, die man im Kommandomenue (wie in Kingdom Hearts) sofort benutzen kann, ohne sie in die Hand zu nehmen.
 * Welche das sind, legt der Tag {@code #kingdomomnitrix:command_items} fest.
 */
public final class CommandItems {
	public static final TagKey<Item> TAG = TagKey.of(RegistryKeys.ITEM, KingdomOmnitrix.id("command_items"));
	private static final int COOLDOWN_TICKS = 20;

	public enum Result { USED, NOT_ALLOWED, MISSING, COOLDOWN, NO_EFFECT }

	private CommandItems() {
	}

	/** Erste passende Stelle im Inventar (Hotbar, Hauptinventar, Zweithand). */
	public static Optional<ItemStack> find(PlayerInventory inventory, Item item) {
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isEmpty() && stack.isOf(item)) {
				return Optional.of(stack);
			}
		}
		return Optional.empty();
	}

	public static Result use(ServerPlayerEntity player, Identifier itemId) {
		Optional<Item> item = Registries.ITEM.getOrEmpty(itemId);
		if (item.isEmpty() || !item.get().getDefaultStack().isIn(TAG)) {
			return Result.NOT_ALLOWED;
		}
		Optional<ItemStack> found = find(player.getInventory(), item.get());
		if (found.isEmpty()) {
			return Result.MISSING;
		}
		if (player.getItemCooldownManager().isCoolingDown(item.get())) {
			return Result.COOLDOWN;
		}
		ItemStack stack = found.get();
		if (item.get() instanceof HiPotionItem) {
			if (player.getHealth() >= player.getMaxHealth()) {
				player.sendMessage(Text.translatable("message.kingdomomnitrix.cure_full").formatted(Formatting.GRAY), true);
				return Result.NO_EFFECT;
			}
			HiPotionItem.heal(player);
			stack.decrementUnlessCreative(1, player);
		} else {
			// Essen und Traenke: dieselbe Wirkung wie nach dem normalen Verzehr, nur ohne Wartezeit
			ItemStack remainder = stack.finishUsing(player.getServerWorld(), player);
			if (remainder != stack && !remainder.isEmpty()) {
				player.getInventory().offerOrDrop(remainder);
			}
		}
		player.getItemCooldownManager().set(item.get(), COOLDOWN_TICKS);
		return Result.USED;
	}
}
