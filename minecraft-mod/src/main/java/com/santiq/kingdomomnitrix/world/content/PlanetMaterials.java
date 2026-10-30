package com.santiq.kingdomomnitrix.world.content;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.block.Block;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Materialien der Planeten, aufsteigend: Sonnenkupfer (Veldin) und Azurit (Kerwan) auf Eisen-Stufe, Viridium (Novalis)
 * und Aquarin (Rilgar) auf Diamant-Stufe, Pyronit (Torren IV) auf Netherit-Stufe.
 */
public final class PlanetMaterials {
	/** Werkzeug-Materialien (Haltbarkeit, Abbautempo, Schaden, Verzauberbarkeit). */
	public enum Tool implements ToolMaterial {
		SOLAR_COPPER(BlockTags.INCORRECT_FOR_IRON_TOOL, 320, 6.5f, 2.0f, 16, () -> PlanetItems.SOLAR_COPPER_INGOT),
		AZURITE(BlockTags.INCORRECT_FOR_IRON_TOOL, 540, 7.0f, 2.5f, 18, () -> PlanetItems.AZURITE_INGOT),
		VIRIDIUM(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1050, 8.0f, 3.0f, 12, () -> PlanetItems.VIRIDIUM_INGOT),
		AQUARINE(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1500, 8.5f, 3.5f, 20, () -> PlanetItems.AQUARINE_INGOT),
		PYRONITE(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2100, 9.5f, 4.5f, 15, () -> PlanetItems.PYRONITE_INGOT);

		private final TagKey<Block> inverse;
		private final int durability;
		private final float speed;
		private final float damage;
		private final int enchantability;
		private final Supplier<Item> repair;

		Tool(TagKey<Block> inverse, int durability, float speed, float damage, int enchantability, Supplier<Item> repair) {
			this.inverse = inverse;
			this.durability = durability;
			this.speed = speed;
			this.damage = damage;
			this.enchantability = enchantability;
			this.repair = repair;
		}

		@Override
		public int getDurability() {
			return durability;
		}

		@Override
		public float getMiningSpeedMultiplier() {
			return speed;
		}

		@Override
		public float getAttackDamage() {
			return damage;
		}

		@Override
		public TagKey<Block> getInverseTag() {
			return inverse;
		}

		@Override
		public int getEnchantability() {
			return enchantability;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return Ingredient.ofItems(repair.get());
		}
	}

	public static final RegistryEntry<ArmorMaterial> SOLAR_COPPER_ARMOR = armor("solar_copper", 2, 6, 5, 2, 16,
			SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.5f, 0.0f, () -> PlanetItems.SOLAR_COPPER_INGOT);
	public static final RegistryEntry<ArmorMaterial> AZURITE_ARMOR = armor("azurite", 3, 6, 5, 2, 18,
			SoundEvents.ITEM_ARMOR_EQUIP_IRON, 1.0f, 0.0f, () -> PlanetItems.AZURITE_INGOT);
	public static final RegistryEntry<ArmorMaterial> VIRIDIUM_ARMOR = armor("viridium", 3, 7, 6, 3, 12,
			SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, 2.0f, 0.0f, () -> PlanetItems.VIRIDIUM_INGOT);
	public static final RegistryEntry<ArmorMaterial> AQUARINE_ARMOR = armor("aquarine", 3, 8, 6, 3, 20,
			SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, 2.5f, 0.05f, () -> PlanetItems.AQUARINE_INGOT);
	public static final RegistryEntry<ArmorMaterial> PYRONITE_ARMOR = armor("pyronite", 4, 9, 7, 4, 15,
			SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, 3.5f, 0.1f, () -> PlanetItems.PYRONITE_INGOT);

	private PlanetMaterials() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Planeten-Materialien registriert: {}", PYRONITE_ARMOR.getIdAsString());
	}

	private static RegistryEntry<ArmorMaterial> armor(String name, int helmet, int chest, int legs, int boots, int enchantability,
			RegistryEntry<SoundEvent> sound, float toughness, float knockback, Supplier<Item> repair) {
		EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
		defense.put(ArmorItem.Type.HELMET, helmet);
		defense.put(ArmorItem.Type.CHESTPLATE, chest);
		defense.put(ArmorItem.Type.LEGGINGS, legs);
		defense.put(ArmorItem.Type.BOOTS, boots);
		defense.put(ArmorItem.Type.BODY, chest);
		ArmorMaterial material = new ArmorMaterial(defense, enchantability, sound, () -> Ingredient.ofItems(repair.get()),
				List.of(new ArmorMaterial.Layer(KingdomOmnitrix.id(name))), toughness, knockback);
		return Registry.registerReference(Registries.ARMOR_MATERIAL, KingdomOmnitrix.id(name), material);
	}
}
