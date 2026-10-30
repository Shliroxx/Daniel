package com.santiq.kingdomomnitrix.client.alien;

import com.santiq.kingdomomnitrix.alien.FlightStyle;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Alien-Flug des eigenen Spielers — wie in Alien Evolution und den Superhelden-Mods statt Kreativflug:
 * <ul>
 *   <li><b>Abheben:</b> Sprung in der Luft (oder Doppelsprung wie gewohnt). Landen beendet den Flug.</li>
 *   <li><b>Fahrt in Blickrichtung</b>, auch steil hoch und runter; Seitwaerts mit A/D, Sprung/Schleichen steigt/sinkt.</li>
 *   <li><b>Sprinten = Schub</b> bis zum Hoechsttempo der Flugart ({@link FlightStyle}), mit Schwung (Jetray traege,
 *       Alien X fast ohne Traegheit) und weitem Sichtfeld.</li>
 *   <li><b>Ohne Eingabe Schweben</b>, die Fahrt laeuft je nach Flugart aus.</li>
 * </ul>
 * Minecraft rechnet die Spielerbewegung clientseitig; der Server sieht nur die Positionen, gibt den Flug frei
 * ({@code AlienTraitHandler}) und zeigt Schweif und Ueberschallknall fuer alle. Ein Flug-Tempo-Effekt des Servers
 * (Jetrays Windschatten) wirkt als Faktor ueber {@code abilities.flySpeed}.
 */
public final class AlienFlightControl {
	/** Kreativ-Flugtempo von Minecraft — Bezug fuer den Tempo-Faktor */
	private static final float BASE_FLY_SPEED = 0.05f;
	/** Startschub beim Abheben aus dem Sprung */
	private static final double TAKEOFF_LIFT = 0.3;
	/** so viele Ticks muss der Spieler in der Luft sein, damit ein Sprung abhebt (der Sprung vom Boden zaehlt nicht) */
	private static final int TAKEOFF_AIR_TICKS = 2;
	/** Restfahrt je Tick beim Schweben, senkrecht */
	private static final double HOVER_VERTICAL_DRIFT = 0.6;
	/** zusaetzliches Sichtfeld bei Hoechsttempo */
	private static final float MAX_FOV_BONUS = 0.22f;

	private static Vec3d velocity = Vec3d.ZERO;
	private static boolean jumpWasDown;
	private static int airTicks;
	private static float fov = 1.0f;
	private static float prevFov = 1.0f;

	private AlienFlightControl() {
	}

	public static void tick(MinecraftClient client) {
		prevFov = fov;
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			reset();
			return;
		}
		boolean free = client.currentScreen == null;
		boolean jumpDown = free && client.options.jumpKey.isPressed();
		boolean jumpPressed = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;
		airTicks = player.isOnGround() || player.isClimbing() || player.isTouchingWater() ? 0 : airTicks + 1;

		Optional<FlightStyle> found = player.isCreative() || player.isSpectator() ? Optional.empty() : FlightStyle.of(player);
		if (found.isEmpty() || !player.getAbilities().allowFlying || player.hasVehicle() || player.isFallFlying()) {
			velocity = player.getVelocity();
			easeFov(1.0f);
			return;
		}
		FlightStyle style = found.get();
		if (!player.getAbilities().flying) {
			if (jumpPressed && airTicks > TAKEOFF_AIR_TICKS && !player.isTouchingWater()) {
				player.getAbilities().flying = true;
				player.sendAbilitiesUpdate();
				Vec3d now = player.getVelocity();
				velocity = new Vec3d(now.x, Math.max(now.y, 0.0) + TAKEOFF_LIFT, now.z);
				player.setVelocity(velocity);
				player.fallDistance = 0.0f;
			} else {
				velocity = player.getVelocity();
			}
			easeFov(1.0f);
			return;
		}
		if (player.isTouchingWater()) {
			// im Wasser schwimmt man (Minecraft-Physik), die Fahrt wird uebernommen
			velocity = player.getVelocity();
			easeFov(1.0f);
			return;
		}

		float forward = free ? player.input.movementForward : 0.0f;
		float side = free ? player.input.movementSideways : 0.0f;
		boolean sneak = free && client.options.sneakKey.isPressed();
		boolean boost = free && (client.options.sprintKey.isPressed() || player.isSprinting()) && forward > 0.0f;
		double factor = MathHelper.clamp(player.getAbilities().getFlySpeed() / BASE_FLY_SPEED, 0.5, 2.5);

		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d look = player.getRotationVec(1.0f);
		// wie Minecrafts Eingabe: positive Seitwaerts-Eingabe ist links
		Vec3d sideways = new Vec3d(MathHelper.cos(yaw), 0.0, MathHelper.sin(yaw));
		Vec3d direction = look.multiply(forward).add(sideways.multiply(side));
		if (direction.lengthSquared() > 1.0) {
			direction = direction.normalize();
		}
		double up = jumpDown ? 1.0 : sneak ? -1.0 : 0.0;
		double speed = (boost ? style.boost() : style.cruise()) * factor;
		Vec3d target = direction.multiply(speed).add(0.0, up * style.vertical() * factor, 0.0);

		if (direction.lengthSquared() < 1.0e-4 && up == 0.0) {
			// Schweben: Restfahrt laeuft aus, Steigen und Sinken schneller (sonst schiesst man nach dem Abheben weit hoch)
			velocity = new Vec3d(velocity.x * style.drift(), velocity.y * Math.min(style.drift(), HOVER_VERTICAL_DRIFT),
					velocity.z * style.drift());
			if (velocity.lengthSquared() < 1.0e-4) {
				velocity = Vec3d.ZERO;
			}
		} else {
			// beim Bremsen und Wenden doppelt so schnell nachgeben wie beim Beschleunigen (weniger „Eis“)
			double accel = target.lengthSquared() < velocity.lengthSquared() ? Math.min(1.0, style.accel() * 2.0) : style.accel();
			velocity = velocity.add(target.subtract(velocity).multiply(accel));
		}
		// gegen eine Wand: Fahrt in die Wand weg, sonst „klebt“ der Schwung
		if (player.horizontalCollision) {
			velocity = new Vec3d(velocity.x * 0.2, velocity.y, velocity.z * 0.2);
		}
		if (player.verticalCollision && velocity.y > 0.0) {
			velocity = new Vec3d(velocity.x, 0.0, velocity.z);
		}
		player.setVelocity(velocity);
		player.fallDistance = 0.0f;

		double share = MathHelper.clamp((velocity.length() - style.cruise()) / Math.max(0.1, style.boost() - style.cruise()), 0.0, 1.0);
		easeFov(1.0f + (float) share * MAX_FOV_BONUS);
	}

	private static void easeFov(float target) {
		fov += (target - fov) * 0.2f;
	}

	/** Sichtfeld-Faktor fuer den Schub (weich zwischen den Ticks). */
	public static float fovMultiplier(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevFov, fov);
	}

	public static void reset() {
		velocity = Vec3d.ZERO;
		jumpWasDown = false;
		airTicks = 0;
		fov = 1.0f;
		prevFov = 1.0f;
	}
}
