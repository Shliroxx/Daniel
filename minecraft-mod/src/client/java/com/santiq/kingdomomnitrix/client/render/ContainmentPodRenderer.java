package com.santiq.kingdomomnitrix.client.render;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixGeo;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixItemRenderer;
import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import com.santiq.kingdomomnitrix.world.plumber.ContainmentPodBlock;
import com.santiq.kingdomomnitrix.world.plumber.ContainmentPodBlockEntity;
import java.util.UUID;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Containment-Kapsel als Galvan-Pod (Texturen aus Alien Evolution): Unterschale aus drei gestuften Ringen, Kuppel aus
 * vier Segmenten. Geschlossen glimmt gruenes Licht in den Naehten.
 *
 * <p>Oeffnung (Zeit t ab {@link ContainmentPodBlockEntity#openedAt}): bis {@link ContainmentPodBlockEntity#BURST} bebt
 * die Kapsel und die Naehte gluehen immer heller; dann sprengen die vier Segmente mit Ueberschwingen nach aussen auf,
 * ein gruener Lichtstrahl schiesst nach oben. Das Teil steigt aus der Wiege auf — das kaputte Omnitrix als 3D-Modell
 * mit flackerndem Licht (Zifferblatt erst nach oben, dann zum Betrachter gedreht), die Konsole als leuchtendes Bild —
 * und fliegt, wenn es jemand zum ersten Mal holt, in einem Bogen an dessen Arm. Nach
 * {@link ContainmentPodBlockEntity#OPEN_TICKS} schliessen sich die Segmente wieder.</p>
 */
public class ContainmentPodRenderer implements BlockEntityRenderer<ContainmentPodBlockEntity> {
	private static final Identifier SIDES = KingdomOmnitrix.id("textures/entity/containment_pod/ae_pod_sides.png");
	private static final Identifier FRONT = KingdomOmnitrix.id("textures/entity/containment_pod/ae_pod_front.png");
	private static final Identifier BACK = KingdomOmnitrix.id("textures/entity/containment_pod/ae_pod_back.png");
	private static final Identifier TOP = KingdomOmnitrix.id("textures/entity/containment_pod/ae_pod_top.png");
	private static final Identifier BOTTOM = KingdomOmnitrix.id("textures/entity/containment_pod/ae_pod_bottom.png");
	/** weiss, fuer additive Leuchtflaechen (Farbe kommt aus dem Vertex) */
	private static final Identifier GLOW = KingdomOmnitrix.id("textures/entity/containment_pod/glow.png");
	private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
	private static final float[] GREEN = {0.25f, 1.0f, 0.1f};
	/** Hoehe der Trennfuge zwischen Schale und Kuppel (Pixel) */
	private static final float EQUATOR = 8.0f;
	/** groesster Oeffnungswinkel der Segmente (Grad) */
	private static final float PETAL_OPEN = 128.0f;
	/** halbe Breite der Kugel an der Fuge (Pixel), Bezug fuer die Texturkoordinaten */
	private static final float SPAN = 9.0f;
	/** Kapsel groesser als ein Block: wuchtiger Pod in der Kammer */
	private static final float SIZE = 1.35f;

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
		float t = time - pod.openedAt();
		boolean active = t >= 0.0f && t < ContainmentPodBlockEntity.OPEN_TICKS + 20.0f;
		float petals = active ? petalAngle(t) : 0.0f;
		float open = MathHelper.clamp(petals / PETAL_OPEN, 0.0f, 1.0f);
		// Beben vor dem Aufsprengen, Naehte gluehen auf
		float shake = active && t < ContainmentPodBlockEntity.BURST ? t / ContainmentPodBlockEntity.BURST : 0.0f;
		float seam = active ? (t < ContainmentPodBlockEntity.BURST ? 0.35f + 0.65f * shake : 0.0f)
				: 0.22f + 0.12f * MathHelper.sin(time * 0.12f);
		boolean shell = !(pod.getCachedState().getBlock() instanceof ContainmentPodBlock block) || block.part() == ContainmentPodBlock.Part.SHELL;

		matrices.push();
		matrices.translate(0.5f, 0.0f, 0.5f);
		if (shake > 0.0f) {
			matrices.translate(MathHelper.sin(time * 3.1f) * 0.02f * shake, 0.0f, MathHelper.cos(time * 2.7f) * 0.02f * shake);
		}
		matrices.scale(SIZE / 16.0f, SIZE / 16.0f, SIZE / 16.0f);
		shellPart(matrices, consumers, light);
		if (open > 0.0f) {
			interior(matrices, consumers, open, time);
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				// ein Segment der Huellen-Kapsel ist beim Absturz geborsten
				petal(matrices, consumers, light, sx, sz, petals, shell && sx > 0 && sz < 0);
			}
		}
		if (seam > 0.0f) {
			seams(matrices, consumers, seam);
		}
		matrices.pop();

		if (active && t > ContainmentPodBlockEntity.BURST) {
			beam(matrices, consumers, open, time);
		}
		content(pod, matrices, consumers, light, t, time, open, active, shell, tickDelta);
	}

	// --- Ablauf -----------------------------------------------------------------------------------------------------

	/** Oeffnungswinkel der Segmente: kurzes Zucken beim Beben, Aufsprengen mit Ueberschwingen, Schliessen am Ende. */
	private static float petalAngle(float t) {
		int burst = ContainmentPodBlockEntity.BURST;
		int close = ContainmentPodBlockEntity.OPEN_TICKS;
		if (t < burst) {
			return Math.max(0.0f, MathHelper.sin(t * 2.3f)) * 4.0f * t / burst;
		}
		if (t < burst + 8) {
			return PETAL_OPEN * backOut((t - burst) / 8.0f);
		}
		if (t > close) {
			return PETAL_OPEN * (1.0f - smooth(MathHelper.clamp((t - close) / 20.0f, 0.0f, 1.0f)));
		}
		return PETAL_OPEN;
	}

	private static float backOut(float x) {
		float c1 = 1.70158f;
		float c3 = c1 + 1.0f;
		float y = x - 1.0f;
		return 1.0f + c3 * y * y * y + c1 * y * y;
	}

	private static float smooth(float t) {
		return t * t * (3.0f - 2.0f * t);
	}

	// --- Kapsel -----------------------------------------------------------------------------------------------------

	/** Unterschale: drei gestufte Ringe (unten schmal, an der Fuge am breitesten) */
	private static void shellPart(MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(BOTTOM)), matrices, -6, 0, -6, 6, 2, 6, 0.85f, light, false);
		cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(BACK)), matrices, -8, 2, -8, 8, 5, 8, 0.95f, light, false);
		cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(FRONT)), matrices, -9, 5, -9, 9, EQUATOR, 9, 1.0f, light, false);
	}

	/**
	 * Ein Kuppelsegment (Viertel) aus drei Lagen, um die Scharnierachse an der Fuge nach aussen geklappt. Achse =
	 * oben × Richtung des Segments: positive Drehung kippt die Spitze nach aussen.
	 */
	private static void petal(MatrixStack matrices, VertexConsumerProvider consumers, int light, int sx, int sz, float angle, boolean cracked) {
		matrices.push();
		float hinge = 6.4f;
		matrices.translate(sx * hinge, EQUATOR, sz * hinge);
		if (angle != 0.0f) {
			float inv = (float) (1.0 / Math.sqrt(2.0));
			matrices.multiply(new Quaternionf().rotationAxis(angle * MathHelper.RADIANS_PER_DEGREE, sz * inv, 0.0f, -sx * inv));
		}
		matrices.translate(-sx * hinge, -EQUATOR, -sz * hinge);
		float gap = 0.35f;
		Identifier texture = cracked ? SIDES : (sx + sz == 0) ? BACK : TOP;
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
		cuboid(buffer, matrices, quad(sx, gap), EQUATOR, quad(sz, gap), quad(sx, 9.0f), 11.0f, quad(sz, 9.0f), 1.0f, light, true);
		cuboid(buffer, matrices, quad(sx, gap), 11.0f, quad(sz, gap), quad(sx, 7.0f), 14.0f, quad(sz, 7.0f), 1.0f, light, true);
		cuboid(buffer, matrices, quad(sx, gap), 14.0f, quad(sz, gap), quad(sx, 4.5f), 16.0f, quad(sz, 4.5f), 1.05f, light, true);
		matrices.pop();
	}

	private static float quad(int sign, float value) {
		return sign * value;
	}

	/** Innenraum bei offener Kuppel: dunkle Mulde, Wiege, gruenes Glimmen am Grund */
	private static void interior(MatrixStack matrices, VertexConsumerProvider consumers, float open, float time) {
		cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SIDES)), matrices, -7.5f, 3, -7.5f, 7.5f, EQUATOR + 0.02f, 7.5f, 0.08f, FULL,
				false, true);
		cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SIDES)), matrices, -2.5f, 3, -2.5f, 2.5f, 4.5f, 2.5f, 0.6f, FULL, false);
		float pulse = (0.7f + 0.3f * MathHelper.sin(time * 0.3f)) * open;
		flat(consumers.getBuffer(RenderLayer.getEyes(GLOW)), matrices, -7.4f, 3.05f, -7.4f, 7.4f, 7.4f, GREEN[0] * pulse, GREEN[1] * pulse,
				GREEN[2] * pulse);
	}

	/** Leuchtende Naehte: Kreuz ueber der Kuppel und Ring an der Fuge */
	private static void seams(MatrixStack matrices, VertexConsumerProvider consumers, float strength) {
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEyes(GLOW));
		float r = GREEN[0] * strength;
		float g = GREEN[1] * strength;
		float b = GREEN[2] * strength;
		float w = 0.3f;
		// Kreuz (zwei duenne senkrechte Flaechen) zwischen den Segmenten
		glowBox(buffer, matrices, -w, EQUATOR, -9.05f, w, 16.05f, 9.05f, r, g, b);
		glowBox(buffer, matrices, -9.05f, EQUATOR, -w, 9.05f, 16.05f, w, r, g, b);
		// Ring an der Fuge
		glowBox(buffer, matrices, -9.1f, EQUATOR - 0.3f, -9.1f, 9.1f, EQUATOR + 0.3f, 9.1f, r, g, b);
	}

	/** Gruener Lichtstrahl aus der offenen Kapsel nach oben (additiv, zwei gekreuzte Flaechen) */
	private static void beam(MatrixStack matrices, VertexConsumerProvider consumers, float open, float time) {
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f pose = matrices.peek().getPositionMatrix();
		float alpha = 0.45f * open * (0.8f + 0.2f * MathHelper.sin(time * 0.5f));
		float y0 = 0.3f;
		float y1 = 3.2f;
		float w0 = 0.22f;
		float w1 = 0.05f;
		for (int axis = 0; axis < 2; axis++) {
			float ax = axis == 0 ? 1 : 0;
			float az = axis == 0 ? 0 : 1;
			float[][] pts = {{0.5f - ax * w0, y0, 0.5f - az * w0, alpha}, {0.5f + ax * w0, y0, 0.5f + az * w0, alpha},
					{0.5f + ax * w1, y1, 0.5f + az * w1, 0.0f}, {0.5f - ax * w1, y1, 0.5f - az * w1, 0.0f}};
			for (int pass = 0; pass < 2; pass++) {
				for (int k = 0; k < 4; k++) {
					float[] p = pts[pass == 0 ? k : 3 - k];
					buffer.vertex(pose, p[0], p[1], p[2]).color(GREEN[0], GREEN[1], GREEN[2], p[3]);
				}
			}
		}
	}

	// --- Inhalt -----------------------------------------------------------------------------------------------------

	private void content(ContainmentPodBlockEntity pod, MatrixStack matrices, VertexConsumerProvider consumers, int light, float t, float time,
			float open, boolean active, boolean shell, float tickDelta) {
		UUID target = pod.flightTarget();
		int flyStart = ContainmentPodBlockEntity.FLY_START;
		int flyEnd = ContainmentPodBlockEntity.FLY_END;
		boolean taken = active && target != null && t >= flyEnd && t < ContainmentPodBlockEntity.OPEN_TICKS + 20;
		if (open <= 0.01f || taken) {
			return;
		}
		// Aufstieg aus der Wiege, sanftes Schweben
		float rise = smooth(MathHelper.clamp((t - ContainmentPodBlockEntity.BURST - 4) / (ContainmentPodBlockEntity.RISE_END
				- ContainmentPodBlockEntity.BURST - 4.0f), 0.0f, 1.0f));
		Vec3d pos = new Vec3d(0.5, 0.3 + 1.0 * rise + 0.05 * Math.sin(time * 0.12) * rise, 0.5);
		float scale = 1.0f;
		float face = rise;
		if (target != null && t >= flyStart) {
			PlayerEntity player = pod.getWorld().getPlayerByUuid(target);
			if (player != null) {
				float f = smooth(MathHelper.clamp((t - flyStart) / (flyEnd - flyStart), 0.0f, 1.0f));
				Vec3d hand = player.getLerpedPos(tickDelta).add(0.0, 1.0, 0.0).subtract(Vec3d.of(pod.getPos()));
				pos = pos.lerp(hand, f).add(0.0, Math.sin(f * Math.PI) * 0.8, 0.0);
				scale = 1.0f - 0.55f * f;
			}
		}
		matrices.push();
		matrices.translate(pos.x, pos.y, pos.z);
		// zum Betrachter drehen (nur um die Hochachse), anfangs liegt das Zifferblatt nach oben
		float yaw = MinecraftClient.getInstance().gameRenderer.getCamera().getYaw();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw + MathHelper.sin(time * 0.05f) * 12.0f));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f * (1.0f - face)));
		halo(matrices, consumers, 0.55f * scale, open * (0.8f + 0.2f * MathHelper.sin(time * 0.4f)));
		if (shell) {
			// kaputtes Omnitrix: Licht stottert
			float flicker = MathHelper.sin(time * 1.9f) * MathHelper.sin(time * 0.43f) > 0.25f ? 0.2f : 1.0f;
			float s = 2.8f * scale;
			matrices.scale(s, s, s);
			OmnitrixItemRenderer.toDialCenter(matrices);
			OmnitrixGeo.render(matrices, consumers, false, FULL, 0.0f, 0.0f, 0.0f,
					new float[]{GREEN[0] * flicker, GREEN[1] * flicker, GREEN[2] * flicker});
		} else {
			float s = 0.75f * scale;
			matrices.scale(s, s, s);
			items.renderItem(new ItemStack(Ben10Items.OMNITRIX_CONSOLE), ModelTransformationMode.FIXED, FULL, OverlayTexture.DEFAULT_UV, matrices,
					consumers, pod.getWorld(), 0);
		}
		matrices.pop();
	}

	/** Leuchtscheibe hinter dem Teil (zum Betrachter gedreht) */
	private static void halo(MatrixStack matrices, VertexConsumerProvider consumers, float size, float strength) {
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEyes(KingdomOmnitrix.id("textures/entity/omnitrix_reactor/ring.png")));
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f pose = entry.getPositionMatrix();
		float r = GREEN[0] * strength;
		float g = GREEN[1] * strength;
		float b = GREEN[2] * strength;
		float[][] pts = {{-size, -size, 0, 0}, {size, -size, 1, 0}, {size, size, 1, 1}, {-size, size, 0, 1}};
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 0; k < 4; k++) {
				float[] p = pts[pass == 0 ? k : 3 - k];
				buffer.vertex(pose, p[0], p[1], -0.15f).color(r, g, b, 1.0f).texture(p[2], p[3]).overlay(OverlayTexture.DEFAULT_UV).light(FULL)
						.normal(entry, 0, 0, 1);
			}
		}
	}

	// --- Geometrie --------------------------------------------------------------------------------------------------

	private static void cuboid(VertexConsumer buffer, MatrixStack matrices, float x0, float y0, float z0, float x1, float y1, float z1, float shade,
			int light, boolean noBottom) {
		cuboid(buffer, matrices, x0, y0, z0, x1, y1, z1, shade, light, noBottom, false);
	}

	/** Quader (Ecken in beliebiger Reihenfolge) mit der ganzen Textur je Seite; {@code inward} zeigt die Innenseite. */
	private static void cuboid(VertexConsumer buffer, MatrixStack matrices, float ax, float ay, float az, float bx, float by, float bz, float shade,
			int light, boolean noBottom, boolean inward) {
		float x0 = Math.min(ax, bx);
		float x1 = Math.max(ax, bx);
		float y0 = Math.min(ay, by);
		float y1 = Math.max(ay, by);
		float z0 = Math.min(az, bz);
		float z1 = Math.max(az, bz);
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f pose = entry.getPositionMatrix();
		float[][][] faces = {
				{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {0, 1, 0}},
				{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {0, -1, 0}},
				{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {0, 0, 1}},
				{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {0, 0, -1}},
				{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {1, 0, 0}},
				{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {-1, 0, 0}}};
		for (int f = 0; f < faces.length; f++) {
			if ((noBottom && f == 1) || (inward && f == 0)) {
				continue;
			}
			float[][] face = faces[f];
			float c = shade * (f == 0 ? 1.0f : f == 1 ? 0.5f : 0.78f + 0.08f * (f % 2));
			float dir = inward ? -1.0f : 1.0f;
			for (int k = 0; k < 4; k++) {
				float[] p = face[inward ? 3 - k : k];
				// UV aus der Position: die Streifen laufen ueber alle Ringe der Kugel durch statt je Ring gestaucht
				float u = f < 2 ? (p[0] + SPAN) / (2 * SPAN) : (f < 4 ? (p[0] + SPAN) / (2 * SPAN) : (p[2] + SPAN) / (2 * SPAN));
				float v = f < 2 ? (p[2] + SPAN) / (2 * SPAN) : 1.0f - MathHelper.clamp(p[1] / 16.0f, 0.0f, 1.0f);
				buffer.vertex(pose, p[0], p[1], p[2]).color(c, c, c, 1.0f).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
						.light(light).normal(entry, face[4][0] * dir, face[4][1] * dir, face[4][2] * dir);
			}
		}
	}

	/** additiver Leuchtquader (Helligkeit in der Farbe) */
	private static void glowBox(VertexConsumer buffer, MatrixStack matrices, float x0, float y0, float z0, float x1, float y1, float z1, float r,
			float g, float b) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f pose = entry.getPositionMatrix();
		float[][][] faces = {
				{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}},
				{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}},
				{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}},
				{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}},
				{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}}};
		for (float[][] face : faces) {
			for (float[] p : face) {
				buffer.vertex(pose, p[0], p[1], p[2]).color(r, g, b, 1.0f).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(FULL)
						.normal(entry, 0, 1, 0);
			}
		}
	}

	/** waagrechte Leuchtflaeche (additiv) */
	private static void flat(VertexConsumer buffer, MatrixStack matrices, float x0, float y, float z0, float x1, float z1, float r, float g, float b) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f pose = entry.getPositionMatrix();
		float[][] pts = {{x0, z0}, {x0, z1}, {x1, z1}, {x1, z0}};
		for (float[] p : pts) {
			buffer.vertex(pose, p[0], y, p[1]).color(r, g, b, 1.0f).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(FULL)
					.normal(entry, 0, 1, 0);
		}
	}

	@Override
	public boolean rendersOutsideBoundingBox(ContainmentPodBlockEntity pod) {
		return true;
	}
}
