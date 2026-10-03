package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Ghostfreak (Ectonurit). Eigenes Dauer-System „Spuk“ (0–100) und Angst als Waffe:
 *
 * <ul>
 *   <li>Gegner sammeln <b>Angst</b> (0–{@link #MAX_TERROR}). Bei voller Angst geraten sie in <b>Panik</b>: sie fliehen
 *       vor Ghostfreak, greifen nicht an und nehmen von ihm +30 % Schaden.</li>
 *   <li>Spuk steigt mit jeder gesaeten Angst, jeder Panik und mit Treffern; nach 6 s ohne Nachschub sinkt er.</li>
 *   <li>Ab {@link #EERIE} <b>unheimlich</b>: jeder Nahkampftreffer saet Angst, der Tentakelhieb packt bis zu drei Ziele.</li>
 *   <li>Bei {@link #MAX} <b>Seelenernte bereit</b>: Gegner um Ghostfreak bekommen von selbst Angst; der naechste
 *       Albtraum wird zur Seelenernte (Panik-Opfer zehren, jeder Tod heilt Ghostfreak).</li>
 *   <li><b>Hinterhalt</b>: nach Phasenform oder Schattenschritt macht der naechste Nahkampftreffer doppelten Schaden.</li>
 * </ul>
 * Besessenheit: Ghostfreak faehrt in einen Mob, steuert ihn gegen andere Feinde und zehrt ihn aus; danach bricht er
 * heraus. Bosse und Spieler werden stattdessen gelaehmt und angehoben.
 * Anzeige ueber {@link AlienMeterPayload#DREAD} (violette Aura).
 */
final class GhostfreakAbilities {
	static final float MAX = 100.0f;
	static final float EERIE = 50.0f;
	static final int MAX_TERROR = 3;
	private static final Identifier GHOSTFREAK = KingdomOmnitrix.id("ghostfreak");
	private static final float DREAD_PER_TERROR = 3.0f;
	private static final float DREAD_PANIC = 6.0f;
	private static final float DREAD_DEALT = 0.5f;
	private static final int CALM_TICKS = 120;
	private static final float DECAY = 3.0f;
	private static final int TERROR_TICKS = 200;
	private static final int PANIC_TICKS = 80;
	private static final float PANIC_BONUS = 0.3f;
	private static final int AMBUSH_TICKS = 60;
	private static final DustParticleEffect ECTO = new DustParticleEffect(new Vector3f(0.56f, 0.36f, 0.85f), 1.2f);
	private static final DustParticleEffect PALE = new DustParticleEffect(new Vector3f(0.92f, 0.9f, 1.0f), 1.5f);

	private static final Map<UUID, Float> DREAD = new HashMap<>();
	private static final Map<UUID, Long> LAST_GAIN = new HashMap<>();
	/** Angst im Gegner: Stufe und Ablauf */
	private static final Map<UUID, Terror> TERROR = new HashMap<>();
	/** Gegner in Panik: vor wem und bis wann */
	private static final Map<UUID, Panic> PANIC = new HashMap<>();
	/** Hinterhalt bereit bis */
	private static final Map<UUID, Long> AMBUSH = new HashMap<>();
	/** Phasenform bis */
	private static final Map<UUID, Long> PHASE = new HashMap<>();
	/** laufende Besessenheit je Ghostfreak */
	private static final Map<UUID, Possession> POSSESSIONS = new HashMap<>();
	/** laufender Albtraum je Ghostfreak */
	private static final Map<UUID, Nightmare> NIGHTMARES = new HashMap<>();
	/** verhindert, dass Bonus-Schaden erneut Bonus-Schaden ausloest */
	private static boolean bonusHit;

	private static final class Terror {
		int level;
		long until;
	}

	private record Panic(UUID owner, RegistryKey<World> world, long until) {
	}

	private record Possession(UUID mob, RegistryKey<World> world, long until) {
	}

	private record Nightmare(long until, double radius, boolean harvest) {
	}

	private GhostfreakAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("tentacle_lash"), GhostfreakAbilities::tentacleLash);
		AbilityRegistry.register(KingdomOmnitrix.id("phase_shift"), GhostfreakAbilities::phaseShift);
		AbilityRegistry.register(KingdomOmnitrix.id("haunting_scare"), GhostfreakAbilities::hauntingScare);
		AbilityRegistry.register(KingdomOmnitrix.id("possession"), GhostfreakAbilities::possession);
		AbilityRegistry.register(KingdomOmnitrix.id("shadow_step"), GhostfreakAbilities::shadowStep);
		AbilityRegistry.register(KingdomOmnitrix.id("nightmare"), GhostfreakAbilities::nightmare);
		ServerTickEvents.END_SERVER_TICK.register(GhostfreakAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			endPossession(handler.getPlayer(), false);
			forgetState(id);
			PANIC.values().removeIf(p -> p.owner().equals(id));
			AlienMeterSync.forget(id);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GhostfreakAbilities::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken <= 0.0f || bonusHit || !(source.getAttacker() instanceof ServerPlayerEntity attacker) || attacker == target
					|| !isGhostfreak(attacker)) {
				return;
			}
			addDread(attacker, taken * DREAD_DEALT);
			onHit(attacker, target, taken, source.isOf(DamageTypes.PLAYER_ATTACK) && source.getSource() == attacker);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			TERROR.remove(entity.getUuid());
			PANIC.remove(entity.getUuid());
			if (source.getAttacker() instanceof ServerPlayerEntity killer && isGhostfreak(killer)) {
				Nightmare nightmare = NIGHTMARES.get(killer.getUuid());
				if (nightmare != null && nightmare.harvest()) {
					killer.heal(4.0f);
					ServerWorld world = killer.getServerWorld();
					drawLine(world, entity.getPos().add(0, entity.getHeight() * 0.5, 0), killer.getPos().add(0, 1.0, 0), PALE, 2.0);
					world.playSound(null, killer.getBlockPos(), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.PLAYERS, 1.5f, 0.8f);
				}
			}
		});
	}

	// --- Spuk ------------------------------------------------------------------------------------

	private static boolean isGhostfreak(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(GHOSTFREAK::equals).isPresent();
	}

	static float dread(ServerPlayerEntity player) {
		return DREAD.getOrDefault(player.getUuid(), 0.0f);
	}

	private static void addDread(ServerPlayerEntity player, float amount) {
		float before = dread(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		DREAD.put(player.getUuid(), after);
		ServerWorld world = player.getServerWorld();
		if (amount > 0.0f) {
			LAST_GAIN.put(player.getUuid(), world.getTime());
		}
		if (before < MAX && after >= MAX) {
			world.spawnParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getBodyY(0.6), player.getZ(), 30, 0.6, 0.8, 0.6, 0.03);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.6f, 0.6f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.ghostfreak_harvest").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
		}
		AlienMeterSync.update(player, AlienMeterPayload.DREAD, after);
	}

	// --- Angst und Panik -------------------------------------------------------------------------

	static int terror(LivingEntity target) {
		Terror t = TERROR.get(target.getUuid());
		return t == null ? 0 : t.level;
	}

	private static boolean panicking(LivingEntity target) {
		return PANIC.containsKey(target.getUuid());
	}

	/** Angst saeen; bei voller Angst Panik. */
	private static void frighten(ServerPlayerEntity player, LivingEntity target, int amount) {
		if (amount <= 0 || !target.isAlive()) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Terror t = TERROR.computeIfAbsent(target.getUuid(), id -> new Terror());
		int before = t.level;
		t.level = Math.min(MAX_TERROR, t.level + amount);
		t.until = now + TERROR_TICKS;
		addDread(player, (t.level - before) * DREAD_PER_TERROR);
		world.spawnParticles(ParticleTypes.SOUL, target.getX(), target.getBodyY(1.0) + 0.3, target.getZ(), 2 + 2 * t.level, 0.25, 0.15, 0.25, 0.01);
		if (t.level >= MAX_TERROR && !panicking(target)) {
			panic(player, target, PANIC_TICKS);
		}
	}

	private static void panic(ServerPlayerEntity player, LivingEntity target, int ticks) {
		ServerWorld world = player.getServerWorld();
		boolean fresh = !panicking(target);
		PANIC.put(target.getUuid(), new Panic(player.getUuid(), world.getRegistryKey(), world.getTime() + ticks));
		if (target instanceof MobEntity mob) {
			mob.setTarget(null);
		} else {
			// Spieler und Sonstige: weichen zurueck, zittern
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, ticks, 0), player);
		}
		if (fresh) {
			addDread(player, DREAD_PANIC);
			world.spawnParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getBodyY(0.6), target.getZ(), 14, 0.3, 0.5, 0.3, 0.03);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_VEX_HURT, SoundCategory.HOSTILE, 1.0f, 0.5f);
		}
	}

	// --- Treffer ---------------------------------------------------------------------------------

	private static void onHit(ServerPlayerEntity player, LivingEntity target, float taken, boolean melee) {
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		float bonus = 0.0f;
		if (panicking(target)) {
			bonus += taken * PANIC_BONUS;
		}
		if (melee && AMBUSH.getOrDefault(player.getUuid(), 0L) > now) {
			AMBUSH.remove(player.getUuid());
			bonus += taken;
			frighten(player, target, 2);
			world.spawnParticles(PALE, target.getX(), target.getBodyY(0.6), target.getZ(), 20, 0.3, 0.4, 0.3, 0.0);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PHANTOM_BITE, SoundCategory.PLAYERS, 1.0f, 0.6f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.ghostfreak_ambush").formatted(Formatting.LIGHT_PURPLE), true);
		} else if (melee && dread(player) >= EERIE) {
			frighten(player, target, 1);
		}
		if (bonus > 0.0f && target.isAlive()) {
			bonusHit = true;
			try {
				target.timeUntilRegen = 0;
				target.damage(world.getDamageSources().indirectMagic(player, player), bonus);
			} finally {
				bonusHit = false;
			}
		}
	}

	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		// Panik: wer flieht, greift nicht an
		Entity attacker = source.getAttacker();
		if (attacker instanceof LivingEntity living && target instanceof ServerPlayerEntity player) {
			Panic panic = PANIC.get(living.getUuid());
			if (panic != null && panic.owner().equals(player.getUuid())) {
				return false;
			}
			// besessene Mobs greifen ihren Ghostfreak nicht an
			Possession possession = POSSESSIONS.get(player.getUuid());
			if (possession != null && possession.mob().equals(living.getUuid())) {
				return false;
			}
		}
		return true;
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Tentakelhieb: zieht heran, entzieht Leben, saet Angst; unheimlich packt er bis zu drei Ziele. */
	private static boolean tentacleLash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 10.0);
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, range, 0.85, e -> PartyRules.canHarm(player, e) && !possessedBy(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		List<LivingEntity> targets = new java.util.ArrayList<>();
		targets.add(found.get());
		if (dread(player) >= EERIE) {
			Vec3d look = player.getRotationVec(1.0f);
			world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(range),
							e -> e != player && e != found.get() && e.isAlive() && PartyRules.canHarm(player, e) && !possessedBy(player, e)
									&& e.getPos().subtract(player.getPos()).normalize().dotProduct(look) > 0.6 && e.distanceTo(player) <= range)
					.stream().sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(player))).limit(2).forEach(targets::add);
		}
		float damage = (float) ctx.param("damage", 6.0);
		for (LivingEntity target : targets) {
			Vec3d pull = player.getPos().subtract(target.getPos());
			if (pull.lengthSquared() > 1.0E-4) {
				pull = pull.normalize().multiply(ctx.param("pull", 1.2));
				target.setVelocity(pull.x, 0.3, pull.z);
				target.velocityModified = true;
			}
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().indirectMagic(player, player), damage);
			frighten(player, target, 1);
			player.heal((float) ctx.param("heal", 2.0));
			drawLine(world, player.getPos().add(0.0, player.getHeight() * 0.6, 0.0), target.getPos().add(0.0, target.getHeight() * 0.5, 0.0), ECTO, 3.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_TENDRIL_CLICKS, 1.0f, 1.0f);
		return true;
	}

	/** Phasenform: unverwundbar, unsichtbar, Gegner verlieren das Ziel; wer durchquert wird, bekommt Angst. Danach Hinterhalt. */
	private static boolean phaseShift(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) ctx.param("ticks", 60);
		ctx.grantInvulnerability(ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, ticks, 0, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 1, false, false));
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(24.0), m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
		PHASE.put(player.getUuid(), world.getTime() + ticks);
		world.spawnParticles(ParticleTypes.SOUL, player.getX(), player.getBodyY(0.5), player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_VEX_CHARGE, 1.0f, 0.6f);
		return true;
	}

	/** Spukschrei: Angst +2 im Umkreis (volle Angst = Panik), Gegner weggestossen und kurz blind. */
	private static boolean hauntingScare(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 8.0) * (1.0 + dread(player) / 200.0);
		int ticks = (int) Math.round(ctx.param("seconds", 6.0) * 20.0);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			if (possessedBy(player, target)) {
				continue;
			}
			Vec3d away = target.getPos().subtract(player.getPos());
			if (away.horizontalLengthSquared() > 1.0E-4) {
				target.takeKnockback(ctx.param("push", 1.2), -away.x, -away.z);
			}
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 0), player);
			frighten(player, target, 2);
		}
		for (int i = 0; i < 24; i++) {
			double angle = i * Math.PI * 2.0 / 24.0;
			world.spawnParticles(ECTO, player.getX() + Math.cos(angle) * radius * 0.6, player.getBodyY(0.5), player.getZ() + Math.sin(angle) * radius * 0.6,
					2, 0.2, 0.3, 0.2, 0.0);
		}
		world.spawnParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getBodyY(0.5), player.getZ(), 40, radius * 0.3, 0.6, radius * 0.3, 0.02);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GHAST_SCREAM, 0.9f, 0.7f);
		return true;
	}

	/**
	 * Besessenheit: Ghostfreak faehrt (unsichtbar, unverwundbar) in einen Mob, der dann gegen andere Feinde kaempft und
	 * dabei ausgezehrt wird; danach bricht Ghostfreak heraus (Schaden, Angst ringsum). Schleichen beendet sie frueh.
	 * Bosse und Spieler: gelaehmt, geschwaecht, angehoben.
	 */
	private static boolean possession(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		if (POSSESSIONS.containsKey(player.getUuid())) {
			return false;
		}
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 6.0), 0.8, e -> PartyRules.canHarm(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		int ticks = (int) Math.round(ctx.param("seconds", 4.0) * 20.0 * (1.0 + terror(target) / 3.0));
		drawLine(world, player.getPos().add(0, 1.0, 0), target.getPos().add(0, target.getHeight() * 0.5, 0), PALE, 3.0);
		if (target instanceof MobEntity mob && !(target instanceof PlayerEntity) && !target.getType().isIn(ConventionalEntityTypeTags.BOSSES)
				&& !mob.hasPassengers() && player.startRiding(mob, true)) {
			POSSESSIONS.put(player.getUuid(), new Possession(mob.getUuid(), world.getRegistryKey(), world.getTime() + ticks));
			ctx.grantInvulnerability(ticks + 10);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, ticks + 10, 0, false, false));
			mob.setTarget(null);
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 1, false, false));
			TERROR.remove(mob.getUuid());
			PANIC.remove(mob.getUuid());
			player.sendMessage(Text.translatable("message.kingdomomnitrix.ghostfreak_possess", mob.getName()).formatted(Formatting.LIGHT_PURPLE), true);
		} else {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 9), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, Math.min(ticks, 40), 0), player);
			frighten(player, target, 2);
		}
		world.spawnParticles(ParticleTypes.SOUL, target.getX(), target.getBodyY(0.5), target.getZ(), 30, 0.3, 0.6, 0.3, 0.03);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_VEX_AMBIENT, 1.2f, 0.5f);
		return true;
	}

	/**
	 * Schattenschritt: blickt Ghostfreak auf einen Gegner, taucht er hinter ihm auf (Blick zum Ziel, Angst +1), sonst
	 * bis {@code distance} Bloecke nach vorn. Danach Hinterhalt.
	 */
	private static boolean shadowStep(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double distance = ctx.param("distance", 10.0);
		Optional<LivingEntity> victim = Targeting.findMeleeTarget(player, distance, 0.92, e -> PartyRules.canHarm(player, e) && !possessedBy(player, e));
		if (victim.isPresent()) {
			LivingEntity target = victim.get();
			Vec3d back = target.getRotationVec(1.0f).multiply(1, 0, 1);
			back = back.lengthSquared() < 1.0E-4 ? target.getPos().subtract(player.getPos()).multiply(1, 0, 1) : back;
			Vec3d behind = target.getPos().subtract(back.normalize().multiply(target.getWidth() * 0.5 + 0.9));
			if (world.isSpaceEmpty(player, player.getBoundingBox().offset(behind.subtract(player.getPos())))) {
				Vec3d face = target.getPos().subtract(behind);
				float yaw = (float) (MathHelper.atan2(face.z, face.x) * (180.0 / Math.PI)) - 90.0f;
				step(ctx, player, world, behind, yaw);
				frighten(player, target, 1);
				return true;
			}
		}
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		HitResult wall = world.raycast(new RaycastContext(eye, eye.add(look.multiply(distance)), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? distance : Math.max(0.0, eye.distanceTo(wall.getPos()) - 0.8);
		for (double d = reach; d >= 1.0; d -= 0.5) {
			Vec3d feet = eye.add(look.multiply(d)).subtract(0.0, player.getStandingEyeHeight(), 0.0);
			if (world.isSpaceEmpty(player, player.getBoundingBox().offset(feet.subtract(player.getPos())))) {
				step(ctx, player, world, new Vec3d(feet.x, Math.max(feet.y, BlockPos.ofFloored(feet).getY()), feet.z), player.getYaw());
				return true;
			}
		}
		player.sendMessage(Text.translatable("message.kingdomomnitrix.no_space_step").formatted(Formatting.GRAY), true);
		return false;
	}

	private static void step(AbilityContext ctx, ServerPlayerEntity player, ServerWorld world, Vec3d to, float yaw) {
		world.spawnParticles(ParticleTypes.SQUID_INK, player.getX(), player.getBodyY(0.5), player.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
		player.teleport(world, to.x, to.y, to.z, yaw, player.getPitch());
		player.fallDistance = 0.0f;
		ctx.grantInvulnerability((int) ctx.param("invulnerable_ticks", 10));
		AMBUSH.put(player.getUuid(), world.getTime() + AMBUSH_TICKS);
		world.spawnParticles(ParticleTypes.SOUL, to.x, to.y + 1.0, to.z, 20, 0.3, 0.5, 0.3, 0.02);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
	}

	/**
	 * Albtraum: alle Gegner im Umkreis (waechst mit Spuk) geraten in Panik, Dunkelheit und Verdorren. Verbraucht den
	 * Spuk; bei vollem Spuk Seelenernte: Panik-Opfer zehren 2 Schaden je Sekunde, jeder Tod heilt Ghostfreak.
	 */
	private static boolean nightmare(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float spent = dread(player);
		boolean harvest = spent >= MAX;
		double radius = ctx.param("radius", 10.0) * (1.0 + spent / 200.0);
		int ticks = (int) Math.round(ctx.param("seconds", 6.0) * 20.0 * (harvest ? 1.5 : 1.0));
		float damage = (float) (ctx.param("damage", 8.0) * (1.0 + spent / 100.0));
		DREAD.put(player.getUuid(), 0.0f);
		AlienMeterSync.update(player, AlienMeterPayload.DREAD, 0.0f);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			if (possessedBy(player, target)) {
				continue;
			}
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().indirectMagic(player, player), damage);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, ticks, 0), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, ticks, 1), player);
			Terror t = TERROR.computeIfAbsent(target.getUuid(), id -> new Terror());
			t.level = MAX_TERROR;
			t.until = world.getTime() + TERROR_TICKS;
			panic(player, target, ticks);
		}
		NIGHTMARES.put(player.getUuid(), new Nightmare(world.getTime() + ticks, radius, harvest));
		world.spawnParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getBodyY(0.5), player.getZ(), 160, radius * 0.4, 1.0, radius * 0.4, 0.02);
		world.spawnParticles(harvest ? PALE : ECTO, player.getX(), player.getBodyY(0.5), player.getZ(), 80, radius * 0.4, 1.2, radius * 0.4, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_ROAR, 1.2f, 0.8f);
		if (harvest) {
			BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WITHER_AMBIENT, 1.0f, 0.6f);
		}
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			if (!isGhostfreak(player)) {
				if (POSSESSIONS.containsKey(id)) {
					endPossession(player, false);
				}
				if (DREAD.containsKey(id) || AMBUSH.containsKey(id) || PHASE.containsKey(id) || NIGHTMARES.containsKey(id)) {
					if (DREAD.containsKey(id)) {
						AlienMeterSync.clear(player, AlienMeterPayload.DREAD);
					}
					forgetState(id);
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			if (now % 20 == 0 && dread(player) > 0.0f && dread(player) < MAX && now - LAST_GAIN.getOrDefault(id, 0L) > CALM_TICKS) {
				addDread(player, -DECAY);
			}
			// Seelenernte bereit: die Naehe Ghostfreaks allein saet Angst
			if (now % 40 == 0 && dread(player) >= MAX) {
				for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(6.0),
						e -> e != player && e.isAlive() && e instanceof Monster && PartyRules.canHarm(player, e) && !possessedBy(player, e))) {
					frighten(player, near, 1);
				}
			}
			tickPhase(world, player, now);
			tickPossession(world, player, now);
			tickNightmare(world, player, now);
		}
		if (server.getTicks() % 10 == 0) {
			tickPanic(server);
		}
		if (!TERROR.isEmpty() && server.getTicks() % 20 == 0) {
			long now = server.getOverworld().getTime();
			TERROR.entrySet().removeIf(e -> e.getValue().until <= now);
		}
	}

	private static void tickPhase(ServerWorld world, ServerPlayerEntity player, long now) {
		Long until = PHASE.get(player.getUuid());
		if (until == null) {
			return;
		}
		if (now >= until) {
			PHASE.remove(player.getUuid());
			AMBUSH.put(player.getUuid(), now + AMBUSH_TICKS);
			world.spawnParticles(PALE, player.getX(), player.getBodyY(0.5), player.getZ(), 16, 0.3, 0.6, 0.3, 0.0);
			return;
		}
		if (now % 2 == 0) {
			world.spawnParticles(ECTO, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.3, 0.5, 0.3, 0.0);
		}
		// wer von der Phasenform durchquert wird, erschauert (einmal je Sekunde)
		if (now % 20 == 0) {
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(0.5),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), player);
				frighten(player, target, 1);
			}
		}
	}

	private static void tickPossession(ServerWorld world, ServerPlayerEntity player, long now) {
		Possession possession = POSSESSIONS.get(player.getUuid());
		if (possession == null) {
			return;
		}
		Entity entity = world.getEntity(possession.mob());
		if (!(entity instanceof MobEntity mob) || !mob.isAlive() || player.getVehicle() != mob || now >= possession.until()) {
			endPossession(player, true);
			return;
		}
		LivingEntity target = mob.getTarget();
		if ((target == null || !target.isAlive() || target == player || !PartyRules.canHarm(player, target)) && now % 5 == 0) {
			mob.setTarget(world.getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(16.0),
							e -> e != mob && e != player && e.isAlive() && e instanceof Monster && PartyRules.canHarm(player, e))
					.stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mob))).orElse(null));
		}
		if (now % 20 == 0) {
			// der Wirt wird ausgezehrt, Ghostfreak naehrt sich davon
			bonusHit = true;
			try {
				mob.timeUntilRegen = 0;
				mob.damage(world.getDamageSources().indirectMagic(player, player), 1.0f);
			} finally {
				bonusHit = false;
			}
			addDread(player, 3.0f);
			player.heal(1.0f);
		}
		if (now % 4 == 0) {
			world.spawnParticles(ECTO, mob.getX(), mob.getBodyY(0.6), mob.getZ(), 3, mob.getWidth() * 0.4, mob.getHeight() * 0.3, mob.getWidth() * 0.4, 0.0);
		}
	}

	/** Besessenheit beenden; {@code burst}: Ghostfreak bricht heraus (Schaden am Wirt, Angst ringsum). */
	private static void endPossession(ServerPlayerEntity player, boolean burst) {
		Possession possession = POSSESSIONS.remove(player.getUuid());
		if (possession == null) {
			return;
		}
		ServerWorld world = player.getServer() == null ? null : player.getServer().getWorld(possession.world());
		Entity entity = world == null ? null : world.getEntity(possession.mob());
		if (player.hasVehicle()) {
			player.stopRiding();
		}
		player.removeStatusEffect(StatusEffects.INVISIBILITY);
		if (world == null) {
			return;
		}
		if (entity instanceof MobEntity mob) {
			mob.setTarget(null);
			mob.removeStatusEffect(StatusEffects.STRENGTH);
		}
		if (burst) {
			ctxlessInvulnerable(player);
			if (entity instanceof LivingEntity host && host.isAlive()) {
				bonusHit = true;
				try {
					host.timeUntilRegen = 0;
					host.damage(world.getDamageSources().indirectMagic(player, player), 4.0f + dread(player) / 10.0f);
				} finally {
					bonusHit = false;
				}
			}
			for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(5.0),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
				frighten(player, near, 2);
			}
			world.spawnParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.6, 0.8, 0.6, 0.05);
			world.spawnParticles(PALE, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.6, 0.8, 0.6, 0.0);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VEX_DEATH, SoundCategory.PLAYERS, 1.2f, 0.5f);
		}
	}

	/** Kurz unverwundbar nach dem Herausbrechen, damit der Wirt nicht sofort zurueckschlaegt. */
	private static void ctxlessInvulnerable(ServerPlayerEntity player) {
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20, 4, false, false));
	}

	private static void tickNightmare(ServerWorld world, ServerPlayerEntity player, long now) {
		Nightmare nightmare = NIGHTMARES.get(player.getUuid());
		if (nightmare == null) {
			return;
		}
		if (now >= nightmare.until()) {
			NIGHTMARES.remove(player.getUuid());
			return;
		}
		if (now % 20 == 0 && nightmare.harvest()) {
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(nightmare.radius() + 6.0),
					e -> e != player && e.isAlive() && panicking(e) && PartyRules.canHarm(player, e))) {
				target.timeUntilRegen = 0;
				target.damage(world.getDamageSources().indirectMagic(player, player), 2.0f);
				drawLine(world, target.getPos().add(0, target.getHeight() * 0.5, 0), player.getPos().add(0, 1.0, 0), ECTO, 1.5);
			}
		}
	}

	/** Panik: Mobs laufen von ihrem Schrecken weg und verlieren jedes Ziel. */
	private static void tickPanic(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Panic>> it = PANIC.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Panic> entry = it.next();
			Panic panic = entry.getValue();
			ServerWorld world = server.getWorld(panic.world());
			Entity entity = world == null ? null : world.getEntity(entry.getKey());
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(panic.owner());
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || owner == null || world.getTime() >= panic.until()) {
				it.remove();
				continue;
			}
			if (living instanceof MobEntity mob) {
				if (mob.getTarget() == owner) {
					mob.setTarget(null);
				}
				if (mob instanceof PathAwareEntity walker) {
					Vec3d away = NoPenaltyTargeting.findFrom(walker, 16, 7, owner.getPos());
					if (away != null) {
						walker.getNavigation().startMovingTo(away.x, away.y, away.z, 1.4);
					}
				}
			}
			world.spawnParticles(ParticleTypes.SOUL, living.getX(), living.getBodyY(1.0) + 0.2, living.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
		}
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	private static boolean possessedBy(ServerPlayerEntity player, LivingEntity entity) {
		Possession possession = POSSESSIONS.get(player.getUuid());
		return possession != null && possession.mob().equals(entity.getUuid());
	}

	private static void drawLine(ServerWorld world, Vec3d from, Vec3d to, DustParticleEffect dust, double density) {
		Vec3d step = to.subtract(from);
		int points = Math.max(4, (int) (step.length() * density));
		for (int i = 1; i <= points; i++) {
			Vec3d p = from.add(step.multiply(i / (double) points));
			world.spawnParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private static void forgetState(UUID id) {
		DREAD.remove(id);
		LAST_GAIN.remove(id);
		AMBUSH.remove(id);
		PHASE.remove(id);
		NIGHTMARES.remove(id);
		POSSESSIONS.remove(id);
	}
}
