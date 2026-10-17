package com.santiq.kingdomomnitrix.omnitrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Fehlfunktions-Wahrscheinlichkeit: Schwelle, Anstieg, Schutz durch Meisterschaft und Master Control. */
class MalfunctionsTest {
	private static final Malfunctions RULES = Malfunctions.DEFAULT;
	private static final float MAX = 0.4f;

	@Test
	void nothingBelowTheThreshold() {
		assertEquals(0.0f, RULES.chance(MAX, RULES.startHeat() - 0.01f, 1, false));
		assertEquals(0.0f, RULES.chance(MAX, 0.0f, 1, false));
	}

	@Test
	void quarterAtThresholdFullAtMaxHeat() {
		assertEquals(MAX * 0.25f, RULES.chance(MAX, RULES.startHeat(), 1, false), 1.0e-6f);
		assertEquals(MAX, RULES.chance(MAX, 1.0f, 1, false), 1.0e-6f);
		assertTrue(RULES.chance(MAX, 0.9f, 1, false) > RULES.chance(MAX, 0.8f, 1, false), "steigt nicht mit der Hitze");
	}

	@Test
	void masteryLowersAndMasteredPrevents() {
		float novice = RULES.chance(MAX, 1.0f, 1, false);
		float trained = RULES.chance(MAX, 1.0f, 6, false);
		assertTrue(trained < novice, "Meisterschaft senkt die Chance nicht");
		assertEquals(novice * (1.0f - 5 * Malfunctions.MASTERY_REDUCTION_PER_LEVEL), trained, 1.0e-6f);
		assertEquals(0.0f, RULES.chance(MAX, 1.0f, Malfunctions.MASTERED_LEVEL, false));
	}

	@Test
	void masterControlAndDisabledPrevent() {
		assertEquals(0.0f, RULES.chance(MAX, 1.0f, 1, true));
		Malfunctions off = new Malfunctions(false, 0.75f, 0.35f, 0.25f, 8, 10);
		assertEquals(0.0f, off.chance(MAX, 1.0f, 1, false));
	}
}
