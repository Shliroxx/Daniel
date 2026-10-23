package com.santiq.kingdomomnitrix.client.suit;

import com.santiq.kingdomomnitrix.suit.forge.ForgeMaterials;
import com.santiq.kingdomomnitrix.suit.fusion.FusionCodexItem;
import com.santiq.kingdomomnitrix.suit.fusion.FusionCoreBlockEntity;
import com.santiq.kingdomomnitrix.suit.fusion.FusionInjectorBlockEntity;
import com.santiq.kingdomomnitrix.suit.fusion.FusionRecipes;
import java.util.Map;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Fusionsaltar im Stil des Draconic-Fusion-Crafting: Mittelstueck und Energiekerne schweben ueber dem Kern, die
 * Platten ueber den Injektoren. Waehrend der Fusion schiessen pulsierende Strahlen in Linienfarbe von jedem Injektor
 * in den Kern, die Platten schrumpfen und drehen schneller, um das Mittelstueck waechst eine Energiekugel mit zwei
 * Ringen. Zum Abschluss: Lichtblitz und Schockwellenring.
 */
public final class FusionAltarRenderer {
	private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
	private static final float CORE_Y = 1.25f;
	private static final float PLATE_Y = 1.15f;
	private static final float FINALE_TICKS = 30.0f;
	private static final int BAR_W = 90;
	/** Farbe der Altarstufen im Hologramm (Index = Stufe) */
	private static final int[] TIER_COLORS = {0, 0xFF3AE8FF, 0xFF4BFF6A, 0xFFD8CCFF, 0xFFFF3A6A};
	private static final float[] DEFAULT_COLOR = {0.23f, 0.91f, 1.0f};
	/** Leuchtfarbe je Platte (Linie und Stufe) */
	private static final Map<Item, float[]> COLORS = Map.of(
			ForgeMaterials.TAYDENITE_PLATE, rgb(0x3AE89A), ForgeMaterials.BIOTECH_WEAVE, rgb(0x4BFF6A),
			ForgeMaterials.STAR_MATRIX, rgb(0xC8BCFF), ForgeMaterials.MAGNAPLATE_PLATING, rgb(0xA86AFF),
			ForgeMaterials.CARBONOX_PLATE, rgb(0xFF2A2A), ForgeMaterials.HELLFIRE_PLATE, rgb(0xFF8A1A));

	private FusionAltarRenderer() {
	}

	static float[] rgb(int c) {
		return new float[] {((c >> 16) & 0xFF) / 255.0f, ((c >> 8) & 0xFF) / 255.0f, (c & 0xFF) / 255.0f};
	}

	static float[] colorOf(Item plate) {
		return COLORS.getOrDefault(plate, DEFAULT_COLOR);
	}

	// --- Kern ---------------------------------------------------------------------------------------------------------

	public static final class Core implements BlockEntityRenderer<FusionCoreBlockEntity> {
		private final ItemRenderer items;
		private final TextRenderer text;
		private final BlockEntityRenderDispatcher dispatcher;

		public Core(BlockEntityRendererFactory.Context context) {
			this.items = context.getItemRenderer();
			this.text = context.getTextRenderer();
			this.dispatcher = context.getRenderDispatcher();
		}

		@Override
		public void render(FusionCoreBlockEntity core, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light,
				int overlay) {
			if (core.getWorld() == null) {
				return;
			}
			float time = (core.getWorld().getTime() % 72000L) + tickDelta;
			boolean working = core.working();
			float p = working ? core.progress() : 0.0f;
			float[] color = colorOf(core.plate());
			float since = core.getWorld().getTime() - core.finishedAt() + tickDelta;
			boolean finale = since >= 0 && since < FINALE_TICKS;
			if (finale && !working) {
				color = colorOf(lastPlate(core));
			}

			matrices.push();
			matrices.translate(0.5f, CORE_Y, 0.5f);
			// Energiekugel und Ringe waehrend der Fusion
			if (working) {
				float charge = smooth(MathHelper.clamp(p / 0.15f, 0.0f, 1.0f));
				float radius = 0.28f + 0.42f * charge + 0.05f * MathHelper.sin(time * 0.6f) * charge;
				glowBall(matrices, consumers, radius, color, 0.10f + 0.25f * p, time);
				glowBall(matrices, consumers, radius * 0.55f, new float[] {1.0f, 1.0f, 1.0f}, 0.12f + 0.35f * p, -time);
				ring(matrices, consumers, radius * 1.35f, 0.025f, color, 0.55f, time * (3.0f + 9.0f * p), 70.0f);
				ring(matrices, consumers, radius * 1.55f, 0.02f, color, 0.4f, -time * (2.0f + 7.0f * p), 110.0f);
			}
			if (finale) {
				shockwave(matrices, consumers, since, color);
				float flash = Math.max(0.0f, 1.0f - since / 8.0f);
				glowBall(matrices, consumers, 0.4f + since * 0.12f, new float[] {1.0f, 1.0f, 1.0f}, flash * 0.6f, time);
			}

			// Mittelstueck: schwebt, dreht sich, pulsiert waehrend der Fusion
			ItemStack center = core.center();
			if (!center.isEmpty()) {
				matrices.push();
				matrices.translate(0.0f, 0.08f * MathHelper.sin(time * 0.08f), 0.0f);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * (2.0f + 10.0f * p)));
				float pop = finale ? 1.0f + 0.4f * Math.max(0.0f, 1.0f - since / 15.0f) : 1.0f;
				float s = (0.75f + 0.08f * MathHelper.sin(time * 0.5f) * p) * pop;
				matrices.scale(s, s, s);
				items.renderItem(center, ModelTransformationMode.FIXED, FULL, OverlayTexture.DEFAULT_UV, matrices, consumers, core.getWorld(), 0);
				matrices.pop();
			}
			// Energiekerne: kreisen um das Mittelstueck (hoechstens acht sichtbar)
			ItemStack cores = core.cores();
			int shown = Math.min(cores.getCount(), 8);
			for (int i = 0; i < shown; i++) {
				float a = time * (0.05f + 0.15f * p) + i * MathHelper.TAU / shown;
				float r = 0.62f - 0.2f * p;
				matrices.push();
				matrices.translate(MathHelper.cos(a) * r, -0.35f + 0.06f * MathHelper.sin(time * 0.1f + i), MathHelper.sin(a) * r);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-a * MathHelper.DEGREES_PER_RADIAN + time * 4.0f));
				matrices.scale(0.32f, 0.32f, 0.32f);
				items.renderItem(cores, ModelTransformationMode.FIXED, FULL, OverlayTexture.DEFAULT_UV, matrices, consumers, core.getWorld(), i);
				matrices.pop();
			}
			matrices.pop();

			hud(core, matrices, consumers, time);

			// Strahlen: Injektor -> Kern
			if (working) {
				Vec3d target = new Vec3d(0.5, CORE_Y, 0.5);
				BlockPos origin = core.getPos();
				int index = 0;
				for (BlockPos at : core.active()) {
					Vec3d from = new Vec3d(at.getX() - origin.getX() + 0.5, at.getY() - origin.getY() + PLATE_Y, at.getZ() - origin.getZ() + 0.5);
					float pulse = 0.6f + 0.4f * MathHelper.sin(time * 0.9f + index * 1.7f);
					float width = (0.035f + 0.05f * p) * pulse;
					beam(matrices, consumers, from, target, width, color, 0.55f + 0.35f * p, time + index * 7.0f);
					beam(matrices, consumers, from, target, width * 0.35f, new float[] {1.0f, 1.0f, 1.0f}, 0.7f, time);
					index++;
				}
			}
		}

		/**
		 * Hologramm ueber dem Kern (nur in der Naehe): Altarstufe; waehrend der Fusion Fortschritt, Hitze und
		 * Instabilitaet als Balken, bei Stocken eine blinkende Warnung mit Restzeit.
		 */
		private void hud(FusionCoreBlockEntity core, MatrixStack matrices, VertexConsumerProvider consumers, float time) {
			Camera camera = dispatcher.camera;
			if (camera == null || camera.getPos().squaredDistanceTo(Vec3d.ofCenter(core.getPos())) > 12 * 12) {
				return;
			}
			matrices.push();
			matrices.translate(0.5f, 2.45f, 0.5f);
			matrices.multiply(camera.getRotation());
			float scale = 0.02f;
			matrices.scale(scale, -scale, scale);
			Matrix4f pose = matrices.peek().getPositionMatrix();
			Text title = Text.translatable(FusionCodexItem.TITLES[Math.max(1, Math.min(4, core.clientTier()))]);
			drawCentered(title, pose, consumers, 0, TIER_COLORS[Math.max(1, Math.min(4, core.clientTier()))]);
			if (core.working()) {
				VertexConsumer bars = consumers.getBuffer(RenderLayer.getTextBackground());
				bar(bars, pose, 12, core.progress(), 0xFF3AE8FF);
				bar(bars, pose, 24, core.heat() / 100.0f, heatColor(core.heat()));
				bar(bars, pose, 36, Math.min(1.0f, core.instability() / 25.0f), 0xFFB070FF);
				label(Text.translatable("hud.kingdomomnitrix.fusion.progress", (int) (core.progress() * 100)), pose, consumers, 12);
				label(Text.translatable("hud.kingdomomnitrix.fusion.heat", (int) core.heat()), pose, consumers, 24);
				label(Text.translatable("hud.kingdomomnitrix.fusion.instability", core.instability()), pose, consumers, 36);
				if (core.stalled() && ((int) (time / 6)) % 2 == 0) {
					int left = (FusionCoreBlockEntity.STALL_LIMIT - core.stallTicks()) / 20;
					drawCentered(Text.translatable("hud.kingdomomnitrix.fusion.stalled", Math.max(0, left)), pose, consumers, 50, 0xFFFF4A3A);
				}
			}
			matrices.pop();
		}

		private void drawCentered(Text line, Matrix4f pose, VertexConsumerProvider consumers, int y, int color) {
			float x = -text.getWidth(line) / 2.0f;
			text.draw(line, x, y, color, false, pose, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0x90000000, FULL);
		}

		private void label(Text line, Matrix4f pose, VertexConsumerProvider consumers, int y) {
			text.draw(line, -BAR_W / 2.0f + 2, y + 1, 0xFFFFFFFF, true, pose, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, FULL);
		}

		private static void bar(VertexConsumer buffer, Matrix4f pose, int y, float fraction, int color) {
			float x0 = -BAR_W / 2.0f;
			float x1 = x0 + BAR_W * Math.max(0.0f, Math.min(1.0f, fraction));
			rect(buffer, pose, x0 - 1, y - 1, BAR_W / 2.0f + 1, y + 10, 0xC0000000);
			rect(buffer, pose, x0, y, x1, y + 9, color);
		}

		private static void rect(VertexConsumer buffer, Matrix4f pose, float x0, float y0, float x1, float y1, int color) {
			buffer.vertex(pose, x0, y0, 0.01f).color(color).light(FULL);
			buffer.vertex(pose, x0, y1, 0.01f).color(color).light(FULL);
			buffer.vertex(pose, x1, y1, 0.01f).color(color).light(FULL);
			buffer.vertex(pose, x1, y0, 0.01f).color(color).light(FULL);
		}

		private static int heatColor(float heat) {
			return heat < 50.0f ? 0xFFFFB02A : heat < 75.0f ? 0xFFFF6A1A : 0xFFFF2A2A;
		}

		private static Item lastPlate(FusionCoreBlockEntity core) {
			return core.center().isEmpty() ? core.plate() : plateFor(core.center());
		}

		@Override
		public boolean rendersOutsideBoundingBox(FusionCoreBlockEntity core) {
			return true;
		}

		@Override
		public int getRenderDistance() {
			return 96;
		}
	}

	/** Farbe nach Abschluss: aus dem Ergebnis ableiten (Rezept mit diesem Teil als Ergebnis). */
	static Item plateFor(ItemStack result) {
		return FusionRecipes.all().stream().filter(r -> result.isOf(r.result())).map(FusionRecipes.Recipe::plate).findFirst()
				.orElse(Items.AIR);
	}

	// --- Injektor -----------------------------------------------------------------------------------------------------

	public static final class Injector implements BlockEntityRenderer<FusionInjectorBlockEntity> {
		private final ItemRenderer items;

		public Injector(BlockEntityRendererFactory.Context context) {
			this.items = context.getItemRenderer();
		}

		@Override
		public void render(FusionInjectorBlockEntity injector, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
				int light, int overlay) {
			ItemStack stack = injector.stack();
			if (injector.getWorld() == null) {
				return;
			}
			float time = (injector.getWorld().getTime() % 72000L) + tickDelta;
			if (injector.damaged()) {
				// durchgebrannt: flackernder roter Ring ueber dem Sockel
				matrices.push();
				matrices.translate(0.5f, 0.95f, 0.5f);
				float flicker = 0.4f + 0.6f * Math.abs(MathHelper.sin(time * 0.9f));
				ring(matrices, consumers, 0.36f, 0.03f, new float[] {1.0f, 0.12f, 0.08f}, flicker, time * 3.0f, 90.0f);
				matrices.pop();
			}
			if (stack.isEmpty()) {
				return;
			}
			FusionCoreBlockEntity core = feeding(injector);
			float p = core != null ? core.progress() : 0.0f;
			float[] color = injector.damaged() ? new float[] {1.0f, 0.15f, 0.1f} : colorOf(stack.getItem());
			matrices.push();
			matrices.translate(0.5f, PLATE_Y + 0.05f * MathHelper.sin(time * 0.1f + injector.getPos().hashCode()), 0.5f);
			// Sockel-Leuchten
			ring(matrices, consumers, 0.32f, 0.02f, color, core != null ? 0.8f : 0.35f, time * (1.0f + 8.0f * p), 90.0f);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * (2.5f + 20.0f * p)));
			float s = 0.6f * (1.0f - 0.55f * p);
			matrices.scale(s, s, s);
			items.renderItem(stack, ModelTransformationMode.FIXED, FULL, OverlayTexture.DEFAULT_UV, matrices, consumers, injector.getWorld(), 0);
			matrices.pop();
		}

		/** Kern, der gerade aus diesem Injektor zieht (oder null). */
		private static FusionCoreBlockEntity feeding(FusionInjectorBlockEntity injector) {
			BlockPos pos = injector.getPos();
			int r = FusionCoreBlockEntity.RANGE;
			int ry = FusionCoreBlockEntity.RANGE_Y;
			for (BlockPos at : BlockPos.iterate(pos.add(-r, -ry, -r), pos.add(r, ry, r))) {
				BlockEntity be = injector.getWorld().getBlockEntity(at);
				if (be instanceof FusionCoreBlockEntity core && core.working() && core.active().contains(pos)) {
					return core;
				}
			}
			return null;
		}

		@Override
		public boolean rendersOutsideBoundingBox(FusionInjectorBlockEntity injector) {
			return true;
		}
	}

	// --- Zeichenhilfen ------------------------------------------------------------------------------------------------

	private static float smooth(float t) {
		return t * t * (3.0f - 2.0f * t);
	}

	/** Strahl als zwei gekreuzte, zur Spitze hin schmaler werdende Baender mit wandernden Helligkeitsknoten. */
	private static void beam(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d from, Vec3d to, float width, float[] color,
			float alpha, float time) {
		Vec3d d = to.subtract(from);
		float length = (float) d.length();
		if (length < 0.01f) {
			return;
		}
		matrices.push();
		matrices.translate(from.x, from.y, from.z);
		float yaw = (float) Math.atan2(d.x, d.z);
		float pitch = (float) Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
		matrices.multiply(RotationAxis.NEGATIVE_X.rotation(pitch));
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f pose = matrices.peek().getPositionMatrix();
		int segments = 12;
		for (int i = 0; i < segments; i++) {
			float z0 = length * i / segments;
			float z1 = length * (i + 1) / segments;
			float k0 = knot(i / (float) segments, time);
			float k1 = knot((i + 1) / (float) segments, time);
			float w0 = width * (1.0f - 0.4f * i / segments) * (0.7f + 0.6f * k0);
			float w1 = width * (1.0f - 0.4f * (i + 1) / segments) * (0.7f + 0.6f * k1);
			float a0 = alpha * (0.6f + 0.4f * k0);
			float a1 = alpha * (0.6f + 0.4f * k1);
			for (int axis = 0; axis < 2; axis++) {
				float ax = axis == 0 ? 1.0f : 0.0f;
				float ay = 1.0f - ax;
				quad(buffer, pose, -ax * w0, -ay * w0, z0, ax * w0, ay * w0, z0, ax * w1, ay * w1, z1, -ax * w1, -ay * w1, z1, color, a0, a1);
				quad(buffer, pose, -ax * w1, -ay * w1, z1, ax * w1, ay * w1, z1, ax * w0, ay * w0, z0, -ax * w0, -ay * w0, z0, color, a1, a0);
			}
		}
		matrices.pop();
	}

	/** Helligkeitsknoten, die vom Injektor zum Kern laufen. */
	private static float knot(float along, float time) {
		float phase = along * 3.0f - time * 0.12f;
		return (float) Math.pow(0.5f + 0.5f * MathHelper.sin(phase * MathHelper.TAU), 4);
	}

	/** Leuchtende Kugel aus Breiten-/Laengenbaendern (additiv). */
	private static void glowBall(MatrixStack matrices, VertexConsumerProvider consumers, float radius, float[] color, float alpha, float time) {
		if (alpha <= 0.01f) {
			return;
		}
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 2.0f));
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f pose = matrices.peek().getPositionMatrix();
		int lat = 10;
		int lon = 16;
		for (int i = 0; i < lat; i++) {
			float t0 = MathHelper.PI * i / lat - MathHelper.HALF_PI;
			float t1 = MathHelper.PI * (i + 1) / lat - MathHelper.HALF_PI;
			for (int j = 0; j < lon; j++) {
				float p0 = MathHelper.TAU * j / lon;
				float p1 = MathHelper.TAU * (j + 1) / lon;
				float[] a = sph(t0, p0, radius);
				float[] b = sph(t0, p1, radius);
				float[] c = sph(t1, p1, radius);
				float[] e = sph(t1, p0, radius);
				quad(buffer, pose, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], e[0], e[1], e[2], color, alpha, alpha);
				quad(buffer, pose, e[0], e[1], e[2], c[0], c[1], c[2], b[0], b[1], b[2], a[0], a[1], a[2], color, alpha * 0.5f, alpha * 0.5f);
			}
		}
		matrices.pop();
	}

	private static float[] sph(float theta, float phi, float r) {
		return new float[] {MathHelper.cos(theta) * MathHelper.cos(phi) * r, MathHelper.sin(theta) * r, MathHelper.cos(theta) * MathHelper.sin(phi) * r};
	}

	/** Flacher Leuchtring (gekippt um tilt Grad), dreht um die Hochachse. */
	private static void ring(MatrixStack matrices, VertexConsumerProvider consumers, float radius, float thickness, float[] color, float alpha,
			float spin, float tilt) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tilt));
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f pose = matrices.peek().getPositionMatrix();
		int n = 40;
		for (int i = 0; i < n; i++) {
			float a0 = MathHelper.TAU * i / n;
			float a1 = MathHelper.TAU * (i + 1) / n;
			float fade = 0.35f + 0.65f * (0.5f + 0.5f * MathHelper.sin(a0 * 3.0f));
			float ri = radius - thickness;
			float ro = radius + thickness;
			quad(buffer, pose, MathHelper.cos(a0) * ri, MathHelper.sin(a0) * ri, 0, MathHelper.cos(a1) * ri, MathHelper.sin(a1) * ri, 0,
					MathHelper.cos(a1) * ro, MathHelper.sin(a1) * ro, 0, MathHelper.cos(a0) * ro, MathHelper.sin(a0) * ro, 0, color,
					alpha * fade, alpha * fade);
			quad(buffer, pose, MathHelper.cos(a0) * ro, MathHelper.sin(a0) * ro, 0, MathHelper.cos(a1) * ro, MathHelper.sin(a1) * ro, 0,
					MathHelper.cos(a1) * ri, MathHelper.sin(a1) * ri, 0, MathHelper.cos(a0) * ri, MathHelper.sin(a0) * ri, 0, color,
					alpha * fade, alpha * fade);
		}
		matrices.pop();
	}

	/** Schockwelle: flacher, wachsender Ring auf Altarhoehe. */
	private static void shockwave(MatrixStack matrices, VertexConsumerProvider consumers, float since, float[] color) {
		float t = since / FINALE_TICKS;
		float radius = 0.3f + 7.5f * smooth(Math.min(1.0f, t * 1.4f));
		float alpha = (1.0f - t) * 0.8f;
		matrices.push();
		matrices.translate(0.0f, -CORE_Y + 0.6f, 0.0f);
		ring(matrices, consumers, radius, 0.12f + 0.25f * t, color, alpha, 0.0f, 90.0f);
		ring(matrices, consumers, radius * 0.8f, 0.06f, new float[] {1.0f, 1.0f, 1.0f}, alpha * 0.6f, 0.0f, 90.0f);
		matrices.pop();
	}

	private static void quad(VertexConsumer buffer, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2,
			float y2, float z2, float x3, float y3, float z3, float[] color, float a0, float a1) {
		buffer.vertex(pose, x0, y0, z0).color(color[0], color[1], color[2], a0);
		buffer.vertex(pose, x1, y1, z1).color(color[0], color[1], color[2], a0);
		buffer.vertex(pose, x2, y2, z2).color(color[0], color[1], color[2], a1);
		buffer.vertex(pose, x3, y3, z3).color(color[0], color[1], color[2], a1);
	}
}
