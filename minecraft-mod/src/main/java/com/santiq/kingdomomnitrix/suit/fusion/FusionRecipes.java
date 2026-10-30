package com.santiq.kingdomomnitrix.suit.fusion;

import com.santiq.kingdomomnitrix.suit.SuitItems;
import com.santiq.kingdomomnitrix.suit.SuitSet;
import com.santiq.kingdomomnitrix.suit.forge.ForgeMaterials;
import com.santiq.kingdomomnitrix.suit.weapon.SuitWeapon;
import com.santiq.kingdomomnitrix.suit.weapon.SuitWeapons;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Fusionsrezepte: Mittelstueck (Ruestungsteil der Vorstufe, bei Stufe 1 Netherit) im Kern, je eine Panzerplatte auf
 * den Injektoren, Energiekerne im Kern. Helm 5 Platten, Brust 8, Beine 7, Stiefel 4; Stufe 1/2/3 braucht 2/4/8
 * Energiekerne und laeuft 10/20/30 Sekunden; sie braucht einen Altar und Injektoren mindestens derselben Stufe. Das
 * Ergebnis behaelt Verzauberungen und Namen des Mittelstuecks.
 */
public final class FusionRecipes {
	/**
	 * Ein Rezept. tier: Ruestungsstufe 1..3 = noetige Altar- und Injektorstufe. Instabilitaet wie bei der
	 * Thaumcraft-Infusion: hoehere Stufen und mehr Platten sind schwerer zu halten.
	 */
	public record Recipe(Item center, Item plate, int plates, int cores, int ticks, Item result, int tier) {
		public int instability() {
			int base = switch (tier) {
				case 1 -> 10;
				case 2 -> 24;
				default -> 36;
			};
			return base + plates / 2;
		}

		/** Hitze je Tick (ohne Kuehlung). */
		public float heatRate() {
			return switch (tier) {
				case 1 -> 0.05f;
				case 2 -> 0.2f;
				default -> 0.3f;
			};
		}
	}

	private static final List<Recipe> RECIPES = new ArrayList<>();
	private static final ArmorItem.Type[] PIECES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS};
	private static final Item[] NETHERITE = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
			Items.NETHERITE_BOOTS};
	private static final int[] PLATES = {5, 8, 7, 4};

	static {
		line(new SuitSet[] {SuitSet.TAYDENITE, SuitSet.GALVAN, SuitSet.CELESTIAL},
				new Item[] {ForgeMaterials.TAYDENITE_PLATE, ForgeMaterials.BIOTECH_WEAVE, ForgeMaterials.STAR_MATRIX});
		line(new SuitSet[] {SuitSet.MAGNAPLATE, SuitSet.CARBONOX, SuitSet.INFERNOX},
				new Item[] {ForgeMaterials.MAGNAPLATE_PLATING, ForgeMaterials.CARBONOX_PLATE, ForgeMaterials.HELLFIRE_PLATE});
		// Waffen: vier Platten, Energiekerne und Dauer wie die Ruestung derselben Stufe
		weapon(Items.NETHERITE_SWORD, ForgeMaterials.TAYDENITE_PLATE, SuitWeapon.TAYDENITE_BLADE);
		weapon(Items.CROSSBOW, ForgeMaterials.BIOTECH_WEAVE, SuitWeapon.GALVAN_RIFLE);
		weapon(SuitWeapons.item(SuitWeapon.TAYDENITE_BLADE), ForgeMaterials.STAR_MATRIX, SuitWeapon.STAR_SWORD);
		weapon(Items.NETHERITE_AXE, ForgeMaterials.MAGNAPLATE_PLATING, SuitWeapon.MAGNAPLATE_HAMMER);
		weapon(Items.CROSSBOW, ForgeMaterials.CARBONOX_PLATE, SuitWeapon.CARBONOX_LAUNCHER);
		weapon(SuitWeapons.item(SuitWeapon.CARBONOX_LAUNCHER), ForgeMaterials.HELLFIRE_PLATE, SuitWeapon.INFERNOX_RYNO);
	}

	private FusionRecipes() {
	}

	private static void weapon(Item center, Item plate, SuitWeapon weapon) {
		int t = weapon.set().tier();
		RECIPES.add(new Recipe(center, plate, 4, 2 << (t - 1), 200 * t, SuitWeapons.item(weapon), t));
	}

	private static void line(SuitSet[] tiers, Item[] plates) {
		for (int p = 0; p < PIECES.length; p++) {
			Item center = NETHERITE[p];
			for (int t = 0; t < tiers.length; t++) {
				Item result = SuitItems.get(tiers[t], PIECES[p]);
				RECIPES.add(new Recipe(center, plates[t], PLATES[p], 2 << t, 200 * (t + 1), result, t + 1));
				center = result;
			}
		}
	}

	public static List<Recipe> all() {
		return List.copyOf(RECIPES);
	}

	/** Rezept zum Mittelstueck (es gibt hoechstens eins je Mittelstueck und Platte). */
	public static Optional<Recipe> forCenter(ItemStack center, List<ItemStack> injected) {
		for (Recipe recipe : RECIPES) {
			if (center.isOf(recipe.center()) && injected.stream().anyMatch(s -> s.isOf(recipe.plate()))) {
				return Optional.of(recipe);
			}
		}
		for (Recipe recipe : RECIPES) {
			if (center.isOf(recipe.center())) {
				return Optional.of(recipe);
			}
		}
		return Optional.empty();
	}
}
