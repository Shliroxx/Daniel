package com.santiq.kingdomomnitrix.omnitrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Hitze-Logik des Omnitrix: Zeitstempel-Berechnung, Grenzen, Warnung, Master Control. */
class OmnitrixStateTest {
	private static final OmnitrixProfile PROFILE = OmnitrixProfile.DEFAULT;
	private static final float EPSILON = 1.0e-4f;

	@Test
	void heatRisesWhileTransformed() {
		OmnitrixState state = OmnitrixState.EMPTY.withHeat(0L, false, PROFILE, 0.5f);
		float after = state.heatAt(200L, true, PROFILE);
		assertEquals(0.5f + PROFILE.heatPerSecondActive() * 10.0f, after, EPSILON);
	}

	@Test
	void heatDecaysInHumanForm() {
		OmnitrixState state = OmnitrixState.EMPTY.withHeat(0L, false, PROFILE, 0.5f);
		assertEquals(0.5f - PROFILE.heatDecayPerSecond() * 5.0f, state.heatAt(100L, false, PROFILE), EPSILON);
	}

	@Test
	void heatStaysWithinBounds() {
		OmnitrixState hot = OmnitrixState.EMPTY.withHeat(0L, false, PROFILE, 5.0f);
		assertEquals(1.0f, hot.heat(), EPSILON);
		assertEquals(1.0f, hot.heatAt(1_000_000L, true, PROFILE), EPSILON);
		assertEquals(0.0f, hot.heatAt(1_000_000L, false, PROFILE), EPSILON);
		assertEquals(0.0f, OmnitrixState.EMPTY.withHeat(0L, false, PROFILE, -3.0f).heat(), EPSILON);
	}

	@Test
	void timeBeforeStampDoesNotChangeHeat() {
		OmnitrixState state = OmnitrixState.EMPTY.withHeat(500L, false, PROFILE, 0.4f);
		assertEquals(0.4f, state.heatAt(100L, true, PROFILE), EPSILON);
	}

	@Test
	void masterControlProducesNoHeatWithDefaultProfile() {
		OmnitrixState master = OmnitrixState.EMPTY.withMasterControl(true).withHeat(0L, false, PROFILE, 0.3f);
		assertEquals(0.0f, master.heatFactor(PROFILE), EPSILON);
		assertEquals(0.3f, master.heatAt(10_000L, true, PROFILE), EPSILON);
	}

	@Test
	void warningResetsBelowThreshold() {
		OmnitrixState warned = OmnitrixState.EMPTY.withHeat(0L, false, PROFILE, 0.9f).withWarned(true);
		assertTrue(warned.withHeat(0L, false, PROFILE, 0.0f).warned());
		assertFalse(warned.withHeat(0L, false, PROFILE, -0.5f).warned());
	}

	@Test
	void lockAndOverheatAreTimeBased() {
		OmnitrixState state = OmnitrixState.EMPTY.withLockedUntil(100L).withOverheatedUntil(50L);
		assertTrue(state.isLocked(99L));
		assertFalse(state.isLocked(100L));
		assertTrue(state.isOverheated(49L));
		assertFalse(state.isOverheated(50L));
	}

	@Test
	void statusLightPulsesAroundBrightness() {
		for (OmnitrixStatus status : OmnitrixStatus.values()) {
			for (float t = 0.0f; t < 2.0f; t += 0.05f) {
				float light = status.light(t);
				assertTrue(light >= 0.0f && light <= status.brightness() + EPSILON, status + " bei " + t);
			}
		}
	}

	@Test
	void cueAndStatusOrdinalsRoundTrip() {
		for (OmnitrixCue cue : OmnitrixCue.values()) {
			assertEquals(cue, OmnitrixCue.byOrdinal(cue.ordinal()));
		}
		assertEquals(OmnitrixCue.ERROR, OmnitrixCue.byOrdinal(-1));
		assertEquals(OmnitrixStatus.IDLE, OmnitrixStatus.byOrdinal(999));
	}
}
