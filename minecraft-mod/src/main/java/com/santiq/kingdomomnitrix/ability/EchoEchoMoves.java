package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.alien.EchoCloneEntity;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Echo Echo, Kraefte 2.0: Schallkugel, Echo-Tausch, Rueckkopplung, Bassdruck, Klon-Detonation, Schall-Symphonie;
 * Kombos Echo-Rempler, Tonfall, Volle Breitseite, Knalllandung, Resonanzbruch; Passive Sonorosianische Resonanz,
 * Echoortung und Schalldaempfung. Jeder Schalltreffer laeuft ueber {@link EchoEchoAbilities#soundHit}, zaehlt also fuer
 * die Resonanz — je mehr Quellen (Echo Echo und Klone) gleichzeitig treffen, desto staerker.
 */
final class EchoEchoMoves {
	private static final int SOUND = 0xC8E8FF;
	private static final int WHITE = 0xFFFFFF;

	private EchoEchoMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("sound_bullet"), EchoEchoMoves::soundBullet);
		AbilityRegistry.register(KingdomOmnitrix.id("echo_swap"), EchoEchoMoves::echoSwap);
		AbilityRegistry.register(KingdomOmnitrix.id("feedback_loop"), EchoEchoMoves::feedbackLoop);
		AbilityRegistry.register(KingdomOmnitrix.id("bass_drop"), EchoEchoMoves::bassDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("clone_burst"), EchoEchoMoves::cloneBurst);
		AbilityRegistry.register(KingdomOmnitrix.id("sonic_symphony"), EchoEchoMoves::sonicSymphony);
		AbilityRegistry.register(KingdomOmnitrix.id("echo_tackle"), EchoEchoMoves::echoTackle);
		AbilityRegistry.register(KingdomOmnitrix.id("drop_note"), EchoEchoMoves::dropNote);
		AbilityRegistry.register(KingdomOmnitrix.id("point_blank"), EchoEchoMoves::pointBlank);
		AbilityRegistry.register(KingdomOmnitrix.id("boom_landing"), EchoEchoMoves::boomLanding);
		AbilityRegistry.register(KingdomOmnitrix.id("shatter_frequency"), EchoEchoMoves::shatterFrequency);
		PassiveRegistry.register(KingdomOmnitrix.id("sonorosian_resonance"), new AlienPassive() {
			@Override
			public void melee(PassiveContext ctx, LivingEntity target, boolean fullSwing) {
				// jeder Schlag summt: zaehlt als Schallquelle — Schlag plus Schrei bringt Resonanz
				if (fullSwing) {
					EchoEchoAbilities.soundHit(ctx.world(), ctx.player(), target, (float) ctx.param("damage", 1.5), ctx.player().getPos(), 0.2);
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("echolocation"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// alle 2 s ein Ortungsruf: Gegner in der Naehe leuchten durch Waende
				if (ctx.now() % 40 != 0) {
					return;
				}
				ServerPlayerEntity player = ctx.player();
				for (LivingEntity target : AlienKit.around(ctx.world(), player, player.getPos(), ctx.param("radius", 16.0))) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 50, 0, false, false), player);
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("sound_dampening"), new AlienPassive() {
			@Override
			public float incoming(PassiveContext ctx, DamageSource source, float amount) {
				// Schall und Druckwellen gehen durch den Resonanzkoerper hindurch
				return source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isOf(net.minecraft.entity.damage.DamageTypes.SONIC_BOOM)
						? amount * (float) ctx.param("reduction", 0.5) : amount;
			}

			@Override
			public void afterHurt(PassiveContext ctx, DamageSource source, float damage) {
				// wer Echo Echo verletzt, wird von allen Klonen angeschrien
				if (source.getAttacker() instanceof LivingEntity attacker && attacker != ctx.player()) {
					for (EchoCloneEntity clone : EchoEchoAbilities.clones(ctx.player())) {
						if (clone.squaredDistanceTo(attacker) <= 144.0) {
							EchoEchoAbilities.soundHit(ctx.world(), ctx.player(), attacker, (float) ctx.param("retaliate", 1.5), clone.getPos(), 0.3);
							Fx.beam(ctx.world(), clone.getEyePos(), AlienKit.body(attacker), SOUND, 0.08f, 4);
						}
					}
				}
			}
		});
	}

	/** Echo Echo und alle Klone als Schallquellen (Augenhoehe). */
	private static List<Vec3d> voices(ServerPlayerEntity player) {
		List<Vec3d> list = new java.util.ArrayList<>();
		list.add(player.getEyePos());
		for (EchoCloneEntity clone : EchoEchoAbilities.clones(player)) {
			list.add(clone.getEyePos());
		}
		return list;
	}

	/** Mindestens {@code count} Klone sicherstellen. */
	private static List<EchoCloneEntity> ensureClones(ServerWorld world, ServerPlayerEntity player, int count) {
		List<EchoCloneEntity> clones = EchoEchoAbilities.clones(player);
		if (clones.size() < count) {
			EchoEchoAbilities.spawnClones(world, player, count - clones.size(), EchoEchoAbilities.CLONE_LIFE);
			clones = EchoEchoAbilities.clones(player);
		}
		return clones;
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Schallkugel: gebuendelter Schuss aus Echo Echo und jedem Klon auf das Ziel im Blick — viele Quellen, viel Resonanz. */
	private static boolean soundBullet(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		AlienKit.Beam beam = AlienKit.beam(world, player, ctx.param("range", 20.0));
		float damage = (float) ctx.param("damage", 3.0);
		for (Vec3d from : voices(player)) {
			Fx.beam(world, from, beam.end(), SOUND, 0.12f, 5);
			beam.target().ifPresent(target -> EchoEchoAbilities.soundHit(world, player, target, damage, from, 0.3));
		}
		Fx.shockwave(world, beam.end(), SOUND, 1.5f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 0.6f, 2.0f);
		return true;
	}

	/** Echo-Tausch: tauscht den Platz mit dem Klon, der am ehesten in Blickrichtung steht — Schallknall an beiden Stellen. */
	private static boolean echoSwap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		Optional<EchoCloneEntity> best = EchoEchoAbilities.clones(player).stream()
				.max(Comparator.comparingDouble(c -> c.getPos().subtract(player.getPos()).normalize().dotProduct(look)));
		if (best.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		EchoCloneEntity clone = best.get();
		Vec3d mine = player.getPos();
		Vec3d theirs = clone.getPos();
		player.requestTeleport(theirs.x, theirs.y, theirs.z);
		clone.requestTeleport(mine.x, mine.y, mine.z);
		player.fallDistance = 0.0f;
		float damage = (float) ctx.param("damage", 3.0);
		for (Vec3d at : List.of(mine, theirs)) {
			for (LivingEntity target : AlienKit.around(world, player, at.add(0, 0.8, 0), 3.0)) {
				EchoEchoAbilities.soundHit(world, player, target, damage, at, 0.8);
			}
			Fx.shockwave(world, at.add(0, 0.6, 0), SOUND, 3.0f, 8);
		}
		Fx.beam(world, mine.add(0, 1, 0), theirs.add(0, 1, 0), WHITE, 0.15f, 6);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.8f);
		return true;
	}

	/** Rueckkopplung: die Klone umstellen das Ziel im Blick und schreien 3 s lang von allen Seiten hinein. */
	private static boolean feedbackLoop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> found = com.santiq.kingdomomnitrix.util.Targeting.findMeleeTarget(player, ctx.param("range", 14.0), 0.85,
				e -> AlienKit.foe(player, e));
		if (found.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		List<EchoCloneEntity> clones = ensureClones(world, player, 3);
		for (int i = 0; i < clones.size(); i++) {
			double a = i * MathHelper.TAU / clones.size();
			Vec3d at = target.getPos().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
			clones.get(i).requestTeleport(at.x, AlienKit.ground(world, at.add(0, 1.5, 0), 4.0).y, at.z);
		}
		float damage = (float) ctx.param("damage", 2.0);
		AlienKit.hold(world, target, (int) (ctx.param("seconds", 3.0) * 20), net.minecraft.particle.ParticleTypes.NOTE, false);
		PowerTasks.start(player, (int) (ctx.param("seconds", 3.0) * 20), 10, (w, p, age) -> {
			if (!target.isAlive()) {
				return false;
			}
			for (EchoCloneEntity clone : EchoEchoAbilities.clones(p)) {
				if (clone.squaredDistanceTo(target) < 64.0) {
					EchoEchoAbilities.soundHit(w, p, target, damage, clone.getPos(), 0.0);
					Fx.beam(w, clone.getEyePos(), AlienKit.body(target), SOUND, 0.1f, 5);
				}
			}
			w.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.8f, 0.5f + age * 0.02f);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BELL_RESONATE, 1.2f, 1.6f);
		return true;
	}

	/** Bassdruck: tiefer Ton in den Boden — Welle um Echo Echo und jeden Klon, Gegner fliegen hoch und taumeln. */
	private static boolean bassDrop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 4.5);
		float damage = (float) ctx.param("damage", 4.0);
		List<Vec3d> centers = new java.util.ArrayList<>();
		centers.add(player.getPos());
		EchoEchoAbilities.clones(player).forEach(c -> centers.add(c.getPos()));
		for (Vec3d center : centers) {
			for (LivingEntity target : AlienKit.around(world, player, center.add(0, 0.5, 0), radius)) {
				EchoEchoAbilities.soundHit(world, player, target, damage, center, 0.4);
				target.addVelocity(0.0, 0.6, 0.0);
				target.velocityModified = true;
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0, false, true), player);
			}
			Fx.shockwave(world, center.add(0, 0.2, 0), SOUND, (float) radius, 10);
		}
		Fx.shake(world, player.getPos(), 0.3f, 8, 16);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.5f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 2.0f, 0.5f);
		return true;
	}

	/** Klon-Detonation: alle Klone zerplatzen in Schallexplosionen — je mehr Klone, desto verheerender. */
	private static boolean cloneBurst(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		List<EchoCloneEntity> clones = EchoEchoAbilities.clones(player);
		if (clones.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		float damage = (float) ctx.param("damage", 5.0);
		double radius = ctx.param("radius", 3.5);
		for (EchoCloneEntity clone : clones) {
			Vec3d at = clone.getPos().add(0, 0.6, 0);
			for (LivingEntity target : AlienKit.around(world, player, at, radius)) {
				EchoEchoAbilities.soundHit(world, player, target, damage, at, 1.0);
			}
			Fx.explosion(world, at, SOUND, (float) radius * 0.6f);
			Fx.shockwave(world, at, WHITE, (float) radius, 8);
			world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.6f, 1.8f);
			clone.vanish(world);
		}
		return true;
	}

	/**
	 * Schall-Symphonie (Ultimate): der volle Chor stellt sich im Kreis um den Zielpunkt auf und spielt 5 s lang — jede
	 * halbe Sekunde trifft jede Stimme alles in der Mitte; zum Schluss ein Crescendo.
	 */
	private static boolean sonicSymphony(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d center = AlienKit.ground(world, AlienKit.aim(world, player, ctx.param("range", 20.0)), 8.0);
		double ring = ctx.param("ring", 6.0);
		double radius = ctx.param("radius", 5.0);
		float damage = (float) ctx.param("damage", 2.0);
		int ticks = (int) (ctx.param("seconds", 5.0) * 20);
		List<EchoCloneEntity> clones = ensureClones(world, player, EchoEchoAbilities.maxClones(player));
		for (int i = 0; i < clones.size(); i++) {
			double a = i * MathHelper.TAU / clones.size();
			Vec3d at = center.add(Math.cos(a) * ring, 0, Math.sin(a) * ring);
			clones.get(i).requestTeleport(at.x, AlienKit.ground(world, at.add(0, 2.0, 0), 5.0).y, at.z);
		}
		ctx.grantInvulnerability(ticks);
		PowerTasks.start(player, ticks + 1, 10, (w, p, age) -> {
			List<Vec3d> sources = voices(p);
			for (LivingEntity target : AlienKit.around(w, p, center.add(0, 1.0, 0), radius)) {
				for (Vec3d from : sources) {
					EchoEchoAbilities.soundHit(w, p, target, age >= ticks ? damage * 3.0f : damage, from, 0.0);
				}
			}
			for (Vec3d from : sources) {
				Fx.beam(w, from, center.add(0, 1.0, 0), SOUND, 0.1f, 6);
			}
			Fx.shockwave(w, center.add(0, 0.3, 0), age >= ticks ? WHITE : SOUND, (float) radius, 8);
			w.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.2f,
					0.5f + (age / (float) ticks) * 1.5f);
			if (age >= ticks) {
				Fx.flash(w, center.add(0, 1.0, 0), WHITE, 5.0f);
				Fx.shake(w, center, 0.6f, 14, 30);
				w.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.5f, 1.0f);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.EVENT_RAID_HORN.value(), 1.0f, 2.0f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Echo-Rempler (Sprint + Schlag): Schallrempler — das Ziel fliegt, und am Aufprallort entsteht ein neuer Klon. */
	private static boolean echoTackle(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		Vec3d dir = BuiltinAbilities.horizontalLook(player);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 5.0));
		EchoEchoAbilities.soundHit(ctx.world(), player, target, 1.5f, player.getPos(), 1.2);
		target.addVelocity(dir.x * 0.4, 0.3, dir.z * 0.4);
		EchoEchoAbilities.spawnClones(ctx.world(), player, 1, EchoEchoAbilities.CLONE_LIFE);
		Fx.send(ctx.world(), Fx.Kind.SHOCKWAVE, AlienKit.body(target), dir, SOUND, 1.6f, 6, 1, null);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 2.0f);
		return true;
	}

	/** Tonfall (Schlag in der Luft): Schallstoss nach unten — Ziel zu Boden, kleine Welle ringsum. */
	private static boolean dropNote(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		AlienKit.melee(world, player, target, (float) ctx.param("damage", 5.0));
		target.setVelocity(0.0, -1.0, 0.0);
		target.velocityModified = true;
		for (LivingEntity near : AlienKit.around(world, player, target.getPos().add(0, 0.5, 0), 2.5)) {
			EchoEchoAbilities.soundHit(world, player, near, 2.0f, AlienKit.body(player), 0.4);
		}
		Fx.shockwave(world, target.getPos().add(0, 0.2, 0), SOUND, 2.5f, 8);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.5f, 0.6f);
		return true;
	}

	/** Volle Breitseite (Schleichen + Schlag): Schrei aus naechster Naehe — trifft doppelt (Resonanz) und laesst taumeln. */
	private static boolean pointBlank(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float damage = (float) ctx.param("damage", 3.0);
		// Mund und Brustkorb: zwei Quellen auf einmal
		EchoEchoAbilities.soundHit(world, player, target, damage, player.getEyePos(), 0.6);
		EchoEchoAbilities.soundHit(world, player, target, damage, player.getPos(), 0.6);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, false, true), player);
		EchoEchoAbilities.screamFx(world, player.getEyePos(), BuiltinAbilities.horizontalLook(player), 3.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PARROT_IMITATE_WARDEN, 1.2f, 1.8f);
		return true;
	}

	/** Knalllandung (hoch in der Luft schleichen): Sturz mit Schallknall — alle Klone pulsieren im selben Moment. */
	private static boolean boomLanding(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double startY = player.getY() + player.fallDistance;
		float damage = (float) ctx.param("damage", 5.0);
		double radius = ctx.param("radius", 3.5);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.2, -ctx.param("dive", 2.0), player.getVelocity().z * 0.2);
		ctx.grantInvulnerability(40);
		PowerTasks.start(player, 60, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 15.0);
			List<Vec3d> centers = new java.util.ArrayList<>();
			centers.add(p.getPos());
			EchoEchoAbilities.clones(p).forEach(c -> centers.add(c.getPos()));
			for (Vec3d center : centers) {
				for (LivingEntity target : AlienKit.around(world, p, center.add(0, 0.5, 0), radius * boost)) {
					EchoEchoAbilities.soundHit(world, p, target, damage * boost, center, 0.8);
				}
				Fx.shockwave(world, center.add(0, 0.2, 0), SOUND, (float) (radius * boost), 10);
			}
			world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0f, 1.4f);
			return false;
		});
		return true;
	}

	/** Resonanzbruch (Schlag gegen geschwaechtes Ziel): die Eigenfrequenz des Ziels — jeder Klon schreit mit, jeder macht es schlimmer. */
	private static boolean shatterFrequency(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		List<EchoCloneEntity> clones = EchoEchoAbilities.clones(player);
		float damage = (float) (ctx.param("damage", 8.0) + clones.size() * ctx.param("per_clone", 2.0));
		AlienKit.melee(world, player, target, damage);
		for (EchoCloneEntity clone : clones) {
			if (target.isAlive()) {
				EchoEchoAbilities.soundHit(world, player, target, 2.0f, clone.getPos(), 0.2);
			}
			Fx.beam(world, clone.getEyePos(), AlienKit.body(target), SOUND, 0.12f, 6);
		}
		Fx.flash(world, AlienKit.body(target), WHITE, 2.4f);
		Fx.shockwave(world, AlienKit.body(target), SOUND, 3.0f, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_GLASS_BREAK, 1.5f, 1.6f);
		return true;
	}
}
