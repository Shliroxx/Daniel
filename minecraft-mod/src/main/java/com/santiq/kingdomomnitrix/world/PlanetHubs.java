package com.santiq.kingdomomnitrix.world;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.registry.ModBlocks;
import com.santiq.kingdomomnitrix.world.content.PlanetBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Siedlungen rund um den Landeplatz der Ratchet-&-Clank-Welten. Gebaut einmal beim ersten Anflug
 * ({@link com.santiq.kingdomomnitrix.space.LandingPad}), aus Bloecken im Stil der jeweiligen Welt:
 * <ul>
 *   <li><b>Veldin</b>: Ratchets Garage mit Satellitenschuessel, Schrotthaufen, Windrad — Wueste und Tafelberge</li>
 *   <li><b>Kerwan</b>: Metropole — Glastuerme, Strassen mit Mittellinie, Gadgetron-Laden</li>
 *   <li><b>Novalis</b>: Farmen, Felder, Windmuehle und ein abgestuerztes Blarg-Schiff</li>
 *   <li><b>Rilgar</b>: Wasserstadt Blackwater — Plankenstege, Pfahlhaeuser, Startbogen fuer Hoverboard-Rennen</li>
 *   <li><b>Torren IV</b>: Raumhafen — weitere Landeplaetze, Kontrollturm, Hangars, Treibstofftanks</li>
 *   <li><b>Nefarious-Station</b>: schwebende Plattformen, Bruecken und der dunkle Zentralturm</li>
 * </ul>
 * Jede Siedlung hat ein Waffen-Terminal (Laden).
 */
public final class PlanetHubs {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private PlanetHubs() {
	}

	/**
	 * Bauplatz suchen: auf den Landwelten (ohne Ozeane, mit tiefen Becken) die naechste ebene Hochflaeche um (x, z),
	 * anhand der Gelaendehoehe des Generators — ohne Chunks zu erzeugen. Sonst (x, z) unveraendert.
	 */
	public static int[] site(ServerWorld world, int x, int z) {
		String planet = world.getRegistryKey().getValue().getPath();
		if (!planet.equals("veldin") && !planet.equals("kerwan") && !planet.equals("novalis") && !planet.equals("torren_iv")) {
			return new int[] {x, z};
		}
		for (int ring = 0; ring <= 32; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int[] d : new int[][] {{i, -ring}, {i, ring}, {-ring, i}, {ring, i}}) {
					int cx = x + d[0] * 64;
					int cz = z + d[1] * 64;
					if (flat(world, cx, cz)) {
						return new int[] {cx, cz};
					}
				}
			}
		}
		return new int[] {x, z};
	}

	private static boolean flat(ServerWorld world, int x, int z) {
		var generator = world.getChunkManager().getChunkGenerator();
		var noise = world.getChunkManager().getNoiseConfig();
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int[] d : new int[][] {{0, 0}, {24, 0}, {-24, 0}, {0, 24}, {0, -24}, {17, 17}, {-17, -17}, {17, -17}, {-17, 17}}) {
			int h = generator.getHeight(x + d[0], z + d[1], Heightmap.Type.WORLD_SURFACE_WG, world, noise);
			min = Math.min(min, h);
			max = Math.max(max, h);
		}
		return min >= 66 && max <= 110 && max - min <= 10;
	}

	/** Gelaende ebnen, bevor der Landeplatz entsteht; Rueckgabe: Hoehe der Bauflaeche (oberster fester Block). */
	public static int prepare(ServerWorld world, int x, int z, int ground) {
		String planet = world.getRegistryKey().getValue().getPath();
		return switch (planet) {
			case "veldin" -> level(world, x, z, ground, 30, PlanetBlocks.VELDIN_SAND.getDefaultState(), PlanetBlocks.VELDIN_ROCK.getDefaultState());
			case "kerwan" -> level(world, x, z, ground, 46, PlanetBlocks.PAD_PLATE.getDefaultState(), PlanetBlocks.KERWAN_SLATE.getDefaultState());
			case "novalis" -> level(world, x, z, ground, 32, PlanetBlocks.NOVALIS_MOSS.getDefaultState(), PlanetBlocks.NOVALIS_LOAM.getDefaultState());
			case "torren_iv" -> level(world, x, z, ground, 42, PlanetBlocks.TORREN_DUST.getDefaultState(), PlanetBlocks.TORREN_BASALT.getDefaultState());
			default -> ground;   // Rilgar: Stege ueber dem Wasser; Station: Leere
		};
	}

	/** Siedlung um den Landeplatz bauen (Mitte {@code base}, Hoehe der Bauflaeche). */
	public static void build(ServerWorld world, BlockPos base) {
		String planet = world.getRegistryKey().getValue().getPath();
		Random random = Random.create(world.getSeed() ^ planet.hashCode());
		Builder b = new Builder(world, base, footingFor(planet));
		switch (planet) {
			case "veldin" -> veldin(b, random);
			case "kerwan" -> kerwan(b, random);
			case "novalis" -> novalis(b, random);
			case "rilgar" -> rilgar(b, random);
			case "torren_iv" -> torren(b, random);
			case "nefarious_station" -> station(b, random);
			default -> {
				return;
			}
		}
		KingdomOmnitrix.LOGGER.info("Siedlung auf {} gebaut bei {}", planet, base);
	}

	/** Fundament am Hang: der Stein des Planeten (Rilgar steht auf Pfaehlen, die Station schwebt). */
	private static BlockState footingFor(String planet) {
		return switch (planet) {
			case "veldin" -> PlanetBlocks.VELDIN_ROCK.getDefaultState();
			case "kerwan" -> PlanetBlocks.KERWAN_SLATE.getDefaultState();
			case "novalis" -> PlanetBlocks.NOVALIS_LIMESTONE.getDefaultState();
			case "torren_iv" -> PlanetBlocks.TORREN_BASALT.getDefaultState();
			default -> null;
		};
	}

	// --- Gelaende -------------------------------------------------------------------------------

	private static int level(ServerWorld world, int cx, int cz, int ground, int radius, BlockState top, BlockState fill) {
		for (int cxk = (cx - radius) >> 4; cxk <= (cx + radius) >> 4; cxk++) {
			for (int czk = (cz - radius) >> 4; czk <= (cz + radius) >> 4; czk++) {
				world.getChunk(cxk, czk);
			}
		}
		int y0 = MathHelper.clamp(ground, world.getBottomY() + 16, world.getTopY() - 64);
		int blend = 10;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (distance > radius) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				int natural = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
				int target = y0 - 1;
				if (distance > radius - blend) {
					float t = (float) (distance - (radius - blend)) / blend;
					target = Math.round(MathHelper.lerp(t, y0 - 1, natural));
				}
				for (int y = Math.min(natural, target) - 2; y <= target; y++) {
					world.setBlockState(new BlockPos(x, y, z), y == target ? top : fill, FLAGS);
				}
				for (int y = target + 1; y <= Math.max(natural, target) + 30; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!world.getBlockState(pos).isAir()) {
						world.setBlockState(pos, Blocks.AIR.getDefaultState(), FLAGS);
					}
				}
			}
		}
		return y0 - 1;
	}

	// --- Veldin ---------------------------------------------------------------------------------

	private static void veldin(Builder b, Random random) {
		// Ratchets Garage: Sandstein mit orangem Band, Metalldach, grosses Tor zum Landeplatz
		int gx = 14;
		int gz = -6;
		b.box(gx, 1, gz, gx + 12, 7, gz + 11, PlanetBlocks.VELDIN_SANDSTONE.getDefaultState());
		b.box(gx + 1, 1, gz + 1, gx + 11, 6, gz + 10, Blocks.AIR.getDefaultState());
		b.box(gx, 3, gz, gx + 12, 3, gz + 11, PlanetBlocks.HULL_PLATE_ORANGE.getDefaultState());
		b.box(gx + 1, 3, gz + 1, gx + 11, 3, gz + 10, Blocks.AIR.getDefaultState());
		b.box(gx - 1, 8, gz - 1, gx + 13, 8, gz + 12, PlanetBlocks.HULL_PLATE.getDefaultState());
		b.box(gx, 0, gz, gx + 12, 0, gz + 11, PlanetBlocks.PAD_PLATE.getDefaultState());
		b.box(gx, 1, gz + 3, gx, 5, gz + 8, Blocks.AIR.getDefaultState());   // Tor
		b.box(gx, 6, gz + 3, gx, 6, gz + 8, PlanetBlocks.HAZARD_PLATE.getDefaultState());
		// Werkstatt
		b.set(gx + 10, 1, gz + 2, PlanetBlocks.HULL_PLATE_DARK.getDefaultState());
		b.set(gx + 10, 1, gz + 4, PlanetBlocks.HULL_PLATE_DARK.getDefaultState());
		b.set(gx + 10, 1, gz + 6, PlanetBlocks.HULL_PLATE_ORANGE.getDefaultState());
		b.set(gx + 10, 1, gz + 8, PlanetBlocks.HULL_PLATE_ORANGE.getDefaultState());
		b.set(gx + 6, 1, gz + 9, ModBlocks.WEAPON_TERMINAL.getDefaultState());
		b.set(gx + 3, 1, gz + 9, PlanetBlocks.HULL_PLATE_DARK.getDefaultState());
		for (int[] l : new int[][] {{3, 3}, {9, 3}, {3, 8}, {9, 8}}) {
			b.set(gx + l[0], 7, gz + l[1], PlanetBlocks.LIGHT_PANEL.getDefaultState());
		}
		// Satellitenschuessel auf dem Dach
		b.box(gx + 9, 9, gz + 8, gx + 9, 10, gz + 8, PlanetBlocks.GRATE.getDefaultState());
		b.box(gx + 7, 11, gz + 7, gx + 11, 11, gz + 9, PlanetBlocks.HULL_PLATE.getDefaultState());
		b.set(gx + 9, 12, gz + 8, PlanetBlocks.GRATE.getDefaultState());
		// Windrad
		b.box(-14, 1, 10, -14, 14, 10, PlanetBlocks.GRATE.getDefaultState());
		b.set(-14, 15, 10, PlanetBlocks.LIGHT_PANEL.getDefaultState());
		b.box(-17, 14, 10, -11, 14, 10, PlanetBlocks.GRATE.getDefaultState());
		b.box(-14, 11, 10, -14, 17, 10, PlanetBlocks.GRATE.getDefaultState());
		b.set(-14, 14, 10, PlanetBlocks.HULL_PLATE_DARK.getDefaultState());
		// Schrotthaufen (Blarg-Schrott)
		BlockState[] scrap = {PlanetBlocks.HULL_PLATE_DARK.getDefaultState(), PlanetBlocks.HULL_PLATE_DARK.getDefaultState(), PlanetBlocks.GRATE.getDefaultState(),
				PlanetBlocks.HULL_PLATE_DARK.getDefaultState(), PlanetBlocks.GRATE.getDefaultState(), PlanetBlocks.HAZARD_PLATE.getDefaultState()};
		for (int[] pile : new int[][] {{-12, -14}, {-18, 2}, {6, 18}, {22, 14}}) {
			for (int i = 0; i < 18; i++) {
				int dx = pile[0] + random.nextInt(5) - 2;
				int dz = pile[1] + random.nextInt(5) - 2;
				int dy = 1 + random.nextInt(3 - Math.min(2, (Math.abs(dx - pile[0]) + Math.abs(dz - pile[1])) / 2));
				b.set(dx, dy, dz, scrap[random.nextInt(scrap.length)]);
			}
		}
	}

	// --- Kerwan ---------------------------------------------------------------------------------

	private static void kerwan(Builder b, Random random) {
		// Strassenkreuz mit Mittellinie
		for (int d = -44; d <= 44; d++) {
			for (int w = -3; w <= 3; w++) {
				if (Math.abs(d) < 9) {
					continue;
				}
				BlockState road = w == 0 && (d & 3) < 2 ? PlanetBlocks.PAD_MARKING.getDefaultState() : PlanetBlocks.HULL_PLATE_DARK.getDefaultState();
				b.set(d, 0, w, road);
				b.set(w, 0, d, road);
			}
			if (Math.abs(d) >= 10 && d % 8 == 0) {
				b.streetLamp(d, 4, 6);
				b.streetLamp(4, d, 6);
			}
		}
		// Hochhaeuser in den vier Vierteln
		BlockState[] glass = {PlanetBlocks.TECH_GLASS.getDefaultState(), PlanetBlocks.TECH_GLASS.getDefaultState(),
				PlanetBlocks.TECH_GLASS.getDefaultState()};
		int[][] towers = {{14, 14, 7, 28}, {26, 14, 7, 40}, {14, 28, 9, 22}, {-16, 14, 9, 34}, {-30, 16, 7, 24}, {-16, -16, 7, 46},
				{-28, -28, 9, 30}, {16, -16, 9, 26}, {30, -28, 7, 36}};
		for (int[] t : towers) {
			int sign = t[0] < 0 ? -1 : 1;
			int signZ = t[1] < 0 ? -1 : 1;
			int x0 = t[0] - (sign < 0 ? t[2] - 1 : 0);
			int z0 = t[1] - (signZ < 0 ? t[2] - 1 : 0);
			b.tower(x0, z0, t[2], t[3], glass[random.nextInt(glass.length)]);
		}
		// Gadgetron-Laden
		int sx = -22;
		int sz = -10;
		b.box(sx, 0, sz, sx + 10, 6, sz + 8, PlanetBlocks.HULL_PLATE.getDefaultState());
		b.box(sx + 1, 1, sz + 1, sx + 9, 5, sz + 7, Blocks.AIR.getDefaultState());
		b.box(sx, 6, sz, sx + 10, 6, sz + 8, PlanetBlocks.HAZARD_PLATE.getDefaultState());
		b.box(sx, 5, sz + 8, sx + 10, 5, sz + 8, PlanetBlocks.HAZARD_PLATE.getDefaultState());
		b.box(sx + 2, 1, sz + 8, sx + 8, 3, sz + 8, PlanetBlocks.TECH_GLASS.getDefaultState());
		b.box(sx + 5, 1, sz + 8, sx + 5, 2, sz + 8, Blocks.AIR.getDefaultState());
		b.set(sx + 5, 1, sz + 2, ModBlocks.WEAPON_TERMINAL.getDefaultState());
		b.set(sx + 3, 4, sz + 4, PlanetBlocks.LIGHT_PANEL.getDefaultState());
		b.set(sx + 7, 4, sz + 4, PlanetBlocks.LIGHT_PANEL.getDefaultState());
	}

	// --- Novalis --------------------------------------------------------------------------------

	private static void novalis(Builder b, Random random) {
		b.farmhouse(12, -10, PlanetBlocks.NOVALIS_PLANKS.getDefaultState(), PlanetBlocks.HULL_PLATE_ORANGE.getDefaultState());
		b.farmhouse(-20, 8, PlanetBlocks.RILGAR_PLANKS.getDefaultState(), PlanetBlocks.RILGAR_PLANKS.getDefaultState());
		// Felder mit Zaun
		for (int dx = 10; dx <= 24; dx++) {
			for (int dz = 6; dz <= 18; dz++) {
				boolean edge = dx == 10 || dx == 24 || dz == 6 || dz == 18;
				if (edge) {
					b.set(dx, 1, dz, PlanetBlocks.NOVALIS_LOG.getDefaultState());
				} else if (dz % 4 == 0) {
					b.set(dx, 0, dz, PlanetBlocks.NOVALIS_LOAM.getDefaultState());
				} else {
					b.set(dx, 0, dz, PlanetBlocks.NOVALIS_LOAM.getDefaultState());
					b.set(dx, 1, dz, (dx % 2 == 0 ? PlanetBlocks.NOVALIS_BLOOM : PlanetBlocks.NOVALIS_BLOOM).getDefaultState());
				}
			}
		}
		b.set(17, 1, 6, Blocks.AIR.getDefaultState());
		// Windmuehle
		b.box(-14, 1, -16, -10, 10, -12, PlanetBlocks.NOVALIS_LOG.getDefaultState());
		b.box(-13, 1, -15, -11, 9, -13, Blocks.AIR.getDefaultState());
		b.box(-14, 11, -16, -10, 11, -12, PlanetBlocks.RILGAR_PLANKS.getDefaultState());
		b.box(-12, 12, -14, -12, 12, -14, PlanetBlocks.RILGAR_PLANKS.getDefaultState());
		b.box(-12, 4, -17, -12, 16, -17, PlanetBlocks.NOVALIS_PLANKS.getDefaultState());
		b.box(-18, 10, -17, -6, 10, -17, PlanetBlocks.NOVALIS_PLANKS.getDefaultState());
		b.set(-12, 10, -17, PlanetBlocks.NOVALIS_LOG.getDefaultState());
		b.set(-12, 1, -16, Blocks.AIR.getDefaultState());
		b.set(-12, 2, -16, Blocks.AIR.getDefaultState());
		// Abgestuerztes Blarg-Schiff mit Feuer
		int cx = -6;
		int cz = 22;
		for (int i = 0; i < 12; i++) {
			int h = Math.max(0, 3 - Math.abs(i - 5) / 2);
			b.box(cx + i, 0, cz - 2 + i / 4, cx + i, h, cz + 2 + i / 4, i % 3 == 0 ? PlanetBlocks.STATION_PLATE.getDefaultState()
					: PlanetBlocks.HULL_PLATE_DARK.getDefaultState());
		}
		b.box(cx + 12, 0, cz, cx + 15, 1, cz + 2, PlanetBlocks.HULL_PLATE_DARK.getDefaultState());
		b.set(cx + 4, 4, cz + 1, PlanetBlocks.PLANET_CORE.getDefaultState());
		b.set(cx + 9, 3, cz + 3, PlanetBlocks.PLANET_CORE.getDefaultState());
		b.set(cx - 1, 0, cz, PlanetBlocks.NOVALIS_LOAM.getDefaultState());
		b.set(cx - 2, 0, cz + 1, PlanetBlocks.NOVALIS_LOAM.getDefaultState());
		b.set(-6, 1, -2, ModBlocks.WEAPON_TERMINAL.getDefaultState());
		b.streetLamp(-6, -4, 4);
	}

	// --- Rilgar ---------------------------------------------------------------------------------

	private static void rilgar(Builder b, Random random) {
		BlockState plank = PlanetBlocks.RILGAR_PLANKS.getDefaultState();
		BlockState stone = PlanetBlocks.RILGAR_CORALSTONE.getDefaultState();
		// Ring-Steg und vier Stege nach aussen, auf Pfaehlen
		for (int dx = -34; dx <= 34; dx++) {
			for (int dz = -34; dz <= 34; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				boolean ring = r >= 10 && r <= 13;
				boolean arm = (Math.abs(dx) <= 2 || Math.abs(dz) <= 2) && r > 8 && r <= 34;
				if (!ring && !arm) {
					continue;
				}
				b.set(dx, 0, dz, (dx + dz) % 5 == 0 ? stone : plank);
				if ((dx % 4 == 0 && dz % 4 == 0)) {
					b.pillar(dx, -1, dz, PlanetBlocks.RILGAR_LOG.getDefaultState(), 12);
				}
				if (ring && (r > 12.5) && (dx + dz) % 2 == 0) {
					b.set(dx, 1, dz, PlanetBlocks.GRATE.getDefaultState());
				}
			}
		}
		// Pfahlhaeuser an den Stegen
		int[][] houses = {{22, -6}, {-27, 3}, {4, 22}, {-6, -27}};
		for (int[] h : houses) {
			b.box(h[0], 0, h[1], h[0] + 6, 0, h[1] + 6, plank);
			b.box(h[0], 1, h[1], h[0] + 6, 4, h[1] + 6, PlanetBlocks.HULL_PLATE.getDefaultState());
			b.box(h[0] + 1, 1, h[1] + 1, h[0] + 5, 3, h[1] + 5, Blocks.AIR.getDefaultState());
			b.box(h[0] - 1, 5, h[1] - 1, h[0] + 7, 5, h[1] + 7, PlanetBlocks.HULL_PLATE_DARK.getDefaultState());
			b.box(h[0] + 1, 6, h[1] + 1, h[0] + 5, 6, h[1] + 5, PlanetBlocks.HULL_PLATE_DARK.getDefaultState());
			b.set(h[0] + 3, 1, h[1], Blocks.AIR.getDefaultState());
			b.set(h[0] + 3, 2, h[1], Blocks.AIR.getDefaultState());
			b.set(h[0] + 6, 2, h[1] + 3, PlanetBlocks.TECH_GLASS.getDefaultState());
			b.set(h[0], 2, h[1] + 3, PlanetBlocks.TECH_GLASS.getDefaultState());
			b.set(h[0] + 3, 4, h[1] + 3, PlanetBlocks.LIGHT_PANEL.getDefaultState());
		}
		// Startbogen der Hoverboard-Strecke
		b.box(30, 1, -3, 30, 7, -3, PlanetBlocks.HULL_PLATE.getDefaultState());
		b.box(30, 1, 3, 30, 7, 3, PlanetBlocks.HULL_PLATE.getDefaultState());
		for (int dz = -3; dz <= 3; dz++) {
			b.set(30, 8, dz, ((dz & 1) == 0 ? PlanetBlocks.HULL_PLATE_DARK : PlanetBlocks.HULL_PLATE).getDefaultState());
		}
		b.set(-12, 1, 0, ModBlocks.WEAPON_TERMINAL.getDefaultState());
		for (int[] l : new int[][] {{11, 0}, {-11, 2}, {0, 11}, {0, -11}}) {
			b.streetLamp(l[0], l[1], 4);
		}
	}

	// --- Torren IV ------------------------------------------------------------------------------

	private static void torren(Builder b, Random random) {
		// weitere Landeplaetze
		for (int[] p : new int[][] {{24, 0}, {-24, 0}, {0, 26}}) {
			b.disc(p[0], 0, p[1], 6, PlanetBlocks.PAD_PLATE.getDefaultState());
			b.ring(p[0], 0, p[1], 6, PlanetBlocks.PAD_MARKING.getDefaultState());
		}
		// Kontrollturm
		b.cylinder(14, 1, -20, 4, 20, PlanetBlocks.HULL_PLATE.getDefaultState());
		b.cylinder(14, 2, -20, 3, 18, Blocks.AIR.getDefaultState());
		b.cylinder(14, 21, -20, 6, 1, PlanetBlocks.HULL_PLATE.getDefaultState());
		b.ring(14, 22, -20, 6, PlanetBlocks.TECH_GLASS.getDefaultState());
		b.ring(14, 23, -20, 6, PlanetBlocks.TECH_GLASS.getDefaultState());
		b.cylinder(14, 24, -20, 6, 1, PlanetBlocks.HULL_PLATE.getDefaultState());
		b.set(14, 25, -20, PlanetBlocks.LIGHT_PANEL.getDefaultState());
		b.set(14, 26, -20, PlanetBlocks.GRATE.getDefaultState());
		b.box(14, 1, -16, 14, 2, -16, Blocks.AIR.getDefaultState());
		// Hangars (Bogenhallen)
		for (int[] h : new int[][] {{-30, -22}, {-30, 14}}) {
			for (int dz = 0; dz < 14; dz++) {
				for (int a = 0; a <= 32; a++) {
					double angle = Math.PI * a / 32.0;
					int x = (int) Math.round(Math.cos(angle) * 7);
					int y = (int) Math.round(Math.sin(angle) * 7);
					b.set(h[0] + x, y, h[1] + dz, dz == 0 || dz == 13 ? PlanetBlocks.HULL_PLATE_DARK.getDefaultState() : PlanetBlocks.HULL_PLATE.getDefaultState());
				}
			}
			b.box(h[0] - 4, 1, h[1] + 13, h[0] + 4, 5, h[1] + 13, Blocks.AIR.getDefaultState());
		}
		// Treibstofftanks und Frachtkisten
		for (int[] t : new int[][] {{30, -20}, {36, -14}, {30, 18}}) {
			b.cylinder(t[0], 1, t[1], 3, 6, PlanetBlocks.HULL_PLATE.getDefaultState());
			b.ring(t[0], 4, t[1], 3, PlanetBlocks.HULL_PLATE_ORANGE.getDefaultState());
		}
		for (int i = 0; i < 14; i++) {
			int x = -10 + random.nextInt(20);
			int z = 14 + random.nextInt(6);
			b.set(x, 1, z, (i % 2 == 0 ? PlanetBlocks.HULL_PLATE_ORANGE : PlanetBlocks.HULL_PLATE_ORANGE).getDefaultState());
		}
		b.set(10, 1, -14, ModBlocks.WEAPON_TERMINAL.getDefaultState());
		for (int[] l : new int[][] {{12, 12}, {-12, 12}, {12, -12}, {-12, -12}}) {
			b.streetLamp(l[0], l[1], 6);
		}
	}

	// --- Nefarious-Station ----------------------------------------------------------------------

	private static void station(Builder b, Random random) {
		BlockState deck = PlanetBlocks.STATION_PLATE.getDefaultState();
		BlockState trim = PlanetBlocks.STATION_TRIM.getDefaultState();
		// Hauptdeck unter dem Landeplatz und vier Aussenplattformen mit Bruecken
		b.disc(0, -1, 0, 10, deck);
		int[][] outer = {{30, 0}, {-30, 0}, {0, 30}, {0, -30}};
		for (int[] o : outer) {
			b.disc(o[0], 0, o[1], 8, deck);
			b.ring(o[0], 0, o[1], 8, trim);
			b.ring(o[0], 1, o[1], 8, PlanetBlocks.GRATE.getDefaultState());
			int steps = Math.max(Math.abs(o[0]), Math.abs(o[1]));
			for (int s = 8; s <= steps - 8; s++) {
				int x = Integer.signum(o[0]) * s;
				int z = Integer.signum(o[1]) * s;
				for (int w = -1; w <= 1; w++) {
					b.set(x + (o[0] == 0 ? w : 0), 0, z + (o[1] == 0 ? w : 0), w == 0 ? trim : deck);
				}
				b.set(x + (o[0] == 0 ? 2 : 0), 1, z + (o[1] == 0 ? 2 : 0), PlanetBlocks.GRATE.getDefaultState());
				b.set(x + (o[0] == 0 ? -2 : 0), 1, z + (o[1] == 0 ? -2 : 0), PlanetBlocks.GRATE.getDefaultState());
			}
			b.set(o[0], 1, o[1], PlanetBlocks.LIGHT_PANEL.getDefaultState());
		}
		// Zentralturm auf der Nordplattform: dunkel, violette Fenster, gruene Kuppel (wie Nefarious' Kopf)
		int tx = 0;
		int tz = -30;
		b.cylinder(tx, 1, tz, 5, 26, PlanetBlocks.HULL_PLATE_DARK.getDefaultState());
		b.cylinder(tx, 2, tz, 4, 24, Blocks.AIR.getDefaultState());
		for (int y = 4; y < 26; y += 5) {
			b.ring(tx, y, tz, 5, PlanetBlocks.TECH_GLASS.getDefaultState());
		}
		b.cylinder(tx, 27, tz, 6, 1, trim);
		for (int y = 0; y < 4; y++) {
			b.cylinder(tx, 28 + y, tz, 4 - y, 1, PlanetBlocks.TECH_GLASS.getDefaultState());
		}
		b.set(tx, 1, tz + 5, Blocks.AIR.getDefaultState());
		b.set(tx, 2, tz + 5, Blocks.AIR.getDefaultState());
		b.set(tx, 1, tz, PlanetBlocks.STATION_TRIM.getDefaultState());
		// Laden auf der Ostplattform
		b.set(30, 1, 3, ModBlocks.WEAPON_TERMINAL.getDefaultState());
	}

	// --- Bauhelfer ------------------------------------------------------------------------------

	/**
	 * Setzt Bloecke relativ zur Mitte der Bauflaeche (y = 0 ist die Flaeche selbst). Mit {@code footings} bekommen
	 * Bodenbloecke am Rand der eingeebneten Flaeche (Hang) ein Fundament bis zum Grund.
	 */
	private record Builder(ServerWorld world, BlockPos base, BlockState footing) {
		void set(int dx, int dy, int dz, BlockState state) {
			BlockPos pos = base.add(dx, dy, dz);
			world.setBlockState(pos, state, FLAGS);
			if (footing != null && dy <= 1 && state.isFullCube(world, pos)) {
				footing(pos.down());
			}
		}

		private void footing(BlockPos from) {
			BlockPos.Mutable pos = from.mutableCopy();
			for (int i = 0; i < 24 && !world.getBlockState(pos).isSolidBlock(world, pos); i++) {
				world.setBlockState(pos, footing, FLAGS);
				pos.move(0, -1, 0);
			}
		}

		void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
						set(x, y, z, state);
					}
				}
			}
		}

		void disc(int cx, int y, int cz, int r, BlockState state) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz <= r * r + r) {
						set(cx + dx, y, cz + dz, state);
					}
				}
			}
		}

		void ring(int cx, int y, int cz, int r, BlockState state) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					int d = dx * dx + dz * dz;
					if (d <= r * r + r && d > (r - 1) * (r - 1) + (r - 1)) {
						set(cx + dx, y, cz + dz, state);
					}
				}
			}
		}

		void cylinder(int cx, int y0, int cz, int r, int height, BlockState state) {
			for (int y = y0; y < y0 + height; y++) {
				disc(cx, y, cz, r, state);
			}
		}

		/** Pfahl nach unten bis zum festen Grund (hoechstens {@code max}). */
		void pillar(int dx, int dy, int dz, BlockState state, int max) {
			for (int i = 0; i < max; i++) {
				BlockPos pos = base.add(dx, dy - i, dz);
				if (world.getBlockState(pos).isSolidBlock(world, pos)) {
					return;
				}
				world.setBlockState(pos, state, FLAGS);
			}
		}

		void streetLamp(int dx, int dz, int height) {
			box(dx, 1, dz, dx, height, dz, PlanetBlocks.GRATE.getDefaultState());
			set(dx, height + 1, dz, PlanetBlocks.LIGHT_PANEL.getDefaultState());
		}

		void tower(int x0, int z0, int size, int height, BlockState glass) {
			for (int y = 1; y <= height; y++) {
				boolean floor = y % 4 == 0;
				for (int dx = 0; dx < size; dx++) {
					for (int dz = 0; dz < size; dz++) {
						boolean edge = dx == 0 || dz == 0 || dx == size - 1 || dz == size - 1;
						boolean corner = (dx == 0 || dx == size - 1) && (dz == 0 || dz == size - 1);
						if (!edge && !floor) {
							continue;
						}
						BlockState state = corner || floor ? PlanetBlocks.HULL_PLATE.getDefaultState() : glass;
						set(x0 + dx, y, z0 + dz, state);
					}
				}
			}
			box(x0, height + 1, z0, x0 + size - 1, height + 1, z0 + size - 1, PlanetBlocks.HULL_PLATE.getDefaultState());
			set(x0 + size / 2, height + 2, z0 + size / 2, PlanetBlocks.LIGHT_PANEL.getDefaultState());
			set(x0 + size / 2, height + 3, z0 + size / 2, PlanetBlocks.GRATE.getDefaultState());
			set(x0 + size / 2, 1, z0, Blocks.AIR.getDefaultState());
			set(x0 + size / 2, 2, z0, Blocks.AIR.getDefaultState());
		}

		void farmhouse(int x0, int z0, BlockState wall, BlockState roof) {
			box(x0, 1, z0, x0 + 8, 4, z0 + 6, wall);
			box(x0 + 1, 1, z0 + 1, x0 + 7, 3, z0 + 5, Blocks.AIR.getDefaultState());
			box(x0, 0, z0, x0 + 8, 0, z0 + 6, PlanetBlocks.HULL_PLATE_DARK.getDefaultState());
			for (int i = 0; i <= 3; i++) {
				box(x0 - 1, 5 + i, z0 - 1 + i, x0 + 9, 5 + i, z0 + 7 - i, roof);
			}
			set(x0 + 4, 1, z0 + 6, Blocks.AIR.getDefaultState());
			set(x0 + 4, 2, z0 + 6, Blocks.AIR.getDefaultState());
			set(x0 + 2, 2, z0, PlanetBlocks.TECH_GLASS.getDefaultState());
			set(x0 + 6, 2, z0, PlanetBlocks.TECH_GLASS.getDefaultState());
			set(x0 + 4, 3, z0 + 3, PlanetBlocks.LIGHT_PANEL.getDefaultState());
		}
	}
}
