package com.santiq.kingdomomnitrix.world.plumber;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DirectionsTest {
	@Test
	void cardinalDirectionsFollowMinecraftAxes() {
		assertEquals("direction.kingdomomnitrix.n", Directions.key(0, -50));
		assertEquals("direction.kingdomomnitrix.e", Directions.key(50, 0));
		assertEquals("direction.kingdomomnitrix.s", Directions.key(0, 50));
		assertEquals("direction.kingdomomnitrix.w", Directions.key(-50, 0));
	}

	@Test
	void diagonalsAndSectorEdges() {
		assertEquals("direction.kingdomomnitrix.ne", Directions.key(40, -40));
		assertEquals("direction.kingdomomnitrix.sw", Directions.key(-40, 40));
		// 20 Grad neben Norden bleibt Norden
		assertEquals("direction.kingdomomnitrix.n", Directions.key(18, -50));
	}
}
