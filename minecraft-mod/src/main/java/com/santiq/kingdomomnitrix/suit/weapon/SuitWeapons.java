package com.santiq.kingdomomnitrix.suit.weapon;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.vfx.CrystalGrowth;
import com.santiq.kingdomomnitrix.vfx.EarthWave;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import com.santiq.kingdomomnitrix.world.content.PlanetBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Registrierung und Spezialangriffe der Endgame-Waffen. Zeitversetzte Teile (Kristallspur, Meteore, Raketen) laufen
 * ueber eine kleine Warteschlange im Server-Tick. Getroffen werden nur Lebewesen, die keine Spieler sind.
 */
public final class SuitWeapons {
	private static final Map<SuitWeapon, SuitWeaponItem> ITEMS = new EnumMap<>(SuitWeapon.class);
	private static final List<Item> ALL = new ArrayList<>();

	/** Verzoegerte Aktion: laeuft, wenn die Weltzeit erreicht ist. */
	private record Task(ServerWorld world, long at, Runnable action) {
	}

	private static final List<Task> TASKS = new ArrayList<>();

	static {
		for (SuitWeapon weapon : SuitWeapon.values()) {
			SuitWeaponItem item = new SuitWeaponItem(weapon, new Item.Settings().rarity(weapon.set().rarity()).maxDamage(800 + 600 * weapon.set().tier())
					.fireproof());
			Registry.register(Registries.ITEM, KingdomOmnitrix.id(weapon.id()), item);
			ITEMS.put(weapon, item);
			ALL.add(item);
		}
	}

	private SuitWeapons() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SuitWeapons::tick);
	}

	public static List<Item> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static SuitWeaponItem item(SuitWeapon weapon) {
		return ITEMS.get(weapon);
	}

	private static void later(ServerWorld world, int ticks, Runnable action) {
		TASKS.add(new Task(world, world.getTime() + ticks, action));
	}

	private static void tick(MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}
		List<Task> due = new ArrayList<>();
		for (Iterator<Task> it = TASKS.iterator(); it.hasNext();) {
			Task task = it.next();
			if (task.world().getTime() >= task.at()) {
				due.add(task);
				it.remove();
			}
		}
		due.forEach(task -> task.action().run());
	}

	/** Spezialangriff bzw. Schuss; false, wenn nichts passiert ist (z. B. Hammer in der Luft). */
	public static boolean fire(ServerPlayerEntity player, SuitWeapon weapon) {
		ServerWorld world = player.getServerWorld();
		return switch (weapon) {
			case TAYDENITE_BLADE -> crystalTrail(world, player, weapon);
			case GALVAN_RIFLE -> biotechBeam(world, player, weapon);
			case STAR_SWORD -> starfall(world, player, weapon);
			case MAGNAPLATE_HAMMER -> groundSlam(world, player, weapon);
			case CARBONOX_LAUNCHER -> plasmaBolt(world, player);
			case INFERNOX_RYNO -> missileSwarm(world, player);
		};
	}

	// --- Ben-10-Linie -------------------------------------------------------------------------------------------------

	/** Kristallspur: acht Diamondhead-Kristalle brechen nacheinander in Blickrichtung aus dem Boden. */
	private static boolean crystalTrail(ServerWorld world, ServerPlayerEntity player, SuitWeapon weapon) {
		Vec3d dir = horizontal(player);
		Vec3d start = player.getPos();
		float yaw = (float) Math.atan2(-dir.x, dir.z);
		Set<Entity> hit = new HashSet<>();
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.2f, 0.6f);
		for (int i = 1; i <= 8; i++) {
			int step = i;
			later(world, i, () -> {
				Vec3d at = ground(world, start.add(dir.multiply(1.2 + step * 1.3)));
				CrystalGrowth.shard(world, at, PlanetBlocks.PETROPIA_GIANT_CRYSTAL.getDefaultState(), yaw, 0.25f, 0.7f + step * 0.06f,
						1.6f + step * 0.25f, 40, weapon.color());
				world.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 0.8f, 0.8f + step * 0.05f);
				for (LivingEntity target : targets(world, player, new Box(at, at).expand(1.3, 2.0, 1.3))) {
					if (hit.add(target)) {
						target.damage(world.getDamageSources().playerAttack(player), 8.0f);
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
						target.addVelocity(0, 0.45, 0);
					}
				}
			});
		}
		return true;
	}

	/** Biotech-Strahl: durchdringt bis zu 32 Bloecke und trifft alles auf der Linie. */
	private static boolean biotechBeam(ServerWorld world, ServerPlayerEntity player, SuitWeapon weapon) {
		Vec3d from = player.getEyePos().add(player.getRotationVector().multiply(0.6)).add(0, -0.15, 0);
		Vec3d end = blockHit(world, player, 32.0).getPos();
		Fx.beam(world, from, end, weapon.color(), 0.18f, 8);
		Fx.flash(world, end, weapon.color(), 1.2f);
		Fx.sparks(world, end, weapon.color(), 0.4f, 16);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.8f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 0.6f, 1.6f);
		Vec3d ray = end.subtract(from);
		for (LivingEntity target : targets(world, player, new Box(from, end).expand(1.0))) {
			if (target.getBoundingBox().expand(0.4).raycast(from, end).isPresent()) {
				target.damage(world.getDamageSources().playerAttack(player), 14.0f);
				target.addVelocity(ray.normalize().multiply(0.4));
			}
		}
		return true;
	}

	/** Sternenschlag: fuenf Meteore stuerzen nacheinander auf den Zielpunkt (bis 24 Bloecke). */
	private static boolean starfall(ServerWorld world, ServerPlayerEntity player, SuitWeapon weapon) {
		Vec3d target = blockHit(world, player, 24.0).getPos();
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 1.2f, 0.6f);
		Fx.charge(world, player, weapon.color(), 1.2f, 20);
		for (int i = 0; i < 5; i++) {
			double angle = i * MathHelper.TAU / 5 + world.random.nextDouble() * 0.5;
			double r = i == 0 ? 0 : 2.0 + world.random.nextDouble() * 1.5;
			Vec3d point = ground(world, target.add(Math.cos(angle) * r, 2.0, Math.sin(angle) * r));
			later(world, 8 + i * 5, () -> {
				Fx.beam(world, point.add(-4, 24, 3), point, weapon.color(), 0.9f, 6);
				Fx.explosion(world, point, weapon.color(), 2.5f);
				Fx.shockwave(world, point, weapon.color(), 3.5f, 10);
				world.playSound(null, BlockPos.ofFloored(point), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 1.3f);
				for (LivingEntity victim : targets(world, player, new Box(point, point).expand(3.0, 2.5, 3.0))) {
					victim.damage(world.getDamageSources().playerAttack(player), 14.0f);
					victim.addVelocity(victim.getPos().subtract(point).normalize().multiply(0.6).add(0, 0.4, 0));
				}
			});
		}
		return true;
	}

	// --- Ratchet-Linie ------------------------------------------------------------------------------------------------

	/** Schockwellen-Schlag: nur am Boden; Ring aus aufbrechender Erde, schleudert Gegner hoch. */
	private static boolean groundSlam(ServerWorld world, ServerPlayerEntity player, SuitWeapon weapon) {
		if (!player.isOnGround()) {
			return false;
		}
		Vec3d center = player.getPos();
		EarthWave.ring(world, center, 6.0, 10, 1.0f);
		Fx.shockwave(world, center.add(0, 0.1, 0), weapon.color(), 6.5f, 10);
		Fx.shake(world, center, 0.8f, 10, 16);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.0f, 0.5f);
		for (LivingEntity target : targets(world, player, new Box(center, center).expand(6.0, 2.0, 6.0))) {
			if (target.squaredDistanceTo(center) <= 36.0) {
				target.damage(world.getDamageSources().playerAttack(player), 10.0f);
				Vec3d push = target.getPos().subtract(center).normalize();
				target.addVelocity(push.x * 0.9, 0.7, push.z * 0.9);
			}
		}
		return true;
	}

	/** Plasma-Bolzen: explodiert beim Aufprall (kein Blockschaden). */
	private static boolean plasmaBolt(ServerWorld world, ServerPlayerEntity player) {
		HeroProjectileEntity.shoot(world, player, ModItems.PLASMA_SHOT, 2.4f, 0.4f).withDamage(12.0f).withExplosion(1.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.7f);
		Fx.flash(world, player.getEyePos().add(player.getRotationVector()), 0xFF2A2A, 0.8f);
		return true;
	}

	/** Raketenschwarm: sechs Raketen, leicht gestreut, im Abstand von drei Ticks. */
	private static boolean missileSwarm(ServerWorld world, ServerPlayerEntity player) {
		for (int i = 0; i < 6; i++) {
			later(world, i * 3, () -> {
				if (!player.isAlive()) {
					return;
				}
				HeroProjectileEntity.shoot(world, player, ModItems.FIRE_ORB, 1.9f, 7.0f).withDamage(9.0f).withExplosion(1.8f);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0f, 0.8f);
				Fx.flames(world, player.getEyePos(), player.getRotationVector().multiply(-0.4), 0xFF8A1A, 0.2f, 6);
			});
		}
		return true;
	}

	// --- Hilfen -------------------------------------------------------------------------------------------------------

	private static List<LivingEntity> targets(ServerWorld world, PlayerEntity player, Box box) {
		return world.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !(e instanceof PlayerEntity)
				&& (e instanceof Monster || !e.isTeammate(player)));
	}

	private static Vec3d horizontal(PlayerEntity player) {
		Vec3d look = player.getRotationVector();
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		return flat.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
	}

	private static BlockHitResult blockHit(ServerWorld world, PlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		return world.raycast(new RaycastContext(eye, eye.add(player.getRotationVector().multiply(range)), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
	}

	/** Bodenhoehe unter einem Punkt (bis 6 Bloecke tief). */
	private static Vec3d ground(ServerWorld world, Vec3d at) {
		Vec3d top = at.add(0, 1.5, 0);
		BlockHitResult hit = world.raycast(new RaycastContext(top, top.add(0, -7.5, 0), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
		return hit.getType() == HitResult.Type.MISS ? at : hit.getPos();
	}
}
