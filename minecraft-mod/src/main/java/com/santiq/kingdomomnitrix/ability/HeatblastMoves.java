package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Heatblast-Kombos: Glutansturm, Feuerkomet, Glutgriff, Phoenix-Sturz, Einaeschern; Passive Glutkoerper und Hitzeschleier (der
 * Pyronit-Kern selbst sitzt in {@link HeatblastAbilities}). Alles nutzt die Kernhitze: Gluehend staerker, ueberhitzt
 * entlaedt die naechste Kraft als Blaufeuer.
 */
final class HeatblastMoves {
	private static final int FIRE = HeatblastAbilities.FIRE;
	private static final int EMBER = 0xFFB040;

	private HeatblastMoves() {
	}

	static void register() {
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
