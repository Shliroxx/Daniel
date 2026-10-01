package com.santiq.kingdomomnitrix.combat;

/**
 * Fluechtiger Kampfzustand eines Spielers (nur Server, nicht gespeichert — Combos und Blocken
 * ueberleben keinen Neustart, das ist gewollt).
 */
final class CombatState {
	int comboStep;
	long lastAttackTick = Long.MIN_VALUE / 2;
	long nextAttackTick;
	boolean airComboActive;

	boolean guarding;
	long guardStartTick;

	long dodgeReadyTick;
	boolean airDodgeUsed;
	long invulnerableUntil;

	int lockTargetId = -1;
}
