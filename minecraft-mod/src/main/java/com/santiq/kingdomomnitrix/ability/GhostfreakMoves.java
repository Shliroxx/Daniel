package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/**
 * Ghostfreak, Kraefte 2.0: Seelenraub, Geisterflug, Geisterketten, Unsichtbarkeit, Heimsuchung, Geisterlegion; Kombos
 * Geisterstoss, Schattenfall, Kalter Griff, Spukeinschlag, Seelenriss; Passive Koerperlos und Nachtgestalt (Spuk und
 * Angst sitzen in {@link GhostfreakAbilities}). Angst fuehrt zu Panik — Panische nehmen von Ghostfreak mehr Schaden.
 */
final class GhostfreakMoves {
	private static final int ECTO = 0x8F5CD9;
	private static final int PALE = 0xEBE6FF;
	private static final int SOUL = 0x5AE0E0;

	private GhostfreakMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("soul_drain"), GhostfreakMoves::soulDrain);
		AbilityRegistry.register(KingdomOmnitrix.id("ghost_flight"), GhostfreakMoves::ghostFlight);
		AbilityRegistry.register(KingdomOmnitrix.id("spectral_chains"), GhostfreakMoves::spectralChains);
		AbilityRegistry.register(KingdomOmnitrix.id("vanish"), GhostfreakMoves::vanish);
		AbilityRegistry.register(KingdomOmnitrix.id("haunting"), GhostfreakMoves::haunting);
		AbilityRegistry.register(KingdomOmnitrix.id("phantom_legion"), GhostfreakMoves::phantomLegion);
		AbilityRegistry.register(KingdomOmnitrix.id("spectral_rush"), GhostfreakMoves::spectralRush);
		AbilityRegistry.register(KingdomOmnitrix.id("shadow_drop"), GhostfreakMoves::shadowDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("chilling_touch"), GhostfreakMoves::chillingTouch);
		AbilityRegistry.register(KingdomOmnitrix.id("haunt_crash"), GhostfreakMoves::hauntCrash);
		AbilityRegistry.register(KingdomOmnitrix.id("soul_rend"), GhostfreakMoves::soulRend);
		PassiveRegistry.register(KingdomOmnitrix.id("intangible"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// koerperlos: Angriffe gehen oft einfach hindurch — im Sonnenlicht (Ectonurit-Schwaeche) viel seltener
				if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.isIn(DamageTypeTags.IS_FIRE)) {
					return amount;
				}
				ServerPlayerEntity player = ctx.player();
				boolean sun = ctx.world().isDay() && ctx.world().isSkyVisible(player.getBlockPos().up());
				double chance = sun ? ctx.param("sun_chance", 0.1) : ctx.param("chance", 0.25);
				if (ctx.world().random.nextDouble() < chance) {
					Fx.flash(ctx.world(), player.getPos().add(0, 1.0, 0), PALE, 1.0f);
					return 0.0f;
				}
				return sun ? amount * (float) ctx.param("sun_penalty", 1.15) : amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("night_stalker"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// schleichend im Dunkeln wird Ghostfreak unsichtbar
				ServerPlayerEntity player = ctx.player();
				if (ctx.now() % 10 == 0 && player.isSneaking()
						&& ctx.world().getLightLevel(LightType.BLOCK, player.getBlockPos()) < 8
						&& (ctx.world().isNight() || !ctx.world().isSkyVisible(player.getBlockPos().up()))) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 30, 0, true, false, true));
				}
			}

			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				return ctx.world().isNight() ? amount * (float) ctx.param("night_bonus", 1.2) : amount;
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Seelenraub: ein Strahl zieht 2 s lang Lebenskraft aus dem Ziel — Schaden, Heilung, Angst. */
	private static boolean soulDrain(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 12.0), 0.92, e -> AlienKit.foe(player, e));
		if (found.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		UUID id = found.get().getUuid();
		float damage = (float) ctx.param("damage", 1.5);
		PowerTasks.start(player, 40, 4, (world, p, age) -> {
			if (!(world.getEntity(id) instanceof LivingEntity t) || !t.isAlive() || t.squaredDistanceTo(p) > 196) {
				return false;
			}
			Fx.beam(world, AlienKit.body(t), p.getEyePos(), SOUL, 0.12f, 5);
			AlienKit.magic(world, p, t, damage);
			p.heal(damage * 0.6f);
			if (age % 16 == 0) {
				GhostfreakAbilities.frighten(p, t, 1);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.PARTICLE_SOUL_ESCAPE.value(), 1.4f, 0.6f);
		return true;
	}

	/** Geisterflug: 3 s schwebt Ghostfreak in Blickrichtung — durch Waende hindurch, unberuehrbar; danach Hinterhalt bereit. */
	private static boolean ghostFlight(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		ctx.grantInvulnerability(ticks);
		PowerTasks.start(player, ticks, 1, (world, p, age) -> {
			Vec3d look = p.getRotationVec(1.0f);
			BuiltinAbilities.launch(p, look.x * 0.7, look.y * 0.7 + 0.04, look.z * 0.7);
			GhostfreakAbilities.phaseThrough(world, p);
			if (age % 3 == 0) {
				Fx.smoke(world, p.getPos().add(0, 1.0, 0), PALE, 0.6f, 1);
			}
			return true;
		}, (p, aborted) -> GhostfreakAbilities.ambush(p, 100));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_VEX_CHARGE, 1.2f, 0.6f);
		return true;
	}

	/** Geisterketten: Ketten aus Ektoplasma fesseln bis zu drei Gegner um das Ziel 3 Sekunden — jeder bekommt Angst. */
	private static boolean spectralChains(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d at = AlienKit.aim(world, player, ctx.param("range", 14.0));
		List<LivingEntity> bound = new ArrayList<>(AlienKit.around(world, player, at, ctx.param("radius", 4.0)));
		bound.sort(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(at)));
		if (bound.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		for (LivingEntity target : bound.subList(0, Math.min(3, bound.size()))) {
			AlienKit.hold(world, target, ticks, AlienKit.dust("#8F5CD9", 1.2f), true);
			GhostfreakAbilities.frighten(player, target, 1);
			Fx.beam(world, player.getEyePos(), AlienKit.body(target), ECTO, 0.1f, 10);
			Fx.aura(world, target, ECTO, 0.8f, ticks);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_CHAIN_PLACE, 1.4f, 0.6f);
		return true;
	}

	/** Unsichtbarkeit: 8 s unsichtbar, Gegner verlieren die Spur; der naechste Nahkampftreffer ist ein Hinterhalt. */
	private static boolean vanish(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, ticks, 0, false, false));
		for (MobEntity mob : ctx.world().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(24.0), m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
		GhostfreakAbilities.ambush(player, ticks);
		Fx.smoke(ctx.world(), player.getPos().add(0, 1.0, 0), PALE, 1.2f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2f, 0.8f);
		return true;
	}

	/** Heimsuchung: verflucht das Ziel 10 s — es wird jede Sekunde von Geisterhaenden getroffen, verlangsamt, voller Angst. */
	private static boolean haunting(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 16.0), 0.92, e -> AlienKit.foe(player, e));
		if (found.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		UUID id = found.get().getUuid();
		float damage = (float) ctx.param("damage", 2.0);
		PowerTasks.start(player, (int) (ctx.param("seconds", 10.0) * 20), 20, (world, p, age) -> {
			if (!(world.getEntity(id) instanceof LivingEntity t) || !t.isAlive()) {
				return false;
			}
			AlienKit.magic(world, p, t, damage);
			t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 1), p);
			GhostfreakAbilities.frighten(p, t, 1);
			double a = age * 0.7;
			Fx.beam(world, AlienKit.body(t).add(Math.cos(a) * 1.5, 1.0, Math.sin(a) * 1.5), AlienKit.body(t), ECTO, 0.1f, 6);
			world.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_VEX_AMBIENT, SoundCategory.PLAYERS, 0.8f, 0.6f);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.6f);
		return true;
	}

	/** Geisterlegion (Ultimate): sechs Geisterbilder umkreisen 6 s die Umgebung und greifen jeden Gegner an — Massenpanik. */
	private static boolean phantomLegion(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d center = player.getPos();
		double radius = ctx.param("radius", 10.0);
		float damage = (float) ctx.param("damage", 3.0);
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		ctx.grantInvulnerability(20);
		PowerTasks.start(player, ticks, 2, (world, p, age) -> {
			for (int i = 0; i < 6; i++) {
				double a = age * 0.08 + i * MathHelper.TAU / 6;
				Vec3d ghost = center.add(Math.cos(a) * radius * 0.6, 1.5 + Math.sin(age * 0.15 + i) * 0.5, Math.sin(a) * radius * 0.6);
				Fx.smoke(world, ghost, PALE, 0.8f, 1);
				if ((age + i * 4) % 24 == 0) {
					List<LivingEntity> foes = AlienKit.around(world, p, ghost, radius);
					if (!foes.isEmpty()) {
						LivingEntity t = foes.get(world.random.nextInt(foes.size()));
						Fx.beam(world, ghost, AlienKit.body(t), ECTO, 0.12f, 5);
						AlienKit.magic(world, p, t, damage);
						GhostfreakAbilities.frighten(p, t, 1);
					}
				}
			}
			if (age % 30 == 0) {
				world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_VEX_AMBIENT, SoundCategory.PLAYERS, 1.4f, 0.5f);
			}
			return true;
		});
		Fx.shockwave(ctx.world(), center.add(0, 0.2, 0), ECTO, (float) radius, 14);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WITHER_AMBIENT, 0.8f, 1.4f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Geisterstoss (Sprint + Schlag): faehrt durch das Ziel hindurch — Schaden und zweimal Angst. */
	private static boolean spectralRush(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.magic(ctx.world(), player, target, (float) ctx.param("damage", 5.0));
		GhostfreakAbilities.frighten(player, target, 2);
		Vec3d behind = target.getPos().add(dir.multiply(1.8));
		player.requestTeleport(behind.x, behind.y, behind.z);
		Fx.beam(ctx.world(), AlienKit.body(target).subtract(dir), AlienKit.body(target).add(dir), PALE, 0.3f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_VEX_CHARGE, 1.2f, 0.8f);
		return true;
	}

	/** Schattenfall (Schlag in der Luft): packt das Ziel von oben und schmettert es zu Boden — Angst. */
	private static boolean shadowDrop(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0));
		target.setVelocity(0.0, -1.4, 0.0);
		target.velocityModified = true;
		GhostfreakAbilities.frighten(player, target, 1);
		Fx.smoke(ctx.world(), AlienKit.body(target), ECTO, 0.8f, 3);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_BITE, 1.2f, 0.6f);
		return true;
	}

	/** Kalter Griff (Schleichen + Schlag): eisige Geisterhand — verlangsamt, schwaecht, Angst; von hinten doppelter Schaden. */
	private static boolean chillingTouch(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d facing = target.getRotationVec(1.0f).multiply(1, 0, 1);
		Vec3d toPlayer = player.getPos().subtract(target.getPos()).multiply(1, 0, 1);
		boolean behind = facing.lengthSquared() > 1.0E-4 && toPlayer.lengthSquared() > 1.0E-4 && facing.normalize().dotProduct(toPlayer.normalize()) < -0.3;
		AlienKit.magic(ctx.world(), player, target, (float) ctx.param("damage", 4.0) * (behind ? 2.0f : 1.0f));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2), player);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 1), player);
		GhostfreakAbilities.frighten(player, target, 2);
		Fx.flash(ctx.world(), AlienKit.body(target), PALE, behind ? 1.8f : 1.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_POWDER_SNOW_STEP, 1.2f, 0.6f);
		return true;
	}

	/** Spukeinschlag (hoch in der Luft schleichen): stuerzt herab, der Aufprall jagt allen ringsum Angst ein. */
	private static boolean hauntCrash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 5.0);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 1.8), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround()) {
				Fx.smoke(world, p.getPos().add(0, 1.0, 0), PALE, 0.5f, 1);
				return true;
			}
			for (LivingEntity target : AlienKit.blast(world, p, p.getPos().add(0, 0.6, 0), 4.0, damage, 0.6, 0.3)) {
				GhostfreakAbilities.frighten(p, target, 2);
			}
			Fx.shockwave(world, p.getPos().add(0, 0.1, 0), ECTO, 4.5f, 10);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 1.0f, 0.6f);
			return false;
		});
		return true;
	}

	/** Seelenriss (Schlag gegen geschwaechtes Ziel): reisst die Seele heraus — hoher Schaden; faellt das Ziel, heilt er und der Spuk steigt. */
	private static boolean soulRend(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float damage = (float) ctx.param("damage", 10.0) * (GhostfreakAbilities.panicking(target) ? 1.5f : 1.0f);
		Vec3d body = AlienKit.body(target);
		AlienKit.magic(world, player, target, damage);
		Fx.beam(world, body, player.getEyePos(), SOUL, 0.25f, 10);
		if (!target.isAlive()) {
			player.heal((float) ctx.param("heal", 6.0));
			GhostfreakAbilities.addDread(player, 30.0f);
			Fx.flash(world, body, SOUL, 2.4f);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.PARTICLE_SOUL_ESCAPE.value(), 1.6f, 0.5f);
		return true;
	}
}
