package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.power.Debris;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Goop, Kraefte 2.0: Saeuresalve, Schleimfalle, Schleimstacheln, Huepfblob, Masse-Neubildung, Saeure-Tsunami; Kombos
 * Schleimrutsche, Schleimkappe, Saeuregriff, Platsch, Totale Zersetzung; Passive Aetzende Beruehrung (Kern-Nahkampf),
 * Schleimkoerper und Antigrav-Projektor. Saeure kommt aus {@link GoopAbilities#corrode}.
 */
final class GoopMoves {
	private static final int SLIME = 0x5FD13A;
	private static final int ACID = 0xB6FF3A;

	private GoopMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("acid_volley"), GoopMoves::acidVolley);
		AbilityRegistry.register(KingdomOmnitrix.id("goo_trap"), GoopMoves::gooTrap);
		AbilityRegistry.register(KingdomOmnitrix.id("slime_spikes"), GoopMoves::slimeSpikes);
		AbilityRegistry.register(KingdomOmnitrix.id("bounce_blob"), GoopMoves::bounceBlob);
		AbilityRegistry.register(KingdomOmnitrix.id("regenerate_mass"), GoopMoves::regenerateMass);
		AbilityRegistry.register(KingdomOmnitrix.id("acid_tsunami"), GoopMoves::acidTsunami);
		AbilityRegistry.register(KingdomOmnitrix.id("slime_slide"), GoopMoves::slimeSlide);
		AbilityRegistry.register(KingdomOmnitrix.id("goo_drop"), GoopMoves::gooDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("acid_grip"), GoopMoves::acidGrip);
		AbilityRegistry.register(KingdomOmnitrix.id("splat"), GoopMoves::splat);
		AbilityRegistry.register(KingdomOmnitrix.id("total_corrosion"), GoopMoves::totalCorrosion);
		PassiveRegistry.register(KingdomOmnitrix.id("corrosive_touch"), new AlienPassive() {
			@Override
			public void afterHit(PassiveContext ctx, LivingEntity target, DamageSource source, float damage) {
				// jeder direkte Treffer hinterlaesst Saeure
				if (!AlienKit.bonusHit && source.getSource() == ctx.player()) {
					GoopAbilities.corrode(ctx.player(), target, (int) ctx.param("levels", 1.0));
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("slime_body"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Schlaege versinken im Schleim und verlieren Wucht
				return source.getAttacker() != null && source.getSource() == source.getAttacker() ? amount * (float) ctx.param("melee", 0.8) : amount;
			}

			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				// wer in Goop hineinschlaegt, bekommt Saeure an die Haende
				if (source.getAttacker() instanceof LivingEntity attacker && source.getSource() == attacker && attacker != ctx.player()) {
					GoopAbilities.corrode(ctx.player(), attacker, (int) ctx.param("levels", 1.0));
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("anti_gravity_projector"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// der Projektor traegt ihn: hoehere Spruenge, beim Schleichen in der Luft schwebt er langsam
				ServerPlayerEntity goop = ctx.player();
				if (ctx.now() % 20 == 0) {
					goop.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, (int) ctx.param("jump_level", 1.0), false, false));
				}
				if (goop.isSneaking() && !goop.isOnGround() && goop.getVelocity().y < -0.1) {
					goop.setVelocity(goop.getVelocity().x, -0.1, goop.getVelocity().z);
					goop.velocityModified = true;
					goop.fallDistance = 0.0f;
				}
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Saeuresalve: vier Saeureklumpen im Bogen — wo sie aufschlagen, aetzt es. */
	private static boolean acidVolley(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = goop.getRotationVec(1.0f);
		Vec3d side = new Vec3d(-look.z, 0.0, look.x).normalize();
		float damage = (float) ctx.param("damage", 2.0);
		int count = (int) ctx.param("count", 4.0);
		for (int i = 0; i < count; i++) {
			double spread = (i - (count - 1) / 2.0) * 0.12;
			Vec3d dir = look.add(side.multiply(spread)).normalize();
			Debris.projectile(world, goop, Blocks.SLIME_BLOCK.getDefaultState(), AlienKit.muzzle(goop), dir.multiply(ctx.param("speed", 1.3)).add(0, 0.3, 0),
					0.4f, (w, at) -> {
						for (LivingEntity target : AlienKit.around(w, goop, at, 1.6)) {
							AlienKit.magic(w, goop, target, damage);
							GoopAbilities.corrode(goop, target, 1);
						}
						Fx.sparks(w, at, ACID, 0.3f, 8);
						w.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.PLAYERS, 0.8f, 1.2f);
					});
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_ATTACK, 1.0f, 0.8f);
		return true;
	}

	/** Schleimfalle: klebriger Schleim am Zielpunkt (6 s) — wer drin steht, kommt kaum vom Fleck und aetzt langsam. */
	private static boolean gooTrap(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d center = AlienKit.ground(world, AlienKit.aim(world, goop, ctx.param("range", 14.0)), 8.0);
		double radius = ctx.param("radius", 3.0);
		PowerTasks.start(goop, (int) (ctx.param("seconds", 6.0) * 20), 5, (w, p, age) -> {
			for (int i = 0; i < 3; i++) {
				double a = w.random.nextDouble() * MathHelper.TAU;
				double r = w.random.nextDouble() * radius;
				Fx.sparks(w, center.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), SLIME, 0.02f, 1);
			}
			for (LivingEntity target : AlienKit.around(w, p, center.add(0, 0.5, 0), radius)) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 4, false, true), p);
				if (age % 40 == 0) {
					GoopAbilities.corrode(p, target, 1);
				}
			}
			return true;
		});
		Fx.shockwave(world, center.add(0, 0.1, 0), SLIME, (float) radius, 8);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_HONEY_BLOCK_PLACE, 1.4f, 0.6f);
		return true;
	}

	/** Schleimstacheln: Goop formt Stacheln in alle Richtungen — Treffer und Saeure ringsum. */
	private static boolean slimeSpikes(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 4.5);
		float damage = (float) ctx.param("damage", 5.0);
		for (LivingEntity target : AlienKit.around(world, goop, AlienKit.body(goop), radius)) {
			AlienKit.melee(world, goop, target, damage);
			GoopAbilities.corrode(goop, target, 1);
			AlienKit.push(target, goop.getPos(), 0.6, 0.2);
		}
		for (int i = 0; i < 12; i++) {
			double a = i * MathHelper.TAU / 12;
			Fx.beam(world, AlienKit.body(goop), AlienKit.body(goop).add(Math.cos(a) * radius, 0, Math.sin(a) * radius), SLIME, 0.12f, 6);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_SLIME_BLOCK_PLACE, 1.4f, 0.6f);
		return true;
	}

	/** Huepfblob: wird zum Gummiball — gewaltiger Satz, der Aufprall spritzt Saeure ringsum. */
	private static boolean bounceBlob(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(goop);
		BuiltinAbilities.launch(goop, look.x * ctx.param("power", 1.2), ctx.param("lift", 1.3), look.z * ctx.param("power", 1.2));
		float damage = (float) ctx.param("damage", 4.0);
		PowerTasks.start(goop, 80, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (age < 4 || !p.isOnGround()) {
				return true;
			}
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.5, 0), 3.0)) {
				AlienKit.magic(world, p, target, damage);
				GoopAbilities.corrode(p, target, 1);
			}
			Fx.shockwave(world, p.getPos().add(0, 0.1, 0), SLIME, 3.0f, 8);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.4f, 0.6f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_JUMP, 1.4f, 0.8f);
		return true;
	}

	/** Masse-Neubildung: Goop formt sich neu — heilt 30 %, loest schaedliche Effekte, verspritzt dabei Saeure. */
	private static boolean regenerateMass(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		ServerWorld world = ctx.world();
		goop.heal(goop.getMaxHealth() * (float) ctx.param("heal", 0.3));
		goop.getStatusEffects().stream().filter(e -> e.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL)
				.map(StatusEffectInstance::getEffectType).toList().forEach(goop::removeStatusEffect);
		for (LivingEntity target : AlienKit.around(world, goop, goop.getPos(), ctx.param("radius", 3.0))) {
			GoopAbilities.corrode(goop, target, 1);
		}
		Fx.sparks(world, AlienKit.body(goop), SLIME, 0.4f, 20);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_SQUISH, 1.4f, 1.2f);
		return true;
	}

	/** Saeure-Tsunami (Ultimate): eine Welle aus Saeure rollt 20 Bloecke nach vorn — reisst mit und zersetzt alles. */
	private static boolean acidTsunami(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(goop);
		Vec3d side = new Vec3d(-dir.z, 0.0, dir.x);
		Vec3d origin = goop.getPos();
		int ticks = (int) (ctx.param("seconds", 2.0) * 20);
		double length = ctx.param("length", 20.0);
		double half = ctx.param("width", 6.0) * 0.5;
		float damage = (float) ctx.param("damage", 6.0);
		Set<UUID> hit = new HashSet<>();
		ctx.grantInvulnerability(20);
		PowerTasks.start(goop, ticks, 1, (world, p, age) -> {
			Vec3d front = origin.add(dir.multiply(length * age / ticks));
			for (double d = -half; d <= half; d += 1.0) {
				Vec3d at = front.add(side.multiply(d));
				Fx.sparks(world, at.add(0, 0.6 + world.random.nextDouble() * 1.4, 0), age % 2 == 0 ? SLIME : ACID, 0.1f, 1);
			}
			for (LivingEntity target : AlienKit.around(world, p, front.add(0, 1.0, 0), half + 0.5)) {
				target.setVelocity(dir.x * 0.9, 0.25, dir.z * 0.9);
				target.velocityModified = true;
				if (hit.add(target.getUuid())) {
					AlienKit.magic(world, p, target, damage);
					GoopAbilities.corrode(p, target, (int) ctx.param("levels", 3.0));
				}
			}
			if (age % 5 == 0) {
				world.playSound(null, BlockPos.ofFloored(front), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.2f, 0.6f);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_DEATH, 1.6f, 0.4f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Schleimrutsche (Sprint + Schlag): rutscht durch das Ziel hindurch — +2 Saeure, es wird ausgehebelt. */
	private static boolean slimeSlide(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity goop = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(goop);
		AlienKit.melee(ctx.world(), goop, target, (float) ctx.param("damage", 4.0));
		GoopAbilities.corrode(goop, target, 2);
		target.setVelocity(-dir.x * 0.3, 0.7, -dir.z * 0.3);
		target.velocityModified = true;
		BuiltinAbilities.launch(goop, dir.x * 1.0, 0.0, dir.z * 1.0);
		Fx.sparks(ctx.world(), target.getPos(), SLIME, 0.3f, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_SQUISH, 1.2f, 1.0f);
		return true;
	}

	/** Schleimkappe (Schlag in der Luft): landet auf dem Kopf des Ziels — es sieht nichts mehr und aetzt. */
	private static boolean gooDrop(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity goop = ctx.player();
		AlienKit.melee(ctx.world(), goop, target, (float) ctx.param("damage", 4.0));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, true), goop);
		GoopAbilities.corrode(goop, target, 1);
		Fx.sparks(ctx.world(), target.getEyePos(), SLIME, 0.2f, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_SQUISH, 1.2f, 0.7f);
		return true;
	}

	/** Saeuregriff (Schleichen + Schlag): die Hand zerfliesst um das Ziel — +3 Saeure auf einen Schlag. */
	private static boolean acidGrip(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity goop = ctx.player();
		AlienKit.melee(ctx.world(), goop, target, (float) ctx.param("damage", 2.0));
		GoopAbilities.corrode(goop, target, (int) ctx.param("levels", 3.0));
		AlienKit.hold(ctx.world(), target, 20, GoopAbilities.SLIME, false);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, 1.4f, 0.6f);
		return true;
	}

	/** Platsch (hoch in der Luft schleichen): klatscht flach auf — Saeurespritzer im Umkreis nach Fallhoehe. */
	private static boolean splat(AbilityContext ctx) {
		ServerPlayerEntity goop = ctx.player();
		double startY = goop.getY() + goop.fallDistance;
		float damage = (float) ctx.param("damage", 5.0);
		double radius = ctx.param("radius", 3.5);
		BuiltinAbilities.launch(goop, goop.getVelocity().x * 0.2, -ctx.param("dive", 2.0), goop.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(goop, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.5, 0), radius * boost)) {
				AlienKit.magic(world, p, target, damage * boost);
				GoopAbilities.corrode(p, target, 2);
			}
			Fx.shockwave(world, p.getPos().add(0, 0.1, 0), SLIME, (float) (radius * boost), 10);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.6f, 0.4f);
			return false;
		});
		return true;
	}

	/** Totale Zersetzung (Schlag gegen geschwaechtes Ziel): verbraucht alle Saeure im Ziel — Schaden je Stufe, Spritzer ringsum. */
	private static boolean totalCorrosion(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity goop = ctx.player();
		ServerWorld world = ctx.world();
		int level = GoopAbilities.consumeAcid(target);
		float damage = (float) (ctx.param("damage", 8.0) + level * ctx.param("per_level", 4.0));
		AlienKit.magic(world, goop, target, damage);
		for (LivingEntity near : AlienKit.around(world, goop, target.getPos(), 3.5)) {
			if (near != target) {
				GoopAbilities.corrode(goop, near, Math.max(1, level / 2));
			}
		}
		Fx.sparks(world, AlienKit.body(target), ACID, 0.5f, 20);
		Fx.smoke(world, AlienKit.body(target), ACID, 1.0f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_LAVA_EXTINGUISH, 1.2f, 1.2f);
		return true;
	}
}
