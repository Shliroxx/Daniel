package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Gemeinsame Bausteine der spaeter dazugekommenen Aliens (Rath, Spidermonkey, Way Big, Alien X, Brainstorm, Goop):
 * Schaden ohne Rueckkopplung, Strahlen, Kegel, Partikellinien und Festhalten (Wurzeln/Netze/Kokons) mit sauberem
 * Aufraeumen. Jedes Alien behaelt sein eigenes System in seiner eigenen Klasse.
 */
final class AlienKit {
	/** gerade laufender Zusatzschaden — Ereignis-Handler sollen darauf nicht erneut reagieren */
	static boolean bonusHit;

	/** festgehaltene Ziele: bis wann, in welcher Welt, Partikel */
	private static final Map<UUID, Hold> HOLDS = new HashMap<>();

	private record Hold(RegistryKey<World> world, long until, ParticleEffect particle, boolean lift) {
	}

	private AlienKit() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AlienKit::tickHolds);
	}

	static DustParticleEffect dust(String hex, float size) {
		int c = Integer.parseInt(hex.substring(1), 16);
		return new DustParticleEffect(new Vector3f(((c >> 16) & 0xFF) / 255.0f, ((c >> 8) & 0xFF) / 255.0f, (c & 0xFF) / 255.0f), size);
	}

	/** Schaden als Zusatztreffer (loest keine weiteren Alien-Boni aus). */
	static void hit(LivingEntity target, DamageSource source, float damage) {
		boolean before = bonusHit;
		bonusHit = true;
		try {
			target.timeUntilRegen = 0;
			target.damage(source, damage);
		} finally {
			bonusHit = before;
		}
	}

	static void magic(ServerWorld world, ServerPlayerEntity owner, LivingEntity target, float damage) {
		hit(target, world.getDamageSources().indirectMagic(owner, owner), damage);
	}

	static void melee(ServerWorld world, ServerPlayerEntity owner, LivingEntity target, float damage) {
		hit(target, world.getDamageSources().playerAttack(owner), damage);
	}

	static boolean foe(ServerPlayerEntity owner, Entity entity) {
		return entity instanceof LivingEntity living && living != owner && living.isAlive() && PartyRules.canHarm(owner, living);
	}

	static List<LivingEntity> around(ServerWorld world, ServerPlayerEntity owner, Vec3d center, double radius) {
		return world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(radius),
				e -> foe(owner, e) && e.getPos().add(0, e.getHeight() * 0.5, 0).distanceTo(center) <= radius + e.getWidth() * 0.5);
	}

	/** Kegel vor dem Spieler (Augen, Blickrichtung). */
	static List<LivingEntity> cone(ServerWorld world, ServerPlayerEntity owner, double range, double minDot) {
		Vec3d eye = owner.getEyePos();
		Vec3d look = owner.getRotationVec(1.0f);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : around(world, owner, eye, range)) {
			Vec3d to = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(eye);
			if (to.lengthSquared() < 1.0E-4 || to.normalize().dotProduct(look) >= minDot) {
				out.add(e);
			}
		}
		return out;
	}

	/** Blickstrahl bis zur Wand: erstes Wesen (oder leer) und Endpunkt. */
	record Beam(Optional<LivingEntity> target, Vec3d end) {
	}

	static Beam beam(ServerWorld world, ServerPlayerEntity owner, double range) {
		Vec3d eye = owner.getEyePos();
		Vec3d end = eye.add(owner.getRotationVec(1.0f).multiply(range));
		HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner));
		Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		Optional<LivingEntity> found = Targeting.findLivingTarget(owner, eye.distanceTo(stop)).filter(e -> foe(owner, e));
		return new Beam(found, found.map(e -> e.getPos().add(0, e.getHeight() * 0.5, 0)).orElse(stop));
	}

	/** Alle schaedigbaren Wesen entlang einer Linie (Breite + halbe Koerperbreite). */
	static List<LivingEntity> along(ServerWorld world, ServerPlayerEntity owner, Vec3d from, Vec3d to, double width) {
		Vec3d line = to.subtract(from);
		double length = line.length();
		List<LivingEntity> out = new ArrayList<>();
		if (length < 1.0E-3) {
			return out;
		}
		Vec3d dir = line.multiply(1.0 / length);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(from, to).expand(width + 1.0), e -> foe(owner, e))) {
			Vec3d c = e.getPos().add(0, e.getHeight() * 0.5, 0);
			double t = c.subtract(from).dotProduct(dir);
			if (t >= 0 && t <= length && c.distanceTo(from.add(dir.multiply(t))) <= width + e.getWidth() * 0.5) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(from)));
		return out;
	}

	static Vec3d muzzle(ServerPlayerEntity player) {
		return player.getEyePos().add(player.getRotationVec(1.0f)).add(0.0, -0.25, 0.0);
	}

	static void line(ServerWorld world, Vec3d from, Vec3d to, ParticleEffect particle, double density) {
		Vec3d step = to.subtract(from);
		int points = Math.max(4, (int) (step.length() * density));
		for (int i = 1; i <= points; i++) {
			Vec3d p = from.add(step.multiply(i / (double) points));
			world.spawnParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	static void ring(ServerWorld world, Vec3d center, double radius, ParticleEffect particle) {
		int points = Math.max(12, (int) (radius * 8));
		for (int i = 0; i < points; i++) {
			double a = i * MathHelper.TAU / points;
			world.spawnParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0.0, 0.05, 0.0, 0.0);
		}
	}

	static void push(LivingEntity target, Vec3d from, double strength, double lift) {
		Vec3d away = target.getPos().subtract(from).multiply(1, 0, 1);
		away = away.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : away.normalize().multiply(strength);
		target.addVelocity(away.x, lift, away.z);
		target.velocityModified = true;
	}

	/**
	 * Ziel festhalten (keine Bewegung, Fallen erlaubt; {@code lift}: schwebt stattdessen leicht). Laeuft ueber den
	 * gemeinsamen Takt; beim Tod oder Entladen verfaellt der Eintrag von selbst.
	 */
	static void hold(ServerWorld world, LivingEntity target, int ticks, ParticleEffect particle, boolean lift) {
		Hold old = HOLDS.get(target.getUuid());
		long until = world.getTime() + ticks;
		HOLDS.put(target.getUuid(), new Hold(world.getRegistryKey(), old == null ? until : Math.max(old.until(), until), particle, lift));
	}

	static boolean held(LivingEntity target) {
		return HOLDS.containsKey(target.getUuid());
	}

	private static void tickHolds(MinecraftServer server) {
		if (HOLDS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Hold> entry = it.next();
			Hold hold = entry.getValue();
			ServerWorld world = server.getWorld(hold.world());
			Entity entity = world == null ? null : world.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity target) || !target.isAlive() || world.getTime() >= hold.until()) {
				it.remove();
				continue;
			}
			target.setVelocity(0.0, hold.lift() ? 0.03 : Math.min(0.0, target.getVelocity().y), 0.0);
			target.velocityModified = true;
			target.fallDistance = 0.0f;
			if (world.getTime() % 5 == 0) {
				world.spawnParticles(hold.particle(), target.getX(), target.getBodyY(0.5), target.getZ(), 3, target.getWidth() * 0.4,
						target.getHeight() * 0.35, target.getWidth() * 0.4, 0.0);
			}
		}
	}
}
