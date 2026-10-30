package com.santiq.kingdomomnitrix.ability;

/**
 * Faehigkeit mit Aufladen: Taste halten laedt auf, Loslassen loest aus. Abklingzeit und Energie werden erst beim
 * Ausloesen verbraucht. Bei voller Ladung wird nach {@link #HOLD_LIMIT} Ticks automatisch ausgeloest.
 */
public interface ChargeableAbility extends AlienAbility {
	int HOLD_LIMIT = 40;

	/** Dauerkraft (Strahl, Atem): wirkt waehrend des Haltens, ohne Lade-Toene und Voll-Blitz. */
	default boolean channel() {
		return false;
	}

	/** Ticks bis zur vollen Ladung */
	int chargeTicks(AbilityContext context);

	/** waehrend des Aufladens jeden Tick (Effekte, Verlangsamung ...); {@code charge} 0..1 */
	default void charging(AbilityContext context, int ticks, float charge) {
	}

	/** Ausloesen mit Ladung 0..1; @return true, wenn gewirkt */
	boolean release(AbilityContext context, float charge);

	/** Kurzes Antippen wirkt wie Ausloesen ohne Ladung. */
	@Override
	default boolean activate(AbilityContext context) {
		return release(context, 0.0f);
	}
}
