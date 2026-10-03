package com.santiq.kingdomomnitrix.util;

import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Sichtlinien-Hilfen: Was schaut der Spieler an — ein Lebewesen oder einen Block?
 */
public final class Targeting {
	private Targeting() {
	}

	/** Naechstes lebendes Ziel auf der Blicklinie, durch Bloecke begrenzt. */
	public static Optional<LivingEntity> findLivingTarget(LivingEntity user, double range) {
		World world = user.getWorld();
		Vec3d start = user.getEyePos();
		Vec3d direction = user.getRotationVec(1.0f);
		Vec3d end = lookEnd(user, range);

		Box area = user.getBoundingBox().stretch(direction.multiply(range)).expand(1.0);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity candidate : world.getOtherEntities(user, area,
				e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
			Box hitbox = candidate.getBoundingBox().expand(candidate.getTargetingMargin() + 0.3);
			Optional<Vec3d> hit = hitbox.raycast(start, end);
			if (hit.isPresent()) {
				double distance = start.squaredDistanceTo(hit.get());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = (LivingEntity) candidate;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Nahkampf-Ziel: zuerst exakt auf der Blicklinie, sonst das naechste Lebewesen im Kegel vor dem Spieler (cos ≥
	 * {@code minDot}, z. B. 0,7 ≈ 45°) mit freier Sicht. Verzeiht knappes Zielen bei Bissen und Hieben.
	 */
	public static Optional<LivingEntity> findMeleeTarget(LivingEntity user, double range, double minDot,
			java.util.function.Predicate<LivingEntity> allowed) {
		Optional<LivingEntity> direct = findLivingTarget(user, range).filter(allowed);
		if (direct.isPresent()) {
			return direct;
		}
		Vec3d eye = user.getEyePos();
		Vec3d look = user.getRotationVec(1.0f);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity candidate : user.getWorld().getOtherEntities(user, user.getBoundingBox().expand(range),
				e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && allowed.test(living))) {
			Vec3d center = candidate.getBoundingBox().getCenter();
			Vec3d to = center.subtract(eye);
			double distance = to.length();
			if (distance > range + candidate.getWidth() * 0.5 || distance < 1.0E-4 || to.normalize().dotProduct(look) < minDot) {
				continue;
			}
			BlockHitResult wall = user.getWorld().raycast(new RaycastContext(eye, center, RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE, user));
			if (wall.getType() != HitResult.Type.MISS && wall.getPos().squaredDistanceTo(eye) < distance * distance - 0.25) {
				continue;
			}
			if (distance < bestDistance) {
				bestDistance = distance;
				best = (LivingEntity) candidate;
			}
		}
		return Optional.ofNullable(best);
	}

	/** Endpunkt der Blicklinie: erster getroffener Block oder die volle Reichweite. */
	public static Vec3d lookEnd(LivingEntity user, double range) {
		Vec3d start = user.getEyePos();
		Vec3d end = start.add(user.getRotationVec(1.0f).multiply(range));
		BlockHitResult blockHit = user.getWorld().raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
		return blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getPos();
	}
}
