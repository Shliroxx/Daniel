package com.santiq.kingdomomnitrix.suit.power;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * Schalter des Anzugs, gespeichert in der Brustplatte (der Zustand reist mit dem Teil). Standard: alles an.
 */
public final class SuitToggles {
	public static final int SHIELD = 1;
	public static final int FLIGHT = 2;
	public static final int BOOST = 4;
	public static final int ALL = SHIELD | FLIGHT | BOOST;
	private static final String KEY = "SuitToggles";

	private SuitToggles() {
	}

	public static int get(ItemStack chest) {
		NbtCompound nbt = chest.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		return nbt.contains(KEY) ? nbt.getInt(KEY) : ALL;
	}

	public static void flip(ItemStack chest, int bit) {
		NbtCompound nbt = chest.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putInt(KEY, get(chest) ^ (bit & ALL));
		chest.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	public static boolean on(int toggles, int bit) {
		return (toggles & bit) != 0;
	}
}
