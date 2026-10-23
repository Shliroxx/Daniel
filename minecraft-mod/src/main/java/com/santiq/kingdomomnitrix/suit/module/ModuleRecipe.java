package com.santiq.kingdomomnitrix.suit.module;

import com.santiq.kingdomomnitrix.suit.SuitArmorItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/**
 * Werkbank-Rezept fuer Upgrade-Module:
 * <ul>
 *   <li><b>Einbauen</b>: ein Ruestungsteil eines Endgame-Anzugs plus ein oder mehrere Module (so viele, wie Plaetze
 *       frei sind) ergibt das Teil mit eingebauten Modulen.</li>
 *   <li><b>Ausbauen</b>: das Teil allein ergibt das Teil ohne das zuletzt eingebaute Modul; das Modul bleibt im
 *       Raster liegen. Mehrfach wiederholen, um alle auszubauen.</li>
 * </ul>
 */
public class ModuleRecipe extends SpecialCraftingRecipe {
	public ModuleRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	private record Parts(ItemStack armor, List<SuitModule> modules) {
	}

	private static Parts parts(CraftingRecipeInput input) {
		ItemStack armor = ItemStack.EMPTY;
		List<SuitModule> modules = new ArrayList<>();
		for (int i = 0; i < input.getSize(); i++) {
			ItemStack stack = input.getStackInSlot(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() instanceof SuitArmorItem) {
				if (!armor.isEmpty()) {
					return null;
				}
				armor = stack;
			} else if (stack.getItem() instanceof ModuleItem module) {
				modules.add(module.module());
			} else {
				return null;
			}
		}
		return armor.isEmpty() ? null : new Parts(armor, modules);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		Parts parts = parts(input);
		if (parts == null) {
			return false;
		}
		List<SuitModule> installed = SuitModules.installed(parts.armor());
		if (parts.modules().isEmpty()) {
			return !installed.isEmpty();
		}
		return installed.size() + parts.modules().size() <= SuitModules.slots(parts.armor());
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		Parts parts = parts(input);
		if (parts == null) {
			return ItemStack.EMPTY;
		}
		ItemStack out = parts.armor().copyWithCount(1);
		List<SuitModule> installed = new ArrayList<>(SuitModules.installed(out));
		if (parts.modules().isEmpty()) {
			if (installed.isEmpty()) {
				return ItemStack.EMPTY;
			}
			installed.remove(installed.size() - 1);
		} else {
			installed.addAll(parts.modules());
			if (installed.size() > SuitModules.slots(out)) {
				return ItemStack.EMPTY;
			}
		}
		SuitModules.setInstalled(out, installed);
		return out;
	}

	/** Ausbauen: das entfernte Modul bleibt im Feld des Ruestungsteils liegen. */
	@Override
	public DefaultedList<ItemStack> getRemainder(CraftingRecipeInput input) {
		DefaultedList<ItemStack> remainder = super.getRemainder(input);
		Parts parts = parts(input);
		if (parts != null && parts.modules().isEmpty()) {
			List<SuitModule> installed = SuitModules.installed(parts.armor());
			if (!installed.isEmpty()) {
				for (int i = 0; i < input.getSize(); i++) {
					if (input.getStackInSlot(i).getItem() instanceof SuitArmorItem) {
						remainder.set(i, new ItemStack(SuitModules.item(installed.get(installed.size() - 1))));
					}
				}
			}
		}
		return remainder;
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 1;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SuitModules.RECIPE;
	}
}
