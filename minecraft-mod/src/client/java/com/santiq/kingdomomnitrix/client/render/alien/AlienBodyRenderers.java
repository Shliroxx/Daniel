package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.AlienUniforms;
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
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
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
	/** Uniform beim Verwandeln/Zurueckverwandeln je Spieler (die Rueckverwandlung zeichnet noch das alte Alien) */
	private static final Map<Integer, String> LAST_UNIFORM = new HashMap<>();
	private static final Map<Integer, String> REVERT_UNIFORM = new HashMap<>();
	/** Ticks nach Verwandlungsbeginn, in denen der Koerper voll leuchtet und abklingt */
	private static final int GLOW_TICKS = 12;
	/** Aufprall der Verwandlung (Koerper landet nach dem Wachsen) */
	private static final int IMPACT_TICK = 6;
	/** Aufraeumen der Animationsdaten alle 5 s */
	private static final int PRUNE_INTERVAL = 100;
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
		INFO.clear();
		BadgeTint.clear();
		TEXTURE_VARIANTS.clear();
		AlienPose.reload(newContext);
		AlienArms.clearCache();
	}

	/** Verfolgt Verwandlungs-Wechsel aller sichtbaren Spieler (fuer Rueckverwandlung und Kamera-Stoss). */
	private static void tick(MinecraftClient client) {
		if (client.world == null) {
			ACTIVE.clear();
			REVERTING.clear();
			if (!RENDERERS.isEmpty()) {
				retainAnimationData(Set.of());
			}
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
		REVERT_UNIFORM.keySet().retainAll(REVERTING.keySet());
		LAST_UNIFORM.keySet().retainAll(seen);
		if (now % PRUNE_INTERVAL == 0) {
			retainAnimationData(seen);
		}
	}

	/** Animationsdaten verschwundener Spieler in allen Alien-Renderern verwerfen. */
	private static void retainAnimationData(Set<Integer> players) {
		for (GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable> renderer : RENDERERS.values()) {
			renderer.getAnimatable().retain(players);
		}
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
		currentAge = player.age;
		long remaining = state.remainingTicks(player.getWorld().getTime());
		currentWarn = state.isTransformed() && remaining > 0 && remaining <= WARN_TICKS
				&& (player.age / WARN_BLINK) % 2 == 0;
		currentBadgeColor = com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors.primary(player);
		currentUniform = state.activeAlien().map(id -> AlienUniforms.get(player, id))
				.or(() -> Optional.ofNullable(REVERT_UNIFORM.get(player.getId())))
				.orElse(AlienUniforms.CLASSIC);
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
		AlienBodyRenderer renderer = new AlienBodyRenderer(context, new DefaultedEntityGeoModel<>(assetPath, true) {
			@Override
			public Identifier getTextureResource(AlienBodyAnimatable animatable) {
				return BadgeTint.tint(animatedTexture(model, uniformTexture(model, super.getTextureResource(animatable), currentUniform)),
						currentBadgeColor);
			}

			@Override
			public Identifier getModelResource(AlienBodyAnimatable animatable) {
				return uniformModel(model, super.getModelResource(animatable), currentUniform);
			}

			@Override
			public void setCustomAnimations(AlienBodyAnimatable animatable, long instanceId, AnimationState<AlienBodyAnimatable> state) {
				AlienPose.Info info = poseOf(model);
				if (info != null && state.getData(DataTickets.ENTITY) instanceof AbstractClientPlayerEntity player) {
					// wie AE: Koerper folgt der Spielerpose (inkl. Kopf) — die Standard-Kopfdrehung entfaellt
					AlienPose.apply(getAnimationProcessor(), player, state.getPartialTick(), info);
				} else {
					super.setCustomAnimations(animatable, instanceId, state);
				}
			}
		});
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

	private static final Map<Identifier, AlienRenderInfo> INFO = new HashMap<>();
	private static final Map<Identifier, Identifier[]> TEXTURE_VARIANTS = new HashMap<>();
	/** Abzeichen blinkt rot in den letzten 10 s (Serien-Piepen kommt vom Server) */
	public static final int WARN_TICKS = 200;
	private static final int WARN_BLINK = 5;
	private static long currentAge;
	private static boolean currentWarn;
	/** Uniform des gerade gezeichneten Spielers (Zeichnen laeuft im Render-Thread nacheinander) */
	private static String currentUniform = AlienUniforms.CLASSIC;
	/** Farbmodul des gerade gezeichneten Spielers (Abzeichen-Farbe) */
	private static int currentBadgeColor = BadgeTint.CLASSIC;

	/** Darstellungs-Angaben eines Modells (einmal pro Ressourcen-Neuladen gelesen). */
	public static AlienRenderInfo info(Identifier model) {
		return INFO.computeIfAbsent(model, AlienRenderInfo::load);
	}

	/**
	 * Textur einer Uniform: {@code <name>_<uniform>.png}, wenn das Modell die Uniform in {@code alien_render} fuehrt,
	 * sonst die Grundtextur (classic).
	 */
	public static Identifier uniformTexture(Identifier model, Identifier base, String uniform) {
		if (AlienUniforms.CLASSIC.equals(uniform) || !info(model).hasUniform(uniform)) {
			return base;
		}
		String path = base.getPath();
		return Identifier.of(base.getNamespace(), path.substring(0, path.length() - 4) + "_" + uniform + ".png");
	}

	/**
	 * Geometrie einer Uniform: {@code <name>_<uniform>.geo.json}, wenn {@code alien_render} {@code "uniform_models": true}
	 * setzt (importierte Alien-Evolution-Modelle haben je Uniform eigene Geometrie), sonst die Grundgeometrie.
	 */
	public static Identifier uniformModel(Identifier model, Identifier base, String uniform) {
		AlienRenderInfo info = info(model);
		if (AlienUniforms.CLASSIC.equals(uniform) || !info.hasUniform(uniform) || !info.uniformModels()) {
			return base;
		}
		String path = base.getPath();
		return Identifier.of(base.getNamespace(), path.substring(0, path.length() - ".geo.json".length()) + "_" + uniform + ".geo.json");
	}

	/**
	 * Pose-Angaben (Schwung-Faktoren, Faehigkeits-Posen), wenn das Modell der Spielerpose folgt
	 * ({@code "vanilla_pose": true} in {@code alien_render}), sonst {@code null} (eigene Animationen fuer alle Knochen).
	 */
	static AlienPose.Info poseOf(Identifier model) {
		return info(model).pose();
	}

	/**
	 * Glut-Frame und Warnzustand: {@code <textur>_f<i>.png} (alle 2 Ticks, wie AE) und {@code _warn} (Abzeichen rot,
	 * blinkt in den letzten {@link #WARN_TICKS} Ticks vor dem Zeitablauf), wenn {@code alien_render} sie ankuendigt.
	 */
	static Identifier animatedTexture(Identifier model, Identifier texture) {
		AlienRenderInfo info = info(model);
		int frame = info.glowFrames() > 1 ? (int) ((currentAge / 2) % info.glowFrames()) : 0;
		boolean warn = info.warnTextures() && currentWarn;
		if (frame == 0 && !warn) {
			return texture;
		}
		// Namen je Textur einmal vorbereiten statt jedes Bild neue Identifier zu bauen
		Identifier[] variants = TEXTURE_VARIANTS.computeIfAbsent(texture, base -> {
			String path = base.getPath().substring(0, base.getPath().length() - 4);
			Identifier[] all = new Identifier[info.glowFrames() * 2];
			for (int f = 0; f < info.glowFrames(); f++) {
				for (int w = 0; w < 2; w++) {
					all[f * 2 + w] = f == 0 && w == 0 ? base : Identifier.of(base.getNamespace(),
							path + (f > 0 ? "_f" + f : "") + (w == 1 ? "_warn" : "") + ".png");
				}
			}
			return all;
		});
		int index = frame * 2 + (warn ? 1 : 0);
		return index < variants.length ? variants[index] : texture;
	}

	/** Uniformen eines Modells laut {@code alien_render/<name>.json} ({"uniforms": ["classic", "evo", …]}). */
	public static java.util.List<String> uniformsOf(Identifier model) {
		return info(model).uniforms();
	}

	/** Darstellungsgroesse aus {@code alien_render/<name>.json} ({"scale": 0.84}); fehlt die Datei: 1. */
	public static float renderScale(Identifier model) {
		return info(model).scale();
	}
}
