package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Ripjaws, Kraefte 2.0: Wasserstrahl, Wasserfontaene, Fressrausch, Blasenschild, Sogstroemung, Mahlstrom; Kombos
 * Hairamme, Kiefersturz, Unterwasserzug, Wasserbombe, Todesbiss; Passive Gezeitenkraft, Tiefseejaeger und Blutwitterung.
 * Viele Kraefte hinterlassen Gezeitenzonen ({@link TideZones}) — dort ist Ripjaws nass und staerker.
 */
final class RipjawsMoves {
	private static final int WATER = 0x3A8CFF;
	private static final int FOAM = 0xD8F0FF;
	private static final int DEEP = 0x1A4A9A;

	private RipjawsMoves() {
	}

	/** Nass (Wasser, Regen, eigene Zone) ist alles staerker. */
	private static float wet(ServerPlayerEntity player) {
		return TideZones.isWet(player) ? 1.4f : 1.0f;
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("water_jet"), RipjawsMoves::waterJet);
		AbilityRegistry.register(KingdomOmnitrix.id("geyser"), RipjawsMoves::geyser);
		AbilityRegistry.register(KingdomOmnitrix.id("feeding_frenzy"), RipjawsMoves::feedingFrenzy);
		AbilityRegistry.register(KingdomOmnitrix.id("bubble_shield"), RipjawsMoves::bubbleShield);
		AbilityRegistry.register(KingdomOmnitrix.id("riptide_pull"), RipjawsMoves::riptidePull);
		AbilityRegistry.register(KingdomOmnitrix.id("maelstrom"), RipjawsMoves::maelstrom);
		AbilityRegistry.register(KingdomOmnitrix.id("shark_ram"), RipjawsMoves::sharkRam);
		AbilityRegistry.register(KingdomOmnitrix.id("jaw_dive"), RipjawsMoves::jawDive);
		AbilityRegistry.register(KingdomOmnitrix.id("drag_under"), RipjawsMoves::dragUnder);
		AbilityRegistry.register(KingdomOmnitrix.id("splash_down"), RipjawsMoves::splashDown);
		AbilityRegistry.register(KingdomOmnitrix.id("death_bite"), RipjawsMoves::deathBite);
		PassiveRegistry.register(KingdomOmnitrix.id("tidal_strength"), new AlienPassive() {
			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				// nass schlaegt Ripjaws haerter zu (Wasser, Regen, eigene Gezeitenzone)
				return TideZones.isWet(ctx.player()) ? amount * (float) ctx.param("wet_bonus", 1.25) : amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("deep_hunter"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// unter Wasser in seinem Element: sieht im Dunkeln, heilt langsam
				ServerPlayerEntity player = ctx.player();
				if (ctx.now() % 20 == 0 && player.isSubmergedInWater()) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, true));
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, true, false, true));
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("blood_scent"), new AlienPassive() {
			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				// Blut im Wasser: angeschlagene Ziele (unter der Haelfte) treffen ihn wie ein Hai — mehr Schaden, etwas Heilung
				if (target.getHealth() < target.getMaxHealth() * 0.5f) {
					ctx.player().heal((float) ctx.param("heal", 1.0));
					return amount * (float) ctx.param("bonus", 1.2);
				}
				return amount;
			}
		});
	}

	private static void splash(ServerWorld world, Vec3d at, int count) {
		world.spawnParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, count, 0.6, 0.3, 0.6, 0.3);
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Wasserstrahl: Hochdruckstrahl aus dem Maul — Schaden, Rueckstoss, am Ende eine kleine Gezeitenzone. */
	private static boolean waterJet(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		AlienKit.Beam beam = AlienKit.beam(world, player, ctx.param("range", 14.0));
		Vec3d from = AlienKit.muzzle(player);
		float damage = (float) ctx.param("damage", 6.0) * wet(player);
		for (LivingEntity target : AlienKit.along(world, player, from, beam.end(), 0.8)) {
			AlienKit.melee(world, player, target, damage);
			AlienKit.push(target, player.getPos(), 1.2, 0.2);
			target.extinguish();
		}
		Fx.beam(world, from, beam.end(), WATER, 0.35f, 8);
		Fx.beam(world, from, beam.end(), FOAM, 0.12f, 8);
		splash(world, beam.end(), 20);
		RipjawsAbilities.zone(player, beam.end(), 2.0, 100);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 1.0f);
		return true;
	}

	/** Wasserfontaene: eine Fontaene schiesst Ripjaws hoch und schleudert Gegner ringsum mit in die Luft. */
	private static boolean geyser(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		BuiltinAbilities.launch(player, player.getVelocity().x, ctx.param("up", 1.5) * wet(player), player.getVelocity().z);
		for (LivingEntity target : AlienKit.blast(world, player, player.getPos().add(0, 0.5, 0), ctx.param("radius", 3.5),
				(float) ctx.param("damage", 4.0), 0.4, 1.0)) {
			target.extinguish();
		}
		for (int h = 0; h < 6; h++) {
			Fx.smoke(world, player.getPos().add(0, h * 0.8, 0), FOAM, 1.0f, 1);
		}
		splash(world, player.getPos(), 40);
		Fx.shockwave(world, player.getPos().add(0, 0.1, 0), WATER, 3.5f, 8);
		RipjawsAbilities.zone(player, player.getPos(), 3.0, 120);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_SPLASH, 1.4f, 0.7f);
		return true;
	}

	/** Fressrausch: sechs schnelle Bisse nach vorn in zwei Sekunden; jeder heilt ein wenig. */
	private static boolean feedingFrenzy(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 3.0) * wet(player);
		PowerTasks.start(player, 36, 6, (world, p, age) -> {
			boolean hit = false;
			for (LivingEntity target : AlienKit.cone(world, p, ctx.param("range", 3.5), 0.5)) {
				AlienKit.melee(world, p, target, damage);
				hit = true;
			}
			if (hit) {
				p.heal(1.0f);
			}
			Vec3d jaw = p.getEyePos().add(p.getRotationVec(1.0f).multiply(1.4));
			Fx.flash(world, jaw, FOAM, 0.8f);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_FOX_BITE, SoundCategory.PLAYERS, 1.0f, 0.6f + age * 0.01f);
			return true;
		});
		return true;
	}

	/** Blasenschild: eine Wasserblase umgibt Ripjaws 6 s — zaehlt als Wasser, weniger Schaden, Geschosse werden gebremst. */
	private static boolean bubbleShield(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 1, false, false));
		PowerTasks.start(player, ticks, 5, (world, p, age) -> {
			RipjawsAbilities.zone(p, p.getPos(), 2.0, 10);
			for (var projectile : world.getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class, p.getBoundingBox().expand(3.0),
					e -> e.getOwner() != p)) {
				projectile.setVelocity(projectile.getVelocity().multiply(0.3));
				projectile.velocityModified = true;
			}
			if (age % 10 == 0) {
				Fx.aura(world, p, WATER, 1.3f, 12);
			}
			return true;
		});
		splash(ctx.world(), player.getPos().add(0, 1.0, 0), 30);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1.4f, 1.0f);
		return true;
	}

	/** Sogstroemung: eine Stroemung reisst alle Gegner bis 10 Bloecke zu Ripjaws heran und bremst sie. */
	private static boolean riptidePull(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 10.0);
		int pulled = 0;
		for (LivingEntity target : AlienKit.around(world, player, player.getPos(), radius)) {
			Vec3d pull = player.getPos().subtract(target.getPos());
			double d = pull.length();
			if (d < 1.5) {
				continue;
			}
			target.setVelocity(pull.normalize().multiply(Math.min(1.6, d * 0.18)).add(0, 0.3, 0));
			target.velocityModified = true;
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2), player);
			Fx.beam(world, AlienKit.body(target), player.getPos().add(0, 1.0, 0), WATER, 0.15f, 8);
			pulled++;
		}
		RipjawsAbilities.zone(player, player.getPos(), 3.5, 140);
		Fx.shockwave(world, player.getPos().add(0, 0.1, 0), WATER, (float) radius, 14);
		if (pulled == 0) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_TRIDENT_RIPTIDE_2.value(), 1.2f, 0.7f);
		return true;
	}

	/** Mahlstrom (Ultimate): riesiger Strudel am Zielpunkt — zieht, dreht und verletzt 6 s lang, endet in einer Flutwelle. */
	private static boolean maelstrom(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d center = AlienKit.ground(ctx.world(), AlienKit.aim(ctx.world(), player, ctx.param("range", 18.0)), 10.0);
		double radius = ctx.param("radius", 9.0);
		float damage = (float) ctx.param("damage", 3.0);
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		RipjawsAbilities.zone(player, center, radius, ticks + 100);
		PowerTasks.start(player, ticks, 2, (world, p, age) -> {
			double spin = age * 0.25;
			for (int i = 0; i < 4; i++) {
				double a = spin + i * MathHelper.TAU / 4;
				double r = radius * (0.3 + 0.7 * ((age / 2 + i * 3) % 10) / 10.0);
				Fx.smoke(world, center.add(Math.cos(a) * r, 0.3, Math.sin(a) * r), i % 2 == 0 ? WATER : FOAM, 1.0f, 1);
			}
			if (age % 10 == 0) {
				for (LivingEntity target : AlienKit.around(world, p, center.add(0, 1.0, 0), radius)) {
					Vec3d to = center.subtract(target.getPos()).multiply(1, 0, 1);
					Vec3d tangent = new Vec3d(-to.z, 0, to.x).normalize().multiply(0.4);
					Vec3d pull = to.lengthSquared() > 0.25 ? to.normalize().multiply(0.35) : Vec3d.ZERO;
					target.addVelocity(pull.x + tangent.x, 0.05, pull.z + tangent.z);
					target.velocityModified = true;
					AlienKit.melee(world, p, target, damage);
					target.extinguish();
				}
				splash(world, center, 30);
				world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundCategory.PLAYERS, 1.6f, 0.6f);
			}
			return true;
		}, (p, aborted) -> {
			if (aborted) {
				return;
			}
			ServerWorld world = p.getServerWorld();
			AlienKit.blast(world, p, center.add(0, 0.8, 0), radius, damage * 4.0f, 1.8, 0.6);
			Fx.shockwave(world, center.add(0, 0.2, 0), WATER, (float) radius * 1.2f, 14);
			splash(world, center, 120);
			Fx.shake(world, center, 0.5f, 12, 24);
			world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 2.0f, 0.5f);
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_TRIDENT_THUNDER.value(), 0.8f, 1.4f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Hairamme (Sprint + Schlag): Kopfstoss mit dem ganzen Koerper — weit weggeschleudert, nass doppelt so weit. */
	private static boolean sharkRam(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		double force = 1.3 * wet(player) * wet(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 5.0) * wet(player));
		target.setVelocity(dir.x * force, 0.4, dir.z * force);
		target.velocityModified = true;
		splash(ctx.world(), AlienKit.body(target), 16);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
		return true;
	}

	/** Kiefersturz (Schlag in der Luft): Biss von oben, reisst das Ziel mit nach unten. */
	private static boolean jawDive(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0) * wet(player));
		target.setVelocity(0.0, -1.4, 0.0);
		target.velocityModified = true;
		Fx.flash(ctx.world(), AlienKit.body(target), FOAM, 1.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_FOX_BITE, 1.4f, 0.5f);
		return true;
	}

	/** Unterwasserzug (Schleichen + Schlag): zieht das Ziel unter Wasser — festgehalten, es geht ihm die Luft aus. */
	private static boolean dragUnder(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 2.5) * 20);
		AlienKit.hold(ctx.world(), target, ticks, AlienKit.dust("#3A8CFF", 1.2f), false);
		RipjawsAbilities.zone(player, target.getPos(), 2.0, ticks + 40);
		UUID id = target.getUuid();
		float damage = (float) ctx.param("damage", 2.0);
		PowerTasks.start(player, ticks, 10, (world, p, age) -> {
			if (!(world.getEntity(id) instanceof LivingEntity t) || !t.isAlive()) {
				return false;
			}
			AlienKit.hit(t, world.getDamageSources().drown(), damage);
			world.spawnParticles(ParticleTypes.BUBBLE, t.getX(), t.getEyeY(), t.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_DROWNED_SWIM, 1.2f, 0.7f);
		return true;
	}

	/** Wasserbombe (hoch in der Luft schleichen): Sturz, beim Aufprall eine Flutwelle und eine Gezeitenzone. */
	private static boolean splashDown(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 7.0);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 1.8), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				return true;
			}
			AlienKit.blast(world, p, p.getPos().add(0, 0.6, 0), 4.0, damage * wet(p), 1.2, 0.5);
			RipjawsAbilities.zone(p, p.getPos(), 4.0, 140);
			splash(world, p.getPos(), 80);
			Fx.shockwave(world, p.getPos().add(0, 0.1, 0), WATER, 5.0f, 10);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.8f, 0.6f);
			return false;
		});
		return true;
	}

	/** Todesbiss (Schlag gegen geschwaechtes Ziel): die Kiefer schliessen sich endgueltig — hoher Schaden, viel Heilung. */
	private static boolean deathBite(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		float damage = ((float) ctx.param("damage", 10.0) + (target.getMaxHealth() - target.getHealth()) * 0.2f) * wet(player);
		AlienKit.melee(ctx.world(), player, target, damage);
		player.heal(damage * (float) ctx.param("heal", 0.4));
		Fx.flash(ctx.world(), AlienKit.body(target), DEEP, 2.2f);
		splash(ctx.world(), AlienKit.body(target), 30);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ATTACK, 1.4f, 0.6f);
		return true;
	}
}
