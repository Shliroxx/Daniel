package com.santiq.kingdomomnitrix.client.magic;

import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.keyblade.KeybladeItem;
import com.santiq.kingdomomnitrix.magic.MagicManager;
import com.santiq.kingdomomnitrix.magic.SpellRegistry;
import com.santiq.kingdomomnitrix.networking.SelectSpellPayload;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Zauberwahl: Magie-Taste halten und Mausrad drehen; kurzes Tippen ohne Mausrad schaltet zum naechsten Zauber.
 * Waehrend die Taste gehalten wird, bewegt das Mausrad nicht die Hotbar (siehe {@code MouseMixin}).
 */
public final class MagicInput {
	private static boolean scrolledWhileHeld;
	private static boolean wasHeld;

	private MagicInput() {
	}

	private static boolean active(MinecraftClient client) {
		return client.player != null && client.currentScreen == null
				&& client.player.getMainHandStack().getItem() instanceof KeybladeItem;
	}

	public static void tick(MinecraftClient client) {
		boolean held = active(client) && ModKeyBindings.MAGIC.isPressed();
		if (wasHeld && !held && !scrolledWhileHeld && active(client)) {
			cycle(client, 1);
		}
		if (!held) {
			scrolledWhileHeld = false;
		}
		wasHeld = held;
		// wasPressed-Zaehler leeren, damit er nicht anwaechst
		while (ModKeyBindings.MAGIC.wasPressed()) {
			// Auswertung erfolgt ueber isPressed/Loslassen
		}
	}

	/** @return true, wenn das Mausrad fuer die Zauberwahl verbraucht wurde */
	public static boolean onScroll(double vertical) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!active(client) || !ModKeyBindings.MAGIC.isPressed() || vertical == 0) {
			return false;
		}
		scrolledWhileHeld = true;
		cycle(client, vertical > 0 ? -1 : 1);
		return true;
	}

	private static void cycle(MinecraftClient client, int direction) {
		if (client.player == null || client.world == null) {
			return;
		}
		List<Identifier> spells = SpellRegistry.sortedIds(client.world.getRegistryManager());
		if (spells.isEmpty()) {
			return;
		}
		Optional<Identifier> current = MagicManager.selectedSpell(client.player);
		int index = current.map(spells::indexOf).orElse(-1);
		Identifier next = spells.get(Math.floorMod(index + direction, spells.size()));
		ClientPlayNetworking.send(new SelectSpellPayload(next));
		MagicHud.flashSelection(next);
	}

	public static void reset() {
		scrolledWhileHeld = false;
		wasHeld = false;
	}
}
