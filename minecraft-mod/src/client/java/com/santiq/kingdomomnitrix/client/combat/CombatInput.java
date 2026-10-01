package com.santiq.kingdomomnitrix.client.combat;

import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.combat.ComboWeapon;
import com.santiq.kingdomomnitrix.networking.ComboAttackPayload;
import com.santiq.kingdomomnitrix.networking.DodgePayload;
import com.santiq.kingdomomnitrix.networking.GuardPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Kampfeingaben: Linksklick (kurz = Combo, halten = schwerer Angriff), Ausweichen, Blocken, Lock-On.
 * Mit einer {@link ComboWeapon} in der Hand ersetzt das den normalen Minecraft-Angriff
 * (siehe {@code MinecraftClientMixin}).
 */
public final class CombatInput {
	/** Ab so vielen Ticks Halten wird aus dem Linksklick ein schwerer Angriff. */
	private static final int HEAVY_HOLD_TICKS = 8;

	private static int attackHeldTicks;
	private static boolean heavyFired;
	private static boolean guardSent;

	private CombatInput() {
	}

	public static boolean holdsComboWeapon(MinecraftClient client) {
		return client.player != null && client.player.getMainHandStack().getItem() instanceof ComboWeapon;
	}

	public static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		boolean free = client.currentScreen == null;
		boolean comboWeapon = holdsComboWeapon(client);

		handleAttack(client, free && comboWeapon);
		handleGuard(free && comboWeapon && ModKeyBindings.GUARD.isPressed());

		while (ModKeyBindings.DODGE.wasPressed()) {
			if (free) {
				dodge(player);
			}
		}
		while (ModKeyBindings.LOCK_ON.wasPressed()) {
			if (free) {
				ClientLockOn.onKeyPressed(client);
			}
		}
		ClientLockOn.tick(client);
	}

	private static void handleAttack(MinecraftClient client, boolean active) {
		boolean pressed = active && client.options.attackKey.isPressed();
		if (pressed) {
			attackHeldTicks++;
			if (!heavyFired && attackHeldTicks >= HEAVY_HOLD_TICKS) {
				heavyFired = true;
				ClientPlayNetworking.send(new ComboAttackPayload(true));
			}
			return;
		}
		if (attackHeldTicks > 0 && !heavyFired && active) {
			ClientPlayNetworking.send(new ComboAttackPayload(false));
		}
		attackHeldTicks = 0;
		heavyFired = false;
	}

	private static void handleGuard(boolean wanted) {
		if (wanted != guardSent) {
			guardSent = wanted;
			ClientPlayNetworking.send(new GuardPayload(wanted));
		}
	}

	/** Richtung aus den Bewegungstasten in Weltkoordinaten; ohne Eingabe nach hinten. */
	private static void dodge(ClientPlayerEntity player) {
		float forward = player.input.movementForward;
		float sideways = player.input.movementSideways;
		if (Math.abs(forward) < 0.01f && Math.abs(sideways) < 0.01f) {
			forward = -1.0f;
		}
		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		float sin = MathHelper.sin(yaw);
		float cos = MathHelper.cos(yaw);
		float x = sideways * cos - forward * sin;
		float z = forward * cos + sideways * sin;
		ClientPlayNetworking.send(new DodgePayload(x, z));
	}

	/** Beim Verlassen der Welt. */
	public static void reset() {
		attackHeldTicks = 0;
		heavyFired = false;
		guardSent = false;
	}
}
