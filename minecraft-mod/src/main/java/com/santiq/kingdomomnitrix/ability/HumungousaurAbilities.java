package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.vfx.EarthWave;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
	private static final int DUST = 0x9C8466;
	private static final int IMPACT = 0xFFE2A8;
	private static final int SKIN = 0xC8A070;

	private record Charge(long until, Vec3d direction, float damage, Set<UUID> rammed) {
	}

	private record Quake(Vec3d center, long start, double radius, float damage, double launch, Set<UUID> hit) {
	}

	private HumungousaurAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("mega_punch"), new MegaPunch());
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
		EarthWave.ring(world, player.getPos(), radius, 4, (float) Math.min(1.2, 0.25 * radius));
		Fx.shockwave(world, player.getPos().add(0, 0.1, 0), DUST, (float) radius, 10);
		Fx.smoke(world, player.getPos(), DUST, (float) (0.6 + radius * 0.1), (int) (2 + radius * 0.5));
		Fx.shake(world, player.getPos(), (float) Math.min(0.6, radius * 0.08), 8, (int) (radius * 4 + 8));
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

	/**
	 * Megaschlag (Halten laedt auf, bis 1,25 s): Faustschlag mit Druckwelle in Blickrichtung — trifft alles in der Linie
	 * und schlaegt einen Tunnel durch Waende und Felsen; Reichweite, Breite und Wucht wachsen mit Stufe und Ladung.
	 */
	private static final class MegaPunch implements ChargeableAbility {
		@Override
		public int chargeTicks(AbilityContext ctx) {
			return 25;
		}

		@Override
		public void charging(AbilityContext ctx, int ticks, float charge) {
			ServerPlayerEntity player = ctx.player();
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 3, 1, false, false));
			if (ticks % 4 == 0) {
				Fx.charge(ctx.world(), player, charge >= 1.0f ? 0xFF7A30 : IMPACT, (float) (0.8 + charge) * (float) size(player), 6);
			}
		}

		@Override
		public boolean release(AbilityContext ctx, float charge) {
			return megaPunch(ctx, charge);
		}
	}

	private static boolean megaPunch(AbilityContext ctx, float charge) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double size = size(player);
		double range = ctx.param("range", 6.0) * size * (1.0 + charge * 0.8);
		float damage = (float) (ctx.param("damage", 8.0) * size * (1.0 + charge * 0.75));
		double knockback = ctx.param("knockback", 1.6) * size * (1.0 + charge * 0.5);
		Vec3d look = player.getRotationVec(1.0f);
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
		// Tunnel: Faust und Druckwelle brechen durch alles bis zur Staerke
		float strength = AlienStrength.of(player) * (float) size * (1.0f + charge);
		Vec3d fist = player.getEyePos().add(look.multiply(0.8)).add(0, -0.4 * size, 0);
		Destruction.carve(world, player, fist, look, range, 0.9 + 0.35 * size + charge * 0.9, strength, 0.5);
		for (int i = 0; i < 4; i++) {
			Vec3d at = fist.add(look.multiply(range * (i + 1) / 4.0));
			Fx.send(world, Fx.Kind.SHOCKWAVE, at, look, IMPACT, (float) (1.0 + i * 0.6 + charge), 6 + i * 2, 1, null);
			Fx.smoke(world, at, DUST, (float) (0.6 + charge * 0.5), 2);
		}
		Fx.flash(world, fist.add(look.multiply(1.2)), IMPACT, (float) (1.4 + charge * 1.4));
		Fx.explosion(world, fist.add(look.multiply(range)), IMPACT, (float) (1.2 + charge * 2.0));
		Fx.shake(world, player.getPos(), 0.3f + charge * 0.4f, 8, 20);
		// Ultimate: Raketenfinger — fuenf Geschosse aus den Fingerspitzen
		if (com.santiq.kingdomomnitrix.alien.Evolution.isUltimate(player)) {
			for (int i = 0; i < 5; i++) {
				com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity.shoot(world, player, net.minecraft.item.Items.FIRE_CHARGE, 2.0f, 6.0f)
						.withDamage((float) ctx.param("missile_damage", 6.0)).withExplosion(1.4f);
			}
			BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4f, 0.7f);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.6f + charge * 0.6f, 1.4f - charge * 0.5f);
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
		// der Schwanz fegt auf Hueft-Hoehe alles weg, was im Kreis steht (Waende, Baeume, Zaeune)
		Destruction.crater(world, player, player.getPos().add(0, 0.5 + 0.3 * size, 0), radius * 0.8, 0.3, AlienStrength.of(player) * (float) size * 0.7f,
				0.0, 0.15);
		for (int i = 0; i < 3; i++) {
			Fx.shockwave(world, player.getPos().add(0, 0.6 + i * 0.4, 0), i == 1 ? SKIN : DUST, (float) (radius * (0.7 + i * 0.15)), 6 + i * 2);
		}
		Fx.sparks(world, player.getPos().add(0, 0.8, 0), IMPACT, 0.6f, 20);
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
		float strength = AlienStrength.of(player) * (float) size;
		double radius = ctx.param("radius", 10.0) * size;
		EarthWave.ring(ctx.world(), player.getPos(), radius, (int) radius, (float) Math.min(1.4, 0.5 + 0.2 * size));
		Destruction.crater(ctx.world(), player, player.getPos().add(0, -0.6, 0), 2.0 + size, 0.45, strength, 0.6, 0.4);
		for (int i = 0; i < 6; i++) {
			double a = i * MathHelper.TAU / 6 + ctx.world().random.nextDouble() * 0.6;
			Vec3d dir = new Vec3d(Math.cos(a), -0.06, Math.sin(a));
			Destruction.carve(ctx.world(), player, player.getPos().add(dir.multiply(2.5 + size)).add(0, -1.3, 0), dir, radius * 0.9, 1.1, strength, 1.0);
		}
		Fx.shake(ctx.world(), player.getPos(), 0.8f, 20, (int) radius * 3);
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
			// Fussabdruck: weicher Boden (Erde, Sand, Gras) gibt nach
			Destruction.crater(world, player, player.getPos().add(0, -0.4, 0), 1.6, 0.4, 1.0f, 0.0, 0.1);
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
		// rammt durch Waende: Schneise in Koerperhoehe und -breite vor dem Kopf
		double size = size(player);
		Destruction.carve(world, player, player.getPos().add(charge.direction().multiply(0.6 + 0.4 * size)).add(0, player.getHeight() * 0.5, 0),
				charge.direction(), 1.4, Math.max(0.9, player.getWidth() * 0.75), AlienStrength.of(player) * (float) size, 0.0);
		if (now % 3 == 0) {
			Fx.smoke(world, player.getPos(), DUST, 0.9f, 2);
			Fx.shockwave(world, player.getPos().add(0, 0.1, 0), DUST, 1.8f, 6);
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
		if (age % 3 == 0) {
			Fx.shockwave(world, quake.center().add(0, 0.1, 0), DUST, (float) front + 1.0f, 6);
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
