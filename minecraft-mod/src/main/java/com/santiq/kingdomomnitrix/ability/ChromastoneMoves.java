package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.world.content.TempBlocks;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/**
 * Chromastone, Kraefte 2.0: Lichtlanze, Kristallspitzen, Spiegelprisma, Blendblitz, Energieentladung, Spektraler
 * Kataklysmus; Kombos Prismenansturm, Sternenfall, Absorptionsgriff, Prismenmeteor, Spektralbruch; Passive
 * Solarzellen, Kristallresonanz und Brechung. Alles speist oder leert den Spektralspeicher aus
 * {@link ChromastoneAbilities} (Laden, Abgeben, Nova).
 */
final class ChromastoneMoves {
	private static final int VIOLET = 0xB46AFF;
	private static final int WHITE = 0xFFFFFF;

	private ChromastoneMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("light_lance"), ChromastoneMoves::lightLance);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_spires"), ChromastoneMoves::crystalSpires);
		AbilityRegistry.register(KingdomOmnitrix.id("mirror_prism"), ChromastoneMoves::mirrorPrism);
		AbilityRegistry.register(KingdomOmnitrix.id("blinding_flash"), ChromastoneMoves::blindingFlash);
		AbilityRegistry.register(KingdomOmnitrix.id("energy_release"), ChromastoneMoves::energyRelease);
		AbilityRegistry.register(KingdomOmnitrix.id("spectral_cataclysm"), ChromastoneMoves::spectralCataclysm);
		AbilityRegistry.register(KingdomOmnitrix.id("prism_rush"), ChromastoneMoves::prismRush);
		AbilityRegistry.register(KingdomOmnitrix.id("starfall_strike"), ChromastoneMoves::starfallStrike);
		AbilityRegistry.register(KingdomOmnitrix.id("absorb_touch"), ChromastoneMoves::absorbTouch);
		AbilityRegistry.register(KingdomOmnitrix.id("meteor_prism"), ChromastoneMoves::meteorPrism);
		AbilityRegistry.register(KingdomOmnitrix.id("spectrum_break"), ChromastoneMoves::spectrumBreak);
		PassiveRegistry.register(KingdomOmnitrix.id("solar_cells"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// Sonnenlicht laedt den Speicher langsam auf
				ServerPlayerEntity player = ctx.player();
				ServerWorld world = ctx.world();
				if (ctx.now() % 20 == 0 && world.isDay() && !world.isRaining()
						&& world.getLightLevel(LightType.SKY, player.getBlockPos()) >= 14) {
					ChromastoneAbilities.charge(player, (float) ctx.param("per_second", 1.0));
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("crystal_resonance"), new AlienPassive() {
			@Override
			public void melee(PassiveContext ctx, LivingEntity target, boolean fullSwing) {
				// jeder Schlag laesst den Kristallkoerper schwingen und laedt etwas Speicher
				ChromastoneAbilities.charge(ctx.player(), (float) ctx.param("charge", 3.0) * (fullSwing ? 1.0f : 0.4f));
				Fx.sparks(ctx.world(), AlienKit.body(target), AlienKit.color(ChromastoneAbilities.SPECTRUM[ctx.player().getRandom().nextInt(7)]), 0.3f, 4);
			}

			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				// voller Speicher: Schlaege leuchten und treffen haerter
				return ChromastoneAbilities.store(ctx.player()) >= ChromastoneAbilities.MAX * 0.75f && source.getSource() == ctx.player()
						? amount * (float) ctx.param("charged_bonus", 1.2) : amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("refraction"), new AlienPassive() {
			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				// auch koerperliche Treffer bricht der Kristall teilweise zu Licht
				if (!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
					ChromastoneAbilities.charge(ctx.player(), damage * (float) ctx.param("rate", 2.0));
				}
			}
		});
	}

	private static int spectrum(int i) {
		return AlienKit.color(ChromastoneAbilities.SPECTRUM[Math.floorMod(i, ChromastoneAbilities.SPECTRUM.length)]);
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Lichtlanze: durchschlagender Strahl — trifft alles in der Linie; bis 15 Speicher werden zu Bonusschaden. */
	private static boolean lightLance(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float damage = (float) ctx.param("damage", 6.0) + ChromastoneAbilities.spend(player, 15.0f) * 0.3f;
		Vec3d from = AlienKit.muzzle(player);
		Vec3d to = player.getEyePos().add(player.getRotationVec(1.0f).multiply(ctx.param("range", 22.0)));
		to = world.raycast(new net.minecraft.world.RaycastContext(player.getEyePos(), to, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
				net.minecraft.world.RaycastContext.FluidHandling.NONE, player)).getPos();
		for (LivingEntity target : AlienKit.along(world, player, from, to, 0.6)) {
			ChromastoneAbilities.hit(world, player, target, damage);
		}
		Fx.beam(world, from, to, WHITE, 0.3f, 6);
		ChromastoneAbilities.rainbow(world, from, to, 2.0);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.8f, 2.0f);
		return true;
	}

	/** Kristallspitzen: ein Ring aus Kristallen bricht um Chromastone hervor — Schaden und hochgeschleudert. */
	private static boolean crystalSpires(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 4.0);
		int ticks = (int) (ctx.param("seconds", 4.0) * 20);
		for (int i = 0; i < 16; i++) {
			double a = i * MathHelper.TAU / 16;
			BlockPos ground = TempBlocks.surface(world, BlockPos.ofFloored(player.getPos().add(Math.cos(a) * radius, 0, Math.sin(a) * radius)), 4);
			if (ground != null) {
				TempBlocks.place(world, ground, Blocks.AMETHYST_CLUSTER.getDefaultState(), ticks, player, VIOLET, false);
			}
		}
		for (LivingEntity target : AlienKit.around(world, player, player.getPos().add(0, 0.5, 0), radius + 1.0)) {
			ChromastoneAbilities.hit(world, player, target, (float) ctx.param("damage", 6.0));
			target.addVelocity(0.0, 0.8, 0.0);
			target.velocityModified = true;
		}
		Fx.shards(world, player.getPos().add(0, 0.5, 0), VIOLET, 0.6f, 30);
		Fx.shockwave(world, player.getPos().add(0, 0.2, 0), VIOLET, (float) radius + 1.0f, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 1.6f, 0.7f);
		return true;
	}

	/** Spiegelprisma: ein schwebendes Prisma am Zielpunkt feuert 8 s lang jede Sekunde auf den naechsten Gegner. */
	private static boolean mirrorPrism(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d at = AlienKit.ground(world, AlienKit.aim(world, player, ctx.param("range", 12.0)), 6.0).add(0, 1.8, 0);
		double reach = ctx.param("reach", 10.0);
		float damage = (float) ctx.param("damage", 4.0);
		PowerTasks.start(player, (int) (ctx.param("seconds", 8.0) * 20), 2, (w, p, age) -> {
			Fx.sparks(w, at.add(Math.cos(age * 0.3) * 0.3, Math.sin(age * 0.2) * 0.2, Math.sin(age * 0.3) * 0.3), spectrum(age / 2), 0.05f, 2);
			if (age % 20 != 0) {
				return true;
			}
			List<LivingEntity> near = AlienKit.around(w, p, at, reach);
			near.stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(at))).ifPresent(target -> {
				ChromastoneAbilities.rainbow(w, at, AlienKit.body(target), 2.0);
				ChromastoneAbilities.hit(w, p, target, damage);
				w.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.6f);
			});
			return true;
		});
		Fx.flash(world, at, VIOLET, 1.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.2f);
		return true;
	}

	/** Blendblitz: greller Lichtkegel — Gegner sind 5 s blind und schwach; jeder Getroffene laedt etwas Speicher. */
	private static boolean blindingFlash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 5.0) * 20);
		int count = 0;
		for (LivingEntity target : AlienKit.cone(world, player, ctx.param("range", 12.0), 0.5)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, ticks, 0, false, true), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1, false, true), player);
			count++;
		}
		ChromastoneAbilities.charge(player, count * (float) ctx.param("charge", 5.0));
		Vec3d look = player.getRotationVec(1.0f);
		Fx.flash(world, player.getEyePos().add(look.multiply(1.5)), WHITE, 4.0f);
		Fx.send(world, Fx.Kind.SHOCKWAVE, player.getEyePos().add(look.multiply(3.0)), look, WHITE, 4.0f, 8, 1, null);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2f, 2.0f);
		return true;
	}

	/** Energieentladung: der ganze Speicher in einem dicken Strahl — Schaden waechst mit der gespeicherten Energie. */
	private static boolean energyRelease(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float used = ChromastoneAbilities.spend(player, ChromastoneAbilities.MAX);
		float damage = (float) ctx.param("damage", 6.0) + used * (float) ctx.param("per_store", 0.25);
		Vec3d from = AlienKit.muzzle(player);
		AlienKit.Beam beam = AlienKit.beam(world, player, ctx.param("range", 26.0));
		for (LivingEntity target : AlienKit.along(world, player, from, beam.end(), 1.2)) {
			ChromastoneAbilities.hit(world, player, target, damage);
			AlienKit.push(target, player.getPos(), 1.0 + used / 100.0, 0.3);
		}
		Fx.beam(world, from, beam.end(), WHITE, 0.6f + used / 200.0f, 10);
		for (int i = 0; i < 7; i++) {
			Fx.beam(world, from, beam.end().add(Math.cos(i) * 0.3, Math.sin(i) * 0.3, 0), spectrum(i), 0.15f, 10);
		}
		Fx.explosion(world, beam.end(), VIOLET, 1.0f + used / 50.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.6f);
		return true;
	}

	/**
	 * Spektraler Kataklysmus (Ultimate): laedt 2 s lang alles Licht der Umgebung auf (Speicher voll), dann eine riesige
	 * Regenbogen-Nova mit Kristallring — blendet, stoesst weg, gewaltiger Schaden.
	 */
	private static boolean spectralCataclysm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int charge = (int) (ctx.param("charge_seconds", 2.0) * 20);
		double radius = ctx.param("radius", 14.0);
		float damage = (float) ctx.param("damage", 18.0);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, charge, 4, false, false));
		ctx.grantInvulnerability(charge + 10);
		Fx.charge(ctx.world(), player, WHITE, 2.0f, charge);
		PowerTasks.start(player, charge + 1, 1, (world, p, age) -> {
			ChromastoneAbilities.charge(p, ChromastoneAbilities.MAX / charge);
			double r = 4.0 * (1.0 - age / (double) charge) + 0.5;
			for (int i = 0; i < 7; i++) {
				double a = age * 0.25 + i * MathHelper.TAU / 7;
				Fx.sparks(world, AlienKit.body(p).add(Math.cos(a) * r, 0, Math.sin(a) * r), spectrum(i), 0.02f, 1);
			}
			if (age < charge) {
				return true;
			}
			ChromastoneAbilities.spend(p, ChromastoneAbilities.MAX);
			ChromastoneAbilities.nova(world, p, radius, damage);
			for (int i = 0; i < 24; i++) {
				double a = i * MathHelper.TAU / 24;
				BlockPos ground = TempBlocks.surface(world, BlockPos.ofFloored(p.getPos().add(Math.cos(a) * radius * 0.5, 0, Math.sin(a) * radius * 0.5)), 5);
				if (ground != null) {
					TempBlocks.place(world, ground, Blocks.AMETHYST_CLUSTER.getDefaultState(), 160, p, VIOLET, false);
				}
			}
			Fx.flash(world, AlienKit.body(p), WHITE, 8.0f);
			for (int ring = 0; ring < 7; ring++) {
				Fx.shockwave(world, p.getPos().add(0, 0.3 + ring * 0.15, 0), spectrum(ring), (float) (radius * (ring + 1) / 7.0), 16);
			}
			Fx.shake(world, p.getPos(), 0.7f, 16, (int) radius * 3);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.5f, 1.2f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.4f, 0.8f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Prismenansturm (Sprint + Schlag): Kristallschulter — der Treffer bricht sich auf zwei Gegner daneben. */
	private static boolean prismRush(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		float damage = (float) ctx.param("damage", 6.0);
		AlienKit.melee(world, player, target, damage);
		target.setVelocity(dir.x * 1.2, 0.4, dir.z * 1.2);
		target.velocityModified = true;
		AlienKit.around(world, player, AlienKit.body(target), 6.0).stream().filter(e -> e != target)
				.sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(target))).limit(2).forEach(next -> {
					ChromastoneAbilities.rainbow(world, AlienKit.body(target), AlienKit.body(next), 2.0);
					ChromastoneAbilities.hit(world, player, next, damage * 0.5f);
				});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 1.4f, 0.8f);
		return true;
	}

	/** Sternenfall (Schlag in der Luft): Lichtspeer nach unten — Ziel zu Boden, kleine Nova am Ziel. */
	private static boolean starfallStrike(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float damage = (float) ctx.param("damage", 6.0);
		AlienKit.melee(world, player, target, damage);
		target.setVelocity(0.0, -1.2, 0.0);
		target.velocityModified = true;
		for (LivingEntity near : AlienKit.around(world, player, target.getPos().add(0, 0.5, 0), 2.5)) {
			if (near != target) {
				ChromastoneAbilities.hit(world, player, near, damage * 0.5f);
			}
		}
		Fx.beam(world, AlienKit.body(player), target.getPos(), WHITE, 0.3f, 6);
		Fx.shockwave(world, target.getPos().add(0, 0.2, 0), VIOLET, 2.5f, 8);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, 1.4f, 1.2f);
		return true;
	}

	/** Absorptionsgriff (Schleichen + Schlag): saugt dem Ziel Energie aus — es wird langsam, Chromastone laedt stark. */
	private static boolean absorbTouch(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 3.0));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2, false, true), player);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, false, true), player);
		ChromastoneAbilities.charge(player, (float) ctx.param("charge", 20.0));
		ChromastoneAbilities.rainbow(ctx.world(), AlienKit.body(target), AlienKit.body(player), 3.0);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.6f);
		return true;
	}

	/** Prismenmeteor (hoch in der Luft schleichen): stuerzt als Lichtkomet — Nova beim Aufschlag, staerker je hoeher. */
	private static boolean meteorPrism(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 7.0);
		double radius = ctx.param("radius", 3.5);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.2), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				Fx.sparks(world, p.getPos().add(0, 1.0, 0), spectrum(age), 0.15f, 2);
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			ChromastoneAbilities.nova(world, p, radius * boost, damage * boost);
			ChromastoneAbilities.charge(p, 10.0f * boost);
			return false;
		});
		return true;
	}

	/** Spektralbruch (Schlag gegen geschwaechtes Ziel): das volle Spektrum zerbricht im Ziel — sieben Strahlen ringsum. */
	private static boolean spectrumBreak(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d at = AlienKit.body(target);
		float damage = (float) (ctx.param("damage", 10.0) + (target.getMaxHealth() - target.getHealth()) * 0.2)
				+ ChromastoneAbilities.spend(player, 30.0f) * 0.2f;
		AlienKit.melee(world, player, target, damage);
		for (int i = 0; i < 7; i++) {
			double a = i * MathHelper.TAU / 7;
			Fx.beam(world, at, at.add(Math.cos(a) * 6.0, 0.2, Math.sin(a) * 6.0), spectrum(i), 0.15f, 8);
		}
		for (LivingEntity near : AlienKit.around(world, player, at, ctx.param("radius", 6.0))) {
			if (near != target) {
				ChromastoneAbilities.hit(world, player, near, damage * 0.4f);
			}
		}
		Fx.flash(world, at, WHITE, 3.0f);
		Fx.shards(world, at, VIOLET, 0.6f, 24);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, 1.6f, 0.6f);
		return true;
	}
}
