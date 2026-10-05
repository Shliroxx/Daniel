package com.santiq.kingdomomnitrix.client.omnitrix;

import com.santiq.kingdomomnitrix.omnitrix.ScanRule;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Flutterer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Smart-Scan auf dem Client: bewertet Umgebung, Ziel und Zustand des Spielers nach den {@link ScanRule}-Regeln und
 * empfiehlt ein freigeschaltetes Alien. Rechnet hoechstens alle {@link #INTERVAL} Ticks neu und nur, wenn jemand fragt
 * (Rad, Schnellwahl, Smart-Wahl-Taste) — kein Dauerbetrieb.
 */
public final class SmartScan {
	private static final int INTERVAL = 20;
	private static final double TARGET_RANGE = 16.0;

	private static long lastScan;
	private static boolean valid;
	private static Optional<ScanRule.Recommendation> cached = Optional.empty();

	private SmartScan() {
	}

	/** Aktuelle Empfehlung (zwischengespeichert, alle 20 Ticks neu). */
	public static Optional<ScanRule.Recommendation> recommendation() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return Optional.empty();
		}
		long now = client.world.getTime();
		if (!valid || now - lastScan >= INTERVAL || now < lastScan) {
			lastScan = now;
			valid = true;
			cached = scan(client, player);
		}
		return cached;
	}

	/** Sofort neu bewerten (Smart-Wahl-Taste). */
	public static Optional<ScanRule.Recommendation> rescan() {
		valid = false;
		return recommendation();
	}

	private static Optional<ScanRule.Recommendation> scan(MinecraftClient client, ClientPlayerEntity player) {
		List<ScanRule> rules = ScanRule.all(client.world.getRegistryManager());
		if (rules.isEmpty()) {
			return Optional.empty();
		}
		LivingEntity target = target(client, player);
		var data = HeroDataAccess.get(player);
		return ScanRule.recommend(rules, condition -> matches(condition, player, target), data::hasAlien);
	}

	private static boolean matches(ScanRule.Condition condition, ClientPlayerEntity player, LivingEntity target) {
		World world = player.getWorld();
		return switch (condition.type()) {
			case "lava_near" -> lavaNear(world, player.getBlockPos(), Math.round(condition.radius()));
			case "on_fire" -> player.isOnFire();
			case "in_water" -> player.isTouchingWater();
			case "submerged" -> player.isSubmergedInWater();
			case "drop_near" -> dropNear(world, player.getBlockPos(), Math.round(condition.radius())) >= condition.min();
			case "dark" -> world.getLightLevel(player.getBlockPos()) <= condition.max();
			case "target_is" -> target != null && isEntity(target, condition);
			case "entity_near" -> count(world, player, condition.radius(), e -> isEntity(e, condition)) >= condition.min();
			case "block_near" -> blocksNear(world, player.getBlockPos(), Math.round(condition.radius()), condition, Math.round(condition.min()));
			case "falling" -> !player.isOnGround() && player.fallDistance + gapBelow(world, player.getBlockPos()) >= condition.min();
			case "tight_space" -> freeHeight(world, player.getBlockPos()) <= condition.max();
			case "target_armor" -> target != null && target.getArmor() >= condition.min();
			case "target_flying" -> target != null && (target instanceof Flutterer || target instanceof FlyingEntity
					|| (!target.isOnGround() && target.hasNoGravity()));
			case "enemies_near" -> count(world, player, condition.radius(), e -> e instanceof Monster) >= condition.min();
			case "ranged_enemies" -> count(world, player, condition.radius(), e -> e instanceof RangedAttackMob && e instanceof Monster) >= condition.min();
			case "explosive_near" -> count(world, player, condition.radius(), e -> e instanceof CreeperEntity || e instanceof TntEntity) >= 1;
			case "low_health" -> player.getHealth() / Math.max(1.0f, player.getMaxHealth()) <= condition.max();
			default -> false;
		};
	}

	/**
	 * Ziel: angeschaute Kreatur (auch friedliche wie ein Eisengolem — der Spieler zeigt bewusst darauf), sonst das
	 * naechste Monster in Reichweite.
	 */
	private static LivingEntity target(MinecraftClient client, ClientPlayerEntity player) {
		LivingEntity aimed = aimed(player);
		if (aimed != null) {
			return aimed;
		}
		return player.getWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(TARGET_RANGE),
						e -> e instanceof Monster && e.isAlive())
				.stream().min((a, b) -> Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player))).orElse(null);
	}

	private static int count(World world, ClientPlayerEntity player, float radius, java.util.function.Predicate<Entity> filter) {
		Box box = player.getBoundingBox().expand(radius);
		return world.getOtherEntities(player, box, e -> e.isAlive() && filter.test(e)).size();
	}

	private static boolean lavaNear(World world, BlockPos center, int radius) {
		for (BlockPos pos : BlockPos.iterateOutwards(center, radius, Math.max(2, radius / 2), radius)) {
			if (world.getFluidState(pos).isIn(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Angeschaute Kreatur bis {@link #TARGET_RANGE} Bloecke (die Spiel-Zielerfassung reicht nur 3 Bloecke); Waende
	 * verdecken. Andere Spieler zaehlen nicht.
	 */
	private static LivingEntity aimed(ClientPlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d reach = player.getRotationVec(1.0f).multiply(TARGET_RANGE);
		Vec3d end = eye.add(reach);
		HitResult block = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		double limit = block.getType() == HitResult.Type.MISS ? TARGET_RANGE * TARGET_RANGE : block.getPos().squaredDistanceTo(eye);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, player.getBoundingBox().stretch(reach).expand(1.0),
				e -> e instanceof LivingEntity && !(e instanceof PlayerEntity) && e.isAlive() && !e.isSpectator(), limit);
		return hit != null ? (LivingEntity) hit.getEntity() : null;
	}

	/** Kreatur passt zum Ziel der Bedingung (ID oder {@code #tag}). */
	private static boolean isEntity(Entity entity, ScanRule.Condition condition) {
		if (condition.targetIsTag()) {
			return entity.getType().isIn(TagKey.of(RegistryKeys.ENTITY_TYPE, condition.targetId()));
		}
		return Registries.ENTITY_TYPE.getId(entity.getType()).equals(condition.targetId());
	}

	/** Mindestens {@code needed} passende Bloecke im Wuerfel um den Spieler (bricht beim Erreichen ab). */
	private static boolean blocksNear(World world, BlockPos center, int radius, ScanRule.Condition condition, int needed) {
		TagKey<Block> tag = condition.targetIsTag() ? TagKey.of(RegistryKeys.BLOCK, condition.targetId()) : null;
		Block block = tag == null ? Registries.BLOCK.get(condition.targetId()) : null;
		int found = 0;
		for (BlockPos pos : BlockPos.iterateOutwards(center, radius, Math.max(2, radius / 2), radius)) {
			BlockState state = world.getBlockState(pos);
			if (tag != null ? state.isIn(tag) : state.isOf(block)) {
				if (++found >= Math.max(1, needed)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Tiefster Abgrund direkt neben dem Spieler (4 Richtungen, 1..radius Bloecke entfernt). */
	private static int dropNear(World world, BlockPos feet, int radius) {
		int deepest = 0;
		for (Direction direction : Direction.Type.HORIZONTAL) {
			for (int step = 1; step <= Math.max(1, radius); step++) {
				BlockPos column = feet.offset(direction, step);
				if (!world.getBlockState(column).getCollisionShape(world, column).isEmpty()) {
					break;
				}
				deepest = Math.max(deepest, gapBelow(world, column) + 1);
			}
		}
		return deepest;
	}

	/** Bloecke bis zum Boden unter den Fuessen (hoechstens 32). */
	private static int gapBelow(World world, BlockPos feet) {
		for (int dy = 1; dy <= 32; dy++) {
			BlockPos pos = feet.down(dy);
			if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty() || !world.getFluidState(pos).isEmpty()) {
				return dy - 1;
			}
		}
		return 32;
	}

	/** Freie Hoehe ab den Fuessen (hoechstens 4). */
	private static int freeHeight(World world, BlockPos feet) {
		int free = 0;
		for (int dy = 0; dy < 4; dy++) {
			BlockPos pos = feet.up(dy);
			if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
				break;
			}
			free++;
		}
		return free;
	}
}
