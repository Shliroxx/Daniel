package com.santiq.kingdomomnitrix.client.gadget;

import com.santiq.kingdomomnitrix.networking.SwingshotStatePayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Zeichnet die Swingshot-Seile aller Spieler in Sichtweite (Zustand kommt vom Server). */
public final class SwingshotRopes {
	private static final Map<Integer, Vec3d> ROPES = new HashMap<>();
	private static final int SEGMENTS = 12;

	private SwingshotRopes() {
	}

	public static void receive(SwingshotStatePayload payload) {
		if (payload.active()) {
			ROPES.put(payload.entityId(), payload.anchor());
		} else {
			ROPES.remove(payload.entityId());
		}
	}

	public static void reset() {
		ROPES.clear();
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ROPES.isEmpty() || client.world == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		ROPES.keySet().removeIf(id -> client.world.getEntityById(id) == null);
		Camera camera = context.camera();
		Vec3d cameraPos = camera.getPos();
		float tickDelta = context.tickCounter().getTickDelta(true);
		MatrixStack matrices = context.matrixStack();
		VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
		matrices.push();
		matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f matrix = entry.getPositionMatrix();
		for (Map.Entry<Integer, Vec3d> rope : ROPES.entrySet()) {
			Entity entity = client.world.getEntityById(rope.getKey());
			if (entity == null) {
				continue;
			}
			Vec3d start = handPosition(client, entity, tickDelta);
			Vec3d end = rope.getValue();
			Vec3d previous = start;
			for (int i = 1; i <= SEGMENTS; i++) {
				Vec3d point = start.lerp(end, i / (double) SEGMENTS);
				int color = i % 2 == 0 ? 0xFFB8C2CC : 0xFF5B6670;
				segment(lines, matrix, entry, previous, point, color);
				previous = point;
			}
		}
		matrices.pop();
	}

	private static Vec3d handPosition(MinecraftClient client, Entity entity, float tickDelta) {
		Vec3d base = entity.getLerpedPos(tickDelta);
		float yaw = MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
		// Rechte Hand: etwas seitlich vor dem Koerper; in der Ego-Sicht naeher an der Kamera.
		boolean firstPerson = entity == client.cameraEntity && client.options.getPerspective().isFirstPerson();
		double side = firstPerson ? 0.35 : 0.4;
		double height = firstPerson ? entity.getStandingEyeHeight() - 0.35 : entity.getHeight() * 0.55;
		Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0.0, -MathHelper.sin(yaw)).multiply(side);
		Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw)).multiply(firstPerson ? 0.4 : 0.15);
		return base.add(0.0, height, 0.0).add(right).add(forward);
	}

	private static void segment(VertexConsumer lines, Matrix4f matrix, MatrixStack.Entry entry, Vec3d from, Vec3d to, int color) {
		Vec3d normal = to.subtract(from).normalize();
		lines.vertex(matrix, (float) from.x, (float) from.y, (float) from.z).color(color)
				.normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
		lines.vertex(matrix, (float) to.x, (float) to.y, (float) to.z).color(color)
				.normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
	}
}
