package com.santiq.kingdomomnitrix.vfx;

import com.santiq.kingdomomnitrix.registry.ModParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Zusammengesetzte Effekte (serverseitig gestartet, an alle Spieler in Sichtweite gesendet).
 * Jede Methode ist eine fertige „Choreografie“, damit Spielcode nur noch einen Aufruf braucht.
 */
public final class Vfx {
	private Vfx() {
	}

	/** Einzelnes Partikel mit genauer Richtung (count 0 = delta ist die Geschwindigkeit). */
	public static void directed(ServerWorld world, ParticleEffect effect, Vec3d pos, Vec3d velocity) {
		world.spawnParticles(effect, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
	}

	/** Partikel in alle Richtungen aus einem Punkt. */
	public static void burst(ServerWorld world, ParticleEffect effect, Vec3d pos, int count, double speed) {
		for (int i = 0; i < count; i++) {
			double yaw = world.random.nextDouble() * Math.PI * 2;
			double pitch = (world.random.nextDouble() - 0.5) * Math.PI;
			Vec3d dir = new Vec3d(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
			directed(world, effect, pos, dir.multiply(speed * (0.5 + world.random.nextDouble() * 0.5)));
		}
	}

	// --- Omnitrix -------------------------------------------------------------------------------

	/** Verwandlung: gruener Blitz, aufsteigende DNA-Doppelhelix, Funkenring. */
	public static void transform(ServerWorld world, Entity player) {
		Vec3d center = new Vec3d(player.getX(), player.getBodyY(0.5), player.getZ());
		directed(world, ModParticles.OMNITRIX_FLASH, center, Vec3d.ZERO);
		double height = Math.max(2.0, player.getHeight() + 0.4);
		int steps = 28;
		for (int i = 0; i < steps; i++) {
			double t = (double) i / steps;
			double angle = t * Math.PI * 4;
			for (int strand = 0; strand < 2; strand++) {
				double a = angle + strand * Math.PI;
				Vec3d pos = new Vec3d(player.getX() + Math.cos(a) * 0.8, player.getY() + t * height, player.getZ() + Math.sin(a) * 0.8);
				directed(world, ModParticles.DNA_HELIX, pos, new Vec3d(0, 0.03, 0));
			}
		}
		ring(world, ModParticles.KEYBLADE_SPARK, new Vec3d(player.getX(), player.getY() + 0.1, player.getZ()), 1.2, 16, 0.12);
	}

	/** Rueckverwandlung: roter Funkenregen nach aussen. */
	public static void revert(ServerWorld world, Entity player) {
		Vec3d center = new Vec3d(player.getX(), player.getBodyY(0.5), player.getZ());
		burst(world, ModParticles.OMNITRIX_REVERT, center, 26, 0.35);
	}

	// --- Kampf ----------------------------------------------------------------------------------

	/**
	 * Schwung-Spur vor dem Spieler: Bogen von links nach rechts (gerade Schritte) oder umgekehrt.
	 * {@code air} legt den Bogen schraeg von oben nach unten.
	 */
	public static void slash(ServerWorld world, PlayerEntity player, int step, boolean air) {
		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
		Vec3d right = new Vec3d(-forward.z, 0, forward.x);
		Vec3d origin = new Vec3d(player.getX(), player.getBodyY(0.6), player.getZ());
		int points = 16;
		boolean leftToRight = step % 2 == 0;
		for (int i = 0; i < points; i++) {
			double t = (double) i / (points - 1);
			double side = (leftToRight ? t : 1 - t) * 2 - 1;
			double reach = 1.4 + Math.cos(side * Math.PI / 2) * 0.5;
			double lift = air ? (0.6 - t * 1.2) : side * 0.15;
			Vec3d pos = origin.add(forward.multiply(reach)).add(right.multiply(side * 1.3)).add(0, lift, 0);
			directed(world, ModParticles.SLASH, pos, forward.multiply(0.05));
		}
	}

	/** Treffer: heller Funkenstern, mit Keyblade zusaetzlich goldene Funken. */
	public static void hit(ServerWorld world, Entity target, boolean keyblade, boolean critical) {
		Vec3d pos = new Vec3d(target.getX(), target.getBodyY(0.55), target.getZ());
		directed(world, ModParticles.HIT_SPARK, pos, Vec3d.ZERO);
		if (keyblade || critical) {
			burst(world, ModParticles.KEYBLADE_SPARK, pos, critical ? 12 : 6, 0.3);
		}
	}

	/** Finisher: Goldring am Boden, der sich ausbreitet, und Funkenkranz. */
	public static void finisher(ServerWorld world, PlayerEntity player) {
		Vec3d feet = new Vec3d(player.getX(), player.getY() + 0.15, player.getZ());
		directed(world, ModParticles.FINISHER_RING, feet.add(0, 0.8, 0), Vec3d.ZERO);
		ring(world, ModParticles.KEYBLADE_SPARK, feet, 1.0, 20, 0.35);
	}

	/** Partikel auf einem waagrechten Kreis, die nach aussen fliegen. */
	public static void ring(ServerWorld world, ParticleEffect effect, Vec3d center, double radius, int count, double speed) {
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2 * i / count;
			Vec3d dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
			directed(world, effect, center.add(dir.multiply(radius)), dir.multiply(speed));
		}
	}

	// --- Magie ----------------------------------------------------------------------------------

	public static void thunderStrike(ServerWorld world, Vec3d point) {
		burst(world, ModParticles.THUNDER_SPARK, point.add(0, 0.6, 0), 24, 0.45);
		directed(world, ModParticles.HIT_SPARK, point.add(0, 0.8, 0), Vec3d.ZERO);
	}

	public static void cure(ServerWorld world, Entity target) {
		for (int i = 0; i < 10; i++) {
			double angle = Math.PI * 2 * i / 10;
			Vec3d pos = new Vec3d(target.getX() + Math.cos(angle) * 0.7, target.getY() + 0.2 + world.random.nextDouble() * 0.6,
					target.getZ() + Math.sin(angle) * 0.7);
			directed(world, ModParticles.CURE_LEAF, pos, new Vec3d(0, 0.06, 0));
		}
	}

	// --- Technik --------------------------------------------------------------------------------

	/** Muendungsfeuer vor der Waffe in Blickrichtung. */
	public static void muzzle(ServerWorld world, PlayerEntity player) {
		Vec3d look = player.getRotationVec(1.0f);
		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0, -MathHelper.sin(yaw)).multiply(-0.35);
		Vec3d pos = player.getEyePos().add(look.multiply(0.9)).add(right).add(0, -0.25, 0);
		directed(world, ModParticles.MUZZLE_FLASH, pos, look.multiply(0.05));
	}

	public static void explosion(ServerWorld world, Vec3d pos) {
		directed(world, ModParticles.HIT_SPARK, pos, Vec3d.ZERO);
		burst(world, ModParticles.FIRE_EMBER, pos, 24, 0.4);
		burst(world, ModParticles.PLASMA, pos, 10, 0.25);
	}

	public static void rotorWind(ServerWorld world, Entity player) {
		directed(world, ModParticles.ROTOR_WIND, new Vec3d(player.getX(), player.getY() + player.getHeight() + 0.2, player.getZ()),
				new Vec3d(0, -0.02, 0));
	}
}
