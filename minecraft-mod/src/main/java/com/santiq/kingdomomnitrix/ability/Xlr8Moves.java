package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * XLR8, Kraefte 2.0: Schwanzpeitsche, Blitz-Entwaffnung, Sogspur, Reibungsfeuer, Phantom-Hiebe, Mach-Sprint; Kombos
 * Ueberschallstoss, Sprungtritt, Fussfeger, Sturzflug, Hundert Schlaege; Passive Reflexe (Tempo und Tempo-Lauf sitzen in
 * {@link Xlr8Abilities}). Alles skaliert mit dem Tempo, die Schallmauer verstaerkt die naechste Kraft.
 */
final class Xlr8Moves {
	private static final int STREAK = Xlr8Abilities.STREAK;
	private static final int CORE = Xlr8Abilities.CORE;
	private static final int HEAT = 0xFF8A30;

	private Xlr8Moves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("tail_whip"), Xlr8Moves::tailWhip);
		AbilityRegistry.register(KingdomOmnitrix.id("rapid_disarm"), Xlr8Moves::rapidDisarm);
		AbilityRegistry.register(KingdomOmnitrix.id("vacuum_trail"), Xlr8Moves::vacuumTrail);
		AbilityRegistry.register(KingdomOmnitrix.id("friction_fire"), Xlr8Moves::frictionFire);
		AbilityRegistry.register(KingdomOmnitrix.id("phantom_strikes"), Xlr8Moves::phantomStrikes);
		AbilityRegistry.register(KingdomOmnitrix.id("mach_sprint"), Xlr8Moves::machSprint);
		AbilityRegistry.register(KingdomOmnitrix.id("supersonic_shove"), Xlr8Moves::supersonicShove);
		AbilityRegistry.register(KingdomOmnitrix.id("blitz_kick"), Xlr8Moves::blitzKick);
		AbilityRegistry.register(KingdomOmnitrix.id("leg_sweep"), Xlr8Moves::legSweep);
		AbilityRegistry.register(KingdomOmnitrix.id("speed_dive"), Xlr8Moves::speedDive);
		AbilityRegistry.register(KingdomOmnitrix.id("hundred_blows"), Xlr8Moves::hundredBlows);
		PassiveRegistry.register(KingdomOmnitrix.id("quick_reflexes"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// verschwommen weicht XLR8 Schlaegen und Geschossen manchmal einfach aus
				boolean dodgeable = source.getAttacker() != null || source.isIn(DamageTypeTags.IS_PROJECTILE);
				if (dodgeable && Xlr8Abilities.tempo(ctx.player()) >= Xlr8Abilities.BLUR
						&& ctx.world().random.nextDouble() < ctx.param("chance", 0.2)) {
					ServerPlayerEntity player = ctx.player();
					Fx.flash(ctx.world(), player.getPos().add(0, 1.0, 0), STREAK, 1.2f);
					Fx.beam(ctx.world(), player.getPos().add(0, 1.0, 0), player.getPos().add(0, 1.0, 0)
							.add(BuiltinAbilities.horizontalLook(player).multiply(-1.5)), STREAK, 0.25f, 5);
					ctx.world().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.8f, 1.8f);
					return 0.0f;
				}
				return amount;
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Schwanzpeitsche: blitzschnelle Drehung, der Schwanz trifft alles ringsum, wirft weg und verlangsamt. */
	private static boolean tailWhip(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Xlr8Abilities.Boost boost = Xlr8Abilities.boost(player);
		double radius = ctx.param("radius", 3.5) * (boost.full() ? 1.5 : 1.0);
		float damage = (float) ctx.param("damage", 5.0) * boost.factor();
		for (LivingEntity target : AlienKit.blast(ctx.world(), player, player.getPos().add(0, 0.8, 0), radius, damage, 1.2, 0.25)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1), player);
		}
		ServerWorld world = ctx.world();
		for (int i = 0; i < 12; i++) {
			double a = i * MathHelper.TAU / 12;
			Vec3d from = player.getPos().add(Math.cos(a) * radius * 0.4, 0.6, Math.sin(a) * radius * 0.4);
			Vec3d to = player.getPos().add(Math.cos(a + 0.5) * radius, 0.5, Math.sin(a + 0.5) * radius);
			Fx.beam(world, from, to, STREAK, 0.12f, 5);
		}
		Fx.shockwave(world, player.getPos().add(0, 0.2, 0), CORE, (float) radius, 8);
		Xlr8Abilities.addTempo(player, 6.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 1.4f);
		return true;
	}

	/**
	 * Blitz-Entwaffnung: XLR8 rast an allen Gegnern im Umkreis vorbei und nimmt ihnen die Waffen ab — wie in der Serie,
	 * bevor es jemand merkt. Entwaffnete sind kurz geschwaecht.
	 */
	private static boolean rapidDisarm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 10.0);
		Vec3d home = player.getPos();
		int taken = 0;
		Vec3d last = home.add(0, 1.0, 0);
		for (LivingEntity target : AlienKit.around(world, player, home, radius)) {
			Vec3d at = AlienKit.body(target);
			Fx.beam(world, last, at, STREAK, 0.15f, 8);
			last = at;
			ItemStack held = target.getMainHandStack();
			if (!held.isEmpty() && target instanceof MobEntity) {
				target.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				ItemEntity item = new ItemEntity(world, home.x, home.y + 0.5, home.z, held.copy());
				item.setPickupDelay(10);
				world.spawnEntity(item);
				taken++;
			}
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, (int) (ctx.param("seconds", 8.0) * 20), 1), player);
			AlienKit.melee(world, player, target, (float) ctx.param("damage", 2.0));
			Fx.flash(world, at, CORE, 1.0f);
		}
		Fx.beam(world, last, home.add(0, 1.0, 0), STREAK, 0.15f, 8);
		if (taken == 0 && last.equals(home.add(0, 1.0, 0))) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		Xlr8Abilities.addTempo(player, 10.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ITEM_PICKUP, 1.0f, 1.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 2.0f);
		return true;
	}

	/**
	 * Sogspur: ein gerader Spurt bis zu 16 Bloecke — der Luftsog hinter XLR8 reisst Gegner von beiden Seiten in die Spur
	 * und wirbelt sie herum (betaeubt).
	 */
	private static boolean vacuumTrail(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Xlr8Abilities.Boost boost = Xlr8Abilities.boost(player);
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Vec3d start = player.getPos();
		double max = ctx.param("distance", 16.0) * (boost.full() ? 1.5 : 1.0);
		BlockHitResult wall = world.raycast(new RaycastContext(start.add(0, 0.9, 0), start.add(0, 0.9, 0).add(look.multiply(max)),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		double distance = wall.getType() == HitResult.Type.MISS ? max : Math.max(1.0, start.add(0, 0.9, 0).distanceTo(wall.getPos()) - 0.8);
		Vec3d end = start.add(look.multiply(distance));
		player.requestTeleport(end.x, end.y, end.z);
		float damage = (float) ctx.param("damage", 4.0) * boost.factor();
		for (LivingEntity target : AlienKit.along(world, player, start.add(0, 0.9, 0), end.add(0, 0.9, 0), ctx.param("width", 3.5))) {
			Vec3d c = target.getPos();
			double t = c.subtract(start).dotProduct(look);
			Vec3d onLine = start.add(look.multiply(t));
			Vec3d pull = onLine.subtract(c).multiply(0.5).add(look.multiply(0.6));
			target.setVelocity(pull.x, 0.5, pull.z);
			target.velocityModified = true;
			AlienKit.melee(world, player, target, damage);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), player);
		}
		for (double h : new double[] {0.4, 1.0, 1.6}) {
			Fx.beam(world, start.add(0, h, 0), end.add(0, h, 0), STREAK, 0.12f, 10);
		}
		for (int i = 0; i <= 4; i++) {
			Fx.send(world, Fx.Kind.SHOCKWAVE, start.lerp(end, i / 4.0).add(0, 1.0, 0), look, CORE, 1.6f, 8, 1, null);
		}
		Fx.smoke(world, start.add(0, 0.6, 0), 0xE6F0F8, 1.0f, 4);
		Xlr8Abilities.addTempo(player, 15.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 1.2f, 1.2f);
		return true;
	}

	/** Reibungsfeuer: rasende Kreise um sich selbst, bis der Boden brennt — ein Feuerring (Radius 4) haelt 4 Sekunden. */
	private static boolean frictionFire(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Xlr8Abilities.Boost boost = Xlr8Abilities.boost(player);
		Vec3d center = player.getPos();
		double radius = ctx.param("radius", 4.0);
		float damage = (float) ctx.param("damage", 2.0) * boost.factor();
		int ticks = (int) (ctx.param("seconds", 4.0) * 20);
		PowerTasks.start(player, ticks, 2, (world, p, age) -> {
			double spin = age * 0.6;
			for (int i = 0; i < 6; i++) {
				double a = spin + i * MathHelper.TAU / 6;
				Fx.flames(world, center.add(Math.cos(a) * radius, 0.2, Math.sin(a) * radius), new Vec3d(0, 0.12, 0), HEAT, 0.2f, 2);
			}
			if (age % 10 == 0) {
				for (LivingEntity target : AlienKit.around(world, p, center.add(0, 0.8, 0), radius + 1.0)) {
					double d = target.getPos().multiply(1, 0, 1).distanceTo(center.multiply(1, 0, 1));
					if (Math.abs(d - radius) < 1.4) {
						AlienKit.melee(world, p, target, damage);
						target.setOnFireFor(3.0f);
					}
				}
				world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.2f);
			}
			return true;
		});
		for (int i = 0; i < 16; i++) {
			double a = i * MathHelper.TAU / 16;
			Fx.beam(ctx.world(), center.add(Math.cos(a) * radius, 0.3, Math.sin(a) * radius),
					center.add(Math.cos(a + 0.4) * radius, 0.3, Math.sin(a + 0.4) * radius), STREAK, 0.12f, 8);
		}
		Fx.shockwave(ctx.world(), center.add(0, 0.1, 0), HEAT, (float) radius, 10);
		Xlr8Abilities.addTempo(player, 20.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_FIRECHARGE_USE, 1.0f, 1.5f);
		return true;
	}

	/** Phantom-Hiebe: Nachbilder umkreisen jeden Gegner im Umkreis und schlagen von allen Seiten — Blindheit, Verwirrung. */
	private static boolean phantomStrikes(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Xlr8Abilities.Boost boost = Xlr8Abilities.boost(player);
		double radius = ctx.param("radius", 8.0);
		float damage = (float) ctx.param("damage", 2.5) * boost.factor();
		int hits = (int) ctx.param("hits", 5.0) + (boost.full() ? 3 : 0);
		List<UUID> targets = new ArrayList<>();
		for (LivingEntity target : AlienKit.around(ctx.world(), player, player.getPos(), radius)) {
			targets.add(target.getUuid());
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0), player);
		}
		if (targets.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		ctx.grantInvulnerability(hits * 3 + 4);
		PowerTasks.start(player, hits * 3, 3, (world, p, age) -> {
			for (UUID id : targets) {
				if (world.getEntity(id) instanceof LivingEntity target && target.isAlive()) {
					double a = age * 1.3 + id.hashCode();
					Vec3d at = AlienKit.body(target);
					Vec3d from = at.add(Math.cos(a) * 1.6, 0.2, Math.sin(a) * 1.6);
					Fx.beam(world, from, at, STREAK, 0.12f, 4);
					Fx.flash(world, from, CORE, 0.6f);
					AlienKit.melee(world, p, target, damage);
				}
			}
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.9f, 1.6f);
			return true;
		});
		Xlr8Abilities.addTempo(player, 10.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_FLAP, 1.2f, 1.5f);
		return true;
	}

	/**
	 * Mach-Sprint (Ultimate): drei Sekunden Hyperschall — volle Schallmauer, unverwundbar, alles im Weg wird umgerannt.
	 * Zum Abschluss ein Ueberschallknall, der ringsum alles umwirft und Glas zerspringen laesst.
	 */
	private static boolean machSprint(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		float damage = (float) ctx.param("damage", 7.0);
		Xlr8Abilities.addTempo(player, Xlr8Abilities.MAX);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 5, false, false));
		ctx.grantInvulnerability(ticks);
		Set<UUID> hit = new HashSet<>();
		PowerTasks.start(player, ticks, 1, (world, p, age) -> {
			Vec3d look = BuiltinAbilities.horizontalLook(p);
			Vec3d v = p.getVelocity();
			if (v.multiply(1, 0, 1).lengthSquared() < 0.6) {
				// selbst rennen, falls der Spieler steht: die Kraft traegt ihn
				BuiltinAbilities.launch(p, look.x * 1.1, Math.min(v.y, 0.0), look.z * 1.1);
			}
			for (double h : new double[] {0.5, 1.2}) {
				Fx.beam(world, p.getPos().add(0, h, 0), p.getPos().add(0, h, 0).subtract(look.multiply(4.0)), STREAK, 0.18f, 4);
			}
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.9, 0), 2.0)) {
				if (hit.add(target.getUuid())) {
					AlienKit.melee(world, p, target, damage);
					target.setVelocity(look.x * 1.4, 0.6, look.z * 1.4);
					target.velocityModified = true;
					Fx.flash(world, AlienKit.body(target), CORE, 1.6f);
				}
			}
			Destruction.carve(world, p, p.getPos().add(look.multiply(0.7)).add(0, 0.9, 0), look, 1.4, 1.0, AlienStrength.of(p) * 1.6f, 0.0);
			if (age % 10 == 0) {
				Fx.send(world, Fx.Kind.SHOCKWAVE, p.getPos().add(0, 1.0, 0).subtract(look), look, CORE, 2.5f, 8, 1, null);
			}
			return true;
		}, (p, aborted) -> {
			if (aborted) {
				return;
			}
			ServerWorld world = p.getServerWorld();
			AlienKit.blast(world, p, p.getPos().add(0, 1.0, 0), 7.0, damage, 1.6, 0.5);
			Fx.shockwave(world, p.getPos().add(0, 0.2, 0), CORE, 8.0f, 12);
			Fx.flash(world, p.getPos().add(0, 1.0, 0), CORE, 3.0f);
			Fx.shake(world, p.getPos(), 0.6f, 12, 30);
			Destruction.crater(world, p, p.getPos().add(0, 1.0, 0), 5.0, 0.6f, 0.0, 0.2);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0f, 1.6f);
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.9f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Ueberschallstoss (Sprint + Schlag): Aufprall mit Schallknall — das Ziel fliegt weit, Druckring. */
	private static boolean supersonicShove(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		float tempo = Xlr8Abilities.tempo(player);
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		double force = ctx.param("power", 1.6) * (1.0 + tempo / Xlr8Abilities.MAX);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 5.0) * (1.0f + tempo / 100.0f));
		target.setVelocity(dir.x * force, 0.5, dir.z * force);
		target.velocityModified = true;
		Fx.send(ctx.world(), Fx.Kind.SHOCKWAVE, AlienKit.body(target), dir, CORE, 2.2f, 8, 1, null);
		Fx.flash(ctx.world(), AlienKit.body(target), CORE, 1.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 1.4f);
		return true;
	}

	/** Sprungtritt (Schlag in der Luft): zwei Tritte in einer Drehung — das Ziel fliegt hoch, XLR8 federt mit nach oben. */
	private static boolean blitzKick(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 4.0) * (1.0f + Xlr8Abilities.tempo(player) / 150.0f);
		AlienKit.melee(ctx.world(), player, target, damage);
		target.setVelocity(target.getVelocity().x * 0.2, 0.9, target.getVelocity().z * 0.2);
		target.velocityModified = true;
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.4, 0.75, player.getVelocity().z * 0.4);
		UUID id = target.getUuid();
		PowerTasks.start(player, 6, 6, (world, p, age) -> {
			if (age > 0 && world.getEntity(id) instanceof LivingEntity t && t.isAlive()) {
				AlienKit.melee(world, p, t, damage);
				Fx.flash(world, AlienKit.body(t), CORE, 1.2f);
			}
			return true;
		});
		Fx.send(ctx.world(), Fx.Kind.SHOCKWAVE, AlienKit.body(target), new Vec3d(0, 1, 0), STREAK, 1.8f, 6, 1, null);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.5f);
		return true;
	}

	/** Fussfeger (Schleichen + Schlag): Schwanz und Bein fegen das Ziel um — liegt am Boden (stark verlangsamt), Nachtritt. */
	private static boolean legSweep(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 4.0));
		target.setVelocity(0.0, -0.4, 0.0);
		target.velocityModified = true;
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, (int) (ctx.param("seconds", 2.5) * 20), 4), player);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0), player);
		Fx.shockwave(ctx.world(), target.getPos().add(0, 0.1, 0), STREAK, 2.0f, 6);
		Fx.smoke(ctx.world(), target.getPos(), 0xA89A88, 0.7f, 3);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
		return true;
	}

	/** Sturzflug (hoch in der Luft schleichen): schraeg nach vorn hinab, Aufprall mit Druckwelle nach Tempo. */
	private static boolean speedDive(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		float damage = (float) ctx.param("damage", 6.0) * (1.0f + Xlr8Abilities.tempo(player) / 100.0f);
		BuiltinAbilities.launch(player, look.x * 1.4, -1.6, look.z * 1.4);
		ctx.grantInvulnerability(30);
		PowerTasks.start(player, 40, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround()) {
				Fx.beam(world, p.getPos().add(0, 1.0, 0), p.getPos().add(0, 2.5, 0).subtract(look.multiply(1.5)), STREAK, 0.15f, 4);
				return true;
			}
			AlienKit.blast(world, p, p.getPos().add(0, 0.6, 0), 3.5, damage, 1.0, 0.4);
			Fx.shockwave(world, p.getPos().add(0, 0.1, 0), CORE, 4.0f, 10);
			Fx.flash(world, p.getPos().add(0, 0.5, 0), CORE, 1.8f);
			Destruction.crater(world, p, p.getPos().add(0, -0.5, 0), 1.3, 0.5, AlienStrength.of(p), 0.3, 0.4);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.5f, 1.9f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_SWOOP, 1.0f, 1.4f);
		return true;
	}

	/** Hundert Schlaege (Schlag gegen geschwaechtes Ziel): zehn Treffer in einer Sekunde, der letzte als Schallknall. */
	private static boolean hundredBlows(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 1.6) * (1.0f + Xlr8Abilities.tempo(player) / 100.0f);
		UUID id = target.getUuid();
		AlienKit.hold(ctx.world(), target, 22, AlienKit.dust("#38B6FF", 1.0f), false);
		PowerTasks.start(player, 20, 2, (world, p, age) -> {
			if (!(world.getEntity(id) instanceof LivingEntity t) || !t.isAlive()) {
				return false;
			}
			boolean last = age >= 18;
			Vec3d at = AlienKit.body(t);
			double a = age * 2.1;
			Fx.beam(world, at.add(Math.cos(a) * 1.4, Math.sin(a * 0.7) * 0.6, Math.sin(a) * 1.4), at, STREAK, 0.1f, 3);
			AlienKit.melee(world, p, t, last ? damage * 5.0f : damage);
			if (last) {
				Vec3d away = t.getPos().subtract(p.getPos()).multiply(1, 0, 1);
				if (away.lengthSquared() > 1.0E-4) {
					t.setVelocity(away.normalize().x * 1.8, 0.6, away.normalize().z * 1.8);
					t.velocityModified = true;
				}
				Fx.send(world, Fx.Kind.SHOCKWAVE, at, away.lengthSquared() > 1.0E-4 ? away.normalize() : new Vec3d(0, 1, 0), CORE, 2.6f, 8, 1, null);
				Fx.flash(world, at, CORE, 2.2f);
				world.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.6f, 1.8f);
			} else {
				world.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.7f, 1.4f + age * 0.03f);
			}
			return true;
		});
		return true;
	}
}
