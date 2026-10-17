package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.vfx.EarthWave;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
	static final int RAY = 0x7FE3FF;
	static final int CORE = 0xFFFFFF;
	static final int DUST = 0x9C8466;
	static final float MAX = 100.0f;
	private static final Identifier WAY_BIG = KingdomOmnitrix.id("way_big");

	private static final Map<UUID, Float> CHARGE = new HashMap<>();
	private static final Map<UUID, Long> LEAPS = new HashMap<>();
	/** Sternensturz: Start und Zentrum */
	private static final Map<UUID, Object[]> STARFALL = new HashMap<>();
	private static final Map<UUID, Long> GUARD = new HashMap<>();

	private WayBigAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("cosmic_ray"), new CosmicRay());
		AbilityRegistry.register(KingdomOmnitrix.id("titan_stomp"), WayBigAbilities::titanStomp);
		AbilityRegistry.register(KingdomOmnitrix.id("giant_leap"), WayBigAbilities::giantLeap);
		AbilityRegistry.register(KingdomOmnitrix.id("shockwave_clap"), WayBigAbilities::shockwaveClap);
		AbilityRegistry.register(KingdomOmnitrix.id("tokustar_guard"), WayBigAbilities::guard);
		AbilityRegistry.register(KingdomOmnitrix.id("starfall"), WayBigAbilities::starfall);
		ServerTickEvents.END_SERVER_TICK.register(WayBigAbilities::tick);
		WayBigMoves.register();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			// Ladung durch Treffer laeuft ueber die Passive „Kosmische Ladung“ (WayBigMoves)
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

	static void charge(ServerPlayerEntity player, float amount) {
		float before = charge(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		CHARGE.put(player.getUuid(), after);
		if (before < MAX && after >= MAX) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.way_big_charged").formatted(Formatting.AQUA), true);
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 1.4f);
		}
	}

	private static void quake(ServerWorld world, ServerPlayerEntity big, Vec3d center, double radius, float damage, double launch) {
		quake(world, big, center, radius, damage, launch, 0.0);
	}

	/** Beben mit Krater ({@code crater} = Radius, 0 = keiner): springende Bodenbloecke, Druckwelle, Staub, Wackeln. */
	static void quake(ServerWorld world, ServerPlayerEntity big, Vec3d center, double radius, float damage, double launch, double crater) {
		for (LivingEntity target : AlienKit.around(world, big, center, radius)) {
			if (damage > 0.0f) {
				AlienKit.melee(world, big, target, damage);
			}
			AlienKit.push(target, center, 0.8, launch);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2), big);
		}
		EarthWave.ring(world, center, radius, (int) (radius * 0.8), 1.3f);
		Fx.shockwave(world, center.add(0, 0.1, 0), DUST, (float) radius * 1.2f, 14);
		Fx.smoke(world, center, DUST, (float) (1.2 + radius * 0.08), (int) (4 + radius * 0.4));
		Fx.shake(world, center, 0.8f, 14, (int) (radius * 4));
		if (crater > 0.0) {
			Destruction.crater(world, big, center.add(0, -0.6, 0), crater, 0.45, AlienStrength.of(big), 0.5, 0.5);
		}
		world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.PLAYERS, 2.0f, 0.4f);
		world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5f, 0.5f);
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/**
	 * Kosmischer Strahl (Halten laedt auf, bis 1,5 s): der Strahl aus den gekreuzten Armen. Verbraucht die kosmische
	 * Ladung fuer Schaden und Breite, brennt eine Schneise durch das Gelaende und explodiert am Ende.
	 */
	private static final class CosmicRay implements ChargeableAbility {
		@Override
		public int chargeTicks(AbilityContext ctx) {
			return 30;
		}

		@Override
		public void charging(AbilityContext ctx, int ticks, float charge) {
			ServerPlayerEntity big = ctx.player();
			big.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 3, 3, false, false));
			if (ticks % 3 == 0) {
				Vec3d arms = big.getEyePos().add(big.getRotationVec(1.0f).multiply(2.0)).add(0, -1.0, 0);
				Fx.flash(ctx.world(), arms, RAY, 1.0f + charge * 3.0f);
				Fx.sparks(ctx.world(), arms, RAY, 0.3f + charge * 0.5f, 4);
			}
		}

		@Override
		public boolean release(AbilityContext ctx, float charge) {
			return cosmicRay(ctx, charge);
		}
	}

	private static boolean cosmicRay(AbilityContext ctx, float charge) {
		ServerPlayerEntity big = ctx.player();
		ServerWorld world = ctx.world();
		float spent = charge(big);
		CHARGE.put(big.getUuid(), 0.0f);
		Vec3d look = big.getRotationVec(1.0f);
		Vec3d from = big.getEyePos().add(look.multiply(2.0)).add(0, -1.0, 0);
		// der Strahl endet am ersten Hindernis, das er nicht durchbrennen kann, sonst bei voller Reichweite
		double range = ctx.param("range", 48.0) * (0.6 + 0.4 * charge);
		Vec3d to = from.add(look.multiply(range));
		double width = 1.0 + spent / 50.0 + charge;
		float damage = (float) ctx.param("damage", 10.0) * (1.0f + charge) + spent * 0.2f;
		for (LivingEntity target : AlienKit.along(world, big, from, to, width)) {
			AlienKit.magic(world, big, target, damage);
			AlienKit.push(target, big.getPos(), 1.0 + charge, 0.3);
		}
		Destruction.carve(world, big, from.add(look.multiply(2.0)), look, range - 2.0, 0.6 + width * 0.6, AlienStrength.of(big) * (0.3f + charge),
				0.15);
		Fx.beam(world, from, to, RAY, (float) width * 1.6f, 18);
		Fx.beam(world, from, to, CORE, (float) width * 0.6f, 18);
		Fx.flash(world, from.add(look.multiply(6.0)), CORE, (float) (1.5 + width));
		Fx.explosion(world, to, RAY, (float) (2.5 + width * 2.0));
		Fx.shockwave(world, to, RAY, (float) (4.0 + width * 3.0), 14);
		Fx.shake(world, big.getPos(), 0.5f + charge * 0.5f, 16, 64);
		Destruction.crater(world, big, to, 1.5 + width * 1.2, AlienStrength.of(big) * (0.3f + charge), 0.4, 0.6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.7f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.6f);
		world.playSound(null, BlockPos.ofFloored(to), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.0f, 0.6f);
		return true;
	}

	/** Titanenstampfer: Beben um Way Big, schleudert hoch. */
	private static boolean titanStomp(AbilityContext ctx) {
		ServerPlayerEntity big = ctx.player();
		quake(ctx.world(), big, big.getPos(), ctx.param("radius", 8.0), (float) ctx.param("damage", 10.0), 0.8, 3.5);
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
		for (int i = 0; i < 5; i++) {
			Vec3d p = big.getEyePos().add(look.multiply(3 + i * 2.5)).add(0, -1, 0);
			Fx.send(world, Fx.Kind.SHOCKWAVE, p, look, 0xE8F4FF, 2.0f + i * 1.4f, 8 + i * 2, 1, null);
		}
		Fx.flash(world, big.getEyePos().add(look.multiply(3)), 0xFFFFFF, 4.0f);
		// der Luftstoss knickt Baeume, reisst Laub, Glas, Erde und Holz im Kegel weg
		Destruction.carve(world, big, big.getEyePos().add(look.multiply(3)).add(0, -1, 0), look, ctx.param("range", 14.0), 3.0, 2.2f, 0.6);
		Fx.shake(world, big.getPos(), 0.5f, 10, 40);
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
			if (charge(big) >= MAX && now % 4 == 0) {
				Fx.sparks(world, new Vec3d(big.getX(), big.getBodyY(0.6), big.getZ()), RAY, 0.4f, 3);
			}
			// Titanenschritt laeuft ueber die gleichnamige Passive (WayBigMoves)
			Long leap = LEAPS.get(id);
			if (leap != null && now - leap > 5 && big.isOnGround()) {
				LEAPS.remove(id);
				quake(world, big, big.getPos(), 10.0, 12.0f, 0.9, 5.0);
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
					Fx.beam(world, hit.add(0, 40, 0), hit, RAY, 2.0f, 10);
					Fx.beam(world, hit.add(0, 40, 0), hit, CORE, 0.7f, 10);
					Fx.explosion(world, hit.add(0, 0.5, 0), RAY, 3.5f);
					Fx.shockwave(world, hit.add(0, 0.1, 0), RAY, 6.0f, 10);
					Fx.shake(world, hit, 0.5f, 8, 40);
					Destruction.crater(world, big, hit.add(0, -0.5, 0), 2.8, 0.6, AlienStrength.of(big) * 0.4f, 0.3, 0.6);
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
		LEAPS.remove(id);
		STARFALL.remove(id);
		GUARD.remove(id);
	}
}
