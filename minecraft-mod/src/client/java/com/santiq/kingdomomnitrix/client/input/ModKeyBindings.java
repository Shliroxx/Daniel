package com.santiq.kingdomomnitrix.client.input;

import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.client.screen.OmnitrixWheelScreen;
import com.santiq.kingdomomnitrix.networking.AbilityRequestPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Tastenbelegung (in den Minecraft-Steuerungsoptionen unter "Kingdom Omnitrix" aenderbar). */
public final class ModKeyBindings {
	public static final String CATEGORY = "category.kingdomomnitrix";

	public static final KeyBinding OPEN_OMNITRIX = register("key.kingdomomnitrix.omnitrix", GLFW.GLFW_KEY_G);
	public static final KeyBinding[] ABILITIES = {
			register("key.kingdomomnitrix.ability_1", GLFW.GLFW_KEY_R),
			register("key.kingdomomnitrix.ability_2", GLFW.GLFW_KEY_V),
			register("key.kingdomomnitrix.ability_3", GLFW.GLFW_KEY_B),
	};

	private ModKeyBindings() {
	}

	private static KeyBinding register(String translationKey, int defaultKey) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, defaultKey, CATEGORY));
	}

	public static void register() {
		if (ABILITIES.length != AlienDefinition.MAX_ABILITIES) {
			throw new IllegalStateException("Anzahl der Faehigkeiten-Tasten passt nicht zu MAX_ABILITIES");
		}
		ClientTickEvents.END_CLIENT_TICK.register(ModKeyBindings::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		while (OPEN_OMNITRIX.wasPressed()) {
			if (client.currentScreen == null && OmnitrixItem.hasOmnitrix(client.player)) {
				OmnitrixWheelScreen.open(client);
			}
		}
		for (int slot = 0; slot < ABILITIES.length; slot++) {
			while (ABILITIES[slot].wasPressed()) {
				if (client.currentScreen == null && TransformationManager.get(client.player).isTransformed()) {
					ClientPlayNetworking.send(new AbilityRequestPayload(slot));
				}
			}
		}
	}
}
