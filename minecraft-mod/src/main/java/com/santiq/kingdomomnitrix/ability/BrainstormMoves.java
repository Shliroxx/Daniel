package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
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
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Brainstorm, Kraefte 2.0: Elektropeitsche, Kettenblitz, Telekinese-Feld, Kugelblitz, Taktisches Genie, Donnerkuppel;
 * Kombos Panzerramme, Schaedelknacker, Scherengriff, Donnersturz, Neuronale Ueberlastung; Passive Ueberlegener Intellekt,
 * Krabbenpanzer und Statische Entladung. Blitze laufen ueber {@link BrainstormAbilities#zap} — analysierte Ziele
 * leiten sie weiter.
 */
final class BrainstormMoves {
	private static final int PINK = 0xF07AC8;
	private static final int BOLT = 0xE6F4FF;

	private BrainstormMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("electro_whip"), BrainstormMoves::electroWhip);
		AbilityRegistry.register(KingdomOmnitrix.id("chain_lightning"), BrainstormMoves::chainLightning);
		AbilityRegistry.register(KingdomOmnitrix.id("telekinetic_lift"), BrainstormMoves::telekineticLift);
		AbilityRegistry.register(KingdomOmnitrix.id("ball_lightning"), BrainstormMoves::ballLightning);
		AbilityRegistry.register(KingdomOmnitrix.id("tactical_genius"), BrainstormMoves::tacticalGenius);
		AbilityRegistry.register(KingdomOmnitrix.id("thunderdome"), BrainstormMoves::thunderdome);
		AbilityRegistry.register(KingdomOmnitrix.id("shell_ram"), BrainstormMoves::shellRam);
		AbilityRegistry.register(KingdomOmnitrix.id("skull_crack"), BrainstormMoves::skullCrack);
		AbilityRegistry.register(KingdomOmnitrix.id("pincer_grip"), BrainstormMoves::pincerGrip);
		AbilityRegistry.register(KingdomOmnitrix.id("thunder_drop"), BrainstormMoves::thunderDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("neural_overload"), BrainstormMoves::neuralOverload);
		PassiveRegistry.register(KingdomOmnitrix.id("superior_intellect"), new AlienPassive() {
			@Override
			public void melee(PassiveContext ctx, LivingEntity target, boolean fullSwing) {
				// ein Blick genuegt: wer von Brainstorm geschlagen wird, ist analysiert
				if (fullSwing) {
					BrainstormAbilities.analyze(ctx.world(), ctx.player(), target, (int) ctx.param("analyze_ticks", 100.0));
				}
			}

			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				// er kennt die Schwachstellen der Analysierten
				return BrainstormAbilities.analyzed(target) ? amount * (float) ctx.param("weak_spot", 1.2) : amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("crab_shell"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// der Krabbenpanzer haelt Geschosse und Schlaege ab
				if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
					return amount * (float) ctx.param("projectile", 0.6);
				}
				return source.getAttacker() != null && source.getSource() == source.getAttacker() ? amount * (float) ctx.param("melee", 0.85) : amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("static_discharge"), new AlienPassive() {
			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				// wer ihn im Nahkampf trifft, bekommt einen Schlag
				if (source.getAttacker() instanceof LivingEntity attacker && source.getSource() == attacker && AlienKit.foe(ctx.player(), attacker)) {
					BrainstormAbilities.zap(ctx.world(), ctx.player(), AlienKit.body(ctx.player()), attacker, (float) ctx.param("damage", 3.0), new HashSet<>());
				}
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Elektropeitsche: Blitzpeitsche 10 Bloecke geradeaus — trifft alles auf der Linie und laehmt kurz. */
	private static boolean electroWhip(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d from = AlienKit.muzzle(brain);
		Vec3d to = AlienKit.beam(world, brain, ctx.param("range", 10.0)).end();
		float damage = (float) ctx.param("damage", 5.0) * BrainstormAbilities.spendStored(brain);
		BrainstormAbilities.jagged(world, from, to);
		for (LivingEntity target : AlienKit.along(world, brain, from, to, 0.8)) {
			AlienKit.magic(world, brain, target, damage);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3, false, true), brain);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
		return true;
	}

	/** Kettenblitz: springt vom naechsten Gegner auf bis zu vier weitere — auch ohne Analyse. */
	private static boolean chainLightning(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> first = AlienKit.around(world, brain, brain.getPos(), ctx.param("range", 12.0)).stream()
				.min(Comparator.comparingDouble(e -> e.squaredDistanceTo(brain)));
		if (first.isEmpty()) {
			brain.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		float damage = (float) ctx.param("damage", 6.0) * BrainstormAbilities.spendStored(brain);
		Set<UUID> done = new HashSet<>();
		LivingEntity current = first.get();
		Vec3d from = AlienKit.muzzle(brain);
		for (int i = 0; i <= (int) ctx.param("jumps", 4.0) && current != null; i++) {
			BrainstormAbilities.zap(world, brain, from, current, damage * (1.0f - 0.12f * i), done);
			from = AlienKit.body(current);
			LivingEntity last = current;
			current = AlienKit.around(world, brain, last.getPos(), 7.0).stream().filter(e -> !done.contains(e.getUuid()))
					.min(Comparator.comparingDouble(e -> e.squaredDistanceTo(last))).orElse(null);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
		return true;
	}

	/** Telekinese-Feld: hebt das Ziel im Blick in einem Elektrofeld hoch, haelt es 3 s und laesst es fallen. */
	private static boolean telekineticLift(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(brain, ctx.param("range", 16.0), 0.85, e -> AlienKit.foe(brain, e));
		if (found.isEmpty()) {
			brain.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		Vec3d hover = target.getPos().add(0, ctx.param("height", 5.0), 0);
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		PowerTasks.start(brain, ticks, 1, (world, p, age) -> {
			if (!target.isAlive()) {
				return false;
			}
			Vec3d to = target.getPos().lerp(hover, 0.2);
			target.requestTeleport(to.x, to.y, to.z);
			target.setVelocity(Vec3d.ZERO);
			target.fallDistance = 0.0f;
			if (age % 4 == 0) {
				BrainstormAbilities.jagged(world, AlienKit.muzzle(p), AlienKit.body(target));
			}
			return true;
		}, (p, aborted) -> {
			if (target.isAlive()) {
				target.setVelocity(0.0, -1.5, 0.0);
				target.velocityModified = true;
				target.fallDistance = (float) ctx.param("height", 5.0) + 3.0f;
			}
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_AMBIENT, 1.5f, 1.6f);
		return true;
	}

	/** Kugelblitz: eine langsame Blitzkugel rollt 4 s geradeaus und schlaegt in alles in ihrer Naehe ein. */
	private static boolean ballLightning(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		Vec3d dir = brain.getRotationVec(1.0f).multiply(1, 0, 1).normalize();
		Vec3d[] pos = {AlienKit.muzzle(brain)};
		float damage = (float) ctx.param("damage", 3.0);
		double reach = ctx.param("reach", 3.5);
		PowerTasks.start(brain, (int) (ctx.param("seconds", 4.0) * 20), 1, (world, p, age) -> {
			Vec3d next = pos[0].add(dir.multiply(ctx.param("speed", 0.35)));
			if (!world.isSpaceEmpty(net.minecraft.util.math.Box.of(next, 0.5, 0.5, 0.5))) {
				return false;
			}
			pos[0] = next;
			Fx.sparks(world, pos[0], BOLT, 0.2f, 3);
			Fx.flash(world, pos[0], PINK, 0.6f);
			if (age % 10 == 0) {
				for (LivingEntity target : AlienKit.around(world, p, pos[0], reach)) {
					BrainstormAbilities.zap(world, p, pos[0], target, damage, new HashSet<>());
				}
				world.playSound(null, BlockPos.ofFloored(pos[0]), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.0f, 2.0f);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
		return true;
	}

	/** Taktisches Genie: analysiert alle Gegner ringsum, Verbuendete bekommen den Plan — Staerke und Eile fuer 10 s. */
	private static boolean tacticalGenius(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 10.0) * 20);
		double radius = ctx.param("radius", 16.0);
		int count = 0;
		for (LivingEntity target : AlienKit.around(world, brain, brain.getPos(), radius)) {
			BrainstormAbilities.analyze(world, brain, target, ticks);
			count++;
		}
		brain.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, ticks, 1, false, false));
		for (ServerPlayerEntity ally : world.getPlayers(o -> o != brain && o.squaredDistanceTo(brain) <= radius * radius && PartyRules.isAlly(brain, o))) {
			ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 0, false, true));
			ally.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, ticks, 1, false, true));
		}
		brain.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.brainstorm_analysis", count).formatted(net.minecraft.util.Formatting.LIGHT_PURPLE), true);
		Fx.shockwave(world, brain.getPos().add(0, 0.2, 0), PINK, (float) radius, 14);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.4f, 1.0f);
		return true;
	}

	/**
	 * Donnerkuppel (Ultimate): eine Elektrokuppel um Brainstorm (6 s) — wer hinaus will, wird zurueckgestossen; jede
	 * Sekunde schlaegt es in alle Gegner darin ein und analysiert sie. Zum Schluss entlaedt sich die Kuppel.
	 */
	private static boolean thunderdome(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		Vec3d center = brain.getPos();
		double radius = ctx.param("radius", 9.0);
		float damage = (float) ctx.param("damage", 3.0);
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		brain.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20, 0, false, false));
		brain.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, ticks, 0, false, false));
		ctx.grantInvulnerability(20);
		PowerTasks.start(brain, ticks + 1, 2, (world, p, age) -> {
			for (int i = 0; i < 6; i++) {
				double a = world.random.nextDouble() * MathHelper.TAU;
				double h = world.random.nextDouble() * radius * 0.8;
				double r = Math.sqrt(Math.max(0.0, radius * radius - h * h));
				Fx.sparks(world, center.add(Math.cos(a) * r, h, Math.sin(a) * r), i % 2 == 0 ? PINK : BOLT, 0.05f, 1);
			}
			for (LivingEntity target : AlienKit.around(world, p, center, radius + 2.0)) {
				double d = target.getPos().distanceTo(center);
				if (d > radius - 1.0) {
					// Kuppelrand: zurueck nach innen
					Vec3d in = center.subtract(target.getPos()).multiply(1, 0, 1).normalize().multiply(0.6);
					target.setVelocity(in.x, 0.2, in.z);
					target.velocityModified = true;
				}
				if (age % 20 == 0 && d <= radius) {
					BrainstormAbilities.analyze(world, p, target, 60);
					BrainstormAbilities.zap(world, p, center.add(0, radius * 0.7, 0), target, damage, new HashSet<>());
				}
			}
			if (age >= ticks) {
				AlienKit.blast(world, p, center.add(0, 1.0, 0), radius, (float) ctx.param("final_damage", 10.0), 1.2, 0.5);
				Fx.flash(world, center.add(0, 2.0, 0), BOLT, 6.0f);
				world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.5f, 1.0f);
			}
			return true;
		});
		Fx.shockwave(ctx.world(), center.add(0, 0.2, 0), PINK, (float) radius, 16);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Panzerramme (Sprint + Schlag): rammt mit dem Panzer — dazu ein Stromschlag. */
	private static boolean shellRam(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity brain = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(brain);
		AlienKit.melee(ctx.world(), brain, target, (float) ctx.param("damage", 5.0));
		BrainstormAbilities.zap(ctx.world(), brain, AlienKit.body(brain), target, 2.0f, new HashSet<>());
		target.setVelocity(dir.x * 1.3, 0.4, dir.z * 1.3);
		target.velocityModified = true;
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 1.4f);
		return true;
	}

	/** Schaedelknacker (Schlag in der Luft): Kopfstoss mit dem Gehirnpanzer von oben — Ziel analysiert und zu Boden. */
	private static boolean skullCrack(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity brain = ctx.player();
		AlienKit.melee(ctx.world(), brain, target, (float) ctx.param("damage", 6.0));
		BrainstormAbilities.analyze(ctx.world(), brain, target, 200);
		target.setVelocity(0.0, -1.0, 0.0);
		target.velocityModified = true;
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0, false, true), brain);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 1.4f);
		return true;
	}

	/** Scherengriff (Schleichen + Schlag): die Scheren packen zu — 2 s festgehalten, jede halbe Sekunde ein Stromstoss. */
	private static boolean pincerGrip(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity brain = ctx.player();
		AlienKit.melee(ctx.world(), brain, target, (float) ctx.param("damage", 3.0));
		AlienKit.hold(ctx.world(), target, 40, net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, false);
		PowerTasks.start(brain, 40, 10, (world, p, age) -> {
			if (target.isAlive() && target.squaredDistanceTo(p) < 25.0) {
				BrainstormAbilities.zap(world, p, AlienKit.body(p), target, 1.5f, new HashSet<>());
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
		return true;
	}

	/** Donnersturz (hoch in der Luft schleichen): stuerzt herab und entlaedt sich beim Aufschlag in alle ringsum. */
	private static boolean thunderDrop(AbilityContext ctx) {
		ServerPlayerEntity brain = ctx.player();
		double startY = brain.getY() + brain.fallDistance;
		float damage = (float) ctx.param("damage", 6.0);
		double radius = ctx.param("radius", 4.0);
		BuiltinAbilities.launch(brain, brain.getVelocity().x * 0.2, -ctx.param("dive", 2.0), brain.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(brain, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				Fx.sparks(world, p.getPos().add(0, 1.0, 0), BOLT, 0.15f, 2);
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			Set<UUID> done = new HashSet<>();
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.5, 0), radius * boost)) {
				if (!done.contains(target.getUuid())) {
					BrainstormAbilities.zap(world, p, p.getPos().add(0, 0.5, 0), target, damage * boost, done);
				}
			}
			Fx.shockwave(world, p.getPos().add(0, 0.2, 0), PINK, (float) (radius * boost), 10);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.2f, 1.2f);
			return false;
		});
		return true;
	}

	/** Neuronale Ueberlastung (Schlag gegen geschwaechtes Ziel): ein Blitz direkt ins Nervensystem — springt auf alle Analysierten. */
	private static boolean neuralOverload(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity brain = ctx.player();
		ServerWorld world = ctx.world();
		float damage = (float) (ctx.param("damage", 10.0) + (target.getMaxHealth() - target.getHealth()) * 0.2) * BrainstormAbilities.spendStored(brain);
		AlienKit.magic(world, brain, target, damage);
		Set<UUID> done = new HashSet<>();
		done.add(target.getUuid());
		for (LivingEntity other : AlienKit.around(world, brain, target.getPos(), ctx.param("radius", 12.0))) {
			if (BrainstormAbilities.analyzed(other) && !done.contains(other.getUuid())) {
				BrainstormAbilities.zap(world, brain, AlienKit.body(target), other, damage * 0.5f, done);
			}
		}
		Fx.flash(world, AlienKit.body(target), PINK, 2.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f, 1.6f);
		return true;
	}
}
