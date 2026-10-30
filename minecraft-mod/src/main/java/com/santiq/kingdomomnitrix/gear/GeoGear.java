package com.santiq.kingdomomnitrix.gear;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Aeltere Ausruestung (Keyblades, Ratchet-Gadgets) als GeckoLib-Item: Modell, Textur, Leuchtmaske und Leerlauf-Animation
 * liegen unter geo|textures|animations/item/gear/&lt;item-id&gt; (tools/generate_weapon_models.py). Die Item-Klassen
 * behalten ihre eigene Oberklasse und holen sich die GeckoLib-Anbindung ueber dieses Interface.
 */
public interface GeoGear extends GeoItem {
	/** Vom Client gesetzt: baut den Renderer fuer ein Ausruestungsteil. Auf dem Server bleibt es leer. */
	final class Hooks {
		@Nullable
		public static Function<Item, GeoRenderProvider> renderer;
		private static final Map<GeoGear, AnimatableInstanceCache> CACHES = new IdentityHashMap<>();

		private Hooks() {
		}
	}

	/** Registry-Pfad des Items (z. B. "kingdom_key"), Grundlage aller Dateinamen. */
	default String gearId() {
		return Registries.ITEM.getId((Item) this).getPath();
	}

	@Override
	default void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		if (Hooks.renderer != null) {
			consumer.accept(Hooks.renderer.apply((Item) this));
		}
	}

	@Override
	default void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		RawAnimation idle = RawAnimation.begin().thenLoop("animation.kingdomomnitrix.gear." + gearId() + ".idle");
		controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(idle)));
	}

	@Override
	default AnimatableInstanceCache getAnimatableInstanceCache() {
		synchronized (Hooks.CACHES) {
			return Hooks.CACHES.computeIfAbsent(this, GeckoLibUtil::createInstanceCache);
		}
	}
}
