package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.power.Debris;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Swampfire, Kraefte 2.0: Samenbomben, Rankenpeitsche, Feuerwand, Dornenrinde, Sumpfgeysir, Dschungelzorn; Kombos
 * Flammensturm, Schlammschlag, Wurzelgriff, Sumpfsturz, Selbstentzuendung; Passive Methanfaeuste (Kern-Nahkampf),
 * Methankoerper und Photosynthese. Gas kommt aus {@link SwampfireAbilities#addCloud}, Feuer zuendet es ueber
 * {@link SwampfireAbilities#igniteAt} — Kettenreaktionen inklusive.
 */
final class SwampfireMoves {
	private static final int FIRE = 0xFF7A20;
	private static final int GAS = 0x8CAD40;
	private static final int PLANT = 0x4C8C34;
	private static final int MUD = 0x5C4630;

	private SwampfireMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("seed_bombs"), SwampfireMoves::seedBombs);
		AbilityRegistry.register(KingdomOmnitrix.id("vine_whip"), SwampfireMoves::vineWhip);
		AbilityRegistry.register(KingdomOmnitrix.id("fire_wall"), SwampfireMoves::fireWall);
		AbilityRegistry.register(KingdomOmnitrix.id("thorn_bark"), SwampfireMoves::thornBark);
		AbilityRegistry.register(KingdomOmnitrix.id("swamp_geyser"), SwampfireMoves::swampGeyser);
		AbilityRegistry.register(KingdomOmnitrix.id("jungle_wrath"), SwampfireMoves::jungleWrath);
		AbilityRegistry.register(KingdomOmnitrix.id("flame_dash"), SwampfireMoves::flameDash);
		AbilityRegistry.register(KingdomOmnitrix.id("mud_slam"), SwampfireMoves::mudSlam);
		AbilityRegistry.register(KingdomOmnitrix.id("root_grab"), SwampfireMoves::rootGrab);
		AbilityRegistry.register(KingdomOmnitrix.id("swamp_crash"), SwampfireMoves::swampCrash);
		AbilityRegistry.register(KingdomOmnitrix.id("combustion"), SwampfireMoves::combustion);
		PassiveRegistry.register(KingdomOmnitrix.id("burning_fists"), new AlienPassive() {
			@Override
			public void melee(PassiveContext ctx, LivingEntity target, boolean fullSwing) {
				// brennende Faeuste: jeder Schlag setzt in Brand und zuendet Gas am Ziel
				target.setOnFireFor((float) ctx.param("fire_seconds", 3.0));
				SwampfireAbilities.igniteAt(ctx.world(), AlienKit.body(target), 0.5);
				Fx.flames(ctx.world(), AlienKit.body(target), Vec3d.ZERO, FIRE, 0.25f, 3);
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("methane_body"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Explosionen (auch die eigenen Wolken in der Naehe) richten an ihm wenig aus
				return source.isIn(DamageTypeTags.IS_EXPLOSION) ? amount * (float) ctx.param("explosion", 0.4) : amount;
			}

			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				// verletzt stoesst er Gas aus — wer ihn trifft, steht danach in einer Wolke
				ServerPlayerEntity player = ctx.player();
				if (damage >= ctx.param("min_damage", 4.0) && player.getRandom().nextFloat() < ctx.param("chance", 0.35)) {
					SwampfireAbilities.addCloud(ctx.world(), player, player.getPos().add(0, 0.8, 0), 2.0, 120, 5.0f);
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("photosynthesis"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// Pflanzenwesen: auf Gras, im Regen oder in Wasser regeneriert er langsam
				ServerPlayerEntity player = ctx.player();
				if (ctx.now() % 40 != 0) {
					return;
				}
				ServerWorld world = ctx.world();
				BlockPos below = player.getBlockPos().down();
				boolean green = world.getBlockState(below).isIn(BlockTags.DIRT) || world.getBlockState(below).isIn(BlockTags.LEAVES)
						|| world.hasRain(player.getBlockPos()) || player.isTouchingWater();
				if (green && player.getHealth() < player.getMaxHealth()) {
					player.heal((float) ctx.param("heal", 1.0));
					Fx.sparks(world, player.getPos().add(0, 0.2, 0), PLANT, 0.15f, 3);
				}
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Samenbomben: drei Samenkapseln — jede platzt zu einer Gaswolke und fesselt Gegner dort mit Ranken. */
	private static boolean seedBombs(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d side = new Vec3d(-look.z, 0.0, look.x).normalize();
		int count = (int) ctx.param("count", 3.0);
		int rootTicks = (int) ctx.param("root_ticks", 30.0);
		float damage = (float) ctx.param("damage", 3.0);
		for (int i = 0; i < count; i++) {
			double spread = (i - (count - 1) / 2.0) * 0.18;
			Vec3d dir = look.add(side.multiply(spread)).normalize();
			Debris.projectile(world, player, Blocks.MOSS_BLOCK.getDefaultState(), AlienKit.muzzle(player), dir.multiply(ctx.param("speed", 1.2)).add(0, 0.25, 0),
					0.45f, (w, at) -> {
						SwampfireAbilities.addCloud(w, player, at.add(0, 0.6, 0), 2.0, 200, 6.0f);
						for (LivingEntity target : AlienKit.around(w, player, at, 2.0)) {
							AlienKit.hit(target, w.getDamageSources().thorns(player), damage);
							SwampfireAbilities.root(w, player, target, rootTicks);
						}
						Fx.smoke(w, at, GAS, 1.0f, 4);
						w.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_ROOTS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
					});
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SNOWBALL_THROW, 1.0f, 0.5f);
		return true;
	}

	/** Rankenpeitsche: eine Ranke schlaegt 9 Bloecke weit — trifft alles auf der Linie und reisst es heran. */
	private static boolean vineWhip(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d from = AlienKit.muzzle(player);
		AlienKit.Beam beam = AlienKit.beam(world, player, ctx.param("range", 9.0));
		Vec3d to = beam.end();
		float damage = (float) ctx.param("damage", 5.0);
		Fx.beam(world, from, to, PLANT, 0.25f, 8);
		for (LivingEntity target : AlienKit.along(world, player, from, to, 0.8)) {
			AlienKit.melee(world, player, target, damage);
			Vec3d pull = player.getPos().subtract(target.getPos()).multiply(1, 0, 1);
			if (pull.lengthSquared() > 1.0E-4) {
				pull = pull.normalize().multiply(Math.min(1.4, pull.length() * 0.2));
				target.setVelocity(pull.x, 0.35, pull.z);
				target.velocityModified = true;
			}
			target.setOnFireFor(2.0f);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_CAVE_VINES_PLACE, 1.4f, 0.8f);
		return true;
	}

	/** Feuerwand: eine Methanwand quer vor ihm, sofort entzuendet — brennt 5 s, wer durchgeht, brennt mit. */
	private static boolean fireWall(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Vec3d side = new Vec3d(-look.z, 0.0, look.x);
		Vec3d center = player.getPos().add(look.multiply(ctx.param("distance", 4.0)));
		double half = ctx.param("width", 8.0) * 0.5;
		float damage = (float) ctx.param("damage", 3.0);
		List<Vec3d> points = new ArrayList<>();
		for (double d = -half; d <= half; d += 1.0) {
			points.add(AlienKit.ground(ctx.world(), center.add(side.multiply(d)).add(0, 2.0, 0), 5.0));
		}
		SwampfireAbilities.igniteAt(ctx.world(), center.add(0, 1.0, 0), half);
		PowerTasks.start(player, (int) (ctx.param("seconds", 5.0) * 20), 2, (world, p, age) -> {
			for (Vec3d at : points) {
				Fx.flames(world, at.add(0, 0.2, 0), new Vec3d(0, 0.12, 0), FIRE, 0.25f, 1);
			}
			if (age % 10 == 0) {
				Set<UUID> hit = new HashSet<>();
				for (Vec3d at : points) {
					for (LivingEntity target : AlienKit.around(world, p, at.add(0, 0.8, 0), 1.0)) {
						if (hit.add(target.getUuid())) {
							AlienKit.hit(target, world.getDamageSources().inFire(), damage);
							target.setOnFireFor(4.0f);
						}
					}
				}
				world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 1.2f, 0.8f);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_FIRECHARGE_USE, 1.2f, 0.7f);
		return true;
	}

	/** Dornenrinde: Rinde mit Dornen ueberzieht ihn fuer 8 s — Ruestung, und wer ihn beruehrt, sticht sich. */
	private static boolean thornBark(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		float damage = (float) ctx.param("damage", 2.5);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 1, false, false));
		Fx.aura(ctx.world(), player, PLANT, 1.2f, ticks);
		PowerTasks.start(player, ticks, 10, (world, p, age) -> {
			for (LivingEntity target : AlienKit.around(world, p, AlienKit.body(p), 1.8)) {
				AlienKit.hit(target, world.getDamageSources().thorns(p), damage);
				Fx.sparks(world, AlienKit.body(target), PLANT, 0.3f, 4);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ROOTS_PLACE, 1.4f, 0.6f);
		return true;
	}

	/** Sumpfgeysir: am Zielpunkt bricht der Sumpf auf — schleudert Gegner hoch und laesst eine Gaswolke zurueck. */
	private static boolean swampGeyser(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d at = AlienKit.ground(world, AlienKit.aim(world, player, ctx.param("range", 16.0)), 8.0);
		double radius = ctx.param("radius", 3.0);
		for (LivingEntity target : AlienKit.around(world, player, at.add(0, 0.5, 0), radius)) {
			AlienKit.magic(world, player, target, (float) ctx.param("damage", 5.0));
			target.setVelocity(target.getVelocity().x * 0.3, ctx.param("launch", 1.2), target.getVelocity().z * 0.3);
			target.velocityModified = true;
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1, false, true), player);
		}
		SwampfireAbilities.addCloud(world, player, at.add(0, 1.2, 0), radius, 240, (float) ctx.param("cloud_damage", 8.0));
		for (int i = 0; i < 8; i++) {
			Fx.smoke(world, at.add(0, i * 0.6, 0), MUD, 0.9f, 2);
		}
		Fx.shockwave(world, at.add(0, 0.2, 0), MUD, (float) radius, 8);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_MUD_BREAK, 1.5f, 0.5f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_SPLASH, 1.2f, 0.6f);
		return true;
	}

	/**
	 * Dschungelzorn (Ultimate): riesige Ranken brechen im Umkreis hervor — fesseln alle Gegner, stechen jede Sekunde und
	 * huellen jeden in Gas. Nach 3 s zuendet Swampfire alles auf einmal.
	 */
	private static boolean jungleWrath(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 10.0);
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		float damage = (float) ctx.param("damage", 3.0);
		List<LivingEntity> targets = AlienKit.around(world, player, player.getPos(), radius);
		for (LivingEntity target : targets) {
			SwampfireAbilities.root(world, player, target, ticks + 10);
			SwampfireAbilities.addCloud(world, player, AlienKit.body(target), 2.5, ticks + 40, (float) ctx.param("cloud_damage", 10.0));
		}
		Fx.shockwave(world, player.getPos().add(0, 0.2, 0), PLANT, (float) radius, 16);
		ctx.grantInvulnerability(20);
		PowerTasks.start(player, ticks + 1, 1, (w, p, age) -> {
			for (LivingEntity target : targets) {
				if (!target.isAlive()) {
					continue;
				}
				double a = age * 0.5;
				Fx.sparks(w, target.getPos().add(Math.cos(a) * 0.7, (age % 20) * 0.1, Math.sin(a) * 0.7), PLANT, 0.05f, 1);
				if (age % 20 == 0) {
					AlienKit.hit(target, w.getDamageSources().thorns(p), damage);
				}
			}
			if (age < ticks) {
				return true;
			}
			// Zuendung: jede Wolke um ein Ziel geht hoch, die Kette erledigt den Rest
			for (LivingEntity target : targets) {
				SwampfireAbilities.igniteAt(w, AlienKit.body(target), 1.0);
			}
			Fx.flash(w, p.getPos().add(0, 1.0, 0), FIRE, 4.0f);
			w.playSound(null, p.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.5f, 0.5f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ROOTS_BREAK, 1.6f, 0.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.8f, 1.1f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Flammensturm (Sprint + Schlag): brennender Rempler — das Ziel fliegt und steht lange in Flammen. */
	private static boolean flameDash(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0));
		target.setOnFireFor((float) ctx.param("fire_seconds", 6.0));
		target.setVelocity(dir.x * 1.4, 0.4, dir.z * 1.4);
		target.velocityModified = true;
		SwampfireAbilities.igniteAt(ctx.world(), AlienKit.body(target), 1.0);
		Fx.flames(ctx.world(), AlienKit.body(target), dir.multiply(0.2), FIRE, 0.4f, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
		return true;
	}

	/** Schlammschlag (Schlag in der Luft): Schlammfaust von oben — Ziel zu Boden, verlangsamt und von Gas umhuellt. */
	private static boolean mudSlam(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0));
		target.setVelocity(0.0, -1.0, 0.0);
		target.velocityModified = true;
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2, false, true), player);
		SwampfireAbilities.addCloud(ctx.world(), player, AlienKit.body(target), 2.0, 160, 6.0f);
		Fx.smoke(ctx.world(), AlienKit.body(target), MUD, 1.0f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_MUD_BREAK, 1.4f, 0.7f);
		return true;
	}

	/** Wurzelgriff (Schleichen + Schlag): Wurzeln wachsen aus der Faust und halten das Ziel fest. */
	private static boolean rootGrab(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.hit(target, ctx.world().getDamageSources().thorns(player), (float) ctx.param("damage", 4.0));
		SwampfireAbilities.root(ctx.world(), player, target, (int) ctx.param("root_ticks", 50.0));
		// Wurzelgriff: Ranken umschlingen das Ziel (AE „vines“)
		AttachedPropEntity.attach(ctx.world(), "vines", target, (int) ctx.param("root_ticks", 50.0));
		Fx.sparks(ctx.world(), target.getPos().add(0, 0.3, 0), PLANT, 0.3f, 12);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ROOTS_PLACE, 1.4f, 0.7f);
		return true;
	}

	/** Sumpfsturz (hoch in der Luft schleichen): stuerzt als Gasbombe herab — explodiert beim Aufschlag, staerker je hoeher. */
	private static boolean swampCrash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 7.0);
		double radius = ctx.param("radius", 3.5);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.0), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				Fx.smoke(world, p.getPos().add(0, 1.0, 0), GAS, 0.6f, 1);
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			for (LivingEntity target : AlienKit.blast(world, p, p.getPos().add(0, 0.5, 0), radius * boost, damage * boost, 0.9, 0.5)) {
				target.setOnFireFor(4.0f);
			}
			SwampfireAbilities.igniteAt(world, p.getPos().add(0, 0.8, 0), radius * boost);
			Fx.explosion(world, p.getPos().add(0, 0.5, 0), FIRE, (float) (radius * boost * 0.6));
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 0.9f);
			return false;
		});
		return true;
	}

	/** Selbstentzuendung (Schlag gegen geschwaechtes Ziel): Methan im Ziel geht hoch — grosse Stichflamme, Wolken ringsum zuenden. */
	private static boolean combustion(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d at = AlienKit.body(target);
		float damage = (float) (ctx.param("damage", 10.0) + (target.getMaxHealth() - target.getHealth()) * 0.2);
		AlienKit.melee(world, player, target, damage);
		target.setOnFireFor(8.0f);
		for (LivingEntity near : AlienKit.blast(world, player, at, ctx.param("radius", 3.5), damage * 0.4f, 0.8, 0.4)) {
			near.setOnFireFor(4.0f);
		}
		SwampfireAbilities.igniteAt(world, at, 3.0);
		for (int i = 0; i < 6; i++) {
			double a = i * MathHelper.TAU / 6;
			Fx.flames(world, at, new Vec3d(Math.cos(a) * 0.2, 0.3, Math.sin(a) * 0.2), FIRE, 0.3f, 4);
		}
		Fx.explosion(world, at, FIRE, 2.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0f, 1.1f);
		return true;
	}
}
