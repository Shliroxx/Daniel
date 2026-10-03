package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
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
 * Upgrade (Galvanic Mechamorph). Eigenes Dauer-System „Integration“ (0–100) und Naniten:
 *
 * <ul>
 *   <li>Integration steigt mit Treffern, mit jedem Gegner, durch den die Fluessigform fliesst, mit Techno-Fusion und
 *       langsam neben Technik-Bloecken (Eisen, Kupfer, Redstone-Bauteile). Nach 6 s ohne Nachschub sinkt sie.</li>
 *   <li>Treffer legen <b>Naniten</b> (0–{@link #MAX_NANITES}) in Gegner. Sie machen die Systemuebernahme laenger und
 *       entladen sich beim Morphwaffen-Treffer.</li>
 *   <li>Ab {@link #LINKED} <b>vernetzt</b>: der Optikstrahl durchschlaegt alles in der Linie und springt auf zwei
 *       weitere Ziele, Morphwaffen-Treffer schlagen als Kettenblitz weiter.</li>
 *   <li>Bei {@link #MAX} <b>Overclock</b> fuer {@link #OVERCLOCK_TICKS} Ticks: +40 % Schaden, schneller, die Anzeige
 *       laeuft dabei leer; die Plasmakanone wird zur Omega-Kanone.</li>
 * </ul>
 * Systemuebernahme macht einen Mob fuer einige Sekunden zum Verbuendeten: er greift die naechsten Feinde an und nie
 * Upgrade oder dessen Gruppe. Bosse und Spieler bekommen stattdessen eine Systemstoerung.
 * Anzeige ueber {@link AlienMeterPayload#SYNC} (neongruene Aura).
 */
final class UpgradeAbilities {
	static final float MAX = 100.0f;
	static final float LINKED = 50.0f;
	static final int MAX_NANITES = 5;
	private static final Identifier UPGRADE = KingdomOmnitrix.id("upgrade");
	private static final float SYNC_DEALT = 0.5f;
	private static final float SYNC_BEAM = 8.0f;
	private static final float SYNC_FLOW = 5.0f;
	private static final float SYNC_FUSION = 15.0f;
	private static final float SYNC_TECH_BLOCK = 1.0f;
	private static final int CALM_TICKS = 120;
	private static final float DECAY = 3.0f;
	private static final int OVERCLOCK_TICKS = 160;
	private static final float OVERCLOCK_BONUS = 0.4f;
	private static final int NANITE_TICKS = 300;
	private static final int PLASMA_CHARGE = 20;
	private static final DustParticleEffect NEON = new DustParticleEffect(new Vector3f(0.22f, 1.0f, 0.08f), 1.0f);
	private static final DustParticleEffect NEON_BIG = new DustParticleEffect(new Vector3f(0.22f, 1.0f, 0.08f), 1.8f);
	private static final DustParticleEffect CYAN = new DustParticleEffect(new Vector3f(0.0f, 0.9f, 1.0f), 1.6f);
	private static final DustParticleEffect LIQUID = new DustParticleEffect(new Vector3f(0.04f, 0.04f, 0.04f), 1.6f);

	private static final Map<UUID, Float> SYNC = new HashMap<>();
	private static final Map<UUID, Long> LAST_GAIN = new HashMap<>();
	private static final Map<UUID, Long> OVERCLOCK = new HashMap<>();
	/** Fluessigform: Ende und schon durchflossene Gegner */
	private static final Map<UUID, Flow> FLOWS = new HashMap<>();
	/** Techno-Fusion: Ende */
	private static final Map<UUID, Long> FUSION = new HashMap<>();
	/** Morphwaffen: Ende */
	private static final Map<UUID, Long> MACES = new HashMap<>();
	/** geladene Plasmakanonen */
	private static final Map<UUID, Charge> CHARGES = new HashMap<>();
	/** Naniten im Gegner: Anzahl und Ablauf */
	private static final Map<UUID, int[]> NANITES = new HashMap<>();
	private static final Map<UUID, Long> NANITE_UNTIL = new HashMap<>();
	/** uebernommene Mobs */
	private static final Map<UUID, Hijack> HIJACKS = new HashMap<>();
	/** verhindert, dass Bonus-Schaden erneut Bonus-Schaden ausloest */
	private static boolean bonusHit;

	private record Flow(long until, Set<UUID> touched) {
	}

	private record Charge(long fireAt, float damage, double range, double explosion, boolean omega) {
	}

	private record Hijack(UUID owner, net.minecraft.registry.RegistryKey<World> world, long until) {
	}

	private UpgradeAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("optic_beam"), UpgradeAbilities::opticBeam);
		AbilityRegistry.register(KingdomOmnitrix.id("liquid_form"), UpgradeAbilities::liquidForm);
		AbilityRegistry.register(KingdomOmnitrix.id("tech_upgrade"), UpgradeAbilities::techFusion);
		AbilityRegistry.register(KingdomOmnitrix.id("mace_fists"), UpgradeAbilities::maceFists);
		AbilityRegistry.register(KingdomOmnitrix.id("system_override"), UpgradeAbilities::systemOverride);
		AbilityRegistry.register(KingdomOmnitrix.id("plasma_cannon"), UpgradeAbilities::plasmaCannon);
		ServerTickEvents.END_SERVER_TICK.register(UpgradeAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			forgetState(id);
			releaseAll(server, id);
			AlienMeterSync.forget(id);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(UpgradeAbilities::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken <= 0.0f || bonusHit || !(source.getAttacker() instanceof ServerPlayerEntity attacker) || attacker == target
					|| !isUpgrade(attacker)) {
				return;
			}
			addSync(attacker, taken * SYNC_DEALT);
			if (source.isOf(DamageTypes.PLAYER_ATTACK) && source.getSource() == attacker) {
				onMelee(attacker, target, taken);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			NANITES.remove(entity.getUuid());
			NANITE_UNTIL.remove(entity.getUuid());
			HIJACKS.remove(entity.getUuid());
		});
	}

	// --- Integration -----------------------------------------------------------------------------

	private static boolean isUpgrade(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(UPGRADE::equals).isPresent();
	}

	static float sync(ServerPlayerEntity player) {
		return SYNC.getOrDefault(player.getUuid(), 0.0f);
	}

	private static boolean overclocked(ServerPlayerEntity player) {
		return OVERCLOCK.containsKey(player.getUuid());
	}

	/** Schadensfaktor aus dem aktuellen Zustand. */
	private static float power(ServerPlayerEntity player) {
		return overclocked(player) ? 1.0f + OVERCLOCK_BONUS : 1.0f + sync(player) / 400.0f;
	}

	private static void addSync(ServerPlayerEntity player, float amount) {
		if (overclocked(player) && amount > 0.0f) {
			return; // waehrend Overclock laeuft die Anzeige nur leer
		}
		float before = sync(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		SYNC.put(player.getUuid(), after);
		ServerWorld world = player.getServerWorld();
		if (amount > 0.0f) {
			LAST_GAIN.put(player.getUuid(), world.getTime());
		}
		if (before < LINKED && after >= LINKED) {
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.8f);
		}
		if (before < MAX && after >= MAX) {
			startOverclock(player);
		}
		AlienMeterSync.update(player, AlienMeterPayload.SYNC, after);
	}

	private static void startOverclock(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		OVERCLOCK.put(player.getUuid(), world.getTime() + OVERCLOCK_TICKS);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, OVERCLOCK_TICKS, 1, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, OVERCLOCK_TICKS, 2, false, false));
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getBodyY(0.6), player.getZ(), 60, 0.6, 0.9, 0.6, 0.4);
		world.spawnParticles(CYAN, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.5, 0.8, 0.5, 0.0);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.4f, 1.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE, SoundCategory.PLAYERS, 0.5f, 2.0f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.upgrade_overclock").formatted(Formatting.AQUA, Formatting.BOLD), true);
	}

	// --- Naniten ---------------------------------------------------------------------------------

	static int nanites(LivingEntity target) {
		int[] n = NANITES.get(target.getUuid());
		return n == null ? 0 : n[0];
	}

	private static void infect(ServerWorld world, LivingEntity target, int amount) {
		int[] n = NANITES.computeIfAbsent(target.getUuid(), id -> new int[1]);
		n[0] = Math.min(MAX_NANITES, n[0] + amount);
		NANITE_UNTIL.put(target.getUuid(), world.getTime() + NANITE_TICKS);
		world.spawnParticles(NEON, target.getX(), target.getBodyY(0.6), target.getZ(), 4 + 2 * n[0], 0.3, 0.4, 0.3, 0.0);
	}

	private static int purge(LivingEntity target) {
		int[] n = NANITES.remove(target.getUuid());
		NANITE_UNTIL.remove(target.getUuid());
		return n == null ? 0 : n[0];
	}

	// --- Treffer ---------------------------------------------------------------------------------

	private static void onMelee(ServerPlayerEntity player, LivingEntity target, float taken) {
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		boolean fused = FUSION.getOrDefault(player.getUuid(), 0L) > now;
		boolean maces = MACES.getOrDefault(player.getUuid(), 0L) > now;
		if (fused || maces) {
			infect(world, target, 1);
		}
		bonusHit = true;
		try {
			if (fused) {
				// Techno-Fusion: die verschmolzene Waffe entlaedt einen Stromstoss
				target.timeUntilRegen = 0;
				target.damage(world.getDamageSources().indirectMagic(player, player), 2.0f * power(player));
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getBodyY(0.6), target.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
			}
			if (maces) {
				maceShock(world, player, target, taken);
			}
		} finally {
			bonusHit = false;
		}
	}

	/** Morphwaffen-Treffer: Schockwelle um das Ziel, gespeicherte Naniten entladen sich, vernetzt als Kettenblitz. */
	private static void maceShock(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float taken) {
		float factor = power(player);
		int stored = purge(target);
		if (stored > 0) {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().indirectMagic(player, player), stored * 2.0f * factor);
			world.spawnParticles(NEON_BIG, target.getX(), target.getBodyY(0.5), target.getZ(), 10 * stored, 0.4, 0.5, 0.4, 0.05);
			world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.7f, 1.8f);
		}
		for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(3.0),
				e -> e != player && e != target && e.isAlive() && PartyRules.canHarm(player, e) && !isAlly(player, e))) {
			near.timeUntilRegen = 0;
			near.damage(world.getDamageSources().playerAttack(player), taken * 0.4f);
			Vec3d push = near.getPos().subtract(target.getPos()).multiply(1, 0, 1);
			if (push.lengthSquared() > 1.0E-4) {
				push = push.normalize().multiply(0.8);
				near.addVelocity(push.x, 0.3, push.z);
				near.velocityModified = true;
			}
		}
		world.spawnParticles(ParticleTypes.EXPLOSION, target.getX(), target.getBodyY(0.4), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.5f, 1.6f);
		if (sync(player) >= LINKED || overclocked(player)) {
			chain(world, player, target, taken * 0.6f, 2, 6.0);
		}
	}

	/** Kettenblitz: springt vom Ziel auf bis zu {@code jumps} weitere naechste Gegner. */
	private static void chain(ServerWorld world, ServerPlayerEntity player, LivingEntity from, float damage, int jumps, double reach) {
		Set<UUID> done = new HashSet<>();
		done.add(from.getUuid());
		LivingEntity current = from;
		for (int i = 0; i < jumps; i++) {
			LivingEntity origin = current;
			Optional<LivingEntity> next = world.getEntitiesByClass(LivingEntity.class, origin.getBoundingBox().expand(reach),
							e -> e != player && e.isAlive() && !done.contains(e.getUuid()) && PartyRules.canHarm(player, e) && !isAlly(player, e))
					.stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(origin)));
			if (next.isEmpty()) {
				return;
			}
			LivingEntity hit = next.get();
			done.add(hit.getUuid());
			drawLine(world, origin.getPos().add(0, origin.getHeight() * 0.6, 0), hit.getPos().add(0, hit.getHeight() * 0.6, 0), CYAN, 3.0);
			hit.timeUntilRegen = 0;
			hit.damage(world.getDamageSources().indirectMagic(player, player), damage);
			infect(world, hit, 1);
			world.playSound(null, hit.getBlockPos(), SoundEvents.ENTITY_BEE_STING, SoundCategory.PLAYERS, 0.4f, 2.0f);
			current = hit;
		}
	}

	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		// uebernommene Mobs greifen ihren Herrn und dessen Gruppe nie an
		Entity attacker = source.getAttacker();
		if (attacker instanceof MobEntity mob && target instanceof ServerPlayerEntity player) {
			Hijack hijack = HIJACKS.get(mob.getUuid());
			if (hijack == null) {
				return true;
			}
			ServerPlayerEntity owner = ownerOf(player.getServer(), hijack);
			if (hijack.owner().equals(player.getUuid()) || (owner != null && !PartyRules.canHarm(owner, player))) {
				return false;
			}
		}
		return true;
	}

	private static ServerPlayerEntity ownerOf(MinecraftServer server, Hijack hijack) {
		return server == null ? null : server.getPlayerManager().getPlayer(hijack.owner());
	}

	/** Uebernommener Mob dieses Spielers? Wird von Flaechentreffern verschont. */
	private static boolean isAlly(ServerPlayerEntity player, LivingEntity entity) {
		Hijack hijack = HIJACKS.get(entity.getUuid());
		return hijack != null && hijack.owner().equals(player.getUuid());
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Optikstrahl: legt Naniten; vernetzt durchschlagend und mit zwei Spruengen. */
	private static boolean opticBeam(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 24.0);
		boolean linked = sync(player) >= LINKED || overclocked(player);
		float damage = (float) ctx.param("damage", 7.0) * power(player);
		Vec3d eye = player.getEyePos();
		Vec3d stop = wallStop(world, player, eye, range);
		List<LivingEntity> hits = new ArrayList<>();
		if (linked) {
			hits.addAll(alongLine(world, player, eye, stop, 0.9));
		} else {
			Optional<LivingEntity> first = Targeting.findLivingTarget(player, eye.distanceTo(stop));
			if (first.isPresent() && PartyRules.canHarm(player, first.get()) && !isAlly(player, first.get())) {
				hits.add(first.get());
				stop = first.get().getPos().add(0.0, first.get().getHeight() * 0.5, 0.0);
			}
		}
		drawLine(world, muzzle(player), stop, linked ? NEON_BIG : NEON, 3.0);
		for (LivingEntity target : hits) {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().indirectMagic(player, player), damage);
			infect(world, target, 1);
			addSync(player, SYNC_BEAM);
		}
		if (linked && !hits.isEmpty()) {
			chain(world, player, hits.get(hits.size() - 1), damage * 0.5f, 2, 8.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GUARDIAN_ATTACK, 0.8f, linked ? 2.0f : 1.6f);
		return true;
	}

	/** Fluessigmetall: unverwundbar und schnell; jeder durchflossene Gegner wird verletzt, infiziert und laedt auf. */
	private static boolean liquidForm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) ctx.param("ticks", 40);
		ctx.grantInvulnerability(ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, ticks, 0, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 3, false, false));
		FLOWS.put(player.getUuid(), new Flow(ctx.world().getTime() + ticks, new HashSet<>()));
		for (MobEntity mob : ctx.world().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(16.0), m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
		ctx.world().spawnParticles(LIQUID, player.getX(), player.getY() + 0.1, player.getZ(), 40, 0.6, 0.05, 0.6, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, 1.0f, 0.6f);
		return true;
	}

	/**
	 * Techno-Fusion: verschmilzt mit dem Werkzeug in der Hand (repariert, Eile, Stromstoss bei jedem Treffer). Ohne
	 * Werkzeug verschmilzt Upgrade mit seiner Ruestung (repariert sie, Schutz und Absorption).
	 */
	private static boolean techFusion(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) Math.round(ctx.param("seconds", 20.0) * 20.0);
		double repair = ctx.param("repair", 0.25);
		ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);
		if (!stack.isEmpty() && stack.isDamageable()) {
			stack.setDamage(Math.max(0, stack.getDamage() - (int) Math.ceil(stack.getMaxDamage() * repair)));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, ticks, (int) ctx.param("haste_level", 1)));
			FUSION.put(player.getUuid(), world.getTime() + ticks);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.upgrade_done", stack.getName()).formatted(Formatting.GREEN), true);
		} else {
			int fused = 0;
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack armor = player.getEquippedStack(slot);
				if (!armor.isEmpty() && armor.isDamageable()) {
					armor.setDamage(Math.max(0, armor.getDamage() - (int) Math.ceil(armor.getMaxDamage() * repair)));
					fused++;
				}
			}
			if (fused == 0) {
				player.sendMessage(Text.translatable("message.kingdomomnitrix.upgrade_nothing").formatted(Formatting.GRAY), true);
				return false;
			}
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 0, false, false));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, ticks, fused - 1, false, false));
			player.sendMessage(Text.translatable("message.kingdomomnitrix.upgrade_armor").formatted(Formatting.GREEN), true);
		}
		addSync(player, SYNC_FUSION);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getBodyY(0.6), player.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
		world.spawnParticles(NEON, player.getX(), player.getBodyY(0.6), player.getZ(), 30, 0.4, 0.7, 0.4, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
		return true;
	}

	/** Morphwaffen: Arme werden zu Streitkolben — Treffer erzeugen Schockwellen und entladen Naniten. */
	private static boolean maceFists(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) Math.round(ctx.param("seconds", 12.0) * 20.0);
		MACES.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 1, false, false));
		ctx.world().spawnParticles(NEON_BIG, player.getX(), player.getBodyY(0.6), player.getZ(), 30, 0.6, 0.4, 0.6, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_PISTON_EXTEND, 1.0f, 0.7f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ANVIL_PLACE, 0.6f, 1.4f);
		return true;
	}

	/**
	 * Systemuebernahme: ein Mob wird fuer einige Sekunden zum Verbuendeten (+2 s je Nanit, Golems doppelt so lang).
	 * Bosse und Spieler: Systemstoerung (Langsamkeit, Schwaeche, Abbaulaehmung, Leuchten).
	 */
	private static boolean systemOverride(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 16.0), 0.8,
				e -> PartyRules.canHarm(player, e) && !isAlly(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		int stored = purge(target);
		int ticks = (int) Math.round((ctx.param("seconds", 6.0) + 2.0 * stored) * 20.0);
		drawLine(world, player.getEyePos().add(0, -0.2, 0), target.getPos().add(0, target.getHeight() * 0.6, 0), NEON, 4.0);
		if (target instanceof MobEntity mob && !(target instanceof PlayerEntity) && !target.getType().isIn(ConventionalEntityTypeTags.BOSSES)) {
			if (mob.getType() == EntityType.IRON_GOLEM || mob.getType() == EntityType.SNOW_GOLEM) {
				ticks *= 2;
			}
			HIJACKS.put(mob.getUuid(), new Hijack(player.getUuid(), world.getRegistryKey(), world.getTime() + ticks));
			mob.setTarget(null);
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ticks, 0, false, false));
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 1, false, false));
			player.sendMessage(Text.translatable("message.kingdomomnitrix.upgrade_override", mob.getName()).formatted(Formatting.GREEN), true);
		} else {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 3), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, ticks, 2), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ticks, 0), player);
		}
		addSync(player, 4.0f + 2.0f * stored);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getBodyY(0.6), target.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.6f);
		return true;
	}

	/**
	 * Plasmakanone: 1 s Aufladen (Upgrade steht fest), dann ein durchschlagender Strahl. Verbraucht die Integration fuer
	 * Schaden; im Overclock wird sie zur Omega-Kanone (dreifach breit, Detonationen entlang der Bahn, ohne Blockschaden).
	 */
	private static boolean plasmaCannon(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		boolean omega = overclocked(player);
		float spent = omega ? MAX : sync(player);
		float damage = (float) (ctx.param("damage", 16.0) * (1.0 + spent / 100.0) * (omega ? 1.5 : 1.0));
		if (omega) {
			OVERCLOCK.remove(player.getUuid());
		}
		SYNC.put(player.getUuid(), 0.0f);
		AlienMeterSync.update(player, AlienMeterPayload.SYNC, 0.0f);
		CHARGES.put(player.getUuid(), new Charge(world.getTime() + PLASMA_CHARGE, damage, ctx.param("range", 32.0), ctx.param("explosion", 2.0), omega));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, PLASMA_CHARGE, 5, false, false));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_AMBIENT, 1.5f, 2.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.6f);
		return true;
	}

	private static void firePlasma(ServerWorld world, ServerPlayerEntity player, Charge charge) {
		Vec3d eye = player.getEyePos();
		Vec3d stop = wallStop(world, player, eye, charge.range());
		double width = charge.omega() ? 2.4 : 1.0;
		for (LivingEntity target : alongLine(world, player, eye, stop, width)) {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().indirectMagic(player, player), charge.damage());
			infect(world, target, 2);
		}
		drawLine(world, muzzle(player), stop, charge.omega() ? CYAN : NEON_BIG, charge.omega() ? 6.0 : 4.0);
		if (charge.omega()) {
			Vec3d step = stop.subtract(eye);
			int blasts = Math.max(1, (int) (step.length() / 4.0));
			for (int i = 1; i <= blasts; i++) {
				Vec3d p = eye.add(step.multiply(i / (double) blasts));
				world.createExplosion(player, p.x, p.y, p.z, (float) charge.explosion(), World.ExplosionSourceType.NONE);
			}
		} else if (charge.explosion() > 0.0) {
			world.createExplosion(player, stop.x, stop.y, stop.z, (float) charge.explosion(), World.ExplosionSourceType.NONE);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.2f, charge.omega() ? 0.8f : 1.3f);
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			if (!isUpgrade(player)) {
				if (SYNC.containsKey(id) || OVERCLOCK.containsKey(id) || FLOWS.containsKey(id) || CHARGES.containsKey(id)
						|| FUSION.containsKey(id) || MACES.containsKey(id)) {
					if (SYNC.containsKey(id)) {
						AlienMeterSync.clear(player, AlienMeterPayload.SYNC);
					}
					forgetState(id);
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			tickOverclock(world, player, now);
			if (now % 20 == 0 && !overclocked(player)) {
				if (nearTech(world, player.getBlockPos())) {
					addSync(player, SYNC_TECH_BLOCK);
				} else if (sync(player) > 0.0f && now - LAST_GAIN.getOrDefault(id, 0L) > CALM_TICKS) {
					addSync(player, -DECAY);
				}
			}
			tickFlow(world, player, now);
			Charge charge = CHARGES.get(id);
			if (charge != null) {
				if (now >= charge.fireAt()) {
					CHARGES.remove(id);
					firePlasma(world, player, charge);
				} else {
					Vec3d muzzle = muzzle(player);
					world.spawnParticles(charge.omega() ? CYAN : NEON, muzzle.x, muzzle.y, muzzle.z, 6, 0.15, 0.15, 0.15, 0.0);
				}
			}
			if (MACES.getOrDefault(id, Long.MAX_VALUE) <= now) {
				MACES.remove(id);
			}
			if (FUSION.getOrDefault(id, Long.MAX_VALUE) <= now) {
				FUSION.remove(id);
			}
		}
		if (!HIJACKS.isEmpty()) {
			tickHijacks(server);
		}
		if (!NANITE_UNTIL.isEmpty() && server.getTicks() % 20 == 0) {
			long now = server.getOverworld().getTime();
			NANITE_UNTIL.entrySet().removeIf(e -> {
				if (e.getValue() <= now) {
					NANITES.remove(e.getKey());
					return true;
				}
				return false;
			});
		}
	}

	private static void tickOverclock(ServerWorld world, ServerPlayerEntity player, long now) {
		Long until = OVERCLOCK.get(player.getUuid());
		if (until == null) {
			return;
		}
		if (now >= until) {
			OVERCLOCK.remove(player.getUuid());
			SYNC.put(player.getUuid(), 0.0f);
			AlienMeterSync.update(player, AlienMeterPayload.SYNC, 0.0f);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.2f);
			return;
		}
		float left = MAX * (until - now) / (float) OVERCLOCK_TICKS;
		SYNC.put(player.getUuid(), left);
		AlienMeterSync.update(player, AlienMeterPayload.SYNC, left);
		if (now % 4 == 0) {
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getBodyY(0.5), player.getZ(), 3, 0.4, 0.7, 0.4, 0.1);
		}
	}

	private static void tickFlow(ServerWorld world, ServerPlayerEntity player, long now) {
		Flow flow = FLOWS.get(player.getUuid());
		if (flow == null) {
			return;
		}
		if (now >= flow.until()) {
			FLOWS.remove(player.getUuid());
			world.spawnParticles(LIQUID, player.getX(), player.getY() + 0.5, player.getZ(), 30, 0.4, 0.6, 0.4, 0.0);
			return;
		}
		world.spawnParticles(LIQUID, player.getX(), player.getY() + 0.05, player.getZ(), 4, 0.3, 0.02, 0.3, 0.0);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(0.6),
				e -> e != player && e.isAlive() && !flow.touched().contains(e.getUuid()) && PartyRules.canHarm(player, e) && !isAlly(player, e))) {
			flow.touched().add(target.getUuid());
			bonusHit = true;
			try {
				target.timeUntilRegen = 0;
				target.damage(world.getDamageSources().indirectMagic(player, player), 3.0f * power(player));
			} finally {
				bonusHit = false;
			}
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2), player);
			infect(world, target, 2);
			addSync(player, SYNC_FLOW);
			world.spawnParticles(NEON, target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.3, 0.5, 0.3, 0.0);
			world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_SLIME_BLOCK_STEP, SoundCategory.PLAYERS, 1.0f, 0.6f);
		}
	}

	private static void tickHijacks(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Hijack>> it = HIJACKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Hijack> entry = it.next();
			Hijack hijack = entry.getValue();
			ServerWorld world = server.getWorld(hijack.world());
			Entity entity = world == null ? null : world.getEntity(entry.getKey());
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(hijack.owner());
			if (!(entity instanceof MobEntity mob) || !mob.isAlive() || owner == null || world.getTime() >= hijack.until()) {
				if (entity instanceof MobEntity mob) {
					release(world, mob);
				}
				it.remove();
				continue;
			}
			LivingEntity target = mob.getTarget();
			boolean bad = target == null || !target.isAlive() || target == owner
					|| (target instanceof ServerPlayerEntity p && !PartyRules.canHarm(owner, p)) || isAlly(owner, target);
			if (bad && world.getTime() % 5 == 0) {
				mob.setTarget(world.getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(16.0),
								e -> e != mob && e.isAlive() && e != owner && !isAlly(owner, e) && (e instanceof Monster || mob.getAttacker() == e)
										&& PartyRules.canHarm(owner, e))
						.stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mob))).orElse(null));
			} else if (bad && target == owner) {
				mob.setTarget(null);
			}
			if (world.getTime() % 10 == 0) {
				world.spawnParticles(NEON, mob.getX(), mob.getBodyY(0.9), mob.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
			}
		}
	}

	private static void release(ServerWorld world, MobEntity mob) {
		mob.setTarget(null);
		mob.removeStatusEffect(StatusEffects.GLOWING);
		mob.removeStatusEffect(StatusEffects.STRENGTH);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getBodyY(0.6), mob.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
	}

	private static void releaseAll(MinecraftServer server, UUID owner) {
		for (Iterator<Map.Entry<UUID, Hijack>> it = HIJACKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Hijack> entry = it.next();
			if (!entry.getValue().owner().equals(owner)) {
				continue;
			}
			ServerWorld world = server.getWorld(entry.getValue().world());
			if (world != null && world.getEntity(entry.getKey()) instanceof MobEntity mob) {
				release(world, mob);
			}
			it.remove();
		}
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	/** Technik in der Naehe (3 Bloecke): Eisen, Kupfer, Redstone-Bauteile. */
	private static boolean nearTech(ServerWorld world, BlockPos center) {
		for (BlockPos pos : BlockPos.iterate(center.add(-2, -1, -2), center.add(2, 2, 2))) {
			BlockState state = world.getBlockState(pos);
			if (state.isOf(Blocks.IRON_BLOCK) || state.isOf(Blocks.REDSTONE_BLOCK) || state.isOf(Blocks.OBSERVER) || state.isOf(Blocks.PISTON)
					|| state.isOf(Blocks.STICKY_PISTON) || state.isOf(Blocks.REPEATER) || state.isOf(Blocks.COMPARATOR)
					|| state.isOf(Blocks.HOPPER) || state.isOf(Blocks.DISPENSER) || state.isOf(Blocks.DROPPER) || state.isOf(Blocks.IRON_BARS)
					|| state.isOf(Blocks.REDSTONE_LAMP) || state.isOf(Blocks.LIGHTNING_ROD) || state.isOf(Blocks.CRAFTER)
					|| state.isIn(BlockTags.RAILS) || state.isOf(Blocks.COPPER_BLOCK) || state.isOf(Blocks.CUT_COPPER)) {
				return true;
			}
		}
		return false;
	}

	private static Vec3d wallStop(ServerWorld world, ServerPlayerEntity player, Vec3d eye, double range) {
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return block.getType() == HitResult.Type.MISS ? end : block.getPos();
	}

	/** Alle schaedigbaren Wesen entlang der Strecke (Abstand zur Linie hoechstens {@code width} plus halbe Breite). */
	private static List<LivingEntity> alongLine(ServerWorld world, ServerPlayerEntity player, Vec3d from, Vec3d to, double width) {
		Vec3d line = to.subtract(from);
		double length = line.length();
		if (length < 1.0E-3) {
			return List.of();
		}
		Vec3d dir = line.multiply(1.0 / length);
		Box box = new Box(from, to).expand(width + 1.0);
		List<LivingEntity> result = new ArrayList<>();
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && !isAlly(player, e))) {
			Vec3d center = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0);
			double along = center.subtract(from).dotProduct(dir);
			if (along < 0.0 || along > length) {
				continue;
			}
			double off = center.distanceTo(from.add(dir.multiply(along)));
			if (off <= width + e.getWidth() * 0.5) {
				result.add(e);
			}
		}
		result.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(from)));
		return result;
	}

	/** Austrittspunkt der Strahlen: einen Block vor dem Gesicht, damit die Partikel in der Ich-Perspektive nicht die Sicht verdecken. */
	private static Vec3d muzzle(ServerPlayerEntity player) {
		return player.getEyePos().add(player.getRotationVec(1.0f)).add(0.0, -0.25, 0.0);
	}

	private static void drawLine(ServerWorld world, Vec3d from, Vec3d to, DustParticleEffect dust, double density) {
		Vec3d step = to.subtract(from);
		int points = Math.max(4, (int) (step.length() * density));
		for (int i = 1; i <= points; i++) {
			Vec3d p = from.add(step.multiply(i / (double) points));
			world.spawnParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private static void forgetState(UUID id) {
		SYNC.remove(id);
		LAST_GAIN.remove(id);
		OVERCLOCK.remove(id);
		FLOWS.remove(id);
		FUSION.remove(id);
		MACES.remove(id);
		CHARGES.remove(id);
	}
}
