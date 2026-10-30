package com.santiq.kingdomomnitrix.suit.fusion;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Bauplan des Fusionsaltars in vier Stufen (nach dem Vorbild der Blutaltar-Stufen aus Blood Magic und der
 * Infusions-Stabilisatoren aus Thaumcraft). Jede Stufe setzt die vorige voraus; Koordinaten relativ zum Kern.
 *
 * <ol>
 *   <li><b>Grund-Altar</b>: Kern und Injektoren.</li>
 *   <li><b>Galvan-Altar</b>: 8 Fusionsrunen im Ring unter dem Kern (Abstand 2), 4 Resonanzsaeulen an den Ecken
 *       (Abstand 3, zwei Saeulenbloecke, Resonanzkristall obenauf).</li>
 *   <li><b>Sternen-Altar</b>: 8 weitere Runen im Ring (Abstand 4), 4 Sternensaeulen auf den Achsen (Abstand 6, drei
 *       Saeulenbloecke, Sternenkrone obenauf).</li>
 *   <li><b>Chaos-Altar</b>: der Chaos-Fokus schwebt fuenf Bloecke ueber dem Kern.</li>
 * </ol>
 *
 * Neben der Stufe liefert der Altar die Stabilitaet (gegen die Instabilitaet der Rezepte) und die Kuehlung (gegen
 * die Hitze des Kerns).
 */
public final class FusionAltar {
	public static final int MAX_TIER = 4;
	/** Stabilitaet je Altarstufe */
	private static final int[] TIER_STABILITY = {0, 0, 14, 26, 40};

	/** Ein Bauteil: Block an Position (relativ zum Kern). */
	public record Part(BlockPos offset, Block block) {
	}

	private static final List<List<Part>> TIERS = List.of(List.of(), List.of(), tier2(), tier3(), tier4());

	private FusionAltar() {
	}

	private static List<Part> tier2() {
		List<Part> parts = new ArrayList<>();
		ring(parts, 2);
		for (int sx : new int[] {-3, 3}) {
			for (int sz : new int[] {-3, 3}) {
				parts.add(new Part(new BlockPos(sx, 0, sz), SuitFusion.PYLON));
				parts.add(new Part(new BlockPos(sx, 1, sz), SuitFusion.PYLON));
				parts.add(new Part(new BlockPos(sx, 2, sz), SuitFusion.RESONANCE_CAPSTONE));
			}
		}
		return parts;
	}

	private static List<Part> tier3() {
		List<Part> parts = new ArrayList<>();
		ring(parts, 4);
		for (int[] d : new int[][] {{6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
			for (int y = 0; y < 3; y++) {
				parts.add(new Part(new BlockPos(d[0], y, d[1]), SuitFusion.PYLON));
			}
			parts.add(new Part(new BlockPos(d[0], 3, d[1]), SuitFusion.CELESTIAL_CAPSTONE));
		}
		return parts;
	}

	private static List<Part> tier4() {
		return List.of(new Part(new BlockPos(0, 5, 0), SuitFusion.CHAOS_FOCUS));
	}

	/** Acht Runen unter Kernhoehe: Ecken und Achsen eines Quadrats mit Abstand r. */
	private static void ring(List<Part> parts, int r) {
		for (int[] d : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {r, -r}, {-r, r}, {-r, -r}}) {
			parts.add(new Part(new BlockPos(d[0], -1, d[1]), SuitFusion.RUNE));
		}
	}

	/** Bauteile genau dieser Stufe (ohne die der Vorstufen). */
	public static List<Part> parts(int tier) {
		return tier >= 0 && tier < TIERS.size() ? TIERS.get(tier) : List.of();
	}

	/** Hoechste vollstaendige Stufe des Altars um den Kern. */
	public static int tier(World world, BlockPos core) {
		int tier = 1;
		for (int t = 2; t <= MAX_TIER; t++) {
			if (!missing(world, core, t).isEmpty()) {
				break;
			}
			tier = t;
		}
		return tier;
	}

	/** Fehlende Bauteile einer Stufe. */
	public static List<Part> missing(World world, BlockPos core, int tier) {
		List<Part> out = new ArrayList<>();
		for (Part part : parts(tier)) {
			if (!world.getBlockState(core.add(part.offset())).isOf(part.block())) {
				out.add(part);
			}
		}
		return out;
	}

	public static int stability(int tier) {
		return TIER_STABILITY[Math.max(0, Math.min(MAX_TIER, tier))];
	}

	/**
	 * Kuehlleistung rund um den Kern (Wuerfel mit Radius 3, eine Ebene darunter bis eine darueber): Eis 1, Packeis 2,
	 * Blaues Eis 4, Kryo-Kuehler 6. Hoechstens 40.
	 */
	public static int cooling(World world, BlockPos core) {
		int sum = 0;
		for (BlockPos at : BlockPos.iterate(core.add(-3, -1, -3), core.add(3, 1, 3))) {
			sum += coolant(world.getBlockState(at));
		}
		return Math.min(40, sum);
	}

	private static int coolant(BlockState state) {
		if (state.isOf(SuitFusion.CRYO_COOLER)) {
			return 6;
		}
		if (state.isOf(Blocks.BLUE_ICE)) {
			return 4;
		}
		if (state.isOf(Blocks.PACKED_ICE)) {
			return 2;
		}
		return state.isOf(Blocks.ICE) ? 1 : 0;
	}
}
