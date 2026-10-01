package com.santiq.kingdomomnitrix.client.combat;

import com.santiq.kingdomomnitrix.networking.LockOnPayload;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Lock-On auf dem Client: Zielauswahl, Zielwechsel, Kameranachfuehrung und Aufloesen.
 * Der Server erfaehrt das Ziel per {@link LockOnPayload} und prueft es selbst.
 */
public final class ClientLockOn {
	private static final double ACQUIRE_RANGE = 24.0;
	private static final double KEEP_RANGE = 32.0;
	private static final double MAX_ANGLE_COS = Math.cos(Math.toRadians(60));
	private static final int LOST_SIGHT_TICKS = 40;
	private static final float CAMERA_FOLLOW = 0.35f;

	private static int targetId = -1;
	private static int ticksWithoutSight;

	private ClientLockOn() {
	}

	@Nullable
	public static LivingEntity target(MinecraftClient client) {
		if (targetId < 0 || client.world == null) {
			return null;
		}
		Entity entity = client.world.getEntityById(targetId);
		return entity instanceof LivingEntity living ? living : null;
	}

	public static boolean isTarget(Entity entity) {
		return targetId >= 0 && entity.getId() == targetId;
	}

	/** Taste: kein Ziel → bestes Ziel; Ziel vorhanden → naechstes Ziel; Schleichen + Taste → loesen. */
	public static void onKeyPressed(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		if (player.isSneaking() && targetId >= 0) {
			clear();
			return;
		}
		List<LivingEntity> candidates = candidates(client, player);
		if (candidates.isEmpty()) {
			clear();
			return;
		}
		if (targetId < 0) {
			setTarget(candidates.get(0).getId());
			return;
		}
		int index = -1;
		for (int i = 0; i < candidates.size(); i++) {
			if (candidates.get(i).getId() == targetId) {
				index = i;
				break;
			}
		}
		setTarget(candidates.get((index + 1) % candidates.size()).getId());
	}

	/** Prueft jeden Tick, ob das Ziel noch gueltig ist. */
	public static void tick(MinecraftClient client) {
		if (targetId < 0) {
			return;
		}
		ClientPlayerEntity player = client.player;
		LivingEntity target = target(client);
		if (player == null || target == null || !target.isAlive() || player.squaredDistanceTo(target) > KEEP_RANGE * KEEP_RANGE) {
			clear();
			return;
		}
		ticksWithoutSight = player.canSee(target) ? 0 : ticksWithoutSight + 1;
		if (ticksWithoutSight > LOST_SIGHT_TICKS) {
			clear();
		}
	}

	/** Dreht die Kamera pro Frame weich zum Ziel. */
	public static void updateCamera(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		LivingEntity target = target(client);
		if (player == null || target == null || client.currentScreen != null) {
			return;
		}
		Vec3d eye = player.getEyePos();
		Vec3d aim = target.getPos().add(0, target.getHeight() * 0.6, 0);
		Vec3d delta = aim.subtract(eye);
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		float desiredYaw = (float) (MathHelper.atan2(delta.z, delta.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
		float desiredPitch = (float) -(MathHelper.atan2(delta.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
		float yawStep = MathHelper.wrapDegrees(desiredYaw - player.getYaw()) * CAMERA_FOLLOW;
		float pitchStep = (MathHelper.clamp(desiredPitch, -60.0f, 60.0f) - player.getPitch()) * CAMERA_FOLLOW;
		// changeLookDirection multipliziert mit 0.15 und pflegt die Interpolationswerte mit.
		player.changeLookDirection(yawStep / 0.15, pitchStep / 0.15);
	}

	public static void clear() {
		if (targetId >= 0) {
			targetId = -1;
			ticksWithoutSight = 0;
			if (ClientPlayNetworking.canSend(LockOnPayload.ID)) {
				ClientPlayNetworking.send(new LockOnPayload(-1));
			}
		}
	}

	/** Beim Verlassen der Welt aufraeumen, ohne Paket. */
	public static void reset() {
		targetId = -1;
		ticksWithoutSight = 0;
	}

	private static void setTarget(int entityId) {
		targetId = entityId;
		ticksWithoutSight = 0;
		ClientPlayNetworking.send(new LockOnPayload(entityId));
	}

	/** Gueltige Ziele, sortiert nach Abstand zur Blickrichtung (Fadenkreuz zuerst). */
	private static List<LivingEntity> candidates(MinecraftClient client, ClientPlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		return client.world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(ACQUIRE_RANGE),
						e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity)
								&& !e.isInvisibleTo(player) && player.canSee(e)
								&& e.squaredDistanceTo(player) <= ACQUIRE_RANGE * ACQUIRE_RANGE
								&& angleCos(eye, look, e) >= MAX_ANGLE_COS)
				.stream()
				.sorted(Comparator.comparingDouble(e -> -angleCos(eye, look, e)))
				.toList();
	}

	private static double angleCos(Vec3d eye, Vec3d look, LivingEntity entity) {
		Vec3d to = entity.getPos().add(0, entity.getHeight() / 2, 0).subtract(eye);
		return to.lengthSquared() < 1.0E-6 ? 1.0 : to.normalize().dotProduct(look);
	}
}
