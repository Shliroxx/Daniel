package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.keyblade.KeybladeForgeBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
	/** Ratchet-&-Clank-Kiste: zerfaellt beim ersten Schlag und gibt Bolts frei (siehe Loot-Tabelle). */
	public static final Block BOLT_CRATE = Registry.register(Registries.BLOCK, KingdomOmnitrix.id("bolt_crate"),
			new Block(AbstractBlock.Settings.create()
					.mapColor(MapColor.ORANGE)
					.strength(0.4f)
					.sounds(BlockSoundGroup.WOOD)));

	/** Keyblade-Schmiede: wertet Keyblades mit Bolts und Materialien auf. */
	public static final Block KEYBLADE_FORGE = Registry.register(Registries.BLOCK, KingdomOmnitrix.id("keyblade_forge"),
			new KeybladeForgeBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.IRON_GRAY)
					.strength(3.5f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.ANVIL)));

	private ModBlocks() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Bloecke registriert: {}", Registries.BLOCK.getId(BOLT_CRATE));
	}
}
