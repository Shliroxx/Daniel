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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Way Big, Kraefte 2.0: Kosmische Salve, Bodenwischer, Himmelshaken, Riesengriff, Kosmische Kuppel, Kosmische Kanone;
 * Kombos Titanentritt, Himmelshammer, Riesenschnipser, Kolosssturz, Kosmischer Gnadenstoss; Passive Kosmische Ladung,
 * Titanenschritt (beide aus dem Kern-System) und To'kustar-Haut. Beben kommen aus {@link WayBigAbilities#quake}.
 */
final class WayBigMoves {
	private static final int RAY = WayBigAbilities.RAY;
	private static final int CORE = WayBigAbilities.CORE;
	private static final int DUST = WayBigAbilities.DUST;
	/** Titanenschritt: letzte Schrittposition je Spieler */
	private static final Map<UUID, Vec3d> LAST_STEP = new HashMap<>();

	private WayBigMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("cosmic_bolts"), WayBigMoves::cosmicBolts);
		AbilityRegistry.register(KingdomOmnitrix.id("ground_swipe"), WayBigMoves::groundSwipe);
		AbilityRegistry.register(KingdomOmnitrix.id("skyward_uppercut"), WayBigMoves::skywardUppercut);
		AbilityRegistry.register(KingdomOmnitrix.id("giant_grab"), WayBigMoves::giantGrab);
		AbilityRegistry.register(KingdomOmnitrix.id("cosmic_dome"), WayBigMoves::cosmicDome);
		AbilityRegistry.register(KingdomOmnitrix.id("cosmic_cannon"), WayBigMoves::cosmicCannon);
		AbilityRegistry.register(KingdomOmnitrix.id("titan_kick"), WayBigMoves::titanKick);
		AbilityRegistry.register(KingdomOmnitrix.id("sky_hammer"), WayBigMoves::skyHammer);
		AbilityRegistry.register(KingdomOmnitrix.id("giant_flick"), WayBigMoves::giantFlick);
		AbilityRegistry.register(KingdomOmnitrix.id("colossus_drop"), WayBigMoves::colossusDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("cosmic_finisher"), WayBigMoves::cosmicFinisher);
		PassiveRegistry.register(KingdomOmnitrix.id("cosmic_charge"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// kosmische Energie sammelt sich von selbst
				if (ctx.now() % 20 == 0) {
					WayBigAbilities.charge(ctx.player(), (float) ctx.param("per_second", 5.0));
				}
			}

			@Override
			public void afterHit(PassiveContext ctx, LivingEntity target, DamageSource source, float damage) {
				// und mit jedem Treffer
				if (!AlienKit.bonusHit) {
					WayBigAbilities.charge(ctx.player(), damage * (float) ctx.param("per_damage", 1.0));
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("titan_step"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// jeder Schritt erschuettert den Boden — kleine Gegner in der Naehe taumeln
				ServerPlayerEntity big = ctx.player();
				ServerWorld world = ctx.world();
				Vec3d last = LAST_STEP.get(big.getUuid());
				if (!big.isOnGround() || last != null && last.squaredDistanceTo(big.getPos()) <= 9.0) {
					return;
				}
				LAST_STEP.put(big.getUuid(), big.getPos());
				for (LivingEntity near : AlienKit.around(world, big, big.getPos(), ctx.param("radius", 3.5))) {
					near.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 1), big);
				}
				world.playSound(null, big.getBlockPos(), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.PLAYERS, 1.2f, 0.4f);
				// Fussabdruck: weicher Boden gibt unter dem Riesen nach
				Destruction.crater(world, big, big.getPos().add(0, -0.4, 0), 2.2, 0.35, 1.0f, 0.0, 0.1);
				Fx.smoke(world, big.getPos(), DUST, 1.0f, 2);
				Fx.shake(world, big.getPos(), 0.15f, 6, 24);
			}

			@Override
			public void end(PassiveContext ctx) {
				LAST_STEP.remove(ctx.player().getUuid());
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("tokustar_hide"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Nahkampf kleiner Wesen kratzt kaum an der To'kustar-Haut
				return source.getAttacker() != null && source.getSource() == source.getAttacker()
						? amount * (float) ctx.param("melee", 0.75) : amount;
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Kosmische Salve: drei Energiestoesse aus den Handflaechen auf den Zielbereich; je 10 Ladung machen sie staerker. */
	private static boolean cosmicBolts(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 7.0);
		double range = ctx.param("range", 40.0);
		PowerTasks.start(player, 15, 5, (world, p, age) -> {
			Vec3d at = AlienKit.aim(world, p, range);
			float bonus = WayBigAbilities.charge(p) >= 10.0f ? 1.5f : 1.0f;
			if (bonus > 1.0f) {
				WayBigAbilities.charge(p, -10.0f);
			}
			Vec3d palm = AlienKit.muzzle(p).add(BuiltinAbilities.horizontalLook(p).crossProduct(new Vec3d(0, 1, 0)).multiply(age % 2 == 0 ? 1.2 : -1.2));
			Fx.beam(world, palm, at, RAY, 0.6f, 6);
			Fx.beam(world, palm, at, CORE, 0.25f, 6);
			AlienKit.blast(world, p, at, 2.5, damage * bonus, 0.8, 0.4);
			Fx.explosion(world, at, RAY, 1.6f);
			world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 1.3f);
			return true;
		});
		return true;
	}

	/** Bodenwischer: eine riesige Hand fegt den Halbkreis vor Way Big — alles fliegt zur Seite. */
	private static boolean groundSwipe(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Vec3d side = new Vec3d(-look.z, 0.0, look.x);
		for (LivingEntity target : AlienKit.cone(world, player, ctx.param("range", 10.0), 0.0)) {
			AlienKit.melee(world, player, target, (float) ctx.param("damage", 8.0));
			target.setVelocity(side.x * 2.0 + look.x * 0.5, 0.6, side.z * 2.0 + look.z * 0.5);
			target.velocityModified = true;
		}
		for (int i = 0; i <= 8; i++) {
			double a = -Math.PI / 2 + i * Math.PI / 8;
			Vec3d d = look.multiply(Math.cos(a)).add(side.multiply(Math.sin(a)));
			Fx.smoke(world, player.getPos().add(d.multiply(6.0)), DUST, 1.2f, 2);
		}
		Fx.shake(world, player.getPos(), 0.4f, 8, 30);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 2.0f, 0.4f);
		return true;
	}

	/** Himmelshaken: Aufwaertshaken — alles vor Way Big fliegt hoch in die Luft. */
	private static boolean skywardUppercut(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		for (LivingEntity target : AlienKit.cone(world, player, ctx.param("range", 9.0), 0.5)) {
			AlienKit.melee(world, player, target, (float) ctx.param("damage", 9.0));
			target.setVelocity(target.getVelocity().x * 0.2, ctx.param("launch", 2.0), target.getVelocity().z * 0.2);
			target.velocityModified = true;
		}
		Vec3d front = player.getPos().add(BuiltinAbilities.horizontalLook(player).multiply(4.0));
		Fx.send(world, Fx.Kind.SHOCKWAVE, front.add(0, 2.0, 0), new Vec3d(0, 1, 0), RAY, 4.0f, 10, 1, null);
		Fx.flash(world, front.add(0, 3.0, 0), CORE, 3.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.4f);
		return true;
	}

	/** Riesengriff: packt das Ziel im Blick, haelt es 2 s hoch in der Faust — dann zerdrueckt er es. */
	private static boolean giantGrab(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 12.0), 0.8, e -> AlienKit.foe(player, e));
		if (found.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		float damage = (float) ctx.param("damage", 16.0);
		int ticks = (int) (ctx.param("seconds", 2.0) * 20);
		PowerTasks.start(player, ticks, 1, (world, p, age) -> {
			if (!target.isAlive()) {
				return false;
			}
			Vec3d hand = p.getEyePos().add(BuiltinAbilities.horizontalLook(p).multiply(2.5)).add(0, 0.5, 0);
			target.requestTeleport(hand.x, hand.y, hand.z);
			target.setVelocity(Vec3d.ZERO);
			target.fallDistance = 0.0f;
			if (age % 5 == 0) {
				Fx.sparks(world, AlienKit.body(target), RAY, 0.2f, 3);
			}
			return true;
		}, (p, aborted) -> {
			if (!aborted && target.isAlive()) {
				ServerWorld w = p.getServerWorld();
				AlienKit.melee(w, p, target, damage);
				Fx.flash(w, AlienKit.body(target), CORE, 2.0f);
				Fx.shake(w, target.getPos(), 0.3f, 8, 20);
				w.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.PLAYERS, 1.5f, 0.5f);
			}
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.0f, 0.5f);
		return true;
	}

	/** Kosmische Kuppel: eine Kuppel aus Sternenenergie (5 s) — Verbuendete darin geschuetzt, Geschosse vergluehen. */
	private static boolean cosmicDome(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d center = player.getPos();
		double radius = ctx.param("radius", 7.0);
		int ticks = (int) (ctx.param("seconds", 5.0) * 20);
		PowerTasks.start(player, ticks, 5, (world, p, age) -> {
			for (int i = 0; i < 12; i++) {
				double a = i * MathHelper.TAU / 12 + age * 0.05;
				double h = (i % 3) * radius * 0.3;
				double r = Math.sqrt(Math.max(0.0, radius * radius - h * h));
				Fx.sparks(world, center.add(Math.cos(a) * r, h, Math.sin(a) * r), RAY, 0.05f, 1);
			}
			for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, new net.minecraft.util.math.Box(center, center).expand(radius),
					e -> e.getOwner() != p && e.getPos().distanceTo(center) <= radius)) {
				Fx.flash(world, projectile.getPos(), RAY, 0.8f);
				projectile.discard();
			}
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 1, false, false));
			for (ServerPlayerEntity ally : world.getPlayers(o -> o != p && o.getPos().distanceTo(center) <= radius && PartyRules.isAlly(p, o))) {
				ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 1, false, false));
			}
			return true;
		});
		Fx.shockwave(ctx.world(), center.add(0, 0.2, 0), RAY, (float) radius, 12);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.5f, 0.6f);
		return true;
	}

	/**
	 * Kosmische Kanone (Ultimate): 2 s Aufladen, dann ein gewaltiger kosmischer Strahl, der in 2 s einen 90-Grad-Bogen
	 * vor Way Big ueberstreicht — er pfluegt durch das Gelaende und trifft alles auf seinem Weg.
	 */
	private static boolean cosmicCannon(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int charge = (int) (ctx.param("charge_seconds", 2.0) * 20);
		int sweep = (int) (ctx.param("sweep_seconds", 2.0) * 20);
		double range = ctx.param("range", 60.0);
		float damage = (float) ctx.param("damage", 10.0);
		float yaw = player.getYaw();
		Map<UUID, Long> lastHit = new HashMap<>();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, charge + sweep, 6, false, false));
		ctx.grantInvulnerability(charge + sweep);
		Fx.charge(ctx.world(), player, RAY, 3.0f, charge);
		PowerTasks.start(player, charge + sweep, 1, (world, p, age) -> {
			if (age < charge) {
				if (age % 5 == 0) {
					Fx.sparks(world, p.getEyePos(), RAY, 0.6f, 6);
				}
				return true;
			}
			float t = (age - charge) / (float) sweep;
			float a = (yaw - 45.0f + 90.0f * t) * MathHelper.RADIANS_PER_DEGREE;
			Vec3d dir = new Vec3d(-MathHelper.sin(a), -0.08, MathHelper.cos(a)).normalize();
			// Ursprung auf Brusthoehe vor Way Big: auf Augenhoehe wuerde der Strahl die eigene Sicht ueberstrahlen
			Vec3d from = p.getPos().add(0, p.getHeight() * 0.5, 0).add(dir.multiply(3.0));
			Vec3d to = from.add(dir.multiply(range));
			Fx.beam(world, from, to, RAY, 1.6f, 3);
			Fx.beam(world, from, to, CORE, 0.6f, 3);
			long now = world.getTime();
			for (LivingEntity target : AlienKit.along(world, p, from, to, 2.0)) {
				if (now - lastHit.getOrDefault(target.getUuid(), -100L) >= 10) {
					lastHit.put(target.getUuid(), now);
					AlienKit.magic(world, p, target, damage);
					AlienKit.push(target, p.getPos(), 1.0, 0.4);
				}
			}
			if (age % 4 == 0) {
				Destruction.carve(world, p, from, dir, range * 0.5, 1.5, AlienStrength.of(p) * 0.5f, 0.2);
				world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0f, 0.6f);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 2.0f, 0.5f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Titanentritt (Sprint + Schlag): ein Tritt, der das Ziel weit wegschiesst — kleines Beben am Aufsetzpunkt. */
	private static boolean titanKick(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 9.0));
		target.setVelocity(dir.x * 2.4, 0.8, dir.z * 2.4);
		target.velocityModified = true;
		WayBigAbilities.quake(ctx.world(), player, player.getPos().add(dir.multiply(2.0)), 4.0, 0.0f, 0.3, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.5f);
		return true;
	}

	/** Himmelshammer (Schlag in der Luft): beide Faeuste von oben — Ziel in den Boden, Beben ringsum. */
	private static boolean skyHammer(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 10.0));
		target.setVelocity(0.0, -1.5, 0.0);
		target.velocityModified = true;
		WayBigAbilities.quake(ctx.world(), player, target.getPos(), ctx.param("radius", 5.0), 4.0f, 0.4, 0.0);
		return true;
	}

	/** Riesenschnipser (Schleichen + Schlag): ein Fingerschnipser — das Ziel fliegt in hohem Bogen davon. */
	private static boolean giantFlick(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 4.0));
		target.setVelocity(dir.x * 3.0, 1.4, dir.z * 3.0);
		target.velocityModified = true;
		Fx.flash(ctx.world(), AlienKit.body(target), CORE, 1.2f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.6f, 1.2f);
		return true;
	}

	/** Kolosssturz (hoch in der Luft schleichen): der Riese faellt — Beben und Krater nach Fallhoehe. */
	private static boolean colossusDrop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 10.0);
		double radius = ctx.param("radius", 7.0);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.0), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 20.0);
			WayBigAbilities.quake(world, p, p.getPos(), radius * boost, damage * boost, 0.9, 3.0 * boost);
			return false;
		});
		return true;
	}

	/** Kosmischer Gnadenstoss (Schlag gegen geschwaechtes Ziel): kosmischer Strahl aus naechster Naehe — verbraucht Ladung. */
	private static boolean cosmicFinisher(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float used = Math.min(WayBigAbilities.charge(player), 50.0f);
		WayBigAbilities.charge(player, -used);
		float damage = (float) (ctx.param("damage", 14.0) + (target.getMaxHealth() - target.getHealth()) * 0.2 + used * 0.2);
		AlienKit.magic(world, player, target, damage);
		Vec3d from = AlienKit.muzzle(player);
		Fx.beam(world, from, AlienKit.body(target).add(BuiltinAbilities.horizontalLook(player).multiply(6.0)), RAY, 1.2f, 8);
		Fx.beam(world, from, AlienKit.body(target), CORE, 0.5f, 8);
		Fx.explosion(world, AlienKit.body(target), RAY, 2.4f);
		AlienKit.push(target, player.getPos(), 2.0, 0.6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.8f);
		return true;
	}
}
