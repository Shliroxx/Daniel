package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.EchoCloneEntity;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Echo Echo (Sonorosianer). Eigenes System „Echo-Chor“ — ohne Aura:
 *
 * <ul>
 *   <li><b>Klone</b> (echte Wesen, {@link EchoCloneEntity}): folgen, locken Monster von Echo Echo weg und schreien
 *       selbst. Hoechstens {@link #MAX_CLONES}; sie verklingen beim Zurueckverwandeln.</li>
 *   <li><b>Chor</b>: jede Schall-Faehigkeit kommt von Echo Echo <i>und</i> von jedem Klon zugleich — Kreuzfeuer aus
 *       allen Richtungen.</li>
 *   <li><b>Schallresonanz</b>: trifft Schall aus zwei oder mehr Quellen dasselbe Ziel innerhalb von
 *       {@link #RESONANCE_TICKS} Ticks, schwingt es mit — Bonusschaden je weiterer Quelle und kurze Laehmung.</li>
 * </ul>
 */
final class EchoEchoAbilities {
	static final int MAX_CLONES = 6;
	static final int RESONANCE_TICKS = 10;
	private static final Identifier ECHO_ECHO = KingdomOmnitrix.id("echo_echo");
	private static final float RESONANCE_BONUS = 3.0f;
	private static final int CLONE_LIFE = 600;

	/** Schalltreffer je Ziel: Zeit des ersten Treffers im Fenster und Zahl der Quellen */
	private static final Map<UUID, long[]> HEARD = new HashMap<>();
	/** Schallschild bis */
	private static final Map<UUID, Long> SHIELD = new HashMap<>();
	/** Schallmauer: Ende und Pulsabstand */
	private static final Map<UUID, Long> WALLS = new HashMap<>();
	/** Echo-Chor: Start (drei Schreie im Sekundentakt) */
	private static final Map<UUID, Long> CHORUS = new HashMap<>();
	/** verhindert, dass Resonanzschaden erneut zaehlt */
	private static boolean bonusHit;

	private EchoEchoAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("sonic_scream"), EchoEchoAbilities::sonicScream);
		AbilityRegistry.register(KingdomOmnitrix.id("echo_split"), EchoEchoAbilities::echoSplit);
		AbilityRegistry.register(KingdomOmnitrix.id("sonic_boost"), EchoEchoAbilities::sonicBoost);
		AbilityRegistry.register(KingdomOmnitrix.id("wall_of_sound"), EchoEchoAbilities::wallOfSound);
		AbilityRegistry.register(KingdomOmnitrix.id("sound_shield"), EchoEchoAbilities::soundShield);
		AbilityRegistry.register(KingdomOmnitrix.id("echo_chorus"), EchoEchoAbilities::echoChorus);
		// Klone schreien selbst: kurzer Schrei auf ihr Ziel, zaehlt fuer die Resonanz
		EchoCloneEntity.onScream((clone, target) -> {
			ServerPlayerEntity owner = clone.ownerPlayer();
			if (owner != null && clone.getWorld() instanceof ServerWorld world) {
				soundHit(world, owner, target, 2.5f, clone.getPos(), 0.4);
				screamFx(world, clone.getEyePos(), target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(clone.getEyePos()).normalize(), 4.0);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(EchoEchoAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			dismissClones(handler.getPlayer());
			forgetState(handler.getPlayer().getUuid());
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(EchoEchoAbilities::allowDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> HEARD.remove(entity.getUuid()));
	}

	private static boolean isEchoEcho(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(ECHO_ECHO::equals).isPresent();
	}

	// --- Klone -----------------------------------------------------------------------------------

	static List<EchoCloneEntity> clones(ServerPlayerEntity player) {
		return player.getServerWorld().getEntitiesByClass(EchoCloneEntity.class, player.getBoundingBox().expand(48.0),
				c -> c.isAlive() && player.getUuid().equals(c.ownerId()));
	}

	private static int spawnClones(ServerWorld world, ServerPlayerEntity player, int count, int life) {
		int existing = clones(player).size();
		int spawned = 0;
		int max = maxClones(player);
		for (int i = 0; i < count && existing + spawned < max; i++) {
			EchoCloneEntity clone = ModEntities.ECHO_CLONE.create(world);
			if (clone == null) {
				break;
			}
			double a = world.random.nextDouble() * MathHelper.TAU;
			Vec3d at = player.getPos().add(Math.cos(a) * 1.5, 0.1, Math.sin(a) * 1.5);
			clone.refreshPositionAndAngles(at.x, at.y, at.z, player.getYaw(), 0.0f);
			if (!world.isSpaceEmpty(clone)) {
				clone.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0f);
			}
			clone.setOwner(player, life);
			clone.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(at)), SpawnReason.MOB_SUMMONED, null);
			world.spawnEntity(clone);
			clone.taunt(world, player, 16.0);
			world.spawnParticles(ParticleTypes.NOTE, at.x, at.y + 0.6, at.z, 4, 0.2, 0.2, 0.2, 0.5);
			spawned++;
		}
		return spawned;
	}

	/** Ultimate: mehr Stimmen im Chor. */
	private static int maxClones(ServerPlayerEntity player) {
		return com.santiq.kingdomomnitrix.alien.Evolution.isUltimate(player) ? MAX_CLONES + 2 : MAX_CLONES;
	}

	private static void dismissClones(ServerPlayerEntity player) {
		for (EchoCloneEntity clone : clones(player)) {
			clone.vanish(player.getServerWorld());
		}
	}

	/** Alle Schallquellen: Echo Echo selbst und jeder Klon (Position, Blick-/Zielrichtung). */
	private static List<Vec3d[]> sources(ServerPlayerEntity player, Vec3d aim) {
		List<Vec3d[]> list = new ArrayList<>();
		Vec3d eye = player.getEyePos();
		list.add(new Vec3d[]{eye, aim.subtract(eye).normalize()});
		for (EchoCloneEntity clone : clones(player)) {
			Vec3d ce = clone.getEyePos();
			Vec3d dir = aim.subtract(ce);
			list.add(new Vec3d[]{ce, dir.lengthSquared() < 1.0E-4 ? clone.getRotationVec(1.0f) : dir.normalize()});
		}
		return list;
	}

	/** Zielpunkt des Chors: was Echo Echo anvisiert (Block oder Luft in Reichweite). */
	private static Vec3d aimPoint(ServerWorld world, ServerPlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		HitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
	}

	// --- Schall und Resonanz ---------------------------------------------------------------------

	/**
	 * Ein Schalltreffer: Schaden, Rueckstoss von der Quelle weg; zweite und weitere Quelle im Fenster → Resonanz.
	 */
	private static void soundHit(ServerWorld world, ServerPlayerEntity owner, LivingEntity target, float damage, Vec3d from, double knockback) {
		if (!target.isAlive() || target == owner || target instanceof EchoCloneEntity || !PartyRules.canHarm(owner, target)) {
			return;
		}
		long now = world.getTime();
		long[] heard = HEARD.get(target.getUuid());
		if (heard == null || now - heard[0] > RESONANCE_TICKS) {
			heard = new long[]{now, 0};
			HEARD.put(target.getUuid(), heard);
		}
		heard[1]++;
		bonusHit = true;
		try {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().indirectMagic(owner, owner), damage);
			if (heard[1] >= 2) {
				// Resonanz: schwingt mit — mehr Schaden je Quelle, kurz gelaehmt
				target.timeUntilRegen = 0;
				target.damage(world.getDamageSources().sonicBoom(owner), RESONANCE_BONUS * (heard[1] - 1));
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 6), owner);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0), owner);
				world.spawnParticles(ParticleTypes.NOTE, target.getX(), target.getBodyY(1.0) + 0.3, target.getZ(), (int) heard[1] * 2, 0.3, 0.2, 0.3, 1.0);
				if (heard[1] == 2) {
					world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 0.5f);
				}
			}
		} finally {
			bonusHit = false;
		}
		Vec3d away = target.getPos().subtract(from).multiply(1, 0, 1);
		if (away.lengthSquared() > 1.0E-4 && knockback > 0.0) {
			away = away.normalize().multiply(knockback);
			target.addVelocity(away.x, 0.15, away.z);
			target.velocityModified = true;
		}
	}

	/** Kegel aus einer Quelle: trifft alles im Kegel bis {@code range}. */
	private static void cone(ServerWorld world, ServerPlayerEntity owner, Vec3d from, Vec3d dir, double range, double dot, float damage, double knockback) {
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new net.minecraft.util.math.Box(from, from).expand(range),
				e -> e.isAlive() && e != owner && !(e instanceof EchoCloneEntity))) {
			Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(from);
			if (to.length() <= range && to.normalize().dotProduct(dir) >= dot) {
				soundHit(world, owner, target, damage, from, knockback);
			}
		}
		screamFx(world, from, dir, range);
	}

	private static void screamFx(ServerWorld world, Vec3d from, Vec3d dir, double range) {
		for (int i = 1; i <= (int) range; i++) {
			Vec3d p = from.add(dir.multiply(i));
			world.spawnParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		// Schallschild: Geschosse prallen an der Schallwand ab
		if (target instanceof ServerPlayerEntity player && SHIELD.containsKey(player.getUuid()) && source.getSource() instanceof ProjectileEntity projectile) {
			Vec3d v = projectile.getVelocity();
			projectile.setVelocity(v.multiply(-0.8));
			projectile.velocityModified = true;
			player.getServerWorld().spawnParticles(ParticleTypes.NOTE, projectile.getX(), projectile.getY(), projectile.getZ(), 3, 0.1, 0.1, 0.1, 1.0);
			return false;
		}
		return true;
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Schallschrei: Kegel von Echo Echo und jedem Klon auf den Zielpunkt — Kreuzfeuer mit Resonanz. */
	private static boolean sonicScream(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		boolean ultimate = com.santiq.kingdomomnitrix.alien.Evolution.isUltimate(player);
		double range = ctx.param("range", 10.0) * (ultimate ? 1.5 : 1.0);
		Vec3d aim = aimPoint(world, player, range);
		for (Vec3d[] source : sources(player, aim)) {
			cone(world, player, source[0], source[1], range, 0.8, (float) ctx.param("damage", 4.0) * (ultimate ? 1.5f : 1.0f), ctx.param("knockback", 0.6));
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 2.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PARROT_IMITATE_WARDEN, 1.0f, 1.6f);
		return true;
	}

	/** Echo-Teilung: neue Klone neben Echo Echo (hoechstens {@link #MAX_CLONES} gleichzeitig). */
	private static boolean echoSplit(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int spawned = spawnClones(ctx.world(), player, (int) ctx.param("count", 2), (int) Math.round(ctx.param("seconds", 30.0) * 20.0));
		if (spawned == 0) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.echo_echo_max", maxClones(player)).formatted(Formatting.GRAY), true);
			return false;
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.0f, 1.2f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.0f, 1.6f);
		return true;
	}

	/** Schallstoss: Echo Echo schiesst sich mit einer Druckwelle in Blickrichtung; was hinter ihm steht, fliegt weg. */
	private static boolean sonicBoost(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d dir = player.getRotationVec(1.0f);
		player.setVelocity(dir.multiply(ctx.param("power", 1.5)).add(0, 0.3, 0));
		player.velocityModified = true;
		player.fallDistance = 0.0f;
		cone(world, player, player.getPos().add(0, 0.5, 0), dir.multiply(-1), 4.0, 0.3, (float) ctx.param("damage", 3.0), 1.2);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.8f);
		return true;
	}

	/**
	 * Schallmauer: alle Klone springen in einen Ring um Echo Echo; 4 s lang stossen sie im Takt Schallwellen nach
	 * aussen — jeder Puls jedes Klons zaehlt als eigene Quelle (Resonanz an den Raendern).
	 */
	private static boolean wallOfSound(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		List<EchoCloneEntity> clones = clones(player);
		if (clones.size() < 2) {
			spawnClones(world, player, 2 - clones.size(), CLONE_LIFE);
			clones = clones(player);
		}
		double radius = ctx.param("radius", 4.0);
		for (int i = 0; i < clones.size(); i++) {
			double a = i * MathHelper.TAU / clones.size();
			Vec3d at = player.getPos().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
			clones.get(i).requestTeleport(at.x, at.y, at.z);
			world.spawnParticles(ParticleTypes.NOTE, at.x, at.y + 0.6, at.z, 3, 0.2, 0.2, 0.2, 0.5);
		}
		WALLS.put(player.getUuid(), world.getTime() + (long) (ctx.param("seconds", 4.0) * 20));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BELL_RESONATE, 1.2f, 1.4f);
		return true;
	}

	/** Schallschild: Geschosse prallen ab, kein Rueckstoss; Naechstes in der Naehe wird weggedrueckt. */
	private static boolean soundShield(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) Math.round(ctx.param("seconds", 5.0) * 20.0);
		SHIELD.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 0, false, false));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
		return true;
	}

	/** Echo-Chor: Klone bis zum Maximum, dann schreien alle dreimal im Sekundentakt auf alles im Umkreis. */
	private static boolean echoChorus(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		spawnClones(ctx.world(), player, maxClones(player), CLONE_LIFE);
		CHORUS.put(player.getUuid(), ctx.world().getTime());
		ctx.grantInvulnerability(20);
		BuiltinAbilities.sound(ctx, SoundEvents.EVENT_RAID_HORN.value(), 1.0f, 1.8f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			if (!isEchoEcho(player)) {
				if (SHIELD.containsKey(id) || WALLS.containsKey(id) || CHORUS.containsKey(id) || server.getTicks() % 20 == 0) {
					// Klone verklingen mit der Rueckverwandlung
					if (!clones(player).isEmpty()) {
						dismissClones(player);
					}
					forgetState(id);
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			Long shield = SHIELD.get(id);
			if (shield != null) {
				if (now >= shield) {
					SHIELD.remove(id);
				} else if (now % 4 == 0) {
					for (int i = 0; i < 8; i++) {
						double a = i * MathHelper.TAU / 8 + now * 0.2;
						world.spawnParticles(ParticleTypes.NOTE, player.getX() + Math.cos(a) * 1.2, player.getBodyY(0.5), player.getZ() + Math.sin(a) * 1.2,
								1, 0.0, 0.0, 0.0, 0.0);
					}
				}
			}
			Long wall = WALLS.get(id);
			if (wall != null) {
				if (now >= wall) {
					WALLS.remove(id);
				} else if (now % 10 == 0) {
					for (EchoCloneEntity clone : clones(player)) {
						Vec3d out = clone.getPos().subtract(player.getPos()).multiply(1, 0, 1);
						out = out.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : out.normalize();
						cone(world, player, clone.getPos().add(0, 0.5, 0), out, 4.0, 0.2, 2.5f, 0.9);
					}
					world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0f, 0.6f);
				}
			}
			Long chorus = CHORUS.get(id);
			if (chorus != null) {
				long age = now - chorus;
				if (age >= 60) {
					CHORUS.remove(id);
				} else if (age % 20 == 10) {
					// alle Quellen schreien rundum auf jedes Ziel im Umkreis
					List<LivingEntity> near = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(12.0),
							e -> e.isAlive() && e != player && !(e instanceof EchoCloneEntity) && PartyRules.canHarm(player, e));
					List<Entity> voices = new ArrayList<>(clones(player));
					voices.add(player);
					for (Entity voice : voices) {
						for (LivingEntity target : near) {
							if (voice.distanceTo(target) <= 12.0f) {
								soundHit(world, player, target, 3.0f, voice.getPos(), 0.3);
							}
						}
						world.spawnParticles(ParticleTypes.SONIC_BOOM, voice.getX(), voice.getBodyY(0.6), voice.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
					}
					world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.2f, 1.6f);
				}
			}
		}
		if (!HEARD.isEmpty() && server.getTicks() % 40 == 0) {
			long now = server.getOverworld().getTime();
			HEARD.values().removeIf(h -> now - h[0] > RESONANCE_TICKS * 4L);
		}
	}

	private static void forgetState(UUID id) {
		SHIELD.remove(id);
		WALLS.remove(id);
		CHORUS.remove(id);
	}
}
