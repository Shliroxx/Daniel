package com.santiq.kingdomomnitrix.combat;

import net.minecraft.item.ItemStack;

/**
 * Waffen mit Action-Kampf: Linksklick-Combo, Halten fuer schweren Angriff, Blocken.
 * Werte koennen pro Stack variieren (Phase 5: Keyblade-Stufen).
 */
public interface ComboWeapon {
	/** Anzahl Schlaege einer Bodencombo inklusive Finisher (mindestens 2). */
	default int comboLength(ItemStack stack) {
		return 3;
	}

	/** Schadensfaktor fuer Schlag {@code step} (0-basiert) relativ zum Angriffswert des Spielers. */
	default float comboMultiplier(ItemStack stack, int step) {
		return step == 0 ? 1.0f : 1.1f;
	}

	default float finisherMultiplier(ItemStack stack) {
		return 1.6f;
	}

	default float heavyMultiplier(ItemStack stack) {
		return 2.0f;
	}

	/** Zusaetzliche Reichweite zur normalen Spieler-Reichweite. */
	default double reachBonus(ItemStack stack) {
		return 0.5;
	}

	/** Mindestabstand zwischen zwei leichten Schlaegen in Ticks. */
	default int lightDelayTicks(ItemStack stack) {
		return 6;
	}

	default int heavyDelayTicks(ItemStack stack) {
		return 16;
	}

	default boolean canGuard(ItemStack stack) {
		return true;
	}
}
