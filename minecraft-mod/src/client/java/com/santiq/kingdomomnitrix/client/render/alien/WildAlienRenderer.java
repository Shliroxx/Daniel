package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienUniforms;
import com.santiq.kingdomomnitrix.alien.WildAlienEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Wilde Aliens: Modell und Textur der Art (wie die Omnitrix-Form), Erscheinungsbild je Exemplar aus den Uniformen des
 * Modells (klassisch, Evo, Ultimate — wo vorhanden), Groesse aus {@code alien_render} mal Skalierungs-Attribut.
 * Arme und Beine schwingen beim Laufen, der Kopf folgt dem Blick; Leuchtmaske, wo das Modell eine hat.
 */
public class WildAlienRenderer extends GeoEntityRenderer<WildAlienEntity> {
	/** Leuchtmasken-Existenz je Textur (einmal nachgeschlagen) */
	private static final Map<Identifier, Boolean> GLOW = new HashMap<>();

	public WildAlienRenderer(EntityRendererFactory.Context context) {
		super(context, new Model());
		addRenderLayer(new AutoGlowingGeoLayer<>(this) {
			@Override
			public void render(MatrixStack poseStack, WildAlienEntity animatable, BakedGeoModel bakedModel, RenderLayer renderType,
					VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
				if (hasGlowmask(getTextureResource(animatable))) {
					super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
				}
			}
		});
		this.shadowRadius = 0.45f;
	}

	private static boolean hasGlowmask(Identifier texture) {
		return GLOW.computeIfAbsent(texture, t -> {
			String path = t.getPath();
			Identifier mask = Identifier.of(t.getNamespace(), path.substring(0, path.length() - 4) + "_glowmask.png");
			return MinecraftClient.getInstance().getResourceManager().getResource(mask).isPresent();
		});
	}

	@Override
	public void scaleModelForRender(float widthScale, float heightScale, MatrixStack poseStack, WildAlienEntity animatable,
			BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
		float scale = modelOf(animatable) == null ? 1.0f : AlienBodyRenderers.info(modelOf(animatable)).scale() * animatable.getScale();
		super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight,
				packedOverlay);
	}

	/** Modell-ID der Art (z. B. kingdomomnitrix:echo_echo), null solange die Art fehlt. */
	private static Identifier modelOf(WildAlienEntity entity) {
		return entity.definition().map(AlienDefinition::model).orElse(null);
	}

	/** Uniform des Exemplars: aus den Uniformen des Modells nach seiner Variante. */
	private static String uniformOf(WildAlienEntity entity, Identifier model) {
		List<String> uniforms = AlienBodyRenderers.uniformsOf(model);
		return uniforms.isEmpty() ? AlienUniforms.CLASSIC : uniforms.get(Math.floorMod(entity.variant(), uniforms.size()));
	}

	/** GeckoLib 4.9 markiert die Ein-Parameter-Varianten als veraltet, verlangt sie aber weiterhin. */
	@SuppressWarnings("deprecation")
	private static final class Model extends GeoModel<WildAlienEntity> {
		private static final Identifier FALLBACK = Identifier.of("kingdomomnitrix", "heatblast");

		private static Identifier model(WildAlienEntity entity) {
			Identifier model = modelOf(entity);
			return model == null ? FALLBACK : model;
		}

		@Override
		public Identifier getModelResource(WildAlienEntity entity) {
			Identifier model = model(entity);
			Identifier base = Identifier.of(model.getNamespace(), "geo/entity/alien/" + model.getPath() + ".geo.json");
			return AlienBodyRenderers.uniformModel(model, base, uniformOf(entity, model));
		}

		@Override
		public Identifier getTextureResource(WildAlienEntity entity) {
			Identifier model = model(entity);
			Identifier base = Identifier.of(model.getNamespace(), "textures/entity/alien/" + model.getPath() + ".png");
			return AlienBodyRenderers.uniformTexture(model, base, uniformOf(entity, model));
		}

		@Override
		public Identifier getAnimationResource(WildAlienEntity entity) {
			Identifier model = model(entity);
			return Identifier.of(model.getNamespace(), "animations/entity/alien/" + model.getPath() + ".animation.json");
		}

		@Override
		public void setCustomAnimations(WildAlienEntity entity, long instanceId, AnimationState<WildAlienEntity> state) {
			EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
			GeoBone head = getAnimationProcessor().getBone("head");
			if (head != null && data != null) {
				head.setRotX(data.headPitch() * MathHelper.RADIANS_PER_DEGREE);
				head.setRotY(data.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);
			}
			float swing = state.getLimbSwing() * 0.6662f;
			float amount = Math.min(1.0f, state.getLimbSwingAmount());
			swingBone("right_arm", MathHelper.cos(swing + MathHelper.PI) * amount);
			swingBone("left_arm", MathHelper.cos(swing) * amount);
			swingBone("right_leg", MathHelper.cos(swing) * 1.4f * amount);
			swingBone("left_leg", MathHelper.cos(swing + MathHelper.PI) * 1.4f * amount);
			if (entity.handSwingProgress > 0.0f) {
				// Schlag: rechter Arm holt aus
				GeoBone arm = getAnimationProcessor().getBone("right_arm");
				if (arm != null) {
					arm.setRotX(arm.getRotX() + MathHelper.sin(entity.handSwingProgress * MathHelper.PI) * 1.6f);
				}
			}
		}

		private void swingBone(String name, float rotX) {
			GeoBone bone = getAnimationProcessor().getBone(name);
			if (bone != null) {
				bone.setRotX(rotX);
			}
		}
	}
}
