// Erzeugt von tools/generate_ben10_content.py — nicht von Hand aendern.

package com.santiq.kingdomomnitrix.world.content;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.intprovider.UniformIntProvider;

/** Bloecke der Ben-10-Story (Corrodium, Omnitrix-Werkbank, Klempner-Technik). */
public final class Ben10Blocks {
	/** Corrodium-Erz */
	public static final Block CORRODIUM_ORE = register("corrodium_ore", new ExperienceDroppingBlock(UniformIntProvider.create(3, 7), base(MapColor.LIME, 4.0f, 9.0f, BlockSoundGroup.AMETHYST_BLOCK, 7, true).instrument(NoteBlockInstrument.BASEDRUM)));
	/** Corrodium-Block */
	public static final Block CORRODIUM_BLOCK = register("corrodium_block", new Block(base(MapColor.LIME, 5.0f, 9.0f, BlockSoundGroup.AMETHYST_BLOCK, 9, true)));
	/** Omnitrix-Werkbank */
	public static final Block OMNITRIX_WORKBENCH = register("omnitrix_workbench", new com.santiq.kingdomomnitrix.omnitrix.workbench.OmnitrixWorkbenchBlock(base(MapColor.LIME, 4.0f, 9.0f, BlockSoundGroup.METAL, 6, true).nonOpaque()));
	/** Omnitrix-Stabilisator */
	public static final Block OMNITRIX_STABILIZER = register("omnitrix_stabilizer", new Block(base(MapColor.LIME, 4.0f, 9.0f, BlockSoundGroup.METAL, 8, true)));
	/** Klempner-Rumpfplatte */
	public static final Block PLUMBER_HULL = register("plumber_hull", new Block(base(MapColor.LIGHT_GRAY, 5.0f, 12.0f, BlockSoundGroup.METAL, 0, true)));
	/** Dunkle Klempner-Rumpfplatte */
	public static final Block PLUMBER_HULL_DARK = register("plumber_hull_dark", new Block(base(MapColor.BLACK, 5.0f, 12.0f, BlockSoundGroup.METAL, 3, true)));
	/** Ausgebrannte Rumpfplatte */
	public static final Block PLUMBER_HULL_SCORCHED = register("plumber_hull_scorched", new Block(base(MapColor.BLACK, 4.0f, 10.0f, BlockSoundGroup.METAL, 0, true)));
	/** Klempner-Deckplatte */
	public static final Block PLUMBER_FLOOR = register("plumber_floor", new Block(base(MapColor.IRON_GRAY, 4.0f, 10.0f, BlockSoundGroup.METAL, 0, true)));
	/** Klempner-Leuchtpaneel */
	public static final Block PLUMBER_LIGHT = register("plumber_light", new Block(base(MapColor.LIME, 1.5f, 4.0f, BlockSoundGroup.GLASS, 15, false)));
	/** Klempner-Bordkonsole */
	public static final Block PLUMBER_CONSOLE = register("plumber_console", new Block(base(MapColor.BLACK, 3.0f, 8.0f, BlockSoundGroup.METAL, 7, true)));
	/** Containment-Kapsel (Hülle) */
	public static final Block CONTAINMENT_POD_SHELL = register("containment_pod_shell", new com.santiq.kingdomomnitrix.world.plumber.ContainmentPodBlock(AbstractBlock.Settings.create().mapColor(MapColor.LIME).strength(-1.0f, 3600000.0f).dropsNothing().luminance(state -> 10).sounds(BlockSoundGroup.METAL), com.santiq.kingdomomnitrix.world.plumber.ContainmentPodBlock.Part.SHELL));
	/** Containment-Kapsel (Konsole) */
	public static final Block CONTAINMENT_POD_CONSOLE = register("containment_pod_console", new com.santiq.kingdomomnitrix.world.plumber.ContainmentPodBlock(AbstractBlock.Settings.create().mapColor(MapColor.LIME).strength(-1.0f, 3600000.0f).dropsNothing().luminance(state -> 10).sounds(BlockSoundGroup.METAL), com.santiq.kingdomomnitrix.world.plumber.ContainmentPodBlock.Part.CONSOLE));

	private Ben10Blocks() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Ben-10-Bloecke registriert: {}", Registries.BLOCK.getId(CORRODIUM_ORE));
	}

	private static Block register(String name, Block block) {
		return Registry.register(Registries.BLOCK, KingdomOmnitrix.id(name), block);
	}

	private static AbstractBlock.Settings base(MapColor color, float hardness, float resistance, BlockSoundGroup sound, int light,
			boolean tool) {
		AbstractBlock.Settings settings = AbstractBlock.Settings.create().mapColor(color).strength(hardness, resistance).sounds(sound)
				.luminance(state -> light);
		return tool ? settings.requiresTool() : settings;
	}
}
