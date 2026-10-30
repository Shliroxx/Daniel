package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Atomix, Kraefte 2.0: Protonensalve, Gammastoss, Atombarriere, Kernschmelze, Kettenreaktion, Fusionssonne; Kombos
 * Atomschlag, Fallout, Isotopengriff, Kernsturz, Kritische Masse; Passive Radioaktive Beruehrung (Kern-Nahkampf),
 * Reaktorkern und Strahlenschutz. Strahlung, Spaltung und Schaden kommen aus {@link AtomixAbilities}.
 */
final class AtomixMoves {
	private static final int GREEN = 0x40FF66;
	private static final int YELLOW = 0xD8FF4C;
	private static final int HOT = 0xFFB060;

	private AtomixMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("proton_volley"), AtomixMoves::protonVolley);
		AbilityRegistry.register(KingdomOmnitrix.id("gamma_burst"), AtomixMoves::gammaBurst);
		AbilityRegistry.register(KingdomOmnitrix.id("atomic_barrier"), AtomixMoves::atomicBarrier);
		AbilityRegistry.register(KingdomOmnitrix.id("meltdown_zone"), AtomixMoves::meltdownZone);
		AbilityRegistry.register(KingdomOmnitrix.id("chain_reaction"), AtomixMoves::chainReaction);
		AbilityRegistry.register(KingdomOmnitrix.id("fusion_sun"), AtomixMoves::fusionSun);
		AbilityRegistry.register(KingdomOmnitrix.id("atom_smash"), AtomixMoves::atomSmash);
		AbilityRegistry.register(KingdomOmnitrix.id("fallout_strike"), AtomixMoves::falloutStrike);
		AbilityRegistry.register(KingdomOmnitrix.id("isotope_grip"), AtomixMoves::isotopeGrip);
		AbilityRegistry.register(KingdomOmnitrix.id("nuclear_dive"), AtomixMoves::nuclearDive);
		AbilityRegistry.register(KingdomOmnitrix.id("critical_mass"), AtomixMoves::criticalMass);
		PassiveRegistry.register(KingdomOmnitrix.id("radioactive_touch"), new AlienPassive() {
			@Override
			public void afterHit(PassiveContext ctx, LivingEntity target, DamageSource source, float damage) {
				// jeder direkte Treffer verstrahlt das Ziel um eine Stufe
				if (!AtomixAbilities.bonusHit && source.getSource() == ctx.player()) {
					AtomixAbilities.irradiate(ctx.player(), target, (int) ctx.param("levels", 1.0));
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("reactor_core"), new AlienPassive() {
			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				// je verstrahlter das Ziel, desto haerter trifft Atomix
				return amount * (1.0f + (float) ctx.param("per_level", 0.1) * AtomixAbilities.radiation(target));
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("radiation_shield"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// Gift und Verdorren zersetzt sein Kern sofort
				if (ctx.now() % 10 == 0) {
					ctx.player().removeStatusEffect(StatusEffects.POISON);
					ctx.player().removeStatusEffect(StatusEffects.WITHER);
				}
			}

			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				return source.isIn(DamageTypeTags.IS_FIRE) || source.isIn(DamageTypeTags.IS_EXPLOSION)
						? amount * (float) ctx.param("reduction", 0.7) : amount;
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Protonensalve: drei schnelle Protonenschuesse auf das Ziel im Blick, jeder verstrahlt um eine Stufe. */
	private static boolean protonVolley(AbilityContext ctx) {
		float damage = (float) ctx.param("damage", 3.0);
		double range = ctx.param("range", 22.0);
		int shots = (int) ctx.param("shots", 3.0);
		PowerTasks.start(ctx.player(), shots * 5, 5, (world, p, age) -> {
			AlienKit.Beam beam = AlienKit.beam(world, p, range);
			Fx.beam(world, AlienKit.muzzle(p), beam.end(), GREEN, 0.18f, 5);
			Fx.flash(world, beam.end(), GREEN, 0.8f);
			beam.target().ifPresent(target -> {
				AtomixAbilities.hit(world, p, target, damage);
				AtomixAbilities.irradiate(p, target, 1);
			});
			world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.6f, 1.8f);
			return true;
		});
		return true;
	}

	/** Gammastoss: Kegel aus Gammastrahlen — Schaden und +2 Strahlung fuer alle darin. */
	private static boolean gammaBurst(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = player.getRotationVec(1.0f);
		for (LivingEntity target : AlienKit.cone(world, player, ctx.param("range", 9.0), 0.6)) {
			AtomixAbilities.hit(world, player, target, (float) ctx.param("damage", 5.0));
			AtomixAbilities.irradiate(player, target, (int) ctx.param("levels", 2.0));
		}
		for (int i = 1; i <= 4; i++) {
			Fx.send(world, Fx.Kind.SHOCKWAVE, player.getEyePos().add(look.multiply(i * 2.0)), look, GREEN, 1.0f + i * 0.6f, 8, 1, null);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.6f);
		return true;
	}

	/** Atombarriere: Energiehuelle fuer 6 s — Extra-Leben, Geschosse zerfallen an der Huelle. */
	private static boolean atomicBarrier(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, ticks, (int) ctx.param("absorption", 2.0), false, false));
		Fx.aura(ctx.world(), player, GREEN, 1.4f, ticks);
		PowerTasks.start(player, ticks, 2, (world, p, age) -> {
			for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, p.getBoundingBox().expand(2.0), e -> e.getOwner() != p)) {
				Fx.flash(world, projectile.getPos(), GREEN, 0.6f);
				projectile.discard();
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
		return true;
	}

	/** Kernschmelze: verstrahlter Boden am Zielpunkt — 6 s lang jede Sekunde Schaden und Strahlung fuer alle darauf. */
	private static boolean meltdownZone(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d center = AlienKit.ground(world, AlienKit.aim(world, player, ctx.param("range", 16.0)), 8.0);
		double radius = ctx.param("radius", 4.0);
		float damage = (float) ctx.param("damage", 2.0);
		Fx.scorch(world, center, (float) radius, (int) (ctx.param("seconds", 6.0) * 20));
		PowerTasks.start(player, (int) (ctx.param("seconds", 6.0) * 20), 5, (w, p, age) -> {
			for (int i = 0; i < 3; i++) {
				double a = w.random.nextDouble() * MathHelper.TAU;
				double r = w.random.nextDouble() * radius;
				Fx.sparks(w, center.add(Math.cos(a) * r, 0.2, Math.sin(a) * r), GREEN, 0.08f, 1);
			}
			if (age % 20 == 0) {
				for (LivingEntity target : AlienKit.around(w, p, center.add(0, 0.5, 0), radius)) {
					AtomixAbilities.hit(w, p, target, damage);
					AtomixAbilities.irradiate(p, target, 1);
				}
				w.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_LAVA_POP, SoundCategory.PLAYERS, 1.0f, 0.6f);
			}
			return true;
		});
		Fx.shockwave(world, center.add(0, 0.2, 0), GREEN, (float) radius, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.2f, 0.8f);
		return true;
	}

	/** Kettenreaktion: jedes verstrahlte Ziel im Umkreis spaltet sich sofort — Explosion je Stufe, Strahlung springt weiter. */
	private static boolean chainReaction(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int count = 0;
		for (LivingEntity target : AlienKit.around(world, player, player.getPos(), ctx.param("radius", 16.0))) {
			int level = AtomixAbilities.consume(target);
			if (level <= 0) {
				continue;
			}
			Vec3d at = AlienKit.body(target);
			for (LivingEntity near : AlienKit.around(world, player, at, ctx.param("blast_radius", 3.0))) {
				AtomixAbilities.hit(world, player, near, (float) ctx.param("per_level", 2.5) * level);
			}
			if (level >= 2) {
				AtomixAbilities.fission(world, player, target);
			}
			Fx.explosion(world, at, HOT, 0.6f + level * 0.3f);
			count++;
		}
		if (count == 0) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.atomix_nothing").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0f, 1.3f);
		return true;
	}

	/**
	 * Fusionssonne (Ultimate): eine kleine Sonne ueber dem Zielpunkt brennt 6 s lang — zieht Gegner an, verbrennt und
	 * verstrahlt sie jede Sekunde; zum Schluss detoniert sie.
	 */
	private static boolean fusionSun(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d sun = AlienKit.ground(world, AlienKit.aim(world, player, ctx.param("range", 24.0)), 10.0).add(0, 4.0, 0);
		double radius = ctx.param("radius", 8.0);
		float damage = (float) ctx.param("damage", 3.0);
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		ctx.grantInvulnerability(20);
		PowerTasks.start(player, ticks + 1, 2, (w, p, age) -> {
			Fx.flames(w, sun, Vec3d.ZERO, YELLOW, 0.6f, 4);
			Fx.sparks(w, sun, GREEN, 0.3f, 3);
			if (age % 10 == 0) {
				Fx.flash(w, sun, YELLOW, 3.0f);
			}
			if (age % 20 == 0 && age < ticks) {
				for (LivingEntity target : AlienKit.around(w, p, sun, radius)) {
					Vec3d pull = sun.subtract(target.getPos());
					if (pull.lengthSquared() > 1.0) {
						pull = pull.normalize().multiply(0.4);
						target.addVelocity(pull.x, pull.y * 0.5, pull.z);
						target.velocityModified = true;
					}
					AtomixAbilities.hit(w, p, target, damage);
					AtomixAbilities.irradiate(p, target, 1);
					target.setOnFireFor(3.0f);
				}
				w.playSound(null, BlockPos.ofFloored(sun), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 1.5f, 0.5f);
			}
			if (age < ticks) {
				return true;
			}
			AlienKit.blast(w, p, sun, radius, (float) ctx.param("blast_damage", 16.0), 1.4, 0.6);
			Fx.explosion(w, sun, HOT, (float) radius * 0.6f);
			Fx.flash(w, sun, 0xFFFFFF, 7.0f);
			Fx.shake(w, sun, 0.8f, 16, (int) radius * 3);
			w.playSound(null, BlockPos.ofFloored(sun), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0f, 0.6f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.5f, 0.6f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Atomschlag (Sprint + Schlag): Faust mit Kernenergie — Ziel fliegt und bekommt +2 Strahlung. */
	private static boolean atomSmash(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0));
		AtomixAbilities.irradiate(player, target, 2);
		target.setVelocity(dir.x * 1.4, 0.5, dir.z * 1.4);
		target.velocityModified = true;
		Fx.flash(ctx.world(), AlienKit.body(target), GREEN, 1.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 1.2f);
		return true;
	}

	/** Fallout (Schlag in der Luft): Schlag von oben — Ziel zu Boden, Strahlung im kleinen Umkreis. */
	private static boolean falloutStrike(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		AlienKit.melee(world, player, target, (float) ctx.param("damage", 6.0));
		target.setVelocity(0.0, -1.0, 0.0);
		target.velocityModified = true;
		for (LivingEntity near : AlienKit.around(world, player, target.getPos().add(0, 0.5, 0), 2.5)) {
			AtomixAbilities.irradiate(player, near, 1);
		}
		Fx.shockwave(world, target.getPos().add(0, 0.2, 0), GREEN, 2.5f, 8);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), 1.0f, 1.2f);
		return true;
	}

	/** Isotopengriff (Schleichen + Schlag): pumpt Strahlung ins Ziel — +3 Stufen, es wird schwach. */
	private static boolean isotopeGrip(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 3.0));
		AtomixAbilities.irradiate(player, target, (int) ctx.param("levels", 3.0));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1, false, true), player);
		Fx.aura(ctx.world(), target, GREEN, 0.8f, 40);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.8f);
		return true;
	}

	/** Kernsturz (hoch in der Luft schleichen): Sturz mit Kernexplosion beim Aufschlag — staerker je hoeher. */
	private static boolean nuclearDive(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 7.0);
		double radius = ctx.param("radius", 3.5);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.2), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				Fx.sparks(world, p.getPos().add(0, 1.0, 0), GREEN, 0.15f, 2);
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.5, 0), radius * boost)) {
				AtomixAbilities.hit(world, p, target, damage * boost);
				AtomixAbilities.irradiate(p, target, 1);
				AlienKit.push(target, p.getPos(), 0.8, 0.5);
			}
			Fx.explosion(world, p.getPos().add(0, 0.5, 0), HOT, (float) (radius * boost * 0.6));
			Fx.shockwave(world, p.getPos().add(0, 0.2, 0), GREEN, (float) (radius * boost), 10);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 1.0f);
			return false;
		});
		return true;
	}

	/** Kritische Masse (Schlag gegen geschwaechtes Ziel): bringt den Kern zum Platzen — Schaden je Stufe, Spaltung springt weiter. */
	private static boolean criticalMass(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int level = Math.max(1, AtomixAbilities.consume(target));
		float damage = (float) (ctx.param("damage", 8.0) + level * ctx.param("per_level", 3.0));
		AlienKit.melee(world, player, target, damage);
		AtomixAbilities.fission(world, player, target);
		List<LivingEntity> near = AlienKit.around(world, player, AlienKit.body(target), 4.0);
		for (LivingEntity other : near) {
			if (other != target) {
				AtomixAbilities.hit(world, player, other, damage * 0.3f);
			}
		}
		Fx.explosion(world, AlienKit.body(target), HOT, 1.6f);
		Fx.shake(world, target.getPos(), 0.3f, 8, 16);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0f, 1.4f);
		return true;
	}
}
