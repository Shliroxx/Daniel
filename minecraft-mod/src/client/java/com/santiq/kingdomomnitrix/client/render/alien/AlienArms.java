package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Ego-Sicht-Arme verwandelter Spieler: Statt der Spieler-Skin wird {@code textures/entity/alien/<alien>_arms.png}
 * (Spieler-Skin-Layout, vom Alien-Generator erzeugt) verwendet. Aliens ohne Arm-Textur behalten die Skin.
 */
public final class AlienArms {
	private static final Map<Identifier, Optional<Identifier>> CACHE = new HashMap<>();

	private AlienArms() {
	}

	/** Bei jedem Ressourcen-Neuladen leeren (gemeinsam mit den Alien-Renderern). */
	static void clearCache() {
		CACHE.clear();
	}

	public static Optional<Identifier> armTexture(PlayerEntity player) {
		TransformationState state = TransformationManager.get(player);
		if (!state.isTransformed()) {
			return Optional.empty();
		}
		Optional<Identifier> model = state.activeAlien()
				.flatMap(id -> AlienRegistry.get(player.getWorld().getRegistryManager(), id))
				.map(AlienDefinition::model);
		return model.flatMap(m -> CACHE.computeIfAbsent(m, AlienArms::lookup));
	}

	private static Optional<Identifier> lookup(Identifier model) {
		Identifier texture = Identifier.of(model.getNamespace(), "textures/entity/alien/" + model.getPath() + "_arms.png");
		return MinecraftClient.getInstance().getResourceManager().getResource(texture).isPresent() ? Optional.of(texture) : Optional.empty();
	}
}
