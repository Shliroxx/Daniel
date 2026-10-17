package com.santiq.kingdomomnitrix.client.gadget;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.gadget.GadgetManager;
import com.santiq.kingdomomnitrix.gadget.HeliPackItem;
import java.util.Optional;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Heli-Pack bzw. Heli-Jet auf dem Ruecken jedes Spielers, der ihn im Gadget-Guertel traegt.
 * In der Luft dreht der Doppelrotor (Heli-Pack) bzw. brennen die Schubduesen mit flackernder Flamme (Heli-Jet);
 * am Boden steht der Rotor still. Modelle aus tools/generate_item_models.py (models/gadget/).
 */
public class HeliPackFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public static final Identifier HELI_BODY = KingdomOmnitrix.id("gadget/heli_body");
	public static final Identifier HELI_ROTOR = KingdomOmnitrix.id("gadget/heli_rotor");
	public static final Identifier JET_BODY = KingdomOmnitrix.id("gadget/jet_body");
	public static final Identifier JET_FLAME = KingdomOmnitrix.id("gadget/jet_flame");
	/** Drehachse des Rotors im Item-Raum (Modell-Pixel) */
	private static final float ROTOR_X = 8.0f;
	private static final float ROTOR_Z = 2.75f;
	/** Item-Hoehe, die auf Schulterhoehe liegt */
	private static final float SHOULDER_Y = 14.0f;
	/** Grad je Tick in der Luft */
	private static final float ROTOR_SPEED = 55.0f;
	/** Ruhestellung am Boden (Blaetter schraeg, wie abgestellt) */
	private static final float ROTOR_REST = 45.0f;

	public HeliPackFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	public static void register() {
		ModelLoadingPlugin.register(context -> context.addModels(HELI_BODY, HELI_ROTOR, JET_BODY, JET_FLAME));
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
		boolean jet = HeliPackItem.isJet(pack.get());
		MinecraftClient client = MinecraftClient.getInstance();
		BakedModel missing = client.getBakedModelManager().getMissingModel();
		BakedModel body = client.getBakedModelManager().getModel(jet ? JET_BODY : HELI_BODY);
		if (body == null || body == missing) {
			return;
		}
		boolean active = airborne(player);
		BlockModelRenderer renderer = client.getBlockRenderManager().getModelRenderer();
		var solid = consumers.getBuffer(TexturedRenderLayers.getEntityCutout());

		matrices.push();
		getContextModel().body.rotate(matrices);
		// Ruecken des Rumpfs (z = +2 Modell-Pixel), Item-y nach oben drehen, Item-x 8 auf die Rumpfmitte
		matrices.translate(0.0f, 0.0f, 2.0f / 16.0f);
		if (!player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isEmpty()) {
			matrices.translate(0.0f, 0.0f, 1.0f / 16.0f); // ueber der Brustruestung
		}
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
		matrices.translate(-0.5f, -SHOULDER_Y / 16.0f, 0.0f);
		renderer.render(matrices.peek(), solid, null, body, 1.0f, 1.0f, 1.0f, light, OverlayTexture.DEFAULT_UV);

		if (jet) {
			BakedModel flame = client.getBakedModelManager().getModel(JET_FLAME);
			if (active && flame != null && flame != missing) {
				float time = player.age + tickDelta;
				float flicker = 0.8f + 0.25f * MathHelper.sin(time * 2.7f) + 0.1f * MathHelper.sin(time * 7.3f);
				matrices.push();
				// Flamme waechst von der Duesenunterkante (y = 0) nach unten
				matrices.scale(1.0f, flicker, 1.0f);
				renderer.render(matrices.peek(), solid, null, flame, 1.0f, 1.0f, 1.0f, 0xF000F0, OverlayTexture.DEFAULT_UV);
				matrices.pop();
			}
		} else {
			BakedModel rotor = client.getBakedModelManager().getModel(HELI_ROTOR);
			if (rotor != null && rotor != missing) {
				float angle = active ? (player.age + tickDelta) * ROTOR_SPEED : ROTOR_REST;
				matrices.push();
				matrices.translate(ROTOR_X / 16.0f, 0.0f, ROTOR_Z / 16.0f);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
				matrices.translate(-ROTOR_X / 16.0f, 0.0f, -ROTOR_Z / 16.0f);
				renderer.render(matrices.peek(), solid, null, rotor, 1.0f, 1.0f, 1.0f, light, OverlayTexture.DEFAULT_UV);
				matrices.pop();
			}
		}
		matrices.pop();
	}
}
