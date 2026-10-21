package com.santiq.kingdomomnitrix.client.render;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixGeo;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixItemRenderer;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors;
import com.santiq.kingdomomnitrix.omnitrix.workbench.OmnitrixWorkbenchBlockEntity;
import com.santiq.kingdomomnitrix.omnitrix.workbench.WorkbenchRecipe;
import java.util.Optional;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

/**
 * Omnitrix-Werkbank als Reaktor (nach dem Vorbild des Draconic-Reaktors): ueber der Werkbank, auf Hoehe der vier
 * Stabilisatoren, schwebt ein Wabenfeld mit Plasmakern; die Stabilisatoren tragen rotierende Ringe und schiessen
 * Strahlen ins Feld.
 *
 * <ul>
 *   <li><b>Bereit</b> (vier Stabilisatoren): Feld klein und matt, Ringe drehen langsam.</li>
 *   <li><b>Zusammenbau</b>: Feld laedt sich auf und waechst, Strahlen zuenden. Im Feld materialisiert das Omnitrix als
 *       Explosionsansicht seiner echten Bauteile (Armband-Schichten, Knopf, Zylinder, Zifferblatt, Sanduhr), die
 *       Teile ziehen sich zusammen, Blitzboegen springen ueber das Feld. Der Energiekern kreist erst um das Feld und
 *       taucht dann von oben ins Zifferblatt — die Uhr leuchtet in seiner Farbe auf.</li>
 *   <li><b>Fertig</b>: Lichtblitz, Schockwellenring ueber den ganzen Reaktor, das Feld faellt zusammen; das fertige
 *       Omnitrix dreht sich in der Reaktormitte.</li>
 *   <li>Ohne vollstaendigen Reaktor schweben die eingelegten Teile nur ueber der Werkbank.</li>
 * </ul>
 */
public class OmnitrixWorkbenchRenderer implements BlockEntityRenderer<OmnitrixWorkbenchBlockEntity> {
	private static final Identifier FIELD = KingdomOmnitrix.id("textures/entity/omnitrix_reactor/field.png");
	private static final Identifier RING = KingdomOmnitrix.id("textures/entity/omnitrix_reactor/ring.png");
	private static final Identifier PLASMA = KingdomOmnitrix.id("textures/entity/omnitrix_reactor/plasma.png");
	private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
	/** Feldradius ruhend / voll geladen (Bloecke) */
	private static final float FIELD_IDLE = 0.55f;
	private static final float FIELD_FULL = 1.55f;
	/** Groesse der Uhr im Feld (Vielfaches des Armmodells) */
	private static final float WATCH_SCALE = 2.6f;
	/** Abschluss: Dauer von Blitz und Schockwelle (Ticks) */
	private static final float FINALE_TICKS = 30.0f;

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
		float time = (bench.getWorld().getTime() % 72000L) + tickDelta;
		float progress = bench.progress(tickDelta);
		ItemStack shell = bench.stack(WorkbenchRecipe.SHELL);
		ItemStack console = bench.stack(WorkbenchRecipe.CONSOLE);
		ItemStack core = bench.stack(WorkbenchRecipe.CORE);
		ItemStack output = bench.stack(WorkbenchRecipe.OUTPUT);
		boolean reactor = bench.stabilizerCount() == 4;
		float since = bench.sinceDone() + tickDelta;
		boolean finale = since < FINALE_TICKS && !output.isEmpty();
		int rgb = progress >= 0.0f
				? WorkbenchRecipe.coreColor(core).flatMap(OmnitrixColors::byId).map(OmnitrixColors.Color::primary).orElse(OmnitrixColors.CLASSIC)
				: OmnitrixColors.ofStack(output).map(OmnitrixColors.Color::primary).orElse(OmnitrixColors.CLASSIC);
		float[] color = {((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f};
		float cy = OmnitrixWorkbenchBlockEntity.STABILIZER_HEIGHT + 0.5f;

		// Ladung des Reaktors 0..1: waehrend des Vorgangs schnell hoch, beim Abschluss Zusammenfall
		float charge = progress >= 0.0f ? smooth(MathHelper.clamp(progress / 0.12f, 0.0f, 1.0f)) : 0.0f;
		float radius = MathHelper.lerp(charge, FIELD_IDLE, FIELD_FULL);
		if (finale) {
			radius = FIELD_FULL * Math.max(0.0f, 1.0f - since / 10.0f) + FIELD_IDLE * MathHelper.clamp((since - 10.0f) / 20.0f, 0.0f, 1.0f);
		}

		renderStabilizers(bench, matrices, consumers, time, charge, color, cy, radius, progress >= 0.0f);
		if (!reactor && progress < 0.0f && output.isEmpty()) {
			idleParts(bench, matrices, consumers, light, time, shell, console, core);
			return;
		}

		matrices.push();
		matrices.translate(0.5f, cy, 0.5f);
		float spin = time * (0.6f + Math.max(progress, 0.0f) * 6.0f);
		// Feld: Plasmakern + Waben (zwei gegenlaeufige Schalen)
		float fieldAlpha = (finale ? Math.max(0.0f, 1.0f - since / 12.0f) : 0.3f + 0.4f * charge) * 0.8f;
		plasma(matrices, consumers, radius * 0.8f, color, (0.04f + 0.1f * charge) * (finale ? fieldAlpha : 1.0f), time);
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(18.0f));
		sphere(matrices, consumers.getBuffer(RenderLayer.getEyes(FIELD)), radius, color, fieldAlpha, 0.0f);
		matrices.pop();
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-spin * 0.7f));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(24.0f));
		sphere(matrices, consumers.getBuffer(RenderLayer.getEyes(FIELD)), radius * 1.04f, color, fieldAlpha * 0.35f, 0.5f);
		matrices.pop();

		if (progress >= 0.0f) {
			assembly(bench, matrices, consumers, time, progress, radius, color, shell, console, core);
		} else if (!output.isEmpty()) {
			if (finale) {
				shockwave(matrices, consumers, since, color);
				flash(matrices, consumers, since, color);
			}
			float pop = finale ? 1.0f + 0.35f * Math.max(0.0f, 1.0f - since / 15.0f) : 1.0f;
			watch(matrices, consumers, output, 0.0f, time * 2.0f, WATCH_SCALE * pop, OmnitrixItemRenderer.glow(output), 0.08f * MathHelper.sin(time * 0.1f));
		}
		matrices.pop();
	}

	// --- Zusammenbau ------------------------------------------------------------------------------------------------

	private void assembly(OmnitrixWorkbenchBlockEntity bench, MatrixStack matrices, VertexConsumerProvider consumers, float time, float p,
			float radius, float[] color, ItemStack shell, ItemStack console, ItemStack core) {
		Optional<WorkbenchRecipe.Plan> plan = WorkbenchRecipe.plan(shell, console, core);
		boolean recolor = plan.map(x -> x.kind() == WorkbenchRecipe.Kind.RECOLOR).orElse(false);
		// Uhr: materialisiert (0..0.2), zieht sich zusammen (0.2..0.7); beim Umfaerben von Anfang an zusammengebaut
		float appear = recolor ? 1.0f : smooth(MathHelper.clamp(p / 0.2f, 0.0f, 1.0f));
		float explode = recolor ? 0.0f : 1.0f - smooth(MathHelper.clamp((p - 0.2f) / 0.5f, 0.0f, 1.0f));
		// Kern: kreist um das Feld (bis 0.7), taucht dann von oben ins Zifferblatt (bis 0.88)
		float dive = smooth(MathHelper.clamp((p - 0.7f) / 0.18f, 0.0f, 1.0f));
		float lit = MathHelper.clamp((p - 0.86f) / 0.08f, 0.0f, 1.0f);
		float[] glow = {MathHelper.lerp(lit, 0.25f, color[0]), MathHelper.lerp(lit, 0.25f, color[1]), MathHelper.lerp(lit, 0.25f, color[2])};
		float yaw = time * (1.5f + p * 4.0f);
		watch(matrices, consumers, recolor ? shell : ItemStack.EMPTY, explode, yaw, WATCH_SCALE * appear, glow, 0.0f);
		if (!core.isEmpty() && p < 0.88f) {
			float orbit = time * 0.12f;
			float r = radius * 0.95f * (1.0f - dive);
			float y = MathHelper.lerp(dive, radius + 0.25f, 0.05f) * (p < 0.7f ? 0.15f + 0.85f * smooth(p / 0.7f) : 1.0f);
			matrices.push();
			matrices.translate(MathHelper.cos(orbit) * r, y, MathHelper.sin(orbit) * r);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 9.0f));
			float s = 0.55f * (1.0f - dive * 0.6f);
			matrices.scale(s, s, s);
			items.renderItem(core, ModelTransformationMode.FIXED, FULL, OverlayTexture.DEFAULT_UV, matrices, consumers, bench.getWorld(), 0);
			matrices.pop();
		}
		if (p > 0.35f) {
			arcs(matrices, consumers, time, radius, color, MathHelper.clamp((p - 0.35f) / 0.4f, 0.0f, 1.0f));
		}
		if (lit > 0.0f && lit < 1.0f) {
			flash(matrices, consumers, (1.0f - lit) * 4.0f + 6.0f, color);
		}
	}

	/** Omnitrix in der Reaktormitte (Explosionsansicht oder fertig), Zifferblatt-Mitte im Ursprung. */
	private static void watch(MatrixStack matrices, VertexConsumerProvider consumers, ItemStack finished, float explode, float yaw,
			float scale, float[] glow, float bob) {
		if (scale <= 0.01f) {
			return;
		}
		matrices.push();
		matrices.translate(0.0f, bob, 0.0f);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-15.0f));
		matrices.scale(scale, scale, scale);
		OmnitrixItemRenderer.toDialCenter(matrices);
		if (explode > 0.0f || finished.isEmpty()) {
			OmnitrixGeo.renderExploded(matrices, consumers, FULL, explode, glow);
		} else {
			OmnitrixGeo.render(matrices, consumers, false, FULL, 0.0f, 0.0f, 0.0f, glow);
		}
		matrices.pop();
	}

	// --- Stabilisatoren und Strahlen --------------------------------------------------------------------------------

	private static void renderStabilizers(OmnitrixWorkbenchBlockEntity bench, MatrixStack matrices, VertexConsumerProvider consumers, float time,
			float charge, float[] color, float cy, float radius, boolean active) {
		for (int side = 0; side < 4; side++) {
			int distance = bench.stabilizer(side);
			if (distance <= 0) {
				continue;
			}
			Direction dir = OmnitrixWorkbenchBlockEntity.STABILIZER_SIDES[side];
			// Emitter: Seite des Stabilisators, die zur Werkbank zeigt
			float face = distance - 0.5f;
			matrices.push();
			matrices.translate(0.5f + dir.getOffsetX() * face, cy, 0.5f + dir.getOffsetZ() * face);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-dir.asRotation()));
			// lokal: -z zeigt zur Reaktormitte
			float speed = active ? 3.0f + charge * 14.0f : 1.0f;
			ring(matrices, consumers, -0.08f, 0.62f, time * speed, color, 0.35f + 0.6f * charge);
			ring(matrices, consumers, -0.2f, 0.42f, -time * speed * 1.6f, color, 0.3f + 0.6f * charge);
			if (active && charge > 0.0f) {
				float length = face - radius;
				float pulse = 0.85f + 0.15f * MathHelper.sin(time * 1.3f + side);
				beam(matrices, consumers, length, (0.10f + 0.08f * charge) * pulse, color, 0.55f * charge);
				beam(matrices, consumers, length, 0.035f * pulse, new float[]{1.0f, 1.0f, 1.0f}, 0.7f * charge);
			}
			matrices.pop();
		}
	}

	/** Ring vor dem Emitter (Ebene senkrecht zur Strahlrichtung -z), um die Strahlachse gedreht. */
	private static void ring(MatrixStack matrices, VertexConsumerProvider consumers, float z, float size, float angle, float[] color, float alpha) {
		matrices.push();
		matrices.translate(0.0f, 0.0f, z);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEyes(RING));
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f pose = entry.getPositionMatrix();
		float h = size / 2.0f;
		quadTex(buffer, entry, pose, -h, -h, 0, h, -h, 0, h, h, 0, -h, h, 0, color, alpha);
		matrices.pop();
	}

	/** Strahl entlang -z ueber {@code length} Bloecke: zwei gekreuzte Flaechen, zur Spitze hin schmaler. */
	private static void beam(MatrixStack matrices, VertexConsumerProvider consumers, float length, float width, float[] color, float alpha) {
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f pose = matrices.peek().getPositionMatrix();
		float w0 = width;
		float w1 = width * 0.55f;
		for (int axis = 0; axis < 2; axis++) {
			float ax = axis == 0 ? 1.0f : 0.0f;
			float ay = axis == 0 ? 0.0f : 1.0f;
			quadColor(buffer, pose, -ax * w0, -ay * w0, 0.0f, ax * w0, ay * w0, 0.0f, ax * w1, ay * w1, -length, -ax * w1, -ay * w1, -length,
					color, alpha, alpha);
		}
	}

	/** Blitzboegen: gezackte Linien von der Mitte zur Feldoberflaeche, alle 2 Ticks neu. */
	private static void arcs(MatrixStack matrices, VertexConsumerProvider consumers, float time, float radius, float[] color, float strength) {
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f pose = matrices.peek().getPositionMatrix();
		Random random = Random.create((long) (time / 2.0f));
		int count = 2 + (int) (strength * 4);
		float[] white = {0.85f + color[0] * 0.15f, 0.85f + color[1] * 0.15f, 0.85f + color[2] * 0.15f};
		float a = 0.8f * strength;
		for (int i = 0; i < count; i++) {
			Vec3d end = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().multiply(radius);
			Vec3d prev = end.multiply(0.25);
			int steps = 6;
			for (int s = 1; s <= steps; s++) {
				Vec3d next = end.multiply(0.25 + 0.75 * s / steps);
				if (s < steps) {
					next = next.add((random.nextFloat() - 0.5f) * 0.25f, (random.nextFloat() - 0.5f) * 0.25f, (random.nextFloat() - 0.5f) * 0.25f);
				}
				float w = 0.03f;
				float px = (float) prev.x;
				float py = (float) prev.y;
				float pz = (float) prev.z;
				float nx = (float) next.x;
				float ny = (float) next.y;
				float nz = (float) next.z;
				quadColor(buffer, pose, px - w, py, pz, px + w, py, pz, nx + w, ny, nz, nx - w, ny, nz, white, a, a);
				quadColor(buffer, pose, px, py - w, pz, px, py + w, pz, nx, ny + w, nz, nx, ny - w, nz, white, a, a);
				prev = next;
			}
		}
	}

	// --- Abschluss --------------------------------------------------------------------------------------------------

	/** Waagrechter Ring, der sich ueber den ganzen Reaktor ausdehnt und verblasst. */
	private static void shockwave(MatrixStack matrices, VertexConsumerProvider consumers, float since, float[] color) {
		float t = MathHelper.clamp(since / FINALE_TICKS, 0.0f, 1.0f);
		float size = 1.0f + 15.0f * (1.0f - (1.0f - t) * (1.0f - t));
		float alpha = 1.0f - t;
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEyes(RING));
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f pose = entry.getPositionMatrix();
		float h = size / 2.0f;
		quadTex(buffer, entry, pose, -h, 0, -h, -h, 0, h, h, 0, h, h, 0, -h, color, alpha);
	}

	/** Heller Lichtball in der Mitte, der in den ersten Ticks nach dem Abschluss verglueht. */
	private static void flash(MatrixStack matrices, VertexConsumerProvider consumers, float since, float[] color) {
		float t = MathHelper.clamp(since / 10.0f, 0.0f, 1.0f);
		if (t >= 1.0f) {
			return;
		}
		float[] hot = {MathHelper.lerp(0.6f, color[0], 1.0f), MathHelper.lerp(0.6f, color[1], 1.0f), MathHelper.lerp(0.6f, color[2], 1.0f)};
		plasma(matrices, consumers, 0.4f + 1.6f * t, hot, 0.6f * (1.0f - t), since);
	}

	// --- Geometrie --------------------------------------------------------------------------------------------------

	/** Plasmakugel mit wanderndem Rauschen (Energie-Wirbel-Schicht wie bei aufgeladenen Creepern). */
	private static void plasma(MatrixStack matrices, VertexConsumerProvider consumers, float radius, float[] color, float alpha, float time) {
		if (alpha <= 0.01f) {
			return;
		}
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEnergySwirl(PLASMA, (time * 0.004f) % 1.0f, (time * 0.0025f) % 1.0f));
		sphere(matrices, buffer, radius, new float[]{color[0] * alpha, color[1] * alpha, color[2] * alpha}, 1.0f, 0.0f);
	}

	/** Kugel aus Breiten-/Laengenstreifen (Textur 2:1 in Kugelkoordinaten), aussen und innen sichtbar. */
	private static void sphere(MatrixStack matrices, VertexConsumer buffer, float radius, float[] color, float alpha, float uOffset) {
		if (alpha <= 0.01f || radius <= 0.01f) {
			return;
		}
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f pose = entry.getPositionMatrix();
		int lat = 14;
		int lon = 28;
		for (int i = 0; i < lat; i++) {
			float t0 = (float) Math.PI * i / lat - (float) Math.PI / 2;
			float t1 = (float) Math.PI * (i + 1) / lat - (float) Math.PI / 2;
			for (int j = 0; j < lon; j++) {
				float p0 = 2 * (float) Math.PI * j / lon;
				float p1 = 2 * (float) Math.PI * (j + 1) / lon;
				float u0 = (float) j / lon + uOffset;
				float u1 = (float) (j + 1) / lon + uOffset;
				float v0 = 1.0f - (float) i / lat;
				float v1 = 1.0f - (float) (i + 1) / lat;
				float[] a = point(t0, p0);
				float[] b = point(t0, p1);
				float[] c = point(t1, p1);
				float[] d = point(t1, p0);
				vertex(buffer, entry, pose, a, radius, u0, v0, color, alpha);
				vertex(buffer, entry, pose, b, radius, u1, v0, color, alpha);
				vertex(buffer, entry, pose, c, radius, u1, v1, color, alpha);
				vertex(buffer, entry, pose, d, radius, u0, v1, color, alpha);
				// Rueckseite (Innenwand durch das Feld hindurch)
				vertex(buffer, entry, pose, d, radius, u0, v1, color, alpha * 0.6f);
				vertex(buffer, entry, pose, c, radius, u1, v1, color, alpha * 0.6f);
				vertex(buffer, entry, pose, b, radius, u1, v0, color, alpha * 0.6f);
				vertex(buffer, entry, pose, a, radius, u0, v0, color, alpha * 0.6f);
			}
		}
	}

	private static float[] point(float theta, float phi) {
		float c = MathHelper.cos(theta);
		return new float[]{c * MathHelper.cos(phi), MathHelper.sin(theta), c * MathHelper.sin(phi)};
	}

	private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f pose, float[] n, float radius, float u, float v, float[] color,
			float alpha) {
		// additive Schichten werten Alpha nicht aus: Helligkeit steckt in der Farbe
		buffer.vertex(pose, n[0] * radius, n[1] * radius, n[2] * radius).color(color[0] * alpha, color[1] * alpha, color[2] * alpha, 1.0f).texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(entry, n[0], n[1], n[2]);
	}

	/** Texturiertes Viereck (Eyes-Schicht), beidseitig. */
	private static void quadTex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f pose, float x0, float y0, float z0, float x1, float y1,
			float z1, float x2, float y2, float z2, float x3, float y3, float z3, float[] color, float alpha) {
		float[][] pts = {{x0, y0, z0, 0, 0}, {x1, y1, z1, 1, 0}, {x2, y2, z2, 1, 1}, {x3, y3, z3, 0, 1}};
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 0; k < 4; k++) {
				float[] pt = pts[pass == 0 ? k : 3 - k];
				buffer.vertex(pose, pt[0], pt[1], pt[2]).color(color[0] * alpha, color[1] * alpha, color[2] * alpha, 1.0f).texture(pt[3], pt[4])
						.overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(entry, 0.0f, 1.0f, 0.0f);
			}
		}
	}

	/** Einfarbiges Viereck (Blitz-Schicht, additiv), beidseitig; alpha0 an den ersten beiden, alpha1 an den letzten Ecken. */
	private static void quadColor(VertexConsumer buffer, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2,
			float y2, float z2, float x3, float y3, float z3, float[] color, float alpha0, float alpha1) {
		float[][] pts = {{x0, y0, z0, alpha0}, {x1, y1, z1, alpha0}, {x2, y2, z2, alpha1}, {x3, y3, z3, alpha1}};
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 0; k < 4; k++) {
				float[] pt = pts[pass == 0 ? k : 3 - k];
				buffer.vertex(pose, pt[0], pt[1], pt[2]).color(color[0], color[1], color[2], pt[3]);
			}
		}
	}

	// --- ohne Reaktor -----------------------------------------------------------------------------------------------

	/** Eingelegte Teile schweben ruhig ueber der Werkbank (Reaktor unvollstaendig). */
	private void idleParts(OmnitrixWorkbenchBlockEntity bench, MatrixStack matrices, VertexConsumerProvider consumers, int light, float time,
			ItemStack shell, ItemStack console, ItemStack core) {
		float bob = 0.04f * MathHelper.sin(time * 0.12f);
		floating(matrices, consumers, shell, 0.3f, 1.3f + bob, 0.5f, time, 0.4f, light, bench);
		floating(matrices, consumers, console, 0.7f, 1.3f - bob, 0.5f, -time, 0.4f, light, bench);
		floating(matrices, consumers, core, 0.5f, 1.15f + bob, 0.75f, time * 3.0f, 0.3f, light, bench);
	}

	private void floating(MatrixStack matrices, VertexConsumerProvider consumers, ItemStack stack, float x, float y, float z, float yaw,
			float scale, int light, OmnitrixWorkbenchBlockEntity bench) {
		if (stack.isEmpty()) {
			return;
		}
		matrices.push();
		matrices.translate(x, y, z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.scale(scale, scale, scale);
		items.renderItem(stack, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV, matrices, consumers, bench.getWorld(), 0);
		matrices.pop();
	}

	private static float smooth(float t) {
		return t * t * (3.0f - 2.0f * t);
	}

	@Override
	public boolean rendersOutsideBoundingBox(OmnitrixWorkbenchBlockEntity bench) {
		return true;
	}

	/** Reaktor bis zur vollen Sichtweite sichtbar, nicht nur in den Standard-64-Bloecken. */
	@Override
	public int getRenderDistance() {
		return MinecraftClient.getInstance().options.getClampedViewDistance() * 16;
	}
}
