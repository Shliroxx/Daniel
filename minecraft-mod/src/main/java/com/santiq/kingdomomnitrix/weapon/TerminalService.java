package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import com.santiq.kingdomomnitrix.util.MaterialCost;
import java.util.List;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Serverseitige Laden-Logik des Waffen-Terminals. Jede Aktion prueft Abstand zum Terminal, Bolt-Konto
 * und Inventar selbst; der Client schickt nur die Absicht.
 */
public final class TerminalService {
	public enum Action { BUY, UPGRADE, REFILL }

	private static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

	private TerminalService() {
	}

	public static void handle(ServerPlayerEntity player, Action action, Identifier weaponId, BlockPos terminal) {
		if (!player.getWorld().getBlockState(terminal).isOf(ModBlocks.WEAPON_TERMINAL)
				|| player.squaredDistanceTo(terminal.toCenterPos()) > MAX_DISTANCE_SQ) {
			return;
		}
		switch (action) {
			case BUY -> buy(player, weaponId);
			case UPGRADE -> upgrade(player, weaponId);
			case REFILL -> refill(player);
		}
	}

	private static void buy(ServerPlayerEntity player, Identifier weaponId) {
		Optional<WeaponDefinition> found = WeaponRegistry.get(player.getWorld().getRegistryManager(), weaponId);
		Optional<Item> item = found.flatMap(definition -> Registries.ITEM.getOrEmpty(definition.item()));
		if (found.isEmpty() || item.isEmpty() || found.get().price() <= 0) {
			return;
		}
		if (!charge(player, found.get().price())) {
			return;
		}
		ItemStack stack = WeaponItem.create(item.get(), found.get());
		player.getInventory().offerOrDrop(stack);
		success(player, Text.translatable("message.kingdomomnitrix.terminal.bought", stack.getName()));
	}

	private static void upgrade(ServerPlayerEntity player, Identifier weaponId) {
		Optional<WeaponDefinition> found = WeaponRegistry.get(player.getWorld().getRegistryManager(), weaponId);
		if (found.isEmpty()) {
			return;
		}
		WeaponDefinition definition = found.get();
		ItemStack stack = findWeapon(player.getInventory(), definition.item());
		if (stack.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.terminal.not_owned").formatted(Formatting.RED), true);
			return;
		}
		WeaponState state = WeaponItem.state(stack);
		if (state.level() >= definition.maxLevel()) {
			return;
		}
		WeaponDefinition.Level next = definition.level(state.level() + 1);
		boolean creative = player.getAbilities().creativeMode;
		List<ItemStack> missing = creative ? List.of() : MaterialCost.missing(player.getInventory(), next.items());
		if (!missing.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.terminal.missing_items", MaterialCost.describe(missing))
					.formatted(Formatting.RED), true);
			return;
		}
		if (!charge(player, next.price())) {
			return;
		}
		if (!creative) {
			MaterialCost.consume(player.getInventory(), next.items());
		}
		// Aufstieg fuellt das Magazin (wie in Ratchet & Clank)
		WeaponItem.setState(stack, new WeaponState(state.level() + 1, next.maxAmmo(), next.maxAmmo()));
		Text levelText = state.level() + 1 >= definition.maxLevel()
				? Text.translatable("tooltip.kingdomomnitrix.weapon.max")
				: Text.translatable("tooltip.kingdomomnitrix.weapon.level", state.level() + 1, definition.maxLevel());
		success(player, Text.translatable("message.kingdomomnitrix.terminal.upgraded", stack.getName(), levelText));
	}

	private static void refill(ServerPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		var registries = player.getWorld().getRegistryManager();
		int spent = 0;
		int bolts = HeroDataAccess.get(player).bolts();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!(stack.getItem() instanceof WeaponItem)) {
				continue;
			}
			Optional<WeaponDefinition> definition = WeaponRegistry.forStack(registries, stack);
			WeaponState state = WeaponItem.state(stack);
			if (definition.isEmpty() || state.missingAmmo() <= 0) {
				continue;
			}
			int price = Math.max(0, definition.get().ammoPrice());
			int affordable = price == 0 ? state.missingAmmo() : Math.min(state.missingAmmo(), (bolts - spent) / price);
			if (affordable <= 0) {
				continue;
			}
			WeaponItem.setState(stack, state.withAmmo(state.ammo() + affordable));
			spent += affordable * price;
		}
		if (spent == 0 && bolts == 0) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.terminal.no_bolts").formatted(Formatting.RED), true);
			return;
		}
		int cost = spent;
		HeroDataAccess.update(player, data -> data.addBolts(-cost));
		success(player, Text.translatable("message.kingdomomnitrix.terminal.refilled", cost));
	}

	/** Gesamtkosten, um alle Waffen im Inventar aufzufuellen (fuer die Anzeige im Terminal). */
	public static int refillCost(PlayerInventory inventory, net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
		int total = 0;
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.getItem() instanceof WeaponItem) {
				int price = WeaponRegistry.forStack(registries, stack).map(WeaponDefinition::ammoPrice).orElse(0);
				total += WeaponItem.state(stack).missingAmmo() * price;
			}
		}
		return total;
	}

	public static ItemStack findWeapon(PlayerInventory inventory, Identifier itemId) {
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).equals(itemId)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean charge(ServerPlayerEntity player, int price) {
		if (player.getAbilities().creativeMode || price <= 0) {
			return true;
		}
		if (!HeroDataAccess.get(player).canAfford(price)) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.terminal.no_bolts").formatted(Formatting.RED), true);
			player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.6f, 0.5f);
			return false;
		}
		HeroDataAccess.update(player, data -> data.addBolts(-price));
		return true;
	}

	private static void success(ServerPlayerEntity player, Text message) {
		player.sendMessage(message.copy().formatted(Formatting.GREEN), false);
		player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.8f, 0.9f);
	}
}
