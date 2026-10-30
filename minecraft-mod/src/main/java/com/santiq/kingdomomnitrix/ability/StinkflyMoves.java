package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Stinkfly, Kraefte 2.0: Saeuretropfen, Stachelstich, Schleimfalle, Gasduese, Aetzspray, Seuchenwolke; Kombos
 * Fluegelhieb, Sturzstich, Schleimgriff, Giftbombe, Zersetzen; Passive Facettenaugen und Gestank (die Toxin-Schichten
 * sitzen in {@link StinkflyAbilities}). Fast jede Kraft legt Schichten auf — bei fuenf zersetzt sich das Ziel.
 */
final class StinkflyMoves {
	private static final int GAS = 0x7FB52A;
	private static final int GOO = 0x8EEA4E;
	private static final int CORE = 0xD8F25A;
	private static final int ACID = 0xC8F040;

	private StinkflyMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("acid_drop"), StinkflyMoves::acidDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("stinger_strike"), StinkflyMoves::stingerStrike);
		AbilityRegistry.register(KingdomOmnitrix.id("slime_trap"), StinkflyMoves::slimeTrap);
		AbilityRegistry.register(KingdomOmnitrix.id("gas_jet"), StinkflyMoves::gasJet);
		AbilityRegistry.register(KingdomOmnitrix.id("corrosive_spray"), StinkflyMoves::corrosiveSpray);
		AbilityRegistry.register(KingdomOmnitrix.id("plague_cloud"), StinkflyMoves::plagueCloud);
		AbilityRegistry.register(KingdomOmnitrix.id("wing_slash"), StinkflyMoves::wingSlash);
		AbilityRegistry.register(KingdomOmnitrix.id("dive_sting"), StinkflyMoves::diveSting);
		AbilityRegistry.register(KingdomOmnitrix.id("slime_grab"), StinkflyMoves::slimeGrab);
		AbilityRegistry.register(KingdomOmnitrix.id("toxic_plunge"), StinkflyMoves::toxicPlunge);
		AbilityRegistry.register(KingdomOmnitrix.id("dissolve"), StinkflyMoves::dissolve);
		PassiveRegistry.register(KingdomOmnitrix.id("compound_eyes"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				ctx.player().removeStatusEffect(StatusEffects.BLINDNESS);
				ctx.player().removeStatusEffect(StatusEffects.DARKNESS);
			}

			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Facettenaugen sehen Geschosse kommen: im Flug weicht Stinkfly oft aus
				if (source.isIn(DamageTypeTags.IS_PROJECTILE) && ctx.player().getAbilities().flying
						&& ctx.world().random.nextDouble() < ctx.param("dodge", 0.3)) {
					Fx.smoke(ctx.world(), ctx.player().getPos().add(0, 1.0, 0), GAS, 0.6f, 2);
					return 0.0f;
				}
				return amount;
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("stench"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// beissender Gestank: wer zu nah kommt, dem wird uebel und er wird schwach
				if (ctx.now() % 30 != 0) {
					return;
				}
				ServerPlayerEntity player = ctx.player();
				boolean any = false;
				for (LivingEntity target : AlienKit.around(ctx.world(), player, player.getPos().add(0, 1.0, 0), ctx.param("radius", 3.0))) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), player);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0), player);
					any = true;
				}
				if (any) {
					Fx.smoke(ctx.world(), player.getPos().add(0, 0.8, 0), GAS, 0.6f, 2);
				}
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Saeuretropfen: drei Saeureklumpen fallen unter Stinkfly herab und hinterlassen aetzende Pfuetzen. */
	private static boolean acidDrop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 4.0);
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		for (int i = 0; i < 3; i++) {
			Vec3d at = AlienKit.ground(ctx.world(), player.getPos().add(look.multiply(i * 1.8)).add(0, 0.5, 0), 20.0);
			int delay = i * 4;
			PowerTasks.start(player, delay + 1, 1, (world, p, age) -> {
				if (age < delay) {
					return true;
				}
				Fx.flames(world, at.add(0, 4.0, 0), new Vec3d(0, -0.6, 0), ACID, 0.1f, 4);
				Fx.explosion(world, at.add(0, 0.3, 0), ACID, 0.9f);
				for (LivingEntity target : AlienKit.blast(world, p, at.add(0, 0.6, 0), 2.0, damage, 0.2, 0.1)) {
					StinkflyAbilities.addToxin(p, target, 1);
				}
				StinkflyAbilities.cloud(p, at, 1.6, 80, true);
				world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, SoundCategory.PLAYERS, 1.0f, 0.6f);
				return false;
			});
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_SQUISH, 1.0f, 0.8f);
		return true;
	}

	/** Stachelstich: Stoss mit dem Schwanzstachel nach vorn (bis 6 Bloecke) — Gift, zwei Schichten, verlangsamt. */
	private static boolean stingerStrike(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 6.0), 0.6, e -> AlienKit.foe(player, e));
		Vec3d look = player.getRotationVec(1.0f);
		BuiltinAbilities.launch(player, look.x * 1.2, look.y * 1.2 + 0.1, look.z * 1.2);
		if (found.isEmpty()) {
			return true;
		}
		LivingEntity target = found.get();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 1), player);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), player);
		StinkflyAbilities.addToxin(player, target, 2);
		Fx.beam(ctx.world(), player.getPos().add(0, 0.8, 0), AlienKit.body(target), CORE, 0.1f, 5);
		Fx.flash(ctx.world(), AlienKit.body(target), GOO, 1.2f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BEE_STING, 1.2f, 0.7f);
		return true;
	}

	/** Schleimfalle: ein Schleimklumpen klebt das Ziel im Blick 3 s fest — Schicht, klebrige Pfuetze. */
	private static boolean slimeTrap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 16.0), 0.9, e -> AlienKit.foe(player, e));
		if (found.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		AlienKit.hold(ctx.world(), target, ticks, AlienKit.dust("#8EEA4E", 1.4f), false);
		StinkflyAbilities.addToxin(player, target, 1);
		StinkflyAbilities.cloud(player, target.getPos(), 1.8, ticks, true);
		Fx.beam(ctx.world(), AlienKit.muzzle(player), AlienKit.body(target), GOO, 0.18f, 6);
		Fx.explosion(ctx.world(), AlienKit.body(target), GOO, 0.9f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_ATTACK, 1.2f, 0.6f);
		return true;
	}

	/** Gasduese: schiesst mit einem Gasstoss nach vorn und hinterlaesst eine Spur aus Giftwolken. */
	private static boolean gasJet(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = player.getRotationVec(1.0f);
		double power = ctx.param("power", 2.4);
		BuiltinAbilities.launch(player, look.x * power, look.y * power + 0.1, look.z * power);
		ctx.grantInvulnerability(8);
		PowerTasks.start(player, 12, 3, (world, p, age) -> {
			StinkflyAbilities.cloud(p, p.getPos(), 1.6, 80, false);
			Fx.smoke(world, p.getPos().add(0, 0.6, 0), GAS, 1.0f, 2);
			return true;
		});
		Fx.shockwave(ctx.world(), player.getPos().add(0, 0.6, 0), GAS, 2.5f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PUFFER_FISH_BLOW_OUT, 1.2f, 1.2f);
		return true;
	}

	/** Aetzspray: aetzender Spruehnebel im Kegel — drei Schichten auf jedes Ziel, schwaecht Ruestung und Kraft. */
	private static boolean corrosiveSpray(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 7.0);
		float damage = (float) ctx.param("damage", 3.0);
		PowerTasks.start(player, 20, 2, (w, p, age) -> {
			Vec3d look = p.getRotationVec(1.0f);
			Fx.flames(w, AlienKit.muzzle(p), look.multiply(0.5), ACID, 0.25f, 4);
			if (age % 6 == 0) {
				for (LivingEntity target : AlienKit.cone(w, p, range, 0.8)) {
					AlienKit.magic(w, p, target, damage);
					StinkflyAbilities.addToxin(p, target, 1);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 1), p);
				}
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LLAMA_SPIT, 1.2f, 0.5f);
		return true;
	}

	/**
	 * Seuchenwolke (Ultimate): eine riesige Giftwolke folgt Stinkfly 8 Sekunden lang — jede Sekunde eine Schicht auf
	 * alles darin, Uebelkeit und Gift.
	 */
	private static boolean plagueCloud(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double radius = ctx.param("radius", 8.0);
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		float damage = (float) ctx.param("damage", 2.0);
		PowerTasks.start(player, ticks, 4, (world, p, age) -> {
			var random = world.getRandom();
			for (int i = 0; i < 5; i++) {
				double a = random.nextDouble() * MathHelper.TAU;
				double r = Math.sqrt(random.nextDouble()) * radius;
				Fx.smoke(world, p.getPos().add(Math.cos(a) * r, 0.5 + random.nextDouble() * 2.0, Math.sin(a) * r), GAS, 1.6f, 1);
			}
			if (age % 20 == 0) {
				for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 1.0, 0), radius)) {
					AlienKit.magic(world, p, target, damage);
					StinkflyAbilities.addToxin(p, target, 1);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 40, 0), p);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0), p);
				}
				world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PUFFER_FISH_BLOW_OUT, SoundCategory.PLAYERS, 1.0f, 0.4f);
			}
			return true;
		});
		Fx.shockwave(ctx.world(), player.getPos().add(0, 0.2, 0), GAS, (float) radius, 16);
		Fx.flash(ctx.world(), player.getPos().add(0, 1.0, 0), CORE, 2.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.4f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Fluegelhieb (Sprint + Schlag): die Fluegelkante schneidet — Rueckstoss, eine Schicht, Gaswirbel. */
	private static boolean wingSlash(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 5.0));
		AlienKit.push(target, player.getPos(), 1.2, 0.4);
		StinkflyAbilities.addToxin(player, target, 1);
		Fx.smoke(ctx.world(), AlienKit.body(target), GAS, 0.8f, 3);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_FLAP, 1.2f, 1.4f);
		return true;
	}

	/** Sturzstich (Schlag in der Luft): Stachel von oben — starkes Gift, zwei Schichten. */
	private static boolean diveSting(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 120, 1), player);
		StinkflyAbilities.addToxin(player, target, 2);
		Fx.flash(ctx.world(), AlienKit.body(target), CORE, 1.2f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BEE_STING, 1.4f, 0.6f);
		return true;
	}

	/** Schleimgriff (Schleichen + Schlag): Schleim aus dem Mund klebt das Ziel 2 Sekunden fest. */
	private static boolean slimeGrab(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 3.0));
		AlienKit.hold(ctx.world(), target, (int) (ctx.param("seconds", 2.0) * 20), AlienKit.dust("#8EEA4E", 1.2f), false);
		StinkflyAbilities.addToxin(player, target, 1);
		Fx.explosion(ctx.world(), AlienKit.body(target), GOO, 0.7f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_SQUISH, 1.2f, 0.5f);
		return true;
	}

	/** Giftbombe (hoch in der Luft schleichen): Sturz, beim Aufprall platzt eine grosse Giftwolke. */
	private static boolean toxicPlunge(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 6.0);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 1.8), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround()) {
				Fx.smoke(world, p.getPos().add(0, 1.0, 0), GAS, 0.6f, 1);
				return true;
			}
			for (LivingEntity target : AlienKit.blast(world, p, p.getPos().add(0, 0.6, 0), 3.5, damage, 0.8, 0.3)) {
				StinkflyAbilities.addToxin(p, target, 2);
			}
			StinkflyAbilities.cloud(p, p.getPos(), 3.5, 100, false);
			Fx.explosion(world, p.getPos().add(0, 0.5, 0), GAS, 2.0f);
			Fx.shockwave(world, p.getPos().add(0, 0.1, 0), GAS, 4.0f, 10);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_SLIME_DEATH, SoundCategory.PLAYERS, 1.4f, 0.5f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_SWOOP, 1.0f, 1.2f);
		return true;
	}

	/**
	 * Zersetzen (Schlag gegen geschwaechtes Ziel): verbraucht alle Schichten des Ziels — je Schicht Zusatzschaden; die
	 * Gaswolke springt auf Gegner in der Naehe ueber.
	 */
	private static boolean dissolve(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int stacks = StinkflyAbilities.stacks(target);
		float damage = (float) ctx.param("damage", 6.0) + stacks * (float) ctx.param("per_stack", 3.0);
		AlienKit.magic(world, player, target, damage);
		Fx.explosion(world, AlienKit.body(target), GAS, 1.4f + stacks * 0.2f);
		Set<UUID> spread = new HashSet<>();
		for (LivingEntity near : AlienKit.around(world, player, AlienKit.body(target), 5.0)) {
			if (near != target && spread.add(near.getUuid())) {
				StinkflyAbilities.addToxin(player, near, Math.max(1, stacks / 2));
				Fx.beam(world, AlienKit.body(target), AlienKit.body(near), GAS, 0.1f, 6);
			}
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_DEATH, 1.4f, 0.4f);
		return true;
	}
}
