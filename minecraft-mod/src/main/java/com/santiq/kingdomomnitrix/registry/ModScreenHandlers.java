package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.gadget.GadgetScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;

/** Eigene Menues mit Server-Inventar. */
public final class ModScreenHandlers {
	public static final ScreenHandlerType<GadgetScreenHandler> GADGET_BELT = Registry.register(Registries.SCREEN_HANDLER,
			KingdomOmnitrix.id("gadget_belt"), new ScreenHandlerType<>(GadgetScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

	private ModScreenHandlers() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Menues registriert: {}", Registries.SCREEN_HANDLER.getId(GADGET_BELT));
	}
}
