package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.keyblade.KeybladeForgeBlock;
import com.santiq.kingdomomnitrix.weapon.WeaponTerminalBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.util.math.intprovider.UniformIntProvider;
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

	/** Waffen-Terminal: Waffen kaufen, aufwerten, Munition auffuellen (Bolt-Konto). */
	public static final Block WEAPON_TERMINAL = Registry.register(Registries.BLOCK, KingdomOmnitrix.id("weapon_terminal"),
			new WeaponTerminalBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.LIGHT_BLUE)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.luminance(state -> 7)
					.sounds(BlockSoundGroup.METAL)));

	// --- Erze (Phase 12): Asteroiden im All und die Mod-Welten ----------------------------------

	/** Raritanium (Ratchet & Clank): in Asteroiden und tief in Traverse Town. */
	public static final Block RARITANIUM_ORE = ore("raritanium_ore", MapColor.STONE_GRAY, 3.0f, BlockSoundGroup.STONE, 3, 7, 5);
	/** Mythril (Kingdom Hearts): haeufig in Traverse Town. */
	public static final Block MYTHRIL_ORE = ore("mythril_ore", MapColor.STONE_GRAY, 3.0f, BlockSoundGroup.STONE, 2, 5, 3);
	public static final Block DEEPSLATE_MYTHRIL_ORE = ore("deepslate_mythril_ore", MapColor.DEEPSLATE_GRAY, 4.5f, BlockSoundGroup.DEEPSLATE, 2, 5, 3);
	/** Orichalcum (Kingdom Hearts): sehr selten, tief im Gestein. */
	public static final Block ORICHALCUM_ORE = ore("orichalcum_ore", MapColor.DEEPSLATE_GRAY, 5.0f, BlockSoundGroup.DEEPSLATE, 5, 10, 9);

	private static Block ore(String name, MapColor color, float hardness, BlockSoundGroup sound, int minXp, int maxXp, int light) {
		return Registry.register(Registries.BLOCK, KingdomOmnitrix.id(name),
				new ExperienceDroppingBlock(UniformIntProvider.create(minXp, maxXp), AbstractBlock.Settings.create()
						.mapColor(color)
						.instrument(NoteBlockInstrument.BASEDRUM)
						.strength(hardness, 3.0f)
						.requiresTool()
						.luminance(state -> light)
						.sounds(sound)));
	}

	private ModBlocks() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Bloecke registriert: {}", Registries.BLOCK.getId(BOLT_CRATE));
	}
}
