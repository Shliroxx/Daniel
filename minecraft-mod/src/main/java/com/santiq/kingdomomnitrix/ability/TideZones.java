package com.santiq.kingdomomnitrix.ability;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Ripjaws' Gezeitenzonen: Bereiche, die fuer ihren Erzeuger als Wasser zaehlen (Wasser-Boni, Wasser-Effekte, kein
 * Austrocknen) — ohne echte Wasserbloecke, damit nichts ueberflutet oder weggespuelt wird. Sichtbar als Spritzer- und
 * Tropfenring. Gegner darin kaempfen gegen die Stroemung (siehe {@link RipjawsAbilities}).
 */
public final class TideZones {
	/** Zone: Erzeuger, Welt, Mitte, Radius, Ende (Welt-Tick) */
	record Zone(UUID owner, RegistryKey<World> world, Vec3d center, double radius, long until) {
		boolean contains(Vec3d pos) {
			double dx = pos.x - center.x;
			double dz = pos.z - center.z;
			return dx * dx + dz * dz <= radius * radius && Math.abs(pos.y - center.y) <= 3.0;
		}
	}

	private static final List<Zone> ZONES = new ArrayList<>();

	private TideZones() {
	}

	static void add(Zone zone) {
		ZONES.add(zone);
	}

	static List<Zone> all() {
		return ZONES;
	}

	/** Steht der Spieler in einer eigenen Gezeitenzone? (zaehlt fuer ihn als Wasser) */
	public static boolean isIn(PlayerEntity player) {
		if (ZONES.isEmpty()) {
			return false;
		}
		long now = player.getWorld().getTime();
		for (Zone zone : ZONES) {
			if (zone.owner().equals(player.getUuid()) && zone.until() > now && zone.world() == player.getWorld().getRegistryKey()
					&& zone.contains(player.getPos())) {
				return true;
			}
		}
		return false;
	}

	/** Nass im Sinne von Ripjaws: echtes Wasser, Regen oder eigene Zone. */
	public static boolean isWet(PlayerEntity player) {
		return player.isTouchingWaterOrRain() || isIn(player);
	}

	/** Abgelaufene Zonen entfernen, Ring aus Spritzern zeichnen. */
	static void tick(MinecraftServer server) {
		if (ZONES.isEmpty()) {
			return;
		}
		long tick = server.getTicks();
		Iterator<Zone> it = ZONES.iterator();
		while (it.hasNext()) {
			Zone zone = it.next();
			ServerWorld world = server.getWorld(zone.world());
			if (world == null || world.getTime() >= zone.until()) {
				it.remove();
				continue;
			}
			if (tick % 4 == 0) {
				int points = (int) (zone.radius() * 6);
				for (int i = 0; i < points; i++) {
					double a = i * MathHelper.TAU / points + tick * 0.05;
					world.spawnParticles(ParticleTypes.SPLASH, zone.center().x + Math.cos(a) * zone.radius(), zone.center().y + 0.2,
							zone.center().z + Math.sin(a) * zone.radius(), 1, 0.0, 0.05, 0.0, 0.0);
				}
				world.spawnParticles(ParticleTypes.FALLING_WATER, zone.center().x, zone.center().y + 2.5, zone.center().z,
						(int) (zone.radius() * 3), zone.radius() * 0.5, 0.3, zone.radius() * 0.5, 0.0);
			}
		}
	}

	static void forget(UUID owner) {
		ZONES.removeIf(z -> z.owner().equals(owner));
	}
}
