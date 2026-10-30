package com.santiq.kingdomomnitrix.client.render.omnitrix;

import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Dampf aus dem Omnitrix bei hoher Hitze (Phase N): ab der Warnschwelle zischt es gelegentlich, ueberhitzt dampft das
 * Geraet dicht. Fuer alle Spieler in Sichtweite (der Zustand ist synchronisiert); als Mensch am linken Handgelenk, als
 * Alien am Abzeichen auf Brusthoehe. Rein clientseitig, kein Netzverkehr.
 */
public final class OmnitrixSteam {
	private static final double RANGE_SQ = 48.0 * 48.0;

	private OmnitrixSteam() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(OmnitrixSteam::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client.world == null || client.player == null || client.isPaused()) {
			return;
		}
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player.squaredDistanceTo(client.player) > RANGE_SQ || player.isInvisible()) {
				continue;
			}
			// nach Hitze, nicht nach Anzeige-Zustand (Nachladen verdeckt sonst die Warnung)
			boolean hot = OmnitrixCore.state(player).isOverheated(client.world.getTime());
			float heat = OmnitrixCore.heat(player);
			float warning = OmnitrixCore.profile(player).heatWarning();
			int every = hot ? 1 : heat >= warning ? Math.max(2, Math.round(6 - 4 * (heat - warning) / Math.max(0.01f, 1 - warning))) : 0;
			if (every == 0 || player.age % every != 0) {
				continue;
			}
			boolean alien = TransformationManager.get(player).isTransformed();
			if (!alien && !OmnitrixWrist.wears(player)) {
				continue;
			}
			Vec3d at = source(player, alien);
			var random = player.getRandom();
			client.world.addParticle(hot || random.nextInt(3) == 0 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_SMOKE,
					at.x + (random.nextDouble() - 0.5) * 0.12, at.y, at.z + (random.nextDouble() - 0.5) * 0.12,
					(random.nextDouble() - 0.5) * 0.02, hot ? 0.06 : 0.035, (random.nextDouble() - 0.5) * 0.02);
			if (hot && random.nextInt(6) == 0) {
				client.world.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 0.0, 0.01, 0.0);
			}
		}
	}

	/** Linkes Handgelenk (Mensch) bzw. Abzeichen (Alien) in Weltkoordinaten. */
	private static Vec3d source(AbstractClientPlayerEntity player, boolean alien) {
		float yaw = player.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
		double leftX = MathHelper.cos(yaw);
		double leftZ = MathHelper.sin(yaw);
		double forwardX = -MathHelper.sin(yaw);
		double forwardZ = MathHelper.cos(yaw);
		if (alien) {
			double height = player.getHeight() * 0.72;
			return new Vec3d(player.getX() + forwardX * 0.3, player.getY() + height, player.getZ() + forwardZ * 0.3);
		}
		return new Vec3d(player.getX() + leftX * 0.36 + forwardX * 0.05, player.getY() + 0.78, player.getZ() + leftZ * 0.36 + forwardZ * 0.05);
	}
}
