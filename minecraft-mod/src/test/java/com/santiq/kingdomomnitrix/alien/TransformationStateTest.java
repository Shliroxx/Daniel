package com.santiq.kingdomomnitrix.alien;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Verwandlungs-Zustand: Dauer, Nachladen, Energie, Abklingzeiten. */
class TransformationStateTest {
	private static final Identifier ID = Identifier.of("kingdomomnitrix", "test");
	private static AlienDefinition alien;

	@BeforeAll
	static void setUp() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		alien = AlienDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"{\"color\": \"#FF6A00\", \"model\": \"kingdomomnitrix:heatblast\", \"duration\": 400, \"recharge\": 200,"
						+ " \"max_energy\": 100, \"energy_regen\": 10, \"abilities\": [{\"type\": \"kingdomomnitrix:fire_blast\"}]}"))
				.getOrThrow();
	}

	@Test
	void transformAndRemaining() {
		TransformationState state = TransformationState.EMPTY.transformed(ID, alien, 100L, 400);
		assertTrue(state.isTransformed());
		assertEquals(400L, state.totalTicks());
		assertEquals(300L, state.remainingTicks(200L));
		assertEquals(0L, state.remainingTicks(1_000L));
		assertEquals(alien.maxEnergy(), state.currentEnergy(alien, 100L), 1.0e-4f);
	}

	@Test
	void revertKeepsSelectionAndSetsRecharge() {
		TransformationState state = TransformationState.EMPTY.transformed(ID, alien, 0L, 400).reverted(500L);
		assertFalse(state.isTransformed());
		assertEquals(ID, state.selectedAlien().orElseThrow());
		assertEquals(200L, state.rechargeRemaining(300L));
		assertEquals(0L, state.remainingTicks(10L));
	}

	@Test
	void energyRegeneratesAndCaps() {
		TransformationState state = TransformationState.EMPTY.transformed(ID, alien, 0L, 400).afterAbility(0, 20.0f, 0L, 40L);
		assertEquals(20.0f + alien.energyRegen() * 2.0f, state.currentEnergy(alien, 40L), 1.0e-4f);
		assertEquals(alien.maxEnergy(), state.currentEnergy(alien, 100_000L), 1.0e-4f);
	}

	@Test
	void abilityCooldownPerSlot() {
		TransformationState state = TransformationState.EMPTY.transformed(ID, alien, 0L, 400).afterAbility(2, 50.0f, 10L, 30L);
		assertEquals(30L, state.cooldownRemaining(2, 10L));
		assertEquals(0L, state.cooldownRemaining(0, 10L));
		assertEquals(0L, state.cooldownRemaining(7, 10L));
	}

	@Test
	void humanHealthIsKeptThroughQuickChange() {
		Identifier other = Identifier.of("kingdomomnitrix", "other");
		TransformationState state = TransformationState.EMPTY.transformed(ID, alien, 0L, 400, 7.5f)
				.afterAbility(0, 10.0f, 5L, 100L)
				.quickChanged(other, alien, 50L, 200);
		assertEquals(7.5f, state.humanHealth(), 1.0e-6f);
		assertEquals(other, state.activeAlien().orElseThrow());
		assertEquals(200L, state.remainingTicks(50L));
		assertEquals(0L, state.cooldownRemaining(0, 50L), "Abklingzeiten gelten nur fuer das alte Alien");
		assertEquals(alien.maxEnergy(), state.currentEnergy(alien, 50L), 1.0e-4f);
	}

	@Test
	void revertClearsHumanHealth() {
		assertEquals(0.0f, TransformationState.EMPTY.transformed(ID, alien, 0L, 400, 12.0f).reverted(0L).humanHealth(), 1.0e-6f);
	}

	@Test
	void invulnerabilityOnlyExtends() {
		TransformationState state = TransformationState.EMPTY.withInvulnerableUntil(50L).withInvulnerableUntil(20L);
		assertTrue(state.isInvulnerable(49L));
		assertFalse(state.isInvulnerable(50L));
	}
}
