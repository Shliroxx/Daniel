package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.power.Debris;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.world.content.TempBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Spidermonkey, Kraefte 2.0: Netzbola, Netzzug, Netzwand, Affensprung, Netzwirbel, Spinnenhinterhalt; Kombos
 * Schwungtritt, Spinnenfall, Vier-Arm-Wirbel, Netzsturz, Kokonschlag; Passive Netzjaeger (Kern-Bonus gegen
 * Eingewebte), Kletteraffe (Wandlauf) und Spinnensinn. Netze halten ueber {@link SpidermonkeyAbilities#webbed}.
 */
final class SpidermonkeyMoves {
	private static final int WEB = 0xF2F2F2;
	private static final int FUR = 0x2E5FB8;

	private SpidermonkeyMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("web_bola"), SpidermonkeyMoves::webBola);
		AbilityRegistry.register(KingdomOmnitrix.id("web_yank"), SpidermonkeyMoves::webYank);
		AbilityRegistry.register(KingdomOmnitrix.id("web_wall"), SpidermonkeyMoves::webWall);
		AbilityRegistry.register(KingdomOmnitrix.id("monkey_leap"), SpidermonkeyMoves::monkeyLeap);
		AbilityRegistry.register(KingdomOmnitrix.id("web_spinner"), SpidermonkeyMoves::webSpinner);
		AbilityRegistry.register(KingdomOmnitrix.id("arachnid_ambush"), SpidermonkeyMoves::arachnidAmbush);
		AbilityRegistry.register(KingdomOmnitrix.id("swing_kick"), SpidermonkeyMoves::swingKick);
		AbilityRegistry.register(KingdomOmnitrix.id("spider_drop"), SpidermonkeyMoves::spiderDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("four_arm_flurry"), SpidermonkeyMoves::fourArmFlurry);
		AbilityRegistry.register(KingdomOmnitrix.id("web_crash"), SpidermonkeyMoves::webCrash);
		AbilityRegistry.register(KingdomOmnitrix.id("cocoon_slam"), SpidermonkeyMoves::cocoonSlam);
		PassiveRegistry.register(KingdomOmnitrix.id("web_hunter"), new AlienPassive() {
			@Override
			public float outgoing(PassiveContext ctx, LivingEntity target, DamageSource source, float amount) {
				// Eingewebte sind wehrlos: mehr Schaden
				return AlienKit.held(target) ? amount * (float) ctx.param("webbed_bonus", 1.5) : amount;
			}

			@Override
			public void melee(PassiveContext ctx, LivingEntity target, boolean fullSwing) {
				// gegen Eingewebte schlagen alle vier Arme zu: drei Zusatzschlaege
				if (fullSwing && AlienKit.held(target)) {
					for (int i = 0; i < 3; i++) {
						AlienKit.melee(ctx.world(), ctx.player(), target, (float) ctx.param("extra_hit", 1.5));
					}
					Fx.sparks(ctx.world(), AlienKit.body(target), 0xFFFFFF, 0.35f, 10);
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("monkey_climber"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// Wandlauf: gegen die Wand gelaufen → klettern (Schleichen = festhalten)
				ServerPlayerEntity monkey = ctx.player();
				boolean wall = !ctx.world().isSpaceEmpty(monkey, monkey.getBoundingBox().offset(BuiltinAbilities.horizontalLook(monkey).multiply(0.3)));
				if (wall && !monkey.isOnGround()) {
					monkey.setVelocity(monkey.getVelocity().x, monkey.isSneaking() ? 0.0 : ctx.param("climb_speed", 0.22), monkey.getVelocity().z);
					monkey.velocityModified = true;
					monkey.fallDistance = 0.0f;
				} else if (wall && monkey.isSprinting()) {
					monkey.setVelocity(monkey.getVelocity().x, 0.3, monkey.getVelocity().z);
					monkey.velocityModified = true;
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("spider_sense"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Spinnensinn: manchen Geschossen weicht er im letzten Moment aus
				if (source.isIn(DamageTypeTags.IS_PROJECTILE) && ctx.player().getRandom().nextFloat() < (float) ctx.param("dodge", 0.3)) {
					ServerPlayerEntity monkey = ctx.player();
					Vec3d side = BuiltinAbilities.horizontalLook(monkey);
					double s = monkey.getRandom().nextBoolean() ? 1.0 : -1.0;
					BuiltinAbilities.launch(monkey, -side.z * 0.6 * s, 0.25, side.x * 0.6 * s);
					Fx.smoke(ctx.world(), AlienKit.body(monkey), FUR, 0.5f, 3);
					return 0.0f;
				}
				return amount;
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Netzbola: drei Netzknaeuel im Faecher — wo eines aufschlaegt, kleben Gegner fest. */
	private static boolean webBola(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d side = new Vec3d(-look.z, 0.0, look.x).normalize();
		int ticks = (int) (ctx.param("seconds", 2.0) * 20);
		float damage = (float) ctx.param("damage", 2.0);
		for (int i = -1; i <= 1; i++) {
			Vec3d dir = look.add(side.multiply(i * 0.15)).normalize();
			Debris.projectile(world, player, Blocks.COBWEB.getDefaultState(), AlienKit.muzzle(player), dir.multiply(ctx.param("speed", 1.4)).add(0, 0.15, 0),
					0.5f, (w, at) -> {
						for (LivingEntity target : AlienKit.around(w, player, at, 1.8)) {
							AlienKit.melee(w, player, target, damage);
							SpidermonkeyAbilities.webbed(w, target, ticks);
						}
						Fx.sparks(w, at, WEB, 0.3f, 10);
					});
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SPIDER_AMBIENT, 1.0f, 1.4f);
		return true;
	}

	/** Netzzug: Netzfaden ans Ziel im Blick — reisst es zu Spidermonkey heran und haelt es kurz fest. */
	private static boolean webYank(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		AlienKit.Beam beam = AlienKit.beam(world, player, ctx.param("range", 20.0));
		if (beam.target().isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = beam.target().get();
		AlienKit.line(world, AlienKit.muzzle(player), beam.end(), SpidermonkeyAbilities.WEB, 2.0);
		Vec3d pull = player.getPos().subtract(target.getPos());
		double dist = pull.length();
		if (dist > 1.0) {
			pull = pull.normalize().multiply(Math.min(2.2, 0.4 + dist * 0.12));
			target.setVelocity(pull.x, 0.4, pull.z);
			target.velocityModified = true;
		}
		AlienKit.melee(world, player, target, (float) ctx.param("damage", 3.0));
		PowerTasks.start(player, 14, 14, (w, p, age) -> true, (p, aborted) -> {
			if (target.isAlive()) {
				SpidermonkeyAbilities.webbed(p.getServerWorld(), target, (int) ctx.param("hold_ticks", 30.0));
			}
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, 1.2f, 1.2f);
		return true;
	}

	/** Netzwand: spinnt eine Wand aus Netz quer vor sich (8 s) — haelt alles auf, was hindurch will. */
	private static boolean webWall(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Vec3d side = new Vec3d(-look.z, 0.0, look.x);
		Vec3d center = player.getPos().add(look.multiply(ctx.param("distance", 3.0)));
		int half = (int) (ctx.param("width", 5.0) / 2);
		int height = (int) ctx.param("height", 3.0);
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		int placed = 0;
		for (int d = -half; d <= half; d++) {
			BlockPos base = BlockPos.ofFloored(center.add(side.multiply(d)));
			for (int h = 0; h < height; h++) {
				BlockPos pos = base.up(h);
				if (world.getBlockState(pos).isAir()) {
					TempBlocks.place(world, pos, Blocks.COBWEB.getDefaultState(), ticks, player, WEB, false);
					placed++;
				}
			}
		}
		if (placed == 0) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_space_step").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SPIDER_STEP, 1.4f, 0.8f);
		return true;
	}

	/** Affensprung: akrobatischer Satz weit nach vorn und oben — landet ohne Fallschaden, Tritt beim Aufkommen. */
	private static boolean monkeyLeap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		double power = ctx.param("power", 1.5);
		BuiltinAbilities.launch(player, look.x * power, ctx.param("lift", 1.0), look.z * power);
		float damage = (float) ctx.param("damage", 4.0);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (age % 3 == 0) {
				Fx.smoke(world, p.getPos(), FUR, 0.4f, 1);
			}
			if (age < 4 || !p.isOnGround()) {
				return true;
			}
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.5, 0), 2.5)) {
				AlienKit.melee(world, p, target, damage);
				AlienKit.push(target, p.getPos(), 0.8, 0.3);
			}
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GOAT_LONG_JUMP, 1.2f, 1.2f);
		return true;
	}

	/** Netzwirbel: spinnt Netz ringsum — alle in der Naehe kleben fest und werden ein Stueck herangezogen. */
	private static boolean webSpinner(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 6.0);
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		int count = 0;
		for (LivingEntity target : AlienKit.around(world, player, player.getPos(), radius)) {
			AlienKit.line(world, AlienKit.body(player), AlienKit.body(target), SpidermonkeyAbilities.WEB, 2.0);
			Vec3d pull = player.getPos().subtract(target.getPos()).multiply(1, 0, 1);
			if (pull.lengthSquared() > 4.0) {
				pull = pull.normalize().multiply(0.6);
				target.addVelocity(pull.x, 0.1, pull.z);
				target.velocityModified = true;
			}
			SpidermonkeyAbilities.webbed(world, target, ticks);
			count++;
		}
		for (int i = 0; i < 24; i++) {
			double a = i * MathHelper.TAU / 24;
			Fx.sparks(world, player.getPos().add(Math.cos(a) * radius, 0.4, Math.sin(a) * radius), WEB, 0.05f, 1);
		}
		if (count == 0) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SPIDER_AMBIENT, 1.4f, 0.8f);
		return true;
	}

	/**
	 * Spinnenhinterhalt (Ultimate): Netzfaeden auf bis zu 6 Gegner — alle kleben fest. Dann springt Spidermonkey von einem
	 * zum naechsten und schlaegt jedes Mal mit allen vier Armen zu.
	 */
	private static boolean arachnidAmbush(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		List<LivingEntity> targets = new ArrayList<>(AlienKit.around(world, player, player.getPos(), ctx.param("radius", 16.0)));
		if (targets.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
		List<LivingEntity> queue = targets.subList(0, Math.min(targets.size(), (int) ctx.param("targets", 6.0)));
		int pass = 10;
		for (LivingEntity target : queue) {
			AlienKit.line(world, AlienKit.body(player), AlienKit.body(target), SpidermonkeyAbilities.WEB, 2.0);
			SpidermonkeyAbilities.webbed(world, target, queue.size() * pass + 40);
		}
		float damage = (float) ctx.param("damage", 3.0);
		int[] index = {0};
		ctx.grantInvulnerability(queue.size() * pass + 20);
		PowerTasks.start(player, queue.size() * pass + 2, pass, (w, p, age) -> {
			if (index[0] >= queue.size()) {
				return false;
			}
			LivingEntity target = queue.get(index[0]++);
			if (!target.isAlive()) {
				return true;
			}
			Vec3d beside = target.getPos().add(target.getPos().subtract(p.getPos()).multiply(1, 0, 1).normalize().multiply(-1.2));
			AlienKit.line(w, AlienKit.body(p), AlienKit.body(target), SpidermonkeyAbilities.WEB, 2.0);
			p.requestTeleport(beside.x, target.getY(), beside.z);
			p.fallDistance = 0.0f;
			for (int i = 0; i < 4; i++) {
				AlienKit.melee(w, p, target, damage);
			}
			Fx.flash(w, AlienKit.body(target), 0xFFFFFF, 1.4f);
			w.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.4f);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SPIDER_AMBIENT, 1.6f, 1.0f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Schwungtritt (Sprint + Schlag): Tritt aus dem Lauf — Ziel fliegt und klebt kurz fest, wo es landet. */
	private static boolean swingKick(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 5.0));
		target.setVelocity(dir.x * 1.2, 0.4, dir.z * 1.2);
		target.velocityModified = true;
		PowerTasks.start(player, 12, 12, (w, p, age) -> true, (p, aborted) -> {
			if (target.isAlive()) {
				SpidermonkeyAbilities.webbed(p.getServerWorld(), target, 20);
			}
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.3f);
		return true;
	}

	/** Spinnenfall (Schlag in der Luft): faellt von oben aufs Ziel und klebt es am Boden fest. */
	private static boolean spiderDrop(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 5.0));
		target.setVelocity(0.0, -1.0, 0.0);
		target.velocityModified = true;
		SpidermonkeyAbilities.webbed(ctx.world(), target, (int) ctx.param("hold_ticks", 40.0));
		Fx.sparks(ctx.world(), target.getPos().add(0, 0.2, 0), WEB, 0.3f, 12);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SPIDER_STEP, 1.2f, 1.0f);
		return true;
	}

	/** Vier-Arm-Wirbel (Schleichen + Schlag): vier Schlaege auf einmal — mit jedem Arm einer. */
	private static boolean fourArmFlurry(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		float damage = (float) ctx.param("damage", 2.5);
		PowerTasks.start(player, 8, 2, (w, p, age) -> {
			if (target.isAlive() && target.squaredDistanceTo(p) < 25.0) {
				AlienKit.melee(w, p, target, damage);
				Fx.flash(w, AlienKit.body(target), 0xFFFFFF, 0.8f);
				w.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.8f, 1.4f + age * 0.05f);
			}
			return true;
		});
		return true;
	}

	/** Netzsturz (hoch in der Luft schleichen): stuerzt herab und verspritzt beim Aufschlag Netz — alle ringsum kleben fest. */
	private static boolean webCrash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 5.0);
		double radius = ctx.param("radius", 3.5);
		int ticks = (int) (ctx.param("seconds", 2.0) * 20);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.0), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.5, 0), radius * boost)) {
				AlienKit.melee(world, p, target, damage * boost);
				SpidermonkeyAbilities.webbed(world, target, ticks);
			}
			Fx.shockwave(world, p.getPos().add(0, 0.2, 0), WEB, (float) (radius * boost), 10);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.4f, 0.6f);
			return false;
		});
		return true;
	}

	/** Kokonschlag (Schlag gegen geschwaechtes Ziel): spinnt das Ziel ein, schwingt es herum und schmettert es zu Boden. */
	private static boolean cocoonSlam(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		SpidermonkeyAbilities.webbed(world, target, 30);
		float damage = (float) (ctx.param("damage", 10.0) + (target.getMaxHealth() - target.getHealth()) * 0.2);
		Optional<LivingEntity> second = AlienKit.around(world, player, target.getPos(), 4.0).stream().filter(e -> e != target).findFirst();
		AlienKit.melee(world, player, target, damage);
		target.setVelocity(0.0, -1.2, 0.0);
		target.velocityModified = true;
		// der Kokon trifft beim Schwung einen zweiten Gegner gleich mit
		second.ifPresent(other -> {
			AlienKit.melee(world, player, other, damage * 0.4f);
			AlienKit.push(other, target.getPos(), 1.0, 0.4);
		});
		Fx.shockwave(world, target.getPos().add(0, 0.2, 0), WEB, 3.0f, 8);
		Fx.sparks(world, AlienKit.body(target), WEB, 0.4f, 20);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.6f);
		return true;
	}
}
