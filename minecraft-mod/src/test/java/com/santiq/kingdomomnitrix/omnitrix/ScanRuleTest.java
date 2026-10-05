package com.santiq.kingdomomnitrix.omnitrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

/** Smart-Scan: Punkte, Freischaltung, negative Gewichte, Grund, Gleichstand. */
class ScanRuleTest {
	private static final Identifier HEATBLAST = Identifier.of("kingdomomnitrix", "heatblast");
	private static final Identifier XLR8 = Identifier.of("kingdomomnitrix", "xlr8");
	private static final Identifier FOUR_ARMS = Identifier.of("kingdomomnitrix", "four_arms");

	private static ScanRule rule(String type, Map<Identifier, Float> weights, String reason) {
		return new ScanRule(new ScanRule.Condition(type, 8.0f, 1.0f, 1.0f), weights, reason);
	}

	private static final List<ScanRule> RULES = List.of(
			rule("lava_near", Map.of(HEATBLAST, 6.0f), "lava"),
			rule("in_water", Map.of(XLR8, 4.0f, HEATBLAST, -6.0f), "water"),
			rule("crowd", Map.of(FOUR_ARMS, 4.0f, HEATBLAST, 3.0f), "crowd"));

	@Test
	void picksHighestMatchingAlienWithReason() {
		var pick = ScanRule.recommend(RULES, c -> c.type().equals("lava_near") || c.type().equals("crowd"), id -> true).orElseThrow();
		assertEquals(HEATBLAST, pick.alien());
		assertEquals(9.0f, pick.score(), 1.0e-6f);
		assertEquals("lava", pick.reason(), "Grund = Regel mit dem groessten Beitrag");
	}

	@Test
	void negativeWeightsCanVetoAnAlien() {
		// Lava (+6) und Wasser (-6) heben Heatblast auf 0 auf: nicht mehr empfohlen
		var pick = ScanRule.recommend(RULES, c -> c.type().equals("in_water") || c.type().equals("lava_near"), id -> true).orElseThrow();
		assertEquals(XLR8, pick.alien());
	}

	@Test
	void lockedAliensAreNeverRecommended() {
		var pick = ScanRule.recommend(RULES, c -> c.type().equals("lava_near") || c.type().equals("crowd"),
				id -> !id.equals(HEATBLAST)).orElseThrow();
		assertEquals(FOUR_ARMS, pick.alien());
	}

	@Test
	void nothingMatchesNoRecommendation() {
		assertTrue(ScanRule.recommend(RULES, c -> false, id -> true).isEmpty());
		assertTrue(ScanRule.recommend(RULES, c -> true, Set.<Identifier>of()::contains).isEmpty());
	}

	@Test
	void tiesAreStable() {
		List<ScanRule> tie = List.of(rule("x", Map.of(XLR8, 2.0f, FOUR_ARMS, 2.0f), "tie"));
		assertEquals(FOUR_ARMS, ScanRule.recommend(tie, c -> true, id -> true).orElseThrow().alien());
	}
}
