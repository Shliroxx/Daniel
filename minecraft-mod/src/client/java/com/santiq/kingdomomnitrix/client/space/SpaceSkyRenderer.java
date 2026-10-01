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
 * Himmel im All: schwarzer Grund, 2000 Sterne in verschiedenen Farben und Groessen, farbige Nebel
 * und ein blauer Planet unter dem Spieler (die Oberwelt). Alles fest am Himmel, dreht sich nur mit der Kamera.
 */
public final class SpaceSkyRenderer implements DimensionRenderingRegistry.SkyRenderer {
	private static final int STAR_COUNT = 2000;
	private static final int NEBULA_GRID = 8;
	/** Nah genug, dass auch bei kleiner Sichtweite nichts an der Fern-Ebene abgeschnitten wird. */
	private static final float DISTANCE = 60.0f;
	private static final int[] STAR_COLORS = {0xFFFFFF, 0xCFE3FF, 0xFFF1C9, 0xFFD2D2, 0xD8D2FF};
	private static final float[][] NEBULAE = {
			// Richtung x, y, z, Groesse, Farbe RGB, Deckkraft
			{0.6f, 0.35f, -0.7f, 33f, 0.55f, 0.25f, 0.85f, 0.10f},
			{-0.8f, 0.2f, 0.3f, 27f, 0.20f, 0.55f, 0.85f, 0.09f},
			{0.1f, 0.75f, 0.6f, 24f, 0.85f, 0.35f, 0.45f, 0.07f},
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

		RenderSystem.disableBlend();
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
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
