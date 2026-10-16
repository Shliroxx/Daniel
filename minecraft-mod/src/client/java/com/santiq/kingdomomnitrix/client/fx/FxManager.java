package com.santiq.kingdomomnitrix.client.fx;

import com.santiq.kingdomomnitrix.client.vfx.CameraShake;
import com.santiq.kingdomomnitrix.networking.FxPayload;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * FX-Engine (Client): nimmt {@link FxPayload}s an, erzeugt daraus Bilder ({@link FxSprite}), Strahlen und Emitter
 * (Aura, Aufladen) und zeichnet alles nach den Entitaeten — Leuchtendes additiv, Rauch und Brandflecken normal.
 */
public final class FxManager {
	private static final int MAX_SPRITES = 6000;
	private static final int FULL_LIGHT = 0xF000F0;
	private static final List<FxSprite> SPRITES = new ArrayList<>();
	private static final List<Beam> BEAMS = new ArrayList<>();
	private static final List<Emitter> EMITTERS = new ArrayList<>();
	private static final Random RANDOM = new Random();

	private record Beam(Vec3d from, Vec3d to, int color, float width, int life, int[] age) {
	}

	private record Emitter(Fx.Kind kind, int entity, int color, float size, int life, int[] age) {
	}

	private FxManager() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FxPayload.ID, (payload, context) -> receive(payload));
		ClientTickEvents.END_WORLD_TICK.register(world -> tick());
		WorldRenderEvents.AFTER_ENTITIES.register(FxManager::render);
	}

	// --- Erzeugen -------------------------------------------------------------------------------

	static void add(FxSprite sprite) {
		if (SPRITES.size() < MAX_SPRITES) {
			SPRITES.add(sprite);
		}
	}

	private static double r() {
		return RANDOM.nextDouble() * 2.0 - 1.0;
	}

	private static Vec3d randomDir() {
		Vec3d v;
		do {
			v = new Vec3d(r(), r(), r());
		} while (v.lengthSquared() > 1.0 || v.lengthSquared() < 1.0e-4);
		return v.normalize();
	}

	public static void receive(FxPayload p) {
		Fx.Kind[] kinds = Fx.Kind.values();
		if (p.kind() < 0 || p.kind() >= kinds.length) {
			return;
		}
		Vec3d pos = new Vec3d(p.x(), p.y(), p.z());
		Vec3d vec = new Vec3d(p.ax(), p.ay(), p.az());
		int color = p.color();
		float size = Math.max(0.05f, p.size());
		switch (kinds[p.kind()]) {
			case EXPLOSION -> explosion(pos, color, size);
			case SHOCKWAVE -> shockwave(pos, color, size, Math.max(4, p.duration()));
			case BEAM -> beam(pos, vec, color, size, Math.max(2, p.duration()));
			case FLASH -> flash(pos, color, size, Math.max(2, p.duration()));
			case FLAMES -> flames(pos, vec, color, size, Math.max(1, p.count()));
			case SPARKS -> sparks(pos, color, size, Math.max(1, p.count()));
			case SHARDS -> shards(pos, color, size, Math.max(1, p.count()));
			case SMOKE -> smoke(pos, color, size, Math.max(1, p.count()));
			case SCORCH -> scorch(pos, size, Math.max(20, p.duration()));
			case AURA, CHARGE -> EMITTERS.add(new Emitter(kinds[p.kind()], p.entity(), color, size, Math.max(1, p.duration()), new int[] {0}));
			case SHAKE -> shake(pos, size, Math.max(1, p.duration()), Math.max(1, p.count()));
		}
	}

	public static void flash(Vec3d pos, int color, float size, int life) {
		add(new FxSprite(FxTexture.FLARE, FxSprite.Shape.BILLBOARD, pos, Vec3d.ZERO, life, size, size * 1.3f, color, 1.0f));
		add(new FxSprite(FxTexture.GLOW, FxSprite.Shape.BILLBOARD, pos, Vec3d.ZERO, life + 3, size * 1.6f, size * 2.2f, color, 0.7f));
	}

	public static void explosion(Vec3d pos, int color, float radius) {
		FxSprite fireball = new FxSprite(FxTexture.EXPLOSION, FxSprite.Shape.BILLBOARD, pos, Vec3d.ZERO, 18, radius * 1.6f, radius * 2.4f, color,
				1.0f);
		fireball.rotation = RANDOM.nextFloat() * MathHelper.TAU;
		add(fireball);
		FxSprite core = new FxSprite(FxTexture.EXPLOSION, FxSprite.Shape.BILLBOARD, pos, Vec3d.ZERO, 14, radius * 0.9f, radius * 1.4f, 0xFFF4D0,
				0.9f);
		core.rotation = RANDOM.nextFloat() * MathHelper.TAU;
		add(core);
		flash(pos, color, radius * 2.2f, 5);
		int flames = 10 + (int) (radius * 6);
		for (int i = 0; i < flames; i++) {
			Vec3d dir = randomDir();
			FxSprite f = new FxSprite(FxTexture.EXPLOSION, FxSprite.Shape.BILLBOARD, pos.add(dir.multiply(radius * 0.3)),
					dir.multiply(0.12 + RANDOM.nextDouble() * 0.18 * radius), 10 + RANDOM.nextInt(8), radius * 0.6f, radius * 1.1f, color, 0.9f);
			f.drag = 0.82f;
			f.rotation = RANDOM.nextFloat() * MathHelper.TAU;
			add(f);
		}
		smoke(pos, 0x8A8078, radius * 1.1f, 3 + (int) (radius * 1.5));
		sparks(pos, color, 0.35f + radius * 0.12f, 14 + (int) (radius * 6));
	}

	public static void shockwave(Vec3d pos, int color, float radius, int ticks) {
		Vec3d ground = pos.add(0.0, 0.08, 0.0);
		add(new FxSprite(FxTexture.RING, FxSprite.Shape.FLAT, ground, Vec3d.ZERO, ticks, 0.6f, radius, color, 1.0f));
		FxSprite inner = new FxSprite(FxTexture.RING, FxSprite.Shape.FLAT, ground.add(0, 0.02, 0), Vec3d.ZERO, ticks + 4, 0.3f, radius * 0.75f,
				0xFFFFFF, 0.6f);
		add(inner);
		add(new FxSprite(FxTexture.GLOW, FxSprite.Shape.FLAT, ground, Vec3d.ZERO, ticks / 2 + 2, radius * 0.4f, radius * 0.9f, color, 0.8f));
		int dust = (int) (radius * 3);
		for (int i = 0; i < dust; i++) {
			double a = RANDOM.nextDouble() * MathHelper.TAU;
			Vec3d dir = new Vec3d(Math.cos(a), 0.0, Math.sin(a));
			FxSprite s = new FxSprite(FxTexture.SMOKE, FxSprite.Shape.BILLBOARD, ground.add(dir.multiply(0.5)),
					dir.multiply(radius / ticks * 1.1).add(0, 0.03, 0), ticks + 10, 0.5f, 1.4f, 0xA89C90, 0.35f);
			s.drag = 0.93f;
			add(s);
		}
	}

	public static void beam(Vec3d from, Vec3d to, int color, float width, int life) {
		BEAMS.add(new Beam(from, to, color, width, life, new int[] {0}));
		flash(from, color, width * 3.0f, Math.min(life, 6));
		flash(to, color, width * 4.0f, Math.min(life, 8));
		sparks(to, color, 0.3f, 6);
	}

	public static void flames(Vec3d pos, Vec3d velocity, int color, float spread, int count) {
		double speed = velocity.length();
		Vec3d dir = speed < 1.0e-4 ? new Vec3d(0, 1, 0) : velocity.multiply(1.0 / speed);
		for (int i = 0; i < count; i++) {
			Vec3d jitter = randomDir().multiply(spread);
			Vec3d v = dir.add(jitter).normalize().multiply(speed * (0.7 + RANDOM.nextDouble() * 0.6));
			FxSprite f = new FxSprite(FxTexture.FLAME, FxSprite.Shape.BILLBOARD, pos.add(v.multiply(RANDOM.nextDouble())), v, 12 + RANDOM.nextInt(8),
					0.35f, 1.3f + spread, color, 0.95f);
			f.drag = 0.9f;
			f.gravity = -0.006f;
			add(f);
			if (RANDOM.nextInt(4) == 0) {
				FxSprite core = new FxSprite(FxTexture.GLOW, FxSprite.Shape.BILLBOARD, pos, v.multiply(0.9), 8, 0.4f, 0.9f, 0xFFF0C0, 0.6f);
				core.drag = 0.9f;
				add(core);
			}
		}
	}

	public static void sparks(Vec3d pos, int color, float speed, int count) {
		for (int i = 0; i < count; i++) {
			FxSprite s = new FxSprite(FxTexture.SPARK, FxSprite.Shape.STRETCHED, pos, randomDir().multiply(speed * (0.4 + RANDOM.nextDouble())),
					8 + RANDOM.nextInt(10), 0.12f, 0.06f, color, 1.0f);
			s.gravity = 0.03f;
			s.drag = 0.9f;
			add(s);
		}
	}

	public static void shards(Vec3d pos, int color, float speed, int count) {
		for (int i = 0; i < count; i++) {
			FxSprite s = new FxSprite(FxTexture.SHARD, FxSprite.Shape.STRETCHED, pos, randomDir().multiply(speed * (0.5 + RANDOM.nextDouble())),
					18 + RANDOM.nextInt(12), 0.35f, 0.25f, color, 1.0f);
			s.gravity = 0.04f;
			s.drag = 0.94f;
			s.stretch = 2.0f;
			add(s);
		}
	}

	public static void smoke(Vec3d pos, int color, float size, int count) {
		for (int i = 0; i < count; i++) {
			Vec3d dir = randomDir();
			FxSprite s = new FxSprite(FxTexture.SMOKE, FxSprite.Shape.BILLBOARD, pos.add(dir.multiply(size * 0.4)),
					dir.multiply(0.04).add(0, 0.03 + RANDOM.nextDouble() * 0.03, 0), 30 + RANDOM.nextInt(20), size * 0.7f, size * 1.8f, color, 0.4f);
			s.drag = 0.95f;
			s.rotation = RANDOM.nextFloat() * MathHelper.TAU;
			s.spin = (RANDOM.nextFloat() - 0.5f) * 0.05f;
			add(s);
		}
	}

	public static void scorch(Vec3d pos, float radius, int life) {
		FxSprite s = new FxSprite(FxTexture.SCORCH, FxSprite.Shape.FLAT, pos.add(0, 0.03, 0), Vec3d.ZERO, life, radius, radius, 0xFFFFFF, 0.9f);
		s.rotation = RANDOM.nextFloat() * MathHelper.TAU;
		add(s);
	}

	private static void shake(Vec3d pos, float strength, int ticks, int radius) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		double distance = client.player.getPos().distanceTo(pos);
		if (distance <= radius) {
			CameraShake.start((float) (strength * (1.0 - distance / (radius + 1.0))), ticks, 0);
		}
	}

	// --- Ablauf ---------------------------------------------------------------------------------

	private static void tick() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			SPRITES.clear();
			BEAMS.clear();
			EMITTERS.clear();
			return;
		}
		for (Iterator<Emitter> it = EMITTERS.iterator(); it.hasNext(); ) {
			Emitter e = it.next();
			Entity entity = client.world.getEntityById(e.entity());
			if (entity == null || ++e.age()[0] > e.life()) {
				it.remove();
				continue;
			}
			emit(e, entity);
		}
		for (Iterator<FxSprite> it = SPRITES.iterator(); it.hasNext(); ) {
			FxSprite s = it.next();
			if (s.follow >= 0) {
				Entity entity = client.world.getEntityById(s.follow);
				if (entity != null) {
					s.followX += s.vx;
					s.followY += s.vy;
					s.followZ += s.vz;
					s.prevX = s.x;
					s.prevY = s.y;
					s.prevZ = s.z;
					s.x = entity.getX() + s.followX;
					s.y = entity.getY() + s.followY;
					s.z = entity.getZ() + s.followZ;
					if (++s.age >= s.life) {
						it.remove();
					}
					continue;
				}
			}
			if (!s.tick()) {
				it.remove();
			}
		}
		BEAMS.removeIf(b -> ++b.age()[0] >= b.life());
	}

	private static void emit(Emitter e, Entity entity) {
		float height = entity.getHeight();
		if (e.kind() == Fx.Kind.AURA) {
			for (int i = 0; i < 3; i++) {
				double a = RANDOM.nextDouble() * MathHelper.TAU;
				double rad = e.size() * (0.5 + RANDOM.nextDouble() * 0.5);
				FxSprite f = new FxSprite(FxTexture.FLAME, FxSprite.Shape.BILLBOARD, Vec3d.ZERO, new Vec3d(0, 0.05 + RANDOM.nextDouble() * 0.04, 0),
						12 + RANDOM.nextInt(6), 0.4f, 0.9f, e.color(), 0.8f);
				f.follow = entity.getId();
				f.followX = Math.cos(a) * rad;
				f.followY = RANDOM.nextDouble() * height * 0.8;
				f.followZ = Math.sin(a) * rad;
				add(f);
			}
			if (e.age()[0] % 3 == 0) {
				FxSprite g = new FxSprite(FxTexture.GLOW, FxSprite.Shape.BILLBOARD, Vec3d.ZERO, Vec3d.ZERO, 6, height * 1.1f, height * 1.2f, e.color(),
						0.35f);
				g.follow = entity.getId();
				g.followY = height * 0.5;
				add(g);
			}
		} else {
			float t = Math.min(1.0f, (float) e.age()[0] / e.life());
			Vec3d center = new Vec3d(0, height * 0.55, 0);
			FxSprite orb = new FxSprite(FxTexture.GLOW, FxSprite.Shape.BILLBOARD, Vec3d.ZERO, Vec3d.ZERO, 3, e.size() * t, e.size() * t, e.color(), 0.9f);
			orb.follow = entity.getId();
			orb.followY = center.y;
			add(orb);
			for (int i = 0; i < 4; i++) {
				Vec3d dir = randomDir();
				Vec3d start = center.add(dir.multiply(e.size() * 2.2));
				FxSprite s = new FxSprite(FxTexture.SPARK, FxSprite.Shape.STRETCHED, Vec3d.ZERO, dir.multiply(-e.size() * 2.2 / 8.0), 8, 0.1f, 0.05f,
						e.color(), 1.0f);
				s.follow = entity.getId();
				s.followX = start.x;
				s.followY = start.y;
				s.followZ = start.z;
				s.drag = 1.0f;
				add(s);
			}
		}
	}

	// --- Zeichnen -------------------------------------------------------------------------------

	private static void render(WorldRenderContext context) {
		if (SPRITES.isEmpty() && BEAMS.isEmpty()) {
			return;
		}
		VertexConsumerProvider consumers = context.consumers();
		if (consumers == null) {
			return;
		}
		Camera camera = context.camera();
		Vec3d cam = camera.getPos();
		float tickDelta = context.tickCounter().getTickDelta(false);
		MatrixStack matrices = new MatrixStack();
		MatrixStack.Entry entry = matrices.peek();
		Quaternionf rotation = camera.getRotation();
		for (FxSprite s : SPRITES) {
			VertexConsumer buffer = consumers.getBuffer(s.texture.layer());
			drawSprite(entry, buffer, s, cam, rotation, tickDelta);
		}
		for (Beam b : BEAMS) {
			drawBeam(entry, consumers.getBuffer(FxTexture.BEAM.layer()), b, cam, tickDelta);
		}
	}

	private static float[] uv(FxSprite s) {
		int grid = s.texture.grid;
		if (grid == 1) {
			return new float[] {0, 0, 1, 1};
		}
		int frame = s.frame >= 0 ? s.frame : Math.min(s.texture.frames() - 1, (int) (s.progress(0) * s.texture.frames()));
		float step = 1.0f / grid;
		float u0 = (frame % grid) * step;
		float v0 = (frame / grid) * step;
		return new float[] {u0, v0, u0 + step, v0 + step};
	}

	private static void drawSprite(MatrixStack.Entry entry, VertexConsumer buffer, FxSprite s, Vec3d cam, Quaternionf rotation, float tickDelta) {
		float px = (float) (MathHelper.lerp(tickDelta, s.prevX, s.x) - cam.x);
		float py = (float) (MathHelper.lerp(tickDelta, s.prevY, s.y) - cam.y);
		float pz = (float) (MathHelper.lerp(tickDelta, s.prevZ, s.z) - cam.z);
		float size = s.size(tickDelta);
		float fade = s.fade(tickDelta);
		if (fade <= 0.003f || size <= 0.001f) {
			return;
		}
		float[] uv = uv(s);
		Vector3f[] corners;
		switch (s.shape) {
			case FLAT -> {
				corners = new Vector3f[] {new Vector3f(-1, 0, -1), new Vector3f(-1, 0, 1), new Vector3f(1, 0, 1), new Vector3f(1, 0, -1)};
				for (Vector3f c : corners) {
					c.rotateY(s.rotation).mul(size);
				}
			}
			case STRETCHED -> {
				Vector3f axis = new Vector3f((float) s.vx, (float) s.vy, (float) s.vz);
				float speed = axis.length();
				if (speed < 1.0e-4f) {
					axis.set(0, 1, 0);
				} else {
					axis.div(speed);
				}
				float length = Math.max(size, speed * s.stretch);
				Vector3f toCam = new Vector3f(px, py, pz).normalize();
				Vector3f side = new Vector3f(axis).cross(toCam);
				if (side.lengthSquared() < 1.0e-6f) {
					side.set(1, 0, 0);
				}
				side.normalize().mul(size * 0.5f);
				axis.mul(length);
				corners = new Vector3f[] {new Vector3f(axis).negate().sub(side), new Vector3f(axis).negate().add(side), new Vector3f(axis).add(side),
						new Vector3f(axis).sub(side)};
			}
			default -> {
				corners = new Vector3f[] {new Vector3f(-1, -1, 0), new Vector3f(-1, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, -1, 0)};
				Quaternionf q = new Quaternionf(rotation).rotateZ(s.rotation);
				for (Vector3f c : corners) {
					c.rotate(q).mul(size);
				}
			}
		}
		int color = s.color;
		float r = ((color >> 16) & 0xFF) / 255.0f;
		float g = ((color >> 8) & 0xFF) / 255.0f;
		float b = (color & 0xFF) / 255.0f;
		float a;
		if (s.texture.additive) {
			r *= fade;
			g *= fade;
			b *= fade;
			a = 1.0f;
		} else {
			a = fade;
		}
		Matrix4f pose = entry.getPositionMatrix();
		float[][] uvs = {{uv[2], uv[3]}, {uv[2], uv[1]}, {uv[0], uv[1]}, {uv[0], uv[3]}};
		// beide Wicklungen: von vorn und hinten sichtbar (Ringe am Boden, gestreckte Funken)
		for (int i = 0; i < 4; i++) {
			vertex(buffer, entry, pose, px + corners[i].x, py + corners[i].y, pz + corners[i].z, r, g, b, a, uvs[i][0], uvs[i][1]);
		}
		for (int i = 3; i >= 0; i--) {
			vertex(buffer, entry, pose, px + corners[i].x, py + corners[i].y, pz + corners[i].z, r, g, b, a, uvs[i][0], uvs[i][1]);
		}
	}

	private static void drawBeam(MatrixStack.Entry entry, VertexConsumer buffer, Beam beam, Vec3d cam, float tickDelta) {
		float t = Math.min(1.0f, (beam.age()[0] + tickDelta) / beam.life());
		float width = beam.width() * (t < 0.15f ? t / 0.15f : t > 0.7f ? 1.0f - (t - 0.7f) / 0.3f : 1.0f);
		if (width <= 0.001f) {
			return;
		}
		Vector3f from = new Vector3f((float) (beam.from().x - cam.x), (float) (beam.from().y - cam.y), (float) (beam.from().z - cam.z));
		Vector3f to = new Vector3f((float) (beam.to().x - cam.x), (float) (beam.to().y - cam.y), (float) (beam.to().z - cam.z));
		Vector3f axis = new Vector3f(to).sub(from);
		float length = axis.length();
		if (length < 1.0e-3f) {
			return;
		}
		Vector3f mid = new Vector3f(from).add(to).mul(0.5f);
		Vector3f side = new Vector3f(axis).cross(mid).normalize();
		float scroll = (beam.age()[0] + tickDelta) * 0.15f;
		float u1 = scroll + length / Math.max(0.2f, beam.width() * 4.0f);
		int color = beam.color();
		Matrix4f pose = entry.getPositionMatrix();
		for (float layer : new float[] {1.0f, 0.4f}) {
			Vector3f s = new Vector3f(side).mul(width * layer);
			float r = layer < 1.0f ? 1.0f : ((color >> 16) & 0xFF) / 255.0f;
			float g = layer < 1.0f ? 1.0f : ((color >> 8) & 0xFF) / 255.0f;
			float b = layer < 1.0f ? 1.0f : (color & 0xFF) / 255.0f;
			Vector3f[] c = {new Vector3f(from).sub(s), new Vector3f(from).add(s), new Vector3f(to).add(s), new Vector3f(to).sub(s)};
			float[][] uvs = {{scroll, 1}, {scroll, 0}, {u1, 0}, {u1, 1}};
			for (int i = 0; i < 4; i++) {
				vertex(buffer, entry, pose, c[i].x, c[i].y, c[i].z, r, g, b, 1.0f, uvs[i][0], uvs[i][1]);
			}
			for (int i = 3; i >= 0; i--) {
				vertex(buffer, entry, pose, c[i].x, c[i].y, c[i].z, r, g, b, 1.0f, uvs[i][0], uvs[i][1]);
			}
		}
	}

	static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f pose, float x, float y, float z, float r, float g, float b, float a,
			float u, float v) {
		buffer.vertex(pose, x, y, z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(FULL_LIGHT).normal(entry, 0, 1, 0);
	}
}
