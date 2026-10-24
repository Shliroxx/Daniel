package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.alien.AbilitySlot;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

/**
 * Kombos: Faehigkeiten, die aus der Bewegung entstehen statt auf einer Taste zu liegen. Ein voller Schlag
 * (Angriffsbalken mindestens {@link #MIN_SWING}) im Sprint, in der Luft, beim Schleichen oder gegen ein geschwaechtes
 * Ziel loest die passende Kombo des Aliens aus; Schleichen hoch in der Luft loest den Sturzangriff aus. Kombos haben
 * eigene Abklingzeiten und Energiekosten; ist eine nicht bereit, wird die naechstpassende versucht, sonst bleibt es ein
 * normaler Schlag.
 */
public final class ComboManager {
	/** so voll muss der Angriffsbalken sein — Dauerklicken loest keine Kombos aus */
	private static final float MIN_SWING = 0.85f;
	/** Finisher: Ziel hat hoechstens so viel Leben */
	private static final float FINISHER_HEALTH = 0.3f;
	/** Sturzangriff erst ab dieser Fallstrecke */
	private static final float PLUNGE_FALL = 1.5f;
	/** Prioritaet beim Schlag: Finisher vor Luft vor Sprint vor Schleichen */
	private static final List<AbilitySlot.Trigger> ATTACK_ORDER = List.of(AbilitySlot.Trigger.FINISHER,
			AbilitySlot.Trigger.AIR_ATTACK, AbilitySlot.Trigger.SPRINT_ATTACK, AbilitySlot.Trigger.SNEAK_ATTACK);

	private static final Set<UUID> SNEAKING = new HashSet<>();

	private ComboManager() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClient() && player instanceof ServerPlayerEntity server && entity instanceof LivingEntity target && target.isAlive()
					&& com.santiq.kingdomomnitrix.party.PartyRules.canHarm(server, target)) {
				boolean full = server.getAttackCooldownProgress(0.5f) >= MIN_SWING;
				com.santiq.kingdomomnitrix.ability.passive.PassiveHooks.melee(server, target, full);
				if (full) {
					onAttack(server, target);
				}
			}
			return ActionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(ComboManager::tick);
	}

	private static void onAttack(ServerPlayerEntity player, LivingEntity target) {
		Optional<AlienDefinition> alien = TransformationManager.activeDefinition(player);
		if (alien.isEmpty()) {
			return;
		}
		for (AbilitySlot.Trigger trigger : ATTACK_ORDER) {
			if (matches(player, target, trigger) && fire(player, alien.get(), trigger, target)) {
				return;
			}
		}
	}

	private static boolean matches(ServerPlayerEntity player, LivingEntity target, AbilitySlot.Trigger trigger) {
		return switch (trigger) {
			case FINISHER -> target.getHealth() <= target.getMaxHealth() * FINISHER_HEALTH;
			case AIR_ATTACK -> !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater();
			case SPRINT_ATTACK -> player.isSprinting();
			case SNEAK_ATTACK -> player.isSneaking();
			default -> false;
		};
	}

	/** Erste bereite Kombo mit diesem Ausloeser wirken; true, wenn eine gewirkt hat. */
	private static boolean fire(ServerPlayerEntity player, AlienDefinition alien, AbilitySlot.Trigger trigger, LivingEntity target) {
		for (int index : alien.comboIndices()) {
			if (alien.abilities().get(index).trigger() == trigger
					&& TransformationManager.runAbility(player, index, target) == TransformationManager.Result.SUCCESS) {
				return true;
			}
		}
		return false;
	}

	/** Sturzangriff: Schleichen beginnt hoch in der Luft (nicht im Flug — dort heisst Schleichen „sinken“). */
	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			boolean sneaking = player.isSneaking();
			boolean started = sneaking && !SNEAKING.contains(player.getUuid());
			if (sneaking) {
				SNEAKING.add(player.getUuid());
			} else {
				SNEAKING.remove(player.getUuid());
			}
			if (started && !player.isOnGround() && !player.getAbilities().flying && !player.isTouchingWater()
					&& player.fallDistance >= PLUNGE_FALL) {
				TransformationManager.activeDefinition(player).ifPresent(alien -> fire(player, alien, AbilitySlot.Trigger.PLUNGE, null));
			}
		}
		SNEAKING.removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
	}
}
