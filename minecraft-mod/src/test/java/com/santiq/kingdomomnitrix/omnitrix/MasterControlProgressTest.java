package com.santiq.kingdomomnitrix.omnitrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class MasterControlProgressTest {
	@Test
	void progressIsDoneAtThreshold() {
		assertFalse(new MasterControlProgress.Progress(4, 5, 5).done());
		assertTrue(new MasterControlProgress.Progress(5, 5, 5).done());
		assertTrue(new MasterControlProgress.Progress(7, 5, 5).done());
	}

	@Test
	void unlockThresholdsDefaultToFiveAliensAtLevelFive() {
		OmnitrixProfile.MasterControl rules = OmnitrixProfile.MasterControl.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
				.getOrThrow();
		assertEquals(5, rules.unlockAliens());
		assertEquals(5, rules.unlockMastery());
	}

	@Test
	void unlockThresholdsAreDataDriven() {
		OmnitrixProfile.MasterControl rules = OmnitrixProfile.MasterControl.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"unlock_aliens\": 3, \"unlock_mastery\": 8}")).getOrThrow();
		assertEquals(3, rules.unlockAliens());
		assertEquals(8, rules.unlockMastery());
		assertTrue(OmnitrixProfile.MasterControl.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"unlock_mastery\": 11}")).isError());
	}
}
