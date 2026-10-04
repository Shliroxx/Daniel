package com.santiq.kingdomomnitrix.space;

import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.util.MaterialCost;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;

/**
 * Galaxiekarte und Ausbau der Aphelion (Ratchet & Clank):
 * <ul>
 *   <li><b>Reisen</b>: der Pilot waehlt einen Planeten; das Schiff steigt auf und springt in den Warp (Dauer je nach
 *       Warp-Antrieb), dann erscheint es ueber dem Landeplatz der Zielwelt.</li>
 *   <li><b>Freischaltung</b>: Heldenstufe, vorher besuchter Planet und Warp-Stufe fuer fernere Sternsysteme.</li>
 *   <li><b>Ausbau</b>: Triebwerk, Warp-Antrieb, Bordkanone — je drei Stufen fuer Bolts und Raritanium.</li>
 *   <li><b>Bordkanone</b>: Linksklick im Cockpit feuert Laser in Flugrichtung.</li>
 * </ul>
 */
public final class Galaxy {
	public enum Action { TRAVEL, UPGRADE, FIRE }

	/** Ergebnis der Pruefung, ob ein Planet angeflogen werden kann (Karte und Server nutzen dieselbe Regel). */
	public enum Access {
		OPEN, HERE, LEVEL, WARP, REQUIRES, NO_WORLD;

		/** Zeile in der Galaxiekarte. */
		public String screenKey() {
			return switch (this) {
				case OPEN -> "screen.kingdomomnitrix.galaxy.access.open";
				case HERE -> "screen.kingdomomnitrix.galaxy.access.here";
				case LEVEL -> "screen.kingdomomnitrix.galaxy.access.level";
				case WARP -> "screen.kingdomomnitrix.galaxy.access.warp";
				case REQUIRES -> "screen.kingdomomnitrix.galaxy.access.requires";
				case NO_WORLD -> "screen.kingdomomnitrix.galaxy.access.no_world";
			};
		}

		/** Meldung, wenn die Reise abgelehnt wird. */
		public String messageKey() {
			return switch (this) {
				case OPEN, HERE -> "message.kingdomomnitrix.galaxy.access.here";
				case LEVEL -> "message.kingdomomnitrix.galaxy.access.level";
				case WARP -> "message.kingdomomnitrix.galaxy.access.warp";
				case REQUIRES -> "message.kingdomomnitrix.galaxy.access.requires";
				case NO_WORLD -> "message.kingdomomnitrix.galaxy.access.no_world";
			};
		}
	}

	private static final Map<UUID, Long> CANNON_READY = new HashMap<>();

	private Galaxy() {
	}

	public static void handle(ServerPlayerEntity player, int action, String value) {
		if (action < 0 || action >= Action.values().length) {
			return;
		}
		switch (Action.values()[action]) {
			case TRAVEL -> {
				Identifier id = Identifier.tryParse(value);
				if (id != null) {
					travel(player, id);
				}
			}
			case UPGRADE -> {
				for (ShipLog.Upgrade upgrade : ShipLog.Upgrade.values()) {
					if (upgrade.key().equals(value)) {
						upgrade(player, upgrade);
					}
				}
			}
			case FIRE -> fire(player);
		}
	}

	/** Darf {@code player} den Planeten anfliegen? {@code worldExists}: Server kennt die Ziel-Dimension. */
	public static Access access(PlayerEntity player, Identifier planetId, Planet planet, boolean worldExists) {
		if (player.getWorld().getRegistryKey().equals(planet.worldKey())) {
			return Access.HERE;
		}
		if (!worldExists) {
			return Access.NO_WORLD;
		}
		if (player.getAbilities().creativeMode) {
			return Access.OPEN;
		}
		if (HeroDataAccess.get(player).level() < planet.heroLevel()) {
			return Access.LEVEL;
		}
		ShipLog.State log = ShipLog.get(player);
		if (log.warp() < planet.warp()) {
			return Access.WARP;
		}
		if (planet.requires().isPresent() && !log.visited().contains(planet.requires().get())) {
			return Access.REQUIRES;
		}
		return Access.OPEN;
	}

	// --- Reisen ---------------------------------------------------------------------------------

	private static void travel(ServerPlayerEntity player, Identifier planetId) {
		if (!(player.getVehicle() instanceof ShipEntity ship) || ship.getControllingPassenger() != player) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.galaxy.not_pilot").formatted(Formatting.RED), true);
			return;
		}
		if (ship.isWarping()) {
			return;
		}
		Optional<Planet> planet = PlanetRegistry.get(player.getServerWorld().getRegistryManager(), planetId);
		if (planet.isEmpty()) {
			return;
		}
		boolean exists = player.getServer().getWorld(planet.get().worldKey()) != null;
		Access access = access(player, planetId, planet.get(), exists);
		if (access != Access.OPEN) {
			player.sendMessage(Text.translatable(access.messageKey(),
					Planet.name(planetId), planet.get().heroLevel(), planet.get().warp(),
					planet.get().requires().map(Planet::name).orElse(Text.empty())).formatted(Formatting.RED), false);
			return;
		}
		int ticks = ShipLog.get(player).warpTicks();
		ship.startWarp(planetId, ticks);
		ServerWorld world = player.getServerWorld();
		world.playSound(null, ship.getX(), ship.getY(), ship.getZ(), ModSounds.SHIP_LAUNCH, SoundCategory.NEUTRAL, 1.2f, 0.9f);
		ShipAi.sayToCrew(ship, "warp_start", Planet.name(planetId), planet.get().systemName(), ticks / 20);
	}

	/** Jeden Server-Tick waehrend des Warps: Lichtstreifen, am Ende Ankunft. */
	static void tickWarp(ShipEntity ship, ServerWorld world) {
		int left = ship.warpTicksLeft();
		Vec3d look = Vec3d.fromPolar(ship.getPitch(), ship.getYaw());
		for (int i = 0; i < 4; i++) {
			Vec3d offset = new Vec3d(world.random.nextGaussian() * 3, world.random.nextGaussian() * 2, world.random.nextGaussian() * 3);
			Vec3d at = ship.getPos().add(look.multiply(6)).add(offset);
			world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, -look.x, -look.y, -look.z, 1.6);
		}
		if (left == 20) {
			world.playSound(null, ship.getX(), ship.getY(), ship.getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.NEUTRAL, 1.2f, 0.6f);
		}
		if (left > 1) {
			ship.tickWarp();
			return;
		}
		Identifier target = ship.warpTarget();
		ship.stopWarp();
		if (target != null) {
			arrive(ship, world, target);
		}
	}

	private static void arrive(ShipEntity ship, ServerWorld from, Identifier planetId) {
		Optional<Planet> planet = PlanetRegistry.get(from.getRegistryManager(), planetId);
		ServerWorld to = planet.map(p -> from.getServer().getWorld(p.worldKey())).orElse(null);
		if (planet.isEmpty() || to == null) {
			ShipAi.sayToCrew(ship, "warp_failed");
			return;
		}
		BlockPos pad;
		if (planet.get().landing().isPresent()) {
			List<Integer> landing = planet.get().landing().get();
			pad = LandingPad.ensure(to, landing.get(0), landing.get(1));
		} else {
			BlockPos spawn = SpaceTravel.arrivalPoint(to, Optional.empty());
			to.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
			pad = new BlockPos(spawn.getX(), to.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ());
		}
		double y = Math.min(pad.getY() + 18.0, to.getTopY() - SpaceTravel.ATMOSPHERE_MARGIN - 24.0);
		Entity moved = ship.teleportTo(new TeleportTarget(to, new Vec3d(pad.getX() + 0.5, y, pad.getZ() + 0.5), Vec3d.ZERO,
				ship.getYaw(), 20.0f, TeleportTarget.NO_OP));
		if (!(moved instanceof ShipEntity arrived)) {
			return;
		}
		arrived.setHomeWorld(to.getRegistryKey().getValue());
		to.playSound(null, arrived.getX(), arrived.getY(), arrived.getZ(), ModSounds.WORLD_RIFT, SoundCategory.NEUTRAL, 1.0f, 1.2f);
		for (Entity passenger : arrived.getPassengerList()) {
			if (passenger instanceof ServerPlayerEntity player) {
				ShipLog.update(player, log -> log.visit(planetId));
			}
		}
		ShipAi.sayToCrew(arrived, "warp_arrival", Planet.name(planetId), Planet.description(planetId));
		if (planet.get().landing().isPresent()) {
			ShipAi.sayToCrew(arrived, "landing_pad");
		}
	}

	// --- Ausbau ---------------------------------------------------------------------------------

	private static void upgrade(ServerPlayerEntity player, ShipLog.Upgrade upgrade) {
		ShipLog.State log = ShipLog.get(player);
		int next = log.level(upgrade) + 1;
		if (next > ShipLog.MAX_LEVEL) {
			return;
		}
		int[] price = ShipLog.price(next);
		List<ItemStack> materials = List.of(new ItemStack(ModItems.RARITANIUM, price[1]));
		if (!player.getAbilities().creativeMode) {
			if (!HeroDataAccess.get(player).canAfford(price[0])) {
				player.sendMessage(Text.translatable("message.kingdomomnitrix.terminal.no_bolts").formatted(Formatting.RED), true);
				return;
			}
			if (!MaterialCost.missing(player.getInventory(), materials).isEmpty()) {
				player.sendMessage(Text.translatable("message.kingdomomnitrix.galaxy.no_raritanium", price[1]).formatted(Formatting.RED), true);
				return;
			}
			MaterialCost.consume(player.getInventory(), materials);
			HeroDataAccess.update(player, data -> data.addBolts(-price[0]));
		}
		ShipLog.update(player, state -> state.withLevel(upgrade, next));
		player.playSoundToPlayer(ModSounds.WEAPON_BUY, SoundCategory.PLAYERS, 1.0f, 1.0f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.galaxy.upgraded",
				Text.translatable(upgrade.translationKey()), next).formatted(Formatting.GREEN), false);
	}

	// --- Bordkanone -----------------------------------------------------------------------------

	private static void fire(ServerPlayerEntity player) {
		if (!(player.getVehicle() instanceof ShipEntity ship) || ship.getControllingPassenger() != player || ship.isWarping()) {
			return;
		}
		int level = ShipLog.get(player).cannon();
		if (level <= 0) {
			return;
		}
		long now = player.getServerWorld().getTime();
		if (CANNON_READY.getOrDefault(player.getUuid(), 0L) > now) {
			return;
		}
		CANNON_READY.put(player.getUuid(), now + 8 - level);
		ServerWorld world = player.getServerWorld();
		Vec3d look = Vec3d.fromPolar(ship.getPitch(), ship.getYaw());
		Vec3d side = Vec3d.fromPolar(0.0f, ship.getYaw() - 90.0f);
		for (int gun = -1; gun <= 1; gun += 2) {
			HeroProjectileEntity shot = HeroProjectileEntity.shoot(world, player, ModItems.PLASMA_SHOT, 3.2f, 0.4f)
					.withDamage(5.0f + 3.0f * level);
			if (level >= 3) {
				shot.withExplosion(1.0f);
			}
			Vec3d muzzle = ship.getPos().add(0.0, 0.6, 0.0).add(look.multiply(3.2)).add(side.multiply(gun * 1.2));
			shot.setPosition(muzzle.x, muzzle.y, muzzle.z);
			shot.setVelocity(look.x, look.y, look.z, 3.2f, 0.4f);
			// Muendungsblitz und Leuchtspur der ersten Meter (das Geschoss selbst ist klein und schnell)
			world.spawnParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
			for (int step = 1; step <= 10; step++) {
				Vec3d at = muzzle.add(look.multiply(step * 1.2));
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
				world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		world.playSound(null, ship.getX(), ship.getY(), ship.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.8f,
				1.6f + MathHelper.nextFloat(world.random, -0.1f, 0.1f));
	}

	public static void forget(UUID player) {
		CANNON_READY.remove(player);
	}
}
