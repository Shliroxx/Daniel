package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Heatblast, Kraefte 2.0: Flammenwerfer, Feuerrolle, Hitze aufnehmen, Feuertornado, Lavafontaene, Meteorhagel; Kombos
 * Glutansturm, Feuerkomet, Glutgriff, Phoenix-Sturz, Einaeschern; Passive Glutkoerper und Hitzeschleier (der
 * Pyronit-Kern selbst sitzt in {@link HeatblastAbilities}). Alles nutzt die Kernhitze: Gluehend staerker, ueberhitzt
 * entlaedt die naechste Kraft als Blaufeuer.
 */
final class HeatblastMoves {
	private static final int FIRE = HeatblastAbilities.FIRE;
	private static final int EMBER = 0xFFB040;
	private static final int MAGMA = 0xFF4A10;

	private HeatblastMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("flamethrower"), HeatblastMoves::flamethrower);
		AbilityRegistry.register(KingdomOmnitrix.id("blaze_roll"), HeatblastMoves::blazeRoll);
		AbilityRegistry.register(KingdomOmnitrix.id("heat_absorb"), HeatblastMoves::heatAbsorb);
		AbilityRegistry.register(KingdomOmnitrix.id("fire_tornado"), HeatblastMoves::fireTornado);
		AbilityRegistry.register(KingdomOmnitrix.id("magma_eruption"), HeatblastMoves::magmaEruption);
		AbilityRegistry.register(KingdomOmnitrix.id("meteor_rain"), HeatblastMoves::meteorRain);
		AbilityRegistry.register(KingdomOmnitrix.id("blazing_rush"), HeatblastMoves::blazingRush);
		AbilityRegistry.register(KingdomOmnitrix.id("comet_kick"), HeatblastMoves::cometKick);
		AbilityRegistry.register(KingdomOmnitrix.id("ember_grip"), HeatblastMoves::emberGrip);
		AbilityRegistry.register(KingdomOmnitrix.id("phoenix_dive"), HeatblastMoves::phoenixDive);
		AbilityRegistry.register(KingdomOmnitrix.id("incinerate"), HeatblastMoves::incinerate);
		PassiveRegistry.register(KingdomOmnitrix.id("molten_touch"), new AlienPassive() {
			@Override
			public void melee(PassiveContext ctx, LivingEntity target, boolean fullSwing) {
				// jeder Schlag mit gluehenden Faeusten: Brand, ab Gluehend Zusatz-Feuerschaden
				target.setOnFireFor((float) ctx.param("fire_seconds", 4.0));
				if (HeatblastAbilities.heat(ctx.player()) >= HeatblastAbilities.GLOW) {
					AlienKit.hit(target, ctx.world().getDamageSources().onFire(), (float) ctx.param("glow_damage", 2.0));
				}
				Fx.flames(ctx.world(), AlienKit.body(target), Vec3d.ZERO, FIRE, 0.25f, 4);
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("heat_haze"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Geschosse verbrennen im Hitzeflimmern, bevor sie treffen (je heisser, desto oefter)
				if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
					double chance = ctx.param("chance", 0.25) + HeatblastAbilities.heat(ctx.player()) / HeatblastAbilities.MAX * 0.25;
					if (ctx.world().random.nextDouble() < chance) {
						Fx.flash(ctx.world(), ctx.player().getEyePos(), EMBER, 1.2f);
						Fx.smoke(ctx.world(), ctx.player().getEyePos(), 0x4A4440, 0.5f, 2);
						return 0.0f;
					}
				}
				// Kaelte trifft einen Pyroniten hart
				if (source.isIn(DamageTypeTags.IS_FREEZING)) {
					return amount * 1.5f;
				}
				return amount;
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Flammenwerfer: 1,5 s Feuerstrom im Kegel, trifft mehrmals, zuendet Boden und Gegner. */
	private static boolean flamethrower(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		int ticks = (int) ctx.param("ticks", 30.0);
		double range = ctx.param("range", 7.0) * (power.blue() ? 1.4 : 1.0);
		float damage = (float) ctx.param("damage", 1.6) * power.factor();
		int color = power.color();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 1, false, false));
		PowerTasks.start(player, ticks, 1, (world, p, age) -> {
			Vec3d look = p.getRotationVec(1.0f);
			Vec3d hand = p.getEyePos().add(look.multiply(0.7)).add(0, -0.3, 0);
			Fx.flames(world, hand, look.multiply(0.6), color, 0.22f, 4);
			if (age % 4 == 0) {
				for (LivingEntity target : AlienKit.cone(world, p, range, 0.82)) {
					HeatblastAbilities.hit(p, target, damage, 3.0f);
				}
				Destruction.ignite(world, p, p.getEyePos().add(look.multiply(range * 0.7)), 1.5, 0.15);
			}
			if (age % 6 == 0) {
				world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.PLAYERS, 0.8f, 0.8f);
			}
			return true;
		});
		HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 12.0));
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_FIRE, 1.0f, 0.8f);
		return true;
	}

	/** Feuerrolle: kurzer Sprung als brennendes Rad, unverwundbar waehrenddessen, hinterlaesst eine Flammenspur. */
	private static boolean blazeRoll(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		double power = ctx.param("power", 1.6);
		BuiltinAbilities.launch(player, look.x * power, 0.25, look.z * power);
		ctx.grantInvulnerability((int) ctx.param("invulnerable_ticks", 10));
		float damage = (float) ctx.param("damage", 4.0);
		Set<UUID> hit = new HashSet<>();
		PowerTasks.start(player, 12, 1, (world, p, age) -> {
			Fx.flames(world, p.getPos().add(0, 0.6, 0), Vec3d.ZERO, FIRE, 0.45f, 5);
			for (LivingEntity target : AlienKit.around(world, p, p.getPos().add(0, 0.8, 0), 1.6)) {
				if (hit.add(target.getUuid())) {
					HeatblastAbilities.hit(p, target, damage, 4.0f);
				}
			}
			if (age % 3 == 0) {
				Destruction.ignite(world, p, p.getPos(), 0.8, 0.5);
			}
			return true;
		});
		Fx.flash(ctx.world(), player.getPos().add(0, 0.8, 0), EMBER, 1.4f);
		HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 6.0));
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_FIRECHARGE_USE, 1.0f, 1.3f);
		return true;
	}

	/**
	 * Hitze aufnehmen: zieht Feuer und Glut aus der Umgebung in den Koerper — loescht Brand, kuehlt Lava zu Obsidian
	 * bzw. Bruchstein, heilt und fuellt die Kernhitze je aufgenommener Quelle.
	 */
	private static boolean heatAbsorb(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int radius = (int) ctx.param("radius", 6.0);
		int sources = 0;
		BlockPos center = player.getBlockPos();
		for (BlockPos pos : BlockPos.iterate(center.add(-radius, -2, -radius), center.add(radius, 3, radius))) {
			if (pos.getSquaredDistance(center) > radius * radius || sources >= 40) {
				continue;
			}
			BlockState state = world.getBlockState(pos);
			BlockState cooled = null;
			if (state.isIn(BlockTags.FIRE)) {
				cooled = Blocks.AIR.getDefaultState();
			} else if (state.isOf(Blocks.LAVA)) {
				cooled = state.getFluidState().isStill() ? Blocks.OBSIDIAN.getDefaultState() : Blocks.COBBLESTONE.getDefaultState();
			} else if (state.isOf(Blocks.MAGMA_BLOCK)) {
				cooled = Blocks.NETHERRACK.getDefaultState();
			}
			if (cooled != null && player.canModifyAt(world, pos)) {
				world.setBlockState(pos, cooled);
				Vec3d from = Vec3d.ofCenter(pos);
				Fx.flames(world, from, player.getPos().add(0, 1.0, 0).subtract(from).multiply(0.12), EMBER, 0.1f, 2);
				sources++;
			}
		}
		for (LivingEntity burning : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius), e -> e.isOnFire() && e != player)) {
			burning.extinguish();
			Fx.flames(world, AlienKit.body(burning), player.getPos().add(0, 1.0, 0).subtract(AlienKit.body(burning)).multiply(0.12), EMBER, 0.1f, 3);
			sources++;
		}
		// auch ohne Quellen: Restwaerme der Luft (wenig)
		float gain = 6.0f + sources * (float) ctx.param("heat_per_source", 4.0);
		HeatblastAbilities.addHeat(player, gain);
		player.heal(2.0f + sources * 0.5f);
		Fx.charge(world, player, EMBER, 2.4f, 16);
		Fx.aura(world, player, FIRE, 1.2f, 30);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.2f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.6f);
		return true;
	}

	/** Feuertornado: Flammenwirbel am Zielpunkt, wandert langsam mit dem Blick, zieht Gegner hinein und verbrennt sie. */
	private static boolean fireTornado(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		Vec3d start = AlienKit.ground(ctx.world(), AlienKit.aim(ctx.world(), player, ctx.param("range", 14.0)), 8.0);
		int ticks = (int) (ctx.param("seconds", 5.0) * 20);
		float damage = (float) ctx.param("damage", 2.5) * power.factor();
		double radius = ctx.param("radius", 3.0) * (power.blue() ? 1.4 : 1.0);
		int color = power.color();
		Vec3d[] at = {start};
		PowerTasks.start(player, ticks, 1, (world, p, age) -> {
			// folgt dem Blick, hoechstens 0,12 Bloecke je Tick
			Vec3d want = AlienKit.ground(world, AlienKit.aim(world, p, 16.0), 8.0);
			Vec3d step = want.subtract(at[0]);
			if (step.lengthSquared() > 0.0144) {
				step = step.normalize().multiply(0.12);
			}
			at[0] = at[0].add(step.x, 0, step.z);
			at[0] = AlienKit.ground(world, at[0].add(0, 2, 0), 6.0);
			if (age % 2 == 0) {
				double spin = age * 0.5;
				for (int i = 0; i < 4; i++) {
					double h = i * 1.1;
					double r = 0.6 + h * 0.35;
					double a = spin + i * 1.7;
					Vec3d tangent = new Vec3d(-Math.sin(a), 0.25, Math.cos(a)).multiply(0.25);
					Fx.flames(world, at[0].add(Math.cos(a) * r, h + 0.2, Math.sin(a) * r), tangent, color, 0.2f, 2);
				}
			}
			if (age % 10 == 0) {
				for (LivingEntity target : AlienKit.around(world, p, at[0].add(0, 1.5, 0), radius + 2.0)) {
					Vec3d pull = at[0].subtract(target.getPos()).multiply(1, 0, 1);
					if (pull.lengthSquared() > 0.25) {
						target.addVelocity(pull.normalize().x * 0.35, 0.15, pull.normalize().z * 0.35);
						target.velocityModified = true;
					}
					if (target.getPos().distanceTo(at[0]) <= radius) {
						HeatblastAbilities.hit(p, target, damage, 4.0f);
					}
				}
				Destruction.ignite(world, p, at[0], 1.5, 0.3);
				world.playSound(null, BlockPos.ofFloored(at[0]), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.7f, 0.5f);
			}
			return true;
		});
		Fx.shockwave(ctx.world(), start.add(0, 0.1, 0), color, (float) radius * 1.5f, 10);
		HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 18.0));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_AMBIENT, 1.2f, 0.5f);
		return true;
	}

	/**
	 * Lavafontaene: der Boden unter dem Zielpunkt bricht auf — drei Glutsaeulen schiessen nacheinander hoch, schleudern
	 * Gegner in die Luft und hinterlassen einen brennenden Krater.
	 */
	private static boolean magmaEruption(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		Vec3d center = AlienKit.ground(ctx.world(), AlienKit.aim(ctx.world(), player, ctx.param("range", 16.0)), 10.0);
		float damage = (float) ctx.param("damage", 9.0) * power.factor();
		int color = power.color();
		Fx.scorch(ctx.world(), center, 3.0f, 200);
		Fx.shake(ctx.world(), center, 0.25f, 10, 16);
		PowerTasks.start(player, 30, 10, (world, p, age) -> {
			int n = age / 10;
			double a = n * MathHelper.TAU / 3 + 0.4;
			Vec3d at = n == 0 ? center : center.add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
			for (int h = 0; h < 6; h++) {
				Fx.flames(world, at.add(0, h * 0.7, 0), new Vec3d(0, 0.35, 0), h < 2 ? MAGMA : color, 0.35f, 4);
			}
			Fx.explosion(world, at.add(0, 0.4, 0), color, 1.4f);
			for (LivingEntity target : AlienKit.blast(world, p, at.add(0, 1.0, 0), 2.6, damage, 0.2, 1.0)) {
				target.setOnFireFor(6.0f);
			}
			Destruction.crater(world, p, at.add(0, -0.4, 0), 1.6, 0.6, AlienStrength.of(p), 0.3, 0.7);
			Destruction.ignite(world, p, at, 1.8, 0.4);
			world.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_LAVA_POP, SoundCategory.PLAYERS, 1.5f, 0.6f);
			world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8f, 1.1f);
			return true;
		});
		HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 18.0));
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_FIRE, 1.2f, 0.6f);
		return true;
	}

	/** Meteorhagel (Ultimate): vier Sekunden lang schlagen gluehende Brocken im Zielgebiet ein. */
	private static boolean meteorRain(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		Vec3d center = AlienKit.ground(ctx.world(), AlienKit.aim(ctx.world(), player, ctx.param("range", 24.0)), 16.0);
		double area = ctx.param("area", 7.0) * (power.blue() ? 1.4 : 1.0);
		float damage = (float) ctx.param("damage", 8.0) * power.factor();
		int color = power.color();
		int ticks = (int) (ctx.param("seconds", 4.0) * 20);
		Fx.flash(ctx.world(), player.getPos().add(0, 2.5, 0), color, 3.0f);
		Fx.flames(ctx.world(), player.getPos().add(0, 2.0, 0), new Vec3d(0, 0.8, 0), color, 0.3f, 20);
		PowerTasks.start(player, ticks, 4, (world, p, age) -> {
			var random = world.getRandom();
			double a = random.nextDouble() * MathHelper.TAU;
			double r = Math.sqrt(random.nextDouble()) * area;
			Vec3d impact = AlienKit.ground(world, center.add(Math.cos(a) * r, 6.0, Math.sin(a) * r), 14.0);
			Vec3d sky = impact.add(-3.0, 18.0, -2.0);
			// Brocken faellt sichtbar (Glutschweif), schlaegt kurz danach ein
			Fx.flames(world, sky, impact.subtract(sky).multiply(0.07), color, 0.15f, 10);
			Fx.beam(world, sky, impact, color, 0.6f, 4);
			Fx.explosion(world, impact.add(0, 0.5, 0), color, 1.6f);
			Fx.scorch(world, impact, 1.6f, 160);
			for (LivingEntity target : AlienKit.blast(world, p, impact.add(0, 0.8, 0), 2.4, damage, 0.6, 0.4)) {
				target.setOnFireFor(5.0f);
			}
			Destruction.crater(world, p, impact.add(0, -0.3, 0), 1.5, 0.6, AlienStrength.of(p) * 1.2f, 0.2, 0.6);
			Destruction.ignite(world, p, impact, 1.6, 0.3);
			Fx.shake(world, impact, 0.2f, 6, 20);
			world.playSound(null, BlockPos.ofFloored(impact), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f,
					0.8f + random.nextFloat() * 0.3f);
			return true;
		});
		ctx.grantInvulnerability(20);
		HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 30.0));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WITHER_SHOOT, 1.0f, 0.6f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Glutansturm (Sprint + Schlag): gluehende Faust voran, Ziel fliegt brennend weg, Flammenschweif. */
	private static boolean blazingRush(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		HeatblastAbilities.hit(player, target, (float) ctx.param("damage", 6.0) * power.factor(), 6.0f);
		target.setVelocity(dir.x * 1.6, 0.45, dir.z * 1.6);
		target.velocityModified = true;
		Fx.flames(ctx.world(), AlienKit.body(target), dir.multiply(0.5), power.color(), 0.3f, 14);
		Fx.flash(ctx.world(), AlienKit.body(target), EMBER, 1.6f);
		HeatblastAbilities.addHeat(player, 5.0f);
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_FIRE, 1.0f, 1.3f);
		return true;
	}

	/** Feuerkomet (Schlag in der Luft): Tritt von oben, das Ziel kracht mit Explosion in den Boden. */
	private static boolean cometKick(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		float damage = (float) ctx.param("damage", 7.0) * power.factor();
		HeatblastAbilities.hit(player, target, damage, 5.0f);
		target.setVelocity(0.0, -1.6, 0.0);
		target.velocityModified = true;
		Vec3d ground = AlienKit.ground(ctx.world(), target.getPos(), 6.0);
		Fx.flames(ctx.world(), AlienKit.body(target), new Vec3d(0, -0.6, 0), power.color(), 0.3f, 12);
		Fx.explosion(ctx.world(), ground.add(0, 0.4, 0), power.color(), 1.6f);
		AlienKit.blast(ctx.world(), player, ground.add(0, 0.6, 0), 2.6, damage * 0.5f, 0.6, 0.3).forEach(t -> t.setOnFireFor(4.0f));
		Destruction.crater(ctx.world(), player, ground.add(0, -0.3, 0), 1.3, 0.6, AlienStrength.of(player), 0.2, 0.5);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.3, 0.4, player.getVelocity().z * 0.3);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.8f, 1.3f);
		return true;
	}

	/** Glutgriff (Schleichen + Schlag): packt das Ziel mit gluehender Hand — haelt es fest und brennt es eine Sekunde lang. */
	private static boolean emberGrip(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		float damage = (float) ctx.param("damage", 2.5) * power.factor();
		AlienKit.hold(ctx.world(), target, 24, AlienKit.dust("#FF8A20", 1.2f), false);
		UUID id = target.getUuid();
		PowerTasks.start(player, 24, 6, (world, p, age) -> {
			if (!(world.getEntity(id) instanceof LivingEntity held) || !held.isAlive()) {
				return false;
			}
			HeatblastAbilities.hit(p, held, damage, 3.0f);
			Fx.flames(world, AlienKit.body(held), new Vec3d(0, 0.15, 0), power.color(), 0.4f, 6);
			Fx.smoke(world, AlienKit.body(held).add(0, 0.6, 0), 0x3A3430, 0.5f, 1);
			world.playSound(null, held.getBlockPos(), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.4f);
			return true;
		});
		HeatblastAbilities.addHeat(player, 4.0f);
		return true;
	}

	/** Phoenix-Sturz (hoch in der Luft schleichen): stuerzt als Feuerball herab; Einschlag waechst mit der Hoehe. */
	private static boolean phoenixDive(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 8.0) * power.factor();
		int color = power.color();
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.0), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround()) {
				Fx.flames(world, p.getPos().add(0, 0.8, 0), new Vec3d(0, 0.3, 0), color, 0.4f, 5);
				return true;
			}
			double height = Math.max(0.0, startY - p.getY());
			float boost = (float) Math.min(2.0, 1.0 + height / 15.0);
			Vec3d at = p.getPos();
			Fx.explosion(world, at.add(0, 0.6, 0), color, 2.0f + boost);
			Fx.shockwave(world, at.add(0, 0.1, 0), color, 4.0f * boost, 12);
			Fx.scorch(world, at, 2.5f * boost, 200);
			Fx.shake(world, at, 0.4f * boost, 10, 20);
			AlienKit.blast(world, p, at.add(0, 0.8, 0), 3.5 * boost, damage * boost, 1.0, 0.5).forEach(t -> t.setOnFireFor(6.0f));
			Destruction.crater(world, p, at.add(0, -0.5, 0), 1.6 * boost, 0.5, AlienStrength.of(p) * boost, 0.4, 0.5);
			Destruction.ignite(world, p, at, 3.0 * boost, 0.25);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.4f, 0.8f);
			return false;
		});
		HeatblastAbilities.addHeat(player, 8.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_FIRECHARGE_USE, 1.2f, 0.5f);
		return true;
	}

	/**
	 * Einaeschern (Schlag gegen geschwaechtes Ziel): ganze Kernhitze in einen Griff — hoher Feuerschaden; faellt das Ziel,
	 * explodiert es und setzt Gegner ringsum in Brand.
	 */
	private static boolean incinerate(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		float heat = HeatblastAbilities.heat(player);
		float damage = ((float) ctx.param("damage", 10.0) + heat * (float) ctx.param("per_heat", 0.12)) * power.factor();
		Vec3d body = AlienKit.body(target);
		HeatblastAbilities.hit(player, target, damage, 8.0f);
		Fx.flames(world, body, new Vec3d(0, 0.4, 0), power.color(), 0.5f, 24);
		Fx.flash(world, body, EMBER, 2.4f);
		if (!target.isAlive()) {
			Fx.explosion(world, body, power.color(), 2.4f);
			AlienKit.blast(world, player, body, 4.0, damage * 0.4f, 0.8, 0.3).forEach(t -> t.setOnFireFor(6.0f));
			Destruction.ignite(world, player, target.getPos(), 2.5, 0.3);
		}
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_FIRE, 1.4f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_DEATH, 0.8f, 0.8f);
		return true;
	}
}
