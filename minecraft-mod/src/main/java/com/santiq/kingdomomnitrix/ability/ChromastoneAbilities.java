package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

/**
 * Chromastone (Crystalsapien). Eigenes System „Spektralspeicher“ (0–100) — ohne Aura; die Ladung sieht man als
 * Regenbogenfunkeln am Koerper, das mit dem Speicher dichter wird:
 *
 * <ul>
 *   <li><b>Absorption</b>: Energie-Schaden (Feuer, Blitz, Explosion, Magie, Strahlen) prallt nicht ab, sondern wird
 *       vollstaendig aufgesogen und gespeichert. Sonnenlicht laedt langsam nach.</li>
 *   <li>Jede Faehigkeit gibt Speicher als Bonus wieder ab. Bei vollem Speicher <b>ueberlaedt</b> er: der naechste
 *       Treffer, der Energie bringt, loest eine Spektral-Nova aus.</li>
 *   <li><b>Kristallgitter</b>: kurzzeitig wird <i>jeder</i> Schaden aufgesogen, nicht nur Energie.</li>
 * </ul>
 */
final class ChromastoneAbilities {
	static final float MAX = 100.0f;
	private static final Identifier CHROMASTONE = KingdomOmnitrix.id("chromastone");
	/** Speicher je absorbiertem Schadenspunkt */
	private static final float ABSORB = 5.0f;
	private static final float SUN_PER_SECOND = 1.0f;
	/** Regenbogenfarben fuer Strahlen und Funkeln */
	private static final DustParticleEffect[] SPECTRUM = {
			dust(1.0f, 0.2f, 0.2f), dust(1.0f, 0.55f, 0.1f), dust(1.0f, 0.95f, 0.2f), dust(0.3f, 1.0f, 0.3f),
			dust(0.2f, 0.8f, 1.0f), dust(0.3f, 0.35f, 1.0f), dust(0.75f, 0.35f, 1.0f)};

	private static final Map<UUID, Float> STORE = new HashMap<>();
	/** Kristallgitter bis */
	private static final Map<UUID, Long> LATTICE = new HashMap<>();
	/** Vollspektrum: Start und Dauer, Schaden je Strahl */
	private static final Map<UUID, Spectrum> SPECTRA = new HashMap<>();
	/** verhindert, dass Bonus-Schaden erneut etwas ausloest */
	private static boolean bonusHit;

	private record Spectrum(long start, int ticks, float damage, double range, Set<UUID> hit) {
	}

	private static DustParticleEffect dust(float r, float g, float b) {
		return new DustParticleEffect(new Vector3f(r, g, b), 1.3f);
	}

	private ChromastoneAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("prism_beam"), ChromastoneAbilities::prismBeam);
		AbilityRegistry.register(KingdomOmnitrix.id("spectral_burst"), ChromastoneAbilities::spectralBurst);
		AbilityRegistry.register(KingdomOmnitrix.id("photon_dash"), ChromastoneAbilities::photonDash);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_lattice"), ChromastoneAbilities::crystalLattice);
		AbilityRegistry.register(KingdomOmnitrix.id("solar_charge"), ChromastoneAbilities::solarCharge);
		AbilityRegistry.register(KingdomOmnitrix.id("full_spectrum"), ChromastoneAbilities::fullSpectrum);
		ServerTickEvents.END_SERVER_TICK.register(ChromastoneAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forgetState(handler.getPlayer().getUuid()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ChromastoneAbilities::allowDamage);
	}

	private static boolean isChromastone(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(CHROMASTONE::equals).isPresent();
	}

	static float store(ServerPlayerEntity player) {
		return STORE.getOrDefault(player.getUuid(), 0.0f);
	}

	private static void charge(ServerPlayerEntity player, float amount) {
		float before = store(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		STORE.put(player.getUuid(), after);
		if (before < MAX && after >= MAX) {
			ServerWorld world = player.getServerWorld();
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.5f, 1.8f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.chromastone_full").formatted(Formatting.LIGHT_PURPLE), true);
		}
	}

	/** Bis zu {@code max} Speicher abgeben; Rueckgabe: abgegebene Menge. */
	private static float spend(ServerPlayerEntity player, float max) {
		float used = Math.min(store(player), max);
		STORE.put(player.getUuid(), store(player) - used);
		return used;
	}

	/** Energie-Schaden: Feuer, Blitz, Explosion, Magie, Schall-/Strahlenschaden. */
	private static boolean isEnergy(DamageSource source) {
		return source.isIn(DamageTypeTags.IS_FIRE) || source.isIn(DamageTypeTags.IS_LIGHTNING) || source.isIn(DamageTypeTags.IS_EXPLOSION)
				|| source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC) || source.isOf(DamageTypes.SONIC_BOOM)
				|| source.isOf(DamageTypes.DRAGON_BREATH) || source.isOf(DamageTypes.WITHER_SKULL);
	}

	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		if (!(target instanceof ServerPlayerEntity player) || !isChromastone(player) || amount <= 0.0f
				|| source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		boolean lattice = LATTICE.containsKey(player.getUuid());
		if (!isEnergy(source) && !lattice) {
			return true;
		}
		ServerWorld world = player.getServerWorld();
		boolean wasFull = store(player) >= MAX;
		charge(player, amount * ABSORB);
		player.extinguish();
		for (int i = 0; i < 7; i++) {
			world.spawnParticles(SPECTRUM[i], player.getX(), player.getBodyY(0.5), player.getZ(), 2, 0.4, 0.6, 0.4, 0.0);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.4f);
		// Ueberladung: voller Speicher und noch mehr Energie → Nova
		if (wasFull && isEnergy(source)) {
			nova(world, player, 6.0, 10.0f);
			STORE.put(player.getUuid(), MAX * 0.5f);
		}
		return false;
	}

	// --- Bausteine -------------------------------------------------------------------------------

	private static void hit(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float damage) {
		bonusHit = true;
		try {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().indirectMagic(player, player), damage);
		} finally {
			bonusHit = false;
		}
	}

	/** Regenbogenlinie: Farbbaender nebeneinander entlang der Strecke. */
	private static void rainbow(ServerWorld world, Vec3d from, Vec3d to, double density) {
		Vec3d step = to.subtract(from);
		int points = Math.max(6, (int) (step.length() * density));
		Vec3d side = step.crossProduct(new Vec3d(0, 1, 0));
		side = side.lengthSquared() < 1.0E-4 ? new Vec3d(0.06, 0, 0) : side.normalize().multiply(0.06);
		for (int i = 1; i <= points; i++) {
			Vec3d p = from.add(step.multiply(i / (double) points));
			for (int c = 0; c < SPECTRUM.length; c += 2) {
				Vec3d q = p.add(side.multiply(c - 3));
				world.spawnParticles(SPECTRUM[c], q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	private static void nova(ServerWorld world, ServerPlayerEntity player, double radius, float damage) {
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && e.distanceTo(player) <= radius)) {
			hit(world, player, target, damage);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0), player);
			Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			away = away.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : away.normalize().multiply(0.8);
			target.addVelocity(away.x, 0.3, away.z);
			target.velocityModified = true;
		}
		for (int ring = 0; ring < 7; ring++) {
			double r = radius * (ring + 1) / 7.0;
			for (int i = 0; i < 20; i++) {
				double a = i * MathHelper.TAU / 20 + ring * 0.2;
				world.spawnParticles(SPECTRUM[ring], player.getX() + Math.cos(a) * r, player.getBodyY(0.5), player.getZ() + Math.sin(a) * r, 1,
						0.0, 0.0, 0.0, 0.0);
			}
		}
		world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.4f, 1.8f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 1.4f, 0.6f);
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/**
	 * Prismenstrahl: Regenbogenstrahl auf das erste Ziel, das ihn bricht — Teilstrahlen springen auf die naechsten
	 * Gegner (2, ab halbem Speicher 3). Verbraucht bis 30 Speicher fuer Bonusschaden.
	 */
	private static boolean prismBeam(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 24.0);
		int splits = store(player) >= MAX * 0.5f ? 3 : 2;
		float damage = (float) ctx.param("damage", 6.0) + spend(player, 30.0f) * 0.2f;
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		Optional<LivingEntity> found = Targeting.findLivingTarget(player, eye.distanceTo(stop));
		Vec3d muzzle = eye.add(player.getRotationVec(1.0f)).add(0, -0.25, 0);
		if (found.isPresent() && PartyRules.canHarm(player, found.get())) {
			LivingEntity first = found.get();
			stop = first.getPos().add(0, first.getHeight() * 0.5, 0);
			hit(world, player, first, damage);
			// Brechung: Teilstrahlen auf die naechsten Gegner
			Vec3d from = stop;
			world.getEntitiesByClass(LivingEntity.class, first.getBoundingBox().expand(8.0),
							e -> e != player && e != first && e.isAlive() && PartyRules.canHarm(player, e))
					.stream().sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(first))).limit(splits)
					.forEach(next -> {
						rainbow(world, from, next.getPos().add(0, next.getHeight() * 0.5, 0), 2.0);
						hit(world, player, next, damage * 0.5f);
					});
		}
		rainbow(world, muzzle, stop, 3.0);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GUARDIAN_ATTACK, 0.6f, 1.8f);
		return true;
	}

	/** Spektralblitz: grelle Welle um Chromastone — blendet und stoesst weg; mit 20 Speicher doppelte Wucht. */
	private static boolean spectralBurst(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float used = spend(player, 20.0f);
		double factor = used >= 20.0f ? 2.0 : 1.0;
		nova(ctx.world(), player, ctx.param("radius", 5.0) * (used >= 20.0f ? 1.4 : 1.0), (float) (ctx.param("damage", 5.0) * factor));
		return true;
	}

	/** Lichtsprung: in Lichtgeschwindigkeit bis zu 10 Bloecke nach vorn — wer im Weg steht, wird getroffen. */
	private static boolean photonDash(AbilityContext ctx) {
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
			if (world.isSpaceEmpty(player, player.getBoundingBox().offset(feet.subtract(player.getPos())))) {
				Vec3d from = player.getPos();
				float damage = (float) ctx.param("damage", 5.0);
				Set<UUID> done = new HashSet<>();
				for (int i = 0; i <= (int) (d * 2); i++) {
					Vec3d p = from.lerp(feet, i / (d * 2));
					for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().offset(p.subtract(from)).expand(0.4),
							e -> e != player && e.isAlive() && !done.contains(e.getUuid()) && PartyRules.canHarm(player, e))) {
						done.add(target.getUuid());
						hit(world, player, target, damage);
					}
				}
				rainbow(world, from.add(0, 1.0, 0), feet.add(0, 1.0, 0), 2.0);
				player.requestTeleport(feet.x, feet.y, feet.z);
				player.fallDistance = 0.0f;
				ctx.grantInvulnerability(6);
				BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 2.0f);
				return true;
			}
		}
		player.sendMessage(Text.translatable("message.kingdomomnitrix.no_space_step").formatted(Formatting.GRAY), true);
		return false;
	}

	/** Kristallgitter: einige Sekunden wird jeder Schaden aufgesogen und gespeichert (nicht nur Energie). */
	private static boolean crystalLattice(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) Math.round(ctx.param("seconds", 5.0) * 20.0);
		LATTICE.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 1, false, false));
		ctx.world().spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 1.4f, 0.8f);
		return true;
	}

	/** Spektralladung: zieht Licht aus der Umgebung — hell viel, dunkel wenig; kurz schneller und eiliger. */
	private static boolean solarCharge(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int light = world.getLightLevel(player.getBlockPos());
		float amount = light >= 12 ? (float) ctx.param("bright", 40.0) : (float) ctx.param("dark", 15.0);
		charge(player, amount);
		int ticks = (int) Math.round(ctx.param("seconds", 8.0) * 20.0);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 0, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, ticks, 1, false, false));
		for (int i = 0; i < 7; i++) {
			world.spawnParticles(SPECTRUM[i], player.getX(), player.getBodyY(0.5), player.getZ(), 8, 0.8, 1.0, 0.8, 0.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
		return true;
	}

	/** Vollspektrum: gibt den ganzen Speicher frei — sieben Farbstrahlen kreisen 3 s um Chromastone. */
	private static boolean fullSpectrum(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float used = spend(player, MAX);
		float damage = (float) (ctx.param("damage", 6.0) * (1.0 + used / 50.0));
		SPECTRA.put(player.getUuid(), new Spectrum(ctx.world().getTime(), (int) Math.round(ctx.param("seconds", 3.0) * 20.0), damage,
				ctx.param("range", 12.0), new HashSet<>()));
		ctx.grantInvulnerability(20);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.2f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			if (!isChromastone(player)) {
				if (STORE.containsKey(id) || LATTICE.containsKey(id) || SPECTRA.containsKey(id)) {
					forgetState(id);
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			// Sonnenlicht laedt nach
			if (now % 20 == 0 && world.isDay() && world.getLightLevel(LightType.SKY, player.getBlockPos()) >= 14 && !world.isRaining()) {
				charge(player, SUN_PER_SECOND);
			}
			// Funkeln je nach Ladung (die Ladung sieht man am Koerper, ohne Aura)
			float store = store(player);
			if (store > 0.0f && world.random.nextFloat() < store / 120.0f) {
				world.spawnParticles(SPECTRUM[world.random.nextInt(SPECTRUM.length)], player.getX(), player.getBodyY(world.random.nextDouble()),
						player.getZ(), 1, 0.35, 0.1, 0.35, 0.0);
			}
			Long lattice = LATTICE.get(id);
			if (lattice != null && now >= lattice) {
				LATTICE.remove(id);
			}
			tickSpectrum(world, player, now);
		}
	}

	private static void tickSpectrum(ServerWorld world, ServerPlayerEntity player, long now) {
		Spectrum spectrum = SPECTRA.get(player.getUuid());
		if (spectrum == null) {
			return;
		}
		long age = now - spectrum.start();
		if (age >= spectrum.ticks()) {
			SPECTRA.remove(player.getUuid());
			return;
		}
		// sieben Strahlen, eine Umdrehung pro Sekunde; jeder Gegner wird je halbe Sekunde hoechstens einmal getroffen
		if (age % 10 == 0) {
			spectrum.hit().clear();
		}
		Vec3d center = player.getPos().add(0, player.getHeight() * 0.55, 0);
		double turn = age / 20.0 * MathHelper.TAU;
		List<LivingEntity> near = new ArrayList<>(world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(spectrum.range()),
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e)));
		for (int c = 0; c < 7; c++) {
			double a = turn + c * MathHelper.TAU / 7;
			Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
			if (age % 2 == 0) {
				for (int i = 2; i <= (int) spectrum.range(); i += 1) {
					Vec3d p = center.add(dir.multiply(i));
					world.spawnParticles(SPECTRUM[c], p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
			}
			for (LivingEntity target : near) {
				if (spectrum.hit().contains(target.getUuid())) {
					continue;
				}
				Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(center);
				double along = to.dotProduct(dir);
				if (along > 0.0 && along <= spectrum.range() && to.subtract(dir.multiply(along)).length() <= 0.8 + target.getWidth() * 0.5) {
					spectrum.hit().add(target.getUuid());
					hit(world, player, target, spectrum.damage());
				}
			}
			if (c == 0 && age % 5 == 0) {
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f,
						1.0f + (age % 20) / 20.0f);
			}
		}
	}

	private static void forgetState(UUID id) {
		STORE.remove(id);
		LATTICE.remove(id);
		SPECTRA.remove(id);
	}
}
