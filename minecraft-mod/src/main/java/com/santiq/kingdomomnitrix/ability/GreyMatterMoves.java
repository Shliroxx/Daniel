package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.galvan.GreyMatterKnowledge;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Grey Matter, Kraefte 2.0 — klein, schnell, schlauer als alle: Kopfsprung, Berechneter Schlag, Ueberladung,
 * Mini-Geschuetz, Schwachstellen-Netz, Galvan-Geraet; Kombos Wadenbiss, Kopfnuss, Kabelfalle, Praezisionssturz,
 * Logisches Ende; Passive Beobachtung, Kleines Ziel und Kletterkuenstler. Fast alles wird mit dem Wissen ueber die
 * Gegnerart staerker ({@link GreyMatterKnowledge}).
 */
final class GreyMatterMoves {
	private static final int TECH = 0x38FF14;
	private static final int ELECTRIC = 0x8AE8FF;

	private GreyMatterMoves() {
	}

	/** Faktor nach Wissen: 1 + 0,25 je Stufe (bis 2,25). */
	private static float smart(ServerPlayerEntity player, LivingEntity target) {
		return 1.0f + GreyMatterKnowledge.level(player, target) * 0.25f;
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("head_hop"), GreyMatterMoves::headHop);
		AbilityRegistry.register(KingdomOmnitrix.id("calculated_strike"), GreyMatterMoves::calculatedStrike);
		AbilityRegistry.register(KingdomOmnitrix.id("overload_device"), GreyMatterMoves::overloadDevice);
		AbilityRegistry.register(KingdomOmnitrix.id("mini_turret"), GreyMatterMoves::miniTurret);
		AbilityRegistry.register(KingdomOmnitrix.id("weakness_grid"), GreyMatterMoves::weaknessGrid);
		AbilityRegistry.register(KingdomOmnitrix.id("galvan_device"), GreyMatterMoves::galvanDevice);
		AbilityRegistry.register(KingdomOmnitrix.id("ankle_bite"), GreyMatterMoves::ankleBite);
		AbilityRegistry.register(KingdomOmnitrix.id("skull_hop"), GreyMatterMoves::skullHop);
		AbilityRegistry.register(KingdomOmnitrix.id("wire_trip"), GreyMatterMoves::wireTrip);
		AbilityRegistry.register(KingdomOmnitrix.id("precision_drop"), GreyMatterMoves::precisionDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("logical_conclusion"), GreyMatterMoves::logicalConclusion);
		PassiveRegistry.register(KingdomOmnitrix.id("keen_observer"), new AlienPassive() {
			@Override
			public void melee(PassiveContext ctx, LivingEntity target, boolean fullSwing) {
				// jeder Schlag markiert das Ziel fuer die Gruppe (20 % mehr Schaden fuer alle)
				GreyMatterKnowledge.mark(ctx.player(), target, (int) ctx.param("mark_ticks", 120.0));
			}

			@Override
			public void onKill(PassiveContext ctx, LivingEntity target) {
				// aus jedem Sieg lernt Grey Matter etwas ueber die Art (wie eine Analyse)
				if (ctx.world().random.nextDouble() < ctx.param("learn_chance", 0.35)) {
					GreyMatterAbilities.learnFrom(ctx.player(), target);
					Fx.sparks(ctx.world(), AlienKit.body(target), TECH, 0.3f, 8);
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("tiny_target"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// so klein, dass vieles einfach vorbeigeht
				double chance = source.isIn(DamageTypeTags.IS_PROJECTILE) ? ctx.param("projectile", 0.35)
						: source.isIn(DamageTypeTags.IS_EXPLOSION) ? ctx.param("explosion", 0.2) : 0.0;
				if (chance > 0.0 && ctx.world().random.nextDouble() < chance) {
					Fx.sparks(ctx.world(), ctx.player().getPos().add(0, 0.3, 0), TECH, 0.2f, 4);
					return 0.0f;
				}
				return amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("wall_crawler"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// Galvan klettern: gegen eine Wand laufen fuehrt hinauf, Schleichen haelt an der Wand fest
				ServerPlayerEntity player = ctx.player();
				if (player.getAbilities().flying || player.isOnGround() && !player.isSneaking() && player.forwardSpeed <= 0.0f) {
					return;
				}
				Vec3d look = BuiltinAbilities.horizontalLook(player);
				if (!ctx.world().isSpaceEmpty(player, player.getBoundingBox().offset(look.multiply(0.25)))) {
					Vec3d v = player.getVelocity();
					double climb = player.isSneaking() ? 0.0 : 0.2;
					player.setVelocity(v.x, Math.max(v.y, climb), v.z);
					player.velocityModified = true;
					player.fallDistance = 0.0f;
				}
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Kopfsprung: springt dem naechsten Gegner auf den Kopf, haemmert drei Sekunden lang drauf und verwirrt ihn. */
	private static boolean headHop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 10.0), 0.3, e -> AlienKit.foe(player, e));
		if (found.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		UUID id = found.get().getUuid();
		float damage = (float) ctx.param("damage", 2.0) * smart(player, found.get());
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		found.get().addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, ticks + 40, 0), player);
		found.get().addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 1), player);
		ctx.grantInvulnerability(10);
		PowerTasks.start(player, ticks, 1, (world, p, age) -> {
			if (!(world.getEntity(id) instanceof LivingEntity ride) || !ride.isAlive()) {
				return false;
			}
			p.requestTeleport(ride.getX(), ride.getY() + ride.getHeight(), ride.getZ());
			p.setVelocity(Vec3d.ZERO);
			p.velocityModified = true;
			p.fallDistance = 0.0f;
			if (age % 8 == 0) {
				AlienKit.melee(world, p, ride, damage);
				Fx.sparks(world, ride.getEyePos(), TECH, 0.3f, 5);
				world.playSound(null, ride.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.9f, 1.8f);
			}
			return true;
		}, (p, aborted) -> BuiltinAbilities.launch(p, 0.0, 0.6, 0.0));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_JUMP_SMALL, 1.2f, 1.6f);
		return true;
	}

	/** Berechneter Schlag: exakt durchgerechneter Treffer auf das Ziel im Blick — ignoriert Ruestung, waechst mit Wissen. */
	private static boolean calculatedStrike(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 6.0), 0.5, e -> AlienKit.foe(player, e));
		if (found.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		float damage = (float) ctx.param("damage", 6.0) * smart(player, target);
		AlienKit.magic(world, player, target, damage);
		Vec3d at = AlienKit.body(target);
		Fx.flash(world, at, TECH, 1.6f);
		Fx.send(world, Fx.Kind.SHOCKWAVE, at, player.getRotationVec(1.0f), TECH, 1.4f, 6, 1, null);
		Fx.beam(world, player.getEyePos(), at, TECH, 0.06f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 2.0f);
		return true;
	}

	/** Ueberladung: zusammengebasteltes Geraet fliegt ans Ziel und entlaedt sich nach 1,5 s — Stromschlag, Laehmung. */
	private static boolean overloadDevice(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d at = AlienKit.ground(ctx.world(), AlienKit.aim(ctx.world(), player, ctx.param("range", 14.0)), 6.0).add(0, 0.3, 0);
		float damage = (float) ctx.param("damage", 8.0);
		double radius = ctx.param("radius", 3.5);
		Fx.beam(ctx.world(), player.getEyePos(), at, TECH, 0.05f, 6);
		PowerTasks.start(player, 30, 1, (world, p, age) -> {
			if (age % 6 == 0) {
				Fx.flash(world, at, age % 12 == 0 ? TECH : 0xFF3030, 0.6f);
				world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.8f, 1.0f + age * 0.03f);
			}
			if (age < 29) {
				return true;
			}
			for (LivingEntity target : AlienKit.blast(world, p, at, radius, damage, 0.8, 0.3)) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 4), p);
				GreyMatterKnowledge.mark(p, target, GreyMatterKnowledge.MARK_TICKS);
			}
			for (int i = 0; i < 6; i++) {
				double a = i * MathHelper.TAU / 6;
				Fx.beam(world, at, at.add(Math.cos(a) * radius, 0.4, Math.sin(a) * radius), ELECTRIC, 0.12f, 6);
			}
			Fx.explosion(world, at, ELECTRIC, 1.6f);
			Fx.shockwave(world, at, ELECTRIC, (float) radius, 8);
			world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 1.6f);
			return false;
		});
		BuiltinAbilities.sound(ctx, ModSounds.WEAPON_THROW, 0.8f, 1.6f);
		return true;
	}

	/** Mini-Geschuetz: baut in Sekunden ein Geschuetz, das 10 s lang auf den naechsten Gegner feuert (Wissen = mehr Schaden). */
	private static boolean miniTurret(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d at = AlienKit.ground(ctx.world(), player.getPos().add(BuiltinAbilities.horizontalLook(player).multiply(1.5)).add(0, 1, 0), 4.0)
				.add(0, 0.5, 0);
		float damage = (float) ctx.param("damage", 3.0);
		double range = ctx.param("range", 12.0);
		int ticks = (int) (ctx.param("seconds", 10.0) * 20);
		PowerTasks.start(player, ticks, 1, (world, p, age) -> {
			if (age % 10 == 0) {
				Fx.flash(world, at, TECH, 0.5f);
			}
			if (age % 15 != 0) {
				return true;
			}
			List<LivingEntity> targets = AlienKit.around(world, p, at, range);
			targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(at)));
			if (!targets.isEmpty()) {
				LivingEntity target = targets.get(0);
				Fx.beam(world, at, AlienKit.body(target), TECH, 0.1f, 4);
				AlienKit.magic(world, p, target, damage * smart(p, target));
				world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_DISPENSER_LAUNCH, SoundCategory.PLAYERS, 0.6f, 1.8f);
			}
			return true;
		});
		Fx.sparks(ctx.world(), at, TECH, 0.3f, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ANVIL_USE, 0.6f, 1.8f);
		return true;
	}

	/** Schwachstellen-Netz: analysiert alle Gegner im Umkreis auf einmal und legt bei jedem die Schwachstelle frei. */
	private static boolean weaknessGrid(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		List<LivingEntity> targets = AlienKit.around(world, player, player.getPos(), ctx.param("radius", 12.0));
		if (targets.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		for (LivingEntity target : targets) {
			GreyMatterAbilities.learnFrom(player, target);
			GreyMatterKnowledge.mark(player, target, GreyMatterKnowledge.MARK_TICKS);
			GreyMatterAbilities.weaken(target, (int) (ctx.param("seconds", 10.0) * 20));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0), player);
			Fx.beam(world, player.getEyePos(), AlienKit.body(target), TECH, 0.04f, 10);
		}
		Fx.shockwave(world, player.getPos().add(0, 0.1, 0), TECH, (float) ctx.param("radius", 12.0), 14);
		BuiltinAbilities.sound(ctx, ModSounds.SHIP_AI, 0.9f, 1.6f);
		return true;
	}

	/**
	 * Galvan-Geraet (Ultimate): ein Singularitaets-Geraet — zieht drei Sekunden lang alle Gegner zusammen, analysiert sie
	 * und implodiert dann mit Schaden nach Wissen.
	 */
	private static boolean galvanDevice(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d at = AlienKit.ground(ctx.world(), AlienKit.aim(ctx.world(), player, ctx.param("range", 16.0)), 8.0).add(0, 1.2, 0);
		double radius = ctx.param("radius", 10.0);
		float damage = (float) ctx.param("damage", 14.0);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			double spin = age * 0.4;
			for (int i = 0; i < 3; i++) {
				double a = spin + i * MathHelper.TAU / 3;
				Fx.beam(world, at, at.add(Math.cos(a) * 1.4, Math.sin(spin) * 0.4, Math.sin(a) * 1.4), TECH, 0.08f, 2);
			}
			if (age % 4 == 0) {
				for (LivingEntity target : AlienKit.around(world, p, at, radius)) {
					Vec3d pull = at.subtract(target.getPos()).normalize().multiply(0.3);
					target.addVelocity(pull.x, pull.y * 0.5 + 0.04, pull.z);
					target.velocityModified = true;
					if (age == 0) {
						GreyMatterAbilities.learnFrom(p, target);
					}
				}
			}
			if (age % 20 == 0) {
				Fx.charge(world, p, TECH, 1.0f, 4);
				Fx.shockwave(world, at.add(0, -1.0, 0), TECH, (float) radius, 18);
				world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.4f, 1.6f);
			}
			if (age < 59) {
				return true;
			}
			for (LivingEntity target : AlienKit.around(world, p, at, radius * 0.6)) {
				AlienKit.magic(world, p, target, damage * smart(p, target));
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1), p);
				GreyMatterAbilities.weaken(target, 200);
			}
			Fx.explosion(world, at, TECH, 3.0f);
			Fx.flash(world, at, 0xFFFFFF, 3.0f);
			Fx.shake(world, at, 0.5f, 12, 24);
			world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.4f, 1.4f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.2f, 1.4f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Wadenbiss (Sprint + Schlag): ein Biss in den Knoechel — blutet (Gift), humpelt (verlangsamt). */
	private static boolean ankleBite(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 3.0) * smart(player, target));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0), player);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1), player);
		Fx.sparks(ctx.world(), target.getPos().add(0, 0.3, 0), 0xC02020, 0.25f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_FOX_BITE, 1.2f, 1.6f);
		return true;
	}

	/** Kopfnuss (Schlag in der Luft): landet auf dem Kopf, zwei Schlaege, federt hoch fuer den naechsten Sprung. */
	private static boolean skullHop(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 3.0) * smart(player, target);
		AlienKit.melee(ctx.world(), player, target, damage * 2.0f);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), player);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.5, 0.9, player.getVelocity().z * 0.5);
		Fx.flash(ctx.world(), target.getEyePos(), TECH, 1.2f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_JUMP_SMALL, 1.0f, 1.8f);
		return true;
	}

	/** Kabelfalle (Schleichen + Schlag): verknotet die Beine des Ziels mit Draht — 2,5 Sekunden festgehalten. */
	private static boolean wireTrip(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 2.5) * 20);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 2.0));
		AlienKit.hold(ctx.world(), target, ticks, AlienKit.dust("#38FF14", 0.8f), false);
		if (target instanceof MobEntity mob) {
			mob.getNavigation().stop();
		}
		Fx.sparks(ctx.world(), target.getPos().add(0, 0.2, 0), ELECTRIC, 0.2f, 8);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_TRIPWIRE_ATTACH, 1.2f, 1.2f);
		return true;
	}

	/** Praezisionssturz (hoch in der Luft schleichen): gezielter Sturz auf den naechsten Gegner darunter. */
	private static boolean precisionDrop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d below = player.getPos().add(0, -4.0, 0);
		List<LivingEntity> targets = AlienKit.around(world, player, below, ctx.param("radius", 6.0));
		targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
		float damage = (float) ctx.param("damage", 8.0);
		if (targets.isEmpty()) {
			BuiltinAbilities.launch(player, 0.0, -1.6, 0.0);
			return true;
		}
		LivingEntity target = targets.get(0);
		Vec3d to = target.getEyePos().subtract(player.getPos()).normalize().multiply(1.8);
		BuiltinAbilities.launch(player, to.x, to.y, to.z);
		UUID id = target.getUuid();
		PowerTasks.start(player, 30, 1, (w, p, age) -> {
			p.fallDistance = 0.0f;
			if (!(w.getEntity(id) instanceof LivingEntity t) || !t.isAlive()) {
				return false;
			}
			if (p.squaredDistanceTo(t) < 4.0 || p.isOnGround()) {
				AlienKit.magic(w, p, t, damage * smart(p, t));
				Fx.flash(w, AlienKit.body(t), TECH, 1.8f);
				Fx.shockwave(w, t.getPos().add(0, 0.1, 0), TECH, 2.5f, 6);
				w.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2f, 1.4f);
				BuiltinAbilities.launch(p, 0.0, 0.5, 0.0);
				return false;
			}
			return true;
		});
		return true;
	}

	/**
	 * Logisches Ende (Schlag gegen geschwaechtes Ziel): Grey Matter hat alles durchgerechnet — Schaden nach dem restlichen
	 * Leben und dem Wissen ueber die Art; ab Wissen 3 genau so viel wie noetig.
	 */
	private static boolean logicalConclusion(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int knowledge = GreyMatterKnowledge.level(player, target);
		float share = Math.min(1.0f, 0.5f + knowledge * 0.17f);
		float damage = (float) ctx.param("damage", 4.0) + target.getHealth() * share;
		AlienKit.magic(world, player, target, damage);
		Vec3d at = AlienKit.body(target);
		Fx.flash(world, at, TECH, 2.4f);
		for (int i = 0; i < 4; i++) {
			double a = i * MathHelper.TAU / 4;
			Fx.beam(world, at.add(Math.cos(a) * 2.0, 1.0, Math.sin(a) * 2.0), at, TECH, 0.06f, 8);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.2f, 0.8f);
		BuiltinAbilities.sound(ctx, ModSounds.SHIP_AI, 0.8f, 1.8f);
		return true;
	}
}
