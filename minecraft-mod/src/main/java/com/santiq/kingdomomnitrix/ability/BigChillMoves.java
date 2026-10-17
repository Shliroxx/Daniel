package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.world.content.PlanetBlocks;
import com.santiq.kingdomomnitrix.world.content.TempBlocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Big Chill, Kraefte 2.0: Frostlanze, Frostboden, Fluegelhuelle, Eisdornen, Blizzard, Eiskomet; Kombos Kaeltesturm,
 * Eiszapfen, Frostgriff, Gletschersturz, Zersplitterschlag; Passive Unterkuehlung (Kern-System), Frostkoerper und
 * Phantomfluegel. Alles kuehlt ueber {@link BigChillAbilities#cool} — bei 100 erstarrt das Ziel, harte Treffer
 * zersplittern es.
 */
final class BigChillMoves {
	private static final int ICE = BigChillAbilities.ICE_COLOR;
	private static final int DEEP = BigChillAbilities.DEEP_COLOR;

	private BigChillMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("frost_lance"), BigChillMoves::frostLance);
		AbilityRegistry.register(KingdomOmnitrix.id("frost_ground"), BigChillMoves::frostGround);
		AbilityRegistry.register(KingdomOmnitrix.id("wing_wrap"), BigChillMoves::wingWrap);
		AbilityRegistry.register(KingdomOmnitrix.id("ice_spikes"), BigChillMoves::iceSpikes);
		AbilityRegistry.register(KingdomOmnitrix.id("blizzard"), BigChillMoves::blizzard);
		AbilityRegistry.register(KingdomOmnitrix.id("ice_comet"), BigChillMoves::iceComet);
		AbilityRegistry.register(KingdomOmnitrix.id("cold_rush"), BigChillMoves::coldRush);
		AbilityRegistry.register(KingdomOmnitrix.id("icicle_drop"), BigChillMoves::icicleDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("frost_grip"), BigChillMoves::frostGrip);
		AbilityRegistry.register(KingdomOmnitrix.id("glacier_fall"), BigChillMoves::glacierFall);
		AbilityRegistry.register(KingdomOmnitrix.id("shatter_strike"), BigChillMoves::shatterStrike);
		PassiveRegistry.register(KingdomOmnitrix.id("hypothermia"), new AlienPassive() {
			@Override
			public void afterHit(PassiveContext ctx, LivingEntity target, DamageSource source, float damage) {
				// direkte Treffer (Schlaege, Atem, Kraefte) unterkuehlen; Eissplitter kuehlen ueber das Kern-System
				if (source.getSource() == ctx.player()) {
					BigChillAbilities.cool(ctx.player(), target, (float) ctx.param("chill", 15.0));
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("frost_body"), new AlienPassive() {
			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				// wer Big Chill im Nahkampf trifft, friert an
				if (source.getAttacker() instanceof LivingEntity attacker && source.getSource() == attacker && attacker != ctx.player()
						&& AlienKit.foe(ctx.player(), attacker)) {
					BigChillAbilities.cool(ctx.player(), attacker, (float) ctx.param("chill", 12.0));
				}
			}

			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Necrofriggianer halten Hitze besser aus als man denkt — aber nicht ganz
				return source.isIn(DamageTypeTags.IS_FIRE) ? amount * (float) ctx.param("fire", 0.8) : amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("phantom_wings"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Phasen: manche Schlaege und Geschosse gehen einfach durch ihn hindurch
				if (source.getAttacker() == null || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
					return amount;
				}
				ServerPlayerEntity player = ctx.player();
				if (player.getRandom().nextFloat() < (float) ctx.param("chance", 0.2)) {
					Fx.smoke(ctx.world(), AlienKit.body(player), DEEP, 0.8f, 4);
					ctx.world().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.PLAYERS, 0.6f, 0.5f);
					return 0.0f;
				}
				return amount;
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Frostlanze: gebuendelter Eisstrahl — stark unterkuehlt; ein erstarrtes Ziel zersplittert sofort. */
	private static boolean frostLance(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		AlienKit.Beam beam = AlienKit.beam(world, player, ctx.param("range", 20.0));
		Fx.beam(world, AlienKit.muzzle(player), beam.end(), ICE, 0.35f, 8);
		Fx.shards(world, beam.end(), ICE, 0.35f, 12);
		beam.target().ifPresent(target -> {
			if (BigChillAbilities.frozen(target)) {
				BigChillAbilities.shatter(world, player, target);
			} else {
				AlienKit.magic(world, player, target, (float) ctx.param("damage", 5.0));
				BigChillAbilities.cool(player, target, (float) ctx.param("chill", 40.0));
			}
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_GLASS_HIT, 1.2f, 0.7f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 1.4f);
		return true;
	}

	/** Frostboden: friert den Boden am Zielpunkt — wer darauf steht, kuehlt jede Sekunde weiter aus. */
	private static boolean frostGround(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d center = AlienKit.ground(world, AlienKit.aim(world, player, ctx.param("range", 16.0)), 8.0);
		double radius = ctx.param("radius", 5.0);
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		float chill = (float) ctx.param("chill", 15.0);
		BlockPos at = BlockPos.ofFloored(center);
		for (int r = 1; r <= (int) radius; r += 2) {
			BigChillAbilities.iceRing(world, player, at, r, ticks, true);
		}
		BigChillAbilities.freezeWater(world, at.down(), (int) radius);
		Fx.shockwave(world, center.add(0, 0.2, 0), ICE, (float) radius, 12);
		PowerTasks.start(player, ticks, 20, (w, p, age) -> {
			for (LivingEntity target : AlienKit.around(w, p, center.add(0, 0.5, 0), radius)) {
				if (target.getY() - center.y < 1.5) {
					BigChillAbilities.cool(p, target, chill);
				}
			}
			Fx.smoke(w, center.add(0, 0.3, 0), 0xE6F4FF, (float) radius * 0.4f, 3);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_GLASS_PLACE, 1.4f, 0.5f);
		return true;
	}

	/** Fluegelhuelle: huellt sich 4 s in die Fluegel — viel weniger Schaden, Angreifer frieren an; danach Frostausbruch. */
	private static boolean wingWrap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 4.0) * 20);
		float burstChill = (float) ctx.param("burst_chill", 40.0);
		double radius = ctx.param("radius", 4.5);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, (int) ctx.param("resistance", 2.0), false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 2, false, false));
		Fx.aura(ctx.world(), player, DEEP, 1.2f, ticks);
		PowerTasks.start(player, ticks, 5, (w, p, age) -> {
			for (ProjectileEntity projectile : w.getEntitiesByClass(ProjectileEntity.class, p.getBoundingBox().expand(1.5), e -> e.getOwner() != p)) {
				projectile.setVelocity(projectile.getVelocity().multiply(-0.4));
				projectile.velocityModified = true;
			}
			return true;
		}, (p, aborted) -> {
			if (aborted) {
				return;
			}
			ServerWorld w = p.getServerWorld();
			for (LivingEntity target : AlienKit.around(w, p, AlienKit.body(p), radius)) {
				BigChillAbilities.cool(p, target, burstChill);
				AlienKit.push(target, p.getPos(), 1.0, 0.3);
			}
			Fx.shockwave(w, p.getPos().add(0, 0.5, 0), ICE, (float) radius, 10);
			Fx.shards(w, AlienKit.body(p), ICE, 0.6f, 30);
			w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.2f, 0.5f);
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 0.7f);
		return true;
	}

	/** Eisdornen: eine Reihe Eisspitzen bricht vor Big Chill aus dem Boden — trifft, hebt an und unterkuehlt. */
	private static boolean iceSpikes(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		int length = (int) ctx.param("length", 12.0);
		float damage = (float) ctx.param("damage", 6.0);
		float chill = (float) ctx.param("chill", 30.0);
		java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();
		Vec3d origin = player.getPos();
		PowerTasks.start(player, length, 1, (world, p, age) -> {
			Vec3d at = origin.add(dir.multiply(age + 1.5));
			BlockPos ground = TempBlocks.surface(world, BlockPos.ofFloored(at), 4);
			if (ground != null) {
				TempBlocks.place(world, ground, PlanetBlocks.NECROFRIGGIAN_SPIKE.getDefaultState(), 60, p, ICE, false);
			}
			Fx.shards(world, at.add(0, 0.6, 0), ICE, 0.3f, 6);
			for (LivingEntity target : AlienKit.around(world, p, at.add(0, 0.6, 0), 1.4)) {
				if (hit.add(target.getUuid())) {
					AlienKit.magic(world, p, target, damage);
					BigChillAbilities.cool(p, target, chill);
					target.addVelocity(0.0, 0.7, 0.0);
					target.velocityModified = true;
				}
			}
			if (age % 3 == 0) {
				world.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 0.8f, 0.6f + age * 0.03f);
			}
			return true;
		});
		return true;
	}

	/** Blizzard: 6 s Schneesturm am Zielpunkt — unterkuehlt, blendet und schiebt Gegner herum. */
	private static boolean blizzard(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d center = AlienKit.ground(world, AlienKit.aim(world, player, ctx.param("range", 18.0)), 8.0);
		double radius = ctx.param("radius", 6.0);
		float chill = (float) ctx.param("chill", 12.0);
		float damage = (float) ctx.param("damage", 1.5);
		PowerTasks.start(player, (int) (ctx.param("seconds", 6.0) * 20), 2, (w, p, age) -> {
			for (int i = 0; i < 6; i++) {
				double a = w.random.nextDouble() * MathHelper.TAU;
				double r = w.random.nextDouble() * radius;
				w.spawnParticles(ParticleTypes.SNOWFLAKE, center.x + Math.cos(a) * r, center.y + 0.5 + w.random.nextDouble() * 4.0,
						center.z + Math.sin(a) * r, 1, 0.3, 0.0, 0.3, 0.05);
			}
			if (age % 20 == 0) {
				for (LivingEntity target : AlienKit.around(w, p, center.add(0, 1.0, 0), radius)) {
					AlienKit.magic(w, p, target, damage);
					BigChillAbilities.cool(p, target, chill);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0, false, true), p);
					double a = age * 0.4;
					target.addVelocity(Math.cos(a) * 0.3, 0.05, Math.sin(a) * 0.3);
					target.velocityModified = true;
				}
				Fx.smoke(w, center.add(0, 1.0, 0), 0xE6F4FF, (float) radius * 0.5f, 4);
				w.playSound(null, BlockPos.ofFloored(center), SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.PLAYERS, 1.0f, 0.5f);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_CHARGE, 1.0f, 0.5f);
		return true;
	}

	/**
	 * Eiskomet (Ultimate): ein riesiger Eisbrocken stuerzt nach 1,5 s auf den Zielpunkt. Alle im Einschlag erstarren
	 * zuerst — der Einschlag selbst zersplittert sie dann gleichzeitig.
	 */
	private static boolean iceComet(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d center = AlienKit.ground(world, AlienKit.aim(world, player, ctx.param("range", 30.0)), 16.0);
		double radius = ctx.param("radius", 7.0);
		float damage = (float) ctx.param("damage", 14.0);
		int fall = (int) ctx.param("fall_ticks", 30.0);
		Vec3d sky = center.add(-6.0, 24.0, -6.0);
		ctx.grantInvulnerability(20);
		PowerTasks.start(player, fall + 1, 1, (w, p, age) -> {
			Vec3d at = sky.lerp(center, age / (double) fall);
			Fx.shards(w, at, ICE, 0.2f, 6);
			Fx.smoke(w, at, 0xE6F4FF, 1.6f, 1);
			if (age % 6 == 0) {
				Fx.shockwave(w, center.add(0, 0.2, 0), DEEP, (float) radius * (age / (float) fall), 6);
			}
			if (age < fall) {
				return true;
			}
			// erst erstarren, dann der Einschlag: so zersplittern alle auf einmal
			for (LivingEntity target : AlienKit.around(w, p, center.add(0, 0.8, 0), radius)) {
				BigChillAbilities.cool(p, target, BigChillAbilities.MAX_CHILL);
			}
			AlienKit.blast(w, p, center.add(0, 0.8, 0), radius, damage, 1.2, 0.6);
			BlockPos at0 = BlockPos.ofFloored(center);
			for (int r = 2; r <= (int) radius; r += 2) {
				BigChillAbilities.iceRing(w, p, at0, r, 200, r % 4 == 0);
			}
			Fx.explosion(w, center.add(0, 1.0, 0), ICE, (float) radius * 0.6f);
			Fx.flash(w, center.add(0, 1.0, 0), 0xFFFFFF, 6.0f);
			Fx.shake(w, center, 0.8f, 16, (int) radius * 3);
			w.playSound(null, at0, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.6f, 0.6f);
			w.playSound(null, at0, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.6f, 0.4f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 0.6f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Kaeltesturm (Sprint + Schlag): phast durch das Ziel hindurch und taucht dahinter auf — es friert innerlich. */
	private static boolean coldRush(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		Vec3d from = AlienKit.body(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 5.0));
		BigChillAbilities.cool(player, target, (float) ctx.param("chill", 30.0));
		Vec3d behind = target.getPos().add(dir.multiply(target.getWidth() * 0.5 + 1.2));
		if (ctx.world().isSpaceEmpty(player, player.getBoundingBox().offset(behind.subtract(player.getPos())))) {
			player.requestTeleport(behind.x, behind.y, behind.z);
		}
		Fx.beam(ctx.world(), from, AlienKit.body(player), DEEP, 0.4f, 8);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_VEX_CHARGE, 1.0f, 0.5f);
		return true;
	}

	/** Eiszapfen (Schlag in der Luft): Eiszapfen von oben — Ziel wird zu Boden gerammt und unterkuehlt. */
	private static boolean icicleDrop(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0));
		BigChillAbilities.cool(player, target, (float) ctx.param("chill", 25.0));
		target.setVelocity(0.0, -1.2, 0.0);
		target.velocityModified = true;
		Fx.shards(ctx.world(), target.getPos().add(0, target.getHeight() + 0.5, 0), ICE, 0.4f, 14);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.3, 0.4, player.getVelocity().z * 0.3);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
		return true;
	}

	/** Frostgriff (Schleichen + Schlag): packt das Ziel mit eisigen Klauen — haelt es fest und kuehlt es tief. */
	private static boolean frostGrip(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 3.0));
		BigChillAbilities.cool(player, target, (float) ctx.param("chill", 50.0));
		AlienKit.hold(ctx.world(), target, (int) ctx.param("hold_ticks", 40.0), ParticleTypes.SNOWFLAKE, false);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.7f);
		return true;
	}

	/** Gletschersturz (hoch in der Luft schleichen): Sturz mit Eisring beim Aufschlag — staerker je hoeher. */
	private static boolean glacierFall(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 6.0);
		float chill = (float) ctx.param("chill", 35.0);
		double radius = ctx.param("radius", 4.0);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.2), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				Fx.shards(world, p.getPos().add(0, 1.0, 0), ICE, 0.1f, 2);
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.5, 0), radius * boost)) {
				AlienKit.magic(world, p, target, damage * boost);
				BigChillAbilities.cool(p, target, chill * boost);
				AlienKit.push(target, p.getPos(), 0.6, 0.3);
			}
			BigChillAbilities.iceRing(world, p, p.getBlockPos(), (int) Math.max(2, radius * boost * 0.6), 80, false);
			Fx.shockwave(world, p.getPos().add(0, 0.2, 0), ICE, (float) (radius * boost), 10);
			world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2f, 0.5f);
			return false;
		});
		return true;
	}

	/** Zersplitterschlag (Schlag gegen geschwaechtes Ziel): friert es schlagartig ein und zerschlaegt es im selben Moment. */
	private static boolean shatterStrike(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		if (!BigChillAbilities.frozen(target)) {
			BigChillAbilities.cool(player, target, BigChillAbilities.MAX_CHILL);
		}
		AlienKit.melee(world, player, target, (float) ctx.param("damage", 8.0));
		if (target.isAlive() && BigChillAbilities.frozen(target)) {
			BigChillAbilities.shatter(world, player, target);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_GLASS_BREAK, 1.4f, 0.6f);
		return true;
	}
}
