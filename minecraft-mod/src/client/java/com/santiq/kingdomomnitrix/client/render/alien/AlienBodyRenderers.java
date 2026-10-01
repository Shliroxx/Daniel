package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * Zeichnet verwandelte Spieler mit ihrem Alien-Koerper statt dem Spielermodell.
 *
 * <p>Modell-Dateien pro Alien ({@code model} im Alien-JSON, z. B. {@code kingdomomnitrix:heatblast}):
 * {@code geo/entity/alien/heatblast.geo.json}, {@code animations/entity/alien/heatblast.animation.json},
 * {@code textures/entity/alien/heatblast.png}. Fehlt etwas oder stuerzt das Rendern ab, wird das Modell
 * fuer den Rest der Sitzung uebersprungen und der normale Spieler gezeichnet — kein Absturz.</p>
 */
public final class AlienBodyRenderers {
	private static final Map<Identifier, GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable>> RENDERERS = new HashMap<>();
	private static final Set<Identifier> FAILED = new HashSet<>();
	private static EntityRendererFactory.Context context;

	private AlienBodyRenderers() {
	}

	/** Wird bei jedem Neuladen der Renderer aufgerufen (Ressourcen-Reload, F3+T). */
	public static void onRendererReload(EntityRendererFactory.Context newContext) {
		context = newContext;
		RENDERERS.clear();
		FAILED.clear();
	}

	/** @return true, wenn ein Alien-Koerper gezeichnet wurde und das Spielermodell entfallen soll */
	public static boolean render(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		TransformationState state = TransformationManager.get(player);
		if (!state.isTransformed() || context == null) {
			return false;
		}
		Optional<AlienDefinition> alien = state.activeAlien()
				.flatMap(id -> AlienRegistry.get(player.getWorld().getRegistryManager(), id));
		if (alien.isEmpty()) {
			return false;
		}
		Identifier model = alien.get().model();
		if (FAILED.contains(model)) {
			return false;
		}
		try {
			RENDERERS.computeIfAbsent(model, AlienBodyRenderers::create).render(player, yaw, tickDelta, matrices, vertexConsumers, light);
			return true;
		} catch (RuntimeException e) {
			FAILED.add(model);
			KingdomOmnitrix.LOGGER.error("Alien-Koerper {} konnte nicht gezeichnet werden, nutze Spielermodell", model, e);
			return false;
		}
	}

	private static GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable> create(Identifier model) {
		Identifier assetPath = Identifier.of(model.getNamespace(), "alien/" + model.getPath());
		return new GeoReplacedEntityRenderer<>(context, new DefaultedEntityGeoModel<>(assetPath, true), new AlienBodyAnimatable());
	}
}
