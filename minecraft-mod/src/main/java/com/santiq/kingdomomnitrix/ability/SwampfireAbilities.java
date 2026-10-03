package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Swampfire (Methanosian). Eigenes System „Methan &amp; Wildwuchs“ — ohne Aura:
 *
 * <ul>
 *   <li><b>Methanwolken</b>: Swampfire stoesst Gas aus (Wolke, Duesenspur, Sumpfinferno). Gas allein macht nur
 *       uebel; erst <b>Feuer</b> (Methanflamme, Swampfires brennender Nahkampf) zuendet es. Jede Explosion zuendet
 *       ueberlappende Wolken mit kurzer Verzoegerung — Kettenreaktionen quer ueber das Feld. Keine Blockschaeden, kein
 *       gelegtes Feuer.</li>
 *   <li><b>Wurzelfessel</b>: Ranken halten Gegner fest (keine Bewegung) und stechen.</li>
 *   <li><b>Neubildung</b> (passiv): toedlicher Schaden zerlegt Swampfire in eine Pfuetze, aus der er mit 30 % Leben
 *       nachwaechst — einmal je {@link #REFORM_COOLDOWN} Ticks.</li>
 * </ul>
 */
final class SwampfireAbilities {
	private static final Identifier SWAMPFIRE = KingdomOmnitrix.id("swampfire");
	/** Neubildung hoechstens so oft */
	static final int REFORM_COOLDOWN = 1800;
	private static final int CHAIN_DELAY = 4;
	private static final DustParticleEffect GAS = new DustParticleEffect(new Vector3f(0.55f, 0.68f, 0.25f), 1.8f);
	private static final DustParticleEffect GAS_LIGHT = new DustParticleEffect(new Vector3f(0.72f, 0.82f, 0.4f), 1.2f);
	private static final DustParticleEffect SAP = new DustParticleEffect(new Vector3f(0.3f, 0.55f, 0.2f), 1.3f);

	private static final List<Cloud> CLOUDS = new ArrayList<>();
	/** gefesselte Gegner: bis wann, von wem */
	private static final Map<UUID, Root> ROOTS = new HashMap<>();
	/** letzte Neubildung je Spieler */
	private static final Map<UUID, Long> REFORMED = new HashMap<>();
	/** Sumpfinferno: Zuendzeitpunkt und Mitte */
	private static final Map<UUID, Inferno> INFERNOS = new HashMap<>();
	/** verhindert, dass Explosionsschaden erneut etwas ausloest */
	private static boolean bonusHit;

	private static final class Cloud {
		final UUID owner;
		final RegistryKey<World> world;
		final Vec3d center;
		final double radius;
		final long until;
		final float power;
		/** geplante Zuendung (Kettenreaktion), -1 = keine */
		long igniteAt = -1;

		Cloud(UUID owner, RegistryKey<World> world, Vec3d center, double radius, long until, float power) {
			this.owner = owner;
			this.world = world;
			this.center = center;
			this.radius = radius;
			this.until = until;
			this.power = power;
		}

		boolean contains(Vec3d pos, double margin) {
			return pos.squaredDistanceTo(center) <= (radius + margin) * (radius + margin);
		}
	}

	private record Root(RegistryKey<World> world, UUID owner, long until) {
	}

	private record Inferno(RegistryKey<World> world, Vec3d center, long igniteAt) {
	}

	private SwampfireAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("methane_flame"), SwampfireAbilities::methaneFlame);
		AbilityRegistry.register(KingdomOmnitrix.id("methane_cloud"), SwampfireAbilities::methaneCloud);
		AbilityRegistry.register(KingdomOmnitrix.id("methane_jet"), SwampfireAbilities::methaneJet);
		AbilityRegistry.register(KingdomOmnitrix.id("root_snare"), SwampfireAbilities::rootSnare);
		AbilityRegistry.register(KingdomOmnitrix.id("regrowth"), SwampfireAbilities::regrowth);
		AbilityRegistry.register(KingdomOmnitrix.id("swamp_inferno"), SwampfireAbilities::swampInferno);
		ServerTickEvents.END_SERVER_TICK.register(SwampfireAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			INFERNOS.remove(id);
			CLOUDS.removeIf(c -> c.owner.equals(id));
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register(SwampfireAbilities::allowDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken <= 0.0f || bonusHit || !(source.getAttacker() instanceof ServerPlayerEntity attacker) || attacker == target
					|| !isSwampfire(attacker) || source.getSource() != attacker) {
				return;
			}
			// brennende Faeuste: Nahkampf setzt in Brand und zuendet Gas am Ziel
			target.setOnFireFor(3.0f);
			igniteAt(attacker.getServerWorld(), target.getPos().add(0, target.getHeight() * 0.5, 0), 0.5);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> ROOTS.remove(entity.getUuid()));
	}

	private static boolean isSwampfire(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(SWAMPFIRE::equals).isPresent();
	}

	// --- Gas -------------------------------------------------------------------------------------

	private static void addCloud(ServerWorld world, ServerPlayerEntity player, Vec3d center, double radius, int ticks, float power) {
		CLOUDS.add(new Cloud(player.getUuid(), world.getRegistryKey(), center, radius, world.getTime() + ticks, power));
		world.spawnParticles(GAS, center.x, center.y, center.z, (int) (radius * 12), radius * 0.5, radius * 0.3, radius * 0.5, 0.0);
	}

	/** Feuer an dieser Stelle: jede Wolke, die sie beruehrt, explodiert (sofort). */
	private static boolean igniteAt(ServerWorld world, Vec3d pos, double margin) {
		boolean any = false;
		for (Cloud cloud : CLOUDS) {
			if (cloud.world == world.getRegistryKey() && cloud.igniteAt < 0 && cloud.contains(pos, margin)) {
				cloud.igniteAt = world.getTime();
				any = true;
			}
		}
		return any;
	}

	/** Explosion einer Wolke: Schaden und Brand im Umkreis, Rueckstoss, ueberlappende Wolken zuenden nach. */
	private static void detonate(ServerWorld world, Cloud cloud) {
		ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(cloud.owner);
		double blast = cloud.radius + 1.0;
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, cloud.center.x, cloud.center.y, cloud.center.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.spawnParticles(ParticleTypes.FLAME, cloud.center.x, cloud.center.y, cloud.center.z, (int) (blast * 20), blast * 0.4, blast * 0.3,
				blast * 0.4, 0.1);
		world.playSound(null, BlockPos.ofFloored(cloud.center), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.4f, 0.9f);
		if (owner != null) {
			bonusHit = true;
			try {
				for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(cloud.center, cloud.center).expand(blast),
						e -> e != owner && e.isAlive() && PartyRules.canHarm(owner, e) && e.getPos().distanceTo(cloud.center) <= blast + e.getWidth())) {
					target.timeUntilRegen = 0;
					target.damage(world.getDamageSources().explosion(owner, owner), cloud.power);
					target.setOnFireFor(4.0f);
					Vec3d away = target.getPos().subtract(cloud.center).multiply(1, 0, 1);
					away = away.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : away.normalize().multiply(0.9);
					target.addVelocity(away.x, 0.45, away.z);
					target.velocityModified = true;
				}
			} finally {
				bonusHit = false;
			}
		}
		// Kettenreaktion: ueberlappende Wolken zuenden kurz danach
		for (Cloud other : CLOUDS) {
			if (other != cloud && other.world == cloud.world && other.igniteAt < 0
					&& other.center.distanceTo(cloud.center) <= other.radius + cloud.radius + 1.0) {
				other.igniteAt = world.getTime() + CHAIN_DELAY;
			}
		}
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Methanflamme: Feuerstrahl in Blickrichtung — trifft das erste Wesen, setzt in Brand und zuendet jede Wolke auf der Bahn. */
	private static boolean methaneFlame(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 20.0);
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		java.util.Optional<LivingEntity> hit = com.santiq.kingdomomnitrix.util.Targeting.findLivingTarget(player, eye.distanceTo(stop));
		if (hit.isPresent() && PartyRules.canHarm(player, hit.get())) {
			LivingEntity target = hit.get();
			stop = target.getPos().add(0, target.getHeight() * 0.5, 0);
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().inFire(), (float) ctx.param("damage", 6.0));
			target.setOnFireFor((float) ctx.param("fire_seconds", 4.0));
		}
		// Bahn abtasten: jede Wolke, durch die die Flamme geht, zuendet
		Vec3d step = stop.subtract(eye);
		int points = Math.max(6, (int) (step.length() * 3));
		boolean ignited = false;
		for (int i = 1; i <= points; i++) {
			Vec3d p = eye.add(step.multiply(i / (double) points)).add(0, -0.2, 0);
			if (i > 2) {
				world.spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.06, 0.06, 0.06, 0.01);
			}
			ignited |= igniteAt(world, p, 0.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_FIRECHARGE_USE, 1.0f, 0.8f);
		if (ignited) {
			BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
		}
		return true;
	}

	/** Methanwolke: Gaswolke am Zielpunkt — macht uebel und langsam; wartet auf einen Funken. */
	private static boolean methaneCloud(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 10.0);
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d at = block.getType() == HitResult.Type.MISS ? end : block.getPos().subtract(player.getRotationVec(1.0f).multiply(0.5));
		addCloud(world, player, at, ctx.param("radius", 3.0), (int) Math.round(ctx.param("seconds", 12.0) * 20.0), (float) ctx.param("damage", 8.0));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.5f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP, 1.0f, 0.6f);
		return true;
	}

	/** Methanduese: Stoss in Blickrichtung, hinterlaesst eine Gasspur aus kleinen Wolken. */
	private static boolean methaneJet(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d dir = player.getRotationVec(1.0f);
		Vec3d start = player.getPos().add(0, 0.8, 0);
		player.setVelocity(dir.multiply(ctx.param("power", 1.8)).add(0, 0.25, 0));
		player.velocityModified = true;
		player.fallDistance = 0.0f;
		ctx.grantInvulnerability(8);
		int clouds = (int) ctx.param("trail", 3);
		for (int i = 0; i < clouds; i++) {
			addCloud(world, player, start.add(dir.multiply(-0.5 - 1.6 * i)), 1.6, 200, (float) ctx.param("damage", 5.0));
		}
		world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 20, 0.2, 0.1, 0.2, 0.05);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
		return true;
	}

	/** Wurzelfessel: Ranken brechen im Umkreis um den Zielpunkt hervor und halten jeden Gegner fest. */
	private static boolean rootSnare(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 14.0);
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d at = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		double radius = ctx.param("radius", 4.0);
		int ticks = (int) Math.round(ctx.param("seconds", 3.0) * 20.0);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(at, at).expand(radius, 3.0, radius),
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && e.getPos().distanceTo(at) <= radius + 0.5)) {
			ROOTS.put(target.getUuid(), new Root(world.getRegistryKey(), player.getUuid(), world.getTime() + ticks));
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().thorns(player), (float) ctx.param("damage", 4.0));
		}
		for (int i = 0; i < 24; i++) {
			double a = i * MathHelper.TAU / 24;
			world.spawnParticles(SAP, at.x + Math.cos(a) * radius * 0.8, at.y + 0.3, at.z + Math.sin(a) * radius * 0.8, 3, 0.1, 0.4, 0.1, 0.0);
		}
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.5, at.z, 30, radius * 0.5, 0.5, radius * 0.5, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ROOTS_BREAK, 1.4f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_CAVE_VINES_PLACE, 1.4f, 0.6f);
		return true;
	}

	/**
	 * Nachwachsen: heilt sofort und regeneriert, loest schaedliche Effekte, laesst Pflanzen ringsum wachsen
	 * (Knochenmehl-Wirkung auf wachsende Pflanzen).
	 */
	private static boolean regrowth(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		player.heal(player.getMaxHealth() * (float) ctx.param("heal", 0.3));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, (int) Math.round(ctx.param("seconds", 6.0) * 20.0), 1));
		player.getStatusEffects().stream().filter(e -> !e.getEffectType().value().isBeneficial()).map(StatusEffectInstance::getEffectType).toList()
				.forEach(player::removeStatusEffect);
		player.extinguish();
		int radius = (int) ctx.param("radius", 4.0);
		for (BlockPos pos : BlockPos.iterate(player.getBlockPos().add(-radius, -1, -radius), player.getBlockPos().add(radius, 1, radius))) {
			BlockState state = world.getBlockState(pos);
			if (state.getBlock() instanceof Fertilizable plant && !state.isOf(net.minecraft.block.Blocks.GRASS_BLOCK)
					&& plant.isFertilizable(world, pos, state) && plant.canGrow(world, world.random, pos, state)) {
				plant.grow(world, world.random, pos.toImmutable(), state);
				world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
			}
		}
		world.spawnParticles(SAP, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.5, 0.8, 0.5, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_BONE_MEAL_USE, 1.4f, 0.8f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AZALEA_LEAVES_PLACE, 1.4f, 0.8f);
		return true;
	}

	/** Sumpfinferno: riesige Gaswolke um Swampfire, nach 1,5 s zuendet er sie selbst — Explosionsring mit Kettenreaktion. */
	private static boolean swampInferno(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 8.0);
		float damage = (float) ctx.param("damage", 12.0);
		Vec3d center = player.getPos().add(0, 1.0, 0);
		addCloud(world, player, center, 2.5, 60, damage);
		for (int i = 0; i < 8; i++) {
			double a = i * MathHelper.TAU / 8;
			addCloud(world, player, center.add(Math.cos(a) * radius * 0.6, 0, Math.sin(a) * radius * 0.6), 2.5, 60, damage);
		}
		INFERNOS.put(player.getUuid(), new Inferno(world.getRegistryKey(), center, world.getTime() + (long) (ctx.param("delay_seconds", 1.5) * 20)));
		ctx.grantInvulnerability(50);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP, 1.5f, 0.4f);
		return true;
	}

	// --- Neubildung ------------------------------------------------------------------------------

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayerEntity player) || !isSwampfire(player) || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		if (now - REFORMED.getOrDefault(player.getUuid(), -REFORM_COOLDOWN * 2L) < REFORM_COOLDOWN) {
			return true;
		}
		REFORMED.put(player.getUuid(), now);
		player.setHealth(player.getMaxHealth() * 0.3f);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 4, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1, false, false));
		// Pfuetze platzt: Gas um ihn herum
		addCloud(world, player, player.getPos().add(0, 0.6, 0), 3.0, 200, 8.0f);
		world.spawnParticles(SAP, player.getX(), player.getY() + 0.2, player.getZ(), 60, 0.8, 0.1, 0.8, 0.0);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.PLAYERS, 1.5f, 0.5f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AZALEA_LEAVES_PLACE, SoundCategory.PLAYERS, 1.5f, 0.6f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.swampfire_reform").formatted(Formatting.GREEN), true);
		return false;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			Inferno inferno = INFERNOS.get(player.getUuid());
			if (inferno != null && player.getServerWorld().getRegistryKey() == inferno.world() && player.getServerWorld().getTime() >= inferno.igniteAt()) {
				INFERNOS.remove(player.getUuid());
				igniteAt(player.getServerWorld(), inferno.center(), 0.5);
			}
			if (isSwampfire(player)) {
				// Feuer schadet Swampfire nicht, Regen und Wasser naehren ihn
				player.extinguish();
				if (player.getServerWorld().getTime() % 40 == 0 && player.isTouchingWaterOrRain()) {
					player.heal(1.0f);
				}
			}
		}
		if (!CLOUDS.isEmpty()) {
			tickClouds(server);
		}
		if (!ROOTS.isEmpty()) {
			tickRoots(server);
		}
	}

	private static void tickClouds(MinecraftServer server) {
		// erst sammeln, dann zuenden: Explosionen planen weitere Zuendungen in derselben Liste
		List<Cloud> boom = new ArrayList<>();
		for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
			Cloud cloud = it.next();
			ServerWorld world = server.getWorld(cloud.world);
			if (world == null) {
				it.remove();
				continue;
			}
			long now = world.getTime();
			if (cloud.igniteAt >= 0 && now >= cloud.igniteAt) {
				boom.add(cloud);
				it.remove();
				continue;
			}
			if (now >= cloud.until) {
				it.remove();
				continue;
			}
			if (now % 4 == 0) {
				world.spawnParticles(now % 8 == 0 ? GAS : GAS_LIGHT, cloud.center.x, cloud.center.y, cloud.center.z, (int) (cloud.radius * 3),
						cloud.radius * 0.45, cloud.radius * 0.3, cloud.radius * 0.45, 0.0);
			}
			if (now % 20 == 0) {
				ServerPlayerEntity owner = server.getPlayerManager().getPlayer(cloud.owner);
				for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(cloud.center, cloud.center).expand(cloud.radius),
						e -> e.isAlive() && e != owner && (owner == null || PartyRules.canHarm(owner, e)) && cloud.contains(e.getPos(), 0.5))) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), owner);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0), owner);
					// brennende Gegner zuenden das Gas selbst
					if (target.isOnFire()) {
						cloud.igniteAt = now + 1;
					}
				}
			}
		}
		for (Cloud cloud : boom) {
			ServerWorld world = server.getWorld(cloud.world);
			if (world != null) {
				detonate(world, cloud);
			}
		}
	}

	private static void tickRoots(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Root>> it = ROOTS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Root> entry = it.next();
			Root root = entry.getValue();
			ServerWorld world = server.getWorld(root.world());
			Entity entity = world == null ? null : world.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity target) || !target.isAlive() || world.getTime() >= root.until()) {
				it.remove();
				continue;
			}
			target.setVelocity(0.0, Math.min(0.0, target.getVelocity().y), 0.0);
			target.velocityModified = true;
			if (world.getTime() % 5 == 0) {
				world.spawnParticles(SAP, target.getX(), target.getY() + 0.4, target.getZ(), 4, target.getWidth() * 0.4, 0.4, target.getWidth() * 0.4, 0.0);
			}
		}
	}
}
