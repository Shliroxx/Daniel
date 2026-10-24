package com.santiq.kingdomomnitrix.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

/** Meisterschafts-Stufen und Boni. */
class AlienMasteryTest {
	private static final Identifier HEATBLAST = Identifier.of("kingdomomnitrix", "heatblast");

	@Test
	void levelThresholds() {
		assertEquals(1, AlienMastery.levelFor(0));
		assertEquals(1, AlienMastery.levelFor(AlienMastery.experienceFor(2) - 1));
		assertEquals(2, AlienMastery.levelFor(AlienMastery.experienceFor(2)));
		assertEquals(AlienMastery.MAX_LEVEL, AlienMastery.levelFor(Integer.MAX_VALUE));
	}

	@Test
	void addCapsAtMaxLevel() {
		AlienMastery mastery = AlienMastery.EMPTY.add(HEATBLAST, Integer.MAX_VALUE);
		assertEquals(AlienMastery.MAX_LEVEL, mastery.level(HEATBLAST));
		assertEquals(AlienMastery.experienceFor(AlienMastery.MAX_LEVEL), mastery.experience(HEATBLAST));
		assertSame(mastery, mastery.add(HEATBLAST, 10));
	}

	@Test
	void nonPositiveAmountsChangeNothing() {
		assertSame(AlienMastery.EMPTY, AlienMastery.EMPTY.add(HEATBLAST, 0));
		assertSame(AlienMastery.EMPTY, AlienMastery.EMPTY.add(HEATBLAST, -5));
	}

	@Test
	void bonusesGrowPerLevel() {
		AlienMastery mastery = AlienMastery.EMPTY.add(HEATBLAST, AlienMastery.experienceFor(3));
		assertEquals(3, mastery.level(HEATBLAST));
		assertEquals(2 * AlienMastery.DURATION_PER_LEVEL, mastery.durationBonus(HEATBLAST), 1.0e-6f);
		assertEquals(2 * AlienMastery.COOLDOWN_PER_LEVEL, mastery.cooldownReduction(HEATBLAST), 1.0e-6f);
	}

	@Test
	void masteryPerksUnlockAtTheirLevels() {
		assertEquals(20.0f, AlienMastery.energyCost(20.0f, AlienMastery.LEVEL_ENERGY_DISCOUNT - 1), 1.0e-6f);
		assertEquals(17.0f, AlienMastery.energyCost(20.0f, AlienMastery.LEVEL_ENERGY_DISCOUNT), 1.0e-6f);
		assertEquals(1.0f, AlienMastery.transformHeatFactor(AlienMastery.LEVEL_TRANSFORM_HEAT - 1), 1.0e-6f);
		assertEquals(AlienMastery.TRANSFORM_HEAT_FACTOR, AlienMastery.transformHeatFactor(AlienMastery.LEVEL_TRANSFORM_HEAT), 1.0e-6f);
		assertEquals(0.0f, AlienMastery.healthBonus(AlienMastery.LEVEL_HEALTH - 1), 1.0e-6f);
		assertEquals(AlienMastery.HEALTH_BONUS, AlienMastery.healthBonus(AlienMastery.LEVEL_HEALTH), 1.0e-6f);
		assertEquals(1.0f, AlienMastery.activeHeatFactor(AlienMastery.LEVEL_ACTIVE_HEAT - 1), 1.0e-6f);
		assertEquals(AlienMastery.ACTIVE_HEAT_FACTOR, AlienMastery.activeHeatFactor(AlienMastery.LEVEL_ACTIVE_HEAT), 1.0e-6f);
	}

	@Test
	void masteredAlienMakesNoHeat() {
		assertEquals(0.0f, AlienMastery.transformHeatFactor(AlienMastery.LEVEL_MASTERED), 1.0e-6f);
		assertEquals(0.0f, AlienMastery.activeHeatFactor(AlienMastery.LEVEL_MASTERED), 1.0e-6f);
		assertEquals(AlienMastery.MAX_LEVEL, com.santiq.kingdomomnitrix.alien.AlienMasteryLevels.MAX, "Stufengrenzen auseinandergelaufen");
	}
}
