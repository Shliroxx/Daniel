package com.santiq.kingdomomnitrix.client.render;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixGeo;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixItemRenderer;
import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import com.santiq.kingdomomnitrix.world.plumber.ContainmentPodBlock;
import com.santiq.kingdomomnitrix.world.plumber.ContainmentPodBlockEntity;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Containment-Kapsel (3D): gepanzerter Kasten im flachen Cartoon-Stil mit schwarzen Riss-Baendern. Geschlossen leuchtet
 * nur ein gruener Spalt unter dem Deckel. Beim Oeffnen ({@link ContainmentPodBlockEntity#open}) klappt der Deckel
 * nach hinten auf, innen geht gruenes Licht an und das Teil schwebt heraus — das kaputte Omnitrix (3D-Modell, flackerndes
 * Licht) bzw. die Konsole. Nach {@link ContainmentPodBlockEntity#OPEN_TICKS} Ticks schliesst die Kapsel wieder.
 */
public class ContainmentPodRenderer implements BlockEntityRenderer<ContainmentPodBlockEntity> {
	private static final Identifier SIDE = KingdomOmnitrix.id("textures/entity/containment_pod/side.png");
	private static final Identifier TOP = KingdomOmnitrix.id("textures/entity/containment_pod/top.png");
	/** weiss, fuer die additiven Leuchtflaechen (Farbe kommt aus dem Vertex) */
	private static final Identifier GLOW = KingdomOmnitrix.id("textures/entity/containment_pod/glow.png");
	private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
	private static final float GREEN_R = 0.22f;
	private static final float GREEN_G = 1.0f;
	private static final float GREEN_B = 0.08f;
	/** Masse in Pixeln (1/16 Block): Unterteil bis WALL_TOP, Deckel bis LID_TOP, Wandstaerke WALL */
	private static final float MIN = 1.0f;
	private static final float MAX = 15.0f;
	private static final float WALL_TOP = 9.0f;
	private static final float LID_TOP = 12.0f;
	private static final float WALL = 1.5f;

	private final ItemRenderer items;

	public ContainmentPodRenderer(BlockEntityRendererFactory.Context context) {
		this.items = context.getItemRenderer();
	}

	@Override
	public void render(ContainmentPodBlockEntity pod, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light,
			int overlay) {
		if (pod.getWorld() == null) {
			return;
		}
		float time = pod.getWorld().getTime() + tickDelta;
		float since = time - pod.openedAt();
		float open = openness(since);
		boolean shell = !(pod.getCachedState().getBlock() instanceof ContainmentPodBlock block) || block.part() == ContainmentPodBlock.Part.SHELL;

		matrices.push();
		matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
		// Puffer jeweils direkt vor dem Zeichnen holen: ein neuer Puffer schliesst den vorigen
		// Unterteil: Aussenwaende, dunkle Innenwaende, dunkler Boden
		VertexConsumer side = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SIDE));
		box(side, matrices, MIN, 0, MIN, MAX, WALL_TOP, MAX, 1.0f, light, false);
		box(side, matrices, MIN + WALL, 1, MIN + WALL, MAX - WALL, WALL_TOP + 0.01f, MAX - WALL, 0.12f, light, true);
		// gruener Schimmer im Inneren, staerker wenn offen
		VertexConsumer glow = consumers.getBuffer(RenderLayer.getEyes(GLOW));
		float pulse = 0.55f + 0.45f * MathHelper.sin(time * 0.15f);
		float innerGlow = 0.12f + 0.8f * open;
		flat(glow, matrices, MIN + WALL, 1.05f, MIN + WALL, MAX - WALL, MAX - WALL, GREEN_R * innerGlow, GREEN_G * innerGlow, GREEN_B * innerGlow);
		if (open < 0.05f) {
			// Lichtspalt unter dem geschlossenen Deckel
			float seam = 0.35f + 0.35f * pulse;
			seam(glow, matrices, seam);
		}
		// Deckel: Scharnier an der Rueckseite (z = MIN), klappt um bis zu 115 Grad nach hinten
		matrices.push();
		matrices.translate(0.0f, WALL_TOP, MIN);
		matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(115.0f * open));
		matrices.translate(0.0f, -WALL_TOP, -MIN);
		box(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TOP)), matrices, MIN - 0.3f, WALL_TOP, MIN - 0.3f, MAX + 0.3f, LID_TOP, MAX + 0.3f, 1.0f, light, false);
		matrices.pop();
		matrices.pop();

		// Inhalt: schwebt beim Oeffnen nach oben, dreht sich langsam
		float lift = smooth(MathHelper.clamp((since - 6.0f) / 20.0f, 0.0f, 1.0f)) * (open > 0.0f ? 1.0f : 0.0f);
		matrices.push();
		matrices.translate(0.5f, 0.32f + 0.45f * lift + 0.03f * MathHelper.sin(time * 0.1f) * lift, 0.5f);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * (0.5f + 2.5f * lift)));
		if (shell) {
			// kaputtes Omnitrix: 3D-Modell, Licht flackert unruhig
			float flicker = (MathHelper.sin(time * 1.7f) * MathHelper.sin(time * 0.37f) > 0.2f) ? 0.15f : 0.75f;
			float[] color = {GREEN_R * flicker, GREEN_G * flicker, GREEN_B * flicker};
			matrices.scale(1.6f, 1.6f, 1.6f);
			OmnitrixItemRenderer.toDialCenter(matrices);
			OmnitrixGeo.render(matrices, consumers, false, open > 0.0f ? FULL : light, 0.0f, 0.0f, 0.0f, color);
		} else {
			matrices.scale(0.5f, 0.5f, 0.5f);
			items.renderItem(new ItemStack(Ben10Items.OMNITRIX_CONSOLE), ModelTransformationMode.FIXED, open > 0.0f ? FULL : light,
					OverlayTexture.DEFAULT_UV, matrices, consumers, pod.getWorld(), 0);
		}
		matrices.pop();
	}

	/** Oeffnung 0..1: aufklappen (15 Ticks), offen halten, wieder schliessen (20 Ticks). */
	private static float openness(float since) {
		if (since < 0.0f || since > ContainmentPodBlockEntity.OPEN_TICKS + 20.0f) {
			return 0.0f;
		}
		if (since < 15.0f) {
			return smooth(since / 15.0f);
		}
		if (since > ContainmentPodBlockEntity.OPEN_TICKS) {
			return smooth(1.0f - (since - ContainmentPodBlockEntity.OPEN_TICKS) / 20.0f);
		}
		return 1.0f;
	}

	private static float smooth(float t) {
		return t * t * (3.0f - 2.0f * t);
	}

	/** Quader mit der ganzen Textur auf jeder Seite; {@code inward} dreht die Flaechen nach innen (Innenwand). */
	private static void box(VertexConsumer buffer, MatrixStack matrices, float x0, float y0, float z0, float x1, float y1, float z1, float shade,
			int light, boolean inward) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f pose = entry.getPositionMatrix();
		float[][][] faces = {
				{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {0, 1, 0}},
				{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {0, -1, 0}},
				{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {0, 0, 1}},
				{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {0, 0, -1}},
				{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {1, 0, 0}},
				{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {-1, 0, 0}}};
		float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
		for (int f = 0; f < faces.length; f++) {
			if (inward && f == 0) {
				continue;
			}
			float[][] face = faces[f];
			float side = f >= 2 ? 0.8f + 0.2f * (f % 2) : (f == 0 ? 1.0f : 0.55f);
			float c = shade * side;
			float dir = inward ? -1.0f : 1.0f;
			for (int k = 0; k < 4; k++) {
				float[] p = face[inward ? 3 - k : k];
				buffer.vertex(pose, p[0], p[1], p[2]).color(c, c, c, 1.0f).texture(uv[k][0], uv[k][1]).overlay(OverlayTexture.DEFAULT_UV)
						.light(light).normal(entry, face[4][0] * dir, face[4][1] * dir, face[4][2] * dir);
			}
		}
	}

	/** waagrechte Leuchtflaeche (additiv, Helligkeit in der Farbe) */
	private static void flat(VertexConsumer buffer, MatrixStack matrices, float x0, float y, float z0, float x1, float z1, float r, float g, float b) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f pose = entry.getPositionMatrix();
		float[][] pts = {{x0, z0, 0, 0}, {x0, z1, 0, 1}, {x1, z1, 1, 1}, {x1, z0, 1, 0}};
		for (float[] p : pts) {
			buffer.vertex(pose, p[0], y, p[1]).color(r, g, b, 1.0f).texture(p[2] * 0.02f, p[3] * 0.02f).overlay(OverlayTexture.DEFAULT_UV)
					.light(FULL).normal(entry, 0, 1, 0);
		}
	}

	/** gruener Lichtspalt rund um den geschlossenen Deckel */
	private static void seam(VertexConsumer buffer, MatrixStack matrices, float strength) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f pose = entry.getPositionMatrix();
		float r = GREEN_R * strength;
		float g = GREEN_G * strength;
		float b = GREEN_B * strength;
		float y0 = WALL_TOP - 0.05f;
		float y1 = WALL_TOP + 0.35f;
		float o = 0.05f;
		float[][][] strips = {
				{{MIN - o, MAX + o}, {MAX + o, MAX + o}},
				{{MAX + o, MAX + o}, {MAX + o, MIN - o}},
				{{MAX + o, MIN - o}, {MIN - o, MIN - o}},
				{{MIN - o, MIN - o}, {MIN - o, MAX + o}}};
		for (float[][] s : strips) {
			buffer.vertex(pose, s[0][0], y0, s[0][1]).color(r, g, b, 1.0f).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(entry, 0, 1, 0);
			buffer.vertex(pose, s[1][0], y0, s[1][1]).color(r, g, b, 1.0f).texture(0.02f, 0).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(entry, 0, 1, 0);
			buffer.vertex(pose, s[1][0], y1, s[1][1]).color(r, g, b, 1.0f).texture(0.02f, 0.02f).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(entry, 0, 1, 0);
			buffer.vertex(pose, s[0][0], y1, s[0][1]).color(r, g, b, 1.0f).texture(0, 0.02f).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(entry, 0, 1, 0);
		}
	}

	@Override
	public boolean rendersOutsideBoundingBox(ContainmentPodBlockEntity pod) {
		return true;
	}
}
