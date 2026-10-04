// Erzeugt von tools/generate_planet_content.py — nicht von Hand aendern.

package com.santiq.kingdomomnitrix.world.content;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.List;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.intprovider.UniformIntProvider;

/** Bloecke der Ratchet-&-Clank-Welten (Natur, Erze, Technik). */
public final class PlanetBlocks {
	/** Veldin-Duenensand */
	public static final Block VELDIN_SAND = register("veldin_sand", new Block(base(MapColor.ORANGE, 0.5f, 0.5f, BlockSoundGroup.SAND, 0, false)));
	/** Schichtsandstein */
	public static final Block VELDIN_SANDSTONE = register("veldin_sandstone", new Block(base(MapColor.TERRACOTTA_ORANGE, 1.2f, 4.0f, BlockSoundGroup.STONE, 0, true)));
	/** Veldin-Fels */
	public static final Block VELDIN_ROCK = register("veldin_rock", new Block(base(MapColor.TERRACOTTA_RED, 1.6f, 6.0f, BlockSoundGroup.STONE, 0, true)));
	/** Duerrstrauch */
	public static final Block VELDIN_SCRUB = register("veldin_scrub", new AlienPlantBlock(AbstractBlock.Settings.create().mapColor(MapColor.ORANGE).noCollision().breakInstantly().sounds(BlockSoundGroup.GRASS).luminance(state -> 0).offset(AbstractBlock.OffsetType.XZ).replaceable().pistonBehavior(PistonBehavior.DESTROY)));
	/** Kerwan-Rasen */
	public static final Block KERWAN_TURF = register("kerwan_turf", new Block(base(MapColor.CYAN, 0.7f, 0.7f, BlockSoundGroup.GRASS, 0, false)));
	/** Kerwan-Erde */
	public static final Block KERWAN_SOIL = register("kerwan_soil", new Block(base(MapColor.PURPLE, 0.6f, 0.6f, BlockSoundGroup.GRAVEL, 0, false)));
	/** Kerwan-Schiefer */
	public static final Block KERWAN_SLATE = register("kerwan_slate", new Block(base(MapColor.BLUE, 1.6f, 6.0f, BlockSoundGroup.DEEPSLATE, 0, true)));
	/** Spiralbaum-Stamm */
	public static final Block KERWAN_LOG = register("kerwan_log", new PillarBlock(base(MapColor.PURPLE, 2.0f, 2.0f, BlockSoundGroup.WOOD, 0, false).instrument(NoteBlockInstrument.BASS).burnable()));
	/** Spiralbaum-Bretter */
	public static final Block KERWAN_PLANKS = register("kerwan_planks", new Block(base(MapColor.PURPLE, 2.0f, 3.0f, BlockSoundGroup.WOOD, 0, false)));
	/** Spiralbaum-Laub */
	public static final Block KERWAN_LEAVES = register("kerwan_leaves", new LeavesBlock(AbstractBlock.Settings.copy(Blocks.OAK_LEAVES).mapColor(MapColor.CYAN).sounds(BlockSoundGroup.GRASS)));
	/** Lichtschilf */
	public static final Block KERWAN_REED = register("kerwan_reed", new AlienPlantBlock(AbstractBlock.Settings.create().mapColor(MapColor.CYAN).noCollision().breakInstantly().sounds(BlockSoundGroup.GRASS).luminance(state -> 6).offset(AbstractBlock.OffsetType.XZ).replaceable().pistonBehavior(PistonBehavior.DESTROY)));
	/** Novalis-Moos */
	public static final Block NOVALIS_MOSS = register("novalis_moss", new Block(base(MapColor.GREEN, 0.7f, 0.7f, BlockSoundGroup.MOSS_BLOCK, 0, false)));
	/** Novalis-Lehm */
	public static final Block NOVALIS_LOAM = register("novalis_loam", new Block(base(MapColor.BROWN, 0.6f, 0.6f, BlockSoundGroup.ROOTED_DIRT, 0, false)));
	/** Novalis-Kalkstein */
	public static final Block NOVALIS_LIMESTONE = register("novalis_limestone", new Block(base(MapColor.PALE_YELLOW, 1.4f, 6.0f, BlockSoundGroup.STONE, 0, true)));
	/** Glockenbaum-Stamm */
	public static final Block NOVALIS_LOG = register("novalis_log", new PillarBlock(base(MapColor.BROWN, 2.0f, 2.0f, BlockSoundGroup.WOOD, 0, false).instrument(NoteBlockInstrument.BASS).burnable()));
	/** Glockenbaum-Bretter */
	public static final Block NOVALIS_PLANKS = register("novalis_planks", new Block(base(MapColor.BROWN, 2.0f, 3.0f, BlockSoundGroup.WOOD, 0, false)));
	/** Glockenbaum-Laub */
	public static final Block NOVALIS_LEAVES = register("novalis_leaves", new LeavesBlock(AbstractBlock.Settings.copy(Blocks.OAK_LEAVES).mapColor(MapColor.PINK).sounds(BlockSoundGroup.CHERRY_LEAVES)));
	/** Sternbluete */
	public static final Block NOVALIS_BLOOM = register("novalis_bloom", new AlienPlantBlock(AbstractBlock.Settings.create().mapColor(MapColor.PINK).noCollision().breakInstantly().sounds(BlockSoundGroup.GRASS).luminance(state -> 0).offset(AbstractBlock.OffsetType.XZ).replaceable().pistonBehavior(PistonBehavior.DESTROY)));
	/** Riffsand */
	public static final Block RILGAR_SAND = register("rilgar_sand", new Block(base(MapColor.WHITE, 0.5f, 0.5f, BlockSoundGroup.SAND, 0, false)));
	/** Korallenfels */
	public static final Block RILGAR_CORALSTONE = register("rilgar_coralstone", new Block(base(MapColor.CYAN, 1.4f, 6.0f, BlockSoundGroup.STONE, 0, true)));
	/** Mangroven-Stamm */
	public static final Block RILGAR_LOG = register("rilgar_log", new PillarBlock(base(MapColor.BROWN, 2.0f, 2.0f, BlockSoundGroup.WOOD, 0, false).instrument(NoteBlockInstrument.BASS).burnable()));
	/** Pfahlholz */
	public static final Block RILGAR_PLANKS = register("rilgar_planks", new Block(base(MapColor.BROWN, 2.0f, 3.0f, BlockSoundGroup.WOOD, 0, false)));
	/** Mangroven-Laub */
	public static final Block RILGAR_LEAVES = register("rilgar_leaves", new LeavesBlock(AbstractBlock.Settings.copy(Blocks.OAK_LEAVES).mapColor(MapColor.GREEN).sounds(BlockSoundGroup.GRASS)));
	/** Riffgras */
	public static final Block RILGAR_SEAGRASS = register("rilgar_seagrass", new AlienPlantBlock(AbstractBlock.Settings.create().mapColor(MapColor.CYAN).noCollision().breakInstantly().sounds(BlockSoundGroup.GRASS).luminance(state -> 0).offset(AbstractBlock.OffsetType.XZ).replaceable().pistonBehavior(PistonBehavior.DESTROY)));
	/** Rotstaub */
	public static final Block TORREN_DUST = register("torren_dust", new Block(base(MapColor.RED, 0.5f, 0.5f, BlockSoundGroup.SAND, 0, false)));
	/** Brandkruste */
	public static final Block TORREN_CRUST = register("torren_crust", new Block(base(MapColor.TERRACOTTA_RED, 1.0f, 3.0f, BlockSoundGroup.TUFF, 0, true)));
	/** Saeulenbasalt */
	public static final Block TORREN_BASALT = register("torren_basalt", new Block(base(MapColor.BLACK, 1.8f, 6.0f, BlockSoundGroup.BASALT, 2, true)));
	/** Glutdorn */
	public static final Block TORREN_THORN = register("torren_thorn", new AlienPlantBlock(AbstractBlock.Settings.create().mapColor(MapColor.RED).noCollision().breakInstantly().sounds(BlockSoundGroup.GRASS).luminance(state -> 4).offset(AbstractBlock.OffsetType.XZ).replaceable().pistonBehavior(PistonBehavior.DESTROY)));
	/** Rumpfplatte */
	public static final Block HULL_PLATE = register("hull_plate", new Block(base(MapColor.IRON_GRAY, 3.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));
	/** Dunkle Rumpfplatte */
	public static final Block HULL_PLATE_DARK = register("hull_plate_dark", new Block(base(MapColor.GRAY, 3.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));
	/** Gadgetron-Platte */
	public static final Block HULL_PLATE_ORANGE = register("hull_plate_orange", new Block(base(MapColor.ORANGE, 3.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));
	/** Warnplatte */
	public static final Block HAZARD_PLATE = register("hazard_plate", new Block(base(MapColor.YELLOW, 3.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));
	/** Panzerglas */
	public static final Block TECH_GLASS = register("tech_glass", new TechGlassBlock(AbstractBlock.Settings.copy(Blocks.GLASS).mapColor(MapColor.LIGHT_BLUE).strength(1.2f, 8.0f)));
	/** Lichtpaneel */
	public static final Block LIGHT_PANEL = register("light_panel", new Block(base(MapColor.WHITE, 1.5f, 4.0f, BlockSoundGroup.GLASS, 15, false)));
	/** Gitterrost */
	public static final Block GRATE = register("grate", new Block(base(MapColor.IRON_GRAY, 2.5f, 6.0f, BlockSoundGroup.METAL, 0, true).nonOpaque()));
	/** Stationsplatte */
	public static final Block STATION_PLATE = register("station_plate", new Block(base(MapColor.PURPLE, 4.0f, 9.0f, BlockSoundGroup.NETHERITE, 0, true)));
	/** Stationsleiste */
	public static final Block STATION_TRIM = register("station_trim", new Block(base(MapColor.PURPLE, 4.0f, 9.0f, BlockSoundGroup.NETHERITE, 10, true)));
	/** Landefeld */
	public static final Block PAD_PLATE = register("pad_plate", new Block(base(MapColor.LIGHT_GRAY, 3.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));
	/** Landefeld-Markierung */
	public static final Block PAD_MARKING = register("pad_marking", new Block(base(MapColor.YELLOW, 3.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));
	/** Planetenkern */
	public static final Block PLANET_CORE = register("planet_core", new Block(AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(-1.0f, 3600000.0f).dropsNothing().allowsSpawning((state, world, pos, type) -> false).luminance(state -> 3).sounds(BlockSoundGroup.STONE)));
	/** Sonnenkupfer-Erz */
	public static final Block SOLAR_COPPER_ORE = register("solar_copper_ore", new ExperienceDroppingBlock(UniformIntProvider.create(2, 6), base(MapColor.STONE_GRAY, 3.0f, 3.0f, BlockSoundGroup.STONE, 0, true).instrument(NoteBlockInstrument.BASEDRUM)));
	/** Sonnenkupfer-Block */
	public static final Block SOLAR_COPPER_BLOCK = register("solar_copper_block", new Block(base(MapColor.ORANGE, 5.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));
	/** Azurit-Erz */
	public static final Block AZURITE_ORE = register("azurite_ore", new ExperienceDroppingBlock(UniformIntProvider.create(2, 6), base(MapColor.STONE_GRAY, 3.0f, 3.0f, BlockSoundGroup.STONE, 0, true).instrument(NoteBlockInstrument.BASEDRUM)));
	/** Azurit-Block */
	public static final Block AZURITE_BLOCK = register("azurite_block", new Block(base(MapColor.ORANGE, 5.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));
	/** Viridium-Erz */
	public static final Block VIRIDIUM_ORE = register("viridium_ore", new ExperienceDroppingBlock(UniformIntProvider.create(2, 6), base(MapColor.STONE_GRAY, 3.0f, 3.0f, BlockSoundGroup.STONE, 0, true).instrument(NoteBlockInstrument.BASEDRUM)));
	/** Viridium-Block */
	public static final Block VIRIDIUM_BLOCK = register("viridium_block", new Block(base(MapColor.ORANGE, 5.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));
	/** Aquarin-Erz */
	public static final Block AQUARINE_ORE = register("aquarine_ore", new ExperienceDroppingBlock(UniformIntProvider.create(2, 6), base(MapColor.STONE_GRAY, 3.0f, 3.0f, BlockSoundGroup.STONE, 0, true).instrument(NoteBlockInstrument.BASEDRUM)));
	/** Aquarin-Block */
	public static final Block AQUARINE_BLOCK = register("aquarine_block", new Block(base(MapColor.ORANGE, 5.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));
	/** Pyronit-Erz */
	public static final Block PYRONITE_ORE = register("pyronite_ore", new ExperienceDroppingBlock(UniformIntProvider.create(2, 6), base(MapColor.STONE_GRAY, 3.0f, 3.0f, BlockSoundGroup.STONE, 5, true).instrument(NoteBlockInstrument.BASEDRUM)));
	/** Pyronit-Block */
	public static final Block PYRONITE_BLOCK = register("pyronite_block", new Block(base(MapColor.ORANGE, 5.0f, 6.0f, BlockSoundGroup.METAL, 0, true)));

	/** Render-Ebenen fuer den Client (Pflanzen und Gitter mit Loechern, Laub, Glas). */
	public static final List<Block> CUTOUT = List.of(VELDIN_SCRUB, KERWAN_REED, NOVALIS_BLOOM, RILGAR_SEAGRASS, TORREN_THORN, GRATE);
	public static final List<Block> CUTOUT_MIPPED = List.of(KERWAN_LEAVES, NOVALIS_LEAVES, RILGAR_LEAVES);
	public static final List<Block> TRANSLUCENT = List.of(TECH_GLASS);

	private PlanetBlocks() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Planeten-Bloecke registriert: {}", Registries.BLOCK.getId(HULL_PLATE));
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
