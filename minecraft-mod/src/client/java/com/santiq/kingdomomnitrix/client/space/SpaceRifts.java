package com.santiq.kingdomomnitrix.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.santiq.kingdomomnitrix.space.SpaceRoute;
import com.santiq.kingdomomnitrix.space.SpaceRouteRegistry;
import com.santiq.kingdomomnitrix.space.SpaceTravel;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Zeichnet die Weltraumrisse im All: rotierender Wirbel in der Farbe des Ziels, darueber Name und Entfernung. */
public final class SpaceRifts {
	private static final int ARMS = 5;
	private static final int SEGMENTS = 18;

	private SpaceRifts() {
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || !SpaceTravel.isSpace(client.world) || context.matrixStack() == null) {
			return;
		}
		Camera camera = context.camera();
		Vec3d cameraPos = camera.getPos();
		float time = (client.world.getTime() + context.tickCounter().getTickDelta(true)) * 0.02f;
		MatrixStack matrices = context.matrixStack();
		for (Map.Entry<Identifier, SpaceRoute> entry : SpaceRouteRegistry.all(client.world.getRegistryManager())) {
			SpaceRoute route = entry.getValue();
			Vec3d offset = route.position().subtract(cameraPos);
			double distance = offset.length();
			if (distance > 900) {
				continue;
			}
			matrices.push();
			matrices.translate(offset.x, offset.y, offset.z);
			matrices.multiply(camera.getRotation());
			drawSwirl(matrices.peek().getPositionMatrix(), route, time);
			drawLabel(client, context, matrices, entry.getKey(), route, distance);
			matrices.pop();
		}
	}

	private static void drawSwirl(Matrix4f matrix, SpaceRoute route, float time) {
		float r = (route.color() >> 16 & 0xFF) / 255f;
		float g = (route.color() >> 8 & 0xFF) / 255f;
		float b = (route.color() & 0xFF) / 255f;
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float radius = route.radius();
		for (int arm = 0; arm < ARMS; arm++) {
			double base = arm * Math.PI * 2 / ARMS + time;
			for (int s = 0; s < SEGMENTS; s++) {
				float t0 = (float) s / SEGMENTS;
				float t1 = (float) (s + 1) / SEGMENTS;
				double a0 = base + t0 * 3.2;
				double a1 = base + t1 * 3.2;
				float r0 = radius * (0.08f + t0);
				float r1 = radius * (0.08f + t1);
				float width0 = radius * 0.16f * (1.0f - t0 * 0.6f);
				float width1 = radius * 0.16f * (1.0f - t1 * 0.6f);
				float alpha0 = 0.75f * (1.0f - t0);
				float alpha1 = 0.75f * (1.0f - t1);
				vertex(buffer, matrix, a0, r0 - width0, r, g, b, alpha0);
				vertex(buffer, matrix, a0, r0 + width0, r, g, b, alpha0);
				vertex(buffer, matrix, a1, r1 + width1, r, g, b, alpha1);
				vertex(buffer, matrix, a1, r1 - width1, r, g, b, alpha1);
			}
		}
		// Heller Kern
		float core = radius * 0.22f;
		buffer.vertex(matrix, -core, -core, 0).color(1f, 1f, 1f, 0.9f);
		buffer.vertex(matrix, core, -core, 0).color(1f, 1f, 1f, 0.9f);
		buffer.vertex(matrix, core, core, 0).color(1f, 1f, 1f, 0.9f);
		buffer.vertex(matrix, -core, core, 0).color(1f, 1f, 1f, 0.9f);
		BufferRenderer.drawWithGlobalProgram(buffer.end());
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	private static void vertex(BufferBuilder buffer, Matrix4f matrix, double angle, float radius, float r, float g, float b, float a) {
		buffer.vertex(matrix, (float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius, 0.0f).color(r, g, b, a);
	}

	private static void drawLabel(MinecraftClient client, WorldRenderContext context, MatrixStack matrices, Identifier id, SpaceRoute route, double distance) {
		if (context.consumers() == null) {
			return;
		}
		matrices.push();
		matrices.translate(0.0, route.radius() + 3.0, 0.0);
		// Gleich gross lesbar, egal wie weit weg
		float scale = (float) Math.max(0.05, distance * 0.0025);
		matrices.scale(scale, -scale, scale);
		TextRenderer font = client.textRenderer;
		Text label = SpaceRoute.name(id).copy().append(" · " + Math.round(distance) + " m");
		float x = -font.getWidth(label) / 2.0f;
		font.draw(label, x, 0, 0xFFFFFFFF, true, matrices.peek().getPositionMatrix(), context.consumers(),
				TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		matrices.pop();
	}
}
