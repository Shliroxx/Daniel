package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.DnaSampleItem;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.gadget.HeliPackItem;
import com.santiq.kingdomomnitrix.item.HiPotionItem;
import com.santiq.kingdomomnitrix.keyblade.KeybladeItem;
import com.santiq.kingdomomnitrix.weapon.CombusterItem;
import com.santiq.kingdomomnitrix.weapon.FusionGrenadeItem;
import com.santiq.kingdomomnitrix.weapon.OmniWrenchItem;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
	// --- Kingdom Hearts ---
	public static final Item KINGDOM_KEY = register("kingdom_key", new KeybladeItem(ToolMaterials.NETHERITE,
			new Item.Settings()
					.rarity(Rarity.EPIC)
					.attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 4, -2.2f))));
	public static final Item HEART = register("heart", new Item(new Item.Settings().rarity(Rarity.UNCOMMON)));
	public static final Item HI_POTION = register("hi_potion", new HiPotionItem(new Item.Settings().maxCount(16)));
	public static final Item PAOPU_FRUIT = register("paopu_fruit", new Item(new Item.Settings()
			.rarity(Rarity.RARE)
			.food(new FoodComponent.Builder()
					.nutrition(6)
					.saturationModifier(1.0f)
					.alwaysEdible()
					.statusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1), 1.0f)
					.statusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1200, 1), 1.0f)
					.build())));
	public static final Item SHADOW_SPAWN_EGG = register("shadow_spawn_egg",
			new SpawnEggItem(ModEntities.SHADOW, 0x111111, 0xFFD800, new Item.Settings()));

	// --- Ben 10 ---
	public static final Item OMNITRIX = register("omnitrix", new OmnitrixItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));
	public static final Item DNA_SAMPLE = register("dna_sample", new DnaSampleItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));

	// --- Ratchet & Clank ---
	public static final Item BOLT = register("bolt", new Item(new Item.Settings()));
	public static final Item OMNIWRENCH = register("omniwrench", new OmniWrenchItem(ToolMaterials.DIAMOND,
			new Item.Settings()
					.rarity(Rarity.RARE)
					.attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.DIAMOND, 3, -2.6f))));
	public static final Item COMBUSTER = register("combuster", new CombusterItem(new Item.Settings().maxDamage(CombusterItem.MAGAZINE).rarity(Rarity.UNCOMMON)));
	public static final Item FUSION_GRENADE = register("fusion_grenade", new FusionGrenadeItem(new Item.Settings().maxCount(16)));
	public static final Item HELI_PACK = register("heli_pack", new HeliPackItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));
	public static final Item BOLT_CRATE = register("bolt_crate", new BlockItem(ModBlocks.BOLT_CRATE, new Item.Settings()));

	// --- Geschoss-Darstellung (nicht im Kreativ-Tab) ---
	public static final Item FIRE_ORB = register("fire_orb", new Item(new Item.Settings()));
	public static final Item ICE_ORB = register("ice_orb", new Item(new Item.Settings()));
	public static final Item PLASMA_SHOT = register("plasma_shot", new Item(new Item.Settings()));
	public static final Item CRYSTAL_SHARD = register("crystal_shard", new Item(new Item.Settings()));

	private ModItems() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Items registriert");
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, KingdomOmnitrix.id(name), item);
	}
}
