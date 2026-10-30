package com.santiq.kingdomomnitrix.client.vfx;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Dezente Daueffekte verwandelter Aliens (nur im Client, keine Netzwerklast): Heatblast gibt in Abstaenden einzelne
 * Glutfunken an Kopfflamme und Faeusten ab. Bewusst sparsam — die Figur soll nicht dauerhaft brennen.
 * In der Ego-Sicht des eigenen Spielers entfallen die Kopffunken (sie wuerden die Sicht verdecken).
 */
public final class AlienAmbientVfx {
	private static final Identifier HEATBLAST = KingdomOmnitrix.id("heatblast");
	/** Abstand zwischen zwei Funken je Ort, in Ticks */
	private static final int EMBER_INTERVAL = 5;

	private AlienAmbientVfx() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AlienAmbientVfx::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client.world == null || client.isPaused()) {
			return;
		}
		Random random = client.world.getRandom();
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player.isInvisible() || !TransformationManager.get(player).activeAlien().map(HEATBLAST::equals).orElse(false)) {
				continue;
			}
			if ((player.age + player.getId()) % EMBER_INTERVAL != 0) {
				continue;
			}
			boolean firstPerson = player == client.player && client.options.getPerspective() == Perspective.FIRST_PERSON;
			float yaw = player.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
			Vec3d side = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
			double scale = player.getScale();
			if (!firstPerson && (player.age / EMBER_INTERVAL) % 2 == 0) {
				ember(client, player.getPos().add(0, 2.3 * scale, 0), random, 0.12);           // Kopfflamme
			}
			// Faeuste: abwechselnd rechts/links
			Vec3d fist = player.getPos().add(side.multiply(((player.age / EMBER_INTERVAL) % 2 == 0 ? 0.62 : -0.62) * scale))
					.add(0, 0.75 * scale, 0);
			ember(client, fist, random, 0.12);
		}
	}

	private static void ember(MinecraftClient client, Vec3d pos, Random random, double spread) {
		client.world.addParticle(ParticleTypes.SMALL_FLAME,
				pos.x + (random.nextDouble() - 0.5) * spread, pos.y + random.nextDouble() * spread, pos.z + (random.nextDouble() - 0.5) * spread,
				(random.nextDouble() - 0.5) * 0.01, 0.03 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.01);
	}
}
