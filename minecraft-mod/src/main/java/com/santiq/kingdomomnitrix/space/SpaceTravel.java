package com.santiq.kingdomomnitrix.space;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.mixin.ServerPlayNetworkHandlerAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

/**
 * Raumfahrt: Atmosphaere verlassen, Weltraumrisse durchfliegen, zurueck in die Heimatwelt sinken.
 * Im All gilt geringe Schwerkraft; wer ohne Schiff ins Leere faellt, tritt ueber der Oberwelt wieder ein.
 */
public final class SpaceTravel {
	public static final RegistryKey<World> SPACE = RegistryKey.of(RegistryKeys.WORLD, KingdomOmnitrix.id("space"));
	/** So viele Bloecke unter der Bauhoehe beginnt das All. */
	public static final int ATMOSPHERE_MARGIN = 16;
	/** Unter dieser Hoehe im All sinkt das Schiff zurueck in seine Heimatwelt. */
	public static final double SPACE_FLOOR = 0.0;
	/** Ohne Schiff: ab hier faellt man aus dem All zurueck auf die Oberwelt. */
	public static final double REENTRY_Y = -30.0;
	private static final double ANNOUNCE_DISTANCE = 60.0;
	private static final Identifier LOW_GRAVITY = KingdomOmnitrix.id("space_low_gravity");

	/** Feste Ankunftsorte einzelner Welten (z. B. der Stadtplatz von Traverse Town). */
	private static final Map<RegistryKey<World>, Function<ServerWorld, BlockPos>> ARRIVALS = new HashMap<>();

	private SpaceTravel() {
	}

	public static void registerArrival(RegistryKey<World> world, Function<ServerWorld, BlockPos> arrival) {
		ARRIVALS.put(world, arrival);
	}

	/** Wo man in einer Welt ankommt: fester Ankunftsort, sonst Risskoordinaten, sonst Weltspawn. */
	public static BlockPos arrivalPoint(ServerWorld world, Optional<java.util.List<Integer>> routeArrival) {
		Function<ServerWorld, BlockPos> fixed = ARRIVALS.get(world.getRegistryKey());
		if (fixed != null) {
			return fixed.apply(world);
		}
		BlockPos spawn = world.getSpawnPos();
		return new BlockPos(routeArrival.map(list -> list.get(0)).orElse(spawn.getX()), spawn.getY(),
				routeArrival.map(list -> list.get(1)).orElse(spawn.getZ()));
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (isSpace(world)) {
				tickSpace(world);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> updateGravity(player));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> updateGravity(handler.getPlayer()));
	}

	public static boolean isSpace(World world) {
		return world.getRegistryKey().equals(SPACE);
	}

	// --- Schiff ---------------------------------------------------------------------------------

	static void tickShip(ShipEntity ship, ServerWorld world) {
		for (Entity passenger : ship.getPassengerList()) {
			if (passenger instanceof ServerPlayerEntity player && player.networkHandler != null) {
				ServerPlayNetworkHandlerAccessor handler = (ServerPlayNetworkHandlerAccessor) player.networkHandler;
				handler.kingdomomnitrix$setFloatingTicks(0);
				handler.kingdomomnitrix$setVehicleFloatingTicks(0);
			}
		}
		if (!(ship.getControllingPassenger() instanceof ServerPlayerEntity)) {
			return;
		}
		if (!isSpace(world)) {
			if (ship.getY() > world.getTopY() - ATMOSPHERE_MARGIN) {
				leaveAtmosphere(ship, world);
			} else if (ship.getY() > world.getTopY() - ATMOSPHERE_MARGIN - 40 && ship.age % 200 == 0) {
				ShipAi.sayToCrew(ship, "atmosphere_edge");
			}
			return;
		}
		if (ship.getY() < SPACE_FLOOR) {
			descendHome(ship, world);
			return;
		}
		for (Map.Entry<Identifier, SpaceRoute> entry : SpaceRouteRegistry.all(world.getRegistryManager())) {
			SpaceRoute route = entry.getValue();
			double distance = ship.getPos().distanceTo(route.position());
			if (distance < route.radius()) {
				enterRift(ship, world, entry.getKey(), route);
				return;
			}
			if (distance < route.radius() + ANNOUNCE_DISTANCE && !entry.getKey().equals(ship.announcedRoute)) {
				ship.announcedRoute = entry.getKey();
				ShipAi.sayToCrew(ship, "rift_ahead", SpaceRoute.name(entry.getKey()));
			}
		}
	}

	private static void leaveAtmosphere(ShipEntity ship, ServerWorld world) {
		MinecraftServer server = world.getServer();
		ServerWorld space = server.getWorld(SPACE);
		if (space == null) {
			KingdomOmnitrix.LOGGER.error("Weltraum-Dimension {} fehlt", SPACE.getValue());
			return;
		}
		Vec3d exit = SpaceRouteRegistry.toWorld(world.getRegistryManager(), world.getRegistryKey())
				.map(entry -> entry.getValue().exitPoint())
				.orElse(new Vec3d(0.0, 120.0, 0.0));
		ship.setHomeWorld(world.getRegistryKey().getValue());
		ShipEntity moved = teleport(ship, space, exit);
		if (moved != null) {
			playShipSound(moved, ModSounds.SHIP_LAUNCH);
			ShipAi.sayToCrew(moved, "space_entered");
		}
	}

	private static void enterRift(ShipEntity ship, ServerWorld space, Identifier routeId, SpaceRoute route) {
		ServerWorld destination = space.getServer().getWorld(route.destinationKey());
		if (destination == null) {
			if (ship.age % 100 == 0) {
				ShipAi.sayToCrew(ship, "rift_unstable", SpaceRoute.name(routeId));
			}
			return;
		}
		BlockPos arrival = arrivalPoint(destination, route.arrival());
		int x = arrival.getX();
		int z = arrival.getZ();
		ShipEntity moved = teleport(ship, destination, new Vec3d(x + 0.5, arrivalHeight(destination, x, z), z + 0.5));
		if (moved != null) {
			moved.setHomeWorld(destination.getRegistryKey().getValue());
			moved.announcedRoute = null;
			playShipSound(moved, ModSounds.WORLD_RIFT);
			ShipAi.sayToCrew(moved, "arrival", SpaceRoute.name(routeId));
		}
	}

	private static void descendHome(ShipEntity ship, ServerWorld space) {
		MinecraftServer server = space.getServer();
		Identifier home = Optional.ofNullable(ship.homeWorld()).orElse(World.OVERWORLD.getValue());
		ServerWorld target = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, home));
		if (target == null || isSpace(target)) {
			target = server.getOverworld();
		}
		BlockPos spawn = target.getSpawnPos();
		ShipEntity moved = teleport(ship, target, new Vec3d(spawn.getX() + 0.5, arrivalHeight(target, spawn.getX(), spawn.getZ()), spawn.getZ() + 0.5));
		if (moved != null) {
			ShipAi.sayToCrew(moved, "reentry");
		}
	}

	/** 30 Bloecke ueber dem Boden, aber sicher unter der Grenze zum All. */
	private static double arrivalHeight(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
		return Math.min(ground + 30, world.getTopY() - ATMOSPHERE_MARGIN - 24);
	}

	/** Klang fuer die Besatzung nach einem Weltwechsel (am Schiff, damit Mitreisende ihn ebenfalls hoeren). */
	private static void playShipSound(ShipEntity ship, net.minecraft.sound.SoundEvent sound) {
		ship.getWorld().playSound(null, ship.getX(), ship.getY(), ship.getZ(), sound, net.minecraft.sound.SoundCategory.NEUTRAL, 1.0f, 1.0f);
	}

	private static ShipEntity teleport(ShipEntity ship, ServerWorld target, Vec3d pos) {
		Entity moved = ship.teleportTo(new TeleportTarget(target, pos, Vec3d.ZERO, ship.getYaw(), ship.getPitch(), TeleportTarget.NO_OP));
		return moved instanceof ShipEntity result ? result : null;
	}

	// --- Spieler im All -------------------------------------------------------------------------

	private static void tickSpace(ServerWorld space) {
		// Kopie: der Wiedereintritt nimmt den Spieler aus der Liste der Welt (sonst ConcurrentModificationException)
		for (ServerPlayerEntity player : java.util.List.copyOf(space.getPlayers())) {
			if (player.hasVehicle() || player.isSpectator() || player.getY() > REENTRY_Y) {
				continue;
			}
			ServerWorld overworld = space.getServer().getOverworld();
			BlockPos spawn = overworld.getSpawnPos();
			player.teleportTo(new TeleportTarget(overworld, new Vec3d(spawn.getX() + 0.5, overworld.getTopY() - 40, spawn.getZ() + 0.5),
					Vec3d.ZERO, player.getYaw(), 90.0f, TeleportTarget.NO_OP));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 20 * 30, 0));
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.reentry")
					.formatted(net.minecraft.util.Formatting.AQUA), false);
		}
	}

	/** Im All: 25 % Schwerkraft. Temporaerer Modifikator, wird bei jedem Weltwechsel und Login neu gesetzt. */
	private static void updateGravity(ServerPlayerEntity player) {
		EntityAttributeInstance gravity = player.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY);
		if (gravity == null) {
			return;
		}
		gravity.removeModifier(LOW_GRAVITY);
		if (isSpace(player.getWorld())) {
			gravity.addTemporaryModifier(new EntityAttributeModifier(LOW_GRAVITY, -0.75, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
