package com.santiq.kingdomomnitrix.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Materialkosten (Item-Stapel inkl. Komponenten) im Spielerinventar pruefen, abziehen und beschreiben. */
public final class MaterialCost {
	private MaterialCost() {
	}

	public static int count(PlayerInventory inventory, ItemStack wanted) {
		int total = 0;
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack candidate = inventory.getStack(slot);
			if (ItemStack.areItemsAndComponentsEqual(candidate, wanted)) {
				total += candidate.getCount();
			}
		}
		return total;
	}

	/** Alle Stapel, von denen nicht genug im Inventar liegt (mit der fehlenden Anzahl). */
	public static List<ItemStack> missing(PlayerInventory inventory, List<ItemStack> cost) {
		List<ItemStack> missing = new ArrayList<>();
		for (ItemStack needed : cost) {
			int have = count(inventory, needed);
			if (have < needed.getCount()) {
				missing.add(needed.copyWithCount(needed.getCount() - have));
			}
		}
		return missing;
	}

	/** Zieht die Kosten ab; vorher mit {@link #missing} pruefen. */
	public static void consume(PlayerInventory inventory, List<ItemStack> cost) {
		for (ItemStack needed : cost) {
			int remaining = needed.getCount();
			for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
				ItemStack candidate = inventory.getStack(slot);
				if (ItemStack.areItemsAndComponentsEqual(candidate, needed)) {
					int taken = Math.min(remaining, candidate.getCount());
					candidate.decrement(taken);
					remaining -= taken;
				}
			}
		}
		inventory.markDirty();
	}

	/** „2× Raritanium, 1× Orichalcum“ */
	public static MutableText describe(List<ItemStack> cost) {
		MutableText text = Text.empty();
		for (int i = 0; i < cost.size(); i++) {
			if (i > 0) {
				text.append(", ");
			}
			text.append(cost.get(i).getCount() + "× ").append(cost.get(i).getName());
		}
		return text;
	}
}
