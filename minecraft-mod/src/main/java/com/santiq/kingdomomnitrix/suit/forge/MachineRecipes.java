package com.santiq.kingdomomnitrix.suit.forge;

import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.suit.module.SuitModule;
import com.santiq.kingdomomnitrix.suit.module.SuitModules;
import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import com.santiq.kingdomomnitrix.world.content.PlanetBlocks;
import com.santiq.kingdomomnitrix.world.content.PlanetItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.collection.DefaultedList;

/**
 * Rezepte der drei Maschinen. Bei der Legierungsschmiede ist die Reihenfolge der drei Eingaben egal; jede Zutat muss
 * in mindestens der angegebenen Menge in einem eigenen Slot liegen.
 */
public final class MachineRecipes {
	/** Eine Zutat mit Menge. */
	public record Part(Item item, int count) {
	}

	public record Recipe(MachineKind kind, List<Part> inputs, Item result, int resultCount, int ticks) {
		public ItemStack output() {
			return new ItemStack(result, resultCount);
		}
	}

	private static final List<Recipe> RECIPES = new ArrayList<>();
	/** Vanilla-Brennstoffe (einmal beim ersten Bedarf gelesen). */
	private static Map<Item, Integer> fuels;
	/** Brenndauer der Energiekerne (Ticks) — ein Kern haelt so lange wie 16 Kohle. */
	public static final int CORE_BURN = 25600;

	static {
		crush(ForgeMaterials.RAW_TAYDENITE, ForgeMaterials.TAYDENITE_DUST);
		crush(ForgeMaterials.RAW_BIOTECH, ForgeMaterials.BIOTECH_DUST);
		crush(ForgeMaterials.RAW_CELESTIUM, ForgeMaterials.CELESTIUM_DUST);
		crush(ModItems.RARITANIUM, ForgeMaterials.RARITANIUM_DUST);
		crush(ForgeMaterials.RAW_INFERNITE, ForgeMaterials.INFERNITE_DUST);

		smelt(ForgeMaterials.TAYDENITE_DUST, ForgeMaterials.TAYDENITE_CRYSTAL, 300);
		smelt(ForgeMaterials.BIOTECH_DUST, ForgeMaterials.BIOTECH_GEL, 360);
		smelt(ForgeMaterials.CELESTIUM_DUST, ForgeMaterials.CELESTIUM_INGOT, 600);
		smelt(ForgeMaterials.RARITANIUM_DUST, ForgeMaterials.RARITANIUM_INGOT, 300);
		smelt(ForgeMaterials.INFERNITE_DUST, ForgeMaterials.INFERNITE_CORE, 600);

		forge(ForgeMaterials.TAYDENITE_PLATE, 500, new Part(ForgeMaterials.TAYDENITE_CRYSTAL, 4),
				new Part(PlanetBlocks.PETROPIA_GIANT_CRYSTAL.asItem(), 4), new Part(Items.DIAMOND, 1));
		forge(ForgeMaterials.BIOTECH_WEAVE, 700, new Part(ForgeMaterials.BIOTECH_GEL, 4), new Part(ForgeMaterials.TAYDENITE_PLATE, 1),
				new Part(Items.REDSTONE_BLOCK, 2));
		forge(ForgeMaterials.STAR_MATRIX, 1200, new Part(ForgeMaterials.CELESTIUM_INGOT, 4), new Part(ForgeMaterials.BIOTECH_WEAVE, 1),
				new Part(Items.NETHER_STAR, 1));
		forge(ForgeMaterials.MAGNAPLATE_PLATING, 500, new Part(ForgeMaterials.RARITANIUM_INGOT, 4), new Part(PlanetItems.AZURITE_INGOT, 2),
				new Part(Items.IRON_BLOCK, 1));
		forge(ForgeMaterials.CARBONOX_PLATE, 700, new Part(ForgeMaterials.RARITANIUM_INGOT, 4), new Part(PlanetItems.PYRONITE_INGOT, 2),
				new Part(ForgeMaterials.MAGNAPLATE_PLATING, 1));
		forge(ForgeMaterials.HELLFIRE_PLATE, 1200, new Part(ForgeMaterials.INFERNITE_CORE, 4), new Part(ForgeMaterials.CARBONOX_PLATE, 1),
				new Part(Ben10Items.ENERGY_CORE_RED, 1));

		// Upgrade-Module: leere Platine, dann je Modul Platine + zwei Zutaten (Einbau im Anzug-Konfigurator)
		forge(SuitModules.BLANK, 400, new Part(ForgeMaterials.RARITANIUM_INGOT, 2), new Part(ForgeMaterials.TAYDENITE_CRYSTAL, 2),
				new Part(Items.GOLD_INGOT, 4));
		module(SuitModule.SHIELD_CAPACITY, new Part(ForgeMaterials.BIOTECH_GEL, 4), new Part(Items.DIAMOND, 4));
		module(SuitModule.SHIELD_RECHARGE, new Part(Items.REDSTONE_BLOCK, 4), new Part(ForgeMaterials.TAYDENITE_CRYSTAL, 4));
		module(SuitModule.MOBILITY, new Part(Items.PHANTOM_MEMBRANE, 4), new Part(ForgeMaterials.RARITANIUM_INGOT, 4));
		module(SuitModule.SPEED, new Part(Items.SUGAR, 8), new Part(ForgeMaterials.BIOTECH_DUST, 4));
		module(SuitModule.STRENGTH, new Part(Items.BLAZE_ROD, 4), new Part(ForgeMaterials.INFERNITE_DUST, 4));
		module(SuitModule.VITALITY, new Part(Items.GOLDEN_APPLE, 4), new Part(Items.GLISTERING_MELON_SLICE, 4));
	}

	private static void module(SuitModule module, Part first, Part second) {
		forge(SuitModules.item(module), 600, new Part(SuitModules.BLANK, 1), first, second);
	}

	private MachineRecipes() {
	}

	private static void crush(Item input, Item dust) {
		RECIPES.add(new Recipe(MachineKind.CRUSHER, List.of(new Part(input, 1)), dust, 2, 200));
	}

	private static void smelt(Item dust, Item result, int ticks) {
		RECIPES.add(new Recipe(MachineKind.SMELTER, List.of(new Part(dust, 1)), result, 1, ticks));
	}

	private static void forge(Item result, int ticks, Part... parts) {
		RECIPES.add(new Recipe(MachineKind.FORGE, List.of(parts), result, 1, ticks));
	}

	public static List<Recipe> all() {
		return List.copyOf(RECIPES);
	}

	/** Passendes Rezept fuer die Eingabe-Slots einer Maschine. */
	public static Optional<Recipe> find(MachineKind kind, DefaultedList<ItemStack> items) {
		for (Recipe recipe : RECIPES) {
			if (recipe.kind() == kind && matches(recipe, items, kind.inputs())) {
				return Optional.of(recipe);
			}
		}
		return Optional.empty();
	}

	/** Slot je Zutat (Index in items) oder null, wenn das Rezept nicht passt. Jede Zutat braucht einen eigenen Slot. */
	public static int[] assign(Recipe recipe, DefaultedList<ItemStack> items, int inputs) {
		int[] slots = new int[recipe.inputs().size()];
		boolean[] used = new boolean[inputs];
		outer:
		for (int p = 0; p < slots.length; p++) {
			Part part = recipe.inputs().get(p);
			for (int s = 0; s < inputs; s++) {
				ItemStack stack = items.get(s);
				if (!used[s] && stack.isOf(part.item()) && stack.getCount() >= part.count()) {
					used[s] = true;
					slots[p] = s;
					continue outer;
				}
			}
			return null;
		}
		for (int s = 0; s < inputs; s++) {
			if (!used[s] && !items.get(s).isEmpty()) {
				return null;   // Fremdes im Eingang
			}
		}
		return slots;
	}

	private static boolean matches(Recipe recipe, DefaultedList<ItemStack> items, int inputs) {
		return assign(recipe, items, inputs) != null;
	}

	/** Brenndauer eines Brennstoffs in Ticks (0 = kein Brennstoff). */
	public static int burnTime(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (Ben10Items.CORE_COLORS.containsKey(stack.getItem())) {
			return CORE_BURN;
		}
		if (fuels == null) {
			fuels = AbstractFurnaceBlockEntity.createFuelTimeMap();
		}
		return fuels.getOrDefault(stack.getItem(), 0);
	}

	public static boolean isFuel(ItemStack stack) {
		return burnTime(stack) > 0;
	}
}
