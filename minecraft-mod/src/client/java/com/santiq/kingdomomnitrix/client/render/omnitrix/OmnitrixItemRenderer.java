package com.santiq.kingdomomnitrix.client.render.omnitrix;

import com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Omnitrix als Item (Inventar, Hand, Boden): dasselbe 3D-Modell wie am Arm ({@link OmnitrixGeo}, Prototyp der
 * Originalserie), geschlossen, die Leuchtschicht in der Farbe des eingesetzten Energiekerns (ohne Kern: Klassisch Gruen).
 */
public final class OmnitrixItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/** Drehpunkt im Armraum von {@link OmnitrixGeo} (Bloecke): Zifferblatt-Mitte, damit es bei der Schraegansicht mittig bleibt */
	private static final float CENTER_X = (OmnitrixGeo.DIAL_X - 5.0f) / 16.0f;
	private static final float CENTER_Y = (22.0f - OmnitrixGeo.DIAL_Y) / 16.0f;
	/** Vergroesserung: das Geraet (rund 7 px) fuellt das Item-Feld */
	private static final float SCALE = 2.2f;

	public static void register() {
		BuiltinItemRendererRegistry.INSTANCE.register(com.santiq.kingdomomnitrix.registry.ModItems.OMNITRIX, new OmnitrixItemRenderer());
	}

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light,
			int overlay) {
		float[] glow = glow(stack);
		matrices.push();
		matrices.translate(0.5f, 0.5f, 0.5f);
		matrices.scale(SCALE, SCALE, SCALE);
		toDialCenter(matrices);
		OmnitrixGeo.render(matrices, consumers, false, light, 0.0f, 0.0f, 0.0f, glow);
		matrices.pop();
	}

	/** Armraum von {@link OmnitrixGeo} so legen, dass die Zifferblatt-Mitte im Ursprung liegt und zum Betrachter (+z) zeigt. */
	public static void toDialCenter(MatrixStack matrices) {
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
		matrices.translate(-CENTER_X, -CENTER_Y, 0.0f);
	}

	/** Leuchtfarbe des Omnitrix-Stapels (Energiekern) als RGB 0..1. */
	public static float[] glow(ItemStack stack) {
		int rgb = OmnitrixColors.ofStack(stack).map(OmnitrixColors.Color::primary).orElse(OmnitrixColors.CLASSIC);
		return new float[]{((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f};
	}
}
