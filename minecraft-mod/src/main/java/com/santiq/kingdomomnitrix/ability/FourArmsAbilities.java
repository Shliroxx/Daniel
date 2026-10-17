package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Debris;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.vfx.EarthWave;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
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

/**
 * Vierarm (Tetramand). Eigenes Dauer-System „Wut“ (0–100) und Vier-Schlag-Kombo:
 *
 * <ul>
 *   <li>Wut steigt mit ausgeteiltem und noch staerker mit eingestecktem Schaden; ohne Kampf (4 s) faellt sie ab.</li>
 *   <li>Ab {@link #ANGRY} <b>Zorn</b>: Schlaege mit mehr Wucht, jeder vierte Schlag der Kombo ist ein Vierfach-Schlag.</li>
 *   <li>Bei {@link #MAX} <b>Raserei</b>: die naechste Faehigkeit verbraucht die Wut fuer ihre groesste Form.</li>
 *   <li>Vier-Schlag-Kombo: vier Nahkampftreffer kurz hintereinander — der vierte trifft mit allen vier Faeusten
 *       (Zusatzschaden, weiter Rueckstoss, Bodenstoss).</li>
 * </ul>
 * Bodenangriffe sind laufende Druckwellen (Ring aus Erdbrocken, der nach aussen waechst und jeden einmal trifft).
 * Anzeige ueber {@link AlienMeterPayload#RAGE} (Tacho im HUD, rote Aura). Kein Blockschaden.
 */
final class FourArmsAbilities {
	static final float MAX = 100.0f;
	private static final int RED = 0xFF3020;
	private static final int IMPACT = 0xFFD890;
	private static final int DUST = 0xA08A6A;
	private static final int CLAP = 0xE8F0FF;
	private static final int IRON = 0xC8D0DC;
	static final float ANGRY = 50.0f;
	private static final Identifier FOUR_ARMS = KingdomOmnitrix.id("four_arms");
	/** Wut je Schadenspunkt: ausgeteilt / eingesteckt */
	private static final float RAGE_DEALT = 0.45f;
	private static final float RAGE_TAKEN = 4.0f;
	/** ohne Kampf so viele Ticks, dann faellt die Wut um {@link #RAGE_DECAY} je Sekunde */
	private static final int CALM_TICKS = 80;
	private static final float RAGE_DECAY = 6.0f;
	/** Kombo: Abstand zwischen zwei Schlaegen hoechstens, Schlaege bis zum Vierfach-Schlag */
	private static final int COMBO_WINDOW = 30;
	private static final int COMBO_HITS = 4;
	private static final Identifier SKIN_KNOCKBACK = KingdomOmnitrix.id("four_arms_iron_skin");

	private static final Map<UUID, Float> RAGE = new HashMap<>();
	private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
	private static final Map<UUID, long[]> COMBO = new HashMap<>();
	private static final Map<UUID, Long> IRON_SKIN = new HashMap<>();
	private static final Map<UUID, Leap> LEAPS = new HashMap<>();
	private static final List<Wave> WAVES = new ArrayList<>();
	private static final Map<UUID, Quake> QUAKES = new HashMap<>();
	private static final Map<UUID, Thrown> THROWN = new HashMap<>();

	/** Druckwelle: Ring waechst in {@code ticks} auf {@code radius}, trifft jeden einmal. */
	private static final class Wave {
		final UUID owner;
		final Vec3d center;
		final double radius;
		final float damage;
		final double launch;
		final long start;
		final int ticks;
		final double cone;
		final Vec3d facing;
		final Set<UUID> hit = new HashSet<>();

		Wave(UUID owner, Vec3d center, double radius, float damage, double launch, long start, int ticks, double cone, Vec3d facing) {
			this.owner = owner;
			this.center = center;
			this.radius = radius;
			this.damage = damage;
			this.launch = launch;
			this.start = start;
			this.ticks = ticks;
			this.cone = cone;
			this.facing = facing;
		}
	}

	private record Leap(double startY, float damage, double radius, long until, boolean rampage) {
	}

	private record Quake(long start, int waves, double radius, float damage, double launch) {
	}

	private record Thrown(UUID thrower, float damage, long until) {
	}

	private FourArmsAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("throw"), FourArmsAbilities::grabThrow);
		AbilityRegistry.register(KingdomOmnitrix.id("ground_slam"), new GroundSlam());
		AbilityRegistry.register(KingdomOmnitrix.id("titan_leap"), FourArmsAbilities::titanLeap);
		AbilityRegistry.register(KingdomOmnitrix.id("thunder_clap"), FourArmsAbilities::thunderClap);
		AbilityRegistry.register(KingdomOmnitrix.id("iron_skin"), FourArmsAbilities::ironSkin);
		AbilityRegistry.register(KingdomOmnitrix.id("earthquake"), FourArmsAbilities::earthquake);
		ServerTickEvents.END_SERVER_TICK.register(FourArmsAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			forgetState(handler.getPlayer().getUuid());
			AlienMeterSync.forget(handler.getPlayer().getUuid());
		});
		// Wut und Vier-Schlag-Kombo sind passive Kraefte: Gegner-Schaden, eigener Schaden und Schlaege kommen ueber
		// PassiveHooks (nur wenn freigeschaltet)
		com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry.register(KingdomOmnitrix.id("tetramand_rage"),
				new com.santiq.kingdomomnitrix.ability.passive.AlienPassive() {
					@Override
					public void afterHit(com.santiq.kingdomomnitrix.ability.passive.PassiveContext ctx, LivingEntity target, DamageSource source, float damage) {
						addRage(ctx.player(), damage * RAGE_DEALT);
					}

					@Override
					public void afterHurt(com.santiq.kingdomomnitrix.ability.passive.PassiveContext ctx, DamageSource source, float damage) {
						hurt(ctx.player(), source, damage);
					}
				});
		com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry.register(KingdomOmnitrix.id("four_fist_combo"),
				new com.santiq.kingdomomnitrix.ability.passive.AlienPassive() {
					@Override
					public void melee(com.santiq.kingdomomnitrix.ability.passive.PassiveContext ctx, LivingEntity target, boolean fullSwing) {
						combo(ctx.player(), target);
					}
				});
		com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry.register(KingdomOmnitrix.id("tetramand_hide"),
				new com.santiq.kingdomomnitrix.ability.passive.AlienPassive() {
					@Override
					public float incoming(com.santiq.kingdomomnitrix.ability.passive.PassiveContext ctx, DamageSource source, float amount) {
						// dicke Tetramand-Haut: Geschosse und Explosionen prallen ab, alles andere etwas weniger
						if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_PROJECTILE)) {
							return amount * (float) ctx.param("projectile", 0.7);
						}
						if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)) {
							return amount * (float) ctx.param("explosion", 0.75);
						}
						return amount * (float) ctx.param("other", 0.88);
					}
				});
		FourArmsMoves.register();
	}

	// --- Wut -------------------------------------------------------------------------------------

	private static boolean isFourArms(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(FOUR_ARMS::equals).isPresent();
	}

	static float rage(ServerPlayerEntity player) {
		return RAGE.getOrDefault(player.getUuid(), 0.0f);
	}

	private static void addRage(ServerPlayerEntity player, float amount) {
		float before = rage(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		RAGE.put(player.getUuid(), after);
		if (amount > 0.0f) {
			LAST_COMBAT.put(player.getUuid(), player.getServerWorld().getTime());
		}
		if (before < MAX && after >= MAX) {
			ServerWorld world = player.getServerWorld();
			Fx.aura(world, player, RED, 1.6f, 60);
			Fx.flash(world, player.getPos().add(0, 1.2, 0), RED, 3.0f);
			Fx.shockwave(world, player.getPos().add(0, 0.1, 0), RED, 5.0f, 12);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.2f, 0.7f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.four_arms_rampage").formatted(Formatting.RED), true);
		}
		AlienMeterSync.update(player, AlienMeterPayload.RAGE, after);
	}

	static Power power(ServerPlayerEntity player) {
		float rage = rage(player);
		if (rage >= MAX) {
			RAGE.put(player.getUuid(), 0.0f);
			AlienMeterSync.update(player, AlienMeterPayload.RAGE, 0.0f);
			return new Power(1.8f, true);
		}
		return new Power(1.0f + (rage >= ANGRY ? 0.25f : 0.0f), false);
	}

	record Power(float factor, boolean rampage) {
	}

	/** Eingesteckter Schaden fuellt die Wut; Eisenhaut verdoppelt das und wirft einen Teil auf Nahkaempfer zurueck. */
	private static void hurt(ServerPlayerEntity victim, DamageSource source, float damageTaken) {
		boolean skin = IRON_SKIN.containsKey(victim.getUuid());
		addRage(victim, damageTaken * RAGE_TAKEN * (skin ? 2.0f : 1.0f));
		if (skin && source.getAttacker() instanceof LivingEntity attacker && attacker != victim
				&& attacker.squaredDistanceTo(victim) < 16.0 && PartyRules.canHarm(victim, attacker)) {
			attacker.damage(victim.getServerWorld().getDamageSources().thorns(victim), damageTaken * 0.3f);
		}
	}

	// --- Schnittstelle fuer FourArmsMoves -----------------------------------------------------------

	/** Laufende Druckwelle (Ring oder Kegel), trifft jeden einmal. */
	static void wave(ServerPlayerEntity player, Vec3d center, double radius, float damage, double launch, int ticks) {
		WAVES.add(new Wave(player.getUuid(), center, radius, damage, launch, player.getServerWorld().getTime(), ticks, 0.0, Vec3d.ZERO));
	}

	/** Geworfenes Ziel: schlaegt beim Aufprall (Boden, Wand, anderes Wesen) in die Umgebung ein. */
	static void thrown(ServerPlayerEntity thrower, LivingEntity target, float impactDamage, int ticks) {
		THROWN.put(target.getUuid(), new Thrown(thrower.getUuid(), impactDamage, thrower.getServerWorld().getTime() + ticks));
	}

	/** Landung mit Einschlag: Wucht nach Fallhoehe ueber {@code startY + 6}. */
	static void landing(ServerPlayerEntity player, double startY, float damage, double radius, boolean rampage) {
		LEAPS.put(player.getUuid(), new Leap(startY, damage, radius, player.getServerWorld().getTime() + 100, rampage));
	}

	static float gainRage(ServerPlayerEntity player, float amount) {
		addRage(player, amount);
		return rage(player);
	}

	/** Vier-Schlag-Kombo: zaehlt Nahkampftreffer; der vierte ist ein Vierfach-Schlag. */
	private static void combo(ServerPlayerEntity player, LivingEntity target) {
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		long[] combo = COMBO.computeIfAbsent(player.getUuid(), id -> new long[2]);
		combo[0] = now - combo[1] <= COMBO_WINDOW ? combo[0] + 1 : 1;
		combo[1] = now;
		float rage = rage(player);
		Vec3d push = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
		Vec3d dir = push.lengthSquared() < 1.0E-4 ? BuiltinAbilities.horizontalLook(player) : push.normalize();
		if (rage >= ANGRY) {
			target.takeKnockback(0.5, -dir.x, -dir.z);
		}
		if (combo[0] >= COMBO_HITS) {
			combo[0] = 0;
			float damage = 6.0f + rage / 10.0f;
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().playerAttack(player), damage);
			target.takeKnockback(2.0, -dir.x, -dir.z);
			target.addVelocity(0.0, 0.45, 0.0);
			target.velocityModified = true;
			Fx.flash(world, new Vec3d(target.getX(), target.getBodyY(0.5), target.getZ()), IMPACT, 2.6f);
			Fx.shockwave(world, target.getPos().add(0, 0.1, 0), IMPACT, 3.5f, 8);
			Fx.smoke(world, target.getPos(), DUST, 1.4f, 6);
			EarthWave.ring(world, target.getPos(), 3.0, 4, 0.5f);
			Destruction.crater(world, player, target.getPos().add(0, -0.9, 0), 1.3, AlienStrength.of(player) * 0.8f, 0.0, 0.3);
			Fx.shake(world, target.getPos(), 0.35f, 8, 16);
			world.playSound(null, target.getBlockPos(), ModSounds.ALIEN_SLAM, SoundCategory.PLAYERS, 1.2f, 0.8f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.four_arms_combo").formatted(Formatting.GOLD), true);
		} else {
			Fx.sparks(world, new Vec3d(target.getX(), target.getBodyY(0.6), target.getZ()), IMPACT, 0.4f, 4 + (int) combo[0] * 3);
			Fx.flash(world, new Vec3d(target.getX(), target.getBodyY(0.6), target.getZ()), IMPACT, 1.0f + combo[0] * 0.3f);
		}
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Packen und Werfen: das Ziel wird zum Geschoss und trifft beim Aufprall alles drumherum. Zu gross: Ringer-Wurf. */
	private static boolean grabThrow(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findLivingTarget(player, ctx.param("range", 5.0));
		if (found.isEmpty() || !PartyRules.canHarm(player, found.get())) {
			// kein Ziel: wie in der Serie einen Brocken aus dem Boden reissen und werfen
			return boulder(ctx);
		}
		LivingEntity target = found.get();
		Power power = power(player);
		ServerWorld world = ctx.world();
		float damage = (float) ctx.param("damage", 4.0) * power.factor();
		if (target.getWidth() > ctx.param("max_width", 2.0) * (power.rampage() ? 2.0 : 1.0)) {
			// Ringer-Wurf: ueber die Schulter in den Boden
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().playerAttack(player), damage * 2.5f);
			target.setVelocity(0.0, -1.0, 0.0);
			target.velocityModified = true;
			slamFx(world, target.getPos(), 4.0, 5, 0.9f, 0.0, Vec3d.ZERO);
			world.playSound(null, target.getBlockPos(), ModSounds.ALIEN_SLAM, SoundCategory.PLAYERS, 1.4f, 0.6f);
			return true;
		}
		Vec3d look = player.getRotationVec(1.0f);
		double force = ctx.param("power", 2.2) * (power.rampage() ? 1.5 : 1.0);
		target.timeUntilRegen = 0;
		target.damage(world.getDamageSources().playerAttack(player), damage);
		target.setVelocity(look.x * force, Math.max(0.4, look.y * force + 0.4), look.z * force);
		target.velocityModified = true;
		THROWN.put(target.getUuid(), new Thrown(player.getUuid(), damage * (power.rampage() ? 2.0f : 1.2f), world.getTime() + 60));
		BuiltinAbilities.sound(ctx, ModSounds.WEAPON_THROW, 1.0f, 0.7f);
		return true;
	}

	/**
	 * Bodenschlag (Halten laedt auf, bis 1,5 s): alle vier Faeuste in den Boden. Druckwelle, springende Bodenbloecke und
	 * ein echter Krater, der mit der Ladung waechst.
	 */
	private static final class GroundSlam implements ChargeableAbility {
		@Override
		public int chargeTicks(AbilityContext ctx) {
			return 30;
		}

		@Override
		public void charging(AbilityContext ctx, int ticks, float charge) {
			ServerPlayerEntity player = ctx.player();
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 3, 2, false, false));
			if (ticks % 4 == 0) {
				Fx.charge(ctx.world(), player, charge >= 1.0f ? RED : IMPACT, 1.2f + charge, 6);
				Fx.smoke(ctx.world(), player.getPos(), DUST, 0.6f + charge * 0.6f, 1);
			}
			if (ticks % 10 == 0) {
				Fx.shake(ctx.world(), player.getPos(), 0.05f + charge * 0.15f, 6, 8);
			}
		}

		@Override
		public boolean release(AbilityContext ctx, float charge) {
			ServerPlayerEntity player = ctx.player();
			ServerWorld world = ctx.world();
			Power power = power(player);
			double radius = ctx.param("radius", 5.0) * (1.0 + charge * 1.2) * (power.rampage() ? 1.6 : 1.0);
			float damage = (float) ctx.param("damage", 9.0) * power.factor() * (1.0f + charge * 0.8f);
			WAVES.add(new Wave(player.getUuid(), player.getPos(), radius, damage, ctx.param("launch", 0.5) * power.factor() * (1.0 + charge * 0.6),
					world.getTime(), power.rampage() ? 12 : 8, 0.0, Vec3d.ZERO));
			float strength = AlienStrength.of(player) * (1.0f + charge) * (power.rampage() ? 1.5f : 1.0f);
			double crater = 2.2 + charge * 3.3 + (power.rampage() ? 1.5 : 0.0);
			// Schuesselform: Mitte unter den Fuessen, damit der Krater flach statt kugelfoermig wird
			Destruction.crater(world, player, player.getPos().add(0, -0.6, 0), crater, 0.45, strength, 0.7, 0.35 + charge * 0.25);
			slamFx(world, player.getPos(), radius, power.rampage() ? 12 : 8, (power.rampage() ? 1.4f : 1.0f) + charge * 0.8f, 0.0, Vec3d.ZERO);
			if (charge >= 1.0f) {
				// Erdexplosionen am Kraterrand (nicht an der Kamera)
				for (int i = 0; i < 6; i++) {
					double a = i * MathHelper.TAU / 6;
					Fx.explosion(world, player.getPos().add(Math.cos(a) * crater * 0.9, 0.3, Math.sin(a) * crater * 0.9), IMPACT, 1.6f);
				}
			}
			BuiltinAbilities.sound(ctx, ModSounds.ALIEN_SLAM, 1.3f + charge * 0.5f, power.rampage() ? 0.6f : 0.9f - charge * 0.2f);
			BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.6f + charge * 0.6f, 0.7f);
			return true;
		}
	}

	/** Felsbrocken: reisst ein Stueck Boden heraus (echter Krater) und wirft es; Einschlag mit Krater und Flaechenschaden. */
	private static boolean boulder(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d flat = BuiltinAbilities.horizontalLook(player);
		net.minecraft.util.math.BlockPos source = net.minecraft.util.math.BlockPos.ofFloored(player.getPos().add(flat.multiply(1.6))).down();
		BlockState state = world.getBlockState(source);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		Power power = power(player);
		float strength = AlienStrength.of(player);
		Destruction.crater(world, player, Vec3d.ofCenter(source), 1.5, strength, 0.0, 0.25);
		Fx.smoke(world, Vec3d.ofCenter(source).add(0, 0.6, 0), DUST, 1.2f, 6);
		com.santiq.kingdomomnitrix.vfx.Fx.sparks(world, new Vec3d(source.getX() + 0.5, source.getY() + 1.0, source.getZ() + 0.5), AlienKit.color(new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK, state)), 0.35f, Math.max(1, Math.min(20, 40)));
		float damage = (float) ctx.param("damage", 4.0) * 2.2f * power.factor();
		float size = power.rampage() ? 2.6f : 1.9f;
		double speed = ctx.param("power", 2.2) * 0.75;
		Vec3d start = player.getEyePos().add(flat.multiply(1.2)).add(0, 0.6, 0);
		Debris.projectile(world, player, state, start, look.multiply(speed).add(0, 0.18, 0), size, (w, at) -> {
			for (LivingEntity near : w.getEntitiesByClass(LivingEntity.class, net.minecraft.util.math.Box.of(at, 6, 4, 6),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
				double d = near.getPos().distanceTo(at);
				if (d > 3.2) {
					continue;
				}
				near.timeUntilRegen = 0;
				near.damage(w.getDamageSources().playerAttack(player), (float) (damage * (1.0 - d / 6.4)));
				Vec3d push = near.getPos().subtract(at).multiply(1, 0, 1);
				if (push.lengthSquared() > 1.0E-4) {
					near.takeKnockback(1.4, -push.x, -push.z);
				}
				near.addVelocity(0.0, 0.4, 0.0);
				near.velocityModified = true;
			}
			Destruction.crater(w, player, at.add(0, -0.4, 0), size * 1.3, 0.6, strength, 0.5, 0.45);
			slamFx(w, at, 3.5 + size, 5, 1.0f + size * 0.2f, 0.0, Vec3d.ZERO);
			w.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(at), ModSounds.ALIEN_SLAM, SoundCategory.PLAYERS, 1.6f, 0.6f);
			w.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 0.8f);
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_STONE_BREAK, 1.4f, 0.5f);
		BuiltinAbilities.sound(ctx, ModSounds.WEAPON_THROW, 1.2f, 0.5f);
		return true;
	}

	/** Titanensprung: weiter Satz, die Landung schlaegt nach Fallhoehe ein. */
	private static boolean titanLeap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Power power = power(player);
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		BuiltinAbilities.launch(player, look.x * ctx.param("forward", 1.2), ctx.param("up", 1.3) * (power.rampage() ? 1.3 : 1.0),
				look.z * ctx.param("forward", 1.2));
		LEAPS.put(player.getUuid(), new Leap(player.getY(), (float) ctx.param("damage", 8.0) * power.factor(), ctx.param("radius", 4.5),
				ctx.world().getTime() + 100, power.rampage()));
		Fx.shockwave(ctx.world(), player.getPos().add(0, 0.1, 0), DUST, 3.0f, 8);
		Fx.smoke(ctx.world(), player.getPos(), DUST, 1.2f, 6);
		EarthWave.ring(ctx.world(), player.getPos(), 2.5, 3, 0.4f);
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_SLAM, 1.0f, 1.2f);
		return true;
	}

	/** Donnerklatschen: Schallkegel nach vorn — wirft zurueck, betaeubt, zerschlaegt Geschosse im Kegel. */
	private static boolean thunderClap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Power power = power(player);
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		double radius = ctx.param("radius", 6.0) * (power.rampage() ? 1.6 : 1.0);
		double cone = power.rampage() ? 0.2 : ctx.param("cone", 0.6);
		WAVES.add(new Wave(player.getUuid(), player.getPos(), radius, (float) ctx.param("damage", 7.0) * power.factor(),
				0.2, world.getTime(), 5, cone, look));
		int stun = (int) (ctx.param("seconds", 2.0) * 20 * power.factor());
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			Vec3d flat = target.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			if (flat.lengthSquared() > 1.0E-4 && flat.normalize().dotProduct(look) >= cone) {
				target.takeKnockback(ctx.param("knockback", 2.2) * power.factor(), -flat.x, -flat.z);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, stun, 3), player);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, stun + 40, 0), player);
			}
		}
		for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, player.getBoundingBox().expand(radius),
				p -> p.getOwner() != player)) {
			Vec3d flat = projectile.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			if (flat.lengthSquared() > 1.0E-4 && flat.normalize().dotProduct(look) >= cone) {
				Fx.sparks(world, projectile.getPos(), IMPACT, 0.3f, 8);
				projectile.discard();
			}
		}
		Vec3d front = player.getEyePos().add(look.multiply(1.5));
		// vier Haende klatschen: Druckkegel aus Luftringen, der Boden davor bricht auf
		for (int i = 0; i < 4; i++) {
			Vec3d at = front.add(look.multiply(i * radius / 4.0)).add(0, -0.4, 0);
			Fx.send(world, Fx.Kind.SHOCKWAVE, at, look, CLAP, (float) (1.2 + i * radius / 5.0), 8 + i * 2, 1, null);
		}
		Fx.flash(world, front, CLAP, 2.4f);
		// Schallwelle: Glas, Laub und andere zerbrechliche Bloecke im Kegel zerspringen
		Destruction.carve(world, player, player.getEyePos(), player.getRotationVec(1.0f), radius, 1.8 + (power.rampage() ? 1.0 : 0.0), 0.6f, 0.4);
		EarthWave.line(world, player.getPos(), look, radius, 6, 0.6f, cone);
		Fx.shake(world, player.getPos(), 0.4f, 8, 20);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.4f, 1.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
		return true;
	}

	/** Eisenhaut: haelt alles aus, steht wie ein Fels, verwandelt Treffer in doppelte Wut und schlaegt zurueck. */
	private static boolean ironSkin(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 10.0) * 20);
		IRON_SKIN.put(player.getUuid(), ctx.world().getTime() + ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 1, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, ticks, 1, false, false));
		knockbackImmunity(player, true);
		Fx.aura(ctx.world(), player, IRON, 1.4f, ticks);
		Fx.flash(ctx.world(), player.getPos().add(0, 1.2, 0), IRON, 2.6f);
		Fx.sparks(ctx.world(), player.getPos().add(0, 1.2, 0), IRON, 0.5f, 24);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_ANVIL_LAND, 0.9f, 0.8f);
		return true;
	}

	/** Erdbeben: mehrere Druckwellen nacheinander, jede groesser. */
	private static boolean earthquake(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Power power = power(player);
		QUAKES.put(player.getUuid(), new Quake(ctx.world().getTime(), power.rampage() ? 6 : 4, ctx.param("radius", 9.0),
				(float) ctx.param("damage", 14.0) * power.factor() / 2.0f, ctx.param("launch", 0.8)));
		ctx.grantInvulnerability(20);
		// Erdspalten: Risse laufen sternfoermig vom Einschlag weg und reissen den Boden auf
		int cracks = power.rampage() ? 8 : 5;
		double offset = ctx.world().random.nextDouble() * MathHelper.TAU;
		float strength = AlienStrength.of(player) * (power.rampage() ? 1.5f : 1.0f);
		for (int i = 0; i < cracks; i++) {
			double a = offset + i * MathHelper.TAU / cracks + (ctx.world().random.nextDouble() - 0.5) * 0.5;
			Vec3d dir = new Vec3d(Math.cos(a), -0.08, Math.sin(a));
			Destruction.carve(ctx.world(), player, player.getPos().add(dir.multiply(1.5)).add(0, -1.3, 0), dir, ctx.param("radius", 9.0) * 1.1,
					1.2, strength, 1.6);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.5f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!isFourArms(player)) {
				if (RAGE.containsKey(player.getUuid()) || IRON_SKIN.containsKey(player.getUuid()) || LEAPS.containsKey(player.getUuid())) {
					forget(player);
				}
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long now = world.getTime();
			if (now % 20 == 0 && rage(player) > 0.0f && now - LAST_COMBAT.getOrDefault(player.getUuid(), 0L) > CALM_TICKS) {
				addRage(player, -RAGE_DECAY);
			}
			if (rage(player) >= ANGRY && now % 4 == 0) {
				Fx.send(world, Fx.Kind.FLAMES, new Vec3d(player.getX(), player.getBodyY(0.6), player.getZ()), new Vec3d(0, 0.08, 0),
						rage(player) >= MAX ? RED : 0xB03018, 0.5f, 10, 2, null);
			}
			Long skin = IRON_SKIN.get(player.getUuid());
			if (skin != null && now >= skin) {
				IRON_SKIN.remove(player.getUuid());
				knockbackImmunity(player, false);
			}
			tickLeap(world, player, now);
			tickQuake(world, player, now);
		}
		if (!WAVES.isEmpty()) {
			tickWaves(server);
		}
		if (!THROWN.isEmpty()) {
			tickThrown(server);
		}
	}

	private static void tickLeap(ServerWorld world, ServerPlayerEntity player, long now) {
		Leap leap = LEAPS.get(player.getUuid());
		if (leap == null) {
			return;
		}
		player.fallDistance = 0.0f;
		if (now >= leap.until()) {
			LEAPS.remove(player.getUuid());
			return;
		}
		if (player.isOnGround() && now > leap.until() - 96) {
			LEAPS.remove(player.getUuid());
			// Fallhoehe ueber dem Absprung verstaerkt den Einschlag
			double height = Math.max(0.0, leap.startY() + 6.0 - player.getY());
			float boost = (float) Math.min(2.0, 1.0 + height / 16.0);
			double radius = leap.radius() * (leap.rampage() ? 1.6 : 1.0) * Math.min(1.5, boost);
			WAVES.add(new Wave(player.getUuid(), player.getPos(), radius, leap.damage() * boost, 0.5, now, 6, 0.0, Vec3d.ZERO));
			slamFx(world, player.getPos(), radius, 6, boost, 0.0, Vec3d.ZERO);
			double crater = 1.3 + boost * 1.1 + (leap.rampage() ? 1.0 : 0.0);
			Destruction.crater(world, player, player.getPos().add(0, -0.6, 0), crater, 0.5, AlienStrength.of(player) * boost, 0.6, 0.4);
			world.playSound(null, player.getBlockPos(), ModSounds.ALIEN_SLAM, SoundCategory.PLAYERS, 1.4f, 0.7f);
		}
	}

	private static void tickQuake(ServerWorld world, ServerPlayerEntity player, long now) {
		Quake quake = QUAKES.get(player.getUuid());
		if (quake == null) {
			return;
		}
		long age = now - quake.start();
		int interval = 15;
		if (age % interval == 0) {
			int index = (int) (age / interval);
			if (index >= quake.waves()) {
				QUAKES.remove(player.getUuid());
				return;
			}
			double radius = quake.radius() * (0.5 + 0.5 * (index + 1) / quake.waves());
			WAVES.add(new Wave(player.getUuid(), player.getPos(), radius, quake.damage(), quake.launch(), now, 10, 0.0, Vec3d.ZERO));
			slamFx(world, player.getPos(), radius, 10, 0.8f + index * 0.15f, 0.0, Vec3d.ZERO);
			world.playSound(null, player.getBlockPos(), ModSounds.ALIEN_SLAM, SoundCategory.PLAYERS, 1.5f, 0.5f + index * 0.08f);
		}
	}

	private static void tickWaves(MinecraftServer server) {
		Iterator<Wave> it = WAVES.iterator();
		while (it.hasNext()) {
			Wave wave = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(wave.owner);
			if (player == null) {
				it.remove();
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long age = world.getTime() - wave.start;
			double front = wave.radius * Math.min(1.0, (age + 1) / (double) wave.ticks);
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, net.minecraft.util.math.Box.of(wave.center, front * 2 + 1, 5, front * 2 + 1),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e) && !wave.hit.contains(e.getUuid()))) {
				Vec3d flat = target.getPos().subtract(wave.center).multiply(1, 0, 1);
				double distance = flat.length();
				if (distance > front || Math.abs(target.getY() - wave.center.y) > 3.0) {
					continue;
				}
				if (wave.cone > 0.0 && distance > 1.0E-3 && flat.normalize().dotProduct(wave.facing) < wave.cone) {
					continue;
				}
				wave.hit.add(target.getUuid());
				target.timeUntilRegen = 0;
				target.damage(world.getDamageSources().playerAttack(player), (float) (wave.damage * (1.0 - 0.4 * distance / wave.radius)));
				if (distance > 1.0E-3) {
					target.takeKnockback(1.2, -flat.x / distance, -flat.z / distance);
				}
				target.addVelocity(0.0, wave.launch, 0.0);
				target.velocityModified = true;
			}
			if (age >= wave.ticks) {
				it.remove();
			}
		}
	}

	/** Geworfene Ziele: Aufprall auf Boden, Wand oder anderes Wesen schlaegt in die Umgebung ein. */
	private static void tickThrown(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Thrown>> it = THROWN.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Thrown> entry = it.next();
			ServerPlayerEntity thrower = server.getPlayerManager().getPlayer(entry.getValue().thrower());
			if (thrower == null) {
				it.remove();
				continue;
			}
			ServerWorld world = thrower.getServerWorld();
			if (!(world.getEntity(entry.getKey()) instanceof LivingEntity flying) || !flying.isAlive() || world.getTime() > entry.getValue().until()) {
				it.remove();
				continue;
			}
			List<LivingEntity> bumped = world.getEntitiesByClass(LivingEntity.class, flying.getBoundingBox().expand(0.4),
					e -> e != flying && e != thrower && e.isAlive() && PartyRules.canHarm(thrower, e));
			boolean landed = world.getTime() > entry.getValue().until() - 55 && (flying.isOnGround() || flying.horizontalCollision);
			if (!bumped.isEmpty() || landed) {
				it.remove();
				float damage = entry.getValue().damage();
				for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, flying.getBoundingBox().expand(2.5),
						e -> e != thrower && e.isAlive() && PartyRules.canHarm(thrower, e))) {
					near.timeUntilRegen = 0;
					near.damage(world.getDamageSources().playerAttack(thrower), damage);
					near.addVelocity(0.0, 0.35, 0.0);
					near.velocityModified = true;
				}
				slamFx(world, flying.getPos(), 3.5, 4, 0.8f, 0.0, Vec3d.ZERO);
				world.playSound(null, flying.getBlockPos(), ModSounds.ALIEN_SLAM, SoundCategory.PLAYERS, 1.2f, 1.0f);
			}
		}
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	/** Bodenschlag-Optik: echte Bodenbloecke springen ringfoermig hoch, Staubring, Druckwelle, Kamerawackeln. */
	static void slamFx(ServerWorld world, Vec3d at, double radius, int ticks, float strength, double cone, Vec3d facing) {
		Vec3d ground = at.add(0, 0.1, 0);
		if (cone > 0.0) {
			EarthWave.line(world, at, facing, radius, ticks, 0.7f * strength, cone);
		} else {
			EarthWave.ring(world, at, radius, ticks, 0.7f * strength);
		}
		Fx.shockwave(world, ground, DUST, (float) radius * 1.1f, ticks + 6);
		Fx.flash(world, ground, IMPACT, Math.min(1.6f, 1.0f * strength));
		Fx.smoke(world, ground, DUST, (float) (0.8 + radius * 0.08), (int) (3 + radius * 0.4));
		Fx.shake(world, at, Math.min(1.0f, 0.35f * strength), 10, (int) (radius * 3 + 10));
	}

	private static void knockbackImmunity(ServerPlayerEntity player, boolean on) {
		EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
		if (attribute == null) {
			return;
		}
		attribute.removeModifier(SKIN_KNOCKBACK);
		if (on) {
			attribute.addTemporaryModifier(new EntityAttributeModifier(SKIN_KNOCKBACK, 1.0, EntityAttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Zurueckverwandelt: Anzeige auf 0, Eisenhaut-Attribut weg, Zustand loeschen. */
	private static void forget(ServerPlayerEntity player) {
		if (RAGE.containsKey(player.getUuid())) {
			AlienMeterSync.clear(player, AlienMeterPayload.RAGE);
		}
		if (IRON_SKIN.containsKey(player.getUuid())) {
			knockbackImmunity(player, false);
		}
		forgetState(player.getUuid());
	}

	private static void forgetState(UUID id) {
		RAGE.remove(id);
		LAST_COMBAT.remove(id);
		COMBO.remove(id);
		IRON_SKIN.remove(id);
		LEAPS.remove(id);
		QUAKES.remove(id);
	}
}
