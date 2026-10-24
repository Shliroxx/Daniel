package com.santiq.kingdomomnitrix.suit.forge;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
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

/** Registriert die drei Ruestungs-Maschinen (Bloecke, Block-Entity, Menues). */
public final class SuitForge {
	public static final Map<MachineKind, MachineBlock> MACHINES = new EnumMap<>(MachineKind.class);
	private static final Map<MachineKind, ScreenHandlerType<MachineScreenHandler>> SCREENS = new EnumMap<>(MachineKind.class);

	static {
		for (MachineKind kind : MachineKind.values()) {
			MachineBlock block = Registry.register(Registries.BLOCK, KingdomOmnitrix.id(kind.id()),
					new MachineBlock(kind, AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(4.0f, 8.0f).requiresTool()
							.sounds(BlockSoundGroup.NETHERITE).luminance(state -> state.get(MachineBlock.LIT) ? 11 : 2)));
			Registry.register(Registries.ITEM, KingdomOmnitrix.id(kind.id()), new BlockItem(block, new Item.Settings().rarity(Rarity.RARE)));
			MACHINES.put(kind, block);
			SCREENS.put(kind, Registry.register(Registries.SCREEN_HANDLER, KingdomOmnitrix.id(kind.id()),
					new ScreenHandlerType<>((syncId, inventory) -> new MachineScreenHandler(kind, syncId, inventory),
							FeatureFlags.VANILLA_FEATURES)));
		}
	}

	public static final BlockEntityType<MachineBlockEntity> MACHINE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			KingdomOmnitrix.id("suit_machine"), BlockEntityType.Builder.create(MachineBlockEntity::new,
					MACHINES.values().toArray(new Block[0])).build());

	private SuitForge() {
	}

	public static ScreenHandlerType<MachineScreenHandler> screenType(MachineKind kind) {
		return SCREENS.get(kind);
	}

	public static void register() {
		ForgeMaterials.register();
		KingdomOmnitrix.LOGGER.debug("Ruestungs-Maschinen registriert: {}", MACHINES.size());
	}
}
