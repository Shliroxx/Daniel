package com.santiq.kingdomomnitrix.combat;

/**
 * Kampfwerte einer Waffe fuer einen Angriff. Wird pro Angriff einmal ermittelt
 * (bei Keyblades aus JSON + Stufe), damit der {@link CombatManager} nichts ueber Waffenarten wissen muss.
 *
 * @param bonusDamage    Schaden zusaetzlich zum Angriffswert des Spielers
 * @param comboLength    Schlaege einer Combo inklusive Finisher (mindestens 2)
 * @param stepMultiplier Faktor fuer Schlaege ab dem zweiten
 * @param critChance     Chance (0–1) auf einen kritischen Treffer (×1,5)
 */
public record ComboProfile(
		float bonusDamage,
		int comboLength,
		float stepMultiplier,
		float finisherMultiplier,
		float heavyMultiplier,
		double reachBonus,
		int lightDelayTicks,
		int heavyDelayTicks,
		float critChance,
		boolean canGuard) {

	public static final ComboProfile DEFAULT = new ComboProfile(0.0f, 3, 1.1f, 1.6f, 2.0f, 0.5, 6, 16, 0.0f, true);

	public ComboProfile {
		comboLength = Math.max(2, comboLength);
		lightDelayTicks = Math.max(2, lightDelayTicks);
		heavyDelayTicks = Math.max(4, heavyDelayTicks);
		critChance = Math.max(0.0f, Math.min(1.0f, critChance));
	}
}
