package com.santiq.kingdomomnitrix.ability.passive;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

/**
 * Verhalten einer passiven Kraft. Alle Methoden sind optional; sie laufen nur, solange das Alien aktiv und die Kraft
 * per Meisterschaft freigeschaltet ist. Nur Server.
 */
public interface AlienPassive {
	/** jeden Tick */
	default void tick(PassiveContext ctx) {
	}

	/** Schaden, den das Alien austeilt, veraendern (nach Ruestung des Ziels). */
	default float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
		return amount;
	}

	/** Schaden, den das Alien nimmt, veraendern (nach eigener Ruestung). */
	default float incoming(PassiveContext ctx, DamageSource source, float amount) {
		return amount;
	}

	/**
	 * Nahkampfschlag des Aliens, bevor der Schaden zaehlt (jeder Schlag, auch halb aufgeladen).
	 *
	 * @param fullSwing Angriffsbalken war voll genug fuer eine Kombo
	 */
	default void melee(PassiveContext ctx, LivingEntity target, boolean fullSwing) {
	}

	/** nach einem Treffer des Aliens, den das Ziel ueberlebt hat */
	default void afterHit(PassiveContext ctx, LivingEntity target, DamageSource source, float damage) {
	}

	/** nachdem das Alien Schaden genommen hat */
	default void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
	}

	/** das Alien hat ein Ziel besiegt */
	default void onKill(PassiveContext ctx, LivingEntity target) {
	}

	/** Verwandlung endet oder wechselt: eigene Zustaende aufraeumen */
	default void end(PassiveContext ctx) {
	}
}
