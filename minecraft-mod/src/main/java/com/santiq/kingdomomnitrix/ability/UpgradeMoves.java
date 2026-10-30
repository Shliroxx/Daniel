package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Upgrade, Kraefte 2.0: Dehngriff, Ausruestungs-Upgrade, Nanodrohnen, Laserraster, EMP-Puls, Omega-Uebertaktung; Kombos
 * Fluessigramme, Hammerarm, Naniten-Injektion, Fluessigsturz, Systemabsturz; Passive Selbstreparatur und Fluessiger
 * Koerper (Integration und Naniten sitzen in {@link UpgradeAbilities}). Viele Kraefte legen Naniten, die der
 * Systemabsturz entlaedt.
 */
final class UpgradeMoves {
	private static final int NEON = 0x38FF14;
	private static final int CYAN = 0x00E6FF;
	private static final int LIQUID = 0x141A1E;
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

	private UpgradeMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("stretch_grab"), UpgradeMoves::stretchGrab);
		AbilityRegistry.register(KingdomOmnitrix.id("gear_upgrade"), UpgradeMoves::gearUpgrade);
		AbilityRegistry.register(KingdomOmnitrix.id("nano_drones"), UpgradeMoves::nanoDrones);
		AbilityRegistry.register(KingdomOmnitrix.id("laser_grid"), UpgradeMoves::laserGrid);
		AbilityRegistry.register(KingdomOmnitrix.id("emp_pulse"), UpgradeMoves::empPulse);
		AbilityRegistry.register(KingdomOmnitrix.id("omega_overdrive"), UpgradeMoves::omegaOverdrive);
		AbilityRegistry.register(KingdomOmnitrix.id("liquid_ram"), UpgradeMoves::liquidRam);
		AbilityRegistry.register(KingdomOmnitrix.id("hammer_morph"), UpgradeMoves::hammerMorph);
		AbilityRegistry.register(KingdomOmnitrix.id("nanite_inject"), UpgradeMoves::naniteInject);
		AbilityRegistry.register(KingdomOmnitrix.id("liquid_drop"), UpgradeMoves::liquidDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("system_crash"), UpgradeMoves::systemCrash);
		PassiveRegistry.register(KingdomOmnitrix.id("self_repair"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// Maschinenkoerper: Gift und Verdorren greifen nicht, ausser Kampf repariert er sich
				ServerPlayerEntity player = ctx.player();
				player.removeStatusEffect(StatusEffects.POISON);
				player.removeStatusEffect(StatusEffects.WITHER);
				long calm = ctx.now() - LAST_HURT.getOrDefault(player.getUuid(), 0L);
				if (ctx.now() % 40 == 0 && calm > 60 && player.getHealth() < player.getMaxHealth()) {
					player.heal((float) ctx.param("heal", 1.0));
					Fx.sparks(ctx.world(), player.getPos().add(0, 1.0, 0), NEON, 0.1f, 3);
				}
			}

			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				LAST_HURT.put(ctx.player().getUuid(), ctx.now());
			}

			@Override
			public void end(PassiveContext ctx) {
				LAST_HURT.remove(ctx.player().getUuid());
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("liquid_body"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Schlaege gehen zum Teil durch den fluessigen Koerper hindurch
				if (source.getAttacker() != null && source.getSource() == source.getAttacker()) {
					return amount * (float) ctx.param("melee", 0.75);
				}
				return amount;
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Dehngriff: der Arm schiesst bis 14 Bloecke weit — packt ein Ziel und zieht es heran, sonst zieht er Upgrade zur Wand. */
	private static boolean stretchGrab(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 14.0);
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, range, 0.95, e -> AlienKit.foe(player, e));
		Vec3d hand = AlienKit.muzzle(player);
		if (found.isPresent()) {
			LivingEntity target = found.get();
			Vec3d pull = player.getPos().subtract(target.getPos());
			target.setVelocity(pull.multiply(0.22).add(0, 0.35, 0));
			target.velocityModified = true;
			AlienKit.melee(world, player, target, (float) ctx.param("damage", 3.0) * UpgradeAbilities.power(player));
			UpgradeAbilities.infect(world, target, 1);
			Fx.beam(world, hand, AlienKit.body(target), LIQUID, 0.25f, 6);
			Fx.beam(world, hand, AlienKit.body(target), NEON, 0.08f, 6);
		} else {
			Vec3d end = AlienKit.aim(world, player, range);
			if (end.distanceTo(player.getEyePos()) > range - 0.5) {
				return false;
			}
			Vec3d to = end.subtract(player.getPos());
			BuiltinAbilities.launch(player, to.x * 0.2, to.y * 0.2 + 0.4, to.z * 0.2);
			Fx.beam(world, hand, end, LIQUID, 0.25f, 6);
			Fx.beam(world, hand, end, NEON, 0.08f, 6);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_SQUISH, 1.0f, 1.4f);
		return true;
	}

	/** Ausruestungs-Upgrade: verschmilzt kurz mit der Ausruestung der Gruppe — 10 s Staerke, Tempo und Schutz fuer alle. */
	private static boolean gearUpgrade(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 10.0) * 20);
		List<ServerPlayerEntity> party = new ArrayList<>(world.getPlayers(p -> p.squaredDistanceTo(player) <= 100 && !PartyRules.canHarm(player, p)));
		if (!party.contains(player)) {
			party.add(player);
		}
		for (ServerPlayerEntity member : party) {
			member.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 0), player);
			member.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 0), player);
			member.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 0), player);
			Fx.aura(world, member, NEON, 1.0f, 30);
			if (member != player) {
				Fx.beam(world, player.getPos().add(0, 1.0, 0), member.getPos().add(0, 1.0, 0), NEON, 0.08f, 10);
			}
		}
		UpgradeAbilities.addSync(player, 10.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
		return true;
	}

	/** Nanodrohnen: vier Drohnen umkreisen Upgrade 8 s und schiessen mit Lasern auf den naechsten Gegner (legen Naniten). */
	private static boolean nanoDrones(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		float damage = (float) ctx.param("damage", 2.0);
		double range = ctx.param("range", 12.0);
		PowerTasks.start(player, ticks, 2, (world, p, age) -> {
			for (int i = 0; i < 4; i++) {
				double a = age * 0.12 + i * MathHelper.TAU / 4;
				Vec3d drone = p.getPos().add(Math.cos(a) * 1.5, 2.2 + Math.sin(age * 0.2 + i) * 0.2, Math.sin(a) * 1.5);
				Fx.flash(world, drone, NEON, 0.35f);
				if ((age + i * 5) % 20 == 0) {
					List<LivingEntity> foes = AlienKit.around(world, p, drone, range);
					foes.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(drone)));
					if (!foes.isEmpty()) {
						LivingEntity target = foes.get(0);
						Fx.beam(world, drone, AlienKit.body(target), NEON, 0.08f, 4);
						AlienKit.magic(world, p, target, damage * UpgradeAbilities.power(p));
						UpgradeAbilities.infect(world, target, 1);
						world.playSound(null, drone.x, drone.y, drone.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.4f, 2.0f);
					}
				}
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
		return true;
	}

	/** Laserraster: eine Wand aus Laserstrahlen vor Upgrade, 6 s lang — wer hindurchgeht, nimmt Schaden und wird gebremst. */
	private static boolean laserGrid(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Vec3d side = new Vec3d(-look.z, 0, look.x);
		Vec3d center = player.getPos().add(look.multiply(3.0));
		double half = ctx.param("width", 4.0);
		float damage = (float) ctx.param("damage", 3.0);
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		Vec3d left = center.add(side.multiply(-half));
		Vec3d right = center.add(side.multiply(half));
		PowerTasks.start(player, ticks, 5, (world, p, age) -> {
			for (double h : new double[] {0.3, 1.0, 1.7, 2.4}) {
				Fx.beam(world, left.add(0, h, 0), right.add(0, h, 0), CYAN, 0.05f, 6);
			}
			if (age % 10 == 0) {
				for (LivingEntity target : AlienKit.along(world, p, left.add(0, 1.0, 0), right.add(0, 1.0, 0), 0.8)) {
					AlienKit.magic(world, p, target, damage);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 2), p);
					UpgradeAbilities.infect(world, target, 1);
				}
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 2.0f);
		return true;
	}

	/** EMP-Puls: ein Puls (Radius 8) laehmt Gegner, zerstoert Geschosse und trifft Maschinen (Golems) doppelt. */
	private static boolean empPulse(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 8.0);
		float damage = (float) ctx.param("damage", 5.0) * UpgradeAbilities.power(player);
		for (LivingEntity target : AlienKit.around(world, player, player.getPos().add(0, 1.0, 0), radius)) {
			AlienKit.magic(world, player, target, target instanceof IronGolemEntity ? damage * 2.0f : damage);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 4), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 1), player);
		}
		for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, player.getBoundingBox().expand(radius), e -> e.getOwner() != player)) {
			Fx.sparks(world, projectile.getPos(), CYAN, 0.3f, 6);
			projectile.discard();
		}
		Fx.shockwave(world, player.getPos().add(0, 1.0, 0), CYAN, (float) radius, 10);
		Fx.flash(world, player.getPos().add(0, 1.0, 0), CYAN, 2.6f);
		Fx.shake(world, player.getPos(), 0.25f, 8, 16);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.8f);
		return true;
	}

	/** Omega-Uebertaktung (Ultimate): sofort Overclock, dann 3 s lang Optikstrahlen auf jeden Gegner im Umkreis. */
	private static boolean omegaOverdrive(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		UpgradeAbilities.startOverclock(player);
		double range = ctx.param("range", 16.0);
		float damage = (float) ctx.param("damage", 4.0);
		PowerTasks.start(player, 60, 5, (world, p, age) -> {
			Vec3d eye = p.getEyePos();
			for (LivingEntity target : AlienKit.around(world, p, eye, range)) {
				Fx.beam(world, eye, AlienKit.body(target), NEON, 0.12f, 4);
				AlienKit.magic(world, p, target, damage * UpgradeAbilities.power(p));
				UpgradeAbilities.infect(world, target, 1);
			}
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 0.6f, 1.8f);
			return true;
		});
		ctx.grantInvulnerability(30);
		Fx.flash(ctx.world(), player.getEyePos(), NEON, 3.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.4f, 1.2f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Fluessigramme (Sprint + Schlag): fliesst als Fluessigkeit durch das Ziel hindurch — Schaden, Naniten, dahinter weiter. */
	private static boolean liquidRam(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 5.0) * UpgradeAbilities.power(player));
		UpgradeAbilities.infect(ctx.world(), target, 2);
		Vec3d behind = target.getPos().add(dir.multiply(1.6));
		if (ctx.world().isSpaceEmpty(player, player.getBoundingBox().offset(behind.subtract(player.getPos())))) {
			player.requestTeleport(behind.x, behind.y, behind.z);
		}
		Fx.beam(ctx.world(), AlienKit.body(target).subtract(dir.multiply(1.0)), AlienKit.body(target).add(dir.multiply(1.0)), LIQUID, 0.4f, 6);
		ctx.grantInvulnerability(6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_SQUISH, 1.2f, 0.8f);
		return true;
	}

	/** Hammerarm (Schlag in der Luft): der Arm formt sich zum Hammer — Schlag nach unten, kleiner Krater. */
	private static boolean hammerMorph(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 7.0) * UpgradeAbilities.power(player);
		AlienKit.melee(ctx.world(), player, target, damage);
		target.setVelocity(0.0, -1.4, 0.0);
		target.velocityModified = true;
		Vec3d ground = AlienKit.ground(ctx.world(), target.getPos(), 5.0);
		Destruction.crater(ctx.world(), player, ground.add(0, -0.3, 0), 1.2, 0.5, AlienStrength.of(player), 0.2, 0.4);
		Fx.shockwave(ctx.world(), ground.add(0, 0.1, 0), NEON, 2.5f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ANVIL_LAND, 0.8f, 1.2f);
		return true;
	}

	/** Naniten-Injektion (Schleichen + Schlag): spritzt Naniten ins Ziel — drei auf einmal, gelaehmt. */
	private static boolean naniteInject(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 3.0));
		UpgradeAbilities.infect(ctx.world(), target, 3);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2), player);
		Fx.sparks(ctx.world(), AlienKit.body(target), NEON, 0.2f, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEEHIVE_WORK, 1.2f, 2.0f);
		return true;
	}

	/** Fluessigsturz (hoch in der Luft schleichen): faellt als Fluessigkeit, platscht auf — Schaden und Naniten ringsum. */
	private static boolean liquidDrop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 6.0);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 1.8), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround()) {
				return true;
			}
			for (LivingEntity target : AlienKit.blast(world, p, p.getPos().add(0, 0.5, 0), 3.5, damage * UpgradeAbilities.power(p), 0.8, 0.3)) {
				UpgradeAbilities.infect(world, target, 2);
			}
			Fx.shockwave(world, p.getPos().add(0, 0.1, 0), LIQUID, 4.0f, 8);
			Fx.shockwave(world, p.getPos().add(0, 0.15, 0), NEON, 3.0f, 8);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_SLIME_DEATH, SoundCategory.PLAYERS, 1.4f, 0.6f);
			return false;
		});
		return true;
	}

	/** Systemabsturz (Schlag gegen geschwaechtes Ziel): entlaedt alle Naniten — Schaden je Nanit, Kettenblitz auf Nachbarn. */
	private static boolean systemCrash(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int nanites = UpgradeAbilities.purge(target);
		float damage = ((float) ctx.param("damage", 6.0) + nanites * (float) ctx.param("per_nanite", 3.0)) * UpgradeAbilities.power(player);
		AlienKit.magic(world, player, target, damage);
		UpgradeAbilities.chain(world, player, target, damage * 0.4f, 3, 6.0);
		Fx.explosion(world, AlienKit.body(target), NEON, 1.4f + nanites * 0.15f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.4f);
		return true;
	}
}
