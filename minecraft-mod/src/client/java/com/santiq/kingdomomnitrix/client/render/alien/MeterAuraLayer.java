package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.client.alien.AlienMeters;
import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import java.util.Optional;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Leucht-Aura nach Alien-Anzeige: der Koerper wird ein zweites (bei hoher Anzeige drittes) Mal etwas groesser mit
 * wanderndem Energie-Muster (wie der aufgeladene Creeper) gezeichnet — XLR8 blau nach Tempo, Heatblast orange nach
 * Kernhitze (bei 100 % blau). Je hoeher die Anzeige, desto heller, groesser und schneller. Volle Helligkeit, auch nachts.
 */
public class MeterAuraLayer extends GeoRenderLayer<AlienBodyAnimatable> {
	private static final Identifier SWIRL = Identifier.ofVanilla("textures/entity/creeper/creeper_armor.png");
	/** ab diesem Wert (Prozent) erscheint die Aura */
	private static final float MIN = 8.0f;
	private static final int[][] COLORS = {
			{0x1E6FFF, 0x7FF4FF},
			{0xFF5A10, 0x7FE9FF}};

	private final GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable> owner;

	public MeterAuraLayer(GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable> owner) {
		super(owner);
		this.owner = owner;
	}

	@Override
	public void render(MatrixStack poseStack, AlienBodyAnimatable animatable, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
			VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		if (!(owner.getCurrentEntity() instanceof AbstractClientPlayerEntity player)) {
			return;
		}
		Optional<Integer> meter = AlienMeters.meterOf(player);
		if (meter.isEmpty()) {
			return;
		}
		int m = meter.get();
		float value = AlienMeters.value(player.getId(), m, partialTick);
		if (value < MIN) {
			return;
		}
		float t = MathHelper.clamp((value - MIN) / (100.0f - MIN), 0.0f, 1.0f);
		float age = player.age + partialTick;
		boolean max = value >= 99.5f;
		float pulse = max ? 0.75f + 0.25f * MathHelper.sin(age * 0.5f) : 1.0f;
		int rgb = max ? COLORS[m][1] : COLORS[m][0];
		shell(poseStack, animatable, bakedModel, bufferSource, partialTick, age, 1.0f + 0.04f + 0.05f * t,
				scaled(rgb, (0.25f + 0.75f * t) * pulse), 0.01f + 0.02f * t);
		if (value >= 50.0f) {
			// zweite, weitere Huelle ab der ersten Stufe
			shell(poseStack, animatable, bakedModel, bufferSource, partialTick, age * 1.7f, 1.0f + 0.12f + 0.06f * t,
					scaled(rgb, 0.35f * t * pulse), 0.015f + 0.03f * t);
		}
	}

	private void shell(MatrixStack poseStack, AlienBodyAnimatable animatable, BakedGeoModel model, VertexConsumerProvider buffers,
			float partialTick, float age, float scale, int color, float speed) {
		float u = (age * speed) % 1.0f;
		RenderLayer layer = RenderLayer.getEnergySwirl(SWIRL, u, (age * speed * 0.8f) % 1.0f);
		poseStack.push();
		// um die Koerpermitte vergroessern, nicht um die Fuesse
		poseStack.translate(0.0f, 1.0f, 0.0f);
		poseStack.scale(scale, scale, scale);
		poseStack.translate(0.0f, -1.0f, 0.0f);
		getRenderer().reRender(model, poseStack, buffers, animatable, layer, buffers.getBuffer(layer), partialTick,
				LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, color);
		poseStack.pop();
	}

	/** Energie-Muster wird additiv gemischt: Helligkeit ueber die Farbe, Alpha voll. */
	private static int scaled(int rgb, float brightness) {
		float b = MathHelper.clamp(brightness, 0.0f, 1.0f);
		return ColorHelper.Argb.getArgb(255, (int) (((rgb >> 16) & 0xFF) * b), (int) (((rgb >> 8) & 0xFF) * b), (int) ((rgb & 0xFF) * b));
	}
}
