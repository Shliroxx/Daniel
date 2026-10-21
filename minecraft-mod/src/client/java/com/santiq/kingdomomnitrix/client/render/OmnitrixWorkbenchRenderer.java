package com.santiq.kingdomomnitrix.client.render;

import com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors;
import com.santiq.kingdomomnitrix.omnitrix.workbench.OmnitrixWorkbenchBlockEntity;
import com.santiq.kingdomomnitrix.omnitrix.workbench.WorkbenchRecipe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Animation ueber der Omnitrix-Werkbank.
 *
 * <ul>
 *   <li><b>Belegt, wartend</b>: Huelle, Konsole und Kern schweben ueber dem Montagefeld.</li>
 *   <li><b>Zusammenbau</b>: Huelle und Konsole kreisen erst langsam, ziehen sich dann zusammen; der Kern steigt in
 *       einer Spirale auf und taucht von oben in die Konsole; ein Lichtkegel aus der Sanduhr wird immer heller.</li>
 *   <li><b>Fertig</b>: das Omnitrix (3D-Modell) dreht sich ueber der Werkbank, kurz nach dem Abschluss mit Lichtblitz.</li>
 * </ul>
 */
public class OmnitrixWorkbenchRenderer implements BlockEntityRenderer<OmnitrixWorkbenchBlockEntity> {
	private final ItemRenderer items;

	public OmnitrixWorkbenchRenderer(BlockEntityRendererFactory.Context context) {
		this.items = context.getItemRenderer();
	}

	@Override
	public void render(OmnitrixWorkbenchBlockEntity bench, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light,
			int overlay) {
		if (bench.getWorld() == null) {
			return;
		}
		float time = (bench.getWorld().getTime() % 24000L) + tickDelta;
		float progress = bench.progress(tickDelta);
		ItemStack shell = bench.stack(WorkbenchRecipe.SHELL);
		ItemStack console = bench.stack(WorkbenchRecipe.CONSOLE);
		ItemStack core = bench.stack(WorkbenchRecipe.CORE);
		ItemStack output = bench.stack(WorkbenchRecipe.OUTPUT);
		int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		int color = WorkbenchRecipe.coreColor(core).flatMap(OmnitrixColors::byId).map(OmnitrixColors.Color::primary)
				.orElse(OmnitrixColors.CLASSIC);

		if (progress >= 0.0f) {
			float pull = smooth(MathHelper.clamp((progress - 0.25f) / 0.6f, 0.0f, 1.0f));
			float spin = time * (4.0f + progress * 20.0f);
			float radius = 0.38f * (1.0f - pull);
			float height = 1.35f + 0.08f * MathHelper.sin(time * 0.15f);
			if (!shell.isEmpty()) {
				floating(matrices, consumers, shell, 0.5f + MathHelper.cos(spin * MathHelper.RADIANS_PER_DEGREE) * radius, height,
						0.5f + MathHelper.sin(spin * MathHelper.RADIANS_PER_DEGREE) * radius, spin + 90.0f, 0.55f, full, bench);
			}
			if (!console.isEmpty()) {
				floating(matrices, consumers, console, 0.5f - MathHelper.cos(spin * MathHelper.RADIANS_PER_DEGREE) * radius, height + 0.02f,
						0.5f - MathHelper.sin(spin * MathHelper.RADIANS_PER_DEGREE) * radius, spin - 90.0f, 0.5f, full, bench);
			}
			if (!core.isEmpty()) {
				// Spirale nach oben, dann von oben in die Mitte
				float rise = MathHelper.clamp(progress / 0.7f, 0.0f, 1.0f);
				float dive = MathHelper.clamp((progress - 0.7f) / 0.3f, 0.0f, 1.0f);
				float a = time * 0.3f;
				float cr = 0.3f * (1.0f - rise);
				float cy = 1.05f + rise * 0.75f - dive * (0.75f - (height - 1.05f));
				floating(matrices, consumers, core, 0.5f + MathHelper.cos(a) * cr, cy, 0.5f + MathHelper.sin(a) * cr, time * 8.0f, 0.35f, full,
						bench);
			}
			beam(matrices, consumers, color, 0.25f + progress * 0.75f, height);
			return;
		}
		if (!output.isEmpty()) {
			float bob = 0.06f * MathHelper.sin(time * 0.1f);
			float scale = 0.65f;
			int since = bench.sinceDone();
			if (since < 20) {
				scale *= 1.0f + 0.4f * (1.0f - since / 20.0f);
				beam(matrices, consumers, OmnitrixColors.ofStack(output).map(OmnitrixColors.Color::primary).orElse(color), 1.0f - since / 20.0f,
						1.4f);
			}
			floating(matrices, consumers, output, 0.5f, 1.45f + bob, 0.5f, time * 2.0f, scale, full, bench);
			return;
		}
		// belegt, aber (noch) ohne gueltigen Plan: Teile schweben ruhig ueber ihren Plaetzen
		float bob = 0.04f * MathHelper.sin(time * 0.12f);
		if (!shell.isEmpty()) {
			floating(matrices, consumers, shell, 0.3f, 1.3f + bob, 0.5f, time, 0.4f, light, bench);
		}
		if (!console.isEmpty()) {
			floating(matrices, consumers, console, 0.7f, 1.3f - bob, 0.5f, -time, 0.4f, light, bench);
		}
		if (!core.isEmpty()) {
			floating(matrices, consumers, core, 0.5f, 1.15f + bob, 0.75f, time * 3.0f, 0.3f, light, bench);
		}
	}

	private void floating(MatrixStack matrices, VertexConsumerProvider consumers, ItemStack stack, float x, float y, float z, float yaw,
			float scale, int light, OmnitrixWorkbenchBlockEntity bench) {
		matrices.push();
		matrices.translate(x, y, z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.scale(scale, scale, scale);
		items.renderItem(stack, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV, matrices, consumers, bench.getWorld(), 0);
		matrices.pop();
	}

	/** Lichtkegel aus der Sanduhr nach oben (additiv, vier Seiten eines sich verjuengenden Prismas). */
	private static void beam(MatrixStack matrices, VertexConsumerProvider consumers, int rgb, float strength, float top) {
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f pose = matrices.peek().getPositionMatrix();
		float r = ((rgb >> 16) & 0xFF) / 255.0f;
		float g = ((rgb >> 8) & 0xFF) / 255.0f;
		float b = (rgb & 0xFF) / 255.0f;
		float alpha = MathHelper.clamp(strength, 0.0f, 1.0f) * 0.55f;
		float lo = 0.3f;
		float hi = 0.06f;
		float y0 = 1.01f;
		float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
		for (int i = 0; i < 4; i++) {
			float[] c0 = corners[i];
			float[] c1 = corners[(i + 1) % 4];
			buffer.vertex(pose, 0.5f + c0[0] * lo, y0, 0.5f + c0[1] * lo).color(r, g, b, alpha);
			buffer.vertex(pose, 0.5f + c1[0] * lo, y0, 0.5f + c1[1] * lo).color(r, g, b, alpha);
			buffer.vertex(pose, 0.5f + c1[0] * hi, top, 0.5f + c1[1] * hi).color(r, g, b, 0.0f);
			buffer.vertex(pose, 0.5f + c0[0] * hi, top, 0.5f + c0[1] * hi).color(r, g, b, 0.0f);
		}
	}

	private static float smooth(float t) {
		return t * t * (3.0f - 2.0f * t);
	}

	@Override
	public boolean rendersOutsideBoundingBox(OmnitrixWorkbenchBlockEntity bench) {
		return true;
	}

	/** Animation bis zur vollen Sichtweite sichtbar, nicht nur in den Standard-64-Bloecken. */
	@Override
	public int getRenderDistance() {
		return MinecraftClient.getInstance().options.getClampedViewDistance() * 16;
	}
}
