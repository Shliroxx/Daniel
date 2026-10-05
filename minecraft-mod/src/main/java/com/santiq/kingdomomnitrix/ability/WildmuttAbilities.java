package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.Items;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Wildmutt (Vulpimancer). Eigenes Dauer-System „Jagd“: Wildmutt ist blind und jagt nach Witterung.
 *
 * <ul>
 *   <li><b>Beute</b>: ein markiertes Ziel (Witterung, Gebruell, Ansprung oder der erste Treffer). Treffer auf die Beute
 *       bauen <b>Blutrausch</b> auf (0–{@link #MAX_FRENZY}, je Stufe +{@link #FRENZY_DAMAGE} Schaden, faellt nach
 *       {@link #FRENZY_TICKS} Ticks ohne Treffer ab) und reissen Wunden (Blutung).</li>
 *   <li><b>Kettenjagd</b>: faellt die Beute, heilt Wildmutt, wird kurz schneller und nimmt sofort die naechste Witterung
 *       auf.</li>
 *   <li>Urraserei: Blutrausch voll und gesperrt, Lebensraub, jeder Kill bruellt die Umgebung in die Flucht.</li>
 * </ul>
 * Keine Aura: der Blutrausch zeigt sich ueber Partikel an der Beute, das Heulen bei vollem Rausch und die Meldung.
 */
final class WildmuttAbilities {
	static final int MAX_FRENZY = 5;
	static final float FRENZY_DAMAGE = 0.1f;
	private static final int FRENZY_TICKS = 80;
	private static final float BLEED_DAMAGE = 1.0f;
	private static final int BLEED_TICKS = 80;
	private static final double CHAIN_RADIUS = 16.0;
	private static final Identifier WILDMUTT = KingdomOmnitrix.id("wildmutt");
	private static final DustParticleEffect SCENT = new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.12f), 1.0f);
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.6f, 0.02f, 0.02f), 1.0f);

	/** Beute je Spieler */
	private static final Map<UUID, UUID> PREY = new HashMap<>();
	/** Blutrausch: Stufe und letzter Treffer */
	private static final Map<UUID, long[]> FRENZY = new HashMap<>();
	/** Urraserei: Ende */
	private static final Map<UUID, Long> RAMPAGE = new HashMap<>();
	/** Witterungsspur (scent_track): Ende */
	private static final Map<UUID, Long> TRACKING = new HashMap<>();
	/** Blutungen je Ziel */
	private static final Map<UUID, Bleed> BLEEDS = new HashMap<>();
	/** Gegner auf der Flucht: Ende */
	private static final Map<UUID, Fear> FEARS = new HashMap<>();
	/** Sperre fuer den Zusatzschaden aus dem Blutrausch (kein Kettenauslosen) */
	private static boolean bonusHit;

	private record Bleed(UUID owner, long until, float damage) {
	}

	private record Fear(UUID from, long until) {
	}

	private WildmuttAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("quill_burst"), WildmuttAbilities::quillBurst);
		AbilityRegistry.register(KingdomOmnitrix.id("feral_roar"), WildmuttAbilities::feralRoar);
		AbilityRegistry.register(KingdomOmnitrix.id("pounce"), WildmuttAbilities::pounce);
		AbilityRegistry.register(KingdomOmnitrix.id("savage_maul"), WildmuttAbilities::savageMaul);
		AbilityRegistry.register(KingdomOmnitrix.id("scent_track"), WildmuttAbilities::scentTrack);
		AbilityRegistry.register(KingdomOmnitrix.id("primal_rampage"), WildmuttAbilities::primalRampage);
		ServerTickEvents.END_SERVER_TICK.register(WildmuttAbilities::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(WildmuttAbilities::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(WildmuttAbilities::afterDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			forgetState(handler.getPlayer().getUuid());
		});
	}

	// --- Jagd ------------------------------------------------------------------------------------

	private static boolean isWildmutt(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(WILDMUTT::equals).isPresent();
	}

	static int frenzy(ServerPlayerEntity player) {
		if (RAMPAGE.containsKey(player.getUuid())) {
			return MAX_FRENZY;
		}
		long[] f = FRENZY.get(player.getUuid());
		return f == null ? 0 : (int) f[0];
	}

	private static void setFrenzy(ServerPlayerEntity player, int level) {
		long[] f = FRENZY.computeIfAbsent(player.getUuid(), id -> new long[2]);
		int before = (int) f[0];
		f[0] = MathHelper.clamp(level, 0, MAX_FRENZY);
		f[1] = player.getServerWorld().getTime();
		if (before < MAX_FRENZY && f[0] >= MAX_FRENZY) {
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 1.0f, 0.7f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.wildmutt_frenzy").formatted(Formatting.RED), true);
		}
	}

	private static Optional<LivingEntity> prey(ServerPlayerEntity player) {
		UUID id = PREY.get(player.getUuid());
		if (id == null) {
			return Optional.empty();
		}
		Entity entity = player.getServerWorld().getEntity(id);
		return entity instanceof LivingEntity living && living.isAlive() ? Optional.of(living) : Optional.empty();
	}

	/** Neue Beute: leuchtet, Witterungs-Ring, kurzer Hinweis. */
	private static void mark(ServerPlayerEntity player, LivingEntity target) {
		if (target.getUuid().equals(PREY.get(player.getUuid()))) {
			return;
		}
		PREY.put(player.getUuid(), target.getUuid());
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 30, 0, false, false), player);
		ring(player.getServerWorld(), target.getPos().add(0, 0.2, 0), 0.9, SCENT);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.wildmutt_prey", target.getDisplayName()).formatted(Formatting.GOLD), true);
	}

	private static Optional<LivingEntity> nearestEnemy(ServerPlayerEntity player, double radius, LivingEntity except) {
		double radiusSq = radius * radius;
		return player.getServerWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
						e -> e != player && e != except && e.isAlive() && e.squaredDistanceTo(player) <= radiusSq && PartyRules.canHarm(player, e))
				.stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
	}

	/** Treffer auf die Beute: Blutrausch +1, Blutung, Zusatzschaden; Urraserei: Lebensraub. Erster Treffer markiert. */
	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (bonusHit || damageTaken <= 0.0f || !(source.getAttacker() instanceof ServerPlayerEntity player) || player == target
				|| !isWildmutt(player)) {
			return;
		}
		if (!PREY.containsKey(player.getUuid()) || prey(player).isEmpty()) {
			mark(player, target);
		}
		if (RAMPAGE.containsKey(player.getUuid())) {
			player.heal(damageTaken * 0.25f);
		}
		if (!target.getUuid().equals(PREY.get(player.getUuid()))) {
			return;
		}
		int frenzy = frenzy(player);
		setFrenzy(player, frenzy + 1);
		BLEEDS.put(target.getUuid(), new Bleed(player.getUuid(), player.getServerWorld().getTime() + BLEED_TICKS, BLEED_DAMAGE + frenzy * 0.3f));
		player.getServerWorld().spawnParticles(BLOOD, target.getX(), target.getBodyY(0.6), target.getZ(), 6 + frenzy * 2, 0.3, 0.3, 0.3, 0.0);
		if (frenzy > 0 && target.isAlive()) {
			bonusHit = true;
			try {
				target.timeUntilRegen = 0;
				target.damage(player.getServerWorld().getDamageSources().playerAttack(player), damageTaken * frenzy * FRENZY_DAMAGE);
			} finally {
				bonusHit = false;
			}
		}
	}

	/** Kettenjagd: Beute erlegt → heilen, Tempo, naechste Witterung; Urraserei: Gebruell schlaegt Gegner in die Flucht. */
	private static void afterDeath(LivingEntity target, DamageSource source) {
		if (!(source.getAttacker() instanceof ServerPlayerEntity player) || !isWildmutt(player)) {
			return;
		}
		BLEEDS.remove(target.getUuid());
		if (!target.getUuid().equals(PREY.get(player.getUuid()))) {
			return;
		}
		PREY.remove(player.getUuid());
		player.heal(4.0f);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 2, false, false));
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 0.8f, 1.2f);
		if (RAMPAGE.containsKey(player.getUuid())) {
			fearAround(player, 8.0, 80);
		}
		nearestEnemy(player, CHAIN_RADIUS, target).ifPresent(next -> {
			mark(player, next);
			trail(player.getServerWorld(), player.getPos().add(0, 0.6, 0), next.getPos().add(0, 0.6, 0));
		});
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Stachelsalve: mehr Stacheln im Blutrausch; trifft die Beute, blutet sie. */
	private static boolean quillBurst(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int frenzy = frenzy(player);
		int count = ((int) Math.max(1, ctx.param("count", 7)) + frenzy) * (com.santiq.kingdomomnitrix.alien.Evolution.isUltimate(player) ? 2 : 1);
		float speed = (float) ctx.param("speed", 2.4);
		float spread = (float) ctx.param("spread", 9.0);
		float damage = (float) ctx.param("damage", 4.0) * (1.0f + frenzy * FRENZY_DAMAGE);
		// auf die Beute gezielt, wenn sie in Reichweite ist (Witterung statt Augen)
		Optional<LivingEntity> prey = prey(player).filter(p -> p.squaredDistanceTo(player) < 24 * 24);
		for (int i = 0; i < count; i++) {
			HeroProjectileEntity quill = prey.isPresent()
					? HeroProjectileEntity.shootAt(ctx.world(), player, Items.ARROW, prey.get(), speed, spread)
					: HeroProjectileEntity.shoot(ctx.world(), player, Items.ARROW, speed, spread);
			quill.withDamage(damage);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ARROW_SHOOT, 1.0f, 0.7f);
		return true;
	}

	/** Wildes Gebruell: Gegner fliehen, der naechste wird zur Beute, Blutrausch +2. */
	private static boolean feralRoar(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double radius = ctx.param("radius", 16.0);
		int seconds = (int) ctx.param("seconds", 8.0);
		int count = fearAround(player, radius * 0.5, seconds * 10);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, seconds * 20, 0), player);
			count++;
		}
		nearestEnemy(player, radius, null).ifPresent(target -> mark(player, target));
		setFrenzy(player, frenzy(player) + 2);
		ctx.world().spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getEyeY(), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.0f, 1.3f);
		if (count == 0) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.senses_nothing").formatted(Formatting.GRAY), true);
		}
		return true;
	}

	/** Ansprung: springt die Beute an (sonst geradeaus); getroffene Ziele werden niedergedrueckt. */
	private static boolean pounce(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> prey = prey(player).filter(p -> p.squaredDistanceTo(player) < 14 * 14);
		Vec3d dir = prey.map(p -> p.getPos().subtract(player.getPos()).multiply(1, 0, 1))
				.filter(v -> v.lengthSquared() > 1.0E-4).map(Vec3d::normalize).orElse(BuiltinAbilities.horizontalLook(player));
		double forward = ctx.param("forward", 1.6) * (prey.isPresent() ? Math.min(1.6, 0.4 + prey.get().distanceTo(player) / 8.0) : 1.0);
		double distance = ctx.param("distance", 6.0);
		float damage = (float) ctx.param("damage", 7.0) * (1.0f + frenzy(player) * FRENZY_DAMAGE);
		Box path = player.getBoundingBox().stretch(dir.multiply(distance)).expand(0.8, 0.5, 0.8);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, path, e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
			target.damage(world.getDamageSources().playerAttack(player), damage);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 4), player);
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.5), target.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
		}
		BuiltinAbilities.launch(player, dir.x * forward, ctx.param("up", 0.55), dir.z * forward);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WOLF_GROWL, 1.0f, 0.8f);
		return true;
	}

	/** Wildes Zerfleischen: so viele Bisse wie Blutrausch +3, verbraucht den Rausch fuer tiefe Wunden. */
	private static boolean savageMaul(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 4.0), 0.7, e -> PartyRules.canHarm(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		mark(player, target);
		int frenzy = frenzy(player);
		int hits = (int) Math.max(1, ctx.param("hits", 3)) + frenzy;
		float damage = (float) ctx.param("damage", 4.0);
		bonusHit = true;
		try {
			for (int i = 0; i < hits && target.isAlive(); i++) {
				target.timeUntilRegen = 0;
				target.damage(ctx.world().getDamageSources().playerAttack(player), damage);
			}
		} finally {
			bonusHit = false;
		}
		if (!RAMPAGE.containsKey(player.getUuid())) {
			setFrenzy(player, 0);
		}
		BLEEDS.put(target.getUuid(), new Bleed(player.getUuid(), ctx.world().getTime() + BLEED_TICKS * 2, BLEED_DAMAGE * (1 + frenzy)));
		ctx.world().spawnParticles(BLOOD, target.getX(), target.getBodyY(0.6), target.getZ(), 30, 0.4, 0.4, 0.4, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WOLF_GROWL, 1.0f, 0.6f);
		return true;
	}

	/** Witterung: alles im weiten Umkreis leuchtet, das staerkste Ziel wird Beute, eine Duftspur fuehrt hin. */
	private static boolean scentTrack(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double radius = ctx.param("radius", 48.0);
		int ticks = (int) (ctx.param("seconds", 20.0) * 20);
		LivingEntity strongest = null;
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ticks, 0), player);
			if (strongest == null || target.getMaxHealth() > strongest.getMaxHealth()) {
				strongest = target;
			}
		}
		if (strongest == null) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.senses_nothing").formatted(Formatting.GRAY), true);
			return false;
		}
		mark(player, strongest);
		TRACKING.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 0, false, false));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WOLF_HOWL, 1.0f, 1.0f);
		return true;
	}

	/** Urraserei: Blutrausch voll und gesperrt, Lebensraub, Kills bruellen Gegner in die Flucht. */
	private static boolean primalRampage(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("self_seconds", 12.0) * 20);
		RAMPAGE.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 1, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 1, false, false));
		fearAround(player, ctx.param("radius", 8.0), (int) (ctx.param("seconds", 4.0) * 20));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 0.7f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!isWildmutt(player)) {
				if (FRENZY.containsKey(player.getUuid()) || PREY.containsKey(player.getUuid()) || RAMPAGE.containsKey(player.getUuid())) {
					forgetState(player.getUuid());
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			Long rampage = RAMPAGE.get(player.getUuid());
			if (rampage != null && now >= rampage) {
				RAMPAGE.remove(player.getUuid());
			}
			long[] f = FRENZY.get(player.getUuid());
			if (f != null && f[0] > 0 && rampage == null && now - f[1] > FRENZY_TICKS) {
				setFrenzy(player, (int) f[0] - 1);
			}
			// Ultimate: der Stachelruecken feuert alle 2 s von selbst auf die Beute
			if (now % 40 == 0 && com.santiq.kingdomomnitrix.alien.Evolution.isUltimate(player)) {
				prey(player).filter(p -> p.squaredDistanceTo(player) < 16 * 16).ifPresent(p -> {
					for (int i = 0; i < 3; i++) {
						HeroProjectileEntity.shootAt(world, player, Items.ARROW, p, 2.6f, 4.0f).withDamage(4.0f);
					}
					world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 0.6f);
				});
			}
			Long tracking = TRACKING.get(player.getUuid());
			if (tracking != null) {
				if (now >= tracking) {
					TRACKING.remove(player.getUuid());
				} else if (now % 20 == 0) {
					prey(player).ifPresent(p -> trail(world, player.getPos().add(0, 0.4, 0), p.getPos().add(0, 0.4, 0)));
				}
			}
		}
		if (!BLEEDS.isEmpty() && server.getTicks() % 20 == 0) {
			tickBleeds(server);
		}
		if (!FEARS.isEmpty() && server.getTicks() % 10 == 0) {
			tickFears(server);
		}
	}

	private static void tickBleeds(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Bleed>> it = BLEEDS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Bleed> entry = it.next();
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.getValue().owner());
			if (owner == null || !(owner.getServerWorld().getEntity(entry.getKey()) instanceof LivingEntity target) || !target.isAlive()
					|| owner.getServerWorld().getTime() > entry.getValue().until()) {
				it.remove();
				continue;
			}
			bonusHit = true;
			try {
				target.timeUntilRegen = 0;
				target.damage(owner.getServerWorld().getDamageSources().playerAttack(owner), entry.getValue().damage());
			} finally {
				bonusHit = false;
			}
			owner.getServerWorld().spawnParticles(BLOOD, target.getX(), target.getBodyY(0.5), target.getZ(), 4, 0.2, 0.3, 0.2, 0.0);
		}
	}

	/** Fliehende Gegner laufen von Wildmutt weg und greifen nicht an. */
	private static void tickFears(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Fear>> it = FEARS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Fear> entry = it.next();
			ServerPlayerEntity from = server.getPlayerManager().getPlayer(entry.getValue().from());
			if (from == null || !(from.getServerWorld().getEntity(entry.getKey()) instanceof PathAwareEntity mob) || !mob.isAlive()
					|| from.getServerWorld().getTime() > entry.getValue().until()) {
				it.remove();
				continue;
			}
			mob.setTarget(null);
			Vec3d away = mob.getPos().subtract(from.getPos()).multiply(1, 0, 1);
			Vec3d goal = mob.getPos().add(away.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : away.normalize().multiply(8.0));
			mob.getNavigation().startMovingTo(goal.x, goal.y, goal.z, 1.4);
		}
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	private static int fearAround(ServerPlayerEntity player, double radius, int ticks) {
		int count = 0;
		long until = player.getServerWorld().getTime() + ticks;
		for (MobEntity mob : player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(radius),
				m -> m.isAlive() && m.squaredDistanceTo(player) <= radius * radius && PartyRules.canHarm(player, m))) {
			if (mob instanceof PathAwareEntity) {
				FEARS.put(mob.getUuid(), new Fear(player.getUuid(), until));
			}
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 0), player);
			count++;
		}
		return count;
	}

	/** Duftspur aus orangefarbenen Punkten. */
	private static void trail(ServerWorld world, Vec3d from, Vec3d to) {
		Vec3d step = to.subtract(from);
		int points = Math.min(40, Math.max(4, (int) (step.length() * 1.5)));
		for (int i = 1; i <= points; i++) {
			Vec3d p = from.add(step.multiply(i / (double) points));
			world.spawnParticles(SCENT, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	private static void ring(ServerWorld world, Vec3d center, double radius, DustParticleEffect dust) {
		for (int i = 0; i < 16; i++) {
			double a = i * MathHelper.TAU / 16;
			world.spawnParticles(dust, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
	}

	private static void forgetState(UUID id) {
		PREY.remove(id);
		FRENZY.remove(id);
		RAMPAGE.remove(id);
		TRACKING.remove(id);
	}
}
