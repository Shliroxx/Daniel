package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.vfx.CrystalGrowth;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import com.santiq.kingdomomnitrix.world.content.PlanetBlocks;
import com.santiq.kingdomomnitrix.world.content.TempBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
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

/**
 * Diamondhead (Petrosapien). Eigenes Dauer-System „Resonanz“ (0–100) und echte Kristall-Konstrukte:
 *
 * <ul>
 *   <li>Resonanz steigt, wenn Geschosse an Diamondhead abprallen (er ist immun), mit ausgeteiltem und eingestecktem
 *       Schaden; nach 5 s ohne Kampf klingt sie ab.</li>
 *   <li>Ab {@link #FACETED} <b>facettiert</b>: jedes abprallende Geschoss wird als Kristallsplitter auf den Schuetzen
 *       zurueckgeworfen, Splitter machen mehr Schaden.</li>
 *   <li>Bei {@link #MAX} <b>Prisma</b>: die naechste Faehigkeit verbraucht die Resonanz fuer ihre groesste Form.</li>
 *   <li>Kristall-Konstrukte aus eigenen Bloecken ({@link TempBlocks}): Stacheln brechen aus dem Boden, eine Kuppel aus
 *       Stacheln schuetzt, beim <b>Kristallsurfen</b> waechst unter Diamondhead eine Kristallbruecke, die seinem Blick
 *       folgt (auch bergauf) und hinter ihm zerspringt. Gesetzt wird nur in Luft, nach Ablauf verschwindet alles.</li>
 * </ul>
 * Anzeige ueber {@link AlienMeterPayload#RESONANCE} (gruene Aura).
 */
final class DiamondheadAbilities {
	/** Modi (traits.modes im Alien-JSON, Modell-Animationen mode_<id>_on/_off) */
	private static final String BLADE_MODE = "crystal_blade";
	private static final String SHIELD_MODE = "crystal_shield";
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
	/** Kristallgruen der FX-Engine und Prisma-Weiss */
	static final int CRYSTAL = 0x3AE89A;
	static final int PRISM = 0xE8FFF4;
	/** Kristallsurfen: Tempo (Bloecke/Tick), Lebensdauer der Bruecke (Ticks) */
	private static final double SURF_SPEED = 0.85;
	private static final int BRIDGE_TICKS = 50;

	private static final Map<UUID, Float> CHARGE = new HashMap<>();
	private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
	private static final Map<UUID, Long> ARMOR = new HashMap<>();
	private static final Map<UUID, Long> SURFS = new HashMap<>();
	private static final Map<UUID, Storm> STORMS = new HashMap<>();
	private static final List<Eruption> ERUPTIONS = new ArrayList<>();

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
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_vault"), DiamondheadAbilities::surf);
		AbilityRegistry.register(KingdomOmnitrix.id("spike_eruption"), DiamondheadAbilities::spikeEruption);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_armor"), DiamondheadAbilities::armor);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_storm"), DiamondheadAbilities::storm);
		ServerTickEvents.END_SERVER_TICK.register(DiamondheadAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			forgetState(handler.getPlayer().getUuid());
			AlienMeterSync.forget(handler.getPlayer().getUuid());
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DiamondheadAbilities::allowDamage);
		// Resonanz aus Kampfschaden: passive Kraft „Petrosapien-Resonanz“ (abprallende Geschosse zaehlen immer)
		com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry.register(KingdomOmnitrix.id("petrosapien_resonance"),
				new com.santiq.kingdomomnitrix.ability.passive.AlienPassive() {
					@Override
					public void afterHit(com.santiq.kingdomomnitrix.ability.passive.PassiveContext ctx, LivingEntity target, DamageSource source, float damage) {
						addCharge(ctx.player(), damage * CHARGE_DEALT);
					}

					@Override
					public void afterHurt(com.santiq.kingdomomnitrix.ability.passive.PassiveContext ctx, DamageSource source, float damage) {
						addCharge(ctx.player(), damage * CHARGE_TAKEN);
					}
				});
		DiamondheadMoves.register();
	}

	// --- Resonanz --------------------------------------------------------------------------------

	private static boolean isDiamondhead(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(DIAMONDHEAD::equals).isPresent();
	}

	static float charge(ServerPlayerEntity player) {
		return CHARGE.getOrDefault(player.getUuid(), 0.0f);
	}

	static void addCharge(ServerPlayerEntity player, float amount) {
		float before = charge(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		CHARGE.put(player.getUuid(), after);
		if (amount > 0.0f) {
			LAST_COMBAT.put(player.getUuid(), player.getServerWorld().getTime());
		}
		if (before < MAX && after >= MAX) {
			ServerWorld world = player.getServerWorld();
			Fx.flash(world, player.getPos().add(0, 1.2, 0), PRISM, 2.6f);
			Fx.shards(world, player.getPos().add(0, 1.2, 0), CRYSTAL, 0.35f, 16);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.5f, 1.4f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.diamondhead_prism").formatted(Formatting.GREEN), true);
		}
		AlienMeterSync.update(player, AlienMeterPayload.RESONANCE, after);
	}

	static Power power(ServerPlayerEntity player) {
		float charge = charge(player);
		if (charge >= MAX) {
			CHARGE.put(player.getUuid(), 0.0f);
			AlienMeterSync.update(player, AlienMeterPayload.RESONANCE, 0.0f);
			return new Power(1.8f, true);
		}
		return new Power(charge >= FACETED ? 1.3f : 1.0f, false);
	}

	record Power(float factor, boolean prism) {
	}

	/** Geschoss prallt ab (Immunitaet entscheidet woanders): Resonanz, ab „facettiert“ Rueckwurf als Splitter. */
	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayerEntity player && isDiamondhead(player) && source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			// derselbe Treffer kann mehrfach geprueft werden — hoechstens einmal je Tick zaehlen
			if (LAST_COMBAT.getOrDefault(player.getUuid(), 0L) != now) {
				addCharge(player, CHARGE_PER_PROJECTILE);
				Fx.flash(world, player.getPos().add(0, 1.2, 0), CRYSTAL, 1.2f);
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
		Vec3d hand = player.getEyePos().add(player.getRotationVec(1.0f).multiply(0.9)).add(0, -0.3, 0);
		Fx.flash(ctx.world(), hand, power.prism() ? PRISM : CRYSTAL, 1.3f);
		Fx.shards(ctx.world(), hand, CRYSTAL, 0.2f, 5);
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_CRYSTAL, 1.0f, power.prism() ? 0.7f : 1.0f);
		return true;
	}

	/** Kristallklinge: Arm wird zur Klinge, Schwung im Halbkreis; Prisma: Klingenwelle fliegt 12 Bloecke weit. */
	/**
	 * Kristallschwert: der rechte Unterarm waechst zur Klinge (AE-Modell und -Animation) — ein Modus mit mehr
	 * Angriff, Reichweite und Schwung, der auch Nachbarn trifft. Beim Ausfahren ein weiter Hieb; nochmal druecken zieht
	 * die Klinge ein.
	 */
	private static boolean blade(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		if (!com.santiq.kingdomomnitrix.alien.AlienModes.toggle(player, BLADE_MODE, 0)) {
			BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.7f);
			return true;
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 1.4f, 0.8f);
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
			Fx.flash(world, target.getPos().add(0, target.getHeight() * 0.5, 0), CRYSTAL, 1.4f);
			Fx.shards(world, target.getPos().add(0, target.getHeight() * 0.5, 0), CRYSTAL, 0.35f, 8);
			hits++;
		}
		// Bogen aus Kristallstaub vor Diamondhead; bei Prisma als fliegende Welle bis 12 Bloecke
		double reach = power.prism() ? 12.0 : range;
		Vec3d chest = player.getPos().add(0, 1.1, 0);
		for (int i = -4; i <= 4; i++) {
			double angle = Math.atan2(look.z, look.x) + i * 0.2;
			Vec3d dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
			Fx.send(world, Fx.Kind.SPARKS, chest.add(dir.multiply(range * 0.7)), Vec3d.ZERO, CRYSTAL, 0.25f, 0, 3, null);
		}
		if (power.prism()) {
			Fx.beam(world, chest, chest.add(look.multiply(reach)), PRISM, 0.6f, 10);
		}
		if (hits == 0 && !power.prism()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, power.prism() ? 0.6f : 1.0f);
		return true;
	}

	/**
	 * Kristallsurfen: unter Diamondhead waechst eine drei Bloecke breite Kristallbruecke, die dem Blick folgt (auch
	 * bergauf und bergab); seitlich schiessen Stacheln hoch, Gegner auf dem Weg werden zur Seite geschleudert. Hinter ihm
	 * zerspringt die Bruecke. Schleichen beendet das Surfen.
	 */
	private static boolean surf(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) ctx.param("surf_ticks", 100.0);
		SURFS.put(player.getUuid(), world.getTime() + ticks);
		BlockPos base = player.getBlockPos();
		for (int i = 0; i < 2; i++) {
			TempBlocks.place(world, base.up(i), PlanetBlocks.PETROSAPIEN_CRYSTAL.getDefaultState(), 30, player, CRYSTAL, true);
		}
		BuiltinAbilities.launch(player, 0.0, ctx.param("up", 0.9), 0.0);
		Fx.shockwave(world, player.getPos(), CRYSTAL, 3.0f, 8);
		Fx.flash(world, player.getPos().add(0, 0.5, 0), CRYSTAL, 2.0f);
		Fx.shards(world, player.getPos(), CRYSTAL, 0.4f, 14);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 1.4f, 0.8f);
		return true;
	}

	/** Spitzen-Welle an beliebigem Ort (auch fuer die neuen Kraefte). */
	static void eruption(ServerPlayerEntity player, Vec3d center, double radius, float damage, double launch, int ticks) {
		ERUPTIONS.add(new Eruption(player.getUuid(), center, radius, damage, launch, player.getServerWorld().getTime(), ticks, Vec3d.ZERO, 0.0));
	}

	private static boolean spikeEruption(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Power power = power(player);
		ERUPTIONS.add(new Eruption(player.getUuid(), player.getPos(), ctx.param("radius", 6.0) * (power.prism() ? 1.7 : 1.0),
				(float) ctx.param("damage", 9.0) * power.factor(), ctx.param("launch", 0.7), ctx.world().getTime(), power.prism() ? 14 : 10,
				Vec3d.ZERO, 0.0));
		float radius = (float) (ctx.param("radius", 6.0) * (power.prism() ? 1.7 : 1.0));
		Fx.shockwave(ctx.world(), player.getPos(), power.prism() ? PRISM : CRYSTAL, radius, power.prism() ? 14 : 10);
		Fx.flash(ctx.world(), player.getPos().add(0, 0.3, 0), CRYSTAL, 2.5f);
		Fx.shake(ctx.world(), player.getPos(), 0.45f, 12, (int) radius + 8);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.6f);
		return true;
	}

	/** Kristallpanzer: Schutz, Nahkaempfer stossen sich an Kristallspitzen, die Resonanz baut sich nicht ab. */
	/**
	 * Kristallpanzer: Schild am linken Unterarm (AE-Modell) fuer die Dauer — Modus, der Geschosse und Explosionen
	 * abhaelt und Nahkaempfer an den Stacheln verletzt, dazu Widerstand, Absorption und ein Stachelkranz am Boden.
	 */
	private static boolean armor(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 12.0) * 20);
		com.santiq.kingdomomnitrix.alien.AlienModes.set(player, SHIELD_MODE, ticks);
		ARMOR.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 1, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, ticks, 2, false, false));
		Fx.aura(ctx.world(), player, CRYSTAL, 0.8f, 30);
		Fx.flash(ctx.world(), player.getPos().add(0, 1.0, 0), CRYSTAL, 2.2f);
		// Stachelkranz: Ring aus Kristallstacheln um Diamondhead (haelt Nahkaempfer auf Abstand)
		ServerWorld world = ctx.world();
		for (int i = 0; i < 16; i++) {
			double angle = i * MathHelper.TAU / 16;
			BlockPos ground = TempBlocks.surface(world, BlockPos.ofFloored(player.getX() + Math.cos(angle) * 2.6, player.getY() + 0.5,
					player.getZ() + Math.sin(angle) * 2.6));
			if (ground != null) {
				TempBlocks.place(world, ground, PlanetBlocks.PETROSAPIEN_SPIKE.getDefaultState(), Math.min(ticks, 160), player, CRYSTAL, false);
				if (i % 2 == 0) {
					CrystalGrowth.shard(world, Vec3d.ofBottomCenter(ground), PlanetBlocks.PETROSAPIEN_CRYSTAL.getDefaultState(), (float) angle, 0.55f,
							0.35f, 2.2f, Math.min(ticks, 160), CRYSTAL);
				}
			}
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.0f);
		return true;
	}

	private static boolean storm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Power power = power(player);
		STORMS.put(player.getUuid(), new Storm(ctx.world().getTime(), power.prism() ? 80 : 50, (float) ctx.param("damage", 6.0) * power.factor(),
				(int) Math.max(4, ctx.param("count", 24)) * (power.prism() ? 2 : 1), (float) ctx.param("speed", 1.8), power.prism()));
		ctx.grantInvulnerability(20);
		Fx.charge(ctx.world(), player, power.prism() ? PRISM : CRYSTAL, 1.2f, power.prism() ? 80 : 50);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 0.6f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!isDiamondhead(player)) {
				if (CHARGE.containsKey(player.getUuid()) || ARMOR.containsKey(player.getUuid()) || STORMS.containsKey(player.getUuid())
						|| SURFS.containsKey(player.getUuid())) {
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
			tickSurf(world, player, now);
			tickStorm(world, player, now);
		}
		if (!ERUPTIONS.isEmpty()) {
			tickEruptions(server);
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
			Fx.shards(world, player.getPos().add(0, 1.0, 0), CRYSTAL, 0.15f, 3);
		}
	}

	private static void tickSurf(ServerWorld world, ServerPlayerEntity player, long now) {
		Long until = SURFS.get(player.getUuid());
		if (until == null) {
			return;
		}
		player.fallDistance = 0.0f;
		if (now >= until || player.isSneaking()) {
			SURFS.remove(player.getUuid());
			// Abschluss: kleiner Stachelring, wo das Surfen endet
			ERUPTIONS.add(new Eruption(player.getUuid(), player.getPos(), 3.0, 5.0f, 0.4, now, 5, Vec3d.ZERO, 0.0));
			Fx.shockwave(world, player.getPos(), CRYSTAL, 3.5f, 8);
			return;
		}
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		if (flat.lengthSquared() < 1.0e-4) {
			flat = BuiltinAbilities.horizontalLook(player);
		}
		flat = flat.normalize();
		// Neigung folgt dem Blick: hoch schauen = Rampe hinauf, runter schauen = abwaerts
		double climb = MathHelper.clamp(-player.getPitch() / 90.0 * 0.9, -0.45, 0.55);
		player.setVelocity(flat.x * SURF_SPEED, climb, flat.z * SURF_SPEED);
		player.velocityModified = true;
		// Bruecke unter den Fuessen und ein Stueck voraus, drei Bloecke breit
		Vec3d side = new Vec3d(-flat.z, 0, flat.x);
		BlockPos feet = BlockPos.ofFloored(player.getX(), player.getY() - 0.5, player.getZ());
		for (int ahead = 0; ahead <= 1; ahead++) {
			Vec3d c = Vec3d.ofCenter(feet).add(flat.multiply(ahead));
			for (int w = -1; w <= 1; w++) {
				BlockPos pos = BlockPos.ofFloored(c.add(side.multiply(w)));
				TempBlocks.place(world, pos, PlanetBlocks.PETROSAPIEN_CRYSTAL.getDefaultState(), BRIDGE_TICKS, player, CRYSTAL, false);
			}
		}
		// Kielwasser: seitlich schiessen Stacheln hoch
		if (now % 3 == 0) {
			for (int w : new int[] {-2, 2}) {
				BlockPos spike = BlockPos.ofFloored(Vec3d.ofCenter(feet).add(side.multiply(w)).add(0, 1, 0));
				if (world.getBlockState(spike.down()).isSideSolidFullSquare(world, spike.down(), Direction.UP)) {
					TempBlocks.place(world, spike, PlanetBlocks.PETROSAPIEN_SPIKE.getDefaultState(), BRIDGE_TICKS, player, CRYSTAL, false);
					// Kielwasser: hohe Kristalle kippen von der Bahn weg
					float yaw = (float) Math.atan2(side.z * w, side.x * w);
					CrystalGrowth.shard(world, Vec3d.ofBottomCenter(spike), PlanetBlocks.PETROSAPIEN_CRYSTAL.getDefaultState(), yaw, 0.6f, 0.4f,
							2.4f + world.random.nextFloat(), BRIDGE_TICKS, CRYSTAL);
				}
			}
		}
		if (now % 2 == 0) {
			Fx.shards(world, player.getPos(), CRYSTAL, 0.25f, 3);
			Fx.send(world, Fx.Kind.SPARKS, player.getPos().add(0, 0.1, 0), Vec3d.ZERO, CRYSTAL, 0.3f, 0, 4, null);
		}
		if (now % 6 == 0) {
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f,
					1.0f + world.random.nextFloat() * 0.5f);
		}
		// Gegner auf dem Weg werden zur Seite geschleudert
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(1.4, 0.5, 1.4),
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && e.hurtTime == 0)) {
			target.damage(world.getDamageSources().playerAttack(player), 6.0f);
			Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			double sign = away.dotProduct(side) >= 0 ? 1 : -1;
			target.setVelocity(side.multiply(sign * 1.1).add(flat.multiply(0.5)).add(0, 0.5, 0));
			target.velocityModified = true;
			Fx.flash(world, target.getPos().add(0, target.getHeight() * 0.5, 0), CRYSTAL, 1.5f);
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
		if (age % 2 == 0) {
			for (int i = 0; i < 6; i++) {
				double angle = i * MathHelper.TAU / 6 + age * 0.35;
				Vec3d p = player.getPos().add(Math.cos(angle) * radius, 0.4 + 0.5 * (i % 3), Math.sin(angle) * radius);
				Fx.send(world, Fx.Kind.SHARDS, p, Vec3d.ZERO, storm.prism() ? PRISM : CRYSTAL, 0.05f, 0, 1, null);
			}
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
			Fx.flash(world, player.getPos().add(0, 1.0, 0), storm.prism() ? PRISM : CRYSTAL, 3.5f);
			Fx.shockwave(world, player.getPos(), CRYSTAL, 7.0f, 10);
			Fx.shards(world, player.getPos().add(0, 1.0, 0), CRYSTAL, 0.6f, 30);
			Fx.shake(world, player.getPos(), 0.5f, 12, 16);
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
				BlockPos ground = TempBlocks.surface(world, BlockPos.ofFloored(p.x, eruption.center.y + 0.5, p.z));
				if (ground != null && eruption.placed.add(ground)) {
					boolean tall = Math.floorMod(ground.getX() * 7 + ground.getZ() * 13, 4) == 0;
					TempBlocks.place(world, ground, PlanetBlocks.PETROSAPIEN_SPIKE.getDefaultState(), SPIKE_TICKS, player, CRYSTAL, false);
					if (tall) {
						// Geoden-Buendel: hohe, schraeg nach aussen kippende Kristalle
						float height = (float) (2.0 + 2.5 * front / eruption.radius);
						CrystalGrowth.cluster(world, Vec3d.ofBottomCenter(ground), PlanetBlocks.PETROSAPIEN_CRYSTAL.getDefaultState(),
								3 + world.random.nextInt(3), height, 0.35f, SPIKE_TICKS, CRYSTAL);
					}
					if (world.random.nextInt(3) == 0) {
						Fx.shards(world, Vec3d.ofCenter(ground), CRYSTAL, 0.3f, 3);
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
				// AE-Kristallstachel bleibt im Getroffenen stecken
				AttachedPropEntity.attach(world, "crystal_spike", target, 60);
				Fx.flash(world, target.getPos().add(0, target.getHeight() * 0.5, 0), CRYSTAL, 1.3f);
			}
			if (age >= eruption.ticks) {
				it.remove();
			}
		}
	}

	// --- Kristallbloecke -------------------------------------------------------------------------

	private static void forgetState(UUID id) {
		CHARGE.remove(id);
		LAST_COMBAT.remove(id);
		ARMOR.remove(id);
		SURFS.remove(id);
		STORMS.remove(id);
	}
}
