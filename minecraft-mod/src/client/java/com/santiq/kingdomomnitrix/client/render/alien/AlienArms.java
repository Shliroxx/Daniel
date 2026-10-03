package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.AlienUniforms;
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
	private static final Map<String, Optional<Identifier>> CACHE = new HashMap<>();

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
		Optional<Identifier> alien = state.activeAlien();
		Optional<Identifier> model = alien.flatMap(id -> AlienRegistry.get(player.getWorld().getRegistryManager(), id))
				.map(AlienDefinition::model);
		String uniform = alien.map(id -> AlienUniforms.get(player, id)).orElse(AlienUniforms.CLASSIC);
		return model.flatMap(m -> CACHE.computeIfAbsent(m + "#" + uniform, key -> lookup(m, uniform)));
	}

	/** Arm-Textur der Uniform ({@code <name>_<uniform>_arms.png}), sonst die classic-Arme. */
	private static Optional<Identifier> lookup(Identifier model, String uniform) {
		var resources = MinecraftClient.getInstance().getResourceManager();
		if (!AlienUniforms.CLASSIC.equals(uniform)) {
			Identifier variant = Identifier.of(model.getNamespace(), "textures/entity/alien/" + model.getPath() + "_" + uniform + "_arms.png");
			if (resources.getResource(variant).isPresent()) {
				return Optional.of(variant);
			}
		}
		Identifier texture = Identifier.of(model.getNamespace(), "textures/entity/alien/" + model.getPath() + "_arms.png");
		return resources.getResource(texture).isPresent() ? Optional.of(texture) : Optional.empty();
	}
}
