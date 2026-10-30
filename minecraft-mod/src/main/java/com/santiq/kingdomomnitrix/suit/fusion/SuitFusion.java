package com.santiq.kingdomomnitrix.suit.fusion;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Rarity;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Registriert den Fusionsaltar: Kern, Injektoren (drei Stufen), Bauteile der Altarstufen (Runen, Saeulen, Kronen,
 * Chaos-Fokus, Kryo-Kuehler) und die Schluesselgegenstaende der Abenteuer (Resonanz-Splitter aus Petrosapien-Ruinen,
 * Nefarious-Chip vom Boss Nefarious, Chaos-Herz aus der Geheimen Wasserstrasse), dazu Kryo-Zelle und Fusions-Kodex.
 */
public final class SuitFusion {
	private static final List<Item> ITEMS = new ArrayList<>();

	public static final FusionBlocks.Core CORE = Registry.register(Registries.BLOCK, KingdomOmnitrix.id("fusion_core"),
			new FusionBlocks.Core(machine(MapColor.CYAN, 6.0f).luminance(state -> 12)));
	public static final FusionBlocks.Injector INJECTOR = injector("fusion_injector", 1, MapColor.CYAN);
	public static final FusionBlocks.Injector INJECTOR_GALVAN = injector("fusion_injector_galvan", 2, MapColor.LIME);
	public static final FusionBlocks.Injector INJECTOR_STELLAR = injector("fusion_injector_stellar", 3, MapColor.PURPLE);
	public static final Block RUNE = part("fusion_rune", machine(MapColor.DEEPSLATE_GRAY, 4.0f).luminance(state -> 6),
			VoxelShapes.fullCube(), Rarity.UNCOMMON);
	public static final Block PYLON = part("fusion_pylon", machine(MapColor.IRON_GRAY, 4.0f).luminance(state -> 4),
			FusionBlocks.PYLON_SHAPE, Rarity.UNCOMMON);
	public static final Block RESONANCE_CAPSTONE = part("resonance_capstone", machine(MapColor.EMERALD_GREEN, 4.0f).luminance(state -> 11),
			FusionBlocks.CAPSTONE_SHAPE, Rarity.RARE);
	public static final Block CELESTIAL_CAPSTONE = part("celestial_capstone", machine(MapColor.PURPLE, 5.0f).luminance(state -> 13),
			FusionBlocks.CAPSTONE_SHAPE, Rarity.EPIC);
	public static final Block CHAOS_FOCUS = part("chaos_focus", machine(MapColor.BLACK, 8.0f).luminance(state -> 15),
			FusionBlocks.FOCUS_SHAPE, Rarity.EPIC);
	public static final Block CRYO_COOLER = part("cryo_cooler", machine(MapColor.LIGHT_BLUE, 4.0f).luminance(state -> 5).slipperiness(0.98f),
			VoxelShapes.fullCube(), Rarity.RARE);

	public static final Item CORE_ITEM = blockItem("fusion_core", CORE, Rarity.EPIC);
	public static final Item INJECTOR_ITEM = blockItem("fusion_injector", INJECTOR, Rarity.RARE);
	public static final Item INJECTOR_GALVAN_ITEM = blockItem("fusion_injector_galvan", INJECTOR_GALVAN, Rarity.EPIC);
	public static final Item INJECTOR_STELLAR_ITEM = blockItem("fusion_injector_stellar", INJECTOR_STELLAR, Rarity.EPIC);

	public static final Item RESONANCE_SHARD = item("resonance_shard", new Item(new Item.Settings().rarity(Rarity.RARE)));
	public static final Item NEFARIOUS_CHIP = item("nefarious_chip", new Item(new Item.Settings().rarity(Rarity.EPIC).fireproof()));
	public static final Item CHAOS_HEART = item("chaos_heart", new Item(new Item.Settings().rarity(Rarity.EPIC).fireproof().maxCount(16)));
	public static final Item CRYO_CELL = item("cryo_cell", new Item(new Item.Settings().rarity(Rarity.UNCOMMON).maxCount(16)));
	public static final Item CODEX = item("fusion_codex", new FusionCodexItem(new Item.Settings().rarity(Rarity.RARE).maxCount(1)));

	public static final BlockEntityType<FusionCoreBlockEntity> CORE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			KingdomOmnitrix.id("fusion_core"), BlockEntityType.Builder.create(FusionCoreBlockEntity::new, CORE).build());
	public static final BlockEntityType<FusionInjectorBlockEntity> INJECTOR_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			KingdomOmnitrix.id("fusion_injector"),
			BlockEntityType.Builder.create(FusionInjectorBlockEntity::new, INJECTOR, INJECTOR_GALVAN, INJECTOR_STELLAR).build());

	private SuitFusion() {
	}

	private static AbstractBlock.Settings machine(MapColor color, float strength) {
		return AbstractBlock.Settings.create().mapColor(color).strength(strength, 1200.0f).requiresTool().sounds(BlockSoundGroup.NETHERITE)
				.nonOpaque();
	}

	private static FusionBlocks.Injector injector(String id, int tier, MapColor color) {
		return Registry.register(Registries.BLOCK, KingdomOmnitrix.id(id),
				new FusionBlocks.Injector(machine(color, 5.0f).luminance(state -> 8), tier));
	}

	private static Block part(String id, AbstractBlock.Settings settings, VoxelShape shape, Rarity rarity) {
		Block block = Registry.register(Registries.BLOCK, KingdomOmnitrix.id(id), new FusionBlocks.Part(settings, shape));
		blockItem(id, block, rarity);
		return block;
	}

	private static Item blockItem(String id, Block block, Rarity rarity) {
		return item(id, new BlockItem(block, new Item.Settings().rarity(rarity)));
	}

	private static Item item(String id, Item item) {
		Registry.register(Registries.ITEM, KingdomOmnitrix.id(id), item);
		ITEMS.add(item);
		return item;
	}

	/** Alle Gegenstaende des Altars in Anzeigereihenfolge (fuer die Kreativ-Gruppe). */
	public static List<Item> items() {
		return Collections.unmodifiableList(ITEMS);
	}

	/** Stufe eines Injektor-Blocks (0 = kein Injektor). */
	public static int injectorTier(Block block) {
		return block instanceof FusionBlocks.Injector injector ? injector.tier() : 0;
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Fusionsaltar registriert: {} Gegenstaende", ITEMS.size());
	}
}
