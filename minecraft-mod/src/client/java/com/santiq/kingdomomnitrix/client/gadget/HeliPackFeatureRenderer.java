package com.santiq.kingdomomnitrix.client.gadget;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.client.render.SafeGlowLayer;
import com.santiq.kingdomomnitrix.gadget.GadgetManager;
import com.santiq.kingdomomnitrix.gadget.HeliPackItem;
import com.santiq.kingdomomnitrix.gadget.PackMode;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Clank auf dem Ruecken jedes Spielers, der den Rucksack im Gadget-Guertel traegt — wie in Ratchet &amp; Clank:
 * Clank haengt mit dem Gesicht nach hinten, je nach Modus ({@link PackMode}) mit Rotor aus dem Kopf (dreht nur in der
 * Luft), Schubduesen bzw. Jetpack-Raketen (Flammen beim Schub) oder Schwingen (klappen im Fall auf, am Boden angelegt).
 * Dasselbe GeckoLib-Modell wie das Item (geo/item/gear, tools/generate_weapon_models.py); die beweglichen Knochen
 * rotor, wings und flame stellt dieser Renderer nach dem Flugzustand ein statt nach der Leerlauf-Animation.
 */
public class HeliPackFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	/** Clanks Koerperoberkante liegt so viele Modell-Pixel unter dem Nacken des Spielers */
	private static final float HANG_BELOW_NECK = 13.0f;
	private static final float SCALE = 0.95f;
	/** Grad je Tick in der Luft */
	private static final float ROTOR_SPEED = 55.0f;
	/** Ruhestellung am Boden (Blaetter schraeg, wie abgestellt) */
	private static final float ROTOR_REST = 35.0f;
	/** Breite der angelegten Schwingen (Anteil der vollen Spannweite) */
	private static final float WINGS_FOLDED = 0.18f;

	private final PackRenderer renderer = new PackRenderer();
	private final Pose pose = new Pose();

	public HeliPackFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	public static void register() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer playerRenderer) {
				helper.register(new HeliPackFeatureRenderer(playerRenderer));
			}
		});
	}

	/** In der Luft (nicht im Wasser, nicht am Klettern): Rotor dreht, Duesen brennen. */
	static boolean airborne(AbstractClientPlayerEntity player) {
		return !player.isOnGround() && !player.isTouchingWater() && !player.isClimbing() && !player.hasVehicle();
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (player.isInvisible() || TransformationManager.get(player).isTransformed()) {
			return;
		}
		Optional<ItemStack> pack = GadgetManager.pack(player);
		if (pack.isEmpty()) {
			return;
		}
		// Steigt bzw. faellt der Spieler gerade? (auch fuer andere Spieler, deren Geschwindigkeit der Client nicht kennt)
		double climb = player.getY() - player.prevY;
		boolean active = airborne(player);
		pose.mode = HeliPackItem.mode(pack.get());
		pose.time = player.age + tickDelta;
		pose.rotorSpinning = active;
		pose.wingsOpen = active && climb < -0.01;
		pose.burning = pose.mode == PackMode.THRUSTER ? active : active && climb > 0.01;

		matrices.push();
		getContextModel().body.rotate(matrices);
		// Ruecken des Rumpfs (z = +2 Modell-Pixel), ueber einer Brustruestung einen Pixel weiter hinten
		float back = player.getEquippedStack(EquipmentSlot.CHEST).isEmpty() ? 2.0f : 3.0f;
		matrices.translate(0.0f, 0.0f, back / 16.0f);
		// Spielermodell zeigt y nach unten: umdrehen, dann Clank so tief haengen, dass sein Kopf ueber die Schultern schaut
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
		matrices.translate(0.0f, -HANG_BELOW_NECK / 16.0f, 0.0f);
		matrices.scale(SCALE, SCALE, SCALE);
		Identifier texture = PackRenderer.texture(pose.mode);
		RenderLayer layer = RenderLayer.getEntityCutoutNoCull(texture);
		VertexConsumer buffer = consumers.getBuffer(layer);
		renderer.render(matrices, pose, consumers, layer, buffer, light, tickDelta);
		matrices.pop();
	}

	/** Zustand fuer einen Zeichenvorgang; keine Animations-Controller, die Knochen setzt der Renderer selbst. */
	static final class Pose implements GeoAnimatable {
		private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
		PackMode mode = PackMode.HELI;
		float time;
		boolean rotorSpinning;
		boolean wingsOpen;
		boolean burning;

		@Override
		public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		}

		@Override
		public AnimatableInstanceCache getAnimatableInstanceCache() {
			return cache;
		}

		@Override
		public double getTick(Object object) {
			return time;
		}
	}

	private static final class PackRenderer extends GeoObjectRenderer<Pose> {
		PackRenderer() {
			super(new Model());
			addRenderLayer(new SafeGlowLayer<>(this));
		}

		static Identifier texture(PackMode mode) {
			return KingdomOmnitrix.id("textures/item/gear/" + mode.gear() + ".png");
		}

		/** Ohne die Verschiebung der Grundklasse (+0,5 Block): der Nullpunkt liegt schon am Ruecken. */
		@Override
		public void preRender(MatrixStack poseStack, Pose animatable, BakedGeoModel model, VertexConsumerProvider bufferSource,
				VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			this.objectRenderTranslations = new org.joml.Matrix4f(poseStack.peek().getPositionMatrix());
		}

		@Override
		public void renderRecursively(MatrixStack poseStack, Pose pose, GeoBone bone, RenderLayer renderType,
				VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
				int packedOverlay, int colour) {
			switch (bone.getName()) {
				case "rotor" -> bone.setRotY((pose.rotorSpinning ? pose.time * ROTOR_SPEED : ROTOR_REST) * MathHelper.RADIANS_PER_DEGREE);
				case "wings" -> {
					bone.setRotX(0.0f);
					bone.setScaleX(pose.wingsOpen ? 1.0f : WINGS_FOLDED);
				}
				case "flame" -> {
					bone.setHidden(!pose.burning);
					float flicker = 0.85f + 0.2f * MathHelper.sin(pose.time * 2.7f) + 0.08f * MathHelper.sin(pose.time * 7.3f);
					bone.setScaleX(1.0f);
					bone.setScaleY(flicker);
					bone.setScaleZ(1.0f);
				}
				default -> {
				}
			}
			super.renderRecursively(poseStack, pose, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
					packedOverlay, colour);
		}
	}

	/** Modell und Textur des jeweiligen Modus; die Animation wird nur formal gebraucht (keine Controller). */
	private static final class Model extends GeoModel<Pose> {
		@Override
		public Identifier getModelResource(Pose pose) {
			return KingdomOmnitrix.id("geo/item/gear/" + pose.mode.gear() + ".geo.json");
		}

		@Override
		public Identifier getTextureResource(Pose pose) {
			return PackRenderer.texture(pose.mode);
		}

		@Override
		public Identifier getAnimationResource(Pose pose) {
			return KingdomOmnitrix.id("animations/item/gear/heli_pack.animation.json");
		}
	}
}
