package com.santiq.kingdomomnitrix.omnitrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCalibration.Module;
import org.junit.jupiter.api.Test;

class OmnitrixCalibrationTest {
	private static final OmnitrixProfile BASE = OmnitrixProfile.DEFAULT;

	@Test
	void stockCalibrationLeavesProfileUntouched() {
		assertSame(BASE, OmnitrixCalibration.DEFAULT.apply(BASE));
	}

	@Test
	void coolingLowersActiveHeatAndSpeedsUpCooling() {
		OmnitrixProfile p = OmnitrixCalibration.DEFAULT.withLevel(Module.COOLING, 2).apply(BASE);
		assertEquals(BASE.heatPerSecondActive() * 0.76f, p.heatPerSecondActive(), 1e-6);
		assertEquals(BASE.heatDecayPerSecond() * 1.4f, p.heatDecayPerSecond(), 1e-6);
		assertEquals(BASE.heatPerTransform(), p.heatPerTransform(), 1e-6);
	}

	@Test
	void coreTradesDurationForTransformHeat() {
		OmnitrixProfile p = OmnitrixCalibration.DEFAULT.withLevel(Module.CORE, 3).apply(BASE);
		assertEquals(BASE.durationMultiplier() * 1.45f, p.durationMultiplier(), 1e-6);
		assertEquals(BASE.heatPerTransform() * 1.24f, p.heatPerTransform(), 1e-6);
	}

	@Test
	void bandwidthTradesRechargeForStability() {
		OmnitrixProfile p = OmnitrixCalibration.DEFAULT.withLevel(Module.BANDWIDTH, 3).apply(BASE);
		assertEquals(BASE.cooldownMultiplier() * 0.64f, p.cooldownMultiplier(), 1e-6);
		assertEquals(Math.min(1.0f, BASE.malfunctions().wrongAlienChance() * 1.75f), p.malfunctions().wrongAlienChance(), 1e-6);
		assertEquals(Math.min(1.0f, BASE.malfunctions().driftChance() * 1.75f), p.malfunctions().driftChance(), 1e-6);
	}

	@Test
	void budgetPreventsMaxingEverything() {
		OmnitrixCalibration c = new OmnitrixCalibration(3, 2, 0, "green");
		assertEquals(5, c.used());
		assertEquals(0, c.free());
		assertFalse(c.canRaise(Module.BANDWIDTH));
		assertFalse(c.canRaise(Module.COOLING));
		assertTrue(new OmnitrixCalibration(3, 1, 0, "green").canRaise(Module.BANDWIDTH));
		assertFalse(new OmnitrixCalibration(3, 1, 0, "green").canRaise(Module.COOLING));
	}

	@Test
	void levelsAreClampedAndColorDefaults() {
		OmnitrixCalibration c = new OmnitrixCalibration(9, -2, 1, "");
		assertEquals(OmnitrixCalibration.MAX_LEVEL, c.cooling());
		assertEquals(0, c.core());
		assertEquals(OmnitrixCalibration.DEFAULT_COLOR, c.color());
	}

	@Test
	void costsRiseWithLevel() {
		assertTrue(OmnitrixCalibration.cost(1).bolts() < OmnitrixCalibration.cost(2).bolts());
		assertTrue(OmnitrixCalibration.cost(2).bolts() < OmnitrixCalibration.cost(3).bolts());
		assertEquals(1, OmnitrixCalibration.cost(3).orichalcum());
	}

	@Test
	void colorModulesAreKnown() {
		assertTrue(OmnitrixColors.exists("green"));
		assertTrue(OmnitrixColors.exists("purple"));
		assertFalse(OmnitrixColors.exists("rainbow"));
		assertEquals("green", OmnitrixColors.ALL.getFirst().id());
	}
}
