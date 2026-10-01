package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.DnaSampleItem;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.gadget.HeliPackItem;
import com.santiq.kingdomomnitrix.gadget.SwingshotItem;
import com.santiq.kingdomomnitrix.item.HiPotionItem;
import com.santiq.kingdomomnitrix.keyblade.KeybladeItem;
import com.santiq.kingdomomnitrix.magic.SpellCrystalItem;
import com.santiq.kingdomomnitrix.weapon.BoltItem;
import com.santiq.kingdomomnitrix.weapon.CombusterItem;
import com.santiq.kingdomomnitrix.weapon.FusionGrenadeItem;
import com.santiq.kingdomomnitrix.weapon.OmniWrenchItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
	// --- Kingdom Hearts ---
	public static final Item KINGDOM_KEY = register("kingdom_key", keyblade(Rarity.RARE));
	public static final Item OATHKEEPER = register("oathkeeper", keyblade(Rarity.EPIC));
	public static final Item KEYBLADE_FORGE = register("keyblade_forge", new BlockItem(ModBlocks.KEYBLADE_FORGE, new Item.Settings().rarity(Rarity.UNCOMMON)));
	public static final Item SPELL_CRYSTAL = register("spell_crystal", new SpellCrystalItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));
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
	public static final Item SOLDIER_SPAWN_EGG = register("soldier_spawn_egg",
			new SpawnEggItem(ModEntities.SOLDIER, 0x1B2A4A, 0xC9CDD3, new Item.Settings()));
	public static final Item LARGE_BODY_SPAWN_EGG = register("large_body_spawn_egg",
			new SpawnEggItem(ModEntities.LARGE_BODY, 0x3B2A5A, 0xE0A030, new Item.Settings()));
	public static final Item AIR_SOLDIER_SPAWN_EGG = register("air_soldier_spawn_egg",
			new SpawnEggItem(ModEntities.AIR_SOLDIER, 0x2A6E3A, 0xFFD800, new Item.Settings()));
	public static final Item DARKBALL_SPAWN_EGG = register("darkball_spawn_egg",
			new SpawnEggItem(ModEntities.DARKBALL, 0x0A0A12, 0x8E44AD, new Item.Settings()));

	// --- Ben 10 ---
	public static final Item OMNITRIX = register("omnitrix", new OmnitrixItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));
	public static final Item DNA_SAMPLE = register("dna_sample", new DnaSampleItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));

	// --- Ratchet & Clank ---
	public static final Item BOLT = register("bolt", new BoltItem(new Item.Settings()));
	public static final Item OMNIWRENCH = register("omniwrench", new OmniWrenchItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));
	public static final Item COMBUSTER = register("combuster", new CombusterItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
	public static final Item FUSION_GRENADE = register("fusion_grenade", new FusionGrenadeItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
	public static final Item HELI_PACK = register("heli_pack", new HeliPackItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));
	public static final Item SWINGSHOT = register("swingshot", new SwingshotItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));
	public static final Item WEAPON_TERMINAL = register("weapon_terminal", new BlockItem(ModBlocks.WEAPON_TERMINAL, new Item.Settings().rarity(Rarity.UNCOMMON)));
	public static final Item BOLT_CRATE = register("bolt_crate", new BlockItem(ModBlocks.BOLT_CRATE, new Item.Settings()));

	// --- Geschoss-Darstellung (nicht im Kreativ-Tab) ---
	public static final Item FIRE_ORB = register("fire_orb", new Item(new Item.Settings()));
	public static final Item ICE_ORB = register("ice_orb", new Item(new Item.Settings()));
	public static final Item PLASMA_SHOT = register("plasma_shot", new Item(new Item.Settings()));
	public static final Item DARK_ORB = register("dark_orb", new Item(new Item.Settings()));
	public static final Item CRYSTAL_SHARD = register("crystal_shard", new Item(new Item.Settings()));

	private ModItems() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Items registriert");
	}

	/** Keyblades sind unzerstoerbar; ihre Werte kommen aus data/…/kingdomomnitrix/keyblade/*.json. */
	private static Item keyblade(Rarity rarity) {
		return new KeybladeItem(new Item.Settings()
				.maxCount(1)
				.maxDamage(2000)
				.rarity(rarity)
				.component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false)));
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, KingdomOmnitrix.id(name), item);
	}
}
