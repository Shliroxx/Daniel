package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Brainstorm (Cerebrocrustacean). Eigenes System „Elektro-Intellekt“ — ohne Aura:
 *
 * <ul>
 *   <li><b>Analyse</b>: analysierte Gegner (Leuchten) nehmen +30 % Schaden von allen — und leiten Blitze weiter:
 *       jeder Blitz, der ein analysiertes Ziel trifft, springt auf zwei weitere.</li>
 *   <li><b>Kraftfeld</b>: saugt Schaden auf; aufgesogener Schaden laedt den naechsten Blitz auf (×1 je 5 Punkte).</li>
 *   <li>Brainstorm schwebt mit Elektro-Levitation und laesst im Gedankensturm echte (harmlose) Blitze einschlagen.</li>
 * </ul>
 */
final class BrainstormAbilities {
	private static final Identifier BRAINSTORM = KingdomOmnitrix.id("brainstorm");
	private static final DustParticleEffect PINK = AlienKit.dust("#F07AC8", 1.4f);
	private static final DustParticleEffect BOLT = AlienKit.dust("#E6F4FF", 1.0f);

	/** analysierte Gegner: bis wann, von wem */
	private static final Map<UUID, long[]> ANALYZED = new HashMap<>();
	/** Kraftfeld bis, gespeicherte Ladung */
	private static final Map<UUID, Long> FIELD = new HashMap<>();
	private static final Map<UUID, Float> STORED = new HashMap<>();
	/** Statikgitter: Zentrum und Ende */
	private static final Map<UUID, Object[]> GRIDS = new HashMap<>();
	/** Gedankensturm: Start */
	private static final Map<UUID, Long> STORMS = new HashMap<>();
	private static final Map<UUID, Long> LEVITATE = new HashMap<>();

	private BrainstormAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("lightning_arc"), BrainstormAbilities::lightningArc);
		AbilityRegistry.register(KingdomOmnitrix.id("static_grid"), BrainstormAbilities::staticGrid);
		AbilityRegistry.register(KingdomOmnitrix.id("electro_levitation"), BrainstormAbilities::levitation);
		AbilityRegistry.register(KingdomOmnitrix.id("force_field"), BrainstormAbilities::forceField);
		AbilityRegistry.register(KingdomOmnitrix.id("brain_analysis"), BrainstormAbilities::analysis);
		AbilityRegistry.register(KingdomOmnitrix.id("mind_storm"), BrainstormAbilities::mindStorm);
		ServerTickEvents.END_SERVER_TICK.register(BrainstormAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BrainstormAbilities::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			// Analyse: +30 % von allen Quellen
			if (taken > 0.0f && !AlienKit.bonusHit && analyzed(target)) {
				AlienKit.hit(target, source, taken * 0.3f);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			ANALYZED.remove(entity.getUuid());
		});
	}

	private static boolean isBrainstorm(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(BRAINSTORM::equals).isPresent();
	}

	private static boolean analyzed(LivingEntity target) {
		long[] until = ANALYZED.get(target.getUuid());
		return until != null && target.getWorld().getTime() < until[0];
	}

	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayerEntity brain && FIELD.containsKey(brain.getUuid()) && amount > 0.0f
				&& brain.getWorld().getTime() < FIELD.get(brain.getUuid())) {
			STORED.merge(brain.getUuid(), amount, Float::sum);
			brain.getServerWorld().spawnParticles(PINK, brain.getX(), brain.getBodyY(0.5), brain.getZ(), 10, 0.8, 0.8, 0.8, 0.0);
			brain.getServerWorld().playSound(null, brain.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.0f, 1.6f);
			return false;
		}
		return true;
	}

	/** Blitz von einem Punkt auf ein Ziel; analysierte Ziele leiten auf zwei weitere weiter. */
	private static void zap(ServerWorld world, ServerPlayerEntity brain, Vec3d from, LivingEntity target, float damage, Set<UUID> done) {
		done.add(target.getUuid());
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
		jagged(world, from, to);
		AlienKit.magic(world, brain, target, damage);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 3), brain);
		if (analyzed(target)) {
			world.getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(7.0), e -> AlienKit.foe(brain, e) && !done.contains(e.getUuid()))
					.stream().sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(target))).limit(2)
					.forEach(next -> zap(world, brain, to, next, damage * 0.8f, done));
		}
	}

	/** Zackige Blitzlinie. */
	private static void jagged(ServerWorld world, Vec3d from, Vec3d to) {
		Vec3d prev = from;
		int segments = Math.max(3, (int) (from.distanceTo(to) / 1.5));
		for (int i = 1; i <= segments; i++) {
			Vec3d p = from.lerp(to, i / (double) segments);
			if (i < segments) {
				p = p.add((world.random.nextDouble() - 0.5) * 0.6, (world.random.nextDouble() - 0.5) * 0.6, (world.random.nextDouble() - 0.5) * 0.6);
			}
			AlienKit.line(world, prev, p, BOLT, 4.0);
			prev = p;
		}
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0.2);
	}

	private static float spendStored(ServerPlayerEntity brain) {
		Float stored = STORED.remove(brain.getUuid());
		return stored == null ? 1.0f : 1.0f + stored / 5.0f;
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Blitzbogen: aus dem Gehirn auf das anvisierte Ziel — springt ueber analysierte Gegner weiter. */
	private static boolean lightningArc(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		ServerWorld world = ctx.world();
		AlienKit.Beam beam = AlienKit.beam(world, brain, ctx.param("range", 20.0));
		Vec3d head = brain.getEyePos().add(0, 0.4, 0);
		if (beam.target().isEmpty()) {
			jagged(world, head, beam.end());
		} else {
			zap(world, brain, head, beam.target().get(), (float) ctx.param("damage", 6.0) * spendStored(brain), new HashSet<>());
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);
		return true;
	}

	/** Statikgitter: Feld am Zielpunkt — jede Sekunde ein Blitz auf alles darin (5 s). */
	private static boolean staticGrid(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		AlienKit.Beam beam = AlienKit.beam(ctx.world(), brain, ctx.param("range", 14.0));
		GRIDS.put(brain.getUuid(), new Object[]{beam.end(), ctx.world().getTime() + (long) (ctx.param("seconds", 5.0) * 20), ctx.param("radius", 4.0)});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
		return true;
	}

	/** Elektro-Levitation: Brainstorm schwebt einige Sekunden (Langsamer Fall + Auftrieb). */
	private static boolean levitation(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		int ticks = (int) (ctx.param("seconds", 5.0) * 20);
		LEVITATE.put(brain.getUuid(), ctx.world().getTime() + ticks);
		brain.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, ticks + 40, 0, false, false));
		brain.setVelocity(brain.getVelocity().add(0, 0.6, 0));
		brain.velocityModified = true;
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_AMBIENT, 1.4f, 1.6f);
		return true;
	}

	/** Kraftfeld: 5 s lang wird jeder Schaden aufgesogen und laedt den naechsten Blitz. */
	private static boolean forceField(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		FIELD.put(brain.getUuid(), ctx.world().getTime() + (long) (ctx.param("seconds", 5.0) * 20));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 2.0f);
		return true;
	}

	/** Analyse: alle Gegner im Blickfeld werden 15 s analysiert (leuchten, +30 % Schaden, leiten Blitze). */
	private static boolean analysis(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 15.0) * 20);
		int count = 0;
		for (LivingEntity target : AlienKit.cone(world, brain, ctx.param("range", 20.0), 0.5)) {
			ANALYZED.put(target.getUuid(), new long[]{world.getTime() + ticks});
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ticks, 0), brain);
			world.spawnParticles(PINK, target.getX(), target.getBodyY(1.0) + 0.3, target.getZ(), 8, 0.3, 0.2, 0.3, 0.0);
			count++;
		}
		if (count == 0) {
			brain.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		brain.sendMessage(Text.translatable("message.kingdomomnitrix.brainstorm_analysis", count).formatted(Formatting.LIGHT_PURPLE), true);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f, 1.4f);
		return true;
	}

	/** Gedankensturm: 4 s lang schlagen Blitze in Gegner im Umkreis ein (Optik echter Blitze, kein Feuer). */
	private static boolean mindStorm(AbilityContext ctx) {
		STORMS.put(ctx.player().getUuid(), ctx.world().getTime());
		ctx.grantInvulnerability(20);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.2f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity brain : server.getPlayerManager().getPlayerList()) {
			UUID id = brain.getUuid();
			if (!isBrainstorm(brain)) {
				forget(id);
				continue;
			}
			ServerWorld world = brain.getServerWorld();
			long now = world.getTime();
			Long field = FIELD.get(id);
			if (field != null) {
				if (now >= field) {
					FIELD.remove(id);
				} else if (now % 3 == 0) {
					for (int i = 0; i < 10; i++) {
						double a = i * MathHelper.TAU / 10 + now * 0.15;
						world.spawnParticles(PINK, brain.getX() + Math.cos(a) * 1.3, brain.getBodyY(0.2 + (i % 3) * 0.35),
								brain.getZ() + Math.sin(a) * 1.3, 1, 0.0, 0.0, 0.0, 0.0);
					}
				}
			}
			Long levitate = LEVITATE.get(id);
			if (levitate != null) {
				if (now >= levitate) {
					LEVITATE.remove(id);
				} else {
					brain.setVelocity(brain.getVelocity().x, Math.max(brain.getVelocity().y, brain.isSneaking() ? -0.15 : 0.02), brain.getVelocity().z);
					brain.velocityModified = true;
					brain.fallDistance = 0.0f;
					if (now % 4 == 0) {
						world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, brain.getX(), brain.getY(), brain.getZ(), 3, 0.3, 0.05, 0.3, 0.05);
					}
				}
			}
			Object[] grid = GRIDS.get(id);
			if (grid != null) {
				Vec3d center = (Vec3d) grid[0];
				if (now >= (long) grid[1]) {
					GRIDS.remove(id);
				} else {
					double radius = (double) grid[2];
					if (now % 5 == 0) {
						AlienKit.ring(world, center.add(0, 0.2, 0), radius, BOLT);
					}
					if (now % 20 == 0) {
						for (LivingEntity target : AlienKit.around(world, brain, center, radius)) {
							zap(world, brain, center.add(0, 2.5, 0), target, 3.0f, new HashSet<>());
						}
					}
				}
			}
			Long storm = STORMS.get(id);
			if (storm != null) {
				long age = now - storm;
				if (age >= 80) {
					STORMS.remove(id);
				} else if (age % 8 == 0) {
					Optional<LivingEntity> target = AlienKit.around(world, brain, brain.getPos(), 14.0).stream()
							.skip(world.random.nextInt(Math.max(1, AlienKit.around(world, brain, brain.getPos(), 14.0).size()))).findFirst();
					target.ifPresent(t -> {
						net.minecraft.entity.LightningEntity bolt = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(world);
						if (bolt != null) {
							bolt.refreshPositionAfterTeleport(t.getX(), t.getY(), t.getZ());
							bolt.setCosmetic(true);  // nur Optik: kein Feuer, kein Vanilla-Schaden
							world.spawnEntity(bolt);
						}
						zap(world, brain, t.getPos().add(0, 6, 0), t, 8.0f, new HashSet<>());
					});
				}
			}
		}
		if (!ANALYZED.isEmpty() && server.getTicks() % 40 == 0) {
			long now = server.getOverworld().getTime();
			ANALYZED.values().removeIf(until -> until[0] <= now);
		}
	}

	private static void forget(UUID id) {
		FIELD.remove(id);
		STORED.remove(id);
		GRIDS.remove(id);
		STORMS.remove(id);
		LEVITATE.remove(id);
	}
}
