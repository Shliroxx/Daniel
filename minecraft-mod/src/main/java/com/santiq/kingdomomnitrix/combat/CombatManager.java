package com.santiq.kingdomomnitrix.combat;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.magic.MagicManager;
import com.santiq.kingdomomnitrix.networking.CombatAnimationPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Serverseitiges Action-Kampfsystem: leichte Combo mit Finisher, Luftcombo, schwerer Angriff,
 * Ausweichen mit Unverwundbarkeit, Blocken mit perfektem Block, Lock-On-Ziel.
 *
 * <p>Der Client schickt nur Absichten. Abstaende, Reichweite, Ziele und Schaden bestimmt der Server.</p>
 */
public final class CombatManager {
	private static final Map<UUID, CombatState> STATES = new HashMap<>();

	private static final int COMBO_WINDOW_TICKS = 20;
	private static final double FINISHER_RADIUS = 3.0;
	private static final double HIT_HALF_ANGLE_COS = Math.cos(Math.toRadians(70));
	private static final int DODGE_COOLDOWN_TICKS = 16;
	private static final int DODGE_INVULNERABLE_TICKS = 8;
	private static final double DODGE_SPEED = 1.1;
	private static final int PERFECT_GUARD_TICKS = 6;
	private static final double LOCK_RANGE = 32.0;
	private static final Identifier GUARD_SLOW_MODIFIER = KingdomOmnitrix.id("guard_slow");

	public enum Attack { LIGHT, HEAVY }

	private CombatManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CombatManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUuid()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player) {
				return allowDamage(player, source);
			}
			return true;
		});
	}

	private static CombatState state(ServerPlayerEntity player) {
		return STATES.computeIfAbsent(player.getUuid(), uuid -> new CombatState());
	}

	public static ItemStack comboWeapon(ServerPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		return stack.getItem() instanceof ComboWeapon ? stack : ItemStack.EMPTY;
	}

	// --- Angriffe -------------------------------------------------------------------------------

	public static void attack(ServerPlayerEntity player, Attack type) {
		ItemStack stack = comboWeapon(player);
		if (stack.isEmpty() || !player.isAlive() || player.isSpectator()) {
			return;
		}
		CombatState state = state(player);
		ServerWorld world = player.getServerWorld();
		ComboProfile weapon = ((ComboWeapon) stack.getItem()).comboProfile(world.getRegistryManager(), stack);
		long now = world.getTime();
		if (now < state.nextAttackTick) {
			return;
		}
		if (state.guarding) {
			setGuard(player, false);
		}

		boolean airborne = !player.isOnGround() && !player.isTouchingWater();
		if (now - state.lastAttackTick > COMBO_WINDOW_TICKS || state.airComboActive != airborne) {
			state.comboStep = 0;
		}
		state.airComboActive = airborne;

		int length = weapon.comboLength();
		if (type == Attack.HEAVY) {
			performHeavy(player, world, stack, weapon, state);
			state.comboStep = 0;
			state.nextAttackTick = now + weapon.heavyDelayTicks();
			broadcastAnimation(player, "heavy");
		} else {
			int step = state.comboStep;
			boolean finisher = step >= length - 1;
			performLight(player, world, stack, weapon, state, step, finisher, airborne);
			state.comboStep = finisher ? 0 : step + 1;
			// Nach dem Finisher eine laengere Pause, damit die Combo einen Rhythmus hat.
			state.nextAttackTick = now + weapon.lightDelayTicks() * (finisher ? 2 : 1);
			broadcastAnimation(player, (airborne ? "air_" : "combo_") + (finisher ? "finisher" : String.valueOf(step + 1)));
		}
		state.lastAttackTick = now;
	}

	private static void performLight(ServerPlayerEntity player, ServerWorld world, ItemStack stack, ComboProfile weapon,
			CombatState state, int step, boolean finisher, boolean airborne) {
		float multiplier = finisher ? weapon.finisherMultiplier() : step == 0 ? 1.0f : weapon.stepMultiplier();
		double reach = player.getEntityInteractionRange() + weapon.reachBonus();
		List<LivingEntity> targets = finisher && !airborne
				? livingAround(player, FINISHER_RADIUS + weapon.reachBonus())
				: targetsInFront(player, state, reach);

		Vec3d look = horizontalLook(player);
		for (LivingEntity target : targets) {
			if (!hit(player, world, stack, weapon, target, multiplier)) {
				continue;
			}
			if (airborne) {
				// Luftcombo: Ziel bleibt in der Luft, Finisher schlaegt es nach unten.
				target.setVelocity(target.getVelocity().x * 0.3, finisher ? -1.0 : 0.3, target.getVelocity().z * 0.3);
			} else {
				target.takeKnockback(finisher ? 0.9 : 0.25, -look.x, -look.z);
			}
			target.velocityModified = true;
		}
		if (airborne) {
			// Der Spieler schwebt waehrend der Luftcombo kurz.
			Vec3d velocity = player.getVelocity();
			player.setVelocity(velocity.x * 0.5, finisher ? -0.4 : Math.max(velocity.y, 0.12), velocity.z * 0.5);
			player.velocityModified = true;
			player.fallDistance = 0.0f;
		}
		if (finisher) {
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getBodyY(0.5), player.getZ(), 6, 1.2, 0.2, 1.2, 0.0);
			sound(world, player, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
		} else {
			player.spawnSweepAttackParticles();
			sound(world, player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.2f + step * 0.15f);
		}
	}

	private static void performHeavy(ServerPlayerEntity player, ServerWorld world, ItemStack stack, ComboProfile weapon, CombatState state) {
		double reach = player.getEntityInteractionRange() + weapon.reachBonus() + 0.5;
		Vec3d look = horizontalLook(player);
		for (LivingEntity target : targetsInFront(player, state, reach)) {
			if (hit(player, world, stack, weapon, target, weapon.heavyMultiplier())) {
				target.takeKnockback(1.3, -look.x, -look.z);
				target.addVelocity(0.0, 0.25, 0.0);
				target.velocityModified = true;
				world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
			}
		}
		sound(world, player, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
	}

	/**
	 * Fuegt Schaden mit Waffenbonus, kritischen Treffern und Verzauberungen zu.
	 * Unverwundbarkeits-Ticks des Ziels werden fuer fluessige Combos ausgesetzt.
	 */
	private static boolean hit(ServerPlayerEntity player, ServerWorld world, ItemStack stack, ComboProfile weapon,
			LivingEntity target, float multiplier) {
		float base = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) + weapon.bonusDamage();
		boolean critical = weapon.critChance() > 0 && player.getRandom().nextFloat() < weapon.critChance();
		DamageSource source = world.getDamageSources().playerAttack(player);
		float damage = EnchantmentHelper.getDamage(world, stack, target, source, base * multiplier * (critical ? 1.5f : 1.0f));
		target.timeUntilRegen = 0;
		if (!target.damage(source, damage)) {
			return false;
		}
		EnchantmentHelper.onTargetDamaged(world, target, source, stack);
		player.onAttacking(target);
		MagicManager.onMeleeHit(player);
		if (critical) {
			player.addCritParticles(target);
			world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.8f, 1.0f);
		}
		stack.damage(1, player, EquipmentSlot.MAINHAND);
		return true;
	}

	/** Lock-On-Ziel in Reichweite, sonst alle Lebewesen in einem 140°-Kegel vor dem Spieler. */
	private static List<LivingEntity> targetsInFront(ServerPlayerEntity player, CombatState state, double reach) {
		Entity locked = state.lockTargetId >= 0 ? player.getServerWorld().getEntityById(state.lockTargetId) : null;
		if (locked instanceof LivingEntity living && living.isAlive() && player.squaredDistanceTo(living) <= (reach + 0.5) * (reach + 0.5)) {
			return List.of(living);
		}
		Vec3d eye = player.getEyePos();
		Vec3d look = horizontalLook(player);
		List<LivingEntity> result = new ArrayList<>();
		for (LivingEntity candidate : livingAround(player, reach)) {
			Vec3d toTarget = candidate.getPos().add(0, candidate.getHeight() / 2, 0).subtract(eye);
			Vec3d flat = new Vec3d(toTarget.x, 0, toTarget.z);
			if (flat.lengthSquared() < 0.25 || flat.normalize().dotProduct(look) >= HIT_HALF_ANGLE_COS) {
				result.add(candidate);
			}
		}
		return result;
	}

	private static List<LivingEntity> livingAround(ServerPlayerEntity player, double radius) {
		double radiusSq = radius * radius;
		return player.getServerWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
				e -> e != player && e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(player) <= radiusSq && !isOwnPet(player, e));
	}

	private static boolean isOwnPet(ServerPlayerEntity player, LivingEntity entity) {
		return entity instanceof TameableEntity tameable && tameable.isTamed() && player.equals(tameable.getOwner());
	}

	// --- Ausweichen -----------------------------------------------------------------------------

	public static void dodge(ServerPlayerEntity player, float directionX, float directionZ) {
		if (!player.isAlive() || player.isSpectator() || player.hasVehicle() || player.getAbilities().flying) {
			return;
		}
		CombatState state = state(player);
		long now = player.getWorld().getTime();
		if (now < state.dodgeReadyTick) {
			return;
		}
		boolean onGround = player.isOnGround();
		if (onGround) {
			state.airDodgeUsed = false;
		} else if (state.airDodgeUsed) {
			return;
		} else {
			state.airDodgeUsed = true;
		}
		Vec3d direction = new Vec3d(directionX, 0, directionZ);
		if (!Double.isFinite(direction.x) || !Double.isFinite(direction.z) || direction.lengthSquared() < 1.0E-4) {
			direction = horizontalLook(player).multiply(-1);
		}
		direction = direction.normalize();
		player.setVelocity(direction.x * DODGE_SPEED, onGround ? 0.2 : 0.1, direction.z * DODGE_SPEED);
		player.velocityModified = true;
		player.fallDistance = 0.0f;
		state.invulnerableUntil = now + DODGE_INVULNERABLE_TICKS;
		state.dodgeReadyTick = now + DODGE_COOLDOWN_TICKS;
		if (state.guarding) {
			setGuard(player, false);
		}
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
		sound(world, player, SoundEvents.ENTITY_PHANTOM_FLAP, 0.6f, 1.6f);
		broadcastAnimation(player, "dodge");
	}

	// --- Blocken --------------------------------------------------------------------------------

	public static void setGuard(ServerPlayerEntity player, boolean active) {
		CombatState state = state(player);
		ItemStack stack = comboWeapon(player);
		boolean allowed = active && !stack.isEmpty() && player.isAlive()
				&& ((ComboWeapon) stack.getItem()).comboProfile(player.getServerWorld().getRegistryManager(), stack).canGuard();
		if (allowed == state.guarding) {
			return;
		}
		state.guarding = allowed;
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (allowed) {
			state.guardStartTick = player.getWorld().getTime();
			if (speed != null && !speed.hasModifier(GUARD_SLOW_MODIFIER)) {
				speed.addTemporaryModifier(new EntityAttributeModifier(GUARD_SLOW_MODIFIER, -0.6,
						EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			broadcastAnimation(player, "guard");
		} else {
			if (speed != null) {
				speed.removeModifier(GUARD_SLOW_MODIFIER);
			}
			broadcastAnimation(player, "guard_end");
		}
	}

	private static boolean allowDamage(ServerPlayerEntity player, DamageSource source) {
		CombatState state = STATES.get(player.getUuid());
		if (state == null) {
			return true;
		}
		long now = player.getWorld().getTime();
		if (now < state.invulnerableUntil && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (!state.guarding || source.isIn(DamageTypeTags.BYPASSES_SHIELD)) {
			return true;
		}
		Vec3d from = source.getPosition();
		if (from == null) {
			return true;
		}
		Vec3d toSource = from.subtract(player.getPos());
		Vec3d flat = new Vec3d(toSource.x, 0, toSource.z);
		if (flat.lengthSquared() > 1.0E-4 && flat.normalize().dotProduct(horizontalLook(player)) < 0.0) {
			return true; // von hinten: Blocken hilft nicht
		}
		ServerWorld world = player.getServerWorld();
		boolean perfect = now - state.guardStartTick <= PERFECT_GUARD_TICKS;
		if (source.getAttacker() instanceof LivingEntity attacker && attacker != player) {
			Vec3d push = attacker.getPos().subtract(player.getPos());
			attacker.takeKnockback(perfect ? 1.2 : 0.4, -push.x, -push.z);
			if (perfect) {
				attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 4), player);
				attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 1), player);
			}
		}
		world.spawnParticles(perfect ? ParticleTypes.FLASH : ParticleTypes.CRIT, player.getX(), player.getBodyY(0.6), player.getZ(),
				perfect ? 1 : 6, 0.3, 0.3, 0.3, 0.1);
		sound(world, player, SoundEvents.ITEM_SHIELD_BLOCK, 1.0f, perfect ? 1.6f : 1.0f);
		return false;
	}

	// --- Lock-On --------------------------------------------------------------------------------

	public static void setLockTarget(ServerPlayerEntity player, int entityId) {
		CombatState state = state(player);
		if (entityId < 0) {
			state.lockTargetId = -1;
			return;
		}
		Entity entity = player.getServerWorld().getEntityById(entityId);
		boolean valid = entity instanceof LivingEntity living && living.isAlive() && entity != player
				&& player.squaredDistanceTo(entity) <= LOCK_RANGE * LOCK_RANGE;
		state.lockTargetId = valid ? entityId : -1;
	}

	// --- Ablauf ---------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, CombatState> entry : STATES.entrySet()) {
			CombatState state = entry.getValue();
			if (!state.guarding && state.lockTargetId < 0) {
				continue;
			}
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			if (state.guarding && (comboWeapon(player).isEmpty() || !player.isAlive())) {
				setGuard(player, false);
			}
			if (state.lockTargetId >= 0) {
				Entity target = player.getServerWorld().getEntityById(state.lockTargetId);
				if (!(target instanceof LivingEntity living) || !living.isAlive() || player.squaredDistanceTo(target) > LOCK_RANGE * LOCK_RANGE) {
					state.lockTargetId = -1;
				}
			}
		}
	}

	// --- Hilfen ---------------------------------------------------------------------------------

	private static Vec3d horizontalLook(ServerPlayerEntity player) {
		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
	}

	private static void sound(ServerWorld world, ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}

	/** Teilt allen Clients in Sichtweite (und dem Spieler selbst) mit, welche Kampfanimation laeuft. */
	private static void broadcastAnimation(ServerPlayerEntity player, String animation) {
		CombatAnimationPayload payload = new CombatAnimationPayload(player.getId(), animation);
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
			if (watcher != player) {
				ServerPlayNetworking.send(watcher, payload);
			}
		}
	}
}
