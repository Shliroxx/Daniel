package com.santiq.kingdomomnitrix.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.santiq.kingdomomnitrix.dungeon.WaterwayLayout.Room;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class WaterwayLayoutTest {
	private static final BlockPos O = new BlockPos(100, 40, -200);

	@Test
	void roomsFollowEachOtherWithoutGaps() {
		Room[] rooms = WaterwayLayout.ROOMS;
		for (int i = 1; i < rooms.length; i++) {
			assertEquals(rooms[i - 1].endZ() - 1, rooms[i].startZ(), rooms[i].id() + " schliesst nicht an " + rooms[i - 1].id() + " an");
		}
	}

	@Test
	void sequenceFollowsTheDungeonFlow() {
		String[] expected = {"hall", "canal", "puzzle", "arena", "guardian", "bridge", "treasure", "boss"};
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], WaterwayLayout.ROOMS[i].id());
		}
	}

	@Test
	void roomLookupFindsTheRightRoom() {
		Room arena = WaterwayLayout.ARENA;
		int z = (arena.startZ() + arena.endZ()) / 2;
		assertSame(arena, WaterwayLayout.roomAt(O, O.getX() + 0.5, O.getY(), O.getZ() + z + 0.5));
		assertSame(WaterwayLayout.BOSS, WaterwayLayout.roomAt(O, O.getX(), O.getY() + 3, O.getZ() + WaterwayLayout.BOSS.endZ() + 1));
		assertNull(WaterwayLayout.roomAt(O, O.getX() + 50, O.getY(), O.getZ() - 20));
		assertNull(WaterwayLayout.roomAt(O, O.getX(), O.getY() + 40, O.getZ() - 20));
	}

	@Test
	void secretRoomLiesBesideTheArena() {
		Room arena = WaterwayLayout.ARENA;
		assertTrue(WaterwayLayout.SECRET_X0 > arena.halfWidth() + 1, "Geheimkammer muss hinter der Ostwand liegen");
		assertTrue(WaterwayLayout.SECRET_WALL_Z < arena.startZ() && WaterwayLayout.SECRET_WALL_Z > arena.endZ(),
				"Geheimwand muss in der Arena liegen");
	}

	@Test
	void bossRoomIsTheLargest() {
		for (Room room : WaterwayLayout.ROOMS) {
			assertTrue(room == WaterwayLayout.BOSS || room.halfWidth() * room.length() < WaterwayLayout.BOSS.halfWidth() * WaterwayLayout.BOSS.length());
		}
	}
}
