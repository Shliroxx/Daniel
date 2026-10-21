package com.santiq.kingdomomnitrix.world.plumber;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.world.content.Ben10Blocks;
import com.santiq.kingdomomnitrix.world.content.PlanetBlocks;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Baut das Wrack des Klempner-Kreuzers PROCTOR Block fuer Block (kein Strukturformat). 212 Bloecke lang, beim Absturz in
 * zwei Teile zerbrochen:
 *
 * <ul>
 *   <li><b>Bugteil</b> (Schiffslaenge 0–118): mit der Nase schraeg im Boden, Einschlagkrater. Cockpit (Konsolen,
 *       Logbuch 1), Mannschaftsquartiere, Waffenkammer, Containment-Labor mit der Kapsel der <b>Huelle</b>.</li>
 *   <li><b>Heckteil</b> (132–212, zehn Bloecke weiter hinten und seitlich versetzt): Frachtraum, Maschinenraum mit
 *       Reaktorsaeulen, Logbuch 2 und der Kapsel der <b>Konsole</b>; zwei Triebwerksgondeln und Rueckenfinne.</li>
 *   <li>Aussen: Schleifspur hinter dem Heck, Truemmer, Brandstellen und Rauchsaeulen (Lagerfeuer an den Bruchkanten).</li>
 * </ul>
 *
 * Koordinaten: u entlang des Schiffs (0 = Nasenspitze, nach hinten), v quer (rechts positiv), w senkrecht zur
 * Mittellinie. Querschnitt ist eine Superellipse (Exponent 2,5: kantiger als ein Rohr), die Huelle ist einen Block
 * stark; innen liegt das Deck auf w = {@value #FLOOR}.
 */
final class PlumberShipBuilder {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
	static final int LENGTH = 212;
	private static final int FLOOR = -4;
	private static final int BREAK_A = 118;
	private static final int BREAK_B = 132;
	/** Heckteil liegt so viel weiter hinten bzw. seitlich als im heilen Schiff */
	private static final int B_SHIFT = 10;
	private static final int B_LATERAL = 3;
	private static final double EXP = 2.5;

	/** Ergebnis: wo die Story-Punkte liegen. */
	record Site(BlockPos nose, BlockPos shellPod, BlockPos consolePod) {
	}

	/** Ein Wrackteil: Bereich in u, Hoehe der Mittellinie ueber dem Boden je u, Versatz. */
	private record Section(int u0, int u1, int shift, int lateral, DoubleUnaryOperator center) {
	}

	private final ServerWorld world;
	private final BlockPos nose;
	private final Direction forward;
	private final Direction right;
	private final Random random;
	private final int ground;
	private final Section front;
	private final Section rear;

	private PlumberShipBuilder(ServerWorld world, BlockPos nose, Direction forward, Random random) {
		this.world = world;
		this.nose = nose;
		this.forward = forward;
		this.right = forward.rotateYClockwise();
		this.random = random;
		this.ground = nose.getY();
		// Bug: Nase ~7 Bloecke im Boden, steigt bis zur Bruchkante auf Bodenhoehe; Heck liegt fast flach auf
		this.front = new Section(0, BREAK_A, 0, 0, u -> 2.0 + u * 0.07);
		this.rear = new Section(BREAK_B, LENGTH, B_SHIFT, B_LATERAL, u -> 9.0 + (u - BREAK_B) * 0.015);
	}

	static Site build(ServerWorld world, BlockPos nose, Direction forward, Random random) {
		long started = System.currentTimeMillis();
		PlumberShipBuilder b = new PlumberShipBuilder(world, nose, forward, random);
		b.loadChunks();
		b.crater();
		b.trench();
		b.hull(b.front);
		b.hull(b.rear);
		b.wings();
		b.engines();
		b.support(b.front);
		b.support(b.rear);
		BlockPos shellPod = b.frontRooms();
		BlockPos consolePod = b.rearRooms();
		b.debris();
		b.fires();
		KingdomOmnitrix.LOGGER.info("Klempner-Wrack bei {} gebaut ({} ms)", nose, System.currentTimeMillis() - started);
		return new Site(nose, shellPod, consolePod);
	}

	// --- Form -------------------------------------------------------------------------------------------------------

	/** halbe Breite des Rumpfs an Stelle u */
	private static double halfWidth(double u) {
		if (u < 40) {
			return 3.0 + 8.0 * Math.sqrt(u / 40.0);
		}
		if (u > 185) {
			return 11.0 - 4.0 * (u - 185) / 27.0;
		}
		return 11.0;
	}

	/** halbe Hoehe des Rumpfs an Stelle u */
	private static double halfHeight(double u) {
		if (u < 40) {
			return 2.5 + 6.5 * Math.sqrt(u / 40.0);
		}
		if (u > 185) {
			return 9.0 - 3.0 * (u - 185) / 27.0;
		}
		return 9.0;
	}

	private static double shape(double v, double w, double hw, double hh) {
		return Math.pow(Math.abs(v / hw), EXP) + Math.pow(Math.abs(w / hh), EXP);
	}

	/** Weltposition eines Schiffspunkts in einem Wrackteil. */
	private BlockPos at(Section s, int u, int v, int w) {
		int y = ground + (int) Math.round(s.center().applyAsDouble(u)) + w;
		return nose.offset(forward, -(u + s.shift())).offset(right, v + s.lateral()).withY(y);
	}

	private void set(BlockPos pos, BlockState state) {
		world.setBlockState(pos, state, FLAGS);
	}

	private static BlockState of(Block block) {
		return block.getDefaultState();
	}

	/** feste Zufallszahl 0..1 je Ort (gleiches Wrack bei gleichem Ort) */
	private static double hash(int a, int b, int c) {
		int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		h ^= h >>> 15;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	/** grobes, glattes Rauschen (Wertrauschen auf 6er-Gitter) fuer Loecher und Brandflecken */
	private static double noise(int u, int v, int w, int salt) {
		double fu = u / 6.0;
		double fv = v / 6.0;
		double fw = w / 6.0;
		int iu = MathHelper.floor(fu);
		int iv = MathHelper.floor(fv);
		int iw = MathHelper.floor(fw);
		double tu = fu - iu;
		double tv = fv - iv;
		double tw = fw - iw;
		double sum = 0;
		for (int du = 0; du <= 1; du++) {
			for (int dv = 0; dv <= 1; dv++) {
				for (int dw = 0; dw <= 1; dw++) {
					double weight = (du == 0 ? 1 - tu : tu) * (dv == 0 ? 1 - tv : tv) * (dw == 0 ? 1 - tw : tw);
					sum += weight * hash(iu + du + salt * 31, iv + dv, iw + dw + salt);
				}
			}
		}
		return sum;
	}

	/** wie stark ein Punkt beschaedigt ist: nahe der Bruchkanten und an der Nase hoch */
	private double damage(int u) {
		double nearBreak = Math.max(0, 1 - Math.min(Math.abs(u - BREAK_A), Math.abs(u - BREAK_B)) / 14.0);
		double nearNose = Math.max(0, 1 - u / 22.0);
		return Math.max(nearBreak, nearNose * 0.8);
	}

	// --- Rumpf ------------------------------------------------------------------------------------------------------

	private void hull(Section s) {
		for (int u = s.u0(); u <= s.u1(); u++) {
			double hw = halfWidth(u);
			double hh = halfHeight(u);
			int edge = Math.min(u - s.u0(), s.u1() - u);
			boolean breakEdge = (s == front && u > BREAK_A - 6) || (s == rear && u < BREAK_B + 6);
			for (int v = -(int) Math.ceil(hw); v <= Math.ceil(hw); v++) {
				for (int w = -(int) Math.ceil(hh); w <= Math.ceil(hh); w++) {
					if (shape(v, w, hw, hh) > 1.0) {
						continue;
					}
					boolean inside = shape(v, w, hw - 1.3, hh - 1.3) <= 1.0 && edge > 0;
					// gezackte Bruchkante: je naeher am Bruch, desto mehr fehlt
					if (breakEdge && hash(u, v, w) < (6 - Math.min(edge, 6)) / 6.0 * 0.75) {
						continue;
					}
					BlockPos pos = at(s, u, v, w);
					if (inside) {
						set(pos, interior(u, v, w));
						continue;
					}
					double dmg = damage(u);
					if (noise(u, v, w, 1) > 0.74 - dmg * 0.25 && w > FLOOR + 1) {
						set(pos, of(Blocks.AIR));
						continue;
					}
					set(pos, shell(u, v, w, hw, hh, dmg));
				}
			}
			bulkhead(s, u, hw, hh);
		}
	}

	private BlockState shell(int u, int v, int w, double hw, double hh, double dmg) {
		if (noise(u, v, w, 2) + dmg * 0.6 > 0.82) {
			return of(Ben10Blocks.PLUMBER_HULL_SCORCHED);
		}
		// Cockpitscheiben oben am Bug
		if (u >= 10 && u <= 30 && w >= 1 && Math.abs(v) < hw - 1.5) {
			return of(PlanetBlocks.TECH_GLASS);
		}
		// Deckenlichter in der Mitte, Wandlichter auf Gangshoehe
		if (v == 0 && w > 0 && u % 6 == 0) {
			return of(Ben10Blocks.PLUMBER_LIGHT);
		}
		if (w == FLOOR + 3 && u % 8 == 4 && Math.abs(v) > hw - 2.5) {
			return of(Ben10Blocks.PLUMBER_LIGHT);
		}
		// Klempner-Farben: dunkles Leuchtband auf Hoehe der Mittellinie, dunkler Kiel
		if (Math.abs(w) <= 1 || w < -hh + 2.2) {
			return of(Ben10Blocks.PLUMBER_HULL_DARK);
		}
		return of(Ben10Blocks.PLUMBER_HULL);
	}

	private static BlockState interior(int u, int v, int w) {
		if (w < FLOOR) {
			return of(Ben10Blocks.PLUMBER_HULL_DARK);
		}
		if (w == FLOOR) {
			return of(Ben10Blocks.PLUMBER_FLOOR);
		}
		return of(Blocks.AIR);
	}

	/** Schotten zwischen den Raeumen: Wand mit Tuer (3 breit, 3 hoch), Tuerrahmen beleuchtet. */
	private void bulkhead(Section s, int u, double hw, double hh) {
		if (!isBulkhead(u)) {
			return;
		}
		for (int v = -(int) hw; v <= hw; v++) {
			for (int w = FLOOR + 1; w <= hh; w++) {
				if (shape(v, w, hw - 1.3, hh - 1.3) > 1.0) {
					continue;
				}
				boolean door = Math.abs(v) <= 1 && w <= FLOOR + 3;
				boolean frame = !door && Math.abs(v) <= 2 && w <= FLOOR + 4;
				set(at(s, u, v, w), door ? of(Blocks.AIR) : frame ? of(Ben10Blocks.PLUMBER_LIGHT) : of(Ben10Blocks.PLUMBER_HULL_DARK));
			}
		}
	}

	private static boolean isBulkhead(int u) {
		return u == 34 || u == 58 || u == 82 || u == 170;
	}

	/** Stummelfluegel am Bug (rechts abgerissen) */
	private void wings() {
		for (int u = 66; u <= 100; u++) {
			double hw = halfWidth(u);
			int spanLeft = (int) (14 - Math.abs(u - 83) * 0.35);
			int spanRight = (int) (6 - Math.abs(u - 83) * 0.2);
			for (int side = -1; side <= 1; side += 2) {
				int span = side < 0 ? spanLeft : spanRight;
				for (int i = 0; i < span; i++) {
					int v = side * ((int) hw + i);
					BlockState state = i > span - 3 || hash(u, v, 7) < 0.15 ? of(Ben10Blocks.PLUMBER_HULL_SCORCHED)
							: i % 5 == 4 ? of(Ben10Blocks.PLUMBER_HULL_DARK) : of(Ben10Blocks.PLUMBER_HULL);
					set(at(front, u, v, -3), state);
				}
			}
		}
	}

	/** Triebwerksgondeln links/rechts am Heck und Rueckenfinne */
	private void engines() {
		for (int u = 186; u <= LENGTH; u++) {
			for (int side = -1; side <= 1; side += 2) {
				int cv = side * 15;
				for (int dv = -4; dv <= 4; dv++) {
					for (int dw = -4; dw <= 4; dw++) {
						double r = Math.sqrt(dv * dv + dw * dw);
						if (r > 3.6) {
							continue;
						}
						boolean end = u == LENGTH;
						BlockState state = end ? (r < 2.6 ? of(Ben10Blocks.PLUMBER_LIGHT) : of(Ben10Blocks.PLUMBER_HULL_DARK))
								: r > 2.6 ? (u % 7 == 0 ? of(Ben10Blocks.PLUMBER_HULL_DARK) : of(Ben10Blocks.PLUMBER_HULL))
								: of(Ben10Blocks.PLUMBER_HULL_DARK);
						set(at(rear, u, cv + dv, -2 + dw), state);
					}
				}
				// Pylon zum Rumpf
				if (u >= 194 && u <= 206) {
					for (int v = 9; v <= 12; v++) {
						set(at(rear, u, side * v, -2), of(Ben10Blocks.PLUMBER_HULL_DARK));
					}
				}
			}
			// Rueckenfinne
			if (u >= 176 && u < LENGTH) {
				int top = (int) halfHeight(u) + (int) ((u - 176) * 0.28);
				for (int w = (int) halfHeight(u) - 1; w <= top; w++) {
					set(at(rear, u, 0, w), w == top ? of(Ben10Blocks.PLUMBER_LIGHT) : of(Ben10Blocks.PLUMBER_HULL_DARK));
				}
			}
		}
	}

	/** Erde unter dem Kiel auffuellen, damit kein Teil ueber Senken schwebt (hoechstens 12 Bloecke). */
	private void support(Section s) {
		for (int u = s.u0(); u <= s.u1(); u += 1) {
			double hw = halfWidth(u);
			int keel = -(int) Math.floor(halfHeight(u)) + 1;
			for (int v = -(int) (hw * 0.7); v <= hw * 0.7; v++) {
				BlockPos pos = at(s, u, v, keel).down();
				for (int i = 0; i < 12 && world.getBlockState(pos).isReplaceable(); i++) {
					set(pos, i == 0 ? of(Blocks.COARSE_DIRT) : of(Blocks.DIRT));
					pos = pos.down();
				}
			}
		}
	}

	// --- Umgebung ---------------------------------------------------------------------------------------------------

	private void loadChunks() {
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int u : new int[]{-20, LENGTH + B_SHIFT + 75}) {
			for (int v : new int[]{-35, 35}) {
				BlockPos p = nose.offset(forward, -u).offset(right, v);
				minX = Math.min(minX, p.getX());
				maxX = Math.max(maxX, p.getX());
				minZ = Math.min(minZ, p.getZ());
				maxZ = Math.max(maxZ, p.getZ());
			}
		}
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				world.getChunk(cx, cz);
			}
		}
	}

	/** Einschlagkrater um die Nase: Halbkugel ausheben, Grund verbrannt */
	private void crater() {
		int r = 11;
		BlockPos center = nose.offset(forward, 4).up(2);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -6; dy <= r; dy++) {
					double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 2.2);
					BlockPos pos = center.add(dx, dy, dz);
					if (d <= r) {
						set(pos, of(Blocks.AIR));
					} else if (d <= r + 1.2 && dy < 0 && !world.getBlockState(pos).isAir()) {
						double h = hash(pos.getX(), pos.getY(), pos.getZ());
						set(pos, h < 0.2 ? of(Blocks.MAGMA_BLOCK) : h < 0.55 ? of(Blocks.COARSE_DIRT) : h < 0.75 ? of(Blocks.BLACKSTONE)
								: of(Blocks.GRAVEL));
					}
				}
			}
		}
	}

	/** Schleifspur hinter dem Heck: 60 Bloecke lange Furche aus verbrannter Erde */
	private void trench() {
		for (int u = LENGTH + B_SHIFT - 4; u <= LENGTH + B_SHIFT + 60; u++) {
			int half = 4 - (u - LENGTH - B_SHIFT) / 20;
			for (int v = -half; v <= half; v++) {
				BlockPos column = nose.offset(forward, -u).offset(right, v + B_LATERAL);
				BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column).down();
				int depth = Math.abs(v) <= half - 1 ? 2 : 1;
				for (int i = 0; i < depth; i++) {
					set(top.down(i), of(Blocks.AIR));
				}
				BlockPos floor = top.down(depth);
				double h = hash(floor.getX(), floor.getY(), floor.getZ());
				set(floor, h < 0.08 ? of(Blocks.MAGMA_BLOCK) : h < 0.6 ? of(Blocks.COARSE_DIRT) : of(Blocks.DIRT_PATH));
				if (h > 0.97) {
					set(floor.up(), of(Blocks.FIRE));
				}
				// aufgeworfene Raender
				if (Math.abs(v) == half) {
					set(top.offset(right, Integer.signum(v)).up(), of(Blocks.COARSE_DIRT));
				}
			}
		}
	}

	/** verstreute Rumpfteile um die Bruchstelle und entlang der Schleifspur */
	private void debris() {
		for (int i = 0; i < 70; i++) {
			int u = 100 + random.nextInt(LENGTH + B_SHIFT + 50 - 100);
			int v = (random.nextBoolean() ? 1 : -1) * (14 + random.nextInt(18));
			BlockPos column = nose.offset(forward, -u).offset(right, v);
			BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
			if (!world.getFluidState(top.down()).isEmpty()) {
				continue;
			}
			int size = 1 + random.nextInt(3);
			for (int j = 0; j < size; j++) {
				BlockPos p = top.offset(random.nextBoolean() ? forward : right, j - 1).up(j == 1 && size == 3 ? 1 : 0);
				double h = random.nextDouble();
				set(p, h < 0.6 ? of(Ben10Blocks.PLUMBER_HULL_SCORCHED) : h < 0.85 ? of(Ben10Blocks.PLUMBER_HULL) : of(Ben10Blocks.PLUMBER_HULL_DARK));
			}
			if (random.nextFloat() < 0.25) {
				set(top.up(size == 3 ? 2 : 1), of(Blocks.FIRE));
			}
		}
	}

	/** Feuer auf verbrannten Oberflaechen nahe der Bruchstellen, Rauchsaeulen aus den offenen Enden */
	private void fires() {
		for (Section s : new Section[]{front, rear}) {
			int from = s == front ? BREAK_A - 16 : BREAK_B;
			int to = s == front ? BREAK_A : BREAK_B + 16;
			for (int i = 0; i < 40; i++) {
				int u = from + random.nextInt(to - from + 1);
				int v = random.nextInt(19) - 9;
				BlockPos top = at(s, u, v, (int) halfHeight(u));
				for (int k = 0; k < 4 && !world.getBlockState(top).isAir(); k++) {
					top = top.up();
				}
				if (world.getBlockState(top.down()).isOf(Ben10Blocks.PLUMBER_HULL_SCORCHED) && world.getBlockState(top).isAir()) {
					set(top, of(Blocks.FIRE));
				}
			}
		}
		// Lagerfeuer (Signalrauch) im Deck an den Bruchenden und in der Nase
		set(at(front, BREAK_A - 3, -3, FLOOR), of(Blocks.CAMPFIRE).with(CampfireBlock.SIGNAL_FIRE, true));
		set(at(front, BREAK_A - 3, -3, FLOOR - 1), of(Blocks.HAY_BLOCK));
		set(at(rear, BREAK_B + 3, 4, FLOOR), of(Blocks.CAMPFIRE).with(CampfireBlock.SIGNAL_FIRE, true));
		set(at(rear, BREAK_B + 3, 4, FLOOR - 1), of(Blocks.HAY_BLOCK));
		BlockPos crater = nose.offset(forward, 6).down();
		while (crater.getY() > world.getBottomY() && world.getBlockState(crater).isAir()) {
			crater = crater.down();
		}
		set(crater.up(), of(Blocks.CAMPFIRE).with(CampfireBlock.SIGNAL_FIRE, true));
	}

	// --- Raeume -----------------------------------------------------------------------------------------------------

	/** Bugteil: Cockpit, Quartiere, Waffenkammer, Labor; liefert die Kapsel der Huelle. */
	private BlockPos frontRooms() {
		Section s = front;
		// Cockpit: Konsolenreihe vor den Scheiben, Kapitaenspult mit Logbuch
		for (int v = -3; v <= 3; v++) {
			set(at(s, 20, v, FLOOR + 1), of(Ben10Blocks.PLUMBER_CONSOLE));
		}
		set(at(s, 27, -4, FLOOR + 1), of(Ben10Blocks.PLUMBER_CONSOLE));
		set(at(s, 27, 4, FLOOR + 1), of(Ben10Blocks.PLUMBER_CONSOLE));
		lectern(at(s, 25, 0, FLOOR + 1), PlumberLogs.captain());
		// Quartiere: Kojen (Truhen + Wolle) an den Waenden
		for (int u = 38; u <= 54; u += 8) {
			for (int side = -1; side <= 1; side += 2) {
				set(at(s, u, side * 8, FLOOR + 1), of(Blocks.GRAY_WOOL));
				set(at(s, u + 1, side * 8, FLOOR + 1), of(Blocks.GRAY_WOOL));
				set(at(s, u, side * 8, FLOOR + 3), of(Ben10Blocks.PLUMBER_HULL_DARK));
				set(at(s, u + 1, side * 8, FLOOR + 3), of(Ben10Blocks.PLUMBER_HULL_DARK));
			}
		}
		chest(at(s, 42, 8, FLOOR + 1), side(-1), "quarters");
		chest(at(s, 50, -8, FLOOR + 1), side(1), "quarters");
		// Waffenkammer: Gitterraeume links/rechts mit Truhen
		for (int u = 62; u <= 78; u++) {
			for (int side = -1; side <= 1; side += 2) {
				if (u % 4 != 0) {
					set(at(s, u, side * 6, FLOOR + 1), of(Blocks.IRON_BARS));
					set(at(s, u, side * 6, FLOOR + 2), of(Blocks.IRON_BARS));
				}
			}
		}
		chest(at(s, 66, 8, FLOOR + 1), side(-1), "armory");
		chest(at(s, 74, -8, FLOOR + 1), side(1), "armory");
		set(at(s, 70, 8, FLOOR + 1), of(Blocks.TNT));
		// Labor: Kapsel auf Lichtsockel, Konsolen an den Waenden, Labortruhe
		BlockPos pod = at(s, 98, 0, FLOOR + 1);
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				set(at(s, 98 + du, dv, FLOOR), of(Ben10Blocks.PLUMBER_LIGHT));
			}
		}
		set(pod, of(Ben10Blocks.CONTAINMENT_POD_SHELL));
		set(pod.up(), of(PlanetBlocks.TECH_GLASS));
		for (int u = 88; u <= 108; u += 4) {
			set(at(s, u, 8, FLOOR + 1), of(Ben10Blocks.PLUMBER_CONSOLE));
			set(at(s, u, -8, FLOOR + 1), of(Ben10Blocks.PLUMBER_CONSOLE));
		}
		chest(at(s, 106, 6, FLOOR + 1), side(-1), "lab");
		return pod;
	}

	/** Heckteil: Frachtraum und Maschinenraum; liefert die Kapsel der Konsole. */
	private BlockPos rearRooms() {
		Section s = rear;
		// Frachtraum: Kistenstapel (Rumpfplatten, Faesser) und Truhen
		for (int i = 0; i < 14; i++) {
			int u = 138 + random.nextInt(28);
			int v = (random.nextBoolean() ? 1 : -1) * (4 + random.nextInt(5));
			int height = 1 + random.nextInt(3);
			for (int h = 0; h < height; h++) {
				set(at(s, u, v, FLOOR + 1 + h), random.nextFloat() < 0.5 ? of(Blocks.BARREL) : of(Ben10Blocks.PLUMBER_HULL));
			}
		}
		chest(at(s, 142, 8, FLOOR + 1), side(-1), "cargo");
		chest(at(s, 150, -8, FLOOR + 1), side(1), "cargo");
		chest(at(s, 162, 8, FLOOR + 1), side(-1), "cargo");
		// Maschinenraum: Reaktorsaeulen, Konsolen, Logbuch 2, Kapsel der Konsole
		for (int u : new int[]{178, 198}) {
			for (int side = -1; side <= 1; side += 2) {
				for (int w = FLOOR + 1; w <= FLOOR + 6; w++) {
					set(at(s, u, side * 5, w), w % 2 == 0 ? of(Ben10Blocks.PLUMBER_LIGHT) : of(Ben10Blocks.CORRODIUM_BLOCK));
				}
			}
		}
		BlockPos pod = at(s, 188, 0, FLOOR + 1);
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				set(at(s, 188 + du, dv, FLOOR), of(Ben10Blocks.PLUMBER_LIGHT));
			}
		}
		set(pod, of(Ben10Blocks.CONTAINMENT_POD_CONSOLE));
		set(pod.up(), of(PlanetBlocks.TECH_GLASS));
		for (int u = 182; u <= 194; u += 4) {
			set(at(s, u, 8, FLOOR + 1), of(Ben10Blocks.PLUMBER_CONSOLE));
		}
		lectern(at(s, 174, 3, FLOOR + 1), PlumberLogs.engineer());
		chest(at(s, 196, -7, FLOOR + 1), side(1), "engineering");
		return pod;
	}

	/** Blickrichtung fuer Truhen an der Wand: zur Schiffsmitte */
	private Direction side(int towardsRight) {
		return towardsRight > 0 ? right : right.getOpposite();
	}

	private void chest(BlockPos pos, Direction facing, String area) {
		set(pos, of(Blocks.CHEST).with(ChestBlock.FACING, facing));
		if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity chest) {
			RegistryKey<LootTable> table = RegistryKey.of(RegistryKeys.LOOT_TABLE, KingdomOmnitrix.id("chests/plumber_ship_" + area));
			chest.setLootTable(table, random.nextLong());
		}
	}

	private void lectern(BlockPos pos, ItemStack book) {
		BlockState state = of(Blocks.LECTERN).with(LecternBlock.FACING, forward);
		set(pos, state);
		LecternBlock.putBookIfAbsent(null, world, pos, world.getBlockState(pos), book);
	}
}
