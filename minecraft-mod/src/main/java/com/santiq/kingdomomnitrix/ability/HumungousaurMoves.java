package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Debris;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.vfx.EarthWave;
import com.santiq.kingdomomnitrix.vfx.Fx;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Humungousaur, Kraefte 2.0: Felsbrocken, Urschrei, Bauchklatscher, Stachelplatten, Donnerklatschen, Aussterben;
 * Kombos Dino-Ansturm, Hammerschlag, Baerengriff, Bebenlandung, Riesenwurf; Passive Vaxasaurier-Wachstum
 * (Kern-System), Dicke Haut und Erderschuetterer. Alles skaliert mit {@link HumungousaurAbilities#size}.
 */
final class HumungousaurMoves {
	private static final int DUST = HumungousaurAbilities.DUST;
	private static final int IMPACT = HumungousaurAbilities.IMPACT;
	private static final int SKIN = HumungousaurAbilities.SKIN;

	private HumungousaurMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("boulder_toss"), HumungousaurMoves::boulderToss);
		AbilityRegistry.register(KingdomOmnitrix.id("primal_roar"), HumungousaurMoves::primalRoar);
		AbilityRegistry.register(KingdomOmnitrix.id("belly_flop"), HumungousaurMoves::bellyFlop);
		AbilityRegistry.register(KingdomOmnitrix.id("spike_plates"), HumungousaurMoves::spikePlates);
		AbilityRegistry.register(KingdomOmnitrix.id("titan_clap"), HumungousaurMoves::titanClap);
		AbilityRegistry.register(KingdomOmnitrix.id("extinction"), HumungousaurMoves::extinction);
		AbilityRegistry.register(KingdomOmnitrix.id("dino_charge"), HumungousaurMoves::dinoCharge);
		AbilityRegistry.register(KingdomOmnitrix.id("hammer_drop"), HumungousaurMoves::hammerDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("bear_hug"), HumungousaurMoves::bearHug);
		AbilityRegistry.register(KingdomOmnitrix.id("quake_landing"), HumungousaurMoves::quakeLanding);
		AbilityRegistry.register(KingdomOmnitrix.id("giant_toss"), HumungousaurMoves::giantToss);
		PassiveRegistry.register(KingdomOmnitrix.id("vaxasaurian_growth"), new AlienPassive() {
			@Override
			public void afterHit(PassiveContext ctx, LivingEntity target, DamageSource source, float damage) {
				// eigene Bonus-Treffer zaehlen schon in hit(); alles andere, was er austeilt, laesst ihn wachsen
				if (!HumungousaurAbilities.bonusHit) {
					HumungousaurAbilities.addGrowth(ctx.player(), damage * HumungousaurAbilities.GROWTH_DEALT * (float) ctx.param("rate", 1.0));
				}
			}

			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				// eingesteckter Schaden laesst ihn staerker wachsen
				HumungousaurAbilities.addGrowth(ctx.player(), damage * HumungousaurAbilities.GROWTH_TAKEN * (float) ctx.param("rate", 1.0));
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("thick_hide"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// je Wachstumsstufe prallt ein fester Teil jedes Treffers ab — hoechstens die Haelfte
				float flat = (float) ctx.param("flat", 1.0) * (1 + HumungousaurAbilities.stage(ctx.player()));
				return Math.max(amount * 0.5f, amount - flat);
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("earthshaker"), new AlienPassive() {
			@Override
			public void melee(PassiveContext ctx, LivingEntity target, boolean fullSwing) {
				// volle Schlaege lassen den Boden um das Ziel beben
				if (fullSwing && target.isOnGround()) {
					ServerPlayerEntity player = ctx.player();
					double size = HumungousaurAbilities.size(player);
					for (LivingEntity near : AlienKit.around(ctx.world(), player, target.getPos().add(0, 0.5, 0), ctx.param("radius", 2.0) * size)) {
						if (near != target && near.isOnGround()) {
							AlienKit.push(near, target.getPos(), 0.4, 0.4);
						}
					}
					EarthWave.ring(ctx.world(), target.getPos(), 1.5 * size, 3, 0.35f);
				}
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Felsbrocken: reisst einen Brocken aus dem Boden und wirft ihn — Einschlag mit Druckwelle, groesser je Stufe. */
	private static boolean boulderToss(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double size = HumungousaurAbilities.size(player);
		Vec3d flat = BuiltinAbilities.horizontalLook(player);
		BlockPos source = BlockPos.ofFloored(player.getPos().add(flat.multiply(1.5 * size))).down();
		BlockState state = world.getBlockState(source);
		float strength = AlienStrength.of(player);
		if (state.isAir() || !state.getFluidState().isEmpty() || !Destruction.breakable(world, source, state, player, strength)) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_ground").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		float damage = (float) (ctx.param("damage", 9.0) * size);
		double radius = ctx.param("radius", 3.0) * size;
		Destruction.crater(world, player, Vec3d.ofCenter(source), 1.2 * size, strength, 0.0, 0.3);
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d start = player.getEyePos().add(look.multiply(1.5)).add(0, 0.5, 0);
		Debris.projectile(world, player, state, start, look.multiply(ctx.param("speed", 1.6)).add(0, 0.2, 0), (float) (1.6 * size), (w, at) -> {
			AlienKit.blast(w, player, at, radius, damage, 1.2, 0.5);
			Destruction.crater(w, player, at, 1.4 * size, strength * 0.8f, 0.4, 0.5);
			Fx.explosion(w, at, DUST, (float) radius * 0.6f);
			w.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 0.7f);
		});
		Fx.smoke(world, Vec3d.ofCenter(source), DUST, 1.2f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_STONE_BREAK, 1.5f, 0.5f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ATTACK, 1.0f, 0.7f);
		return true;
	}

	/** Urschrei: Gegner im Umkreis werden schwach und weichen zurueck, Verbuendete stark — und er waechst sofort ein Stueck. */
	private static boolean primalRoar(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 10.0) * HumungousaurAbilities.size(player);
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		for (LivingEntity target : AlienKit.around(world, player, player.getPos(), radius)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1, false, true), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks / 2, 1, false, true), player);
			AlienKit.push(target, player.getPos(), 1.0, 0.2);
		}
		for (ServerPlayerEntity ally : world.getPlayers(p -> p != player && p.squaredDistanceTo(player) <= radius * radius && PartyRules.isAlly(player, p))) {
			ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 0, false, true));
		}
		HumungousaurAbilities.addGrowth(player, (float) ctx.param("growth", 20.0));
		Fx.send(world, Fx.Kind.SHOCKWAVE, player.getEyePos(), BuiltinAbilities.horizontalLook(player), IMPACT, (float) radius * 0.5f, 10, 1, null);
		Fx.shake(world, player.getPos(), 0.4f, 12, (int) radius * 2);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.6f, 0.5f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.8f);
		return true;
	}

	/** Bauchklatscher: Sprung nach vorn und mit vollem Gewicht auf den Boden — Flaechenschaden je nach Groesse. */
	private static boolean bellyFlop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d flat = BuiltinAbilities.horizontalLook(player);
		double size = HumungousaurAbilities.size(player);
		float damage = (float) (ctx.param("damage", 8.0) * size);
		double radius = ctx.param("radius", 4.0) * size;
		BuiltinAbilities.launch(player, flat.x * 1.0, 0.9, flat.z * 1.0);
		ctx.grantInvulnerability(30);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (age < 4 || !p.isOnGround()) {
				return true;
			}
			HumungousaurAbilities.stomp(world, p, radius, damage, 0.7);
			Destruction.crater(world, p, p.getPos().add(0, -0.6, 0), 1.2 * size, 0.4, AlienStrength.of(p), 0.3, 0.4);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 0.6f);
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_STEP, 1.4f, 0.6f);
		return true;
	}

	/** Stachelplatten: Knochenplatten stellen sich 10 s auf — Ruestung, Stacheln stechen Angreifer, kein Schrumpfen. */
	private static boolean spikePlates(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 10.0) * 20);
		float thorns = (float) ctx.param("damage", 3.0);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 0, false, false));
		Fx.aura(ctx.world(), player, SKIN, 1.4f, ticks);
		Fx.shards(ctx.world(), AlienKit.body(player), SKIN, 0.3f, 16);
		PowerTasks.start(player, ticks, 10, (world, p, age) -> {
			// Platten halten das Wachstum: kein Abbau, solange sie stehen
			HumungousaurAbilities.addGrowth(p, 1.0f);
			for (LivingEntity target : AlienKit.around(world, p, AlienKit.body(p), 1.6 * HumungousaurAbilities.size(p))) {
				AlienKit.hit(target, world.getDamageSources().thorns(p), thorns);
				Fx.sparks(world, AlienKit.body(target), SKIN, 0.3f, 4);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.2f, 0.5f);
		return true;
	}

	/** Donnerklatschen: klatscht die Pranken zusammen — Druckkegel nach vorn, Gegner fliegen und sind benommen. */
	private static boolean titanClap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double size = HumungousaurAbilities.size(player);
		Vec3d look = player.getRotationVec(1.0f);
		float damage = (float) (ctx.param("damage", 6.0) * size);
		for (LivingEntity target : AlienKit.cone(world, player, ctx.param("range", 9.0) * size, 0.5)) {
			HumungousaurAbilities.hit(world, player, target, damage);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0, false, true), player);
			target.setVelocity(look.x * 1.8, 0.5, look.z * 1.8);
			target.velocityModified = true;
		}
		Vec3d front = player.getEyePos().add(look.multiply(1.5));
		Fx.flash(world, front, IMPACT, 2.4f);
		for (int i = 1; i <= 3; i++) {
			Fx.send(world, Fx.Kind.SHOCKWAVE, player.getEyePos().add(look.multiply(i * 2.5)), look, IMPACT, (float) (1.5 + i * size), 8, 1, null);
		}
		Fx.shake(world, player.getPos(), 0.35f, 8, 16);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.5f);
		return true;
	}

	/** Aussterben (Ultimate): wie ein Meteor — hoher Sprung, Einschlag mit riesigem Krater und drei Bebenwellen. */
	private static boolean extinction(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d flat = BuiltinAbilities.horizontalLook(player);
		double size = HumungousaurAbilities.size(player);
		float damage = (float) (ctx.param("damage", 14.0) * size);
		double radius = ctx.param("radius", 6.0) * size;
		HumungousaurAbilities.addGrowth(player, HumungousaurAbilities.MAX);
		BuiltinAbilities.launch(player, flat.x * 0.8, ctx.param("jump", 2.2), flat.z * 0.8);
		ctx.grantInvulnerability(100);
		int[] waves = {-1};
		Vec3d[] landed = {null};
		PowerTasks.start(player, 140, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (landed[0] == null) {
				if (age > 10 && !p.isOnGround()) {
					BuiltinAbilities.launch(p, p.getVelocity().x, Math.min(p.getVelocity().y, -1.6), p.getVelocity().z);
					Fx.flames(world, p.getPos().add(0, 0.5, 0), new Vec3d(0, 0.2, 0), 0xFF9A3A, 0.5f, 3);
				}
				if (age < 6 || !p.isOnGround()) {
					return true;
				}
				landed[0] = p.getPos();
				Destruction.crater(world, p, p.getPos().add(0, -0.8, 0), 3.0 * Math.min(size, 1.6), 0.6, AlienStrength.of(p) * 1.6f, 0.5, 0.6);
				Fx.explosion(world, p.getPos().add(0, 0.5, 0), 0xFF9A3A, (float) radius * 0.5f);
				Fx.flash(world, p.getPos().add(0, 1.0, 0), 0xFFFFFF, 5.0f);
				waves[0] = age;
			}
			int wave = (age - waves[0]) / 8;
			if ((age - waves[0]) % 8 == 0 && wave < 3) {
				double r = radius * (0.6 + wave * 0.4);
				for (LivingEntity target : AlienKit.around(world, p, landed[0].add(0, 0.5, 0), r)) {
					HumungousaurAbilities.hit(world, p, target, damage * (1.0f - wave * 0.25f));
					AlienKit.push(target, landed[0], 1.2, 0.8);
				}
				EarthWave.ring(world, landed[0], r, 6, 1.2f);
				Fx.shockwave(world, landed[0].add(0, 0.2, 0), DUST, (float) r, 12);
				Fx.shake(world, landed[0], 0.9f - wave * 0.2f, 14, (int) r * 3);
				world.playSound(null, BlockPos.ofFloored(landed[0]), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.6f, 0.5f + wave * 0.1f);
			}
			return wave < 3;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.6f, 0.4f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Dino-Ansturm (Sprint + Schlag): rammt mit dem ganzen Koerper — das Ziel fliegt weit. */
	private static boolean dinoCharge(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		double size = HumungousaurAbilities.size(player);
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		HumungousaurAbilities.hit(ctx.world(), player, target, (float) (ctx.param("damage", 7.0) * size));
		target.setVelocity(dir.x * 1.6 * size, 0.6, dir.z * 1.6 * size);
		target.velocityModified = true;
		Fx.flash(ctx.world(), AlienKit.body(target), IMPACT, 1.8f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ATTACK, 1.2f, 0.8f);
		return true;
	}

	/** Hammerschlag (Schlag in der Luft): beide Pranken von oben — Ziel in den Boden, kleines Beben. */
	private static boolean hammerDrop(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double size = HumungousaurAbilities.size(player);
		HumungousaurAbilities.hit(world, player, target, (float) (ctx.param("damage", 8.0) * size));
		target.setVelocity(0.0, -1.4, 0.0);
		target.velocityModified = true;
		EarthWave.ring(world, target.getPos(), 2.0 * size, 4, 0.6f);
		Fx.shockwave(world, target.getPos().add(0, 0.2, 0), DUST, (float) (2.5 * size), 8);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.6f);
		return true;
	}

	/** Baerengriff (Schleichen + Schlag): packt das Ziel und drueckt zu — drei Quetsch-Treffer, dann loslassen. */
	private static boolean bearHug(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		float damage = (float) (ctx.param("damage", 3.0) * HumungousaurAbilities.size(player));
		AlienKit.hold(ctx.world(), target, 30, ParticleTypes.CRIT, false);
		PowerTasks.start(player, 30, 10, (world, p, age) -> {
			if (!target.isAlive() || target.squaredDistanceTo(p) > 25.0) {
				return false;
			}
			HumungousaurAbilities.hit(world, p, target, damage);
			Fx.flash(world, AlienKit.body(target), IMPACT, 1.0f);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 0.6f);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.2f);
		return true;
	}

	/** Bebenlandung (hoch in der Luft schleichen): faellt mit voller Masse — Beben nach Hoehe und Groesse. */
	private static boolean quakeLanding(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		double size = HumungousaurAbilities.size(player);
		float damage = (float) (ctx.param("damage", 8.0) * size);
		double radius = ctx.param("radius", 4.0) * size;
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.0), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround()) {
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			HumungousaurAbilities.stomp(world, p, radius * boost, damage * boost, 0.8);
			Destruction.crater(world, p, p.getPos().add(0, -0.6, 0), 1.4 * boost, 0.5, AlienStrength.of(p) * boost, 0.4, 0.5);
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 0.7f);
			return false;
		});
		return true;
	}

	/** Riesenwurf (Schlag gegen geschwaechtes Ziel): hebt das Ziel ueber den Kopf und schleudert es hoch und weit. */
	private static boolean giantToss(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		double size = HumungousaurAbilities.size(player);
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		HumungousaurAbilities.hit(ctx.world(), player, target, (float) ((ctx.param("damage", 10.0) + (target.getMaxHealth() - target.getHealth()) * 0.2) * size));
		target.setVelocity(dir.x * 2.2 * size, 1.4, dir.z * 2.2 * size);
		target.velocityModified = true;
		Fx.send(ctx.world(), Fx.Kind.SHOCKWAVE, AlienKit.body(target), dir, IMPACT, 2.4f, 8, 1, null);
		Fx.shake(ctx.world(), target.getPos(), 0.3f, 8, 16);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 0.9f);
		return true;
	}
}
