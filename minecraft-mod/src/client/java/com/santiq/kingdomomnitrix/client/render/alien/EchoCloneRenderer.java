package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.EchoCloneEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Echo-Echo-Klon: dasselbe AE-Modell wie der verwandelte Spieler (klassische Uniform), in AE-Groesse 0,5. Arme und Beine
 * schwingen beim Laufen wie beim Spieler, der Kopf folgt dem Blick; Leuchtmaske wie am Spieler.
 */
public class EchoCloneRenderer extends GeoEntityRenderer<EchoCloneEntity> {
	private static final float SCALE = 0.5f;

	public EchoCloneRenderer(EntityRendererFactory.Context context) {
		super(context, new Model());
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
		withScale(SCALE);
		this.shadowRadius = 0.25f;
	}

	/** GeckoLib 4.9 markiert die Ein-Parameter-Varianten als veraltet, verlangt sie aber weiterhin. */
	@SuppressWarnings("deprecation")
	private static final class Model extends GeoModel<EchoCloneEntity> {
		@Override
		public Identifier getModelResource(EchoCloneEntity clone) {
			return KingdomOmnitrix.id("geo/entity/alien/echo_echo.geo.json");
		}

		@Override
		public Identifier getTextureResource(EchoCloneEntity clone) {
			return KingdomOmnitrix.id("textures/entity/alien/echo_echo.png");
		}

		@Override
		public Identifier getAnimationResource(EchoCloneEntity clone) {
			return KingdomOmnitrix.id("animations/entity/alien/echo_echo.animation.json");
		}

		@Override
		public void setCustomAnimations(EchoCloneEntity clone, long instanceId, AnimationState<EchoCloneEntity> state) {
			EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
			GeoBone head = getAnimationProcessor().getBone("head");
			if (head != null && data != null) {
				head.setRotX(data.headPitch() * MathHelper.RADIANS_PER_DEGREE);
				head.setRotY(data.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);
			}
			// Gliederschwung wie beim Spieler (die Alien-Animationen lassen die Hauptknochen frei)
			float swing = state.getLimbSwing() * 0.6662f;
			float amount = Math.min(1.0f, state.getLimbSwingAmount());
			swingBone("right_arm", MathHelper.cos(swing + MathHelper.PI) * amount);
			swingBone("left_arm", MathHelper.cos(swing) * amount);
			swingBone("right_leg", MathHelper.cos(swing) * 1.4f * amount);
			swingBone("left_leg", MathHelper.cos(swing + MathHelper.PI) * 1.4f * amount);
		}

		private void swingBone(String name, float rotX) {
			GeoBone bone = getAnimationProcessor().getBone(name);
			if (bone != null) {
				bone.setRotX(rotX);
			}
		}
	}
}
