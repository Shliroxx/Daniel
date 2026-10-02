package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import com.santiq.kingdomomnitrix.client.vfx.CameraShake;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * Zeichnet verwandelte Spieler mit ihrem Alien-Koerper statt dem Spielermodell.
 *
 * <p>Modell-Dateien pro Alien ({@code model} im Alien-JSON, z. B. {@code kingdomomnitrix:heatblast}):
 * {@code geo/entity/alien/heatblast.geo.json}, {@code animations/entity/alien/heatblast.animation.json},
 * {@code textures/entity/alien/heatblast.png}, optional {@code heatblast_glowmask.png} (leuchtende Pixel).
 * Fehlt etwas oder stuerzt das Rendern ab, wird das Modell fuer den Rest der Sitzung uebersprungen und der normale
 * Spieler gezeichnet — kein Absturz.</p>
 *
 * <p>Verwandlungs-Ablauf: Der Koerper waechst ueber die {@code transform}-Animation aus dem Omnitrix-Blitz und leuchtet
 * in den ersten Ticks voll; nach dem Zurueckverwandeln laeuft noch kurz {@code revert} (der Koerper schrumpft weg),
 * bevor das Spielermodell erscheint.</p>
 */
public final class AlienBodyRenderers {
	private static final Map<Identifier, GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable>> RENDERERS = new HashMap<>();
	private static final Set<Identifier> FAILED = new HashSet<>();
	/** zuletzt gesehenes Alien-Modell je Spieler (Entity-ID) */
	private static final Map<Integer, Identifier> ACTIVE = new HashMap<>();
	/** laufende Rueckverwandlung je Spieler: Modell und Start-Tick */
	private static final Map<Integer, Revert> REVERTING = new HashMap<>();
	/** Ticks nach Verwandlungsbeginn, in denen der Koerper voll leuchtet und abklingt */
	private static final int GLOW_TICKS = 12;
	/** Aufprall der Verwandlung (Koerper landet nach dem Wachsen) */
	private static final int IMPACT_TICK = 6;
	private static EntityRendererFactory.Context context;

	private record Revert(Identifier model, long start) {
	}

	private AlienBodyRenderers() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AlienBodyRenderers::tick);
	}

	/** Wird bei jedem Neuladen der Renderer aufgerufen (Ressourcen-Reload, F3+T). */
	public static void onRendererReload(EntityRendererFactory.Context newContext) {
		context = newContext;
		RENDERERS.clear();
		FAILED.clear();
		AlienArms.clearCache();
	}

	/** Verfolgt Verwandlungs-Wechsel aller sichtbaren Spieler (fuer Rueckverwandlung und Kamera-Stoss). */
	private static void tick(MinecraftClient client) {
		if (client.world == null) {
			ACTIVE.clear();
			REVERTING.clear();
			return;
		}
		long now = client.world.getTime();
		Set<Integer> seen = new HashSet<>();
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			seen.add(player.getId());
			Optional<Identifier> model = modelOf(player);
			Identifier previous = ACTIVE.get(player.getId());
			if (model.isPresent()) {
				if (previous == null && player == client.player && now - TransformationManager.get(player).startTick() < IMPACT_TICK) {
					CameraShake.start(0.9f, 10, IMPACT_TICK);
				}
				ACTIVE.put(player.getId(), model.get());
				REVERTING.remove(player.getId());
			} else if (previous != null) {
				ACTIVE.remove(player.getId());
				REVERTING.put(player.getId(), new Revert(previous, now));
				if (player == client.player) {
					CameraShake.start(0.5f, 6, 0);
				}
			}
		}
		ACTIVE.keySet().retainAll(seen);
		REVERTING.entrySet().removeIf(e -> !seen.contains(e.getKey()) || now - e.getValue().start() > AlienBodyAnimatable.REVERT_TICKS);
	}

	static boolean isReverting(PlayerEntity player) {
		return REVERTING.containsKey(player.getId()) && !TransformationManager.get(player).isTransformed();
	}

	private static Optional<Identifier> modelOf(PlayerEntity player) {
		TransformationState state = TransformationManager.get(player);
		if (!state.isTransformed()) {
			return Optional.empty();
		}
		return state.activeAlien()
				.flatMap(id -> AlienRegistry.get(player.getWorld().getRegistryManager(), id))
				.map(AlienDefinition::model);
	}

	/** @return true, wenn ein Alien-Koerper gezeichnet wurde und das Spielermodell entfallen soll */
	public static boolean render(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		if (context == null) {
			return false;
		}
		TransformationState state = TransformationManager.get(player);
		Identifier model;
		int renderLight = light;
		if (state.isTransformed()) {
			Optional<Identifier> active = modelOf(player);
			if (active.isEmpty()) {
				return false;
			}
			model = active.get();
			renderLight = transformLight(light, player.getWorld().getTime() - state.startTick(), tickDelta);
		} else {
			Revert revert = REVERTING.get(player.getId());
			if (revert == null) {
				return false;
			}
			model = revert.model();
			renderLight = transformLight(light, player.getWorld().getTime() - revert.start() + GLOW_TICKS / 2, tickDelta);
		}
		if (FAILED.contains(model)) {
			return false;
		}
		MatrixStack.Entry saved = matrices.peek();
		try {
			RENDERERS.computeIfAbsent(model, AlienBodyRenderers::create).render(player, yaw, tickDelta, matrices, vertexConsumers, renderLight);
			return true;
		} catch (RuntimeException e) {
			// Abbruch mitten im Zeichnen laesst Matrizen auf dem Stapel — aufraeumen, sonst bricht der ganze Frame ab
			while (!matrices.isEmpty() && matrices.peek() != saved) {
				matrices.pop();
			}
			FAILED.add(model);
			KingdomOmnitrix.LOGGER.error("Alien-Koerper {} konnte nicht gezeichnet werden, nutze Spielermodell", model, e);
			return false;
		}
	}

	/** Volle Helligkeit direkt nach dem Blitz, klingt ueber {@link #GLOW_TICKS} auf das normale Licht ab. */
	private static int transformLight(int light, long age, float tickDelta) {
		float t = (age + tickDelta) / GLOW_TICKS;
		if (t >= 1.0f || t < 0.0f) {
			return light;
		}
		int block = LightmapTextureManager.getBlockLightCoordinates(light);
		int sky = LightmapTextureManager.getSkyLightCoordinates(light);
		int boosted = Math.round(MathHelper.lerp(t * t, 15.0f, block));
		return LightmapTextureManager.pack(Math.max(block, boosted), Math.max(sky, Math.round(MathHelper.lerp(t * t, 15.0f, sky))));
	}

	private static GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable> create(Identifier model) {
		Identifier assetPath = Identifier.of(model.getNamespace(), "alien/" + model.getPath());
		AlienBodyRenderer renderer = new AlienBodyRenderer(context, new DefaultedEntityGeoModel<>(assetPath, true));
		float scale = renderScale(model);
		if (scale != 1.0f) {
			renderer.withScale(scale);
		}
		Identifier glowmask = Identifier.of(model.getNamespace(), "textures/entity/alien/" + model.getPath() + "_glowmask.png");
		if (MinecraftClient.getInstance().getResourceManager().getResource(glowmask).isPresent()) {
			renderer.withEvenGlow();
		}
		return renderer;
	}

	/** Darstellungsgroesse aus {@code alien_render/<name>.json} ({"scale": 0.84}); fehlt die Datei: 1. */
	private static float renderScale(Identifier model) {
		Identifier file = Identifier.of(model.getNamespace(), "alien_render/" + model.getPath() + ".json");
		var resource = MinecraftClient.getInstance().getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return 1.0f;
		}
		try (var reader = resource.get().getReader()) {
			float scale = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().get("scale").getAsFloat();
			return scale > 0.05f && scale < 5.0f ? scale : 1.0f;
		} catch (java.io.IOException | RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("Darstellungsgroesse {} unlesbar, nutze 1", file, e);
			return 1.0f;
		}
	}
}
