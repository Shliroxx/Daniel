package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Debris;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.vfx.EarthWave;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Vierarm, Kraefte 2.0: weitere Tasten-Faehigkeiten (Faustsalve, Aufwaertshaken, Bodenplatte, Riesenschwung, Kriegsschrei,
 * Titanen-Meteor) und Kombos aus der Bewegung (Rammstoss, Hammerfaust, Schulterwurf, Meteorfaust, Vierfach-Finale).
 * Druckwellen, Wuerfe und Landungen laufen ueber die Werkzeuge in {@link FourArmsAbilities}, damit alles gleich wirkt
 * und die Wut (Raserei) ueberall gilt.
 */
final class FourArmsMoves {
	private static final int IMPACT = 0xFFD890;
	private static final int DUST = 0xA08A6A;
	private static final int RED = 0xFF3020;
	private static final int GOLD = 0xFFC94A;

	/** Faustsalve: Restschlaege, naechster Schlag, Staerke */
	private record Barrage(int left, long next, float damage, double range, boolean rampage) {
	}

	/** Aufwaertshaken: Ziel, Zeitpunkt des Nachsetzens */
	private record Uppercut(UUID target, long followUp, float damage) {
	}

	/** Riesenschwung: gepacktes Ziel, Beginn, Dauer, Schaden fuer Getroffene und beim Loslassen */
	private static final class Swing {
		final UUID target;
		final long start;
		final int ticks;
		final float damage;
		final Set<UUID> hit = new HashSet<>();

		Swing(UUID target, long start, int ticks, float damage) {
			this.target = target;
			this.start = start;
			this.ticks = ticks;
			this.damage = damage;
		}
	}

	/** Titanen-Meteor: Phase 0 Aufstieg, 1 Sturz auf {@code aim} */
	private static final class Meteor {
		final double startY;
		final float damage;
		final double radius;
		final long until;
		int phase;
		Vec3d aim;

		Meteor(double startY, float damage, double radius, long until) {
			this.startY = startY;
			this.damage = damage;
			this.radius = radius;
			this.until = until;
		}
	}

	private static final Map<UUID, Barrage> BARRAGES = new HashMap<>();
	private static final Map<UUID, Uppercut> UPPERCUTS = new HashMap<>();
	private static final Map<UUID, Swing> SWINGS = new HashMap<>();
	private static final Map<UUID, Meteor> METEORS = new HashMap<>();

	private FourArmsMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("four_fist_barrage"), FourArmsMoves::barrage);
		AbilityRegistry.register(KingdomOmnitrix.id("rising_uppercut"), FourArmsMoves::uppercut);
		AbilityRegistry.register(KingdomOmnitrix.id("slab_heave"), FourArmsMoves::slabHeave);
		AbilityRegistry.register(KingdomOmnitrix.id("giant_swing"), FourArmsMoves::giantSwing);
		AbilityRegistry.register(KingdomOmnitrix.id("war_cry"), FourArmsMoves::warCry);
		AbilityRegistry.register(KingdomOmnitrix.id("titan_meteor"), FourArmsMoves::titanMeteor);
		AbilityRegistry.register(KingdomOmnitrix.id("shoulder_ram"), FourArmsMoves::shoulderRam);
		AbilityRegistry.register(KingdomOmnitrix.id("hammer_fist"), FourArmsMoves::hammerFist);
		AbilityRegistry.register(KingdomOmnitrix.id("grapple_toss"), FourArmsMoves::grappleToss);
		AbilityRegistry.register(KingdomOmnitrix.id("meteor_fist"), FourArmsMoves::meteorFist);
		AbilityRegistry.register(KingdomOmnitrix.id("quad_finisher"), FourArmsMoves::quadFinisher);
		ServerTickEvents.END_SERVER_TICK.register(FourArmsMoves::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Faustsalve: acht Schlaege in schneller Folge (je zwei Faeuste), der letzte schleudert weit weg. */
	private static boolean barrage(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		FourArmsAbilities.Power power = FourArmsAbilities.power(player);
		int hits = (int) ctx.param("hits", 8.0) + (power.rampage() ? 4 : 0);
		BARRAGES.put(player.getUuid(), new Barrage(hits, ctx.world().getTime(), (float) ctx.param("damage", 2.5) * power.factor(),
				ctx.param("range", 3.5), power.rampage()));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, hits * 2 + 4, 1, false, false));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ATTACK, 1.0f, 1.2f);
		return true;
	}

	/** Aufwaertshaken: schleudert das Ziel senkrecht hoch, Vierarm springt hinterher und schmettert es zurueck in den Boden. */
	private static boolean uppercut(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		FourArmsAbilities.Power power = FourArmsAbilities.power(player);
		float damage = (float) ctx.param("damage", 7.0) * power.factor();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 4.0), 0.5, e -> PartyRules.canHarm(player, e));
		BuiltinAbilities.launch(player, 0.0, ctx.param("jump", 1.1), 0.0);
		Vec3d front = player.getPos().add(BuiltinAbilities.horizontalLook(player).multiply(1.2)).add(0, 1.0, 0);
		Fx.flash(world, front, IMPACT, 1.8f);
		Fx.send(world, Fx.Kind.SHOCKWAVE, front, new Vec3d(0, 1, 0), IMPACT, 2.5f, 8, 1, null);
		Fx.shockwave(world, player.getPos().add(0, 0.1, 0), DUST, 3.0f, 8);
		EarthWave.ring(world, player.getPos(), 2.5, 4, 0.5f);
		if (found.isEmpty()) {
			// ohne Ziel: der Haken reisst die Decke auf
			Destruction.carve(world, player, player.getEyePos(), new Vec3d(0, 1, 0), 4.0, 1.2, AlienStrength.of(player), 0.3);
			BuiltinAbilities.sound(ctx, ModSounds.ALIEN_SLAM, 1.0f, 1.4f);
			return true;
		}
		LivingEntity target = found.get();
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().playerAttack(player), damage);
		target.setVelocity(0.0, ctx.param("launch", 1.5) * (power.rampage() ? 1.3 : 1.0), 0.0);
		target.velocityModified = true;
		UPPERCUTS.put(player.getUuid(), new Uppercut(target.getUuid(), world.getTime() + 9, damage * 1.3f));
		Fx.sparks(world, new Vec3d(target.getX(), target.getBodyY(0.5), target.getZ()), IMPACT, 0.6f, 14);
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_SLAM, 1.2f, 1.3f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
		return true;
	}

	/**
	 * Bodenplatte: alle vier Haende reissen eine Reihe Bodenbloecke heraus und schleudern sie als Faecher — wie Vierarm in
	 * der Serie Strassenplatten wirft. Jeder Brocken schlaegt mit Krater und Flaechenschaden ein.
	 */
	private static boolean slabHeave(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		FourArmsAbilities.Power power = FourArmsAbilities.power(player);
		Vec3d flat = BuiltinAbilities.horizontalLook(player);
		Vec3d side = new Vec3d(-flat.z, 0.0, flat.x);
		int count = (int) ctx.param("chunks", 3.0) + (power.rampage() ? 2 : 0);
		float damage = (float) ctx.param("damage", 6.0) * power.factor();
		float strength = AlienStrength.of(player);
		int thrown = 0;
		for (int i = 0; i < count; i++) {
			double offset = (i - (count - 1) / 2.0) * 1.1;
			BlockPos source = BlockPos.ofFloored(player.getPos().add(flat.multiply(1.6)).add(side.multiply(offset))).down();
			BlockState state = world.getBlockState(source);
			if (state.isAir() || !state.getFluidState().isEmpty() || !Destruction.breakable(world, source, state, player, strength)) {
				continue;
			}
			Destruction.crater(world, player, Vec3d.ofCenter(source), 1.1, strength, 0.0, 0.3);
			double spread = (i - (count - 1) / 2.0) * 0.16;
			Vec3d dir = flat.add(side.multiply(spread)).normalize();
			Vec3d start = player.getEyePos().add(dir.multiply(1.3)).add(side.multiply(offset * 0.4)).add(0, 0.4, 0);
			Debris.projectile(world, player, state, start, dir.multiply(ctx.param("speed", 1.5)).add(0, 0.22, 0), 1.3f,
					(w, at) -> impact(w, player, at, damage, 2.6, strength * 0.8f));
			thrown++;
		}
		if (thrown == 0) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_ground").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		EarthWave.line(world, player.getPos(), flat, 3.0, 5, 0.8f, 0.3);
		Fx.smoke(world, player.getPos().add(flat.multiply(1.6)), DUST, 1.4f, 6);
		Fx.shake(world, player.getPos(), 0.25f, 6, 12);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_STONE_BREAK, 1.5f, 0.5f);
		BuiltinAbilities.sound(ctx, ModSounds.WEAPON_THROW, 1.2f, 0.6f);
		return true;
	}

	/** Riesenschwung: packt das naechste Ziel, wirbelt es zweimal herum (trifft alles im Kreis) und schleudert es los. */
	private static boolean giantSwing(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 4.0), 0.3, e -> PartyRules.canHarm(player, e));
		if (found.isEmpty() || found.get().getWidth() > ctx.param("max_width", 2.5)) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		FourArmsAbilities.Power power = FourArmsAbilities.power(player);
		int ticks = (int) ctx.param("ticks", 24.0) + (power.rampage() ? 12 : 0);
		SWINGS.put(player.getUuid(), new Swing(found.get().getUuid(), ctx.world().getTime(), ticks, (float) ctx.param("damage", 6.0) * power.factor()));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 4, false, false));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.8f, 1.3f);
		return true;
	}

	/**
	 * Kriegsschrei der Tetramand: fuellt die Wut, schwaecht und reizt Gegner (sie greifen Vierarm an, nicht seine
	 * Gruppe), Gruppenmitglieder in der Naehe bekommen Staerke.
	 */
	private static boolean warCry(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 10.0);
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		FourArmsAbilities.gainRage(player, (float) ctx.param("rage", 40.0));
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 0), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1), player);
			if (target instanceof MobEntity mob) {
				mob.setTarget(player);
			}
		}
		for (ServerPlayerEntity ally : world.getPlayers(p -> p != player && p.squaredDistanceTo(player) <= radius * radius
				&& !PartyRules.canHarm(player, p))) {
			ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 0), player);
			Fx.flash(world, ally.getPos().add(0, 1.0, 0), RED, 1.2f);
		}
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 0, false, false));
		Fx.aura(world, player, RED, 1.6f, 40);
		Fx.shockwave(world, player.getPos().add(0, 0.1, 0), RED, (float) radius, 14);
		Fx.send(world, Fx.Kind.SHOCKWAVE, player.getEyePos(), BuiltinAbilities.horizontalLook(player), RED, 4.0f, 10, 1, null);
		Fx.flash(world, player.getEyePos(), RED, 2.4f);
		Fx.shake(world, player.getPos(), 0.4f, 12, (int) radius + 6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.6f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_ROAR, 0.6f, 1.2f);
		return true;
	}

	/** Titanen-Meteor: senkrecht in den Himmel, dann im Sturzflug auf den anvisierten Punkt — riesiger Krater, Bebenring. */
	private static boolean titanMeteor(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		FourArmsAbilities.Power power = FourArmsAbilities.power(player);
		BuiltinAbilities.launch(player, 0.0, ctx.param("up", 2.6), 0.0);
		METEORS.put(player.getUuid(), new Meteor(player.getY(), (float) ctx.param("damage", 22.0) * power.factor(),
				ctx.param("radius", 12.0) * (power.rampage() ? 1.4 : 1.0), ctx.world().getTime() + 160));
		ctx.grantInvulnerability(40);
		FourArmsAbilities.slamFx(ctx.world(), player.getPos(), 4.0, 6, 1.0f, 0.0, Vec3d.ZERO);
		Fx.charge(ctx.world(), player, GOLD, 2.0f, 20);
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_SLAM, 1.4f, 0.8f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 0.8f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Rammstoss (Sprint + Schlag): Schulter voran — das Ziel fliegt weit und schlaegt beim Aufprall ein. */
	private static boolean shoulderRam(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		FourArmsAbilities.Power power = FourArmsAbilities.power(player);
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		float damage = (float) ctx.param("damage", 6.0) * power.factor();
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().playerAttack(player), damage);
		target.setVelocity(dir.x * ctx.param("power", 2.2), 0.45, dir.z * ctx.param("power", 2.2));
		target.velocityModified = true;
		FourArmsAbilities.thrown(player, target, damage, 50);
		Vec3d at = new Vec3d(target.getX(), target.getBodyY(0.5), target.getZ());
		Fx.flash(world, at, IMPACT, 2.0f);
		Fx.send(world, Fx.Kind.SHOCKWAVE, at, dir, IMPACT, 2.2f, 6, 1, null);
		Fx.smoke(world, player.getPos(), DUST, 1.0f, 3);
		Fx.shake(world, at, 0.2f, 5, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_SLAM, 0.8f, 1.2f);
		return true;
	}

	/** Hammerfaust (Schlag in der Luft): alle vier Faeuste von oben — das Ziel wird in den Boden gerammt. */
	private static boolean hammerFist(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		FourArmsAbilities.Power power = FourArmsAbilities.power(player);
		float damage = (float) ctx.param("damage", 8.0) * power.factor();
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().playerAttack(player), damage);
		target.setVelocity(0.0, -ctx.param("slam", 1.8), 0.0);
		target.velocityModified = true;
		FourArmsAbilities.thrown(player, target, damage * 0.6f, 30);
		Destruction.crater(world, player, target.getPos().add(0, -1.2, 0), 1.4, 0.6, AlienStrength.of(player) * 0.9f, 0.3, 0.35);
		Vec3d at = new Vec3d(target.getX(), target.getBodyY(0.7), target.getZ());
		Fx.flash(world, at, IMPACT, 2.2f);
		Fx.send(world, Fx.Kind.SHOCKWAVE, at, new Vec3d(0, -1, 0), IMPACT, 2.4f, 6, 1, null);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.3, 0.35, player.getVelocity().z * 0.3);
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_SLAM, 1.3f, 0.9f);
		return true;
	}

	/** Schulterwurf (Schleichen + Schlag): packt das Ziel und wirft es im hohen Bogen hinter sich auf den Boden. */
	private static boolean grappleToss(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null || target.getWidth() > ctx.param("max_width", 2.5)) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		FourArmsAbilities.Power power = FourArmsAbilities.power(player);
		Vec3d back = BuiltinAbilities.horizontalLook(player).multiply(-1.0);
		float damage = (float) ctx.param("damage", 7.0) * power.factor();
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().playerAttack(player), damage * 0.5f);
		target.setPosition(player.getX(), player.getY() + player.getHeight() + 0.3, player.getZ());
		target.setVelocity(back.x * 0.9, 0.55, back.z * 0.9);
		target.velocityModified = true;
		FourArmsAbilities.thrown(player, target, damage, 40);
		Fx.flash(world, player.getEyePos(), IMPACT, 1.4f);
		Fx.sparks(world, player.getEyePos(), IMPACT, 0.4f, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ATTACK, 1.0f, 0.8f);
		return true;
	}

	/** Meteorfaust (hoch in der Luft schleichen): Sturz mit beiden Faustpaaren voran; Einschlag waechst mit der Hoehe. */
	private static boolean meteorFist(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		FourArmsAbilities.Power power = FourArmsAbilities.power(player);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.2), player.getVelocity().z * 0.2);
		// Landung mit Wucht nach der Strecke ab hier (+ bisheriger Fall)
		FourArmsAbilities.landing(player, player.getY() + player.fallDistance - 6.0, (float) ctx.param("damage", 9.0) * power.factor(),
				ctx.param("radius", 5.0), power.rampage());
		Fx.flames(ctx.world(), player.getPos(), new Vec3d(0, 0.3, 0), IMPACT, 0.4f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), 1.0f, 0.6f);
		return true;
	}

	/**
	 * Vierfach-Finale (Schlag gegen geschwaechtes Ziel): alle vier Faeuste ueber dem Kopf verschraenkt, ein Hieb von oben.
	 * Zusatzschaden nach fehlendem Leben, Ziel im Boden, Krater und Druckwelle.
	 */
	private static boolean quadFinisher(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		FourArmsAbilities.Power power = FourArmsAbilities.power(player);
		float missing = target.getMaxHealth() - target.getHealth();
		float damage = ((float) ctx.param("damage", 12.0) + missing * (float) ctx.param("missing", 0.25)) * power.factor();
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().playerAttack(player), damage);
		target.setVelocity(0.0, -1.5, 0.0);
		target.velocityModified = true;
		Vec3d ground = target.getPos();
		FourArmsAbilities.wave(player, ground, ctx.param("radius", 5.0), damage * 0.35f, 0.5, 6);
		Destruction.crater(world, player, ground.add(0, -0.8, 0), 2.2, 0.5, AlienStrength.of(player), 0.5, 0.4);
		FourArmsAbilities.slamFx(world, ground, 5.0, 6, 1.4f, 0.0, Vec3d.ZERO);
		Fx.flash(world, new Vec3d(target.getX(), target.getBodyY(0.6), target.getZ()), GOLD, 3.0f);
		Fx.explosion(world, ground.add(0, 0.4, 0), IMPACT, 1.8f);
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_SLAM, 1.6f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.9f, 0.8f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (BARRAGES.isEmpty() && UPPERCUTS.isEmpty() && SWINGS.isEmpty() && METEORS.isEmpty()) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			if (!com.santiq.kingdomomnitrix.alien.TransformationManager.get(player).isTransformed() || !player.isAlive()) {
				forget(id);
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			tickBarrage(world, player, now);
			tickUppercut(world, player, now);
			tickSwing(world, player, now);
			tickMeteor(world, player, now);
		}
		Set<UUID> online = new HashSet<>();
		server.getPlayerManager().getPlayerList().forEach(p -> online.add(p.getUuid()));
		BARRAGES.keySet().retainAll(online);
		UPPERCUTS.keySet().retainAll(online);
		SWINGS.keySet().retainAll(online);
		METEORS.keySet().retainAll(online);
	}

	private static void tickBarrage(ServerWorld world, ServerPlayerEntity player, long now) {
		Barrage barrage = BARRAGES.get(player.getUuid());
		if (barrage == null || now < barrage.next()) {
			return;
		}
		boolean last = barrage.left() <= 1;
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Vec3d side = new Vec3d(-look.z, 0.0, look.x).multiply(barrage.left() % 2 == 0 ? 0.6 : -0.6);
		Vec3d fist = player.getPos().add(0, player.getHeight() * (barrage.left() % 4 < 2 ? 0.7 : 0.45), 0).add(look.multiply(1.6)).add(side);
		for (LivingEntity target : AlienKit.cone(world, player, barrage.range(), 0.5)) {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().playerAttack(player), barrage.damage() * (last ? 3.0f : 1.0f));
			if (last) {
				Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
				if (away.lengthSquared() > 1.0E-4) {
					target.takeKnockback(2.4, -away.normalize().x, -away.normalize().z);
				}
				target.addVelocity(0.0, 0.4, 0.0);
				target.velocityModified = true;
				FourArmsAbilities.thrown(player, target, barrage.damage() * 2.0f, 40);
			} else {
				target.setVelocity(target.getVelocity().multiply(0.2));
				target.velocityModified = true;
			}
		}
		Fx.flash(world, fist, IMPACT, last ? 2.6f : 1.0f);
		Fx.sparks(world, fist, last ? GOLD : IMPACT, last ? 0.6f : 0.35f, last ? 16 : 5);
		Fx.send(world, Fx.Kind.SHOCKWAVE, fist, look, IMPACT, last ? 2.8f : 1.0f, last ? 8 : 4, 1, null);
		if (last) {
			Destruction.carve(world, player, player.getEyePos(), look, barrage.range() + 1.0, 1.0, AlienStrength.of(player) * 0.8f, 0.2);
			Fx.shake(world, fist, 0.3f, 6, 12);
		}
		world.playSound(null, player.getBlockPos(), last ? ModSounds.ALIEN_SLAM : SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS,
				last ? 1.3f : 0.8f, last ? 0.8f : 0.9f + world.random.nextFloat() * 0.3f);
		if (last) {
			BARRAGES.remove(player.getUuid());
		} else {
			BARRAGES.put(player.getUuid(), new Barrage(barrage.left() - 1, now + (barrage.rampage() ? 1 : 2), barrage.damage(), barrage.range(),
					barrage.rampage()));
		}
	}

	private static void tickUppercut(ServerWorld world, ServerPlayerEntity player, long now) {
		Uppercut uppercut = UPPERCUTS.get(player.getUuid());
		if (uppercut == null || now < uppercut.followUp()) {
			return;
		}
		UPPERCUTS.remove(player.getUuid());
		if (!(world.getEntity(uppercut.target()) instanceof LivingEntity target) || !target.isAlive()
				|| target.squaredDistanceTo(player) > 6.0 * 6.0) {
			return;
		}
		// Nachsetzen: in der Luft hinterher und mit allen Faeusten nach unten schmettern
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().playerAttack(player), uppercut.damage());
		target.setVelocity(0.0, -2.0, 0.0);
		target.velocityModified = true;
		FourArmsAbilities.thrown(player, target, uppercut.damage(), 40);
		Vec3d at = new Vec3d(target.getX(), target.getBodyY(0.6), target.getZ());
		Fx.flash(world, at, GOLD, 2.4f);
		Fx.send(world, Fx.Kind.SHOCKWAVE, at, new Vec3d(0, -1, 0), IMPACT, 2.6f, 6, 1, null);
		BuiltinAbilities.launch(player, 0.0, 0.25, 0.0);
		world.playSound(null, target.getBlockPos(), ModSounds.ALIEN_SLAM, SoundCategory.PLAYERS, 1.3f, 1.0f);
	}

	private static void tickSwing(ServerWorld world, ServerPlayerEntity player, long now) {
		Swing swing = SWINGS.get(player.getUuid());
		if (swing == null) {
			return;
		}
		if (!(world.getEntity(swing.target) instanceof LivingEntity held) || !held.isAlive()) {
			SWINGS.remove(player.getUuid());
			return;
		}
		long age = now - swing.start;
		if (age >= swing.ticks) {
			// loslassen in Blickrichtung
			SWINGS.remove(player.getUuid());
			Vec3d look = player.getRotationVec(1.0f);
			held.timeUntilRegen = 0;
			held.damage(world.getDamageSources().playerAttack(player), swing.damage);
			held.setVelocity(look.x * 2.4, Math.max(0.5, look.y * 2.4 + 0.5), look.z * 2.4);
			held.velocityModified = true;
			FourArmsAbilities.thrown(player, held, swing.damage * 1.5f, 60);
			Fx.flash(world, held.getPos().add(0, 0.6, 0), IMPACT, 2.2f);
			world.playSound(null, player.getBlockPos(), ModSounds.WEAPON_THROW, SoundCategory.PLAYERS, 1.4f, 0.6f);
			return;
		}
		// zwei volle Umdrehungen, schneller werdend; das Ziel kreist auf Armlaenge
		double progress = age / (double) swing.ticks;
		double angle = Math.toRadians(player.getYaw() + 90.0) + progress * progress * MathHelper.TAU * 2.0;
		double reach = 1.6 + player.getWidth() * 0.5;
		Vec3d orbit = player.getPos().add(Math.cos(angle) * reach, 0.6, Math.sin(angle) * reach);
		held.setPosition(orbit);
		held.setVelocity(Vec3d.ZERO);
		held.velocityModified = true;
		held.fallDistance = 0.0f;
		for (LivingEntity hit : world.getEntitiesByClass(LivingEntity.class, held.getBoundingBox().expand(0.8),
				e -> e != player && e != held && e.isAlive() && !swing.hit.contains(e.getUuid()) && PartyRules.canHarm(player, e))) {
			swing.hit.add(hit.getUuid());
			hit.timeUntilRegen = 0;
			hit.damage(world.getDamageSources().playerAttack(player), swing.damage);
			Vec3d away = hit.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			if (away.lengthSquared() > 1.0E-4) {
				hit.takeKnockback(1.8, -away.normalize().x, -away.normalize().z);
			}
			Fx.flash(world, hit.getPos().add(0, 0.8, 0), IMPACT, 1.4f);
			world.playSound(null, hit.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0f, 0.8f);
		}
		if (age % 2 == 0) {
			Fx.smoke(world, orbit.add(0, -0.4, 0), DUST, 0.6f, 1);
			Fx.sparks(world, orbit, IMPACT, 0.15f, 2);
		}
		if (age % 6 == 0) {
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 0.6f + (float) progress);
		}
	}

	private static void tickMeteor(ServerWorld world, ServerPlayerEntity player, long now) {
		Meteor meteor = METEORS.get(player.getUuid());
		if (meteor == null) {
			return;
		}
		player.fallDistance = 0.0f;
		if (now >= meteor.until) {
			METEORS.remove(player.getUuid());
			return;
		}
		if (meteor.phase == 0) {
			if (player.getVelocity().y < 0.1) {
				// Scheitelpunkt: Ziel am Boden in Blickrichtung suchen, dann Sturzflug
				Vec3d eye = player.getEyePos();
				Vec3d look = player.getRotationVec(1.0f);
				Vec3d down = look.y > -0.2 ? new Vec3d(look.x, -0.6, look.z).normalize() : look;
				BlockHitResult hit = world.raycast(new RaycastContext(eye, eye.add(down.multiply(48.0)), RaycastContext.ShapeType.COLLIDER,
						RaycastContext.FluidHandling.NONE, player));
				meteor.aim = hit.getType() == HitResult.Type.MISS ? eye.add(down.multiply(24.0)) : hit.getPos();
				meteor.phase = 1;
				Fx.flash(world, player.getPos().add(0, 1.0, 0), GOLD, 3.0f);
				world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.PLAYERS, 1.4f, 0.5f);
			}
			return;
		}
		Vec3d to = meteor.aim.subtract(player.getPos());
		if (!player.isOnGround() && to.lengthSquared() > 1.0) {
			Vec3d velocity = to.normalize();
			BuiltinAbilities.launch(player, velocity.x * 1.8, Math.min(-1.2, velocity.y * 2.4), velocity.z * 1.8);
			Fx.flames(world, player.getPos().add(0, 1.0, 0), new Vec3d(0, 0.2, 0), IMPACT, 0.5f, 5);
			Fx.smoke(world, player.getPos().add(0, 1.5, 0), DUST, 0.8f, 1);
			return;
		}
		METEORS.remove(player.getUuid());
		double height = Math.max(0.0, meteor.startY + 10.0 - player.getY());
		float boost = (float) Math.min(1.8, 1.0 + height / 30.0);
		Vec3d at = player.getPos();
		FourArmsAbilities.wave(player, at, meteor.radius * boost, meteor.damage * boost, 0.9, 12);
		Destruction.crater(world, player, at.add(0, -1.0, 0), 4.5 * boost, 0.5, AlienStrength.of(player) * 1.6f * boost, 0.6, 0.5);
		float strength = AlienStrength.of(player) * 1.4f;
		for (int i = 0; i < 6; i++) {
			double a = i * MathHelper.TAU / 6 + world.random.nextDouble() * 0.4;
			Vec3d dir = new Vec3d(Math.cos(a), -0.08, Math.sin(a));
			Destruction.carve(world, player, at.add(dir.multiply(3.5)).add(0, -1.3, 0), dir, meteor.radius * 0.9, 1.2, strength, 1.6);
			Fx.explosion(world, at.add(Math.cos(a) * 4.0, 0.4, Math.sin(a) * 4.0), IMPACT, 1.8f);
		}
		FourArmsAbilities.slamFx(world, at, meteor.radius * boost, 14, 2.0f, 0.0, Vec3d.ZERO);
		Fx.explosion(world, at.add(0, 0.6, 0), GOLD, 3.0f + boost);
		Fx.shake(world, at, 1.0f, 20, 40);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0f, 0.5f);
		world.playSound(null, player.getBlockPos(), ModSounds.ALIEN_SLAM, SoundCategory.PLAYERS, 2.0f, 0.5f);
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	/** Einschlag eines geworfenen Brockens: Flaechenschaden mit Abfall zum Rand, Krater, Bodenwelle. */
	private static void impact(ServerWorld world, ServerPlayerEntity player, Vec3d at, float damage, double radius, float strength) {
		for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, Box.of(at, radius * 2, 4, radius * 2),
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
			double d = near.getPos().distanceTo(at);
			if (d > radius) {
				continue;
			}
			near.timeUntilRegen = 0;
			near.damage(world.getDamageSources().playerAttack(player), (float) (damage * (1.0 - 0.5 * d / radius)));
			Vec3d push = near.getPos().subtract(at).multiply(1, 0, 1);
			if (push.lengthSquared() > 1.0E-4) {
				near.takeKnockback(1.2, -push.x, -push.z);
			}
			near.addVelocity(0.0, 0.35, 0.0);
			near.velocityModified = true;
		}
		Destruction.crater(world, player, at.add(0, -0.4, 0), 1.6, 0.6, strength, 0.5, 0.4);
		FourArmsAbilities.slamFx(world, at, radius + 1.5, 5, 0.9f, 0.0, Vec3d.ZERO);
		world.playSound(null, BlockPos.ofFloored(at), ModSounds.ALIEN_SLAM, SoundCategory.PLAYERS, 1.2f, 0.8f);
	}

	private static void forget(UUID id) {
		BARRAGES.remove(id);
		UPPERCUTS.remove(id);
		SWINGS.remove(id);
		METEORS.remove(id);
	}
}
