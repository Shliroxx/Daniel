package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.vfx.EarthWave;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Rath, Kraefte 2.0: Krallenwirbel, Waescheleine, Piledriver, Ellbogensturz, Grossmaul, Koenigliche Keilerei; Kombos
 * Speer-Tackle, Fliegender Ellbogen, Schwitzkasten, Bauchplatscher, Rath-Bombe; Passive Unbeugsam (Kern-Bonus),
 * Appoplexisches Temperament und Ehrenrunde. Wuerfe nutzen {@link RathAbilities#throwBehind}.
 */
final class RathMoves {
	private static final int FURY = RathAbilities.FURY;
	private static final int DUST = RathAbilities.DUST;

	private RathMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("claw_flurry"), RathMoves::clawFlurry);
		AbilityRegistry.register(KingdomOmnitrix.id("clothesline"), RathMoves::clothesline);
		AbilityRegistry.register(KingdomOmnitrix.id("piledriver"), RathMoves::piledriver);
		AbilityRegistry.register(KingdomOmnitrix.id("elbow_drop"), RathMoves::elbowDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("trash_talk"), RathMoves::trashTalk);
		AbilityRegistry.register(KingdomOmnitrix.id("royal_rumble"), RathMoves::royalRumble);
		AbilityRegistry.register(KingdomOmnitrix.id("spear_tackle"), RathMoves::spearTackle);
		AbilityRegistry.register(KingdomOmnitrix.id("flying_elbow"), RathMoves::flyingElbow);
		AbilityRegistry.register(KingdomOmnitrix.id("headlock"), RathMoves::headlock);
		AbilityRegistry.register(KingdomOmnitrix.id("diving_splash"), RathMoves::divingSplash);
		AbilityRegistry.register(KingdomOmnitrix.id("rath_bomb"), RathMoves::rathBomb);
		PassiveRegistry.register(KingdomOmnitrix.id("unbreakable_fury"), new AlienPassive() {
			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				// je weniger Leben, desto haerter — bis +60 % bei 20 % Leben
				ServerPlayerEntity rath = ctx.player();
				float missing = 1.0f - rath.getHealth() / rath.getMaxHealth();
				float max = (float) ctx.param("max_bonus", 0.6);
				return amount * (1.0f + Math.min(max, Math.max(0.0f, (missing - 0.2f) / 0.6f * max)));
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("appoplexian_temper"), new AlienPassive() {
			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				// wer Rath wehtut, macht ihn nur wuetender
				if (damage >= ctx.param("min_damage", 3.0)) {
					ctx.player().addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, (int) ctx.param("ticks", 80.0), 0, false, false));
					Fx.flames(ctx.world(), AlienKit.body(ctx.player()), new Vec3d(0, 0.08, 0), FURY, 0.3f, 3);
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("victory_lap"), new AlienPassive() {
			@Override
			public void onKill(PassiveContext ctx, LivingEntity target) {
				// nach jedem Sieg: kurz verschnaufen und weiter
				ServerPlayerEntity rath = ctx.player();
				rath.heal((float) ctx.param("heal", 3.0));
				rath.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 1, false, false));
				ctx.world().playSound(null, rath.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.8f, 1.4f);
			}
		});
	}

	private static Optional<LivingEntity> grab(ServerPlayerEntity player, double range) {
		return Targeting.findMeleeTarget(player, range, 0.5, e -> AlienKit.foe(player, e));
	}

	private static void noTarget(ServerPlayerEntity player) {
		player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Krallenwirbel: fuenf schnelle Krallenhiebe nach vorn — jeder laesst bluten. */
	private static boolean clawFlurry(AbilityContext ctx) {
		float damage = (float) ctx.param("damage", 2.5);
		double range = ctx.param("range", 3.5);
		int hits = (int) ctx.param("hits", 5.0);
		ctx.player().addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, hits * 3 + 4, 1, false, false));
		PowerTasks.start(ctx.player(), hits * 3, 3, (world, p, age) -> {
			for (LivingEntity target : AlienKit.cone(world, p, range, 0.4)) {
				AlienKit.melee(world, p, target, damage);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0, false, true), p);
			}
			Vec3d front = p.getEyePos().add(p.getRotationVec(1.0f).multiply(1.5));
			Fx.sparks(world, front, FURY, 0.4f, 4);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.2f + age * 0.04f);
			return true;
		});
		return true;
	}

	/** Waescheleine: stuermt mit ausgestrecktem Arm 8 Bloecke vor — jeder im Weg geht zu Boden. */
	private static boolean clothesline(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		float damage = (float) ctx.param("damage", 6.0);
		Set<UUID> hit = new HashSet<>();
		ctx.grantInvulnerability(10);
		PowerTasks.start(player, (int) ctx.param("ticks", 10.0), 1, (world, p, age) -> {
			BuiltinAbilities.launch(p, dir.x * 0.9, Math.min(p.getVelocity().y, 0.0), dir.z * 0.9);
			Fx.smoke(world, p.getPos(), DUST, 0.5f, 1);
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 1.0, 0), 1.8)) {
				if (hit.add(target.getUuid())) {
					AlienKit.melee(world, p, target, damage);
					target.setVelocity(dir.x * 0.4, 0.6, dir.z * 0.4);
					target.velocityModified = true;
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3, false, true), p);
					world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.2f, 0.7f);
				}
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.8f, 1.3f);
		return true;
	}

	/** Piledriver: packt das Ziel, springt hoch und rammt es kopfueber in den Boden — Krater. */
	private static boolean piledriver(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = grab(player, ctx.param("range", 4.0));
		if (found.isEmpty()) {
			noTarget(player);
			return false;
		}
		LivingEntity target = found.get();
		float damage = (float) ctx.param("damage", 12.0);
		BuiltinAbilities.launch(player, 0.0, ctx.param("jump", 1.1), 0.0);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (target.isAlive()) {
				Vec3d above = p.getPos().add(0, p.getHeight() + 0.2, 0);
				target.requestTeleport(above.x, above.y, above.z);
				target.setVelocity(Vec3d.ZERO);
			}
			if (age < 8) {
				return true;
			}
			if (age == 8) {
				BuiltinAbilities.launch(p, 0.0, -1.8, 0.0);
			}
			if (!p.isOnGround()) {
				return true;
			}
			if (target.isAlive()) {
				target.requestTeleport(p.getX(), p.getY(), p.getZ());
				AlienKit.melee(world, p, target, damage);
			}
			Destruction.crater(world, p, p.getPos().add(0, -0.6, 0), 1.8, 0.5, AlienStrength.of(p), 0.3, 0.4);
			EarthWave.ring(world, p.getPos(), 3.0, 4, 0.6f);
			Fx.shake(world, p.getPos(), 0.4f, 8, 16);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 0.8f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.0f, 1.0f);
		return true;
	}

	/** Ellbogensturz: hoher Sprung und Ellbogen voraus auf den Zielpunkt — Druckwelle beim Aufschlag. */
	private static boolean elbowDrop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d target = AlienKit.ground(ctx.world(), AlienKit.aim(ctx.world(), player, ctx.param("range", 14.0)), 8.0);
		Vec3d to = target.subtract(player.getPos());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		BuiltinAbilities.launch(player, to.x / Math.max(1.0, flat) * Math.min(1.4, flat * 0.12), 1.1,
				to.z / Math.max(1.0, flat) * Math.min(1.4, flat * 0.12));
		float damage = (float) ctx.param("damage", 8.0);
		double radius = ctx.param("radius", 3.5);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 80, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (age < 4 || !p.isOnGround()) {
				return true;
			}
			AlienKit.blast(world, p, p.getPos().add(0, 0.5, 0), radius, damage, 0.9, 0.5);
			EarthWave.ring(world, p.getPos(), radius, 4, 0.5f);
			Fx.shockwave(world, p.getPos().add(0, 0.2, 0), DUST, (float) radius, 10);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.4f, 0.6f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_STEP, 1.2f, 0.8f);
		return true;
	}

	/** Grossmaul: „Lass mich dir was sagen!“ — alle Monster in der Naehe gehen auf Rath los, Rath steckt viel weniger ein. */
	private static boolean trashTalk(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		for (LivingEntity target : AlienKit.around(world, player, player.getPos(), ctx.param("radius", 12.0))) {
			if (target instanceof MobEntity mob) {
				mob.setTarget(player);
			}
		}
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, (int) ctx.param("resistance", 1.0), false, false));
		player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.rath_trash_talk").formatted(net.minecraft.util.Formatting.GOLD), true);
		Fx.send(world, Fx.Kind.SHOCKWAVE, player.getEyePos(), BuiltinAbilities.horizontalLook(player), FURY, 4.0f, 10, 1, null);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.4f, 1.2f);
		return true;
	}

	/** Koenigliche Keilerei (Ultimate): 8 s Raserei — jede Sekunde packt Rath den naechsten Gegner und wirft ihn hinter sich. */
	private static boolean royalRumble(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		float damage = (float) ctx.param("damage", 9.0);
		double reach = ctx.param("reach", 7.0);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 2, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 1, false, false));
		Fx.aura(ctx.world(), player, FURY, 1.4f, ticks);
		PowerTasks.start(player, ticks, 20, (world, p, age) -> {
			AlienKit.around(world, p, p.getPos(), reach).stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(p))).ifPresent(target -> {
				Vec3d front = p.getPos().add(BuiltinAbilities.horizontalLook(p).multiply(1.2));
				target.requestTeleport(front.x, front.y, front.z);
				RathAbilities.throwBehind(world, p, target, damage);
			});
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.6f, 0.8f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Speer-Tackle (Sprint + Schlag): rammt das Ziel um — es liegt am Boden und kommt kaum hoch. */
	private static boolean spearTackle(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0));
		target.setVelocity(dir.x * 1.2, 0.2, dir.z * 1.2);
		target.velocityModified = true;
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 4, false, true), player);
		BuiltinAbilities.launch(player, dir.x * 0.8, 0.1, dir.z * 0.8);
		Fx.smoke(ctx.world(), target.getPos(), DUST, 0.8f, 4);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.7f);
		return true;
	}

	/** Fliegender Ellbogen (Schlag in der Luft): Ellbogen von oben — Ziel zu Boden, kleiner Aufprall ringsum. */
	private static boolean flyingElbow(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		AlienKit.melee(world, player, target, (float) ctx.param("damage", 7.0));
		target.setVelocity(0.0, -1.2, 0.0);
		target.velocityModified = true;
		for (LivingEntity near : AlienKit.around(world, player, target.getPos().add(0, 0.5, 0), 2.5)) {
			if (near != target) {
				AlienKit.push(near, target.getPos(), 0.6, 0.4);
			}
		}
		EarthWave.ring(world, target.getPos(), 2.0, 3, 0.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.8f);
		return true;
	}

	/** Schwitzkasten (Schleichen + Schlag): nimmt das Ziel in den Schwitzkasten — 3 s festgehalten, jede Sekunde Schaden. */
	private static boolean headlock(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 3.0);
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		AlienKit.hold(ctx.world(), target, ticks, ParticleTypes.ANGRY_VILLAGER, false);
		PowerTasks.start(player, ticks, 20, (world, p, age) -> {
			if (!target.isAlive() || target.squaredDistanceTo(p) > 25.0) {
				return false;
			}
			AlienKit.melee(world, p, target, damage);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 0.7f);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.4f);
		return true;
	}

	/** Bauchplatscher (hoch in der Luft schleichen): flach auf alles darunter — Wucht nach Fallhoehe. */
	private static boolean divingSplash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 8.0);
		double radius = ctx.param("radius", 3.5);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.3, -ctx.param("dive", 1.8), player.getVelocity().z * 0.3);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			AlienKit.blast(world, p, p.getPos().add(0, 0.5, 0), radius * boost, damage * boost, 0.8, 0.6);
			EarthWave.ring(world, p.getPos(), radius * boost, 4, 0.6f);
			Destruction.crater(world, p, p.getPos().add(0, -0.6, 0), 1.2 * boost, 0.5, AlienStrength.of(p) * boost, 0.3, 0.4);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 0.8f);
			return false;
		});
		return true;
	}

	/** Rath-Bombe (Schlag gegen geschwaechtes Ziel): Powerbomb — ueber den Kopf gerissen und in den Boden geschmettert. */
	private static boolean rathBomb(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d front = player.getPos().add(BuiltinAbilities.horizontalLook(player).multiply(1.4));
		float damage = (float) (ctx.param("damage", 12.0) + (target.getMaxHealth() - target.getHealth()) * 0.2);
		target.requestTeleport(front.x, front.y, front.z);
		AlienKit.melee(world, player, target, damage);
		target.setVelocity(0.0, -0.8, 0.0);
		target.velocityModified = true;
		Destruction.crater(world, player, front.add(0, -0.6, 0), 1.6, 0.5, AlienStrength.of(player), 0.3, 0.4);
		EarthWave.ring(world, front, 3.0, 4, 0.7f);
		Fx.shockwave(world, front.add(0, 0.2, 0), FURY, 3.5f, 10);
		Fx.shake(world, front, 0.4f, 8, 16);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0f, 0.7f);
		return true;
	}
}
