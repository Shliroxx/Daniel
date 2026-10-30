package com.santiq.kingdomomnitrix.combat;

import com.santiq.kingdomomnitrix.networking.DamageFeedbackPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Treffer-Rueckmeldung: nach jedem Schaden, den ein Spieler (auch per Geschoss, Zauber oder Alien-Faehigkeit)
 * verursacht, bekommt genau dieser Spieler Ort, Hoehe, Art und ob es der Todesstoss war. Der Client zeigt daraus
 * Schadenszahlen und die Treffer-Markierung; Mitspieler sehen nur ihre eigenen Treffer.
 */
public final class CombatFeedback {
	/** Art des Treffers, bestimmt Farbe und Groesse der Zahl. */
	public enum Kind {
		NORMAL, HEAVY, FIRE, MAGIC, BLOCKED;

		public static Kind byOrdinal(int ordinal) {
			Kind[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NORMAL;
		}
	}

	/** Ab diesem Anteil am Maximalleben (oder diesem Schaden) gilt ein Treffer als schwer. */
	private static final float HEAVY_FRACTION = 0.2f;
	private static final float HEAVY_DAMAGE = 10.0f;

	private CombatFeedback() {
	}

	/** Leben vor dem letzten Spieler-Treffer: AFTER_DAMAGE feuert beim Todesstoss nicht, AFTER_DEATH kennt keinen Schaden. */
	private static final Map<LivingEntity, Float> HEALTH_BEFORE = new WeakHashMap<>();

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity) {
				HEALTH_BEFORE.put(target, target.getHealth());
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatFeedback::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(CombatFeedback::afterDeath);
	}

	/** Todesstoss: angezeigt wird das Leben, das der Treffer noch genommen hat. */
	private static void afterDeath(LivingEntity target, DamageSource source) {
		Float before = HEALTH_BEFORE.remove(target);
		if (!(source.getAttacker() instanceof ServerPlayerEntity player) || player == target) {
			return;
		}
		float amount = before != null ? before : target.getMaxHealth();
		send(player, target, amount, classify(target, source, amount, false), true);
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		HEALTH_BEFORE.remove(target);
		if (!(source.getAttacker() instanceof ServerPlayerEntity player) || player == target) {
			return;
		}
		if (damageTaken <= 0.0f && !blocked) {
			return;
		}
		send(player, target, blocked ? 0.0f : damageTaken, classify(target, source, damageTaken, blocked), false);
	}

	static Kind classify(LivingEntity target, DamageSource source, float damage, boolean blocked) {
		if (blocked) {
			return Kind.BLOCKED;
		}
		if (source.isIn(DamageTypeTags.IS_FIRE)) {
			return Kind.FIRE;
		}
		if (source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC)) {
			return Kind.MAGIC;
		}
		return isHeavy(damage, target.getMaxHealth()) ? Kind.HEAVY : Kind.NORMAL;
	}

	/** Schwerer Treffer: mindestens {@value #HEAVY_DAMAGE} Schaden oder 20 % des Maximallebens. */
	public static boolean isHeavy(float damage, float maxHealth) {
		return damage >= HEAVY_DAMAGE || (maxHealth > 0.0f && damage >= maxHealth * HEAVY_FRACTION);
	}

	private static void send(ServerPlayerEntity player, Entity target, float amount, Kind kind, boolean lethal) {
		if (!ServerPlayNetworking.canSend(player, DamageFeedbackPayload.ID)) {
			return;
		}
		ServerPlayNetworking.send(player, new DamageFeedbackPayload(target.getX(), target.getY() + target.getHeight(), target.getZ(),
				amount, kind.ordinal(), lethal));
	}
}
