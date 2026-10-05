package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModSounds;
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
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Diamondhead (Petrosapien). Eigenes Dauer-System „Resonanz“ (0–100) und echte Kristall-Konstrukte:
 *
 * <ul>
 *   <li>Resonanz steigt, wenn Geschosse an Diamondhead abprallen (er ist immun), mit ausgeteiltem und eingestecktem
 *       Schaden; nach 5 s ohne Kampf klingt sie ab.</li>
 *   <li>Ab {@link #FACETED} <b>facettiert</b>: jedes abprallende Geschoss wird als Kristallsplitter auf den Schuetzen
 *       zurueckgeworfen, Splitter machen mehr Schaden.</li>
 *   <li>Bei {@link #MAX} <b>Prisma</b>: die naechste Faehigkeit verbraucht die Resonanz fuer ihre groesste Form.</li>
 *   <li>Kristall-Konstrukte: Spitzen wachsen fuer einige Sekunden als echte Bloecke (Amethyst) aus dem Boden — nur in
 *       Luft, nur auf festem Grund — und zerspringen danach; es wird genau das entfernt, was gesetzt wurde. Beim
 *       Herunterfahren des Servers verschwinden alle sofort.</li>
 * </ul>
 * Anzeige ueber {@link AlienMeterPayload#RESONANCE} (gruene Aura).
 */
final class DiamondheadAbilities {
	static final float MAX = 100.0f;
	static final float FACETED = 50.0f;
	private static final Identifier DIAMONDHEAD = KingdomOmnitrix.id("diamondhead");
	private static final float CHARGE_PER_PROJECTILE = 12.0f;
	private static final float CHARGE_DEALT = 0.6f;
	private static final float CHARGE_TAKEN = 2.0f;
	private static final int CALM_TICKS = 100;
	private static final float DECAY = 4.0f;
	/** Lebensdauer der Kristallspitzen (Ticks) */
	private static final int SPIKE_TICKS = 100;
	/**
	 * Entfernen ohne Nachbar-Updates und ohne Drops: sonst faellt ein Amethyst-Cluster, dessen Sockel zuerst
	 * verschwindet, als Item ab (kostenloser Amethyst).
	 */
	private static final int QUIET = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE
			| net.minecraft.block.Block.SKIP_DROPS;
	private static final DustParticleEffect GREEN = new DustParticleEffect(new Vector3f(0.45f, 1.0f, 0.7f), 1.3f);

	private static final Map<UUID, Float> CHARGE = new HashMap<>();
	private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
	private static final Map<UUID, Long> ARMOR = new HashMap<>();
	private static final Map<UUID, Long> LEAPS = new HashMap<>();
	private static final Map<UUID, Storm> STORMS = new HashMap<>();
	private static final List<Eruption> ERUPTIONS = new ArrayList<>();
	private static final List<Spike> SPIKES = new ArrayList<>();

	/** Gesetzter Kristallblock: wird nach Ablauf nur entfernt, wenn dort noch genau dieser Block steht. */
	private record Spike(RegistryKey<World> world, BlockPos pos, BlockState state, long until) {
	}

	/** Spitzen-Welle: waechst ringfoermig nach aussen, setzt Spitzen an der Front und trifft jeden einmal. */
	private static final class Eruption {
		final UUID owner;
		final Vec3d center;
		final double radius;
		final float damage;
		final double launch;
		final long start;
		final int ticks;
		final Vec3d facing;
		final double cone;
		final Set<UUID> hit = new HashSet<>();
		final Set<BlockPos> placed = new HashSet<>();

		Eruption(UUID owner, Vec3d center, double radius, float damage, double launch, long start, int ticks, Vec3d facing, double cone) {
			this.owner = owner;
			this.center = center;
			this.radius = radius;
			this.damage = damage;
			this.launch = launch;
			this.start = start;
			this.ticks = ticks;
			this.facing = facing;
			this.cone = cone;
		}
	}

	/** Kristallsturm: Splitter kreisen um Diamondhead, dann fliegen sie nach aussen. */
	private record Storm(long start, int ticks, float damage, int count, float speed, boolean prism) {
	}

	private DiamondheadAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_volley"), DiamondheadAbilities::volley);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_blade"), DiamondheadAbilities::blade);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_vault"), DiamondheadAbilities::vault);
		AbilityRegistry.register(KingdomOmnitrix.id("spike_eruption"), DiamondheadAbilities::spikeEruption);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_armor"), DiamondheadAbilities::armor);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_storm"), DiamondheadAbilities::storm);
		ServerTickEvents.END_SERVER_TICK.register(DiamondheadAbilities::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(DiamondheadAbilities::removeAllSpikes);
		// Abbauen einer Spitze: zerspringt ohne Drop (kein Amethyst aus Faehigkeiten)
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerWorld server) || SPIKES.isEmpty()) {
				return true;
			}
			for (Iterator<Spike> it = SPIKES.iterator(); it.hasNext(); ) {
				Spike spike = it.next();
				if (spike.pos().equals(pos) && spike.world() == server.getRegistryKey()) {
					shatter(server, spike);
					it.remove();
					return false;
				}
			}
			return true;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			forgetState(handler.getPlayer().getUuid());
			AlienMeterSync.forget(handler.getPlayer().getUuid());
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DiamondheadAbilities::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken <= 0.0f) {
				return;
			}
			if (target instanceof ServerPlayerEntity victim && isDiamondhead(victim)) {
				addCharge(victim, taken * CHARGE_TAKEN);
			}
			if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != target && isDiamondhead(attacker)) {
				addCharge(attacker, taken * CHARGE_DEALT);
			}
		});
	}

	// --- Resonanz --------------------------------------------------------------------------------

	private static boolean isDiamondhead(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(DIAMONDHEAD::equals).isPresent();
	}

	static float charge(ServerPlayerEntity player) {
		return CHARGE.getOrDefault(player.getUuid(), 0.0f);
	}

	private static void addCharge(ServerPlayerEntity player, float amount) {
		float before = charge(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		CHARGE.put(player.getUuid(), after);
		if (amount > 0.0f) {
			LAST_COMBAT.put(player.getUuid(), player.getServerWorld().getTime());
		}
		if (before < MAX && after >= MAX) {
			ServerWorld world = player.getServerWorld();
			world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getBodyY(0.6), player.getZ(), 24, 0.5, 0.7, 0.5, 0.05);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.5f, 1.4f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.diamondhead_prism").formatted(Formatting.GREEN), true);
		}
		AlienMeterSync.update(player, AlienMeterPayload.RESONANCE, after);
	}

	private static Power power(ServerPlayerEntity player) {
		float charge = charge(player);
		if (charge >= MAX) {
			CHARGE.put(player.getUuid(), 0.0f);
			AlienMeterSync.update(player, AlienMeterPayload.RESONANCE, 0.0f);
			return new Power(1.8f, true);
		}
		return new Power(charge >= FACETED ? 1.3f : 1.0f, false);
	}

	private record Power(float factor, boolean prism) {
	}

	/** Geschoss prallt ab (Immunitaet entscheidet woanders): Resonanz, ab „facettiert“ Rueckwurf als Splitter. */
	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayerEntity player && isDiamondhead(player) && source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			// derselbe Treffer kann mehrfach geprueft werden — hoechstens einmal je Tick zaehlen
			if (LAST_COMBAT.getOrDefault(player.getUuid(), 0L) != now) {
				addCharge(player, CHARGE_PER_PROJECTILE);
				world.spawnParticles(GREEN, player.getX(), player.getBodyY(0.6), player.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.0f, 1.4f);
				Entity shooter = source.getAttacker();
				if (charge(player) >= FACETED && shooter instanceof LivingEntity living && living.isAlive() && PartyRules.canHarm(player, living)) {
					HeroProjectileEntity.shootAt(world, player, ModItems.CRYSTAL_SHARD, living, 2.4f, 0.0f).withDamage(Math.max(4.0f, amount));
				}
			}
		}
		return true;
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	private static boolean volley(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Power power = power(player);
		int count = (int) Math.max(1, ctx.param("count", 5)) + (charge(player) >= FACETED ? 3 : 0) + (power.prism() ? 7 : 0);
		float speed = (float) ctx.param("speed", 2.0);
		float spread = (float) ctx.param("spread", 6.0) * (power.prism() ? 1.8f : 1.0f);
		float damage = (float) ctx.param("damage", 4.0) * power.factor();
		for (int i = 0; i < count; i++) {
			HeroProjectileEntity shard = HeroProjectileEntity.shoot(ctx.world(), player, ModItems.CRYSTAL_SHARD, speed, spread).withDamage(damage);
			if (power.prism()) {
				shard.withExplosion(0.8f);
			}
		}
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_CRYSTAL, 1.0f, power.prism() ? 0.7f : 1.0f);
		return true;
	}

	/** Kristallklinge: Arm wird zur Klinge, Schwung im Halbkreis; Prisma: Klingenwelle fliegt 12 Bloecke weit. */
	private static boolean blade(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Power power = power(player);
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		double range = ctx.param("range", 4.0);
		float damage = (float) ctx.param("damage", 9.0) * power.factor();
		int hits = 0;
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, power.prism() ? 12.0 : range)) {
			Vec3d flat = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			double distance = flat.length();
			double cone = power.prism() && distance > range ? 0.85 : 0.2;
			if (distance > 1.0E-3 && flat.normalize().dotProduct(look) < cone) {
				continue;
			}
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().playerAttack(player), damage);
			target.takeKnockback(0.6, -look.x, -look.z);
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.5), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			hits++;
		}
		// Bogen aus Kristallstaub vor Diamondhead; bei Prisma als fliegende Welle bis 12 Bloecke
		double reach = power.prism() ? 12.0 : range;
		for (int i = -6; i <= 6; i++) {
			double angle = Math.atan2(look.z, look.x) + i * 0.13;
			for (double d = 1.2; d <= reach; d += power.prism() ? 1.0 : reach) {
				world.spawnParticles(GREEN, player.getX() + Math.cos(angle) * d, player.getBodyY(0.6), player.getZ() + Math.sin(angle) * d,
						1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (hits == 0 && !power.prism()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, power.prism() ? 0.6f : 1.0f);
		return true;
	}

	/** Kristallsprung: eine Kristallsaeule schiesst unter Diamondhead hoch und katapultiert ihn; Landung: Spitzenring. */
	private static boolean vault(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		BlockPos base = player.getBlockPos();
		for (int i = 0; i < 3; i++) {
			placeSpike(world, base.up(i), Blocks.AMETHYST_BLOCK.getDefaultState(), 30);
		}
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		BuiltinAbilities.launch(player, look.x * ctx.param("forward", 1.3), ctx.param("up", 1.1) + 0.3, look.z * ctx.param("forward", 1.3));
		LEAPS.put(player.getUuid(), world.getTime() + 100);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 1.4f, 0.8f);
		return true;
	}

	private static boolean spikeEruption(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Power power = power(player);
		ERUPTIONS.add(new Eruption(player.getUuid(), player.getPos(), ctx.param("radius", 6.0) * (power.prism() ? 1.7 : 1.0),
				(float) ctx.param("damage", 9.0) * power.factor(), ctx.param("launch", 0.7), ctx.world().getTime(), power.prism() ? 14 : 10,
				Vec3d.ZERO, 0.0));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.6f);
		return true;
	}

	/** Kristallpanzer: Schutz, Nahkaempfer stossen sich an Kristallspitzen, die Resonanz baut sich nicht ab. */
	private static boolean armor(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 12.0) * 20);
		ARMOR.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 1, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, ticks, 2, false, false));
		ctx.world().spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.getDefaultState()),
				player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.5, 0.8, 0.5, 0.1);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.0f);
		return true;
	}

	private static boolean storm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Power power = power(player);
		STORMS.put(player.getUuid(), new Storm(ctx.world().getTime(), power.prism() ? 80 : 50, (float) ctx.param("damage", 6.0) * power.factor(),
				(int) Math.max(4, ctx.param("count", 24)) * (power.prism() ? 2 : 1), (float) ctx.param("speed", 1.8), power.prism()));
		ctx.grantInvulnerability(20);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 0.6f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!isDiamondhead(player)) {
				if (CHARGE.containsKey(player.getUuid()) || ARMOR.containsKey(player.getUuid()) || STORMS.containsKey(player.getUuid())) {
					if (CHARGE.containsKey(player.getUuid())) {
						AlienMeterSync.clear(player, AlienMeterPayload.RESONANCE);
					}
					forgetState(player.getUuid());
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			boolean armored = ARMOR.containsKey(player.getUuid());
			if (now % 20 == 0 && !armored && charge(player) > 0.0f && now - LAST_COMBAT.getOrDefault(player.getUuid(), 0L) > CALM_TICKS) {
				addCharge(player, -DECAY);
			}
			if (armored) {
				tickArmor(world, player, now);
			}
			tickLeap(world, player, now);
			tickStorm(world, player, now);
		}
		if (!ERUPTIONS.isEmpty()) {
			tickEruptions(server);
		}
		if (!SPIKES.isEmpty()) {
			tickSpikes(server);
		}
	}

	private static void tickArmor(ServerWorld world, ServerPlayerEntity player, long now) {
		if (now >= ARMOR.get(player.getUuid())) {
			ARMOR.remove(player.getUuid());
			return;
		}
		if (now % 10 == 0) {
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(1.6),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
				target.damage(world.getDamageSources().thorns(player), 3.0f);
				Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
				if (away.lengthSquared() > 1.0E-4) {
					target.takeKnockback(0.8, -away.x, -away.z);
				}
			}
			world.spawnParticles(GREEN, player.getX(), player.getBodyY(0.5), player.getZ(), 6, 0.5, 0.7, 0.5, 0.0);
		}
	}

	private static void tickLeap(ServerWorld world, ServerPlayerEntity player, long now) {
		Long until = LEAPS.get(player.getUuid());
		if (until == null) {
			return;
		}
		player.fallDistance = 0.0f;
		if (now >= until) {
			LEAPS.remove(player.getUuid());
			return;
		}
		if (player.isOnGround() && now > until - 94) {
			LEAPS.remove(player.getUuid());
			ERUPTIONS.add(new Eruption(player.getUuid(), player.getPos(), 3.5, 6.0f, 0.5, now, 5, Vec3d.ZERO, 0.0));
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 1.2f, 0.7f);
		}
	}

	private static void tickStorm(ServerWorld world, ServerPlayerEntity player, long now) {
		Storm storm = STORMS.get(player.getUuid());
		if (storm == null) {
			return;
		}
		long age = now - storm.start();
		double radius = 2.2 + (storm.prism() ? 1.0 : 0.0);
		// kreisende Splitter
		for (int i = 0; i < 12; i++) {
			double angle = i * MathHelper.TAU / 12 + age * 0.35;
			world.spawnParticles(storm.prism() ? ParticleTypes.END_ROD : GREEN, player.getX() + Math.cos(angle) * radius,
					player.getBodyY(0.3 + 0.4 * ((i % 3) / 2.0)), player.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (age % 8 == 0) {
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius + 0.5),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
				target.timeUntilRegen = 0;
				target.damage(world.getDamageSources().playerAttack(player), storm.damage() * 0.4f);
			}
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.2f + age * 0.01f);
		}
		if (age >= storm.ticks()) {
			STORMS.remove(player.getUuid());
			// Splitter fliegen nach aussen
			for (int i = 0; i < storm.count(); i++) {
				float yaw = player.getYaw() + i * 360.0f / storm.count();
				HeroProjectileEntity shard = new HeroProjectileEntity(world, player);
				shard.setItem(new ItemStack(ModItems.CRYSTAL_SHARD));
				shard.setVelocity(player, -5.0f - (i % 3) * 8.0f, yaw, 0.0f, storm.speed(), 1.0f);
				shard.withDamage(storm.damage());
				world.spawnEntity(shard);
			}
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.4f, 0.7f);
		}
	}

	private static void tickEruptions(MinecraftServer server) {
		Iterator<Eruption> it = ERUPTIONS.iterator();
		while (it.hasNext()) {
			Eruption eruption = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(eruption.owner);
			if (player == null) {
				it.remove();
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long age = world.getTime() - eruption.start;
			double front = eruption.radius * Math.min(1.0, (age + 1) / (double) eruption.ticks);
			// Spitzen an der Front wachsen lassen (etwa eine je Bogenmeter)
			int points = Math.max(6, (int) (front * 2.2));
			for (int i = 0; i < points; i++) {
				double angle = i * MathHelper.TAU / points + age * 0.21;
				Vec3d dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
				if (eruption.cone > 0.0 && dir.dotProduct(eruption.facing) < eruption.cone) {
					continue;
				}
				Vec3d p = eruption.center.add(dir.multiply(front));
				BlockPos ground = surface(world, BlockPos.ofFloored(p.x, eruption.center.y + 0.5, p.z));
				if (ground != null && eruption.placed.add(ground)) {
					boolean tall = (ground.getX() + ground.getZ()) % 3 == 0;
					placeSpike(world, ground, tall ? Blocks.AMETHYST_BLOCK.getDefaultState()
							: Blocks.AMETHYST_CLUSTER.getDefaultState().with(AmethystClusterBlock.FACING, Direction.UP), SPIKE_TICKS);
					if (tall) {
						placeSpike(world, ground.up(), Blocks.AMETHYST_CLUSTER.getDefaultState().with(AmethystClusterBlock.FACING, Direction.UP), SPIKE_TICKS);
					}
				}
			}
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, Box.of(eruption.center, front * 2 + 1, 5, front * 2 + 1),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && !eruption.hit.contains(e.getUuid()))) {
				Vec3d flat = target.getPos().subtract(eruption.center).multiply(1, 0, 1);
				double distance = flat.length();
				if (distance > front || Math.abs(target.getY() - eruption.center.y) > 3.0) {
					continue;
				}
				eruption.hit.add(target.getUuid());
				target.timeUntilRegen = 0;
				target.damage(world.getDamageSources().playerAttack(player), eruption.damage);
				target.addVelocity(0.0, eruption.launch, 0.0);
				target.velocityModified = true;
			}
			if (age >= eruption.ticks) {
				it.remove();
			}
		}
	}

	/** Abgelaufene Spitzen zerspringen (nur wenn dort noch der gesetzte Block steht). */
	private static void tickSpikes(MinecraftServer server) {
		Iterator<Spike> it = SPIKES.iterator();
		while (it.hasNext()) {
			Spike spike = it.next();
			ServerWorld world = server.getWorld(spike.world());
			if (world == null) {
				it.remove();
				continue;
			}
			// ungeladener Chunk: spaeter erneut versuchen (spaetestens beim Herunterfahren)
			if (world.getTime() >= spike.until() && shatter(world, spike)) {
				it.remove();
			}
		}
	}

	// --- Kristallbloecke -------------------------------------------------------------------------

	/** Oberste Luftstelle ueber festem Grund nahe der Hoehe (bis 2 hoch/runter), sonst null. */
	private static BlockPos surface(ServerWorld world, BlockPos around) {
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos pos = around.up(dy);
			if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)) {
				return pos;
			}
		}
		return null;
	}

	private static void placeSpike(ServerWorld world, BlockPos pos, BlockState state, int ticks) {
		if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || !world.getBlockState(pos).isAir()
				|| !world.getEntitiesByClass(LivingEntity.class, new Box(pos), e -> e instanceof ServerPlayerEntity).isEmpty()) {
			return;
		}
		world.setBlockState(pos, state);
		SPIKES.add(new Spike(world.getRegistryKey(), pos, state, world.getTime() + ticks));
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
				6, 0.25, 0.1, 0.25, 0.1);
	}

	/** @return true, wenn erledigt (zersprungen oder inzwischen anders bebaut); false = Chunk nicht geladen */
	private static boolean shatter(ServerWorld world, Spike spike) {
		if (!world.isChunkLoaded(spike.pos().getX() >> 4, spike.pos().getZ() >> 4)) {
			return false;
		}
		if (world.getBlockState(spike.pos()) == spike.state()) {
			world.setBlockState(spike.pos(), Blocks.AIR.getDefaultState(), QUIET);
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, spike.state()), spike.pos().getX() + 0.5,
					spike.pos().getY() + 0.5, spike.pos().getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.1);
			if (world.random.nextInt(4) == 0) {
				world.playSound(null, spike.pos(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 0.6f, 1.2f);
			}
		}
		return true;
	}

	/** Server faehrt herunter: alle Spitzen sofort weg, damit keine im Spielstand bleiben. */
	private static void removeAllSpikes(MinecraftServer server) {
		for (Spike spike : SPIKES) {
			ServerWorld world = server.getWorld(spike.world());
			if (world != null && world.getBlockState(spike.pos()) == spike.state()) {
				world.setBlockState(spike.pos(), Blocks.AIR.getDefaultState(), QUIET);
			}
		}
		SPIKES.clear();
		ERUPTIONS.clear();
	}

	private static void forgetState(UUID id) {
		CHARGE.remove(id);
		LAST_COMBAT.remove(id);
		ARMOR.remove(id);
		LEAPS.remove(id);
		STORMS.remove(id);
	}
}
