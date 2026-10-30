package com.santiq.kingdomomnitrix.suit;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;

/** Ruestungsmaterialien der Endgame-Sets (Werte aus {@link SuitSet}); Reparatur folgt mit den neuen Erzen. */
public final class SuitMaterials {
	private static final Map<SuitSet, RegistryEntry<ArmorMaterial>> MATERIALS = new EnumMap<>(SuitSet.class);

	static {
		for (SuitSet set : SuitSet.values()) {
			EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
			for (ArmorItem.Type type : ArmorItem.Type.values()) {
				defense.put(type, set.defense(type));
			}
			ArmorMaterial material = new ArmorMaterial(defense, set.enchantability(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE,
					() -> Ingredient.EMPTY, List.of(new ArmorMaterial.Layer(KingdomOmnitrix.id(set.id()))), set.toughness(), set.knockback());
			MATERIALS.put(set, Registry.registerReference(Registries.ARMOR_MATERIAL, KingdomOmnitrix.id("suit_" + set.id()), material));
		}
	}

	private SuitMaterials() {
	}

	public static RegistryEntry<ArmorMaterial> of(SuitSet set) {
		return MATERIALS.get(set);
	}
}
