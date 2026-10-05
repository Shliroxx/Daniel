package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Goop (Polymorph). Eigenes System „Polymorph“ — ohne Aura:
 *
 * <ul>
 *   <li><b>Schleimkoerper</b>: Geschosse gehen durch Goop hindurch, Fallschaden gibt es nicht.</li>
 *   <li><b>Saeure</b> (0–5 im Gegner): jede Sekunde Schaden je Stufe, frisst die Ruestung (Schwaeche).</li>
 *   <li><b>Projektor</b> ist die Schwachstelle: ein harter Treffer (8+) laesst Goop kurz zur Pfuetze zusammenfallen
 *       (2 s langsam, kein Angriff) — dafuer ist er in der Pfuetzenform selbst unverwundbar.</li>
 *   <li><b>Verschlingen</b>: Goop schliesst ein Ziel in sich ein und zersetzt es.</li>
 * </ul>
 */
final class GoopAbilities {
	static final int MAX_ACID = 5;
	private static final Identifier GOOP = KingdomOmnitrix.id("goop");
	private static final DustParticleEffect SLIME = AlienKit.dust("#5FD13A", 1.5f);
	private static final DustParticleEffect ACID = AlienKit.dust("#B6FF3A", 1.1f);

	/** Saeure im Gegner: Stufe, Ablauf, Verursacher */
	private static final Map<UUID, Acid> ACIDS = new HashMap<>();
	/** Pfuetzenform bis */
	private static final Map<UUID, Long> PUDDLE = new HashMap<>();
	/** Verschlingen: Ziel und Ende */
	private static final Map<UUID, Object[]> ENGULF = new HashMap<>();
	/** Saeurepfuetzen auf dem Boden */
	private static final List<Pool> POOLS = new ArrayList<>();
	/** Projektor getroffen: zusammengefallen bis */
	private static final Map<UUID, Long> COLLAPSED = new HashMap<>();

	private static final class Acid {
		int level;
		long until;
		UUID source;
		RegistryKey<World> world;
	}

	private record Pool(UUID owner, RegistryKey<World> world, Vec3d center, double radius, long until) {
	}

	private GoopAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("acid_spit"), GoopAbilities::acidSpit);
		AbilityRegistry.register(KingdomOmnitrix.id("slime_whip"), GoopAbilities::slimeWhip);
		AbilityRegistry.register(KingdomOmnitrix.id("puddle_form"), GoopAbilities::puddleForm);
		AbilityRegistry.register(KingdomOmnitrix.id("engulf"), GoopAbilities::engulf);
		AbilityRegistry.register(KingdomOmnitrix.id("acid_pool"), GoopAbilities::acidPool);
		AbilityRegistry.register(KingdomOmnitrix.id("acid_flood"), GoopAbilities::acidFlood);
		ServerTickEvents.END_SERVER_TICK.register(GoopAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			PUDDLE.remove(id);
			ENGULF.remove(id);
			COLLAPSED.remove(id);
			POOLS.removeIf(p -> p.owner().equals(id));
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GoopAbilities::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken <= 0.0f || AlienKit.bonusHit) {
				return;
			}
			if (source.getAttacker() instanceof ServerPlayerEntity goop && goop != target && source.getSource() == goop && isGoop(goop)) {
				corrode(goop, target, 1);
			}
			// Projektor getroffen: Goop faellt kurz zusammen
			if (target instanceof ServerPlayerEntity goop && isGoop(goop) && taken >= 8.0f && !PUDDLE.containsKey(goop.getUuid())) {
				COLLAPSED.put(goop.getUuid(), goop.getServerWorld().getTime() + 40);
				goop.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3, false, false));
				goop.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 4, false, false));
				goop.sendMessage(Text.translatable("message.kingdomomnitrix.goop_projector").formatted(Formatting.RED), true);
				goop.getServerWorld().playSound(null, goop.getBlockPos(), SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.PLAYERS, 1.4f, 0.6f);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> ACIDS.remove(entity.getUuid()));
	}

	private static boolean isGoop(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(GOOP::equals).isPresent();
	}

	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayerEntity goop && isGoop(goop)) {
			if (source.getSource() instanceof ProjectileEntity) {
				goop.getServerWorld().spawnParticles(SLIME, goop.getX(), goop.getBodyY(0.5), goop.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
				return false;  // geht durch den Schleim
			}
			if (PUDDLE.containsKey(goop.getUuid())) {
				return false;
			}
		}
		return true;
	}

	private static void corrode(ServerPlayerEntity goop, LivingEntity target, int amount) {
		if (!AlienKit.foe(goop, target)) {
			return;
		}
		ServerWorld world = goop.getServerWorld();
		Acid acid = ACIDS.computeIfAbsent(target.getUuid(), id -> new Acid());
		acid.level = Math.min(MAX_ACID, acid.level + amount);
		acid.until = world.getTime() + 100;
		acid.source = goop.getUuid();
		acid.world = world.getRegistryKey();
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, Math.min(2, acid.level / 2)), goop);
		world.spawnParticles(ACID, target.getX(), target.getBodyY(0.6), target.getZ(), 3 + acid.level * 2, 0.3, 0.4, 0.3, 0.0);
	}

	private static boolean canAct(ServerPlayerEntity goop) {
		Long collapsed = COLLAPSED.get(goop.getUuid());
		if (collapsed != null && goop.getServerWorld().getTime() < collapsed) {
			goop.sendMessage(Text.translatable("message.kingdomomnitrix.goop_collapsed").formatted(Formatting.GRAY), true);
			return false;
		}
		return true;
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Saeurespucke: Schleimstrahl auf das erste Ziel, +2 Saeure. */
	private static boolean acidSpit(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		if (!canAct(goop)) {
			return false;
		}
		ServerWorld world = ctx.world();
		AlienKit.Beam beam = AlienKit.beam(world, goop, ctx.param("range", 16.0));
		AlienKit.line(world, AlienKit.muzzle(goop), beam.end(), ACID, 3.0);
		beam.target().ifPresent(target -> {
			AlienKit.magic(world, goop, target, (float) ctx.param("damage", 4.0));
			corrode(goop, target, 2);
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LLAMA_SPIT, 1.2f, 0.7f);
		return true;
	}

	/** Schleimpeitsche: der Arm dehnt sich zum Ziel und zieht es heran (+1 Saeure). */
	private static boolean slimeWhip(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		if (!canAct(goop)) {
			return false;
		}
		ServerWorld world = ctx.world();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(goop, ctx.param("range", 12.0), 0.85, e -> AlienKit.foe(goop, e));
		if (found.isEmpty()) {
			goop.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		AlienKit.line(world, goop.getPos().add(0, 1.2, 0), target.getPos().add(0, target.getHeight() * 0.5, 0), SLIME, 3.0);
		Vec3d pull = goop.getPos().subtract(target.getPos()).normalize().multiply(1.4);
		target.setVelocity(pull.x, 0.35, pull.z);
		target.velocityModified = true;
		AlienKit.melee(world, goop, target, (float) ctx.param("damage", 5.0));
		corrode(goop, target, 1);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_SLIME_BLOCK_STEP, 1.4f, 0.6f);
		return true;
	}

	/** Pfuetzenform: flach, schnell, unverwundbar; wer durchquert wird, bekommt Saeure. */
	private static boolean puddleForm(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		PUDDLE.put(goop.getUuid(), ctx.world().getTime() + ticks);
		COLLAPSED.remove(goop.getUuid());
		goop.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, ticks, 0, false, false));
		goop.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 2, false, false));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_SLIME_BLOCK_FALL, 1.4f, 0.6f);
		return true;
	}

	/** Verschlingen: Goop schliesst das Ziel ein — festgehalten, jede Sekunde Saeure, 4 s. */
	private static boolean engulf(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		if (!canAct(goop)) {
			return false;
		}
		Optional<LivingEntity> found = Targeting.findMeleeTarget(goop, ctx.param("range", 4.0), 0.7, e -> AlienKit.foe(goop, e));
		if (found.isEmpty()) {
			goop.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		int ticks = (int) (ctx.param("seconds", 4.0) * 20);
		ENGULF.put(goop.getUuid(), new Object[]{found.get().getUuid(), ctx.world().getTime() + ticks});
		AlienKit.hold(ctx.world(), found.get(), ticks, SLIME, false);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_SQUISH, 1.4f, 0.5f);
		return true;
	}

	/** Saeurepfuetze: Lache am Zielpunkt (8 s) — wer drin steht, zersetzt sich. */
	private static boolean acidPool(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		AlienKit.Beam beam = AlienKit.beam(ctx.world(), goop, ctx.param("range", 12.0));
		POOLS.add(new Pool(goop.getUuid(), ctx.world().getRegistryKey(), beam.end(), ctx.param("radius", 3.5),
				ctx.world().getTime() + (long) (ctx.param("seconds", 8.0) * 20)));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_JUMP, 1.4f, 0.5f);
		return true;
	}

	/** Saeureflut: Goop zerfliesst zu einer riesigen Lache um sich — alles darin bekommt volle Saeure. */
	private static boolean acidFlood(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 9.0);
		POOLS.add(new Pool(goop.getUuid(), world.getRegistryKey(), goop.getPos(), radius, world.getTime() + (long) (ctx.param("seconds", 6.0) * 20)));
		for (LivingEntity target : AlienKit.around(world, goop, goop.getPos(), radius)) {
			corrode(goop, target, MAX_ACID);
			AlienKit.magic(world, goop, target, (float) ctx.param("damage", 8.0));
		}
		ctx.grantInvulnerability(30);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_SLIME_BLOCK_BREAK, 2.0f, 0.4f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity goop : server.getPlayerManager().getPlayerList()) {
			UUID id = goop.getUuid();
			if (!isGoop(goop)) {
				PUDDLE.remove(id);
				ENGULF.remove(id);
				COLLAPSED.remove(id);
				continue;
			}
			goop.fallDistance = 0.0f;
			ServerWorld world = goop.getServerWorld();
			long now = world.getTime();
			Long puddle = PUDDLE.get(id);
			if (puddle != null) {
				if (now >= puddle) {
					PUDDLE.remove(id);
				} else {
					world.spawnParticles(SLIME, goop.getX(), goop.getY() + 0.05, goop.getZ(), 5, 0.5, 0.0, 0.5, 0.0);
					for (LivingEntity target : AlienKit.around(world, goop, goop.getPos(), 1.2)) {
						if (now % 10 == 0) {
							corrode(goop, target, 1);
						}
					}
				}
			}
			Object[] engulf = ENGULF.get(id);
			if (engulf != null) {
				Entity entity = world.getEntity((UUID) engulf[0]);
				if (!(entity instanceof LivingEntity target) || !target.isAlive() || now >= (long) engulf[1]) {
					ENGULF.remove(id);
				} else if (now % 20 == 0) {
					corrode(goop, target, 1);
					AlienKit.magic(world, goop, target, 3.0f);
					goop.heal(1.0f);
				}
			}
		}
		if (!POOLS.isEmpty()) {
			for (Iterator<Pool> it = POOLS.iterator(); it.hasNext(); ) {
				Pool pool = it.next();
				ServerWorld world = server.getWorld(pool.world());
				ServerPlayerEntity owner = server.getPlayerManager().getPlayer(pool.owner());
				if (world == null || owner == null || world.getTime() >= pool.until()) {
					it.remove();
					continue;
				}
				if (world.getTime() % 4 == 0) {
					world.spawnParticles(ACID, pool.center().x, pool.center().y + 0.1, pool.center().z, (int) (pool.radius() * 4), pool.radius() * 0.5,
							0.02, pool.radius() * 0.5, 0.0);
				}
				if (world.getTime() % 20 == 0) {
					for (LivingEntity target : AlienKit.around(world, owner, pool.center(), pool.radius())) {
						corrode(owner, target, 1);
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1), owner);
					}
				}
			}
		}
		if (!ACIDS.isEmpty() && server.getTicks() % 20 == 0) {
			List<Object[]> pending = new ArrayList<>();
			for (Iterator<Map.Entry<UUID, Acid>> it = ACIDS.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Acid> entry = it.next();
				Acid acid = entry.getValue();
				ServerWorld world = server.getWorld(acid.world);
				Entity entity = world == null ? null : world.getEntity(entry.getKey());
				ServerPlayerEntity owner = server.getPlayerManager().getPlayer(acid.source);
				if (!(entity instanceof LivingEntity target) || !target.isAlive() || owner == null || world.getTime() >= acid.until) {
					it.remove();
					continue;
				}
				pending.add(new Object[]{world, owner, target, (float) acid.level});
			}
			// erst sammeln, dann schaden (ein Tod veraendert die Tabelle)
			for (Object[] p : pending) {
				AlienKit.magic((ServerWorld) p[0], (ServerPlayerEntity) p[1], (LivingEntity) p[2], (float) p[3]);
			}
		}
	}
}
