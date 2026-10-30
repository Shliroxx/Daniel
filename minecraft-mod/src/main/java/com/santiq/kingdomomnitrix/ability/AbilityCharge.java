package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/** Laufende Aufladungen (je Spieler hoechstens eine). */
public final class AbilityCharge {
	private static final Map<UUID, Charge> CHARGES = new HashMap<>();

	private record Charge(int slot, ChargeableAbility ability, AbilityContext context, long start) {
		int max() {
			return Math.max(1, ability.chargeTicks(context));
		}
	}

	private AbilityCharge() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AbilityCharge::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CHARGES.remove(handler.getPlayer().getUuid()));
	}

	public static boolean charging(ServerPlayerEntity player) {
		return CHARGES.containsKey(player.getUuid());
	}

	/** Beginnt das Aufladen (Pruefungen hat {@link TransformationManager#useAbility} schon gemacht). */
	public static void begin(ServerPlayerEntity player, int slot, ChargeableAbility ability, AbilityContext context) {
		CHARGES.put(player.getUuid(), new Charge(slot, ability, context, player.getServerWorld().getTime()));
		if (ability.channel()) {
			return;
		}
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.5f, 0.6f);
	}

	/** Taste losgelassen. */
	public static void release(ServerPlayerEntity player, int slot) {
		Charge charge = CHARGES.get(player.getUuid());
		if (charge != null && charge.slot() == slot) {
			fire(player, charge);
		}
	}

	private static void fire(ServerPlayerEntity player, Charge charge) {
		CHARGES.remove(player.getUuid());
		long held = player.getServerWorld().getTime() - charge.start();
		float amount = Math.min(1.0f, held / (float) charge.max());
		boolean worked;
		try {
			worked = charge.ability().release(charge.context(), amount);
		} catch (RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("Aufgeladene Faehigkeit in Slot {} ist abgestuerzt", charge.slot(), e);
			return;
		}
		if (worked) {
			TransformationManager.commitAbility(player, charge.slot(), charge.context());
		}
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Charge> entry = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			Charge charge = entry.getValue();
			if (player == null || !player.isAlive() || !TransformationManager.get(player).isTransformed()
					|| !charge.context().alienId().equals(TransformationManager.get(player).activeAlien().orElse(null))) {
				it.remove();
				continue;
			}
			int held = (int) (player.getServerWorld().getTime() - charge.start());
			float amount = Math.min(1.0f, held / (float) charge.max());
			charge.ability().charging(charge.context(), held, amount);
			if (!charge.ability().channel() && held % 5 == 0) {
				player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS,
						0.25f, 0.5f + amount * 1.3f);
			}
			if (!charge.ability().channel() && held == charge.max()) {
				Fx.flash(player.getServerWorld(), player.getPos().add(0, player.getHeight() * 0.6, 0), 0xFFFFFF, 1.5f + player.getWidth());
				player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.6f);
			}
			if (held >= charge.max() + ChargeableAbility.HOLD_LIMIT) {
				it.remove();
				CHARGES.put(entry.getKey(), charge);   // fire() entfernt selbst
				fire(player, charge);
				return;   // Map wurde veraendert; Rest im naechsten Tick
			}
		}
	}
}
