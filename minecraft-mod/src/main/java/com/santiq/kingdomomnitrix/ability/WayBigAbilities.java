package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Way Big (To'kustar). Eigenes System „Titan“ — ohne Aura:
 *
 * <ul>
 *   <li><b>Kosmische Ladung</b> (0–100): steigt von selbst (5/s) und mit jedem Treffer; der Kosmische Strahl verbraucht
 *       sie fuer Schaden und Breite. Bei voller Ladung knistert Way Big blau.</li>
 *   <li><b>Titanenschritt</b> (passiv): jeder Schritt erschuettert den Boden — kleine Gegner in der Naehe taumeln.</li>
 *   <li>Seine Faehigkeiten treffen Flaechen, nicht Einzelne: Stampfen, Klatschwelle, Riesensprung, Sternensturz.</li>
 * </ul>
 */
final class WayBigAbilities {
	static final float MAX = 100.0f;
	private static final Identifier WAY_BIG = KingdomOmnitrix.id("way_big");
	private static final DustParticleEffect COSMIC = AlienKit.dust("#7FE3FF", 2.0f);
	private static final DustParticleEffect COSMIC_CORE = AlienKit.dust("#FFFFFF", 2.4f);

	private static final Map<UUID, Float> CHARGE = new HashMap<>();
	private static final Map<UUID, Vec3d> LAST_STEP = new HashMap<>();
	private static final Map<UUID, Long> LEAPS = new HashMap<>();
	/** Sternensturz: Start und Zentrum */
	private static final Map<UUID, Object[]> STARFALL = new HashMap<>();
	private static final Map<UUID, Long> GUARD = new HashMap<>();

	private WayBigAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("cosmic_ray"), WayBigAbilities::cosmicRay);
		AbilityRegistry.register(KingdomOmnitrix.id("titan_stomp"), WayBigAbilities::titanStomp);
		AbilityRegistry.register(KingdomOmnitrix.id("giant_leap"), WayBigAbilities::giantLeap);
		AbilityRegistry.register(KingdomOmnitrix.id("shockwave_clap"), WayBigAbilities::shockwaveClap);
		AbilityRegistry.register(KingdomOmnitrix.id("tokustar_guard"), WayBigAbilities::guard);
		AbilityRegistry.register(KingdomOmnitrix.id("starfall"), WayBigAbilities::starfall);
		ServerTickEvents.END_SERVER_TICK.register(WayBigAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken > 0.0f && !AlienKit.bonusHit && source.getAttacker() instanceof ServerPlayerEntity big && big != target && isWayBig(big)) {
				charge(big, taken);
			}
			// To'kustar-Panzer: Nahkampf-Angreifer werden zurueckgestossen
			if (target instanceof ServerPlayerEntity big && GUARD.containsKey(big.getUuid()) && source.getAttacker() instanceof LivingEntity attacker
					&& source.getSource() == attacker && AlienKit.foe(big, attacker)) {
				AlienKit.push(attacker, big.getPos(), 2.0, 0.6);
				AlienKit.melee(big.getServerWorld(), big, attacker, 4.0f);
			}
		});
	}

	private static boolean isWayBig(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(WAY_BIG::equals).isPresent();
	}

	static float charge(ServerPlayerEntity player) {
		return CHARGE.getOrDefault(player.getUuid(), 0.0f);
	}

	private static void charge(ServerPlayerEntity player, float amount) {
		float before = charge(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		CHARGE.put(player.getUuid(), after);
		if (before < MAX && after >= MAX) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.way_big_charged").formatted(Formatting.AQUA), true);
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 1.4f);
		}
	}

	private static void quake(ServerWorld world, ServerPlayerEntity big, Vec3d center, double radius, float damage, double launch) {
		BlockState ground = world.getBlockState(BlockPos.ofFloored(center).down());
		for (LivingEntity target : AlienKit.around(world, big, center, radius)) {
			if (damage > 0.0f) {
				AlienKit.melee(world, big, target, damage);
			}
			AlienKit.push(target, center, 0.8, launch);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2), big);
		}
		if (!ground.isAir()) {
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), center.x, center.y + 0.2, center.z,
					(int) (radius * 15), radius * 0.4, 0.1, radius * 0.4, 0.2);
		}
		world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.PLAYERS, 2.0f, 0.4f);
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Kosmischer Strahl: riesiger Strahl aus den gekreuzten Armen — verbraucht die Ladung fuer Schaden und Breite. */
	private static boolean cosmicRay(AbilityContext ctx) {
		ServerPlayerEntity big = ctx.player();
		ServerWorld world = ctx.world();
		float spent = charge(big);
		CHARGE.put(big.getUuid(), 0.0f);
		double range = ctx.param("range", 48.0);
		Vec3d from = big.getEyePos().add(big.getRotationVec(1.0f).multiply(2.0)).add(0, -1.0, 0);
		Vec3d to = big.getEyePos().add(big.getRotationVec(1.0f).multiply(range));
		double width = 1.0 + spent / 50.0;
		float damage = (float) ctx.param("damage", 10.0) + spent * 0.2f;
		for (LivingEntity target : AlienKit.along(world, big, from, to, width)) {
			AlienKit.magic(world, big, target, damage);
			AlienKit.push(target, big.getPos(), 1.0, 0.3);
		}
		AlienKit.line(world, from, to, COSMIC, 3.0);
		AlienKit.line(world, from, to, COSMIC_CORE, 1.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.7f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.6f);
		return true;
	}

	/** Titanenstampfer: Beben um Way Big, schleudert hoch. */
	private static boolean titanStomp(AbilityContext ctx) {
		ServerPlayerEntity big = ctx.player();
		quake(ctx.world(), big, big.getPos(), ctx.param("radius", 8.0), (float) ctx.param("damage", 10.0), 0.8);
		return true;
	}

	/** Riesensprung: gewaltiger Satz; die Landung bebt im grossen Umkreis. */
	private static boolean giantLeap(AbilityContext ctx) {
		ServerPlayerEntity big = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(big);
		BuiltinAbilities.launch(big, dir.x * 1.6, 1.3, dir.z * 1.6);
		LEAPS.put(big.getUuid(), ctx.world().getTime());
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.4f);
		return true;
	}

	/** Klatschwelle: die Druckwelle zweier riesiger Haende — Kegel, enormer Rueckstoss. */
	private static boolean shockwaveClap(AbilityContext ctx) {
		ServerPlayerEntity big = ctx.player();
		ServerWorld world = ctx.world();
		for (LivingEntity target : AlienKit.cone(world, big, ctx.param("range", 14.0), 0.5)) {
			AlienKit.melee(world, big, target, (float) ctx.param("damage", 8.0));
			AlienKit.push(target, big.getPos(), 2.6, 0.7);
		}
		Vec3d look = big.getRotationVec(1.0f);
		for (int i = 2; i <= 14; i += 2) {
			Vec3d p = big.getEyePos().add(look.multiply(i));
			world.spawnParticles(ParticleTypes.GUST, p.x, p.y - 1, p.z, 1, i * 0.1, 0.3, i * 0.1, 0.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 2.0f, 0.5f);
		return true;
	}

	/** To'kustar-Panzer: kaum Schaden, kein Rueckstoss, Nahkampf-Angreifer prallen ab. */
	private static boolean guard(AbilityContext ctx) {
		ServerPlayerEntity big = ctx.player();
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		GUARD.put(big.getUuid(), ctx.world().getTime() + ticks);
		big.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 2, false, false));
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 2.0f, 0.5f);
		return true;
	}

	/** Sternensturz: acht kosmische Einschlaege im Umkreis nacheinander. */
	private static boolean starfall(AbilityContext ctx) {
		ServerPlayerEntity big = ctx.player();
		STARFALL.put(big.getUuid(), new Object[]{ctx.world().getTime(), big.getPos()});
		ctx.grantInvulnerability(40);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WITHER_SPAWN, 1.0f, 1.4f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity big : server.getPlayerManager().getPlayerList()) {
			UUID id = big.getUuid();
			if (!isWayBig(big)) {
				forget(id);
				continue;
			}
			ServerWorld world = big.getServerWorld();
			long now = world.getTime();
			if (now % 20 == 0) {
				charge(big, 5.0f);
			}
			if (charge(big) >= MAX && now % 4 == 0) {
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, big.getX(), big.getBodyY(0.6), big.getZ(), 4, 1.0, 2.0, 1.0, 0.1);
			}
			// Titanenschritt
			Vec3d last = LAST_STEP.get(id);
			if (big.isOnGround() && (last == null || last.squaredDistanceTo(big.getPos()) > 9.0)) {
				LAST_STEP.put(id, big.getPos());
				for (LivingEntity near : AlienKit.around(world, big, big.getPos(), 3.5)) {
					near.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 1), big);
				}
				world.playSound(null, big.getBlockPos(), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.PLAYERS, 1.2f, 0.4f);
			}
			Long leap = LEAPS.get(id);
			if (leap != null && now - leap > 5 && big.isOnGround()) {
				LEAPS.remove(id);
				quake(world, big, big.getPos(), 10.0, 12.0f, 0.9);
			} else if (leap != null && now - leap > 200) {
				LEAPS.remove(id);
			}
			Long guard = GUARD.get(id);
			if (guard != null && now >= guard) {
				GUARD.remove(id);
			}
			Object[] fall = STARFALL.get(id);
			if (fall != null) {
				long age = now - (long) fall[0];
				Vec3d center = (Vec3d) fall[1];
				if (age >= 48) {
					STARFALL.remove(id);
				} else if (age % 6 == 0) {
					double a = age * 1.7;
					double r = 4 + (age % 12) * 0.8;
					Vec3d hit = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
					AlienKit.line(world, hit.add(0, 24, 0), hit, COSMIC, 1.5);
					world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, hit.x, hit.y + 0.5, hit.z, 1, 0.0, 0.0, 0.0, 0.0);
					for (LivingEntity target : AlienKit.around(world, big, hit, 4.0)) {
						AlienKit.magic(world, big, target, 12.0f);
						AlienKit.push(target, hit, 1.2, 0.6);
					}
					world.playSound(null, BlockPos.ofFloored(hit), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0f, 0.6f);
				}
			}
		}
	}

	private static void forget(UUID id) {
		CHARGE.remove(id);
		LAST_STEP.remove(id);
		LEAPS.remove(id);
		STARFALL.remove(id);
		GUARD.remove(id);
	}
}
