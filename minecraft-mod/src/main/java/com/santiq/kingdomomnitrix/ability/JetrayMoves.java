package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Jetray, Kraefte 2.0: Neuro-Salve, Sturzbomber, Schallmauer, Fassrolle, Statikfeld, Mach-Zehn; Kombos Fluegelschnitt,
 * Himmelsgriff, Schwanzstachel, Sturzflug, Totalentladung; Passive Neuro-Ladung (Ueberladungs-System), Aerodynamik
 * und Aerophibie. Alle Treffer laufen ueber {@link JetrayAbilities#shock}, laden also die Ueberladung auf.
 */
final class JetrayMoves {
	private static final int SHOCK = 0x8CFF40;
	private static final int JET = 0xF2332E;
	private static final int SKY = 0xC8E8FF;

	private JetrayMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("neuro_volley"), JetrayMoves::neuroVolley);
		AbilityRegistry.register(KingdomOmnitrix.id("dive_bomb"), JetrayMoves::diveBomb);
		AbilityRegistry.register(KingdomOmnitrix.id("sonic_barrier"), JetrayMoves::sonicBarrier);
		AbilityRegistry.register(KingdomOmnitrix.id("barrel_roll"), JetrayMoves::barrelRoll);
		AbilityRegistry.register(KingdomOmnitrix.id("static_field"), JetrayMoves::staticField);
		AbilityRegistry.register(KingdomOmnitrix.id("mach_ten"), JetrayMoves::machTen);
		AbilityRegistry.register(KingdomOmnitrix.id("wing_cut"), JetrayMoves::wingCut);
		AbilityRegistry.register(KingdomOmnitrix.id("sky_snatch"), JetrayMoves::skySnatch);
		AbilityRegistry.register(KingdomOmnitrix.id("tail_sting"), JetrayMoves::tailSting);
		AbilityRegistry.register(KingdomOmnitrix.id("power_dive"), JetrayMoves::powerDive);
		AbilityRegistry.register(KingdomOmnitrix.id("total_discharge"), JetrayMoves::totalDischarge);
		PassiveRegistry.register(KingdomOmnitrix.id("neuro_charge"), new AlienPassive() {
			@Override
			public void afterHit(PassiveContext ctx, LivingEntity target, DamageSource source, float damage) {
				// Nahkampfschlaege laden die Ueberladung ebenfalls auf (Faehigkeiten laden ueber den Neuroschock selbst)
				if (source.isOf(DamageTypes.PLAYER_ATTACK) && target.isAlive()) {
					JetrayAbilities.chargeOnly(ctx.world(), ctx.player(), target, (float) ctx.param("overload_damage", 6.0),
							(int) ctx.param("stun_ticks", 20.0));
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("aerodynamics"), new AlienPassive() {
			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				// im Flug trifft Jetray mit voller Wucht
				return airborne(ctx.player()) ? amount * (float) ctx.param("air_bonus", 1.2) : amount;
			}

			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// schwer zu treffen, wenn er fliegt: Geschosse streifen oft nur
				if (airborne(ctx.player()) && source.isIn(DamageTypeTags.IS_PROJECTILE)) {
					return amount * (float) ctx.param("projectile", 0.7);
				}
				return amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("aerophibian"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// im Wasser zu Hause: Regeneration und Kraft, solange er schwimmt
				ServerPlayerEntity player = ctx.player();
				if (ctx.now() % 20 != 0 || !player.isTouchingWater()) {
					return;
				}
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, false, false));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, (int) ctx.param("strength_level", 0.0), false, false));
			}
		});
	}

	private static boolean airborne(ServerPlayerEntity player) {
		return player.getAbilities().flying || player.isFallFlying() || !player.isOnGround() && !player.isTouchingWater();
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Neuro-Salve: fuenf schnelle Augenstrahlen in einer Sekunde, jeder auf das Ziel im Blick. */
	private static boolean neuroVolley(AbilityContext ctx) {
		float damage = (float) ctx.param("damage", 3.0);
		int stun = (int) ctx.param("stun_ticks", 15.0);
		double range = ctx.param("range", 24.0);
		int shots = (int) ctx.param("shots", 5.0);
		PowerTasks.start(ctx.player(), shots * 4, 4, (world, p, age) -> {
			AlienKit.Beam beam = AlienKit.beam(world, p, range);
			Vec3d from = AlienKit.muzzle(p);
			Fx.beam(world, from, beam.end(), SHOCK, 0.12f, 4);
			beam.target().ifPresent(target -> JetrayAbilities.shock(world, p, target, damage, stun));
			Fx.sparks(world, beam.end(), SHOCK, 0.3f, 6);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 0.5f, 2.0f);
			return true;
		});
		return true;
	}

	/** Sturzbomber: steigt kurz auf, stuerzt dann auf den Zielpunkt — Neuroschock-Ring beim Aufschlag. */
	private static boolean diveBomb(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d target = AlienKit.ground(ctx.world(), AlienKit.aim(ctx.world(), player, ctx.param("range", 20.0)), 12.0);
		float damage = (float) ctx.param("damage", 7.0);
		int stun = (int) ctx.param("stun_ticks", 25.0);
		double radius = ctx.param("radius", 4.0);
		BuiltinAbilities.launch(player, 0.0, 0.9, 0.0);
		ctx.grantInvulnerability(30);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (age < 6) {
				return true;
			}
			Vec3d to = target.subtract(p.getPos());
			if (to.length() > 1.6 && !p.isOnGround() && age < 50) {
				Vec3d v = to.normalize().multiply(1.8);
				BuiltinAbilities.launch(p, v.x, v.y, v.z);
				Fx.sparks(world, p.getPos().add(0, 0.6, 0), JET, 0.2f, 3);
				return true;
			}
			for (LivingEntity hit : AlienKit.around(world, p, p.getPos().add(0, 0.5, 0), radius)) {
				JetrayAbilities.shock(world, p, hit, damage, stun);
				AlienKit.push(hit, p.getPos(), 0.8, 0.4);
			}
			Fx.shockwave(world, p.getPos().add(0, 0.3, 0), SHOCK, (float) radius, 10);
			Fx.flash(world, p.getPos().add(0, 0.6, 0), SHOCK, 2.0f);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 1.4f);
			BuiltinAbilities.launch(p, 0.0, 0.4, 0.0);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_SWOOP, 1.0f, 0.9f);
		return true;
	}

	/** Schallmauer: durchbricht die Schallmauer — Kegel aus Ueberschallknall, Gegner fliegen weg und sind benommen. */
	private static boolean sonicBarrier(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = player.getRotationVec(1.0f);
		float damage = (float) ctx.param("damage", 6.0);
		int daze = (int) ctx.param("daze_ticks", 60.0);
		for (LivingEntity target : AlienKit.cone(world, player, ctx.param("range", 10.0), 0.55)) {
			JetrayAbilities.shock(world, player, target, damage, 0);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, daze, 0, false, true), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, daze, 1, false, true), player);
			target.setVelocity(look.x * 1.6, 0.5, look.z * 1.6);
			target.velocityModified = true;
		}
		for (int i = 1; i <= 4; i++) {
			Fx.send(world, Fx.Kind.SHOCKWAVE, player.getEyePos().add(look.multiply(i * 2.0)), look, SKY, 1.2f + i * 0.6f, 8, 1, null);
		}
		BuiltinAbilities.launch(player, -look.x * 0.4, 0.1, -look.z * 0.4);
		Fx.shake(world, player.getPos(), 0.25f, 6, 12);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.5f);
		return true;
	}

	/** Fassrolle: Ausweichrolle zur Seite, kurz unverwundbar; Geschosse in der Naehe prallen zurueck. */
	private static boolean barrelRoll(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		// Seite nach Laufrichtung: nach links, ausser der Spieler steuert gerade nach rechts
		double side = player.sidewaysSpeed < 0 ? -1.0 : 1.0;
		Vec3d dir = new Vec3d(look.z * side, 0.0, -look.x * side);
		double power = ctx.param("power", 1.6);
		BuiltinAbilities.launch(player, dir.x * power, 0.25, dir.z * power);
		ctx.grantInvulnerability((int) ctx.param("invulnerable_ticks", 15.0));
		ServerWorld world = ctx.world();
		for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, player.getBoundingBox().expand(ctx.param("radius", 5.0)),
				e -> e.getOwner() != player)) {
			projectile.setVelocity(projectile.getVelocity().multiply(-1.0));
			projectile.setOwner(player);
			projectile.velocityModified = true;
			Fx.sparks(world, projectile.getPos(), SKY, 0.3f, 4);
		}
		PowerTasks.start(player, 8, 1, (w, p, age) -> {
			double a = age * MathHelper.TAU / 8;
			Fx.smoke(w, p.getPos().add(Math.cos(a) * 0.8, 0.8 + Math.sin(a) * 0.8, Math.sin(a) * 0.8), SKY, 0.5f, 1);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_JUMP, 1.0f, 1.4f);
		return true;
	}

	/** Statikfeld: 6 s geladene Luft — jede Sekunde springt ein Funke auf jeden Gegner in der Naehe ueber. */
	private static boolean staticField(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		double radius = ctx.param("radius", 5.0);
		float damage = (float) ctx.param("damage", 2.0);
		int stun = (int) ctx.param("stun_ticks", 10.0);
		Fx.aura(ctx.world(), player, SHOCK, (float) radius * 0.4f, ticks);
		PowerTasks.start(player, ticks, 20, (world, p, age) -> {
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.9, 0), radius)) {
				Fx.beam(world, AlienKit.body(p), AlienKit.body(target), SHOCK, 0.08f, 4);
				JetrayAbilities.shock(world, p, target, damage, stun);
			}
			world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.6f, 2.0f);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_CHARGE, 1.0f, 1.6f);
		return true;
	}

	/** Mach Zehn (Ultimate): rast mit Ueberschall nacheinander durch bis zu 6 Gegner — jeder Durchflug laedt doppelt. */
	private static boolean machTen(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		List<LivingEntity> targets = new ArrayList<>(AlienKit.around(world, player, player.getPos(), ctx.param("radius", 18.0)));
		if (targets.isEmpty()) {
			return false;
		}
		targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
		int passes = Math.min(targets.size(), (int) ctx.param("passes", 6.0));
		List<LivingEntity> queue = targets.subList(0, passes);
		float damage = (float) ctx.param("damage", 8.0);
		int stun = (int) ctx.param("stun_ticks", 40.0);
		int[] index = {0};
		ctx.grantInvulnerability(passes * 8 + 20);
		Fx.flash(world, player.getPos().add(0, 1, 0), SKY, 2.4f);
		PowerTasks.start(player, passes * 8 + 4, 1, (w, p, age) -> {
			p.fallDistance = 0.0f;
			if (age % 8 != 0) {
				return true;
			}
			if (index[0] >= queue.size()) {
				Fx.shockwave(w, p.getPos().add(0, 1, 0), SKY, 6.0f, 14);
				w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.2f, 1.2f);
				return false;
			}
			LivingEntity target = queue.get(index[0]++);
			if (!target.isAlive()) {
				return true;
			}
			Vec3d from = p.getPos().add(0, 0.8, 0);
			Vec3d through = target.getPos().subtract(p.getPos()).multiply(1, 0, 1);
			Vec3d dir = through.lengthSquared() < 1.0E-4 ? BuiltinAbilities.horizontalLook(p) : through.normalize();
			Vec3d exit = target.getPos().add(dir.multiply(2.0));
			p.requestTeleport(exit.x, Math.max(exit.y, target.getY()) + 0.2, exit.z);
			Fx.beam(w, from, p.getPos().add(0, 0.8, 0), JET, 0.3f, 8);
			JetrayAbilities.shock(w, p, target, damage, stun);
			if (target.isAlive()) {
				JetrayAbilities.chargeOnly(w, p, target, damage, stun);
			}
			Fx.send(w, Fx.Kind.SHOCKWAVE, AlienKit.body(target), dir, SKY, 2.0f, 6, 1, null);
			w.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS, 1.0f, 1.5f);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.8f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Fluegelschnitt (Sprint + Schlag): Fluegelkante im Vorbeiflug — Schnitt, Ziel dreht sich zur Seite weg. */
	private static boolean wingCut(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0));
		JetrayAbilities.chargeOnly(ctx.world(), player, target, (float) ctx.param("damage", 6.0), 20);
		target.setVelocity(-look.z * 1.0, 0.3, look.x * 1.0);
		target.velocityModified = true;
		BuiltinAbilities.launch(player, look.x * 1.0, 0.2, look.z * 1.0);
		Fx.send(ctx.world(), Fx.Kind.SHOCKWAVE, AlienKit.body(target), look, JET, 1.4f, 6, 1, null);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.4f);
		return true;
	}

	/** Himmelsgriff (Schlag in der Luft): packt das Ziel und reisst es mit nach oben — dann faellt es. */
	private static boolean skySnatch(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 4.0));
		double lift = ctx.param("lift", 1.3);
		BuiltinAbilities.launch(player, 0.0, lift, 0.0);
		target.setVelocity(0.0, lift, 0.0);
		target.velocityModified = true;
		PowerTasks.start(player, 10, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (target.isAlive() && age < 9) {
				target.setVelocity(p.getVelocity().x, Math.max(target.getVelocity().y, p.getVelocity().y), p.getVelocity().z);
				target.velocityModified = true;
				Fx.sparks(world, AlienKit.body(target), JET, 0.1f, 1);
			}
			return true;
		}, (p, aborted) -> {
			if (target.isAlive()) {
				target.setVelocity(0.0, -0.8, 0.0);
				target.velocityModified = true;
			}
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_FLAP, 1.2f, 0.8f);
		return true;
	}

	/** Schwanzstachel (Schleichen + Schlag): Schwanzspitze direkt aufs Ziel — lange Laehmung. */
	private static boolean tailSting(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		JetrayAbilities.shock(ctx.world(), player, target, (float) ctx.param("damage", 5.0), (int) ctx.param("stun_ticks", 50.0));
		Fx.beam(ctx.world(), player.getPos().add(0, player.getHeight() * 0.9, 0), AlienKit.body(target), SHOCK, 0.1f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BEE_STING, 1.0f, 0.8f);
		return true;
	}

	/** Sturzflug (hoch in der Luft schleichen): steiler Sturz, Neuroschock-Explosion beim Aufschlag nach Hoehe. */
	private static boolean powerDive(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 7.0);
		double radius = ctx.param("radius", 3.5);
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		BuiltinAbilities.launch(player, look.x * 0.5, -ctx.param("dive", 2.2), look.z * 0.5);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				Fx.sparks(world, p.getPos().add(0, 1.0, 0), JET, 0.2f, 2);
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			for (LivingEntity hit : AlienKit.around(world, p, p.getPos().add(0, 0.5, 0), radius * boost)) {
				JetrayAbilities.shock(world, p, hit, damage * boost, 20);
				AlienKit.push(hit, p.getPos(), 0.8, 0.5);
			}
			Fx.shockwave(world, p.getPos().add(0, 0.3, 0), SHOCK, (float) (radius * boost), 10);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 1.2f);
			return false;
		});
		return true;
	}

	/** Totalentladung (Schlag gegen geschwaechtes Ziel): sofortige Ueberladung, Ladungen springen auf alle in der Naehe. */
	private static boolean totalDischarge(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float damage = (float) ctx.param("damage", 10.0);
		Set<UUID> chained = new HashSet<>();
		Vec3d at = AlienKit.body(target);
		JetrayAbilities.forceOverload(world, player, target, damage, 40);
		chained.add(target.getUuid());
		for (LivingEntity near : AlienKit.around(world, player, at, ctx.param("radius", 6.0))) {
			if (chained.add(near.getUuid())) {
				Fx.beam(world, at, AlienKit.body(near), SHOCK, 0.1f, 6);
				JetrayAbilities.chargeOnly(world, player, near, damage * 0.5f, 30);
			}
		}
		Fx.shake(world, target.getPos(), 0.3f, 8, 16);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
		return true;
	}
}
