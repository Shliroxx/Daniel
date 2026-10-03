package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Atomix (Kryptosapien). Eigenes System „Kernspaltung“ — ohne Aura:
 *
 * <ul>
 *   <li><b>Strahlung</b> im Gegner (0–{@link #MAX_RAD}): jede Sekunde Schaden je Stufe, alle 3 s zerfaellt eine.</li>
 *   <li><b>Spaltung</b>: stirbt ein Ziel mit mindestens 2 Stufen, spaltet sich sein Kern — kleine Explosion, die
 *       naechsten zwei Gegner bekommen +2 Stufen. So laufen Kettenreaktionen durch Gruppen.</li>
 *   <li>Halbwertszeit loest alle Strahlung im Umkreis auf einmal aus; „Hero Time!“ ist die grosse Kernexplosion.</li>
 * </ul>
 * Keine Blockschaeden.
 */
final class AtomixAbilities {
	static final int MAX_RAD = 5;
	private static final Identifier ATOMIX = KingdomOmnitrix.id("atomix");
	private static final int DECAY_TICKS = 60;
	private static final DustParticleEffect GLOW = new DustParticleEffect(new Vector3f(0.25f, 1.0f, 0.4f), 1.4f);
	private static final DustParticleEffect CORE = new DustParticleEffect(new Vector3f(0.85f, 1.0f, 0.3f), 2.0f);

	/** Strahlung je Ziel: Stufe, letzter Zerfall, Verursacher */
	private static final Map<UUID, Rad> RAD = new HashMap<>();
	/** Eindaemmungsfeld: Ziel und Ende je Atomix */
	private static final Map<UUID, Containment> FIELDS = new HashMap<>();
	/** Hero Time: Zuendzeitpunkt je Atomix */
	private static final Map<UUID, Long> HERO_TIME = new HashMap<>();
	/** Kernsprung: Landung pruefen */
	private static final Map<UUID, Long> LEAPS = new HashMap<>();
	/** verhindert, dass Strahlungsschaden erneut etwas ausloest */
	private static boolean bonusHit;

	private static final class Rad {
		int level;
		long lastDecay;
		UUID source;
		RegistryKey<World> world;
	}

	private record Containment(UUID target, long until) {
	}

	private AtomixAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("atomic_bolt"), AtomixAbilities::atomicBolt);
		AbilityRegistry.register(KingdomOmnitrix.id("nuclear_pulse"), AtomixAbilities::nuclearPulse);
		AbilityRegistry.register(KingdomOmnitrix.id("fusion_leap"), AtomixAbilities::fusionLeap);
		AbilityRegistry.register(KingdomOmnitrix.id("containment_field"), AtomixAbilities::containmentField);
		AbilityRegistry.register(KingdomOmnitrix.id("half_life"), AtomixAbilities::halfLife);
		AbilityRegistry.register(KingdomOmnitrix.id("hero_time"), AtomixAbilities::heroTime);
		ServerTickEvents.END_SERVER_TICK.register(AtomixAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			FIELDS.remove(id);
			HERO_TIME.remove(id);
			LEAPS.remove(id);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken > 0.0f && !bonusHit && source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != target
					&& source.getSource() == attacker && isAtomix(attacker)) {
				irradiate(attacker, target, 1);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Rad rad = RAD.remove(entity.getUuid());
			if (rad != null && rad.level >= 2 && entity.getWorld() instanceof ServerWorld world) {
				ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(rad.source);
				if (owner != null) {
					fission(world, owner, entity);
				}
			}
		});
	}

	private static boolean isAtomix(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(ATOMIX::equals).isPresent();
	}

	// --- Strahlung -------------------------------------------------------------------------------

	static int radiation(LivingEntity target) {
		Rad rad = RAD.get(target.getUuid());
		return rad == null ? 0 : rad.level;
	}

	private static void irradiate(ServerPlayerEntity owner, LivingEntity target, int amount) {
		if (amount <= 0 || !target.isAlive() || target == owner || !PartyRules.canHarm(owner, target)) {
			return;
		}
		ServerWorld world = owner.getServerWorld();
		Rad rad = RAD.computeIfAbsent(target.getUuid(), id -> new Rad());
		rad.level = Math.min(MAX_RAD, rad.level + amount);
		rad.lastDecay = world.getTime();
		rad.source = owner.getUuid();
		rad.world = world.getRegistryKey();
		world.spawnParticles(GLOW, target.getX(), target.getBodyY(0.6), target.getZ(), 3 + 2 * rad.level, 0.3, 0.4, 0.3, 0.0);
	}

	private static void hit(ServerWorld world, ServerPlayerEntity owner, LivingEntity target, float damage) {
		bonusHit = true;
		try {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().indirectMagic(owner, owner), damage);
		} finally {
			bonusHit = false;
		}
	}

	/** Spaltung: kleiner Blitz, die zwei naechsten Gegner bekommen +2 Stufen. */
	private static void fission(ServerWorld world, ServerPlayerEntity owner, Entity dead) {
		world.spawnParticles(ParticleTypes.EXPLOSION, dead.getX(), dead.getBodyY(0.5), dead.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
		world.spawnParticles(CORE, dead.getX(), dead.getBodyY(0.5), dead.getZ(), 30, 0.6, 0.6, 0.6, 0.0);
		world.playSound(null, dead.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8f, 1.5f);
		world.getEntitiesByClass(LivingEntity.class, dead.getBoundingBox().expand(6.0),
						e -> e != owner && e != dead && e.isAlive() && PartyRules.canHarm(owner, e))
				.stream().sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(dead))).limit(2)
				.forEach(next -> {
					irradiate(owner, next, 2);
					hit(world, owner, next, 3.0f);
					Vec3d from = dead.getPos().add(0, dead.getHeight() * 0.5, 0);
					Vec3d to = next.getPos().add(0, next.getHeight() * 0.5, 0);
					for (int i = 1; i <= 8; i++) {
						Vec3d p = from.lerp(to, i / 8.0);
						world.spawnParticles(GLOW, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
					}
				});
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Atomstrahl: gruener Energiestrahl auf das erste Ziel, +1 Strahlung (mit vollem Ziel: kleine Kernexplosion). */
	private static boolean atomicBolt(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 24.0);
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		Optional<LivingEntity> found = Targeting.findLivingTarget(player, eye.distanceTo(stop));
		if (found.isPresent() && PartyRules.canHarm(player, found.get())) {
			LivingEntity target = found.get();
			stop = target.getPos().add(0, target.getHeight() * 0.5, 0);
			boolean full = radiation(target) >= MAX_RAD;
			hit(world, player, target, (float) ctx.param("damage", 7.0) * (full ? 1.6f : 1.0f));
			irradiate(player, target, 1);
			if (full) {
				world.spawnParticles(ParticleTypes.EXPLOSION, stop.x, stop.y, stop.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		Vec3d muzzle = eye.add(player.getRotationVec(1.0f)).add(0, -0.25, 0);
		Vec3d step = stop.subtract(muzzle);
		int points = Math.max(6, (int) (step.length() * 3));
		for (int i = 1; i <= points; i++) {
			Vec3d p = muzzle.add(step.multiply(i / (double) points));
			world.spawnParticles(i % 3 == 0 ? CORE : GLOW, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.8f, 2.0f);
		return true;
	}

	/** Kernpuls: Welle um Atomix — +2 Strahlung, Rueckstoss. */
	private static boolean nuclearPulse(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 5.0);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			hit(world, player, target, (float) ctx.param("damage", 5.0));
			irradiate(player, target, 2);
			Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			away = away.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : away.normalize().multiply(1.0);
			target.addVelocity(away.x, 0.35, away.z);
			target.velocityModified = true;
		}
		ring(world, player.getPos().add(0, 1.0, 0), radius, GLOW);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.4f);
		return true;
	}

	/** Kernsprung: hoher Satz nach vorn; die Landung ist ein Strahlungsstoss. */
	private static boolean fusionLeap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		BuiltinAbilities.launch(player, dir.x * ctx.param("forward", 1.3), ctx.param("lift", 1.0), dir.z * ctx.param("forward", 1.3));
		player.fallDistance = 0.0f;
		LEAPS.put(player.getUuid(), ctx.world().getTime());
		ctx.world().spawnParticles(GLOW, player.getX(), player.getY() + 0.2, player.getZ(), 30, 0.5, 0.1, 0.5, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_JUMP, 1.0f, 0.7f);
		return true;
	}

	/** Eindaemmungsfeld: haelt ein Ziel schwebend fest, jede Sekunde +1 Strahlung. */
	private static boolean containmentField(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 14.0), 0.85, e -> PartyRules.canHarm(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		FIELDS.put(player.getUuid(), new Containment(found.get().getUuid(), ctx.world().getTime() + (long) (ctx.param("seconds", 4.0) * 20)));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
		return true;
	}

	/** Halbwertszeit: alle bestrahlten Gegner im Umkreis erleiden ihre ganze Strahlung sofort (3 je Stufe). */
	private static boolean halfLife(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 16.0);
		int hits = 0;
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			int level = radiation(target);
			if (level <= 0) {
				continue;
			}
			RAD.remove(target.getUuid());
			hit(world, player, target, (float) ctx.param("per_level", 3.0) * level);
			world.spawnParticles(CORE, target.getX(), target.getBodyY(0.5), target.getZ(), 10 * level, 0.4, 0.5, 0.4, 0.0);
			hits++;
		}
		if (hits == 0) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.atomix_nothing").formatted(Formatting.GRAY), true);
			return false;
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), 1.2f, 1.4f);
		return true;
	}

	/** Hero Time: Atomix steigt auf, sammelt 1,5 s Energie — dann Kernexplosion (keine Blockschaeden). */
	private static boolean heroTime(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		HERO_TIME.put(player.getUuid(), ctx.world().getTime() + (long) (ctx.param("charge_seconds", 1.5) * 20));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 25, 1, false, false));
		ctx.grantInvulnerability(50);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.atomix_hero_time").formatted(Formatting.GREEN, Formatting.BOLD), true);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.6f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			if (!isAtomix(player)) {
				FIELDS.remove(id);
				HERO_TIME.remove(id);
				LEAPS.remove(id);
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			tickLeap(world, player, now);
			tickField(world, player, now);
			Long boom = HERO_TIME.get(id);
			if (boom != null) {
				if (now >= boom) {
					HERO_TIME.remove(id);
					nuke(world, player);
				} else if (now % 2 == 0) {
					world.spawnParticles(CORE, player.getX(), player.getBodyY(0.5), player.getZ(), 8, 0.6, 0.8, 0.6, 0.0);
				}
			}
		}
		if (!RAD.isEmpty() && server.getTicks() % 20 == 0) {
			tickRadiation(server);
		}
	}

	private static void tickLeap(ServerWorld world, ServerPlayerEntity player, long now) {
		Long start = LEAPS.get(player.getUuid());
		if (start == null || now - start < 5 || !player.isOnGround()) {
			if (start != null && now - start > 100) {
				LEAPS.remove(player.getUuid());
			}
			return;
		}
		LEAPS.remove(player.getUuid());
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(4.0),
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
			hit(world, player, target, 6.0f);
			irradiate(player, target, 2);
		}
		ring(world, player.getPos().add(0, 0.3, 0), 4.0, CORE);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 1.3f);
	}

	private static void tickField(ServerWorld world, ServerPlayerEntity player, long now) {
		Containment field = FIELDS.get(player.getUuid());
		if (field == null) {
			return;
		}
		Entity entity = world.getEntity(field.target());
		if (!(entity instanceof LivingEntity target) || !target.isAlive() || now >= field.until()) {
			FIELDS.remove(player.getUuid());
			return;
		}
		target.setVelocity(0.0, 0.04, 0.0);
		target.velocityModified = true;
		target.fallDistance = 0.0f;
		if (now % 20 == 0) {
			irradiate(player, target, 1);
		}
		if (now % 3 == 0) {
			double a = now * 0.4;
			world.spawnParticles(GLOW, target.getX() + Math.cos(a) * 0.9, target.getBodyY(0.5), target.getZ() + Math.sin(a) * 0.9, 2, 0.0, 0.3, 0.0, 0.0);
		}
	}

	/** Die grosse Kernexplosion: Schaden nach Abstand, volle Strahlung, Rueckstoss — ohne Blockschaden. */
	private static void nuke(ServerWorld world, ServerPlayerEntity player) {
		double radius = 12.0;
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && e.distanceTo(player) <= radius)) {
			float falloff = 1.0f - target.distanceTo(player) / (float) radius * 0.6f;
			hit(world, player, target, 18.0f * falloff);
			irradiate(player, target, MAX_RAD);
			Vec3d away = target.getPos().subtract(player.getPos()).normalize().multiply(1.6 * falloff);
			target.addVelocity(away.x, 0.6, away.z);
			target.velocityModified = true;
		}
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getBodyY(0.5), player.getZ(), 3, 1.0, 1.0, 1.0, 0.0);
		world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		for (int r = 1; r <= 4; r++) {
			ring(world, player.getPos().add(0, 0.5, 0), radius * r / 4.0, r % 2 == 0 ? CORE : GLOW);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.0f, 0.5f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 0.6f);
	}

	private record Pending(ServerWorld world, ServerPlayerEntity owner, LivingEntity target, float damage) {
	}

	private static void tickRadiation(MinecraftServer server) {
		// erst sammeln, dann schaden: ein Tod (Spaltung) veraendert die Strahlungstabelle
		java.util.List<Pending> pending = new java.util.ArrayList<>();
		for (Iterator<Map.Entry<UUID, Rad>> it = RAD.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Rad> entry = it.next();
			Rad rad = entry.getValue();
			ServerWorld world = server.getWorld(rad.world);
			Entity entity = world == null ? null : world.getEntity(entry.getKey());
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(rad.source);
			if (!(entity instanceof LivingEntity target) || !target.isAlive() || owner == null) {
				it.remove();
				continue;
			}
			pending.add(new Pending(world, owner, target, rad.level));
			world.spawnParticles(GLOW, target.getX(), target.getBodyY(0.6), target.getZ(), 2 + rad.level, 0.3, 0.4, 0.3, 0.0);
			if (world.getTime() - rad.lastDecay >= DECAY_TICKS) {
				rad.level--;
				rad.lastDecay = world.getTime();
				if (rad.level <= 0) {
					it.remove();
				}
			}
		}
		for (Pending p : pending) {
			if (p.target().isAlive()) {
				hit(p.world(), p.owner(), p.target(), p.damage());
			}
		}
	}

	private static void ring(ServerWorld world, Vec3d center, double radius, DustParticleEffect dust) {
		int points = Math.max(12, (int) (radius * 8));
		for (int i = 0; i < points; i++) {
			double a = i * MathHelper.TAU / points;
			world.spawnParticles(dust, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0.0, 0.05, 0.0, 0.0);
		}
	}
}
