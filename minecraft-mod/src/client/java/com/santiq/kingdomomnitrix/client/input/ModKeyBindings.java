package com.santiq.kingdomomnitrix.client.input;

import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.client.hero.HeroScreen;
import com.santiq.kingdomomnitrix.client.screen.OmnitrixWheelScreen;
import com.santiq.kingdomomnitrix.networking.AbilityRequestPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
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
	public static final KeyBinding DODGE = register("key.kingdomomnitrix.dodge", GLFW.GLFW_KEY_LEFT_ALT);
	public static final KeyBinding GUARD = register("key.kingdomomnitrix.guard", GLFW.GLFW_KEY_CAPS_LOCK);
	public static final KeyBinding LOCK_ON = register("key.kingdomomnitrix.lock_on", GLFW.GLFW_KEY_Z);
	public static final KeyBinding MAGIC = register("key.kingdomomnitrix.magic", GLFW.GLFW_KEY_M);
	public static final KeyBinding GADGET_BELT = register("key.kingdomomnitrix.gadget_belt", GLFW.GLFW_KEY_H);
	public static final KeyBinding USE_GADGET = register("key.kingdomomnitrix.use_gadget", GLFW.GLFW_KEY_Y);
	public static final KeyBinding PACK_MODE = register("key.kingdomomnitrix.pack_mode", GLFW.GLFW_KEY_J);
	public static final KeyBinding HERO_MENU = register("key.kingdomomnitrix.hero_menu", GLFW.GLFW_KEY_K);

	private static final KeyBinding[] OWN = {OPEN_OMNITRIX, ABILITIES[0], ABILITIES[1], ABILITIES[2], DODGE, GUARD, LOCK_ON, MAGIC,
			GADGET_BELT, USE_GADGET, PACK_MODE, HERO_MENU};
	private static boolean conflictsChecked;

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
		if (!conflictsChecked) {
			conflictsChecked = true;
			warnAboutConflicts(client);
		}
		while (OPEN_OMNITRIX.wasPressed()) {
			if (client.currentScreen == null && OmnitrixItem.hasOmnitrix(client.player)) {
				OmnitrixWheelScreen.open(client);
			}
		}
		while (HERO_MENU.wasPressed()) {
			if (client.currentScreen == null) {
				HeroScreen.open(client);
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

	/**
	 * Minecraft leitet einen Tastendruck nur an EINE Belegung weiter. Doppelt belegte Tasten fuehren dazu,
	 * dass eine der beiden Funktionen stumm bleibt; darum wird das im Log und im Chat gemeldet.
	 */
	private static void warnAboutConflicts(MinecraftClient client) {
		for (KeyBinding own : OWN) {
			if (own.isUnbound()) {
				continue;
			}
			for (KeyBinding other : client.options.allKeys) {
				if (other != own && own.equals(other)) {
					KingdomOmnitrix.LOGGER.warn("Taste {} ist doppelt belegt: {} und {}", own.getBoundKeyLocalizedText().getString(),
							own.getTranslationKey(), other.getTranslationKey());
					client.player.sendMessage(Text.translatable("message.kingdomomnitrix.key_conflict", own.getBoundKeyLocalizedText(),
							Text.translatable(own.getTranslationKey()), Text.translatable(other.getTranslationKey())).formatted(Formatting.YELLOW), false);
				}
			}
		}
	}
}
