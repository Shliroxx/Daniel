package com.santiq.kingdomomnitrix.suit.fusion;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Rarity;

/** Registriert den Fusionsaltar (Kern und Injektor). */
public final class SuitFusion {
	public static final FusionBlocks.Core CORE = Registry.register(Registries.BLOCK, KingdomOmnitrix.id("fusion_core"),
			new FusionBlocks.Core(AbstractBlock.Settings.create().mapColor(MapColor.CYAN).strength(6.0f, 1200.0f).requiresTool()
					.sounds(BlockSoundGroup.NETHERITE).luminance(state -> 12).nonOpaque()));
	public static final FusionBlocks.Injector INJECTOR = Registry.register(Registries.BLOCK, KingdomOmnitrix.id("fusion_injector"),
			new FusionBlocks.Injector(AbstractBlock.Settings.create().mapColor(MapColor.CYAN).strength(5.0f, 1200.0f).requiresTool()
					.sounds(BlockSoundGroup.NETHERITE).luminance(state -> 8).nonOpaque()));
	public static final Item CORE_ITEM = Registry.register(Registries.ITEM, KingdomOmnitrix.id("fusion_core"),
			new BlockItem(CORE, new Item.Settings().rarity(Rarity.EPIC)));
	public static final Item INJECTOR_ITEM = Registry.register(Registries.ITEM, KingdomOmnitrix.id("fusion_injector"),
			new BlockItem(INJECTOR, new Item.Settings().rarity(Rarity.RARE)));
	public static final BlockEntityType<FusionCoreBlockEntity> CORE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			KingdomOmnitrix.id("fusion_core"), BlockEntityType.Builder.create(FusionCoreBlockEntity::new, CORE).build());
	public static final BlockEntityType<FusionInjectorBlockEntity> INJECTOR_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			KingdomOmnitrix.id("fusion_injector"), BlockEntityType.Builder.create(FusionInjectorBlockEntity::new, INJECTOR).build());

	private SuitFusion() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Fusionsaltar registriert: {}", Registries.BLOCK.getId(CORE));
	}
}
