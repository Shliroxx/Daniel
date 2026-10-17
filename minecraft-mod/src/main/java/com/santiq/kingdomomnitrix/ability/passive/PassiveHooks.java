package com.santiq.kingdomomnitrix.ability.passive;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.PassiveSlot;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Verteilt Spiel-Ereignisse an die passiven Kraefte des aktiven Aliens: Tick, Schaden austeilen/nehmen (ueber
 * {@code LivingEntityDamageMixin}), Treffer, Treffer erhalten, Sieg. Merkt sich das aktive Alien je Spieler, damit
 * beim Ende der Verwandlung {@link AlienPassive#end} genau einmal laeuft.
 */
public final class PassiveHooks {
	private static final Map<UUID, Identifier> ACTIVE = new HashMap<>();
	/** Schutz gegen Rekursion, wenn eine passive Kraft selbst Schaden verursacht */
	private static int depth;

	private PassiveHooks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PassiveHooks::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken <= 0.0f) {
				return;
			}
			if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != entity) {
				each(attacker, (passive, ctx) -> passive.afterHit(ctx, entity, source, taken));
			}
			if (entity instanceof ServerPlayerEntity player) {
				each(player, (passive, ctx) -> passive.afterHurt(ctx, source, taken));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != entity) {
				each(attacker, (passive, ctx) -> passive.onKill(ctx, entity));
			}
		});
	}

	/** Vom {@link com.santiq.kingdomomnitrix.ability.ComboManager}: Nahkampfschlag vor dem Schaden. */
	public static void melee(ServerPlayerEntity player, LivingEntity target, boolean fullSwing) {
		each(player, (passive, ctx) -> passive.melee(ctx, target, fullSwing));
	}

	/** Vom Mixin: Schaden nach Ruestung, vor Absorption und Lebenspunkten. */
	public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
		if (depth > 0 || amount <= 0.0f || target.getWorld().isClient()) {
			return amount;
		}
		depth++;
		try {
			float[] value = {amount};
			if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != target) {
				each(attacker, (passive, ctx) -> value[0] = Math.max(0.0f, passive.outgoing(ctx, target, source, value[0])));
			}
			if (target instanceof ServerPlayerEntity player) {
				each(player, (passive, ctx) -> value[0] = Math.max(0.0f, passive.incoming(ctx, source, value[0])));
			}
			return value[0];
		} finally {
			depth--;
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			Identifier now = TransformationManager.get(player).activeAlien().orElse(null);
			Identifier before = ACTIVE.get(player.getUuid());
			if (before != null && !before.equals(now)) {
				endAll(player, before);
			}
			if (now == null) {
				ACTIVE.remove(player.getUuid());
				continue;
			}
			ACTIVE.put(player.getUuid(), now);
			if (player.isAlive()) {
				each(player, AlienPassive::tick);
			}
		}
		ACTIVE.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
	}

	private static void endAll(ServerPlayerEntity player, Identifier alienId) {
		com.santiq.kingdomomnitrix.alien.AlienRegistry.get(player.getServer().getRegistryManager(), alienId).ifPresent(alien -> {
			for (PassiveSlot slot : alien.traits().passives()) {
				PassiveRegistry.get(slot.type()).ifPresent(passive -> safe(slot, () -> passive.end(new PassiveContext(player, alienId, alien, slot))));
			}
		});
	}

	/** Freigeschaltete passive Kraefte des aktiven Aliens (Master Control schaltet alle frei). */
	public static List<PassiveContext> active(ServerPlayerEntity player) {
		Optional<Identifier> alienId = TransformationManager.get(player).activeAlien();
		Optional<AlienDefinition> alien = TransformationManager.activeDefinition(player);
		if (alienId.isEmpty() || alien.isEmpty() || alien.get().traits().passives().isEmpty()) {
			return List.of();
		}
		int mastery = AlienMasteryManager.get(player).level(alienId.get());
		boolean master = OmnitrixCore.state(player).masterControl();
		List<PassiveContext> out = new ArrayList<>();
		for (PassiveSlot slot : alien.get().traits().passives()) {
			if (slot.unlocked(mastery) || master) {
				out.add(new PassiveContext(player, alienId.get(), alien.get(), slot));
			}
		}
		return out;
	}

	private static void each(ServerPlayerEntity player, java.util.function.BiConsumer<AlienPassive, PassiveContext> action) {
		for (PassiveContext ctx : active(player)) {
			PassiveRegistry.get(ctx.slot().type()).ifPresent(passive -> safe(ctx.slot(), () -> action.accept(passive, ctx)));
		}
	}

	private static void safe(PassiveSlot slot, Runnable run) {
		try {
			run.run();
		} catch (RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("Passive Kraft {} ist abgestuerzt", slot.type(), e);
		}
	}
}
