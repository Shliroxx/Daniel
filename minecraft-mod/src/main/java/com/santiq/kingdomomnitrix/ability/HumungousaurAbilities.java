package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
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
 * Humungosaur (Vaxasaurian). Eigenes System „Wachstum“ — ohne Aura, man sieht es an der Groesse:
 *
 * <ul>
 *   <li>Wachstum 0–100 steigt mit ausgeteiltem und (staerker) eingestecktem Schaden; nach 8 s ohne Kampf schrumpft
 *       Humungosaur langsam wieder.</li>
 *   <li>Drei <b>Wachstumsstufen</b> (bei 34/67/100): je Stufe groesser (echte Groesse inkl. Trefferbox und Reichweite),
 *       staerker, gepanzerter, standfester — dafuer etwas langsamer. Jeder Stufenwechsel bebt.</li>
 *   <li>Riesenwuchs (Ultimative): sofort volle Stufe und darueber hinaus riesig; jeder Schritt erschuettert den Boden.</li>
 * </ul>
 * Alle Faehigkeiten wachsen mit der Stufe (Reichweite, Schaden, Rueckstoss).
 */
final class HumungousaurAbilities {
	static final float MAX = 100.0f;
	private static final Identifier HUMUNGOUSAUR = KingdomOmnitrix.id("humungousaur");
	private static final float[] STAGE_AT = {34.0f, 67.0f, 100.0f};
	private static final float GROWTH_DEALT = 0.6f;
	private static final float GROWTH_TAKEN = 1.2f;
	private static final int CALM_TICKS = 160;
	private static final float SHRINK = 2.0f;
	/** Zuwachs je Stufe */
	private static final double SCALE_PER_STAGE = 0.12;
	private static final double DAMAGE_PER_STAGE = 2.0;
	private static final double ARMOR_PER_STAGE = 2.0;
	private static final double KNOCKBACK_PER_STAGE = 0.15;
	private static final double SPEED_PER_STAGE = -0.03;
	private static final double TITAN_SCALE = 0.6;
	private static final Identifier SCALE_ID = KingdomOmnitrix.id("humungousaur_growth_scale");
	private static final Identifier DAMAGE_ID = KingdomOmnitrix.id("humungousaur_growth_damage");
	private static final Identifier ARMOR_ID = KingdomOmnitrix.id("humungousaur_growth_armor");
	private static final Identifier KNOCKBACK_ID = KingdomOmnitrix.id("humungousaur_growth_knockback");
	private static final Identifier SPEED_ID = KingdomOmnitrix.id("humungousaur_growth_speed");
	private static final Identifier REACH_ID = KingdomOmnitrix.id("humungousaur_growth_reach");

	private static final Map<UUID, Float> GROWTH = new HashMap<>();
	private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
	/** angewandte Stufe (fuer Stufenwechsel) */
	private static final Map<UUID, Integer> STAGE = new HashMap<>();
	/** Stampede: Ende, Richtung, schon gerammte Gegner */
	private static final Map<UUID, Charge> CHARGES = new HashMap<>();
	/** Erdbeben-Wellen */
	private static final Map<UUID, Quake> QUAKES = new HashMap<>();
	/** Panzerblock bis */
	private static final Map<UUID, Long> GUARD = new HashMap<>();
	/** Riesenwuchs bis */
	private static final Map<UUID, Long> TITAN = new HashMap<>();
	/** letzte Position fuer Schritt-Beben */
	private static final Map<UUID, Vec3d> LAST_STEP = new HashMap<>();
	/** verhindert, dass Bonus-Schaden erneut Bonus-Schaden ausloest */
	private static boolean bonusHit;

	private record Charge(long until, Vec3d direction, float damage, Set<UUID> rammed) {
	}

	private record Quake(Vec3d center, long start, double radius, float damage, double launch, Set<UUID> hit) {
	}

	private HumungousaurAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("mega_punch"), HumungousaurAbilities::megaPunch);
		AbilityRegistry.register(KingdomOmnitrix.id("tail_sweep"), HumungousaurAbilities::tailSweep);
		AbilityRegistry.register(KingdomOmnitrix.id("stampede"), HumungousaurAbilities::stampede);
		AbilityRegistry.register(KingdomOmnitrix.id("tectonic_quake"), HumungousaurAbilities::tectonicQuake);
		AbilityRegistry.register(KingdomOmnitrix.id("bone_guard"), HumungousaurAbilities::boneGuard);
		AbilityRegistry.register(KingdomOmnitrix.id("titanic_growth"), HumungousaurAbilities::titanicGrowth);
		ServerTickEvents.END_SERVER_TICK.register(HumungousaurAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			removeModifiers(handler.getPlayer());
			forgetState(handler.getPlayer().getUuid());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken <= 0.0f || bonusHit) {
				return;
			}
			if (target instanceof ServerPlayerEntity victim && isHumungousaur(victim)) {
				addGrowth(victim, taken * GROWTH_TAKEN);
				if (GUARD.containsKey(victim.getUuid())) {
					counter(victim, source);
				}
			}
			if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != target && isHumungousaur(attacker)) {
				addGrowth(attacker, taken * GROWTH_DEALT);
			}
		});
	}

	// --- Wachstum --------------------------------------------------------------------------------

	private static boolean isHumungousaur(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(HUMUNGOUSAUR::equals).isPresent();
	}

	static float growth(ServerPlayerEntity player) {
		return GROWTH.getOrDefault(player.getUuid(), 0.0f);
	}

	static int stage(float growth) {
		int stage = 0;
		for (float at : STAGE_AT) {
			if (growth >= at) {
				stage++;
			}
		}
		return stage;
	}

	private static int stage(ServerPlayerEntity player) {
		return stage(growth(player));
	}

	/** Faktor fuer Reichweite/Schaden der Faehigkeiten: Stufe 0 = 1, Stufe 3 = 1,6; Riesenwuchs 2. */
	private static double size(ServerPlayerEntity player) {
		if (com.santiq.kingdomomnitrix.alien.Evolution.isUltimate(player)) {
			return TITAN.containsKey(player.getUuid()) ? 2.4 : 1.8;
		}
		return TITAN.containsKey(player.getUuid()) ? 2.0 : 1.0 + 0.2 * stage(player);
	}

	private static void addGrowth(ServerPlayerEntity player, float amount) {
		if (TITAN.containsKey(player.getUuid()) && amount < 0.0f) {
			return;
		}
		float after = MathHelper.clamp(growth(player) + amount, 0.0f, MAX);
		GROWTH.put(player.getUuid(), after);
		if (amount > 0.0f) {
			LAST_COMBAT.put(player.getUuid(), player.getServerWorld().getTime());
		}
		applyStage(player);
	}

	/** Attribute an die Stufe anpassen; bei Wechsel Beben und Meldung. */
	private static void applyStage(ServerPlayerEntity player) {
		int stage = stage(player);
		boolean titan = TITAN.containsKey(player.getUuid());
		int before = STAGE.getOrDefault(player.getUuid(), 0);
		int key = titan ? 4 : stage;
		if (before == key) {
			return;
		}
		STAGE.put(player.getUuid(), key);
		double scale = SCALE_PER_STAGE * stage + (titan ? TITAN_SCALE : 0.0);
		set(player, EntityAttributes.GENERIC_SCALE, SCALE_ID, scale, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE_ID, DAMAGE_PER_STAGE * stage + (titan ? 4.0 : 0.0), EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.GENERIC_ARMOR, ARMOR_ID, ARMOR_PER_STAGE * stage, EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID, titan ? 1.0 : KNOCKBACK_PER_STAGE * stage,
				EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, SPEED_PER_STAGE * stage, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, REACH_ID, 0.5 * stage + (titan ? 2.0 : 0.0), EntityAttributeModifier.Operation.ADD_VALUE);
		ServerWorld world = player.getServerWorld();
		if (key > before) {
			stomp(world, player, 2.5 * size(player), 0.0f, 0.4);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.2f, 0.7f - 0.05f * key);
			player.sendMessage((key >= 4 ? Text.translatable("message.kingdomomnitrix.humungousaur_titan")
					: Text.translatable("message.kingdomomnitrix.humungousaur_stage", key, STAGE_AT.length)).formatted(Formatting.GOLD), true);
		} else {
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.PLAYERS, 1.0f, 1.2f);
		}
	}

	private static void set(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double value,
			EntityAttributeModifier.Operation operation) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(id);
		if (value != 0.0) {
			instance.addTemporaryModifier(new EntityAttributeModifier(id, value, operation));
		}
	}

	private static void removeModifiers(ServerPlayerEntity player) {
		set(player, EntityAttributes.GENERIC_SCALE, SCALE_ID, 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE_ID, 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.GENERIC_ARMOR, ARMOR_ID, 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, REACH_ID, 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
	}

	// --- Bausteine -------------------------------------------------------------------------------

	/** Bodenstoss um den Spieler: Erdbrocken, Schaden und Anheben fuer Bodennahe. */
	private static void stomp(ServerWorld world, ServerPlayerEntity player, double radius, float damage, double launch) {
		BlockState ground = world.getBlockState(player.getBlockPos().down());
		if (!ground.isAir()) {
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(),
					(int) (12 * radius), radius * 0.4, 0.1, radius * 0.4, 0.2);
		}
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius, 1.0, radius),
				e -> e != player && e.isAlive() && e.isOnGround() && PartyRules.canHarm(player, e))) {
			if (damage > 0.0f) {
				hit(world, player, target, damage);
			}
			Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			away = away.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : away.normalize().multiply(0.4);
			target.addVelocity(away.x, launch, away.z);
			target.velocityModified = true;
		}
	}

	private static void hit(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float damage) {
		bonusHit = true;
		try {
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().playerAttack(player), damage);
		} finally {
			bonusHit = false;
		}
		addGrowth(player, damage * GROWTH_DEALT);
	}

	/** Panzerblock: Nahkampf-Angreifer werden zurueckgeworfen und verletzt. */
	private static void counter(ServerPlayerEntity player, DamageSource source) {
		if (!(source.getAttacker() instanceof LivingEntity attacker) || source.getSource() != attacker || !PartyRules.canHarm(player, attacker)) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		hit(world, player, attacker, (float) (3.0 * size(player)));
		Vec3d away = attacker.getPos().subtract(player.getPos()).multiply(1, 0, 1);
		if (away.lengthSquared() > 1.0E-4) {
			away = away.normalize().multiply(1.2 * size(player));
			attacker.addVelocity(away.x, 0.4, away.z);
			attacker.velocityModified = true;
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.6f);
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Megaschlag: Druckwelle in Blickrichtung — trifft alles in der Linie, Reichweite und Wucht wachsen mit der Stufe. */
	private static boolean megaPunch(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double size = size(player);
		double range = ctx.param("range", 6.0) * size;
		float damage = (float) (ctx.param("damage", 8.0) * size);
		double knockback = ctx.param("knockback", 1.6) * size;
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		Vec3d origin = player.getPos().add(0, player.getHeight() * 0.5, 0);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, range + 1.0)) {
			Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(origin);
			double along = to.dotProduct(dir);
			if (along < 0.0 || along > range || to.subtract(dir.multiply(along)).length() > 1.2 + 0.3 * size + target.getWidth() * 0.5) {
				continue;
			}
			hit(world, player, target, damage);
			target.addVelocity(dir.x * knockback, 0.35 + 0.1 * size, dir.z * knockback);
			target.velocityModified = true;
		}
		BlockState ground = world.getBlockState(player.getBlockPos().down());
		for (int i = 1; i <= (int) range; i++) {
			Vec3d p = player.getPos().add(dir.multiply(i));
			world.spawnParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.8, p.z, 1, 0.1, 0.1, 0.1, 0.0);
			if (!ground.isAir()) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), p.x, p.y + 0.1, p.z, 8, 0.3, 0.05, 0.3, 0.15);
			}
		}
		// Ultimate: Raketenfinger — fuenf Geschosse aus den Fingerspitzen, explodieren ohne Blockschaden
		if (com.santiq.kingdomomnitrix.alien.Evolution.isUltimate(player)) {
			for (int i = 0; i < 5; i++) {
				com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity.shoot(world, player, net.minecraft.item.Items.FIRE_CHARGE, 2.0f, 6.0f)
						.withDamage((float) ctx.param("missile_damage", 6.0)).withExplosion(1.4f);
			}
			BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4f, 0.7f);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.6f, 1.4f);
		return true;
	}

	/** Schwanzfeger: Rundumschlag, schleudert alles nach aussen; kleine Gegner fliegen hoch. */
	private static boolean tailSweep(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double size = size(player);
		double radius = ctx.param("radius", 4.0) * size;
		float damage = (float) (ctx.param("damage", 6.0) * size);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			hit(world, player, target, damage);
			Vec3d away = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			if (away.lengthSquared() > 1.0E-4) {
				away = away.normalize().multiply(ctx.param("knockback", 1.4) * size);
				double lift = target.getHeight() < player.getHeight() * 0.6 ? 0.7 : 0.3;
				target.addVelocity(away.x, lift, away.z);
				target.velocityModified = true;
			}
		}
		for (int i = 0; i < 32; i++) {
			double a = i * MathHelper.TAU / 32;
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(a) * radius * 0.7, player.getY() + 0.6,
					player.getZ() + Math.sin(a) * radius * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
		return true;
	}

	/** Stampede: Sturmlauf nach vorn — rammt alles beiseite (Schaden, Wegschleudern, Taumeln). */
	private static boolean stampede(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) ctx.param("ticks", 20);
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		CHARGES.put(player.getUuid(), new Charge(ctx.world().getTime() + ticks, dir, (float) (ctx.param("damage", 7.0) * size(player)),
				new HashSet<>()));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 1, false, false));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 0.9f);
		return true;
	}

	/** Tektonikbeben: Ringwelle laeuft nach aussen; jeder Bodennahe wird geschleudert, verletzt und verlangsamt. */
	private static boolean tectonicQuake(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double size = size(player);
		QUAKES.put(player.getUuid(), new Quake(player.getPos(), ctx.world().getTime(), ctx.param("radius", 10.0) * size,
				(float) (ctx.param("damage", 7.0) * size), ctx.param("launch", 0.9), new HashSet<>()));
		stomp(ctx.world(), player, 2.0, 0.0f, 0.2);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_EMERGE, 1.2f, 0.7f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0f, 0.5f);
		return true;
	}

	/** Panzerblock: Panzerplatten zu — kaum Schaden, kein Rueckstoss, Angreifer prallen ab; Treffer lassen wachsen. */
	private static boolean boneGuard(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) Math.round(ctx.param("seconds", 6.0) * 20.0);
		GUARD.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 2, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 1, false, false));
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.4f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ANVIL_PLACE, 0.6f, 0.6f);
		return true;
	}

	/** Riesenwuchs: sofort volle Stufe und darueber hinaus riesig; jeder Schritt bebt. Danach bleibt Stufe 2. */
	private static boolean titanicGrowth(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) Math.round(ctx.param("seconds", 15.0) * 20.0);
		TITAN.put(player.getUuid(), ctx.world().getTime() + ticks);
		GROWTH.put(player.getUuid(), MAX);
		LAST_COMBAT.put(player.getUuid(), ctx.world().getTime());
		applyStage(player);
		player.heal(player.getMaxHealth() * 0.3f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.6f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			if (!isHumungousaur(player)) {
				if (STAGE.containsKey(id) || GROWTH.containsKey(id)) {
					removeModifiers(player);
					forgetState(id);
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			if (!STAGE.containsKey(id)) {
				STAGE.put(id, 0);
			}
			if (now % 20 == 0 && !TITAN.containsKey(id) && growth(player) > 0.0f && now - LAST_COMBAT.getOrDefault(id, 0L) > CALM_TICKS) {
				addGrowth(player, -SHRINK);
			}
			tickTitan(world, player, now);
			tickCharge(world, player, now);
			tickQuake(world, player, now);
			Long guard = GUARD.get(id);
			if (guard != null && now >= guard) {
				GUARD.remove(id);
			}
		}
	}

	private static void tickTitan(ServerWorld world, ServerPlayerEntity player, long now) {
		Long until = TITAN.get(player.getUuid());
		if (until == null) {
			return;
		}
		if (now >= until) {
			TITAN.remove(player.getUuid());
			GROWTH.put(player.getUuid(), STAGE_AT[1]);
			applyStage(player);
			return;
		}
		// jeder Schritt bebt
		Vec3d last = LAST_STEP.get(player.getUuid());
		if (player.isOnGround() && (last == null || last.squaredDistanceTo(player.getPos()) > 4.0)) {
			LAST_STEP.put(player.getUuid(), player.getPos());
			stomp(world, player, 3.5, 3.0f, 0.35);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.PLAYERS, 1.5f, 0.5f);
		}
	}

	private static void tickCharge(ServerWorld world, ServerPlayerEntity player, long now) {
		Charge charge = CHARGES.get(player.getUuid());
		if (charge == null) {
			return;
		}
		if (now >= charge.until()) {
			CHARGES.remove(player.getUuid());
			return;
		}
		double speed = 0.7 + 0.1 * stage(player);
		player.setVelocity(charge.direction().x * speed, player.getVelocity().y, charge.direction().z * speed);
		player.velocityModified = true;
		if (now % 3 == 0) {
			BlockState ground = world.getBlockState(player.getBlockPos().down());
			if (!ground.isAir()) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(),
						10, 0.5, 0.05, 0.5, 0.2);
			}
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.PLAYERS, 1.0f, 0.8f);
		}
		double reach = 1.0 + 0.3 * size(player);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(reach, 0.5, reach),
				e -> e != player && e.isAlive() && !charge.rammed().contains(e.getUuid()) && PartyRules.canHarm(player, e))) {
			charge.rammed().add(target.getUuid());
			hit(world, player, target, charge.damage());
			// zur Seite und hoch geschleudert
			Vec3d side = new Vec3d(-charge.direction().z, 0, charge.direction().x);
			if (side.dotProduct(target.getPos().subtract(player.getPos())) < 0) {
				side = side.multiply(-1);
			}
			target.setVelocity(side.multiply(1.1).add(charge.direction().multiply(0.6)).add(0, 0.6, 0));
			target.velocityModified = true;
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2), player);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.2f, 0.6f);
		}
	}

	private static void tickQuake(ServerWorld world, ServerPlayerEntity player, long now) {
		Quake quake = QUAKES.get(player.getUuid());
		if (quake == null) {
			return;
		}
		long age = now - quake.start();
		double front = Math.min(quake.radius(), age * 1.0);
		// Erdbrocken auf der Wellenfront
		int points = Math.max(8, (int) (front * 6));
		BlockState ground = world.getBlockState(BlockPos.ofFloored(quake.center()).down());
		for (int i = 0; i < points; i++) {
			double a = i * MathHelper.TAU / points;
			double x = quake.center().x + Math.cos(a) * front;
			double z = quake.center().z + Math.sin(a) * front;
			if (!ground.isAir()) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), x, quake.center().y + 0.2, z, 2, 0.1, 0.1, 0.1, 0.15);
			}
		}
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class,
				new net.minecraft.util.math.Box(quake.center(), quake.center()).expand(front + 0.5, 2.0, front + 0.5),
				e -> e != player && e.isAlive() && e.isOnGround() && !quake.hit().contains(e.getUuid()) && PartyRules.canHarm(player, e)
						&& e.getPos().multiply(1, 0, 1).distanceTo(quake.center().multiply(1, 0, 1)) <= front + 0.5)) {
			quake.hit().add(target.getUuid());
			hit(world, player, target, quake.damage());
			target.addVelocity(0, quake.launch(), 0);
			target.velocityModified = true;
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2), player);
		}
		if (age % 4 == 0) {
			world.playSound(null, BlockPos.ofFloored(quake.center()), SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.PLAYERS, 1.5f, 0.5f);
		}
		if (front >= quake.radius()) {
			QUAKES.remove(player.getUuid());
		}
	}

	private static void forgetState(UUID id) {
		GROWTH.remove(id);
		LAST_COMBAT.remove(id);
		STAGE.remove(id);
		CHARGES.remove(id);
		QUAKES.remove(id);
		GUARD.remove(id);
		TITAN.remove(id);
		LAST_STEP.remove(id);
	}
}
