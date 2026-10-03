package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.arena.ArenaTerminalBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Block-Entities (Bloecke mit gespeichertem Zustand). */
public final class ModBlockEntities {
	public static final BlockEntityType<ArenaTerminalBlockEntity> ARENA_TERMINAL = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			KingdomOmnitrix.id("arena_terminal"),
			BlockEntityType.Builder.create(ArenaTerminalBlockEntity::new, ModBlocks.ARENA_TERMINAL).build());

	private ModBlockEntities() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Block-Entities registriert: {}", Registries.BLOCK_ENTITY_TYPE.getId(ARENA_TERMINAL));
	}
}
