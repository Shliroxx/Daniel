package com.santiq.kingdomomnitrix.ability;

/**
 * Verhalten eines Faehigkeits-Typs. Die Zahlenwerte kommen aus dem {@code params}-Block des Alien-JSON.
 * Laeuft ausschliesslich auf dem Server.
 */
@FunctionalInterface
public interface AlienAbility {
	/** @return true, wenn die Faehigkeit gewirkt hat (dann werden Energie und Abklingzeit verbraucht) */
	boolean activate(AbilityContext context);
}
