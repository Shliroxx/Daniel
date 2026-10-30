package com.santiq.kingdomomnitrix.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatFeedbackTest {
	@Test
	void heavyFromAbsoluteDamage() {
		assertTrue(CombatFeedback.isHeavy(10.0f, 400.0f));
		assertFalse(CombatFeedback.isHeavy(9.9f, 400.0f));
	}

	@Test
	void heavyFromShareOfMaxHealth() {
		assertTrue(CombatFeedback.isHeavy(4.0f, 20.0f));
		assertFalse(CombatFeedback.isHeavy(3.9f, 20.0f));
		assertFalse(CombatFeedback.isHeavy(3.0f, 0.0f));
	}

	@Test
	void kindByOrdinalFallsBackToNormal() {
		assertEquals(CombatFeedback.Kind.FIRE, CombatFeedback.Kind.byOrdinal(CombatFeedback.Kind.FIRE.ordinal()));
		assertEquals(CombatFeedback.Kind.NORMAL, CombatFeedback.Kind.byOrdinal(-1));
		assertEquals(CombatFeedback.Kind.NORMAL, CombatFeedback.Kind.byOrdinal(99));
	}
}
