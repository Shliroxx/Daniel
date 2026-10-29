package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.vfx.EarthWave;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Rath (Appoplexian). Eigenes System „Ringkampf“ — ohne Aura:
 *
 * <ul>
 *   <li><b>Rivale</b>: Rath fordert einen Gegner heraus. Gegen den Rivalen zaehlt jeder Treffer zur Griff-Kette; jeder
 *       dritte Treffer ist ein Wurf (Suplex: hochreissen, hinter Rath auf den Boden schmettern). Vom Rivalen nimmt Rath
 *       30 % weniger Schaden.</li>
 *   <li><b>Unbeugsam</b>: je weniger Leben, desto haerter — bis +60 % Schaden bei 20 % Leben.</li>
 *   <li>Handgelenk-Krallen lassen bluten (Gift-Schaden ueber Zeit).</li>
 * </ul>
 */
final class RathAbilities {
	static final int FURY = 0xFF8A1E;
	static final int DUST = 0x9C8466;
	private static final Identifier RATH = KingdomOmnitrix.id("rath");
	private static final int CHAIN_WINDOW = 60;

	/** Rivale je Rath: Ziel und Ablauf */
	private static final Map<UUID, Rival> RIVALS = new HashMap<>();
	/** Griff-Kette: Zahl und letzter Treffer */
	private static final Map<UUID, long[]> CHAIN = new HashMap<>();
	/** Sturmangriff: Ende und Richtung */
	private static final Map<UUID, Charge> CHARGES = new HashMap<>();
	/** Finale: Phase (Sprung hoch → Sturz), Ziel */
	private static final Map<UUID, Finale> FINALES = new HashMap<>();

	private record Rival(UUID target, long until) {
	}

	/** {@code hit}: wer schon getroffen wurde — jedes Ziel einmal je Sturmangriff (sonst traf er jeden Tick) */
	private record Charge(long until, Vec3d direction, java.util.Set<UUID> hit) {
	}

	private record Finale(UUID target, long slamAt) {
	}

	private RathAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("wrist_claw"), RathAbilities::wristClaw);
		AbilityRegistry.register(KingdomOmnitrix.id("challenge"), RathAbilities::challenge);
		AbilityRegistry.register(KingdomOmnitrix.id("rath_charge"), RathAbilities::rathCharge);
		AbilityRegistry.register(KingdomOmnitrix.id("suplex"), RathAbilities::suplex);
		AbilityRegistry.register(KingdomOmnitrix.id("appoplexian_roar"), RathAbilities::roar);
		AbilityRegistry.register(KingdomOmnitrix.id("rath_finale"), RathAbilities::finale);
		ServerTickEvents.END_SERVER_TICK.register(RathAbilities::tick);
		RathMoves.register();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RathAbilities::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken <= 0.0f || AlienKit.bonusHit || !(source.getAttacker() instanceof ServerPlayerEntity rath) || rath == target
					|| source.getSource() != rath || !isRath(rath)) {
				return;
			}
			ServerWorld world = rath.getServerWorld();
			// Unbeugsam (mehr Schaden bei wenig Leben) laeuft ueber die gleichnamige Passive (RathMoves)
			Rival rival = RIVALS.get(rath.getUuid());
			if (rival != null && rival.target().equals(target.getUuid())) {
				long now = world.getTime();
				long[] chain = CHAIN.computeIfAbsent(rath.getUuid(), id -> new long[2]);
				chain[0] = now - chain[1] <= CHAIN_WINDOW ? chain[0] + 1 : 1;
				chain[1] = now;
				if (chain[0] >= 3) {
					chain[0] = 0;
					throwBehind(world, rath, target, taken);
				} else {
					rath.sendMessage(Text.translatable("message.kingdomomnitrix.rath_chain", chain[0], 3).formatted(Formatting.GOLD), true);
				}
			}
		});
	}

	private static boolean isRath(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(RATH::equals).isPresent();
	}

	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayerEntity rath && isRath(rath) && source.getAttacker() != null) {
			Rival rival = RIVALS.get(rath.getUuid());
			if (rival != null && rival.target().equals(source.getAttacker().getUuid()) && amount > 1.0f && !AlienKit.bonusHit) {
				// gegen den Rivalen haelt Rath mehr aus: 30 % weniger, als eigener Treffer statt des Originals
				AlienKit.hit(rath, source, amount * 0.7f);
				return false;
			}
		}
		return true;
	}

	/** Wurf: hochreissen und hinter Rath auf den Boden schmettern; Nachbarn bekommen den Aufprall ab. */
	static void throwBehind(ServerWorld world, ServerPlayerEntity rath, LivingEntity target, float taken) {
		Vec3d back = rath.getRotationVec(1.0f).multiply(-1, 0, -1).normalize();
		target.setVelocity(back.x * 1.1, 0.9, back.z * 1.1);
		target.velocityModified = true;
		AlienKit.melee(world, rath, target, Math.max(6.0f, taken));
		for (LivingEntity near : AlienKit.around(world, rath, target.getPos(), 3.0)) {
			if (near != target) {
				AlienKit.melee(world, rath, near, 4.0f);
				AlienKit.push(near, target.getPos(), 0.8, 0.4);
			}
		}
		// Aufschlagstelle hinter Rath: der Boden bricht ein
		Vec3d slam = rath.getPos().add(back.multiply(2.2));
		Destruction.crater(world, rath, slam.add(0, -0.6, 0), 1.8, 0.5, AlienStrength.of(rath), 0.0, 0.35);
		EarthWave.ring(world, slam, 3.0, 4, 0.5f);
		Fx.shockwave(world, slam.add(0, 0.1, 0), DUST, 3.5f, 8);
		Fx.smoke(world, slam, DUST, 1.0f, 4);
		Fx.shake(world, slam, 0.35f, 8, 16);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.4f, 0.6f);
		rath.sendMessage(Text.translatable("message.kingdomomnitrix.rath_suplex").formatted(Formatting.GOLD, Formatting.BOLD), true);
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Handgelenk-Kralle: schneller Stich nach vorn, Blutung (Gift) — gegen den Rivalen doppelt. */
	private static boolean wristClaw(AbilityContext ctx) {
		ServerPlayerEntity rath = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(rath, ctx.param("range", 4.5), 0.75, e -> AlienKit.foe(rath, e));
		if (found.isEmpty()) {
			rath.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		Rival rival = RIVALS.get(rath.getUuid());
		boolean isRival = rival != null && rival.target().equals(target.getUuid());
		AlienKit.melee(world, rath, target, (float) ctx.param("damage", 7.0) * (isRival ? 2.0f : 1.0f));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, isRival ? 1 : 0), rath);
		Vec3d body = new Vec3d(target.getX(), target.getBodyY(0.6), target.getZ());
		Vec3d side = new Vec3d(-rath.getRotationVec(1.0f).z, 0, rath.getRotationVec(1.0f).x).multiply(0.7);
		Fx.beam(world, body.add(side).add(0, 0.4, 0), body.subtract(side).add(0, -0.4, 0), FURY, 0.25f, 5);
		Fx.sparks(world, body, FURY, 0.4f, 10);
		Fx.flash(world, body, FURY, 1.2f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
		return true;
	}

	/** Herausforderung: der anvisierte Gegner wird Rivale (15 s); alle Mobs ringsum greifen Rath an. */
	private static boolean challenge(AbilityContext ctx) {
		ServerPlayerEntity rath = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(rath, ctx.param("range", 16.0), 0.85, e -> AlienKit.foe(rath, e));
		if (found.isEmpty()) {
			rath.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		RIVALS.put(rath.getUuid(), new Rival(target.getUuid(), world.getTime() + (long) (ctx.param("seconds", 15.0) * 20)));
		CHAIN.remove(rath.getUuid());
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, (int) (ctx.param("seconds", 15.0) * 20), 0), rath);
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, rath.getBoundingBox().expand(12.0), m -> AlienKit.foe(rath, m))) {
			mob.setTarget(rath);
		}
		Fx.aura(world, rath, FURY, 1.3f, 40);
		Fx.flash(world, new Vec3d(rath.getX(), rath.getBodyY(0.8), rath.getZ()), FURY, 2.0f);
		Fx.beam(world, rath.getEyePos(), new Vec3d(target.getX(), target.getBodyY(0.7), target.getZ()), FURY, 0.15f, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 1.3f);
		rath.sendMessage(Text.translatable("message.kingdomomnitrix.rath_challenge", target.getName()).formatted(Formatting.GOLD), true);
		return true;
	}

	/** Sturmangriff: Kopf voran nach vorn; wer getroffen wird, fliegt und taumelt. */
	private static boolean rathCharge(AbilityContext ctx) {
		ServerPlayerEntity rath = ctx.player();
		CHARGES.put(rath.getUuid(), new Charge(ctx.world().getTime() + (long) ctx.param("ticks", 14), BuiltinAbilities.horizontalLook(rath),
				new java.util.HashSet<>()));
		ctx.grantInvulnerability(8);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_STEP, 1.4f, 0.8f);
		return true;
	}

	/** Suplex: greift das Ziel vor Rath und wirft es sofort (wie der Kettenwurf), auch ohne Rivalen. */
	private static boolean suplex(AbilityContext ctx) {
		ServerPlayerEntity rath = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(rath, ctx.param("range", 4.0), 0.7, e -> AlienKit.foe(rath, e));
		if (found.isEmpty()) {
			rath.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		throwBehind(ctx.world(), rath, found.get(), (float) ctx.param("damage", 10.0));
		return true;
	}

	/** Appoplexian-Gebruell: kein Rueckstoss, Gegner ringsum geschwaecht, Rath kurz staerker. */
	private static boolean roar(AbilityContext ctx) {
		ServerPlayerEntity rath = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		rath.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 1, false, false));
		rath.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 0, false, false));
		for (LivingEntity target : AlienKit.around(world, rath, rath.getPos(), ctx.param("radius", 8.0))) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1), rath);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), rath);
		}
		double radius = ctx.param("radius", 8.0);
		for (int i = 0; i < 3; i++) {
			Fx.shockwave(world, rath.getPos().add(0, 0.8 + i * 0.5, 0), FURY, (float) (radius * (0.5 + i * 0.25)), 8 + i * 3);
		}
		Fx.shake(world, rath.getPos(), 0.5f, 14, 24);
		// Gebruell: Glas und Laub ringsum zerspringen
		Destruction.crater(world, rath, rath.getPos().add(0, 1.0, 0), radius * 0.75, 0.6f, 0.0, 0.1);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.5f, 0.8f);
		return true;
	}

	/** Rath-Finale: springt mit dem Ziel hoch und rammt es kopfueber in den Boden (Aufprall ringsum). */
	private static boolean finale(AbilityContext ctx) {
		ServerPlayerEntity rath = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(rath, ctx.param("range", 5.0), 0.7, e -> AlienKit.foe(rath, e));
		if (found.isEmpty()) {
			rath.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		rath.setVelocity(0, 1.2, 0);
		rath.velocityModified = true;
		target.setVelocity(0, 1.25, 0);
		target.velocityModified = true;
		ctx.grantInvulnerability(40);
		FINALES.put(rath.getUuid(), new Finale(target.getUuid(), world.getTime() + 14));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.5f, 1.2f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity rath : server.getPlayerManager().getPlayerList()) {
			UUID id = rath.getUuid();
			if (!isRath(rath)) {
				forget(id);
				continue;
			}
			ServerWorld world = rath.getServerWorld();
			long now = world.getTime();
			Rival rival = RIVALS.get(id);
			if (rival != null && now >= rival.until()) {
				RIVALS.remove(id);
			}
			Charge charge = CHARGES.get(id);
			if (charge != null) {
				if (now >= charge.until()) {
					CHARGES.remove(id);
				} else {
					rath.setVelocity(charge.direction().x * 0.9, rath.getVelocity().y, charge.direction().z * 0.9);
					rath.velocityModified = true;
					// Kopf voran durch Waende
					Destruction.carve(world, rath, rath.getPos().add(charge.direction().multiply(0.8)).add(0, 1.0, 0), charge.direction(), 1.2, 1.0,
							AlienStrength.of(rath), 0.0);
					if (now % 2 == 0) {
						Fx.smoke(world, rath.getPos(), DUST, 0.7f, 1);
						Fx.sparks(world, rath.getPos().add(charge.direction().multiply(0.8)).add(0, 1.0, 0), FURY, 0.3f, 3);
					}
					for (LivingEntity target : AlienKit.around(world, rath, rath.getPos().add(0, 1, 0), 1.6)) {
						if (!charge.hit().add(target.getUuid())) {
							continue;
						}
						AlienKit.melee(world, rath, target, 6.0f);
						AlienKit.push(target, rath.getPos(), 1.3, 0.5);
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0), rath);
					}
				}
			}
			Finale finale = FINALES.get(id);
			if (finale != null && now >= finale.slamAt()) {
				FINALES.remove(id);
				if (world.getEntity(finale.target()) instanceof LivingEntity target && target.isAlive()) {
					target.setVelocity(0, -2.4, 0);
					target.velocityModified = true;
					AlienKit.melee(world, rath, target, 18.0f);
					for (LivingEntity near : AlienKit.around(world, rath, target.getPos(), 4.0)) {
						if (near != target) {
							AlienKit.melee(world, rath, near, 6.0f);
							AlienKit.push(near, target.getPos(), 1.0, 0.5);
						}
					}
					Destruction.crater(world, rath, target.getPos().add(0, -0.8, 0), 2.6, 0.55, AlienStrength.of(rath) * 1.3f, 0.4, 0.5);
					EarthWave.ring(world, target.getPos(), 5.0, 5, 0.8f);
					Fx.explosion(world, target.getPos().add(0, 0.4, 0), FURY, 2.6f);
					Fx.shockwave(world, target.getPos().add(0, 0.1, 0), DUST, 6.0f, 10);
					Fx.shake(world, target.getPos(), 0.7f, 12, 30);
					world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 0.7f);
				}
				rath.setVelocity(0, -1.5, 0);
				rath.velocityModified = true;
				rath.fallDistance = 0.0f;
			}
		}
	}

	private static void forget(UUID id) {
		RIVALS.remove(id);
		CHAIN.remove(id);
		CHARGES.remove(id);
		FINALES.remove(id);
	}
}
