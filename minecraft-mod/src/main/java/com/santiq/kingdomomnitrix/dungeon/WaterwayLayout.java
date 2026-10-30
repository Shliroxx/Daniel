package com.santiq.kingdomomnitrix.dungeon;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

/**
 * Grundriss der Geheimen Wasserstrasse, relativ zum Ursprung {@code o} (Mitte der Eingangshalle, Boden). Der Dungeon
 * verlaeuft nach Norden (−z). Reine Geometrie ohne Welt — so ist sie testbar.
 *
 * <pre>
 * Halle → Kanal → Raetsel → Arena (+ Geheimkammer Ost) → Torwaechter → Bruecke → Schatz → Koloss
 * </pre>
 */
public final class WaterwayLayout {
	/** Ein Abschnitt: halbe Breite (x), Beginn (z, Suedseite) und Laenge nach Norden, lichte Hoehe. */
	public record Room(String id, int halfWidth, int startZ, int length, int height) {
		public int endZ() {
			return startZ - length + 1;
		}

		/** Innenraum in Weltkoordinaten. */
		public BlockBox interior(BlockPos o) {
			return new BlockBox(o.getX() - halfWidth, o.getY(), o.getZ() + endZ(), o.getX() + halfWidth, o.getY() + height - 1,
					o.getZ() + startZ);
		}

		public boolean contains(BlockPos o, double x, double y, double z) {
			BlockBox box = interior(o);
			return x >= box.getMinX() && x < box.getMaxX() + 1 && z >= box.getMinZ() && z < box.getMaxZ() + 1
					&& y >= box.getMinY() - 1 && y < box.getMaxY() + 2;
		}
	}

	public static final Room HALL = new Room("hall", 5, 0, 11, 7);
	public static final Room CANAL = new Room("canal", 3, -11, 26, 6);
	public static final Room PUZZLE = new Room("puzzle", 6, -37, 13, 7);
	public static final Room ARENA = new Room("arena", 8, -50, 17, 9);
	public static final Room GUARDIAN = new Room("guardian", 6, -67, 13, 8);
	public static final Room BRIDGE = new Room("bridge", 5, -80, 21, 10);
	public static final Room TREASURE = new Room("treasure", 4, -101, 9, 6);
	public static final Room BOSS = new Room("boss", 11, -110, 23, 13);
	public static final Room[] ROOMS = {HALL, CANAL, PUZZLE, ARENA, GUARDIAN, BRIDGE, TREASURE, BOSS};

	/** Geheimkammer oestlich der Arena (hinter rissiger Wand bei z = −58). */
	public static final int SECRET_WALL_Z = -58;
	public static final int SECRET_X0 = ARENA.halfWidth() + 2;
	public static final int SECRET_X1 = ARENA.halfWidth() + 6;
	/** Tiefe des Schachts unter der Bruecke */
	public static final int PIT_DEPTH = 8;

	private WaterwayLayout() {
	}

	/** Raum, in dem ein Punkt liegt, oder null. */
	public static Room roomAt(BlockPos o, double x, double y, double z) {
		for (Room room : ROOMS) {
			if (room.contains(o, x, y, z)) {
				return room;
			}
		}
		return null;
	}

	/** Durchgang zwischen zwei Raeumen: 3 breit, 3 hoch, in der Wand bei {@code z} (Weltkoordinaten-Ecke unten links). */
	public static BlockPos doorway(BlockPos o, int z) {
		return new BlockPos(o.getX() - 1, o.getY(), o.getZ() + z);
	}
}
