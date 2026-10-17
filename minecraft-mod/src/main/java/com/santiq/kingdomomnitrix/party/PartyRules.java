package com.santiq.kingdomomnitrix.party;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Regeln fuer mehrere Spieler: wen Mod-Angriffe treffen duerfen (Server-Einstellung pvp, keine Treffer in der eigenen
 * Gruppe) und wie stark Gegner mit der Gruppengroesse werden (Entscheidung SANTIQ: je mehr Leute, desto schwerer).
 */
public final class PartyRules {
	/** Je weiterem Gruppenmitglied: +50 % Leben, +15 % Schaden. */
	public static final double HEALTH_PER_MEMBER = 0.5;
	public static final double DAMAGE_PER_MEMBER = 0.15;
	private static final Identifier GROUP_HEALTH = KingdomOmnitrix.id("group_health");
	private static final Identifier GROUP_DAMAGE = KingdomOmnitrix.id("group_damage");

	private PartyRules() {
	}

	/**
	 * Darf ein Mod-Angriff des Spielers dieses Ziel treffen? Spieler nur, wenn der Server PvP erlaubt (und keine
	 * Team-Regel dagegen spricht) und sie nicht in derselben Gruppe sind; eigene Haustiere nie.
	 */
	public static boolean canHarm(ServerPlayerEntity attacker, Entity target) {
		if (target == attacker) {
			return false;
		}
		if (target instanceof PlayerEntity player) {
			return attacker.shouldDamagePlayer(player) && !PartyManager.sameParty(attacker, player);
		}
		return !(target instanceof TameableEntity tameable && tameable.isTamed() && attacker.equals(tameable.getOwner()));
	}

	/** Verbuendete fuer Heilung: Gruppenmitglieder, Spieler bei ausgeschaltetem PvP oder im selben Team, eigene Tiere. */
	public static boolean isAlly(ServerPlayerEntity player, LivingEntity entity) {
		if (entity instanceof PlayerEntity other) {
			return PartyManager.sameParty(player, other) || !player.shouldDamagePlayer(other) || other.isTeammate(player);
		}
		return entity instanceof TameableEntity tameable && tameable.isTamed() && player.equals(tameable.getOwner());
	}

	/** Gruppengroesse fuer die Gegner-Staerke: der Spieler und seine Mitglieder in Reichweite (1–4). */
	public static int groupSize(ServerPlayerEntity player) {
		return PartyManager.nearbyMembers(player, PartyManager.SHARE_RADIUS).size();
	}

	/** Macht einen Gegner fuer eine Gruppe von {@code players} Spielern staerker (einmalig, gespeichert mit dem Gegner). */
	public static void scale(LivingEntity mob, int players) {
		int extra = MathHelper.clamp(players, 1, Party.MAX_SIZE) - 1;
		if (extra <= 0) {
			return;
		}
		add(mob, EntityAttributes.GENERIC_MAX_HEALTH, GROUP_HEALTH, HEALTH_PER_MEMBER * extra);
		add(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, GROUP_DAMAGE, DAMAGE_PER_MEMBER * extra);
		mob.setHealth(mob.getMaxHealth());
	}

	private static void add(LivingEntity mob, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
			Identifier id, double amount) {
		EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
		if (instance != null && !instance.hasModifier(id)) {
			instance.addPersistentModifier(new EntityAttributeModifier(id, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}
}
