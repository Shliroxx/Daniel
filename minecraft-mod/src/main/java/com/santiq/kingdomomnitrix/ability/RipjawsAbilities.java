package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Ripjaws (Piscciss Volann). Eigenes Dauer-System „Gezeiten“ — keine Aura:
 *
 * <ul>
 *   <li><b>Gezeitenzonen</b> ({@link TideZones}): Strudel, Hydroheilung, Flutstoss und Flutwelle hinterlassen Bereiche,
 *       die fuer Ripjaws als Wasser zaehlen — Wasser-Boni, Wasser-Effekte, kein Austrocknen. Gegner darin werden von
 *       der Stroemung gebremst.</li>
 *   <li><b>Biss-Kombo</b>: Bisse innerhalb von {@link #COMBO_TICKS} Ticks steigern sich (+25 % je Stufe); der dritte
 *       ist die <b>Todesrolle</b> — das Ziel wird herumgerissen, nach unten gezogen und festgehalten.</li>
 *   <li>Nass (Wasser, Regen, eigene Zone) ist alles staerker: Biss, Vorstoss, Schwanzhieb, Welle.</li>
 * </ul>
 */
final class RipjawsAbilities {
	private static final Identifier RIPJAWS = KingdomOmnitrix.id("ripjaws");
	private static final int COMBO_TICKS = 60;
	private static final int ROLL_HITS = 3;
	private static final float COMBO_BONUS = 0.25f;

	/** Biss-Kombo: Stufe und letzter Biss */
	private static final Map<UUID, long[]> COMBO = new HashMap<>();
	/** laufende Flutwellen */
	private static final List<Wave> WAVES = new ArrayList<>();

	private static final class Wave {
		final UUID owner;
		final Vec3d origin;
		final Vec3d facing;
		final double radius;
		final double cone;
		final float damage;
		final double knockback;
		final long start;
		final Set<UUID> hit = new HashSet<>();

		Wave(UUID owner, Vec3d origin, Vec3d facing, double radius, double cone, float damage, double knockback, long start) {
			this.owner = owner;
			this.origin = origin;
			this.facing = facing;
			this.radius = radius;
			this.cone = cone;
			this.damage = damage;
			this.knockback = knockback;
			this.start = start;
		}
	}

	private RipjawsAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("jaw_bite"), RipjawsAbilities::jawBite);
		AbilityRegistry.register(KingdomOmnitrix.id("tidal_dash"), RipjawsAbilities::tidalDash);
		AbilityRegistry.register(KingdomOmnitrix.id("whirlpool"), RipjawsAbilities::whirlpool);
		AbilityRegistry.register(KingdomOmnitrix.id("tail_swipe"), RipjawsAbilities::tailSwipe);
		AbilityRegistry.register(KingdomOmnitrix.id("hydro_heal"), RipjawsAbilities::hydroHeal);
		AbilityRegistry.register(KingdomOmnitrix.id("tidal_wave"), RipjawsAbilities::tidalWave);
		ServerTickEvents.END_SERVER_TICK.register(RipjawsAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			COMBO.remove(handler.getPlayer().getUuid());
			TideZones.forget(handler.getPlayer().getUuid());
		});
	}

	private static boolean isRipjaws(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(RIPJAWS::equals).isPresent();
	}

	private static void zone(ServerPlayerEntity player, Vec3d center, double radius, int ticks) {
		TideZones.add(new TideZones.Zone(player.getUuid(), player.getServerWorld().getRegistryKey(), center, radius,
				player.getServerWorld().getTime() + ticks));
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Kieferbiss mit Kombo; der dritte Biss ist die Todesrolle. */
	private static boolean jawBite(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 4.0), 0.7, e -> PartyRules.canHarm(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		long now = world.getTime();
		long[] combo = COMBO.computeIfAbsent(player.getUuid(), id -> new long[2]);
		combo[0] = now - combo[1] <= COMBO_TICKS ? combo[0] + 1 : 1;
		combo[1] = now;
		boolean wet = TideZones.isWet(player);
		float damage = (float) (ctx.param("damage", 8.0) * (wet ? ctx.param("water_multiplier", 1.5) : 1.0) * (1.0 + COMBO_BONUS * (combo[0] - 1)));
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().playerAttack(player), damage);
		player.heal((float) ctx.param("heal", 2.0));
		if (combo[0] >= ROLL_HITS) {
			combo[0] = 0;
			// Todesrolle: herumreissen, nach unten ziehen, festhalten
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().playerAttack(player), damage);
			target.setVelocity(0.0, -0.6, 0.0);
			target.velocityModified = true;
			target.setYaw(target.getYaw() + 180.0f);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 4), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 1), player);
			world.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, target.getX(), target.getBodyY(0.5), target.getZ(), 40, 0.4, 0.6, 0.4, 0.2);
			world.spawnParticles(ParticleTypes.SPLASH, target.getX(), target.getBodyY(0.5), target.getZ(), 40, 0.6, 0.4, 0.6, 0.3);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.2f, 0.7f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.ripjaws_death_roll").formatted(Formatting.AQUA), true);
		} else {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.ripjaws_combo", combo[0], ROLL_HITS).formatted(Formatting.AQUA), true);
		}
		world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getBodyY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, 1.0f, 0.8f);
		return true;
	}

	/** Flutstoss: nass dreimal so weit und rammend; hinterlaesst eine kleine Gezeitenzone am Ziel. */
	private static boolean tidalDash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = player.getRotationVec(1.0f);
		boolean wet = TideZones.isWet(player);
		double power = ctx.param("power", 1.0) * (wet ? ctx.param("water_multiplier", 3.0) : 1.0);
		if (wet) {
			Box path = player.getBoundingBox().stretch(look.multiply(power * 4.0)).expand(0.8);
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, path, e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
				target.damage(world.getDamageSources().playerAttack(player), 5.0f);
				target.takeKnockback(1.0, -look.x, -look.z);
			}
		}
		BuiltinAbilities.launch(player, look.x * power, wet ? look.y * power : 0.25, look.z * power);
		zone(player, player.getPos().add(look.multiply(power * 4.0)), 2.5, 100);
		world.spawnParticles(wet ? ParticleTypes.BUBBLE_COLUMN_UP : ParticleTypes.SPLASH, player.getX(), player.getBodyY(0.5), player.getZ(),
				24, 0.4, 0.4, 0.4, 0.1);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_DOLPHIN_SPLASH, 1.0f, 0.9f);
		return true;
	}

	/** Strudel: Gezeitenzone, die Gegner 6 s lang zur Mitte zieht und ertraenkt. */
	private static boolean whirlpool(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		boolean wet = TideZones.isWet(player);
		double radius = ctx.param("radius", 5.0) * (wet ? 1.5 : 1.0);
		zone(player, player.getPos(), radius, 120);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.2f, 0.8f);
		return true;
	}

	/** Schwanzhieb: Kegel nach vorn; nass mit Wasserklinge (weiter) und durchnaesst Gegner (langsamer). */
	private static boolean tailSwipe(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		boolean wet = TideZones.isWet(player);
		double radius = ctx.param("radius", 4.5) * (wet ? 1.6 : 1.0);
		double cone = ctx.param("cone", 0.3);
		float damage = (float) (ctx.param("damage", 7.0) * (wet ? ctx.param("water_multiplier", 1.3) : 1.0));
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			Vec3d flat = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			if (flat.lengthSquared() > 1.0E-4 && flat.normalize().dotProduct(look) < cone) {
				continue;
			}
			target.damage(world.getDamageSources().playerAttack(player), damage);
			target.takeKnockback(ctx.param("knockback", 1.8), -look.x, -look.z);
			if (wet) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1), player);
			}
		}
		for (int i = -8; i <= 8; i++) {
			double a = Math.atan2(look.z, look.x) + i * 0.12;
			for (double d = 1.0; d <= radius; d += wet ? 0.8 : radius) {
				world.spawnParticles(ParticleTypes.SPLASH, player.getX() + Math.cos(a) * d, player.getBodyY(0.5), player.getZ() + Math.sin(a) * d,
						2, 0.05, 0.05, 0.05, 0.0);
			}
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
		return true;
	}

	/** Hydroheilung: heilt (nass mehr) und legt eine Gezeitenzone um Ripjaws — dort kein Austrocknen, Regeneration. */
	private static boolean hydroHeal(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		boolean wet = TideZones.isWet(player);
		int ticks = (int) (ctx.param("seconds", 5.0) * 20);
		player.heal((float) (wet ? ctx.param("heal_water", 8.0) : ctx.param("heal", 3.0)));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, ticks, wet ? 1 : 0));
		zone(player, player.getPos(), 4.0, ticks * 3);
		ctx.world().spawnParticles(ParticleTypes.BUBBLE, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_DOLPHIN_PLAY, 1.0f, 1.0f);
		return true;
	}

	/** Flutwelle: eine Wasserwand laeuft im Kegel nach vorn, trifft jeden einmal und hinterlaesst Gezeitenzonen. */
	private static boolean tidalWave(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		boolean wet = TideZones.isWet(player);
		float damage = (float) (ctx.param("damage", 10.0) * (wet ? ctx.param("water_multiplier", 1.5) : 1.0));
		WAVES.add(new Wave(player.getUuid(), player.getPos(), BuiltinAbilities.horizontalLook(player), ctx.param("radius", 10.0),
				ctx.param("cone", 0.5), damage, ctx.param("knockback", 2.5), ctx.world().getTime()));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_SPLASH, 1.4f, 0.6f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		TideZones.tick(server);
		long tick = server.getTicks();
		if (tick % 10 == 0) {
			tickCurrents(server);
		}
		if (!WAVES.isEmpty()) {
			tickWaves(server);
		}
		if (tick % 100 == 0 && !COMBO.isEmpty()) {
			COMBO.keySet().removeIf(id -> {
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
				return p == null || !isRipjaws(p);
			});
		}
	}

	/** Stroemung: Gegner in Gezeitenzonen werden gebremst und zur Mitte gezogen, alle 2 s etwas Ertrinkungsschaden. */
	private static void tickCurrents(MinecraftServer server) {
		for (TideZones.Zone zone : TideZones.all()) {
			ServerWorld world = server.getWorld(zone.world());
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(zone.owner());
			if (world == null || owner == null) {
				continue;
			}
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, Box.of(zone.center(), zone.radius() * 2, 6, zone.radius() * 2),
					e -> e != owner && e.isAlive() && zone.contains(e.getPos()) && PartyRules.canHarm(owner, e))) {
				Vec3d in = zone.center().subtract(target.getPos()).multiply(1, 0, 1);
				Vec3d swirl = new Vec3d(-in.z, 0, in.x);
				Vec3d push = in.lengthSquared() > 1.0E-4 ? in.normalize().multiply(0.18).add(swirl.normalize().multiply(0.12)) : Vec3d.ZERO;
				target.setVelocity(target.getVelocity().multiply(0.5, 1.0, 0.5).add(push));
				target.velocityModified = true;
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 1), owner);
				if (server.getTicks() % 40 == 0) {
					target.damage(world.getDamageSources().drown(), 2.0f);
				}
			}
		}
	}

	private static void tickWaves(MinecraftServer server) {
		Iterator<Wave> it = WAVES.iterator();
		while (it.hasNext()) {
			Wave wave = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(wave.owner);
			if (player == null) {
				it.remove();
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long age = world.getTime() - wave.start;
			double front = wave.radius * Math.min(1.0, (age + 1) / 14.0);
			int points = (int) (front * 4);
			for (int i = 0; i < points; i++) {
				double a = i * MathHelper.TAU / Math.max(1, points);
				Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
				if (dir.dotProduct(wave.facing) < wave.cone) {
					continue;
				}
				Vec3d p = wave.origin.add(dir.multiply(front));
				world.spawnParticles(ParticleTypes.SPLASH, p.x, p.y + 0.5, p.z, 4, 0.1, 0.6, 0.1, 0.1);
				world.spawnParticles(ParticleTypes.BUBBLE_POP, p.x, p.y + 1.2, p.z, 1, 0.1, 0.3, 0.1, 0.0);
			}
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, Box.of(wave.origin, front * 2 + 1, 5, front * 2 + 1),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && !wave.hit.contains(e.getUuid()))) {
				Vec3d flat = target.getPos().subtract(wave.origin).multiply(1, 0, 1);
				double distance = flat.length();
				if (distance > front || (distance > 1.0E-3 && flat.normalize().dotProduct(wave.facing) < wave.cone)) {
					continue;
				}
				wave.hit.add(target.getUuid());
				target.damage(world.getDamageSources().playerAttack(player), wave.damage);
				target.takeKnockback(wave.knockback, -wave.facing.x, -wave.facing.z);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), player);
			}
			// alle paar Ticks eine Gezeitenzone hinter der Front
			if (age % 4 == 0) {
				zone(player, wave.origin.add(wave.facing.multiply(front * 0.8)), 2.5, 160);
			}
			if (age >= 14) {
				it.remove();
			}
		}
	}
}
