package com.daniel.heroverse.registry;

import com.daniel.heroverse.Heroverse;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
	/** Ratchet-&-Clank-Kiste: zerfaellt beim ersten Schlag und gibt Bolts frei (siehe Loot-Tabelle). */
	public static final Block BOLT_CRATE = Registry.register(Registries.BLOCK, Heroverse.id("bolt_crate"),
			new Block(AbstractBlock.Settings.create()
					.mapColor(MapColor.ORANGE)
					.strength(0.4f)
					.sounds(BlockSoundGroup.WOOD)));

	private ModBlocks() {
	}

	public static void register() {
		Heroverse.LOGGER.debug("Bloecke registriert: {}", Registries.BLOCK.getId(BOLT_CRATE));
	}
}
