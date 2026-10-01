package com.santiq.kingdomomnitrix.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Liest und schreibt Mod-Zustand im CUSTOM_DATA-Komponenten eines ItemStacks. */
public final class ItemData {
	private ItemData() {
	}

	public static int getInt(ItemStack stack, String key) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		return nbt.getInt(key);
	}

	public static long getLong(ItemStack stack, String key) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		return nbt.getLong(key);
	}

	public static void putInt(ItemStack stack, String key, int value) {
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putInt(key, value));
	}

	public static void putLong(ItemStack stack, String key, long value) {
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putLong(key, value));
	}
}
