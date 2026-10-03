package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.Optional;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Faehigkeiten 4–6 (SPECIAL, UTILITY, ULTIMATE) aller Aliens und die fehlenden Slots von Diamondhead und Grey Matter.
 * Freigeschaltet ueber die Alien-Meisterschaft ({@code unlock_level} im Datenpaket). Gebaut aus wenigen geprueften
 * Bausteinen: Flaeche/Kegel ({@link #area}), Selbst-Verstaerkung ({@link #self}), Ziel-Wirkung ({@link #onTarget}),
 * Strahl ({@link #beam}); dazu Sonderfaelle (Kettenangriff, Schattenschritt, Kristallring).
 *
 * <p>Gemeinsame Parameter der Flaechen-Faehigkeiten: {@code radius}, {@code damage}, {@code knockback}, {@code launch},
 * {@code pull}, {@code fire_seconds}, {@code cone} (0 = Rundum, sonst Mindest-cos zum Blick, z. B. 0,5 ≈ 60°),
 * {@code seconds} (Effektdauer), {@code water_multiplier} (Schaden im Wasser).</p>
 */
final class MasteryAbilities {
	private MasteryAbilities() {
	}

	/** Effekt mit fester Stufe; die Dauer kommt aus dem Parameter {@code seconds}. */
	private record Fx(RegistryEntry<StatusEffect> effect, int amplifier) {
	}

	/** Darstellung einer Flaechen-Faehigkeit. */
	private record Look(ParticleEffect particle, int count, SoundEvent sound, float pitch, boolean magic) {
	}

	static void register() {
		DustParticleEffect crystal = new DustParticleEffect(new Vector3f(0.55f, 1.0f, 0.75f), 1.3f);
		DustParticleEffect toxic = new DustParticleEffect(new Vector3f(0.55f, 0.7f, 0.15f), 1.5f);
		DustParticleEffect tech = new DustParticleEffect(new Vector3f(0.22f, 1.0f, 0.08f), 1.0f);

		// --- Heatblast: HeatblastAbilities (Kernhitze)
		// --- XLR8: Xlr8Abilities (Tempo)
		// --- Vierarm: FourArmsAbilities (Wut)
		// --- Diamondhead: DiamondheadAbilities (Resonanz, Kristall-Konstrukte)
		// --- Grey Matter: GreyMatterAbilities (Analyse-Datenbank)
		// --- Wildmutt
		reg("savage_maul", MasteryAbilities::savageMaul);
		reg("scent_track", ctx -> area(ctx, new Look(ParticleTypes.NOTE, 1, SoundEvents.ENTITY_WOLF_HOWL, 1.0f, true), new Fx(StatusEffects.GLOWING, 0)));
		reg("primal_rampage", ctx -> self(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, new Fx(StatusEffects.STRENGTH, 1),
				new Fx(StatusEffects.SPEED, 1), new Fx(StatusEffects.RESISTANCE, 0))
				&& area(ctx, new Look(ParticleTypes.ANGRY_VILLAGER, 12, SoundEvents.ENTITY_WOLF_GROWL, 0.6f, false), new Fx(StatusEffects.SLOWNESS, 1)));
		// --- Stinkfly
		reg("slime_bomb", MasteryAbilities::slimeBomb);
		reg("updraft", ctx -> area(ctx, new Look(ParticleTypes.GUST, 6, SoundEvents.ENTITY_BREEZE_JUMP, 1.0f, false)));
		reg("toxic_storm", MasteryAbilities::toxicStorm);
		// --- Ripjaws
		reg("tail_swipe", ctx -> area(ctx, new Look(ParticleTypes.SPLASH, 60, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, false)));
		reg("hydro_heal", MasteryAbilities::hydroHeal);
		reg("tidal_wave", ctx -> area(ctx, new Look(ParticleTypes.FISHING, 140, SoundEvents.ENTITY_GENERIC_SPLASH, 0.6f, false),
				new Fx(StatusEffects.SLOWNESS, 1)));
		// --- Upgrade
		reg("mace_fists", ctx -> self(ctx, SoundEvents.BLOCK_PISTON_EXTEND, new Fx(StatusEffects.STRENGTH, 1), new Fx(StatusEffects.HASTE, 1)));
		reg("system_override", ctx -> onTarget(ctx, 16.0, ParticleTypes.ELECTRIC_SPARK, SoundEvents.BLOCK_BEACON_DEACTIVATE, true,
				new Fx(StatusEffects.SLOWNESS, 5), new Fx(StatusEffects.WEAKNESS, 1), new Fx(StatusEffects.GLOWING, 0)));
		reg("plasma_cannon", ctx -> beam(ctx, new Vector3f(0.22f, 1.0f, 0.08f), 2.0f, SoundEvents.ENTITY_WARDEN_SONIC_BOOM));
		// --- Ghostfreak
		reg("possession", ctx -> onTarget(ctx, 6.0, ParticleTypes.SOUL, SoundEvents.ENTITY_VEX_AMBIENT, true,
				new Fx(StatusEffects.SLOWNESS, 9), new Fx(StatusEffects.WEAKNESS, 1), new Fx(StatusEffects.LEVITATION, 0))
				&& self(ctx, SoundEvents.ENTITY_VEX_CHARGE, new Fx(StatusEffects.INVISIBILITY, 0)));
		reg("shadow_step", MasteryAbilities::shadowStep);
		reg("nightmare", ctx -> area(ctx, new Look(ParticleTypes.SCULK_SOUL, 120, SoundEvents.ENTITY_WARDEN_ROAR, 0.8f, true),
				new Fx(StatusEffects.DARKNESS, 0), new Fx(StatusEffects.BLINDNESS, 0), new Fx(StatusEffects.WITHER, 1)));
	}

	private static void reg(String name, AlienAbility ability) {
		AbilityRegistry.register(KingdomOmnitrix.id(name), ability);
	}

	// --- Bausteine ---------------------------------------------------------------------------------

	/**
	 * Flaeche oder Kegel um den Spieler: Schaden, Feuer, Rueckstoss (negativ {@code pull} = heranziehen), Anheben und
	 * Effekte. Trifft nur, was der Spieler schaedigen darf (Gruppe/PvP-Regeln). Loest immer aus (auch ohne Ziel).
	 */
	private static boolean area(AbilityContext ctx, Look look, Fx... effects) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 5.0);
		double cone = ctx.param("cone", 0.0);
		boolean wet = player.isTouchingWater();
		float damage = (float) (ctx.param("damage", 0.0) * (wet ? ctx.param("water_multiplier", 1.0) : 1.0));
		double knockback = ctx.param("knockback", 0.0);
		double pull = ctx.param("pull", 0.0);
		double launch = ctx.param("launch", 0.0);
		float fire = (float) ctx.param("fire_seconds", 0.0);
		int ticks = (int) Math.round(ctx.param("seconds", 5.0) * 20.0);
		Vec3d facing = BuiltinAbilities.horizontalLook(player);
		DamageSource source = look.magic() ? world.getDamageSources().indirectMagic(player, player) : world.getDamageSources().playerAttack(player);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			Vec3d offset = target.getPos().subtract(player.getPos());
			Vec3d flat = new Vec3d(offset.x, 0.0, offset.z);
			if (cone > 0.0 && flat.lengthSquared() > 1.0E-4 && flat.normalize().dotProduct(facing) < cone) {
				continue;
			}
			if (damage > 0.0f) {
				target.damage(source, damage);
			}
			if (fire > 0.0f) {
				target.setOnFireFor(fire);
			}
			if (flat.lengthSquared() > 1.0E-4) {
				if (knockback > 0.0) {
					target.takeKnockback(knockback, -flat.x, -flat.z);
				}
				if (pull > 0.0) {
					Vec3d in = flat.normalize().multiply(-pull);
					target.addVelocity(in.x, 0.1, in.z);
				}
			}
			if (launch > 0.0) {
				target.addVelocity(0.0, launch, 0.0);
			}
			target.velocityModified = true;
			for (Fx fx : effects) {
				target.addStatusEffect(new StatusEffectInstance(fx.effect(), ticks, fx.amplifier()), player);
			}
		}
		Vec3d at = cone > 0.0 ? player.getPos().add(facing.multiply(radius * 0.5)) : player.getPos();
		world.spawnParticles(look.particle(), at.x, player.getY() + 0.4, at.z, look.count(), radius * 0.35, 0.4, radius * 0.35, 0.05);
		BuiltinAbilities.sound(ctx, look.sound(), 1.0f, look.pitch());
		return true;
	}

	/** Effekte auf den Spieler selbst (Dauer {@code seconds}). */
	private static boolean self(AbilityContext ctx, SoundEvent sound, Fx... effects) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) Math.round(ctx.param("self_seconds", ctx.param("seconds", 8.0)) * 20.0);
		for (Fx fx : effects) {
			player.addStatusEffect(new StatusEffectInstance(fx.effect(), ticks, fx.amplifier()));
		}
		ctx.world().spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getBodyY(0.6), player.getZ(), 10, 0.4, 0.5, 0.4, 0.0);
		BuiltinAbilities.sound(ctx, sound, 0.9f, 1.0f);
		return true;
	}

	/**
	 * Wirkung auf ein Ziel vor dem Spieler (verzeihendes Zielen): Schaden {@code damage} und Effekte. Ohne Ziel: nicht
	 * ausgeloest (keine Energie verbraucht).
	 */
	private static boolean onTarget(AbilityContext ctx, double defaultRange, ParticleEffect particle, SoundEvent sound, boolean magic, Fx... effects) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", defaultRange), 0.8, e -> PartyRules.canHarm(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		float damage = (float) ctx.param("damage", 0.0);
		if (damage > 0.0f) {
			target.damage(magic ? ctx.world().getDamageSources().indirectMagic(player, player) : ctx.world().getDamageSources().playerAttack(player), damage);
		}
		int ticks = (int) Math.round(ctx.param("seconds", 6.0) * 20.0);
		for (Fx fx : effects) {
			target.addStatusEffect(new StatusEffectInstance(fx.effect(), ticks, fx.amplifier()), player);
		}
		ctx.world().spawnParticles(particle, target.getX(), target.getBodyY(0.6), target.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
		BuiltinAbilities.sound(ctx, sound, 1.0f, 1.0f);
		return true;
	}

	/** Strahl bis Wand oder erstes Wesen; am Ende optional eine Explosion ohne Blockschaden. */
	private static boolean beam(AbilityContext ctx, Vector3f color, float defaultExplosion, SoundEvent sound) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 32.0);
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		Optional<LivingEntity> hit = Targeting.findLivingTarget(player, eye.distanceTo(stop));
		if (hit.isPresent() && PartyRules.canHarm(player, hit.get())) {
			stop = hit.get().getPos().add(0.0, hit.get().getHeight() * 0.5, 0.0);
			hit.get().damage(world.getDamageSources().indirectMagic(player, player), (float) ctx.param("damage", 14.0));
		}
		float explosion = (float) ctx.param("explosion", defaultExplosion);
		if (explosion > 0.0f) {
			world.createExplosion(player, stop.x, stop.y, stop.z, explosion, World.ExplosionSourceType.NONE);
		}
		Vec3d step = stop.subtract(eye);
		int points = Math.max(6, (int) (step.length() * 4));
		DustParticleEffect dust = new DustParticleEffect(color, 1.6f);
		for (int i = 1; i <= points; i++) {
			Vec3d p = eye.add(step.multiply(i / (double) points));
			world.spawnParticles(dust, p.x, p.y - 0.15, p.z, 2, 0.05, 0.05, 0.05, 0.0);
		}
		BuiltinAbilities.sound(ctx, sound, 1.0f, 1.3f);
		return true;
	}

	private static boolean particles(AbilityContext ctx, ParticleEffect particle, int count, double spread) {
		ServerPlayerEntity player = ctx.player();
		ctx.world().spawnParticles(particle, player.getX(), player.getBodyY(0.5), player.getZ(), count, spread, 0.8, spread, 0.12);
		return true;
	}

	// --- Sonderfaelle ------------------------------------------------------------------------------

	/** Wildmutt: drei schnelle Bisse auf ein Ziel (jeder Treffer zaehlt einzeln). */
	private static boolean savageMaul(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 4.0), 0.7, e -> PartyRules.canHarm(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		int hits = (int) Math.max(1, ctx.param("hits", 3));
		float damage = (float) ctx.param("damage", 4.0);
		for (int i = 0; i < hits && target.isAlive(); i++) {
			target.timeUntilRegen = 0;
			target.damage(ctx.world().getDamageSources().playerAttack(player), damage);
		}
		ctx.world().spawnParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getBodyY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WOLF_GROWL, 1.0f, 0.6f);
		return true;
	}

	/** Stinkfly: schwere Schleimbombe mit Explosion ohne Blockschaden. */
	private static boolean slimeBomb(AbilityContext ctx) {
		HeroProjectileEntity.shoot(ctx.world(), ctx.player(), Items.SLIME_BALL, (float) ctx.param("speed", 1.4), 0.0f)
				.withDamage((float) ctx.param("damage", 6.0))
				.withExplosion((float) ctx.param("explosion", 1.6));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_JUMP, 1.0f, 0.5f);
		return true;
	}

	/** Stinkfly: Ring aus Stinkwolken um den Spieler. */
	private static boolean toxicStorm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int clouds = (int) Math.max(3, ctx.param("clouds", 6));
		double ring = ctx.param("ring", 4.0);
		for (int i = 0; i < clouds; i++) {
			double angle = i * MathHelper.TAU / clouds;
			AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(world, player.getX() + Math.cos(angle) * ring, player.getY(),
					player.getZ() + Math.sin(angle) * ring);
			cloud.setOwner(player);
			cloud.setRadius((float) ctx.param("radius", 2.5));
			cloud.setDuration((int) ctx.param("ticks", 160));
			cloud.setWaitTime(0);
			cloud.setParticleType(new DustParticleEffect(new Vector3f(0.55f, 0.7f, 0.15f), 1.5f));
			cloud.addEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 1));
			cloud.addEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0));
			cloud.addEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
			world.spawnEntity(cloud);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PUFFER_FISH_BLOW_OUT, 1.2f, 0.4f);
		return true;
	}

	/** Ripjaws: Heilung, im Wasser deutlich staerker. */
	private static boolean hydroHeal(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		boolean wet = player.isTouchingWaterOrRain();
		player.heal((float) (wet ? ctx.param("heal_water", 8.0) : ctx.param("heal", 3.0)));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, (int) (ctx.param("seconds", 5.0) * 20), wet ? 1 : 0));
		ctx.world().spawnParticles(ParticleTypes.BUBBLE, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_DOLPHIN_PLAY, 1.0f, 1.0f);
		return true;
	}

	/** Ghostfreak: Sprung durch den Schatten bis {@code distance} Bloecke in Blickrichtung, auf eine sichere Stelle. */
	private static boolean shadowStep(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double distance = ctx.param("distance", 10.0);
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		HitResult wall = world.raycast(new RaycastContext(eye, eye.add(look.multiply(distance)), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? distance : Math.max(0.0, eye.distanceTo(wall.getPos()) - 0.8);
		for (double d = reach; d >= 1.0; d -= 0.5) {
			Vec3d feet = eye.add(look.multiply(d)).subtract(0.0, player.getStandingEyeHeight(), 0.0);
			BlockPos pos = BlockPos.ofFloored(feet);
			if (world.isSpaceEmpty(player, player.getBoundingBox().offset(feet.subtract(player.getPos())))) {
				world.spawnParticles(ParticleTypes.SQUID_INK, player.getX(), player.getBodyY(0.5), player.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
				player.requestTeleport(feet.x, Math.max(feet.y, pos.getY()), feet.z);
				player.fallDistance = 0.0f;
				ctx.grantInvulnerability((int) ctx.param("invulnerable_ticks", 10));
				world.spawnParticles(ParticleTypes.SOUL, feet.x, feet.y + 1.0, feet.z, 20, 0.3, 0.5, 0.3, 0.02);
				BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
				return true;
			}
		}
		player.sendMessage(Text.translatable("message.kingdomomnitrix.no_space_step").formatted(Formatting.GRAY), true);
		return false;
	}
}
