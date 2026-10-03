package com.santiq.kingdomomnitrix.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Himmel im All: schwarzer Grund, 2000 Sterne in verschiedenen Farben und Groessen, leuchtende farbige Nebel
 * (additiv), eine ferne Sonne mit Lichthof, ein Gasriese mit Baendern und Ring und ein blauer Planet unter dem
 * Spieler (die Oberwelt). Alles fest am Himmel, dreht sich nur mit der Kamera.
 */
public final class SpaceSkyRenderer implements DimensionRenderingRegistry.SkyRenderer {
	private static final int STAR_COUNT = 2000;
	private static final int NEBULA_GRID = 8;
	/** Nah genug, dass auch bei kleiner Sichtweite nichts an der Fern-Ebene abgeschnitten wird. */
	private static final float DISTANCE = 60.0f;
	private static final int[] STAR_COLORS = {0xFFFFFF, 0xCFE3FF, 0xFFF1C9, 0xFFD2D2, 0xD8D2FF};
	private static final float[][] NEBULAE = {
			// Richtung x, y, z, Groesse, Farbe RGB, Deckkraft
			{0.6f, 0.35f, -0.7f, 33f, 0.55f, 0.25f, 0.85f, 0.20f},
			{0.75f, 0.25f, -0.55f, 20f, 0.95f, 0.40f, 0.70f, 0.16f},
			{-0.8f, 0.2f, 0.3f, 27f, 0.20f, 0.55f, 0.85f, 0.18f},
			{-0.7f, 0.35f, 0.45f, 16f, 0.30f, 0.85f, 0.75f, 0.14f},
			{0.1f, 0.75f, 0.6f, 24f, 0.85f, 0.35f, 0.45f, 0.14f},
			{-0.2f, -0.1f, -0.95f, 30f, 0.35f, 0.30f, 0.80f, 0.12f},
	};

	private final float[] stars = new float[STAR_COUNT * 5];

	public SpaceSkyRenderer() {
		Random random = Random.create(10842L);
		for (int i = 0; i < STAR_COUNT; i++) {
			Vector3f direction = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1);
			if (direction.lengthSquared() < 0.01f || direction.lengthSquared() > 1.0f) {
				i--;
				continue;
			}
			direction.normalize();
			stars[i * 5] = direction.x;
			stars[i * 5 + 1] = direction.y;
			stars[i * 5 + 2] = direction.z;
			stars[i * 5 + 3] = 0.09f + random.nextFloat() * random.nextFloat() * 0.27f;
			stars[i * 5 + 4] = STAR_COLORS[random.nextInt(STAR_COLORS.length)];
		}
	}

	@Override
	public void render(WorldRenderContext context) {
		Matrix4f matrix = new Matrix4f(context.positionMatrix());
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		// Nebel und Lichthoefe leuchten: additiv gemischt
		RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,
				com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);

		// Nebel: Raster aus Vierecken, Deckkraft je Eckpunkt nach Abstand zur Mitte -> weicher, runder Rand
		BufferBuilder clouds = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		for (float[] nebula : NEBULAE) {
			Vector3f direction = new Vector3f(nebula[0], nebula[1], nebula[2]).normalize();
			Vector3f center = new Vector3f(direction).mul(DISTANCE * 0.98f);
			Vector3f up = Math.abs(direction.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
			Vector3f right = new Vector3f(direction).cross(up).normalize().mul(nebula[3]);
			Vector3f top = new Vector3f(right).cross(direction).normalize().mul(nebula[3]);
			for (int gx = -NEBULA_GRID; gx < NEBULA_GRID; gx++) {
				for (int gy = -NEBULA_GRID; gy < NEBULA_GRID; gy++) {
					nebulaVertex(clouds, matrix, center, right, top, gx, gy, nebula);
					nebulaVertex(clouds, matrix, center, right, top, gx + 1, gy, nebula);
					nebulaVertex(clouds, matrix, center, right, top, gx + 1, gy + 1, nebula);
					nebulaVertex(clouds, matrix, center, right, top, gx, gy + 1, nebula);
				}
			}
		}
		BufferRenderer.drawWithGlobalProgram(clouds.end());
		drawSun(matrix);
		RenderSystem.defaultBlendFunc();

		BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < STAR_COUNT; i++) {
			Vector3f direction = new Vector3f(stars[i * 5], stars[i * 5 + 1], stars[i * 5 + 2]);
			int color = (int) stars[i * 5 + 4];
			billboard(quads, matrix, direction, DISTANCE, stars[i * 5 + 3],
					(color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, 230);
		}
		BufferRenderer.drawWithGlobalProgram(quads.end());

		// Planet unten: Scheibe mit hellem Zentrum (Ozean) und dunklerem Rand (Atmosphaere)
		BufferBuilder planet = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		float planetY = -DISTANCE * 0.95f;
		float radius = 23.0f;
		planet.vertex(matrix, 0.0f, planetY, 0.0f).color(0.25f, 0.55f, 0.95f, 1.0f);
		for (int step = 0; step <= 48; step++) {
			double angle = Math.PI * 2 * step / 48;
			planet.vertex(matrix, (float) Math.cos(angle) * radius, planetY, (float) Math.sin(angle) * radius).color(0.10f, 0.22f, 0.45f, 1.0f);
		}
		BufferRenderer.drawWithGlobalProgram(planet.end());
		drawGasGiant(matrix);

		RenderSystem.disableBlend();
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
	}

	/** Ferne Sonne: weisser Kern, warmer Lichthof in mehreren Ringen (additiv). */
	private static void drawSun(Matrix4f matrix) {
		Vector3f direction = new Vector3f(0.55f, 0.45f, 0.7f).normalize();
		float[][] layers = {{9.0f, 1.0f, 0.55f, 0.25f, 0.10f}, {5.0f, 1.0f, 0.75f, 0.45f, 0.22f}, {2.2f, 1.0f, 0.95f, 0.8f, 0.6f},
				{1.1f, 1.0f, 1.0f, 1.0f, 1.0f}};
		for (float[] layer : layers) {
			BufferBuilder disc = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
			Vector3f center = new Vector3f(direction).mul(DISTANCE * 0.97f);
			Vector3f up = new Vector3f(0, 1, 0);
			Vector3f right = new Vector3f(direction).cross(up).normalize();
			Vector3f top = new Vector3f(right).cross(direction).normalize();
			disc.vertex(matrix, center.x, center.y, center.z).color(layer[1], layer[2], layer[3], layer[4]);
			for (int step = 0; step <= 32; step++) {
				double angle = Math.PI * 2 * step / 32;
				float cos = (float) Math.cos(angle) * layer[0];
				float sin = (float) Math.sin(angle) * layer[0];
				disc.vertex(matrix, center.x + right.x * cos + top.x * sin, center.y + right.y * cos + top.y * sin,
						center.z + right.z * cos + top.z * sin).color(layer[1], layer[2], layer[3], 0.0f);
			}
			BufferRenderer.drawWithGlobalProgram(disc.end());
		}
	}

	/**
	 * Gasriese wie in Ratchet &amp; Clank: Scheibe mit Farbbaendern (je Zeile eigene Farbe, Rand abgedunkelt)
	 * und ein schraeger Ring davor und dahinter.
	 */
	private static void drawGasGiant(Matrix4f matrix) {
		Vector3f direction = new Vector3f(-0.65f, 0.3f, -0.7f).normalize();
		Vector3f center = new Vector3f(direction).mul(DISTANCE * 0.9f);
		Vector3f right = new Vector3f(direction).cross(new Vector3f(0, 1, 0)).normalize();
		Vector3f top = new Vector3f(right).cross(direction).normalize();
		float radius = 8.0f;
		// Ring hinten (obere Haelfte der Ellipse liegt hinter dem Planeten)
		drawRing(matrix, center, right, top, radius, true);
		BufferBuilder bands = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		int rows = 24;
		int cols = 24;
		float[][] palette = {{0.85f, 0.62f, 0.38f}, {0.72f, 0.45f, 0.30f}, {0.92f, 0.78f, 0.55f}, {0.62f, 0.38f, 0.28f}};
		for (int row = 0; row < rows; row++) {
			float[] color = palette[(row * 7 / rows + row / 3) % palette.length];
			for (int col = 0; col < cols; col++) {
				float[][] corners = {{col, row}, {col + 1, row}, {col + 1, row + 1}, {col, row + 1}};
				for (float[] c : corners) {
					float u = c[0] / cols * 2 - 1;
					float v = c[1] / rows * 2 - 1;
					float limb = 1.0f - (u * u + v * v);
					if (limb < 0) {
						// ausserhalb des Kreises: auf den Rand ziehen
						float len = (float) Math.sqrt(u * u + v * v);
						u /= len;
						v /= len;
						limb = 0;
					}
					float shade = 0.35f + 0.65f * (float) Math.sqrt(limb);
					bands.vertex(matrix, center.x + (right.x * u + top.x * v) * radius, center.y + (right.y * u + top.y * v) * radius,
							center.z + (right.z * u + top.z * v) * radius).color(color[0] * shade, color[1] * shade, color[2] * shade, 1.0f);
				}
			}
		}
		BufferRenderer.drawWithGlobalProgram(bands.end());
		drawRing(matrix, center, right, top, radius, false);
	}

	/** Schraeger Ring als flache Ellipse; {@code back} zeichnet nur die hintere (obere) Haelfte, sonst die vordere. */
	private static void drawRing(Matrix4f matrix, Vector3f center, Vector3f right, Vector3f top, float radius, boolean back) {
		BufferBuilder ring = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float inner = radius * 1.35f;
		float outer = radius * 2.1f;
		float tilt = 0.28f;
		int steps = 64;
		for (int i = 0; i < steps; i++) {
			double a0 = Math.PI * 2 * i / steps;
			double a1 = Math.PI * 2 * (i + 1) / steps;
			boolean isBack = Math.sin(a0) > 0;
			if (isBack != back) {
				continue;
			}
			float alpha = 0.55f + 0.2f * (float) Math.cos(a0 * 3);
			float[][] pts = {{(float) a0, inner}, {(float) a1, inner}, {(float) a1, outer}, {(float) a0, outer}};
			for (float[] p : pts) {
				float x = (float) Math.cos(p[0]) * p[1];
				float y = (float) Math.sin(p[0]) * p[1] * tilt;
				// leicht schraeg: Ellipse um 15 Grad gedreht
				float rx = x * 0.966f - y * 0.259f;
				float ry = x * 0.259f + y * 0.966f;
				float edge = (p[1] - inner) / (outer - inner);
				float a = alpha * (0.4f + 0.6f * (float) Math.sin(edge * Math.PI));
				ring.vertex(matrix, center.x + right.x * rx + top.x * ry, center.y + right.y * rx + top.y * ry,
						center.z + right.z * rx + top.z * ry).color(0.85f, 0.75f, 0.6f, a);
			}
		}
		BufferRenderer.drawWithGlobalProgram(ring.end());
	}

	private static void nebulaVertex(BufferBuilder buffer, Matrix4f matrix, Vector3f center, Vector3f right, Vector3f top,
			int gx, int gy, float[] nebula) {
		float u = (float) gx / NEBULA_GRID;
		float v = (float) gy / NEBULA_GRID;
		// leicht unregelmaessige Form ueber eine Welle im Winkel
		double angle = Math.atan2(v, u);
		float edge = 0.85f + 0.15f * (float) Math.sin(angle * 3 + nebula[0] * 5);
		float falloff = Math.max(0.0f, 1.0f - (u * u + v * v) / (edge * edge));
		float alpha = nebula[7] * 2.2f * falloff * falloff;
		buffer.vertex(matrix, center.x + right.x * u + top.x * v, center.y + right.y * u + top.y * v, center.z + right.z * u + top.z * v)
				.color(nebula[4], nebula[5], nebula[6], alpha);
	}

	/** Quadrat senkrecht zur Blickrichtung auf der Himmelskugel. */
	private static void billboard(BufferBuilder buffer, Matrix4f matrix, Vector3f direction, float distance, float size,
			float r, float g, float b, int alpha) {
		Vector3f center = new Vector3f(direction).mul(distance);
		Vector3f up = Math.abs(direction.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		Vector3f right = new Vector3f(direction).cross(up).normalize().mul(size);
		Vector3f top = new Vector3f(right).cross(direction).normalize().mul(size);
		float a = alpha / 255f;
		buffer.vertex(matrix, center.x - right.x - top.x, center.y - right.y - top.y, center.z - right.z - top.z).color(r, g, b, a);
		buffer.vertex(matrix, center.x + right.x - top.x, center.y + right.y - top.y, center.z + right.z - top.z).color(r, g, b, a);
		buffer.vertex(matrix, center.x + right.x + top.x, center.y + right.y + top.y, center.z + right.z + top.z).color(r, g, b, a);
		buffer.vertex(matrix, center.x - right.x + top.x, center.y - right.y + top.y, center.z - right.z + top.z).color(r, g, b, a);
	}
}
