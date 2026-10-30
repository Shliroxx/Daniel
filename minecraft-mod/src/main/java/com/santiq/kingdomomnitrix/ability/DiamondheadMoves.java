package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.vfx.CrystalGrowth;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import com.santiq.kingdomomnitrix.world.content.PlanetBlocks;
import com.santiq.kingdomomnitrix.world.content.TempBlocks;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Diamondhead, Kraefte 2.0: Kristalllanze, Kristallhammer, Kristallwand, Kristallkaefig, Diamantbohrer,
 * Kristallkathedrale; Kombos Kristallramme, Splitterfall, Kristallfessel, Diamantsturz, Kristallisieren; Passive
 * Nachwachsen und Diamanthaut (Resonanz sitzt in {@link DiamondheadAbilities}). Konstrukte sind echte, zeitweise Bloecke
 * ({@link TempBlocks}) und Kristall-Displays ({@link CrystalGrowth}); bei Prisma (volle Resonanz) wird jede Kraft groesser.
 */
final class DiamondheadMoves {
	private static final int CRYSTAL = DiamondheadAbilities.CRYSTAL;
	private static final int PRISM = DiamondheadAbilities.PRISM;
	/** letzter Kampf je Spieler (fuer Nachwachsen) */
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

	private DiamondheadMoves() {
	}

	private static BlockState crystal() {
		return PlanetBlocks.PETROSAPIEN_CRYSTAL.getDefaultState();
	}

	private static BlockState spike() {
		return PlanetBlocks.PETROSAPIEN_SPIKE.getDefaultState();
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_lance"), DiamondheadMoves::crystalLance);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_hammer"), DiamondheadMoves::crystalHammer);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_wall"), DiamondheadMoves::crystalWall);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_cage"), DiamondheadMoves::crystalCage);
		AbilityRegistry.register(KingdomOmnitrix.id("diamond_drill"), DiamondheadMoves::diamondDrill);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_cathedral"), DiamondheadMoves::crystalCathedral);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_ram"), DiamondheadMoves::crystalRam);
		AbilityRegistry.register(KingdomOmnitrix.id("shard_fall"), DiamondheadMoves::shardFall);
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_shackle"), DiamondheadMoves::crystalShackle);
		AbilityRegistry.register(KingdomOmnitrix.id("diamond_drop"), DiamondheadMoves::diamondDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("crystallize"), DiamondheadMoves::crystallize);
		PassiveRegistry.register(KingdomOmnitrix.id("crystal_regrowth"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// Petrosapiens wachsen nach: ohne Treffer seit einigen Sekunden heilt Diamondhead stetig
				ServerPlayerEntity player = ctx.player();
				long calm = ctx.now() - LAST_HURT.getOrDefault(player.getUuid(), 0L);
				if (ctx.now() % 20 == 0 && calm > ctx.param("calm_seconds", 4.0) * 20 && player.getHealth() < player.getMaxHealth()) {
					player.heal((float) ctx.param("heal", 1.0));
					Fx.shards(ctx.world(), player.getPos().add(0, 1.0, 0), CRYSTAL, 0.08f, 2);
				}
			}

			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				LAST_HURT.put(ctx.player().getUuid(), ctx.now());
			}

			@Override
			public void end(PassiveContext ctx) {
				LAST_HURT.remove(ctx.player().getUuid());
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("diamond_hide"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Nahkampf auf Diamant: weniger Schaden, oft splittert der Angreifer sich die Faust
				if (source.getAttacker() instanceof LivingEntity attacker && source.getSource() == attacker && attacker != ctx.player()) {
					if (ctx.world().random.nextDouble() < ctx.param("thorns_chance", 0.35)) {
						AlienKit.hit(attacker, ctx.world().getDamageSources().thorns(ctx.player()), (float) ctx.param("thorns", 3.0));
						Fx.shards(ctx.world(), AlienKit.body(attacker), CRYSTAL, 0.3f, 6);
					}
					return amount * (float) ctx.param("melee", 0.8);
				}
				return amount;
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Kristalllanze: eine Reihe Kristallspitzen bricht in Blickrichtung aus dem Boden, nacheinander bis 14 Bloecke weit. */
	private static boolean crystalLance(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		DiamondheadAbilities.Power power = DiamondheadAbilities.power(player);
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Vec3d start = player.getPos();
		int length = (int) (ctx.param("length", 14.0) * (power.prism() ? 1.4 : 1.0));
		float damage = (float) ctx.param("damage", 7.0) * power.factor();
		Set<UUID> hit = new HashSet<>();
		PowerTasks.start(player, length, 1, (world, p, age) -> {
			Vec3d at = AlienKit.ground(world, start.add(look.multiply(1.5 + age)).add(0, 2.0, 0), 5.0);
			CrystalGrowth.shard(world, at, crystal(), (float) Math.atan2(look.z, look.x), 0.35f, 0.5f, 1.6f + age * 0.06f, 80, CRYSTAL);
			for (LivingEntity target : AlienKit.around(world, p, at.add(0, 0.8, 0), 1.4)) {
				if (hit.add(target.getUuid())) {
					AlienKit.melee(world, p, target, damage);
					target.setVelocity(look.x * 0.4, 0.8, look.z * 0.4);
					target.velocityModified = true;
				}
			}
			Destruction.carve(world, p, at.add(0, 0.8, 0), look, 1.0, 0.8, AlienStrength.of(p) * 0.6f, 0.0);
			if (age % 2 == 0) {
				world.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.PLAYERS, 0.9f,
						0.8f + age * 0.03f);
			}
			return true;
		});
		Fx.flash(ctx.world(), start.add(look).add(0, 0.5, 0), CRYSTAL, 1.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
		return true;
	}

	/** Kristallhammer: der Arm wird zum Hammer — Schlag von oben in den Kegel vor ihm, Kristallsplitter, Krater. */
	private static boolean crystalHammer(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		DiamondheadAbilities.Power power = DiamondheadAbilities.power(player);
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Vec3d impact = AlienKit.ground(world, player.getPos().add(look.multiply(2.2)).add(0, 1.5, 0), 4.0);
		float damage = (float) ctx.param("damage", 10.0) * power.factor();
		double radius = ctx.param("radius", 3.0) * (power.prism() ? 1.5 : 1.0);
		for (LivingEntity target : AlienKit.blast(world, player, impact.add(0, 0.6, 0), radius, damage, 0.6, 0.6)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2), player);
		}
		Destruction.crater(world, player, impact.add(0, -0.4, 0), 1.6 * (power.prism() ? 1.5 : 1.0), 0.6, AlienStrength.of(player) * 1.2f, 0.3, 0.5);
		CrystalGrowth.cluster(world, impact, crystal(), 5, 1.6f, 0.5f, 60, CRYSTAL);
		Fx.shards(world, impact.add(0, 0.5, 0), CRYSTAL, 0.5f, 18);
		Fx.flash(world, impact.add(0, 0.6, 0), PRISM, 2.0f);
		Fx.shockwave(world, impact.add(0, 0.1, 0), CRYSTAL, (float) radius * 1.3f, 8);
		Fx.shake(world, impact, 0.35f, 8, 14);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, 1.4f, 0.5f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.7f);
		return true;
	}

	/** Kristallwand: eine Wand aus Kristall (5 breit, 3 hoch) waechst vor Diamondhead und haelt 8 Sekunden — Deckung. */
	private static boolean crystalWall(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		DiamondheadAbilities.Power power = DiamondheadAbilities.power(player);
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Vec3d side = new Vec3d(-look.z, 0, look.x);
		int half = power.prism() ? 3 : 2;
		int height = power.prism() ? 4 : 3;
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		Vec3d base = player.getPos().add(look.multiply(2.5));
		int placed = 0;
		for (int w = -half; w <= half; w++) {
			BlockPos column = TempBlocks.surface(world, BlockPos.ofFloored(base.add(side.multiply(w))), 4);
			for (int h = 0; h < height; h++) {
				BlockPos pos = column.up(h);
				if (TempBlocks.place(world, pos, h == height - 1 ? spike() : crystal(), ticks, player, CRYSTAL, false)) {
					placed++;
				}
			}
		}
		if (placed == 0) {
			return false;
		}
		Fx.shards(world, base.add(0, 1.2, 0), CRYSTAL, 0.3f, 14);
		Fx.flash(world, base.add(0, 1.2, 0), PRISM, 1.8f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 1.4f, 0.6f);
		return true;
	}

	/** Kristallkaefig: das Ziel im Blick wird in Kristall eingeschlossen (kann sich nicht bewegen), Resonanz steigt. */
	private static boolean crystalCage(AbilityContext ctx) {
		DiamondheadAbilities.Power power = DiamondheadAbilities.power(ctx.player());
		int ticks = (int) (ctx.param("seconds", 4.0) * 20 * (power.prism() ? 1.5 : 1.0));
		if (!PowerMoves.encase(ctx, ctx.param("range", 16.0), crystal(), ticks, CRYSTAL)) {
			return false;
		}
		DiamondheadAbilities.addCharge(ctx.player(), 10.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.2f);
		return true;
	}

	/** Diamantbohrer: beide Arme als Bohrer — Vorstoss 10 Bloecke weit durch Stein und Gegner. */
	private static boolean diamondDrill(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		DiamondheadAbilities.Power power = DiamondheadAbilities.power(player);
		Vec3d look = player.getRotationVec(1.0f);
		int ticks = (int) ctx.param("ticks", 14.0);
		float damage = (float) ctx.param("damage", 6.0) * power.factor();
		float strength = AlienStrength.of(player) * (power.prism() ? 2.0f : 1.4f);
		ctx.grantInvulnerability(ticks);
		Set<UUID> hit = new HashSet<>();
		PowerTasks.start(player, ticks, 1, (world, p, age) -> {
			BuiltinAbilities.launch(p, look.x * 0.75, look.y * 0.75 + 0.02, look.z * 0.75);
			Vec3d tip = p.getPos().add(0, p.getHeight() * 0.5, 0).add(look.multiply(0.9));
			Destruction.carve(world, p, tip, look, 1.2, 1.1, strength, 0.0);
			Fx.shards(world, tip, CRYSTAL, 0.25f, 3);
			for (LivingEntity target : AlienKit.around(world, p, tip, 1.6)) {
				if (hit.add(target.getUuid())) {
					AlienKit.melee(world, p, target, damage);
					AlienKit.push(target, p.getPos(), 1.0, 0.4);
				}
			}
			if (age % 3 == 0) {
				world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.9f, 1.4f);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 1.4f);
		return true;
	}

	/**
	 * Kristallkathedrale (Ultimate): drei Ringe riesiger Kristalle brechen nacheinander nach aussen aus dem Boden und
	 * zerspringen am Ende in einen Splitterregen auf alle Gegner im Umkreis.
	 */
	private static boolean crystalCathedral(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		DiamondheadAbilities.Power power = DiamondheadAbilities.power(player);
		Vec3d center = player.getPos();
		float damage = (float) ctx.param("damage", 12.0) * power.factor();
		double radius = ctx.param("radius", 12.0) * (power.prism() ? 1.3 : 1.0);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 50, 10, (world, p, age) -> {
			int ring = age / 10;
			if (ring < 3) {
				double r = radius * (ring + 1) / 3.0;
				int count = 6 + ring * 4;
				for (int i = 0; i < count; i++) {
					double a = i * MathHelper.TAU / count + ring * 0.3;
					Vec3d at = AlienKit.ground(world, center.add(Math.cos(a) * r, 3.0, Math.sin(a) * r), 8.0);
					CrystalGrowth.shard(world, at, crystal(), (float) a, 0.3f, 0.7f + ring * 0.2f, 3.0f + ring * 1.2f, 70, CRYSTAL);
				}
				DiamondheadAbilities.eruption(p, center, r, damage * 0.5f, 0.6, 8);
				Fx.shockwave(world, center.add(0, 0.1, 0), CRYSTAL, (float) r, 8);
				Fx.shake(world, center, 0.35f, 8, (int) r + 8);
				world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.6f, 0.5f + ring * 0.15f);
				return true;
			}
			// Splitterregen: auf jeden Gegner im Umkreis
			for (LivingEntity target : AlienKit.around(world, p, center, radius + 2.0)) {
				for (int i = 0; i < 3; i++) {
					HeroProjectileEntity.shootAt(world, p, ModItems.CRYSTAL_SHARD, target, 2.2f, 4.0f).withDamage(damage * 0.4f);
				}
			}
			Fx.flash(world, center.add(0, 2.0, 0), PRISM, 3.0f);
			Fx.shards(world, center.add(0, 2.0, 0), CRYSTAL, 0.7f, 40);
			world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 2.0f, 0.6f);
			return false;
		});
		Fx.charge(ctx.world(), player, CRYSTAL, 2.4f, 20);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.6f, 0.5f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Kristallramme (Sprint + Schlag): Schulter mit Kristallplatten voran — Ziel fliegt weit, Splitter spritzen. */
	private static boolean crystalRam(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		DiamondheadAbilities.Power power = DiamondheadAbilities.power(player);
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 6.0) * power.factor());
		target.setVelocity(dir.x * 1.8, 0.45, dir.z * 1.8);
		target.velocityModified = true;
		Fx.shards(ctx.world(), AlienKit.body(target), CRYSTAL, 0.45f, 12);
		Fx.flash(ctx.world(), AlienKit.body(target), PRISM, 1.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 1.4f, 0.7f);
		return true;
	}

	/** Splitterfall (Schlag in der Luft): Schlag nach unten, unter dem Ziel bricht eine Kristallspitze hervor. */
	private static boolean shardFall(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float damage = (float) ctx.param("damage", 7.0) * DiamondheadAbilities.power(player).factor();
		AlienKit.melee(world, player, target, damage);
		Vec3d ground = AlienKit.ground(world, target.getPos(), 6.0);
		CrystalGrowth.cluster(world, ground, crystal(), 4, 2.4f, 0.2f, 60, CRYSTAL);
		target.setVelocity(0.0, 0.9, 0.0);
		target.velocityModified = true;
		AlienKit.blast(world, player, ground.add(0, 0.6, 0), 2.2, damage * 0.5f, 0.6, 0.6);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.3, 0.35, player.getVelocity().z * 0.3);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.9f);
		return true;
	}

	/** Kristallfessel (Schleichen + Schlag): Kristall waechst ueber die Fuesse des Ziels — 2 Sekunden festgewachsen. */
	private static boolean crystalShackle(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 2.0) * 20);
		AlienKit.melee(world, player, target, (float) ctx.param("damage", 3.0));
		AlienKit.hold(world, target, ticks, AlienKit.dust("#3AE89A", 1.2f), false);
		// AE-Kristallkuppel schliesst das Ziel ein und zerspringt (AE-Animation)
		AttachedPropEntity.attach(world, "crystal_dome", target, Math.max(ticks, 80));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 6), player);
		Vec3d feet = target.getPos();
		for (int i = 0; i < 4; i++) {
			float a = i * MathHelper.TAU / 4 + 0.4f;
			CrystalGrowth.shard(world, feet.add(Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4), crystal(), a, 0.5f, 0.3f, 0.9f, ticks, CRYSTAL);
		}
		DiamondheadAbilities.addCharge(player, 6.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 1.0f, 1.2f);
		return true;
	}

	/** Diamantsturz (hoch in der Luft schleichen): Sturz, beim Aufprall wachsen Kristalle ringsum — Schaden nach Hoehe. */
	private static boolean diamondDrop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		DiamondheadAbilities.Power power = DiamondheadAbilities.power(player);
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 8.0) * power.factor();
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.0), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround()) {
				Fx.shards(world, p.getPos().add(0, 1.0, 0), CRYSTAL, 0.1f, 2);
				return true;
			}
			double height = Math.max(0.0, startY - p.getY());
			float boost = (float) Math.min(2.0, 1.0 + height / 15.0);
			DiamondheadAbilities.eruption(p, p.getPos(), 4.0 * boost, damage * boost, 0.7, 8);
			Destruction.crater(world, p, p.getPos().add(0, -0.5, 0), 1.4 * boost, 0.5, AlienStrength.of(p) * boost, 0.3, 0.4);
			Fx.flash(world, p.getPos().add(0, 0.5, 0), PRISM, 2.0f * boost);
			Fx.shake(world, p.getPos(), 0.35f * boost, 10, 18);
			world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.6f, 0.6f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
		return true;
	}

	/**
	 * Kristallisieren (Schlag gegen geschwaechtes Ziel): das Ziel wird von Kristall ueberwuchert und zerspringt — hoher
	 * Schaden, die Splitter fliegen auf die naechsten Gegner.
	 */
	private static boolean crystallize(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		DiamondheadAbilities.Power power = DiamondheadAbilities.power(player);
		float damage = ((float) ctx.param("damage", 12.0) + (target.getMaxHealth() - target.getHealth()) * 0.2f) * power.factor();
		Vec3d body = AlienKit.body(target);
		CrystalGrowth.cluster(world, target.getPos(), crystal(), 6, target.getHeight() * 1.2f, 0.3f, 30, CRYSTAL);
		AlienKit.melee(world, player, target, damage);
		Fx.flash(world, body, PRISM, 2.6f);
		Fx.shards(world, body, CRYSTAL, 0.6f, 30);
		for (LivingEntity near : AlienKit.around(world, player, body, 8.0)) {
			if (near != target) {
				HeroProjectileEntity.shootAt(world, player, ModItems.CRYSTAL_SHARD, near, 2.2f, 2.0f).withDamage(damage * 0.35f);
			}
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_GLASS_BREAK, 1.6f, 0.7f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.4f, 0.5f);
		return true;
	}
}
