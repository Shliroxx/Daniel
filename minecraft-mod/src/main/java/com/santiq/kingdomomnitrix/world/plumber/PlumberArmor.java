package com.santiq.kingdomomnitrix.world.plumber;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;

/** Ruestungsmaterial der Klempner: knapp unter Diamant, Reparatur mit Klempner-Panzerplatten. */
public final class PlumberArmor {
	public static final RegistryEntry<ArmorMaterial> MATERIAL = register();

	private PlumberArmor() {
	}

	private static RegistryEntry<ArmorMaterial> register() {
		EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
		defense.put(ArmorItem.Type.HELMET, 3);
		defense.put(ArmorItem.Type.CHESTPLATE, 7);
		defense.put(ArmorItem.Type.LEGGINGS, 6);
		defense.put(ArmorItem.Type.BOOTS, 3);
		defense.put(ArmorItem.Type.BODY, 7);
		ArmorMaterial material = new ArmorMaterial(defense, 15, SoundEvents.ITEM_ARMOR_EQUIP_IRON,
				() -> Ingredient.ofItems(Ben10Items.PLUMBER_PLATING), List.of(new ArmorMaterial.Layer(KingdomOmnitrix.id("plumber"))), 1.5f, 0.0f);
		return Registry.registerReference(Registries.ARMOR_MATERIAL, KingdomOmnitrix.id("plumber"), material);
	}
}
