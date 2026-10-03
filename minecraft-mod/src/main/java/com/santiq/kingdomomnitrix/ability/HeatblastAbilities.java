package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Heatblast (Pyronite). Eigenes Dauer-System „Kernhitze“ (0–100) statt fester Feuerzauber:
 *
 * <ul>
 *   <li>Jede Faehigkeit heizt den Kern auf; Feuer/Lava und der Nether heizen nach, Wasser und Regen kuehlen schnell
 *       (Dampf, kurz geschwaecht). Sonst kuehlt der Kern langsam ab.</li>
 *   <li><b>Gluehend</b> (ab {@link #GLOW}): +30 % Schaden, Feuerstoss feuert drei Kugeln, Hitze-Aura versengt Gegner
 *       in der Naehe.</li>
 *   <li><b>Ueberhitzt</b> ({@link #MAX}): die naechste Faehigkeit entlaedt den ganzen Kern als <i>Blaufeuer</i> —
 *       doppelter Schaden, groessere Reichweite, Seelenflammen. Danach ist der Kern leer.</li>
 * </ul>
 * Die Anzeige laeuft ueber die Aktionsleiste. Kein Blockschaden, kein Feuer in der Welt (nur an Gegnern).
 */
final class HeatblastAbilities {
	static final float MAX = 100.0f;
	static final float GLOW = 50.0f;
	private static final float GLOW_FACTOR = 1.3f;
	private static final float BLUE_FACTOR = 2.0f;
	/** Pruefintervall fuer passive Hitze (Ticks) */
	private static final int PASSIVE_INTERVAL = 5;
	/** Abkuehlen je Pruefintervall ohne Hitzequelle (1 %/s) und Hitze je Treffer */
	private static final float COOLING = 0.25f;
	private static final float HEAT_PER_HIT = 1.0f;
	private static final Identifier HEATBLAST = KingdomOmnitrix.id("heatblast");

	/** Kernhitze je Spieler */
	private static final Map<UUID, Float> HEAT = new HashMap<>();
	/** laufende Flammensurf-Fahrten und Schutzschilde: Ende (Welt-Tick) */
	private static final Map<UUID, Long> SURF = new HashMap<>();
	private static final Map<UUID, Long> SHIELD = new HashMap<>();
	/** Inferno-Wellen in Bewegung */
	private static final Map<UUID, Wave> WAVES = new HashMap<>();
	/** Sonnenkern nach einer vollen Supernova: Ort und Ende */
	private static final Map<UUID, Core> CORES = new HashMap<>();

	private static final class Wave {
		final Vec3d origin;
		final Vec3d facing;
		final double radius;
		final double cone;
		final float damage;
		final float fire;
		final double knockback;
		final long start;
		final boolean blue;
		final Set<UUID> hit = new HashSet<>();

		Wave(Vec3d origin, Vec3d facing, double radius, double cone, float damage, float fire, double knockback, long start, boolean blue) {
			this.origin = origin;
			this.facing = facing;
			this.radius = radius;
			this.cone = cone;
			this.damage = damage;
			this.fire = fire;
			this.knockback = knockback;
			this.start = start;
			this.blue = blue;
		}
	}

	private record Core(Vec3d pos, long until, float damage, double radius) {
	}

	private HeatblastAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("fire_blast"), HeatblastAbilities::fireBlast);
		AbilityRegistry.register(KingdomOmnitrix.id("fire_burst"), HeatblastAbilities::fireBurst);
		AbilityRegistry.register(KingdomOmnitrix.id("flame_boost"), HeatblastAbilities::flameSurf);
		AbilityRegistry.register(KingdomOmnitrix.id("inferno_wave"), HeatblastAbilities::infernoWave);
		AbilityRegistry.register(KingdomOmnitrix.id("flame_shield"), HeatblastAbilities::flameShield);
		AbilityRegistry.register(KingdomOmnitrix.id("supernova"), HeatblastAbilities::supernova);
		ServerTickEvents.END_SERVER_TICK.register(HeatblastAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
	}

	// --- Kernhitze -------------------------------------------------------------------------------

	static float heat(ServerPlayerEntity player) {
		return HEAT.getOrDefault(player.getUuid(), 0.0f);
	}

	private static void addHeat(ServerPlayerEntity player, float amount) {
		float before = heat(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		HEAT.put(player.getUuid(), after);
		if (before < MAX && after >= MAX) {
			ServerWorld world = player.getServerWorld();
			world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.5, 0.8, 0.5, 0.08);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.0f, 0.7f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.heatblast_overheat").formatted(Formatting.AQUA), true);
		}
	}

	/** Schadensfaktor der aktuellen Stufe; im Ueberhitzt-Zustand wird der Kern entladen (blau). */
	private static Power power(ServerPlayerEntity player) {
		float heat = heat(player);
		if (heat >= MAX) {
			HEAT.put(player.getUuid(), 0.0f);
			return new Power(BLUE_FACTOR, true);
		}
		return new Power(heat >= GLOW ? GLOW_FACTOR : 1.0f, false);
	}

	private record Power(float factor, boolean blue) {
		ParticleEffect flame() {
			return blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
		}
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	private static boolean fireBlast(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Power power = power(player);
		float speed = (float) ctx.param("speed", 2.2);
		float damage = (float) ctx.param("damage", 6.0) * power.factor();
		int count = power.blue() ? 5 : heat(player) >= GLOW ? 3 : 1;
		for (int i = 0; i < count; i++) {
			HeroProjectileEntity orb = HeroProjectileEntity.shoot(ctx.world(), player, ModItems.FIRE_ORB, speed, count == 1 ? 0.0f : 6.0f)
					.withDamage(damage);
			if (power.blue()) {
				orb.withExplosion(1.2f);
			}
		}
		addHeat(player, (float) ctx.param("heat", 8.0));
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_FIRE, 1.0f, power.blue() ? 0.7f : 1.0f);
		return true;
	}

	private static boolean fireBurst(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Power power = power(player);
		double radius = ctx.param("radius", 4.5) * (power.blue() ? 1.5 : 1.0);
		float damage = (float) ctx.param("damage", 7.0) * power.factor();
		float fire = (float) ctx.param("fire_seconds", 5.0);
		for (LivingEntity target : around(player, radius)) {
			hit(player, target, damage, fire);
			Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			if (away.lengthSquared() > 1.0E-4) {
				target.takeKnockback(0.5, -away.x, -away.z);
			}
		}
		ServerWorld world = ctx.world();
		ring(world, power.flame(), player.getPos().add(0, 0.3, 0), radius, 48, 0.12);
		ring(world, power.flame(), player.getPos().add(0, 1.0, 0), radius * 0.7, 36, 0.08);
		world.spawnParticles(ParticleTypes.LAVA, player.getX(), player.getBodyY(0.5), player.getZ(), 12, 0.5, 0.4, 0.5, 0.0);
		addHeat(player, (float) ctx.param("heat", 15.0));
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_FIRE, 1.2f, power.blue() ? 0.55f : 0.75f);
		return true;
	}

	/** Flammensurfen: Absprung, danach Gleiten auf einer Feuersaeule; versengt alles darunter. */
	private static boolean flameSurf(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		BuiltinAbilities.launch(player, look.x * ctx.param("forward", 0.7), ctx.param("up", 1.25), look.z * ctx.param("forward", 0.7));
		int ticks = (int) ctx.param("surf_ticks", 60.0);
		SURF.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, ticks, 0, false, false));
		ctx.world().spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 30, 0.3, 0.1, 0.3, 0.08);
		addHeat(player, (float) ctx.param("heat", 10.0));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_SHOOT, 0.9f, 0.8f);
		return true;
	}

	/** Inferno-Welle: Flammenfront laeuft im Kegel nach aussen und trifft jedes Ziel einmal, wenn sie es erreicht. */
	private static boolean infernoWave(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Power power = power(player);
		WAVES.put(player.getUuid(), new Wave(player.getPos(), BuiltinAbilities.horizontalLook(player),
				ctx.param("radius", 7.0) * (power.blue() ? 1.6 : 1.0), power.blue() ? 0.0 : ctx.param("cone", 0.5),
				(float) ctx.param("damage", 8.0) * power.factor(), (float) ctx.param("fire_seconds", 5.0),
				ctx.param("knockback", 0.6), ctx.world().getTime(), power.blue()));
		addHeat(player, (float) ctx.param("heat", 15.0));
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_FIRECHARGE_USE, 1.0f, power.blue() ? 0.5f : 0.8f);
		return true;
	}

	/** Flammenschild: verbrennt Geschosse in der Naehe, setzt Angreifer in Brand, speichert Hitze. */
	private static boolean flameShield(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) Math.round(ctx.param("seconds", 10.0) * 20.0);
		SHIELD.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 0, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, ticks, 0, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, ticks, 1, false, false));
		ring(ctx.world(), ParticleTypes.FLAME, player.getPos().add(0, 1.0, 0), 1.4, 32, 0.0);
		addHeat(player, (float) ctx.param("heat", 10.0));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
		return true;
	}

	/** Supernova: entlaedt den ganzen Kern — je mehr Hitze, desto groesser. Bei vollem Kern bleibt ein Sonnenkern stehen. */
	private static boolean supernova(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float heat = heat(player);
		HEAT.put(player.getUuid(), 0.0f);
		float scale = 1.0f + heat / MAX;
		double radius = ctx.param("radius", 9.0) * (0.8 + 0.4 * heat / MAX);
		float damage = (float) ctx.param("damage", 16.0) * scale;
		boolean blue = heat >= MAX;
		ParticleEffect flame = blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
		for (LivingEntity target : around(player, radius)) {
			double distance = Math.sqrt(target.squaredDistanceTo(player));
			hit(player, target, (float) (damage * (1.0 - 0.4 * distance / radius)), (float) ctx.param("fire_seconds", 8.0));
			Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			if (away.lengthSquared() > 1.0E-4) {
				target.takeKnockback(ctx.param("knockback", 1.6) * scale * 0.7, -away.x, -away.z);
			}
			target.addVelocity(0.0, ctx.param("launch", 0.5), 0.0);
			target.velocityModified = true;
		}
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getBodyY(0.5), player.getZ(), 2, 0.5, 0.5, 0.5, 0.0);
		world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 3; i++) {
			ring(world, flame, player.getPos().add(0, 0.3 + i * 0.8, 0), radius * (1.0 - i * 0.25), 64, 0.25);
		}
		world.spawnParticles(flame, player.getX(), player.getBodyY(0.5), player.getZ(), 200, radius * 0.3, 1.2, radius * 0.3, 0.2);
		if (blue) {
			CORES.put(player.getUuid(), new Core(player.getPos(), world.getTime() + (long) ctx.param("core_ticks", 80.0),
					(float) ctx.param("core_damage", 3.0), radius * 0.5));
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.4f, blue ? 0.5f : 0.7f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_DEATH, 1.0f, 0.6f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			boolean heatblast = TransformationManager.get(player).activeAlien().filter(HEATBLAST::equals).isPresent();
			if (!heatblast) {
				if (HEAT.containsKey(player.getUuid()) || SURF.containsKey(player.getUuid()) || SHIELD.containsKey(player.getUuid())) {
					forget(player.getUuid());
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			tickSurf(world, player, now);
			tickShield(world, player, now);
			if (now % PASSIVE_INTERVAL == 0) {
				tickPassive(world, player, now);
			}
		}
		if (!WAVES.isEmpty()) {
			tickWaves(server);
		}
		if (!CORES.isEmpty()) {
			tickCores(server);
		}
	}

	/** Umgebung heizt/kuehlt, Aura ab Gluehend, Anzeige in der Aktionsleiste. */
	private static void tickPassive(ServerWorld world, ServerPlayerEntity player, long now) {
		float delta = SHIELD.containsKey(player.getUuid()) ? 2.0f : -COOLING;
		if (player.isInLava() || world.getBlockState(player.getBlockPos()).isIn(net.minecraft.registry.tag.BlockTags.FIRE)) {
			delta = 5.0f;
		} else if (world.getRegistryKey() == World.NETHER) {
			delta = Math.max(delta, 1.0f);
		}
		if (player.isTouchingWaterOrRain()) {
			delta = player.isSubmergedInWater() ? -12.0f : -4.0f;
			world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getBodyY(0.7), player.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
			if (player.isSubmergedInWater()) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, false, false));
			}
		}
		float before = heat(player);
		if (delta != 0.0f && (before > 0.0f || delta > 0.0f)) {
			addHeat(player, delta);
		}
		float heat = heat(player);
		if (heat >= GLOW) {
			// Hitze-Aura: Gegner direkt daneben fangen Feuer
			double aura = heat >= MAX ? 3.0 : 2.0;
			if (now % 20 == 0) {
				for (LivingEntity target : around(player, aura)) {
					hit(player, target, 1.5f, 3.0f);
				}
			}
			world.spawnParticles(heat >= MAX ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
					player.getX(), player.getBodyY(0.5), player.getZ(), heat >= MAX ? 4 : 2, 0.35, 0.6, 0.35, 0.01);
		}
		if (now % 10 == 0 && (heat > 0.0f || before > 0.0f)) {
			player.sendMessage(bar(heat), true);
		}
	}

	private static Text bar(float heat) {
		int filled = Math.round(heat / 10.0f);
		Formatting color = heat >= MAX ? Formatting.AQUA : heat >= GLOW ? Formatting.GOLD : Formatting.RED;
		return Text.translatable("message.kingdomomnitrix.heatblast_heat")
				.append(Text.literal(" " + "▮".repeat(filled)).formatted(color))
				.append(Text.literal("▯".repeat(10 - filled)).formatted(Formatting.DARK_GRAY))
				.append(Text.literal(" " + Math.round(heat) + "%").formatted(color));
	}

	private static void tickSurf(ServerWorld world, ServerPlayerEntity player, long now) {
		Long until = SURF.get(player.getUuid());
		if (until == null) {
			return;
		}
		if (now >= until || player.isOnGround() && now > until - 50) {
			SURF.remove(player.getUuid());
			return;
		}
		player.fallDistance = 0.0f;
		world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() - 0.2, player.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
		if (now % 5 == 0) {
			// Feuersaeule unter Heatblast: alles darunter (bis 4 Bloecke) wird versengt
			var box = player.getBoundingBox().expand(1.2, 0.0, 1.2).stretch(0.0, -4.0, 0.0);
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
				hit(player, target, 3.0f, 3.0f);
			}
		}
	}

	private static void tickShield(ServerWorld world, ServerPlayerEntity player, long now) {
		Long until = SHIELD.get(player.getUuid());
		if (until == null) {
			return;
		}
		if (now >= until) {
			SHIELD.remove(player.getUuid());
			return;
		}
		if (now % 4 == 0) {
			double angle = now * 0.5;
			world.spawnParticles(ParticleTypes.FLAME, player.getX() + Math.cos(angle) * 1.2, player.getBodyY(0.5),
					player.getZ() + Math.sin(angle) * 1.2, 2, 0.05, 0.4, 0.05, 0.0);
		}
		// Geschosse verglühen, bevor sie treffen
		for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, player.getBoundingBox().expand(3.5),
				p -> p.getOwner() != player)) {
			world.spawnParticles(ParticleTypes.FLAME, projectile.getX(), projectile.getY(), projectile.getZ(), 8, 0.1, 0.1, 0.1, 0.05);
			world.spawnParticles(ParticleTypes.SMOKE, projectile.getX(), projectile.getY(), projectile.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
			world.playSound(null, projectile.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6f, 1.4f);
			projectile.discard();
		}
		if (now % 10 == 0) {
			for (LivingEntity target : around(player, 2.5)) {
				hit(player, target, 2.0f, 4.0f);
			}
		}
	}

	private static void tickWaves(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Wave>> it = WAVES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Wave> entry = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			Wave wave = entry.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long age = world.getTime() - wave.start;
			// Front laeuft in 10 Ticks bis zum Rand
			double front = wave.radius * Math.min(1.0, (age + 1) / 10.0);
			ParticleEffect flame = wave.blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
			int points = (int) (front * 8);
			for (int i = 0; i < points; i++) {
				double angle = (i / (double) points) * MathHelper.TAU;
				Vec3d dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
				if (wave.cone > 0.0 && dir.dotProduct(wave.facing) < wave.cone) {
					continue;
				}
				Vec3d p = wave.origin.add(dir.multiply(front));
				world.spawnParticles(flame, p.x, p.y + 0.3, p.z, 1, 0.05, 0.2, 0.05, 0.01);
			}
			for (LivingEntity target : around(player, wave.radius)) {
				Vec3d flat = target.getPos().subtract(wave.origin).multiply(1, 0, 1);
				double distance = flat.length();
				if (distance > front || wave.hit.contains(target.getUuid())) {
					continue;
				}
				if (wave.cone > 0.0 && distance > 1.0E-3 && flat.normalize().dotProduct(wave.facing) < wave.cone) {
					continue;
				}
				wave.hit.add(target.getUuid());
				hit(player, target, wave.damage, wave.fire);
				if (distance > 1.0E-3) {
					target.takeKnockback(wave.knockback, -flat.x / distance, -flat.z / distance);
				}
			}
			if (age >= 10) {
				it.remove();
			}
		}
	}

	private static void tickCores(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Core>> it = CORES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Core> entry = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			Core core = entry.getValue();
			if (player == null || player.getServerWorld().getTime() >= core.until()) {
				it.remove();
				continue;
			}
			ServerWorld world = player.getServerWorld();
			world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, core.pos().x, core.pos().y + 1.0, core.pos().z, 6, 0.4, 0.4, 0.4, 0.02);
			if (world.getTime() % 10 == 0) {
				ring(world, ParticleTypes.SOUL_FIRE_FLAME, core.pos().add(0, 0.2, 0), core.radius(), 32, 0.0);
				for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, net.minecraft.util.math.Box.of(core.pos(),
						core.radius() * 2, 4, core.radius() * 2), e -> e != player && e.isAlive() && PartyRules.canHarm(player, e)
						&& e.getPos().squaredDistanceTo(core.pos()) <= core.radius() * core.radius())) {
					hit(player, target, core.damage(), 3.0f);
				}
			}
		}
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	private static List<LivingEntity> around(ServerPlayerEntity player, double radius) {
		double radiusSq = radius * radius;
		return player.getServerWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
				e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= radiusSq && PartyRules.canHarm(player, e));
	}

	private static void hit(ServerPlayerEntity player, LivingEntity target, float damage, float fireSeconds) {
		target.timeUntilRegen = 0;
		if (target.damage(player.getServerWorld().getDamageSources().playerAttack(player), damage)) {
			// jeder Treffer heizt den Kern nach — Angriff lohnt sich
			HEAT.computeIfPresent(player.getUuid(), (id, h) -> Math.min(MAX - 0.01f, h + HEAT_PER_HIT));
		}
		if (fireSeconds > 0.0f) {
			target.setOnFireFor(fireSeconds);
		}
	}

	private static void ring(ServerWorld world, ParticleEffect particle, Vec3d center, double radius, int count, double outward) {
		for (int i = 0; i < count; i++) {
			double angle = i * MathHelper.TAU / count;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			world.spawnParticles(particle, center.x + dx * radius, center.y, center.z + dz * radius, 0, dx, 0.05, dz, outward);
		}
	}

	private static void forget(UUID id) {
		HEAT.remove(id);
		SURF.remove(id);
		SHIELD.remove(id);
	}
}
