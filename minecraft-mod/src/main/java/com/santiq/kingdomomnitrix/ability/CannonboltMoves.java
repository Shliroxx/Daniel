package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Cannonbolt, Kraefte 2.0: Bowlingkugel, Abrissbirne, Flipperkugel, Stachelpanzer, Kreiselpanzer, Kanonenhagel; Kombos
 * Kugelrammbock, Kugelstampfer, Panzerkonter, Kanonenschlag, Volltreffer; Passive Panzerschale, Rollschwung und
 * Abprallen. Die Rollen nutzen die Werkzeuge aus {@link CannonboltAbilities} (Druckwelle, Durchbrechen, Staubspur).
 */
final class CannonboltMoves {
	private static final int SHELL = 0xF2E6C8;
	private static final int DUST = 0x9C8466;
	private static final int HEAT = 0xFF9A3A;

	private CannonboltMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("bowling_strike"), CannonboltMoves::bowlingStrike);
		AbilityRegistry.register(KingdomOmnitrix.id("wrecking_ball"), CannonboltMoves::wreckingBall);
		AbilityRegistry.register(KingdomOmnitrix.id("pinball"), CannonboltMoves::pinball);
		AbilityRegistry.register(KingdomOmnitrix.id("spike_shell"), CannonboltMoves::spikeShell);
		AbilityRegistry.register(KingdomOmnitrix.id("shell_spin"), CannonboltMoves::shellSpin);
		AbilityRegistry.register(KingdomOmnitrix.id("bounce_barrage"), CannonboltMoves::bounceBarrage);
		AbilityRegistry.register(KingdomOmnitrix.id("curl_charge"), CannonboltMoves::curlCharge);
		AbilityRegistry.register(KingdomOmnitrix.id("ball_stomp"), CannonboltMoves::ballStomp);
		AbilityRegistry.register(KingdomOmnitrix.id("shell_counter"), CannonboltMoves::shellCounter);
		AbilityRegistry.register(KingdomOmnitrix.id("cannon_drop"), CannonboltMoves::cannonDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("bullseye"), CannonboltMoves::bullseye);
		PassiveRegistry.register(KingdomOmnitrix.id("armored_shell"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// der Panzer faengt Geschosse und Explosionen ab
				if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
					return amount * (float) ctx.param("projectile", 0.6);
				}
				if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
					return amount * (float) ctx.param("explosion", 0.7);
				}
				return amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("rolling_momentum"), new AlienPassive() {
			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				// mit Anlauf trifft der Panzer haerter
				return ctx.player().isSprinting() ? amount * (float) ctx.param("sprint_bonus", 1.2) : amount;
			}

			@Override
			public void melee(PassiveContext ctx, LivingEntity target, boolean fullSwing) {
				if (ctx.player().isSprinting()) {
					AlienKit.push(target, ctx.player().getPos(), 0.8, 0.2);
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("bounce_back"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// rund wie ein Ball: Fallschaden wird zum Abprall mit kleiner Druckwelle
				if (source.isIn(DamageTypeTags.IS_FALL)) {
					ServerPlayerEntity player = ctx.player();
					CannonboltAbilities.shockwave(ctx.world(), player, Math.min(10.0f, amount), 2.5, 0.4);
					BuiltinAbilities.launch(player, player.getVelocity().x, Math.min(1.0, 0.3 + amount * 0.05), player.getVelocity().z);
					return 0.0f;
				}
				return amount;
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Bowlingkugel: flach am Boden bis 20 Bloecke geradeaus — jeder Getroffene fliegt hoch wie ein Kegel. */
	private static boolean bowlingStrike(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		float damage = (float) (ctx.param("damage", 7.0) * CannonboltAbilities.spikes(player));
		Set<UUID> hit = new HashSet<>();
		CannonboltAbilities.launchFx(ctx.world(), player, dir, 0.9f);
		PowerTasks.start(player, (int) ctx.param("ticks", 22.0), 1, (world, p, age) -> {
			BuiltinAbilities.launch(p, dir.x * 1.1, Math.min(p.getVelocity().y, 0.0) - 0.04, dir.z * 1.1);
			CannonboltAbilities.trail(world, p);
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.6, 0), 1.6)) {
				if (hit.add(target.getUuid())) {
					AlienKit.melee(world, p, target, damage);
					target.setVelocity(dir.x * 0.5 + (world.random.nextDouble() - 0.5) * 0.6, 1.1, dir.z * 0.5 + (world.random.nextDouble() - 0.5) * 0.6);
					target.velocityModified = true;
					Fx.flash(world, AlienKit.body(target), SHELL, 1.4f);
					world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.PLAYERS, 1.2f, 1.2f);
				}
			}
			CannonboltAbilities.smashAhead(world, p, dir, 0.6f);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.7f);
		return true;
	}

	/** Abrissbirne: zusammengerollt im Bogen auf den Zielpunkt — Krater und Druckwelle beim Einschlag. */
	private static boolean wreckingBall(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d target = AlienKit.ground(ctx.world(), AlienKit.aim(ctx.world(), player, ctx.param("range", 16.0)), 8.0);
		Vec3d to = target.subtract(player.getPos());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		BuiltinAbilities.launch(player, to.x / Math.max(1.0, flat) * Math.min(1.6, flat * 0.12), 1.0 + Math.min(0.6, flat * 0.03),
				to.z / Math.max(1.0, flat) * Math.min(1.6, flat * 0.12));
		float damage = (float) (ctx.param("damage", 10.0) * CannonboltAbilities.spikes(player));
		ctx.grantInvulnerability(30);
		PowerTasks.start(player, 80, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (age < 4 || !p.isOnGround()) {
				return true;
			}
			CannonboltAbilities.shockwave(world, p, damage, 4.5, 0.8);
			Destruction.crater(world, p, p.getPos().add(0, -0.6, 0), 2.0, 0.5, AlienStrength.of(p) * 1.3f, 0.4, 0.5);
			Fx.flash(world, p.getPos().add(0, 0.6, 0), SHELL, 2.2f);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 0.8f);
			return false;
		});
		CannonboltAbilities.launchFx(ctx.world(), player, new Vec3d(0, 1, 0), 0.8f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_JUMP, 1.2f, 0.5f);
		return true;
	}

	/** Flipperkugel: 4 s prallt die Kugel kreuz und quer durch die Gegend und trifft alles auf dem Weg. */
	private static boolean pinball(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float damage = (float) (ctx.param("damage", 5.0) * CannonboltAbilities.spikes(player));
		Vec3d[] dir = {BuiltinAbilities.horizontalLook(player)};
		int ticks = (int) (ctx.param("seconds", 4.0) * 20);
		ctx.grantInvulnerability(ticks);
		java.util.Map<UUID, Long> last = new java.util.HashMap<>();
		PowerTasks.start(player, ticks, 1, (world, p, age) -> {
			if (!world.isSpaceEmpty(p, p.getBoundingBox().offset(dir[0].multiply(0.4)).offset(0, 0.1, 0))) {
				// Abprall mit Zufallswinkel, kleine Druckwelle
				double a = Math.atan2(-dir[0].z, -dir[0].x) + (world.random.nextDouble() - 0.5) * 1.6;
				dir[0] = new Vec3d(Math.cos(a), 0, Math.sin(a));
				Fx.shockwave(world, p.getPos().add(0, 0.6, 0), SHELL, 2.0f, 6);
				world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.PLAYERS, 0.8f, 1.2f);
			}
			BuiltinAbilities.launch(p, dir[0].x * 1.0, Math.min(p.getVelocity().y, 0.0) - 0.04, dir[0].z * 1.0);
			CannonboltAbilities.trail(world, p);
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.6, 0), 1.6)) {
				long now = world.getTime();
				if (now - last.getOrDefault(target.getUuid(), -100L) >= 10) {
					last.put(target.getUuid(), now);
					AlienKit.melee(world, p, target, damage);
					AlienKit.push(target, p.getPos(), 1.0, 0.4);
					// Ziel-Treffer lenkt ebenfalls ab
					Vec3d away = p.getPos().subtract(target.getPos()).multiply(1, 0, 1);
					if (away.lengthSquared() > 1.0E-4) {
						dir[0] = away.normalize();
					}
				}
			}
			return true;
		});
		CannonboltAbilities.launchFx(ctx.world(), player, dir[0], 1.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.8f);
		return true;
	}

	/** Stachelpanzer: Stacheln fahren aus (8 s) — wer Cannonbolt beruehrt oder schlaegt, sticht sich. */
	private static boolean spikeShell(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		float damage = (float) ctx.param("damage", 3.0);
		Fx.aura(ctx.world(), player, SHELL, 1.3f, ticks);
		Fx.shards(ctx.world(), player.getPos().add(0, 1.0, 0), SHELL, 0.3f, 16);
		PowerTasks.start(player, ticks, 10, (world, p, age) -> {
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.9, 0), 1.8)) {
				AlienKit.hit(target, world.getDamageSources().thorns(p), damage);
				Fx.sparks(world, AlienKit.body(target), SHELL, 0.3f, 4);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.2f, 0.6f);
		return true;
	}

	/** Kreiselpanzer: 3 s Drehung auf der Stelle — zieht Gegner heran, trifft ringsum und lenkt Geschosse ab. */
	private static boolean shellSpin(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float damage = (float) (ctx.param("damage", 3.0) * CannonboltAbilities.spikes(player));
		double radius = ctx.param("radius", 4.5);
		ctx.grantInvulnerability(10);
		PowerTasks.start(player, (int) (ctx.param("seconds", 3.0) * 20), 2, (world, p, age) -> {
			Vec3d c = p.getPos().add(0, 0.8, 0);
			for (int i = 0; i < 6; i++) {
				double a = age * 0.6 + i * MathHelper.TAU / 6;
				Fx.smoke(world, c.add(Math.cos(a) * 1.4, -0.6, Math.sin(a) * 1.4), DUST, 0.6f, 1);
			}
			for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, p.getBoundingBox().expand(radius), e -> e.getOwner() != p)) {
				projectile.setVelocity(projectile.getVelocity().multiply(-1.0));
				projectile.setOwner(p);
				projectile.velocityModified = true;
			}
			if (age % 8 == 0) {
				for (LivingEntity target : AlienKit.around(world, p, c, radius)) {
					Vec3d pull = c.subtract(target.getPos()).multiply(1, 0, 1);
					if (target.squaredDistanceTo(p) > 4.0 && pull.lengthSquared() > 1.0E-4) {
						target.addVelocity(pull.normalize().x * 0.3, 0.05, pull.normalize().z * 0.3);
						target.velocityModified = true;
					} else {
						AlienKit.melee(world, p, target, damage);
						AlienKit.push(target, c, 0.9, 0.3);
					}
				}
				world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 0.6f);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), 1.0f, 0.7f);
		return true;
	}

	/** Kanonenhagel (Ultimate): hoch hinaus, dann vier Spruenge in Blickrichtung — jeder Aufprall mit Krater und Druckwelle. */
	private static boolean bounceBarrage(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		float damage = (float) (ctx.param("damage", 12.0) * CannonboltAbilities.spikes(player));
		int[] bounces = {(int) ctx.param("bounces", 4.0)};
		BuiltinAbilities.launch(player, dir.x * 0.8, 1.6, dir.z * 0.8);
		ctx.grantInvulnerability(120);
		PowerTasks.start(player, 200, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (age < 4) {
				return true;
			}
			if (!p.isOnGround()) {
				if (age % 2 == 0) {
					Fx.flames(world, p.getPos().add(0, 0.8, 0), new Vec3d(0, 0.12, 0), HEAT, 0.3f, 2);
				}
				return true;
			}
			CannonboltAbilities.shockwave(world, p, damage, 6.0, 1.0);
			Destruction.crater(world, p, p.getPos().add(0, -0.6, 0), 2.6, 0.5, AlienStrength.of(p) * 1.6f, 0.4, 0.6);
			Fx.explosion(world, p.getPos().add(0, 0.5, 0), HEAT, 2.4f);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.4f, 0.7f);
			if (--bounces[0] <= 0) {
				return false;
			}
			BuiltinAbilities.launch(p, dir.x * 1.0, 1.2, dir.z * 1.0);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.3f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Kugelrammbock (Sprint + Schlag): rollt sich im Lauf zusammen und rammt — das Ziel fliegt weit. */
	private static boolean curlCharge(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) (ctx.param("damage", 6.0) * CannonboltAbilities.spikes(player)));
		target.setVelocity(dir.x * 2.0, 0.5, dir.z * 2.0);
		target.velocityModified = true;
		Fx.flash(ctx.world(), AlienKit.body(target), SHELL, 1.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 1.0f);
		return true;
	}

	/** Kugelstampfer (Schlag in der Luft): faellt als Kugel aufs Ziel — kurze Druckwelle. */
	private static boolean ballStomp(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) (ctx.param("damage", 7.0) * CannonboltAbilities.spikes(player)));
		target.setVelocity(0.0, -1.2, 0.0);
		target.velocityModified = true;
		AlienKit.blast(ctx.world(), player, target.getPos().add(0, 0.5, 0), 2.5, (float) ctx.param("damage", 7.0) * 0.4f, 0.8, 0.3);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.3, 0.5, player.getVelocity().z * 0.3);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_JUMP, 1.2f, 0.6f);
		return true;
	}

	/** Panzerkonter (Schleichen + Schlag): schliesst den Panzer im Schlag — Ziel prallt ab, Cannonbolt 2 s geschuetzt. */
	private static boolean shellCounter(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 4.0));
		AlienKit.push(target, player.getPos(), 1.6, 0.4);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 2, false, false));
		Fx.aura(ctx.world(), player, SHELL, 1.2f, 40);
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_SHIELD_BLOCK, 1.2f, 0.8f);
		return true;
	}

	/** Kanonenschlag (hoch in der Luft schleichen): Sturz als Kanonenkugel — Krater und Druckwelle nach Hoehe. */
	private static boolean cannonDrop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		float damage = (float) (ctx.param("damage", 9.0) * CannonboltAbilities.spikes(player));
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.4), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround()) {
				Fx.flames(world, p.getPos().add(0, 0.8, 0), new Vec3d(0, 0.15, 0), HEAT, 0.3f, 2);
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			CannonboltAbilities.shockwave(world, p, damage * boost, 4.0 * boost, 0.9);
			Destruction.crater(world, p, p.getPos().add(0, -0.6, 0), 1.6 * boost, 0.5, AlienStrength.of(p) * boost, 0.4, 0.5);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 0.9f);
			return false;
		});
		return true;
	}

	/** Volltreffer (Schlag gegen geschwaechtes Ziel): Kanonenkugel aus naechster Naehe — riesiger Schaden, Ziel fliegt explosiv weg. */
	private static boolean bullseye(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		float damage = (float) ((ctx.param("damage", 12.0) + (target.getMaxHealth() - target.getHealth()) * 0.2) * CannonboltAbilities.spikes(player));
		AlienKit.melee(world, player, target, damage);
		target.setVelocity(dir.x * 2.6, 0.8, dir.z * 2.6);
		target.velocityModified = true;
		Fx.explosion(world, AlienKit.body(target), HEAT, 1.8f);
		Fx.send(world, Fx.Kind.SHOCKWAVE, AlienKit.body(target), dir, SHELL, 2.6f, 8, 1, null);
		Fx.shake(world, target.getPos(), 0.35f, 8, 16);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0f, 1.2f);
		return true;
	}
}
