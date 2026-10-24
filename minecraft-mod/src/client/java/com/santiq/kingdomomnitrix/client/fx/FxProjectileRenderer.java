package com.santiq.kingdomomnitrix.client.fx;

import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Geschosse der Faehigkeiten als leuchtende Kugeln mit Lichtschweif (statt eines kleinen fliegenden Items): Halo,
 * heller Kern, bei Feuer ein brodelnder Feuerball, dahinter ein ausblendendes Band entlang der letzten Positionen.
 * OmniWrench und Stacheln bleiben Items.
 */
public class FxProjectileRenderer extends EntityRenderer<HeroProjectileEntity> {
	private static final int TRAIL = 14;
	private static final Map<Integer, Deque<Vec3d>> TRAILS = new HashMap<>();
	private final FlyingItemEntityRenderer<HeroProjectileEntity> items;

	public FxProjectileRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.items = new FlyingItemEntityRenderer<>(context, 1.0f, true);
	}

	@Override
	public Identifier getTexture(HeroProjectileEntity entity) {
		return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
	}

	@Override
	public void render(HeroProjectileEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		HeroProjectileEntity.Kind kind = entity.getKind();
		if (kind == HeroProjectileEntity.Kind.WRENCH || kind == HeroProjectileEntity.Kind.QUILL) {
			items.render(entity, yaw, tickDelta, matrices, consumers, light);
			return;
		}
		int color = HeroProjectileEntity.fxColor(kind);
		Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
		Quaternionf rotation = camera.getRotation();
		Vec3d pos = entity.getLerpedPos(tickDelta);
		float time = entity.age + tickDelta;
		MatrixStack.Entry entry = matrices.peek();
		boolean fire = kind == HeroProjectileEntity.Kind.FIRE;
		float size = fire ? 0.55f : 0.4f;
		billboard(entry, consumers.getBuffer(FxTexture.GLOW.layer()), rotation, size * 2.6f, color, 0.8f, 0, 0, 1, 1, 0);
		if (fire) {
			int frame = ((int) (time * 1.5f)) % 6;
			float u0 = (frame % 4) / 4.0f;
			float v0 = (frame / 4) / 4.0f;
			billboardAlpha(entry, consumers.getBuffer((FxManager.warm(color) ? FxTexture.EXPLOSION_HOT : FxTexture.EXPLOSION).layer()), rotation, size * 1.4f, FxManager.warm(color) ? 0xFFFFFF : color, u0, v0, u0 + 0.25f, v0 + 0.25f,
					time * 0.3f);
		}
		billboard(entry, consumers.getBuffer(FxTexture.FLARE.layer()), rotation, size * 1.2f, 0xFFFFFF, 0.9f, 0, 0, 1, 1, time * 0.2f);
		trail(entity, pos, entry, consumers.getBuffer(FxTexture.BEAM.layer()), camera.getPos(), color, size);
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	private static void billboard(MatrixStack.Entry entry, VertexConsumer buffer, Quaternionf rotation, float size, int color, float alpha, float u0,
			float v0, float u1, float v1, float roll) {
		Vector3f[] c = {new Vector3f(-1, -1, 0), new Vector3f(-1, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, -1, 0)};
		Quaternionf q = new Quaternionf(rotation).rotateZ(roll);
		float r = ((color >> 16) & 0xFF) / 255.0f * alpha;
		float g = ((color >> 8) & 0xFF) / 255.0f * alpha;
		float b = (color & 0xFF) / 255.0f * alpha;
		float[][] uv = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
		Matrix4f pose = entry.getPositionMatrix();
		for (int i = 0; i < 4; i++) {
			c[i].rotate(q).mul(size);
			FxManager.vertex(buffer, entry, pose, c[i].x, c[i].y, c[i].z, r, g, b, 1.0f, uv[i][0], uv[i][1]);
		}
	}

	private static void billboardAlpha(MatrixStack.Entry entry, VertexConsumer buffer, Quaternionf rotation, float size, int color, float u0,
			float v0, float u1, float v1, float roll) {
		Vector3f[] c = {new Vector3f(-1, -1, 0), new Vector3f(-1, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, -1, 0)};
		Quaternionf q = new Quaternionf(rotation).rotateZ(roll);
		float r = ((color >> 16) & 0xFF) / 255.0f;
		float g = ((color >> 8) & 0xFF) / 255.0f;
		float b = (color & 0xFF) / 255.0f;
		float[][] uv = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
		Matrix4f pose = entry.getPositionMatrix();
		for (int i = 0; i < 4; i++) {
			c[i].rotate(q).mul(size);
		}
		for (int i = 0; i < 4; i++) {
			FxManager.vertex(buffer, entry, pose, c[i].x, c[i].y, c[i].z, r, g, b, 1.0f, uv[i][0], uv[i][1]);
		}
		for (int i = 3; i >= 0; i--) {
			FxManager.vertex(buffer, entry, pose, c[i].x, c[i].y, c[i].z, r, g, b, 1.0f, uv[i][0], uv[i][1]);
		}
	}

	/** Schweif: Band durch die letzten Positionen, zur Kamera gedreht, nach hinten duenner und dunkler. */
	private static void trail(HeroProjectileEntity entity, Vec3d pos, MatrixStack.Entry entry, VertexConsumer buffer, Vec3d cam, int color, float size) {
		Deque<Vec3d> points = TRAILS.computeIfAbsent(entity.getId(), id -> new ArrayDeque<>());
		if (points.isEmpty() || points.peekFirst().squaredDistanceTo(pos) > 0.04) {
			points.addFirst(pos);
			while (points.size() > TRAIL) {
				points.removeLast();
			}
		}
		if (TRAILS.size() > 256) {
			TRAILS.keySet().removeIf(id -> MinecraftClient.getInstance().world == null
					|| MinecraftClient.getInstance().world.getEntityById(id) == null);
		}
		Vec3d[] p = points.toArray(new Vec3d[0]);
		if (p.length < 2) {
			return;
		}
		Matrix4f pose = entry.getPositionMatrix();
		float cr = ((color >> 16) & 0xFF) / 255.0f;
		float cg = ((color >> 8) & 0xFF) / 255.0f;
		float cb = (color & 0xFF) / 255.0f;
		for (int i = 0; i < p.length - 1; i++) {
			float f0 = 1.0f - (float) i / (p.length - 1);
			float f1 = 1.0f - (float) (i + 1) / (p.length - 1);
			Vector3f a = rel(p[i], pos);
			Vector3f b = rel(p[i + 1], pos);
			Vector3f axis = new Vector3f(b).sub(a);
			Vector3f toCam = new Vector3f((float) (pos.x + a.x - cam.x), (float) (pos.y + a.y - cam.y), (float) (pos.z + a.z - cam.z));
			Vector3f side = axis.cross(toCam);
			if (side.lengthSquared() < 1.0e-8f) {
				continue;
			}
			side.normalize();
			Vector3f sa = new Vector3f(side).mul(size * 0.8f * f0);
			Vector3f sb = new Vector3f(side).mul(size * 0.8f * f1);
			Vector3f[] c = {new Vector3f(a).sub(sa), new Vector3f(a).add(sa), new Vector3f(b).add(sb), new Vector3f(b).sub(sb)};
			float[] fade = {f0, f0, f1, f1};
			float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
			for (int k = 0; k < 4; k++) {
				FxManager.vertex(buffer, entry, pose, c[k].x, c[k].y, c[k].z, cr * fade[k], cg * fade[k], cb * fade[k], 1.0f, uv[k][0], uv[k][1]);
			}
			for (int k = 3; k >= 0; k--) {
				FxManager.vertex(buffer, entry, pose, c[k].x, c[k].y, c[k].z, cr * fade[k], cg * fade[k], cb * fade[k], 1.0f, uv[k][0], uv[k][1]);
			}
		}
	}

	private static Vector3f rel(Vec3d point, Vec3d origin) {
		return new Vector3f((float) (point.x - origin.x), (float) (point.y - origin.y), (float) (point.z - origin.z));
	}

}
