package com.santiq.kingdomomnitrix.suit.module;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Rarity;

/** Registriert den Anzug-Konfigurator (Block, Block-Entity, Menue). */
public final class SuitConfigurator {
	public static final ConfiguratorBlock BLOCK = Registry.register(Registries.BLOCK, KingdomOmnitrix.id("suit_configurator"),
			new ConfiguratorBlock(AbstractBlock.Settings.create().mapColor(MapColor.CYAN).strength(5.0f, 1200.0f).requiresTool()
					.sounds(BlockSoundGroup.NETHERITE).luminance(state -> 10).nonOpaque()));
	public static final Item ITEM = Registry.register(Registries.ITEM, KingdomOmnitrix.id("suit_configurator"),
			new BlockItem(BLOCK, new Item.Settings().rarity(Rarity.EPIC)));
	public static final BlockEntityType<ConfiguratorBlockEntity> ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			KingdomOmnitrix.id("suit_configurator"), BlockEntityType.Builder.create(ConfiguratorBlockEntity::new, BLOCK).build());
	public static final ScreenHandlerType<ConfiguratorScreenHandler> SCREEN = Registry.register(Registries.SCREEN_HANDLER,
			KingdomOmnitrix.id("suit_configurator"), new ScreenHandlerType<>(ConfiguratorScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

	private SuitConfigurator() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Anzug-Konfigurator registriert: {}", Registries.BLOCK.getId(BLOCK));
	}
}
