package com.santiq.kingdomomnitrix.suit.module;

import java.util.Collections;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/**
 * Die Modul-Sockel des Konfigurators als Inventar: liest und schreibt direkt die Sockelbelegung des Ruestungsteils im
 * Teil-Inventar. Ein Sockel haelt genau ein Modul.
 */
public class ModuleInventory implements Inventory {
	public static final int SIZE = 3;
	private final Inventory piece;

	public ModuleInventory(Inventory piece) {
		this.piece = piece;
	}

	private ItemStack armor() {
		return piece.getStack(0);
	}

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return SuitModules.installed(armor()).isEmpty();
	}

	@Override
	public ItemStack getStack(int slot) {
		List<SuitModule> sockets = SuitModules.sockets(armor());
		return slot < sockets.size() && sockets.get(slot) != null ? new ItemStack(SuitModules.item(sockets.get(slot))) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack current = getStack(slot);
		if (!current.isEmpty() && amount > 0) {
			setStack(slot, ItemStack.EMPTY);
		}
		return current;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return removeStack(slot, 1);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		ItemStack armor = armor();
		List<SuitModule> sockets = SuitModules.sockets(armor);
		if (slot >= sockets.size()) {
			return;
		}
		sockets.set(slot, stack.getItem() instanceof ModuleItem module ? module.module() : null);
		SuitModules.setSockets(armor, sockets);
		piece.markDirty();
	}

	@Override
	public int getMaxCountPerStack() {
		return 1;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return stack.getItem() instanceof ModuleItem && slot < SuitModules.slots(armor());
	}

	@Override
	public void markDirty() {
		piece.markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return piece.canPlayerUse(player);
	}

	@Override
	public void clear() {
		SuitModules.setSockets(armor(), Collections.nCopies(SuitModules.slots(armor()), null));
	}
}
