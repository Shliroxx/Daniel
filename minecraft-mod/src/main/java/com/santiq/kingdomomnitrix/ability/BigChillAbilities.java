package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Big Chill (Necrofriggian). Eigenes System „Unterkuehlung“ — ohne Aura, die Kaelte sieht man an den Gegnern:
 *
 * <ul>
 *   <li>Jeder Gegner hat eine Unterkuehlung 0–100 (Frostpartikel, Vanilla-Frostrand am Bildschirm bei Spielern). Sie
 *       bremst stufenlos und taut nach 3 s ohne Nachschub langsam auf.</li>
 *   <li>Bei 100 <b>erstarrt</b> das Ziel {@link #FROZEN_TICKS} Ticks lang: keine Bewegung, keine KI, zitternder
 *       Frostkoerper. Bosse erstarren nur kurz.</li>
 *   <li><b>Zersplittern</b>: ein harter Treffer ({@link #SHATTER_HIT}+) auf ein erstarrtes Ziel sprengt das Eis — viel
 *       Bonusschaden und Eissplitter, die Nachbarn unterkuehlen. Die Kettenreaktion ist Big Chills eigentliche Waffe.</li>
 *   <li>Eisgefaengnis: echte Eisbloecke um das Ziel (nur in Luft), die nach Ablauf ohne Drop verschwinden — genau die
 *       gesetzten, auch beim Herunterfahren des Servers.</li>
 *   <li>Umhang: Big Chill ist unsichtbar und unantastbar fuer Geschosse; seine Naehe kuehlt.</li>
 * </ul>
 */
final class BigChillAbilities {
	static final float MAX_CHILL = 100.0f;
	static final int FROZEN_TICKS = 60;
	static final float SHATTER_HIT = 6.0f;
	private static final Identifier BIG_CHILL = KingdomOmnitrix.id("big_chill");
	private static final float CHILL_MELEE = 15.0f;
	private static final float CHILL_SHARD = 20.0f;
	private static final float AFTER_THAW = 40.0f;
	private static final int THAW_DELAY = 60;
	private static final float THAW_PER_SECOND = 8.0f;
	private static final int BOSS_FROZEN_TICKS = 15;
	/** Eisbloecke ohne Nachbar-Updates und ohne Drops entfernen (kein Eis aus Faehigkeiten) */
	private static final int QUIET = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
	private static final DustParticleEffect FROST = new DustParticleEffect(new Vector3f(0.7f, 0.9f, 1.0f), 1.1f);
	private static final DustParticleEffect DEEP = new DustParticleEffect(new Vector3f(0.29f, 0.48f, 0.82f), 1.6f);

	/** Unterkuehlung je Ziel */
	private static final Map<UUID, Chill> CHILL = new HashMap<>();
	/** erstarrte Ziele */
	private static final Map<UUID, Frozen> FROZEN = new HashMap<>();
	/** gesetzte Eisbloecke */
	private static final List<IceBlock> ICE = new ArrayList<>();
	/** Umhang bis */
	private static final Map<UUID, Long> CLOAK = new HashMap<>();
	/** Phasenflug: Ende und schon durchflogene Gegner */
	private static final Map<UUID, PhaseDash> DASHES = new HashMap<>();
	/** Absoluter Nullpunkt: Zeitpunkt der Massen-Zersplitterung und betroffene Ziele */
	private static final Map<UUID, ZeroField> ZERO = new HashMap<>();
	/** verhindert, dass Bonus-Schaden erneut Bonus-Schaden ausloest */
	private static boolean bonusHit;

	private static final class Chill {
		float value;
		long lastGain;
		UUID source;
	}

	/** {@code aiWasDisabled}: Ausgangszustand der KI, damit genau der wiederhergestellt wird. */
	private record Frozen(RegistryKey<World> world, long until, boolean aiWasDisabled, UUID source) {
	}

	private record IceBlock(RegistryKey<World> world, BlockPos pos, long until) {
	}

	private record PhaseDash(long until, Vec3d direction, Set<UUID> touched) {
	}

	private record ZeroField(RegistryKey<World> world, long shatterAt, Set<UUID> targets) {
	}

	private BigChillAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("ice_breath"), BigChillAbilities::iceBreath);
		AbilityRegistry.register(KingdomOmnitrix.id("ice_shards"), BigChillAbilities::iceShards);
		AbilityRegistry.register(KingdomOmnitrix.id("phase_flight"), BigChillAbilities::phaseFlight);
		AbilityRegistry.register(KingdomOmnitrix.id("ice_prison"), BigChillAbilities::icePrison);
		AbilityRegistry.register(KingdomOmnitrix.id("cryo_cloak"), BigChillAbilities::cryoCloak);
		AbilityRegistry.register(KingdomOmnitrix.id("absolute_zero"), BigChillAbilities::absoluteZero);
		ServerTickEvents.END_SERVER_TICK.register(BigChillAbilities::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(BigChillAbilities::shutdown);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forgetState(handler.getPlayer().getUuid()));
		// Eis aus Faehigkeiten: abbauen zerbricht es ohne Drop
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerWorld server) || ICE.isEmpty()) {
				return true;
			}
			for (Iterator<IceBlock> it = ICE.iterator(); it.hasNext(); ) {
				IceBlock ice = it.next();
				if (ice.pos().equals(pos) && ice.world() == server.getRegistryKey()) {
					melt(server, ice);
					it.remove();
					return false;
				}
			}
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BigChillAbilities::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BigChillAbilities::afterDamage);
		// Chunk entlaedt waehrend der Starre: KI sofort zurueck, sonst landet „NoAI“ im Spielstand
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			Frozen frozen = FROZEN.remove(entity.getUuid());
			if (frozen != null && entity instanceof MobEntity mob && !mob.getType().isIn(ConventionalEntityTypeTags.BOSSES)) {
				mob.setAiDisabled(frozen.aiWasDisabled());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			CHILL.remove(entity.getUuid());
			FROZEN.remove(entity.getUuid());
		});
	}

	private static boolean isBigChill(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(BIG_CHILL::equals).isPresent();
	}

	// --- Unterkuehlung ---------------------------------------------------------------------------

	static float chill(LivingEntity target) {
		Chill c = CHILL.get(target.getUuid());
		return c == null ? 0.0f : c.value;
	}

	static boolean frozen(LivingEntity target) {
		return FROZEN.containsKey(target.getUuid());
	}

	/** Unterkuehlen; bei 100 erstarrt das Ziel. */
	private static void cool(ServerPlayerEntity player, LivingEntity target, float amount) {
		if (amount <= 0.0f || !target.isAlive() || frozen(target) || target == player) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		Chill c = CHILL.computeIfAbsent(target.getUuid(), id -> new Chill());
		c.value = Math.min(MAX_CHILL, c.value + amount);
		c.lastGain = world.getTime();
		c.source = player.getUuid();
		applySlow(target, c.value);
		world.spawnParticles(FROST, target.getX(), target.getBodyY(0.6), target.getZ(), 3 + (int) (c.value / 15.0f), 0.3, 0.4, 0.3, 0.0);
		if (c.value >= MAX_CHILL) {
			freeze(world, player, target);
		}
	}

	private static void applySlow(LivingEntity target, float value) {
		int level = (int) (value / 25.0f);
		if (level > 0) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, level - 1, false, false));
		}
		// Vanilla-Frost: Frostrand bei Spielern, zitternde Mobs (nur Optik — kein Erfrierungsschaden darunter)
		target.setFrozenTicks(Math.max(target.getFrozenTicks(), (int) (value / MAX_CHILL * (target.getMinFreezeDamageTicks() - 1))));
	}

	private static void freeze(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
		boolean boss = target.getType().isIn(ConventionalEntityTypeTags.BOSSES);
		int ticks = boss ? BOSS_FROZEN_TICKS : FROZEN_TICKS;
		boolean aiWasDisabled = false;
		if (target instanceof MobEntity mob && !boss) {
			aiWasDisabled = mob.isAiDisabled();
			mob.setAiDisabled(true);
			mob.setTarget(null);
		}
		FROZEN.put(target.getUuid(), new Frozen(world.getRegistryKey(), world.getTime() + ticks, aiWasDisabled, player.getUuid()));
		target.setVelocity(0.0, Math.min(0.0, target.getVelocity().y), 0.0);
		target.velocityModified = true;
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 9, false, false));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, ticks, 4, false, false));
		target.setFrozenTicks(target.getMinFreezeDamageTicks() - 1);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()), target.getX(), target.getBodyY(0.5),
				target.getZ(), 30, target.getWidth() * 0.4, target.getHeight() * 0.4, target.getWidth() * 0.4, 0.1);
		world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0f, 0.8f);
	}

	/** Auftauen: KI genau wie vorher, Unterkuehlung bleibt teilweise. */
	private static void thaw(ServerWorld world, LivingEntity target, Frozen frozen, boolean shattered) {
		if (target instanceof MobEntity mob && !target.getType().isIn(ConventionalEntityTypeTags.BOSSES)) {
			mob.setAiDisabled(frozen.aiWasDisabled());
		}
		target.removeStatusEffect(StatusEffects.MINING_FATIGUE);
		Chill c = CHILL.computeIfAbsent(target.getUuid(), id -> new Chill());
		c.value = shattered ? 0.0f : AFTER_THAW;
		c.lastGain = world.getTime();
		target.setFrozenTicks(0);
	}

	/** Zersplittern: Bonusschaden, Splitter unterkuehlen die Nachbarn (Kettenreaktion). */
	private static void shatter(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
		Frozen frozen = FROZEN.remove(target.getUuid());
		if (frozen == null) {
			return;
		}
		thaw(world, target, frozen, true);
		bonusHit = true;
		try {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().freeze(), 8.0f + Math.min(12.0f, target.getMaxHealth() * 0.15f));
		} finally {
			bonusHit = false;
		}
		for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(3.5),
				e -> e != player && e != target && e.isAlive() && PartyRules.canHarm(player, e))) {
			cool(player, near, 30.0f);
		}
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()), target.getX(),
				target.getBodyY(0.5), target.getZ(), 60, target.getWidth() * 0.5, target.getHeight() * 0.4, target.getWidth() * 0.5, 0.3);
		world.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getBodyY(0.5), target.getZ(), 30, 0.6, 0.6, 0.6, 0.2);
		world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.4f, 0.7f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.big_chill_shatter").formatted(Formatting.AQUA), true);
	}

	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayerEntity player && isBigChill(player)) {
			// Umhang: Geschosse gehen durch Big Chill hindurch
			if (CLOAK.containsKey(player.getUuid()) && source.getSource() instanceof ProjectileEntity) {
				return false;
			}
			// Kaelte schadet Big Chill nicht
			if (source.isIn(DamageTypeTags.IS_FREEZING)) {
				return false;
			}
		}
		return true;
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		if (taken <= 0.0f || bonusHit) {
			return;
		}
		ServerWorld world = target.getWorld() instanceof ServerWorld sw ? sw : null;
		if (world == null) {
			return;
		}
		// erstarrte Ziele zersplittern unter hartem Treffer — egal von wem (Gruppenspiel), Big Chill bekommt die Wirkung
		Frozen frozen = FROZEN.get(target.getUuid());
		// Big Chills Eissplitter zersplittern immer, sonst braucht es einen harten Treffer
		boolean shard = source.getSource() instanceof HeroProjectileEntity && source.getAttacker() instanceof ServerPlayerEntity shooter
				&& isBigChill(shooter);
		if (frozen != null && (taken >= SHATTER_HIT || shard) && !source.isIn(DamageTypeTags.IS_FREEZING)) {
			ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(frozen.source());
			if (owner != null) {
				shatter(world, owner, target);
				return;
			}
		}
		if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != target && isBigChill(attacker)) {
			if (source.getSource() instanceof HeroProjectileEntity) {
				cool(attacker, target, CHILL_SHARD);
			} else if (source.getSource() == attacker) {
				cool(attacker, target, CHILL_MELEE);
			}
		}
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Eisatem: Kaeltekegel — unterkuehlt stark, loescht Feuer, laesst Wasser zu (schmelzendem) Frosteis gefrieren. */
	private static boolean iceBreath(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 8.0);
		float chill = (float) ctx.param("chill", 35.0);
		float damage = (float) ctx.param("damage", 3.0);
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, range)) {
			Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(eye);
			if (to.length() > range || to.normalize().dotProduct(look) < 0.75) {
				continue;
			}
			target.extinguish();
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().freeze(), damage);
			cool(player, target, chill);
		}
		for (int i = 1; i <= 12; i++) {
			double d = range * i / 12.0;
			Vec3d p = eye.add(look.multiply(d));
			world.spawnParticles(ParticleTypes.SNOWFLAKE, p.x, p.y - 0.2, p.z, 3, d * 0.08, d * 0.08, d * 0.08, 0.02);
			world.spawnParticles(FROST, p.x, p.y - 0.2, p.z, 2, d * 0.1, d * 0.1, d * 0.1, 0.0);
			freezeWater(world, BlockPos.ofFloored(p.x, p.y - 1.0, p.z), 1);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_SHOOT, 0.9f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_POWDER_SNOW_STEP, 1.0f, 0.6f);
		return true;
	}

	/** Eissplitter: Faecher aus Eisgeschossen; jeder Treffer unterkuehlt, auf Erstarrten zersplittern sie. */
	private static boolean iceShards(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int count = (int) Math.max(1, ctx.param("count", 5));
		float damage = (float) ctx.param("damage", 4.0);
		float speed = (float) ctx.param("speed", 2.2);
		for (int i = 0; i < count; i++) {
			HeroProjectileEntity.shoot(ctx.world(), player, Items.ICE, speed, (float) ctx.param("spread", 5.0)).withDamage(damage);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_GLASS_HIT, 1.0f, 1.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SNOWBALL_THROW, 1.0f, 0.6f);
		return true;
	}

	/** Phasenflug: Big Chill rast entmaterialisiert nach vorn; jeder Durchflogene wird stark unterkuehlt. */
	private static boolean phaseFlight(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) ctx.param("ticks", 12);
		Vec3d dir = player.getRotationVec(1.0f).normalize();
		double power = ctx.param("power", 1.6);
		player.setVelocity(dir.multiply(power));
		player.velocityModified = true;
		player.fallDistance = 0.0f;
		ctx.grantInvulnerability(ticks + 4);
		DASHES.put(player.getUuid(), new PhaseDash(ctx.world().getTime() + ticks, dir, new HashSet<>()));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_VEX_CHARGE, 0.8f, 0.5f);
		return true;
	}

	/** Eisgefaengnis: echte Eisbloecke um das Ziel (nur in Luft), Ziel erstarrt sofort; Eis schmilzt nach Ablauf. */
	private static boolean icePrison(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		java.util.Optional<LivingEntity> found = com.santiq.kingdomomnitrix.util.Targeting.findMeleeTarget(player, ctx.param("range", 16.0), 0.85,
				e -> PartyRules.canHarm(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		int ticks = (int) Math.round(ctx.param("seconds", 5.0) * 20.0);
		// Huelle aus Eis: Ring um das Ziel und Deckel, je nach Groesse
		BlockPos feet = target.getBlockPos();
		int r = Math.max(1, (int) Math.ceil(target.getWidth() * 0.5 + 0.2));
		int h = Math.max(2, (int) Math.ceil(target.getHeight()));
		for (int dy = 0; dy <= h; dy++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					boolean shell = Math.abs(dx) == r || Math.abs(dz) == r || dy == h;
					if (shell) {
						placeIce(world, feet.add(dx, dy, dz), ticks);
					}
				}
			}
		}
		Chill c = CHILL.computeIfAbsent(target.getUuid(), id -> new Chill());
		c.value = MAX_CHILL - 1.0f;
		cool(player, target, 1.0f);
		Frozen frozen = FROZEN.get(target.getUuid());
		if (frozen != null) {
			FROZEN.put(target.getUuid(), new Frozen(frozen.world(), Math.max(frozen.until(), world.getTime() + ticks), frozen.aiWasDisabled(), frozen.source()));
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_GLASS_PLACE, 1.4f, 0.5f);
		return true;
	}

	/** Umhang: unsichtbar, Geschosse gehen durch, die Naehe unterkuehlt; danach Frostausbruch. */
	private static boolean cryoCloak(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) Math.round(ctx.param("seconds", 8.0) * 20.0);
		CLOAK.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, ticks, 0, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 0, false, false));
		ctx.world().spawnParticles(DEEP, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.5, 0.8, 0.5, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 0.5f);
		return true;
	}

	/**
	 * Absoluter Nullpunkt: alles im Umkreis erstarrt sofort, Wasser friert zu Frosteis, Feuer erlischt. Nach 2 s
	 * zersplittern alle, die dann noch erstarrt sind, gleichzeitig.
	 */
	private static boolean absoluteZero(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 12.0);
		Set<UUID> targets = new HashSet<>();
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			target.extinguish();
			Chill c = CHILL.computeIfAbsent(target.getUuid(), id -> new Chill());
			c.value = MAX_CHILL - 1.0f;
			cool(player, target, 1.0f);
			targets.add(target.getUuid());
		}
		freezeWater(world, player.getBlockPos().down(), (int) Math.min(radius, 10));
		ZERO.put(player.getUuid(), new ZeroField(world.getRegistryKey(), world.getTime() + (long) (ctx.param("shatter_seconds", 2.0) * 20), targets));
		for (int ring = 1; ring <= 4; ring++) {
			double rr = radius * ring / 4.0;
			for (int i = 0; i < 36; i++) {
				double a = i * MathHelper.TAU / 36;
				world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX() + Math.cos(a) * rr, player.getY() + 0.3, player.getZ() + Math.sin(a) * rr,
						1, 0.1, 0.2, 0.1, 0.01);
			}
		}
		world.spawnParticles(DEEP, player.getX(), player.getBodyY(0.5), player.getZ(), 80, radius * 0.3, 1.0, radius * 0.3, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.5f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_GLASS_BREAK, 1.2f, 0.4f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			if (!isBigChill(player)) {
				if (CLOAK.containsKey(id) || DASHES.containsKey(id) || ZERO.containsKey(id)) {
					forgetState(id);
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			player.setFrozenTicks(0);
			tickCloak(world, player, now);
			tickDash(world, player, now);
			tickZero(world, player, now);
		}
		if (!FROZEN.isEmpty()) {
			tickFrozen(server);
		}
		if (server.getTicks() % 20 == 0 && !CHILL.isEmpty()) {
			tickThaw(server);
		}
		if (!ICE.isEmpty()) {
			tickIce(server);
		}
	}

	private static void tickCloak(ServerWorld world, ServerPlayerEntity player, long now) {
		Long until = CLOAK.get(player.getUuid());
		if (until == null) {
			return;
		}
		if (now >= until) {
			CLOAK.remove(player.getUuid());
			// Frostausbruch beim Enthuellen
			for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(4.0),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
				cool(player, near, 35.0f);
			}
			world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getBodyY(0.5), player.getZ(), 50, 1.5, 0.8, 1.5, 0.05);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0f, 0.6f);
			return;
		}
		if (now % 20 == 0) {
			for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(4.0),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
				cool(player, near, 8.0f);
			}
		}
		if (now % 3 == 0) {
			world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.4, 0.6, 0.4, 0.0);
		}
	}

	private static void tickDash(ServerWorld world, ServerPlayerEntity player, long now) {
		PhaseDash dash = DASHES.get(player.getUuid());
		if (dash == null) {
			return;
		}
		if (now >= dash.until()) {
			DASHES.remove(player.getUuid());
			return;
		}
		world.spawnParticles(DEEP, player.getX(), player.getBodyY(0.5), player.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(1.0),
				e -> e != player && e.isAlive() && !dash.touched().contains(e.getUuid()) && PartyRules.canHarm(player, e))) {
			dash.touched().add(target.getUuid());
			cool(player, target, 45.0f);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 0.8f, 1.2f);
		}
	}

	private static void tickZero(ServerWorld world, ServerPlayerEntity player, long now) {
		ZeroField field = ZERO.get(player.getUuid());
		if (field == null || now < field.shatterAt()) {
			return;
		}
		ZERO.remove(player.getUuid());
		for (UUID id : field.targets()) {
			if (world.getEntity(id) instanceof LivingEntity target && target.isAlive() && frozen(target)) {
				shatter(world, player, target);
			}
		}
	}

	private static void tickFrozen(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Frozen>> it = FROZEN.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Frozen> entry = it.next();
			Frozen frozen = entry.getValue();
			ServerWorld world = server.getWorld(frozen.world());
			Entity entity = world == null ? null : world.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
				it.remove();
				continue;
			}
			if (world.getTime() >= frozen.until()) {
				it.remove();
				thaw(world, target, frozen, false);
				world.spawnParticles(ParticleTypes.DRIPPING_WATER, target.getX(), target.getBodyY(0.6), target.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
				continue;
			}
			// erstarrt: keine Bewegung (Fallen bleibt erlaubt), Frostkoerper
			target.setVelocity(0.0, Math.min(0.0, target.getVelocity().y), 0.0);
			target.velocityModified = true;
			target.setFrozenTicks(target.getMinFreezeDamageTicks() - 1);
			if (world.getTime() % 4 == 0) {
				// Eiskruste: Frost und Eiskristalle ueber den ganzen Koerper
				world.spawnParticles(FROST, target.getX(), target.getBodyY(0.5), target.getZ(), 6, target.getWidth() * 0.45, target.getHeight() * 0.45,
						target.getWidth() * 0.45, 0.0);
				world.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getBodyY(0.5), target.getZ(), 2, target.getWidth() * 0.4,
						target.getHeight() * 0.4, target.getWidth() * 0.4, 0.0);
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()), target.getX(), target.getBodyY(0.5),
						target.getZ(), 2, target.getWidth() * 0.4, target.getHeight() * 0.4, target.getWidth() * 0.4, 0.0);
			}
		}
	}

	private static void tickThaw(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Chill>> it = CHILL.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Chill> entry = it.next();
			if (FROZEN.containsKey(entry.getKey())) {
				continue;
			}
			Chill c = entry.getValue();
			long now = server.getOverworld().getTime();
			if (now - c.lastGain < THAW_DELAY) {
				continue;
			}
			c.value -= THAW_PER_SECOND;
			if (c.value <= 0.0f) {
				it.remove();
			}
		}
	}

	private static void tickIce(MinecraftServer server) {
		for (Iterator<IceBlock> it = ICE.iterator(); it.hasNext(); ) {
			IceBlock ice = it.next();
			ServerWorld world = server.getWorld(ice.world());
			if (world == null) {
				it.remove();
				continue;
			}
			if (world.getTime() >= ice.until() && melt(world, ice)) {
				it.remove();
			}
		}
	}

	// --- Eis -------------------------------------------------------------------------------------

	private static void placeIce(ServerWorld world, BlockPos pos, int ticks) {
		if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || !world.getBlockState(pos).isAir()
				|| !world.getEntitiesByClass(ServerPlayerEntity.class, new Box(pos), e -> true).isEmpty()) {
			return;
		}
		world.setBlockState(pos, Blocks.ICE.getDefaultState());
		ICE.add(new IceBlock(world.getRegistryKey(), pos, world.getTime() + ticks));
	}

	/** @return true, wenn erledigt (geschmolzen oder inzwischen anders bebaut); false = Chunk nicht geladen */
	private static boolean melt(ServerWorld world, IceBlock ice) {
		if (!world.isChunkLoaded(ice.pos().getX() >> 4, ice.pos().getZ() >> 4)) {
			return false;
		}
		BlockState state = world.getBlockState(ice.pos());
		if (state.isOf(Blocks.ICE)) {
			// Eis wird zu Luft, nicht zu Wasser (kein Fluten durch Faehigkeiten)
			world.setBlockState(ice.pos(), Blocks.AIR.getDefaultState(), QUIET);
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()), ice.pos().getX() + 0.5,
					ice.pos().getY() + 0.5, ice.pos().getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
		}
		return true;
	}

	/** Wasseroberflaechen im Radius zu Frosteis (vanilla: schmilzt von selbst wieder). */
	private static void freezeWater(ServerWorld world, BlockPos center, int radius) {
		BlockState frosted = Blocks.FROSTED_ICE.getDefaultState();
		for (BlockPos pos : BlockPos.iterate(center.add(-radius, -1, -radius), center.add(radius, 1, radius))) {
			if (pos.getSquaredDistance(center) > radius * radius + 1) {
				continue;
			}
			BlockState state = world.getBlockState(pos);
			if (state.isOf(Blocks.WATER) && state.getFluidState().isOf(Fluids.WATER) && state.getFluidState().isStill()
					&& world.getBlockState(pos.up()).isAir()) {
				world.setBlockState(pos, frosted);
				world.scheduleBlockTick(pos.toImmutable(), Blocks.FROSTED_ICE, MathHelper.nextInt(world.getRandom(), 60, 120));
			}
		}
	}

	/** Server faehrt herunter: Eis weg, erstarrte Mobs bekommen ihre KI zurueck — nichts bleibt im Spielstand haengen. */
	private static void shutdown(MinecraftServer server) {
		for (IceBlock ice : ICE) {
			ServerWorld world = server.getWorld(ice.world());
			if (world != null && world.getBlockState(ice.pos()).isOf(Blocks.ICE)) {
				world.setBlockState(ice.pos(), Blocks.AIR.getDefaultState(), QUIET);
			}
		}
		ICE.clear();
		for (Map.Entry<UUID, Frozen> entry : FROZEN.entrySet()) {
			ServerWorld world = server.getWorld(entry.getValue().world());
			if (world != null && world.getEntity(entry.getKey()) instanceof MobEntity mob) {
				mob.setAiDisabled(entry.getValue().aiWasDisabled());
			}
		}
		FROZEN.clear();
		CHILL.clear();
	}

	private static void forgetState(UUID id) {
		CLOAK.remove(id);
		DASHES.remove(id);
		ZERO.remove(id);
	}
}
