package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.passive.AlienPassive;
import com.santiq.kingdomomnitrix.ability.passive.PassiveContext;
import com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Wildmutt, Kraefte 2.0 — blinder Jaeger: Knochenbrecher-Biss, Wandsprung, Stachelteppich, Jagdgeheul,
 * Niederreissen, Wilde Hatz; Kombos Rammsprung, Stachelsturz, Kehlenbiss, Raubtiersprung, Zerfleischen; Passive
 * Witterung und Ueberlebensinstinkt (die Jagd selbst sitzt in {@link WildmuttAbilities}). Alles spielt mit der Beute:
 * wer markiert ist, blutet laenger und laesst den Blutrausch steigen.
 */
final class WildmuttMoves {
	private static final int ORANGE = 0xFF8A1E;
	private static final int BLOOD = 0x9A0A0A;
	private static final int QUILL = 0xE8D8B0;

	private WildmuttMoves() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("crunch_bite"), WildmuttMoves::crunchBite);
		AbilityRegistry.register(KingdomOmnitrix.id("wall_leap"), WildmuttMoves::wallLeap);
		AbilityRegistry.register(KingdomOmnitrix.id("quill_carpet"), WildmuttMoves::quillCarpet);
		AbilityRegistry.register(KingdomOmnitrix.id("hunting_howl"), WildmuttMoves::huntingHowl);
		AbilityRegistry.register(KingdomOmnitrix.id("drag_down"), WildmuttMoves::dragDown);
		AbilityRegistry.register(KingdomOmnitrix.id("wild_hunt"), WildmuttMoves::wildHunt);
		AbilityRegistry.register(KingdomOmnitrix.id("tackle_pounce"), WildmuttMoves::tacklePounce);
		AbilityRegistry.register(KingdomOmnitrix.id("quill_dive"), WildmuttMoves::quillDive);
		AbilityRegistry.register(KingdomOmnitrix.id("throat_bite"), WildmuttMoves::throatBite);
		AbilityRegistry.register(KingdomOmnitrix.id("predator_drop"), WildmuttMoves::predatorDrop);
		AbilityRegistry.register(KingdomOmnitrix.id("rip_apart"), WildmuttMoves::ripApart);
		PassiveRegistry.register(KingdomOmnitrix.id("scent_sight"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// blind, aber die Nase sieht alles: Blindheit/Dunkelheit wirken nicht, Unsichtbare werden gerochen
				ServerPlayerEntity player = ctx.player();
				player.removeStatusEffect(StatusEffects.BLINDNESS);
				player.removeStatusEffect(StatusEffects.DARKNESS);
				if (ctx.now() % 40 == 0) {
					for (LivingEntity hidden : AlienKit.around(ctx.world(), player, player.getPos(), ctx.param("radius", 16.0))) {
						if (hidden.isInvisible()) {
							hidden.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 50, 0, true, false), player);
						}
					}
				}
			}
		});
		PassiveRegistry.register(KingdomOmnitrix.id("survival_instinct"), new AlienPassive() {
			@Override
			public void tick(PassiveContext ctx) {
				// in die Enge getrieben wird das Tier gefaehrlich: unter 40 % Leben schneller und staerker
				ServerPlayerEntity player = ctx.player();
				if (ctx.now() % 20 == 0 && player.getHealth() <= player.getMaxHealth() * ctx.param("threshold", 0.4)) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30, 1, true, false, true));
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 30, 0, true, false, true));
					Fx.sparks(ctx.world(), player.getPos().add(0, 0.8, 0), BLOOD, 0.2f, 3);
				}
			}
		});
	}

	// --- Tasten-Faehigkeiten ---------------------------------------------------------------------

	/** Knochenbrecher-Biss: die Kiefer schnappen zu — viel Schaden, das Ziel ist geschwaecht und wird zur Beute. */
	private static boolean crunchBite(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 3.5), 0.4, e -> AlienKit.foe(player, e));
		if (found.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		WildmuttAbilities.mark(player, target);
		float damage = (float) ctx.param("damage", 8.0) * (1.0f + WildmuttAbilities.frenzy(player) * 0.1f);
		AlienKit.melee(ctx.world(), player, target, damage);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1), player);
		WildmuttAbilities.bleed(player, target, 100, 1.2f);
		Fx.flash(ctx.world(), AlienKit.body(target), ORANGE, 1.4f);
		Fx.sparks(ctx.world(), AlienKit.body(target), BLOOD, 0.4f, 10);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WOLF_GROWL, 1.2f, 0.6f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_FOX_BITE, 1.4f, 0.6f);
		return true;
	}

	/** Wandsprung: weiter Satz; trifft er auf eine Wand, stoesst er sich ab und springt zurueck (bis zu zwei Mal). */
	private static boolean wallLeap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = player.getRotationVec(1.0f);
		double power = ctx.param("power", 1.5);
		BuiltinAbilities.launch(player, look.x * power, Math.max(0.5, look.y * power + 0.5), look.z * power);
		int[] bounces = {(int) ctx.param("bounces", 2.0)};
		Vec3d[] dir = {new Vec3d(look.x, 0, look.z).normalize()};
		PowerTasks.start(player, 50, 1, (world, p, age) -> {
			p.fallDistance = 0.0f;
			if (age > 3 && p.isOnGround()) {
				return false;
			}
			if (bounces[0] > 0 && !world.isSpaceEmpty(p, p.getBoundingBox().offset(dir[0].multiply(0.3)))) {
				bounces[0]--;
				dir[0] = dir[0].multiply(-1.0);
				BuiltinAbilities.launch(p, dir[0].x * power, 0.7, dir[0].z * power);
				Fx.shockwave(world, p.getPos().add(0, 0.8, 0), ORANGE, 1.6f, 6);
				world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WOLF_STEP, SoundCategory.PLAYERS, 1.2f, 0.8f);
			}
			return true;
		});
		Fx.smoke(ctx.world(), player.getPos(), 0xA89A88, 0.8f, 3);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WOLF_GROWL, 0.8f, 1.2f);
		return true;
	}

	/** Stachelteppich: Stacheln regnen ringsum auf den Boden — wer darueber laeuft, wird 8 s lang verletzt und gebremst. */
	private static boolean quillCarpet(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d center = player.getPos();
		double radius = ctx.param("radius", 4.5);
		float damage = (float) ctx.param("damage", 2.0);
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		for (int i = 0; i < 16; i++) {
			double a = i * MathHelper.TAU / 16;
			Fx.shards(ctx.world(), center.add(Math.cos(a) * radius * 0.6, 0.6, Math.sin(a) * radius * 0.6), QUILL, 0.25f, 2);
		}
		PowerTasks.start(player, ticks, 10, (world, p, age) -> {
			for (int i = 0; i < 4; i++) {
				double a = world.random.nextDouble() * MathHelper.TAU;
				double r = Math.sqrt(world.random.nextDouble()) * radius;
				Fx.sparks(world, center.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), QUILL, 0.05f, 1);
			}
			for (LivingEntity target : AlienKit.around(world, p, center.add(0, 0.5, 0), radius)) {
				if (target.isOnGround()) {
					AlienKit.melee(world, p, target, damage);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 2), p);
				}
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP, 1.2f, 0.7f);
		return true;
	}

	/** Jagdgeheul: alle Gegner im Umkreis leuchten (Witterung), die naechste wird Beute, Blutrausch +2, Gruppe schneller. */
	private static boolean huntingHowl(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 24.0);
		List<LivingEntity> foes = AlienKit.around(world, player, player.getPos(), radius);
		for (LivingEntity foe : foes) {
			foe.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0), player);
		}
		WildmuttAbilities.nearestEnemy(player, radius, null).ifPresent(prey -> WildmuttAbilities.mark(player, prey));
		WildmuttAbilities.setFrenzy(player, WildmuttAbilities.frenzy(player) + 2);
		for (ServerPlayerEntity ally : world.getPlayers(p -> p.squaredDistanceTo(player) <= 144 && !com.santiq.kingdomomnitrix.party.PartyRules.canHarm(player, p))) {
			ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 160, 0), player);
		}
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 160, 1, false, false));
		Fx.shockwave(world, player.getPos().add(0, 0.1, 0), ORANGE, (float) Math.min(radius, 14.0), 16);
		Fx.send(world, Fx.Kind.SHOCKWAVE, player.getEyePos(), new Vec3d(0, 1, 0), ORANGE, 3.0f, 12, 1, null);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WOLF_HOWL, 1.6f, 0.6f);
		return true;
	}

	/** Niederreissen: Satz auf die Beute (sonst den naechsten Gegner), drueckt sie 3 s zu Boden und zerfleischt sie. */
	private static boolean dragDown(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = WildmuttAbilities.prey(player)
				.filter(p -> p.squaredDistanceTo(player) <= 144)
				.or(() -> WildmuttAbilities.nearestEnemy(player, ctx.param("range", 12.0), null));
		if (found.isEmpty()) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.kingdomomnitrix.no_target").formatted(net.minecraft.util.Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		WildmuttAbilities.mark(player, target);
		int ticks = (int) (ctx.param("seconds", 3.0) * 20);
		float damage = (float) ctx.param("damage", 2.5);
		UUID id = target.getUuid();
		AlienKit.hold(ctx.world(), target, ticks, AlienKit.dust("#9A0A0A", 1.0f), false);
		ctx.grantInvulnerability(10);
		PowerTasks.start(player, ticks, 1, (world, p, age) -> {
			if (!(world.getEntity(id) instanceof LivingEntity t) || !t.isAlive()) {
				return false;
			}
			p.requestTeleport(t.getX(), t.getY() + Math.min(0.8, t.getHeight() * 0.5), t.getZ());
			p.fallDistance = 0.0f;
			if (age % 8 == 0) {
				AlienKit.melee(world, p, t, damage * (1.0f + WildmuttAbilities.frenzy(p) * 0.15f));
				Fx.sparks(world, AlienKit.body(t), BLOOD, 0.35f, 8);
				world.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_WOLF_GROWL, SoundCategory.PLAYERS, 1.0f, 0.6f + world.random.nextFloat() * 0.3f);
			}
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.6f);
		return true;
	}

	/** Wilde Hatz (Ultimate): 6 s lang springt Wildmutt von Gegner zu Gegner, beisst jeden und heilt mit jedem Biss. */
	private static boolean wildHunt(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		float damage = (float) ctx.param("damage", 7.0);
		double range = ctx.param("range", 12.0);
		ctx.grantInvulnerability(ticks);
		Set<UUID> recent = new HashSet<>();
		PowerTasks.start(player, ticks, 8, (world, p, age) -> {
			if (recent.size() > 3) {
				recent.clear();
			}
			Optional<LivingEntity> next = AlienKit.around(world, p, p.getPos(), range).stream()
					.filter(e -> !recent.contains(e.getUuid()))
					.min(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(p)));
			if (next.isEmpty()) {
				return true;
			}
			LivingEntity t = next.get();
			recent.add(t.getUuid());
			Vec3d from = p.getPos().add(0, 0.6, 0);
			p.requestTeleport(t.getX(), t.getY(), t.getZ());
			Fx.beam(world, from, AlienKit.body(t), ORANGE, 0.15f, 6);
			WildmuttAbilities.mark(p, t);
			AlienKit.melee(world, p, t, damage * (1.0f + WildmuttAbilities.frenzy(p) * 0.1f));
			WildmuttAbilities.bleed(p, t, 100, 1.5f);
			p.heal(damage * 0.3f);
			Fx.sparks(world, AlienKit.body(t), BLOOD, 0.4f, 12);
			world.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_FOX_BITE, SoundCategory.PLAYERS, 1.4f, 0.6f);
			return true;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WOLF_HOWL, 1.8f, 0.5f);
		return true;
	}

	// --- Kombos ----------------------------------------------------------------------------------

	/** Rammsprung (Sprint + Schlag): Wildmutt wirft sich mit vollem Gewicht auf das Ziel — umgeworfen, blutet. */
	private static boolean tacklePounce(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		WildmuttAbilities.mark(player, target);
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 5.0));
		AlienKit.push(target, player.getPos(), 1.2, 0.3);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 4), player);
		WildmuttAbilities.bleed(player, target, 80, 1.0f);
		Fx.flash(ctx.world(), AlienKit.body(target), ORANGE, 1.2f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WOLF_GROWL, 1.2f, 0.8f);
		return true;
	}

	/** Stachelsturz (Schlag in der Luft): ein Stachelregen nach unten auf das Ziel und alles darum herum. */
	private static boolean quillDive(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		for (LivingEntity near : AlienKit.around(world, player, target.getPos(), 3.0)) {
			for (int i = 0; i < 2; i++) {
				HeroProjectileEntity.shootAt(world, player, net.minecraft.item.Items.ARROW, near, 2.0f, 6.0f).withDamage((float) ctx.param("damage", 3.0));
			}
		}
		Fx.shards(world, player.getPos().add(0, 0.8, 0), QUILL, 0.4f, 16);
		BuiltinAbilities.launch(player, player.getVelocity().x * 0.3, 0.4, player.getVelocity().z * 0.3);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PUFFER_FISH_STING, 1.2f, 0.8f);
		return true;
	}

	/** Kehlenbiss (Schleichen + Schlag): lautloser Biss — die Beute blutet doppelt so lange und wird schwach. */
	private static boolean throatBite(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		boolean prey = WildmuttAbilities.prey(player).filter(target::equals).isPresent();
		AlienKit.melee(ctx.world(), player, target, (float) ctx.param("damage", 4.0) * (prey ? 1.5f : 1.0f));
		WildmuttAbilities.bleed(player, target, prey ? 200 : 100, prey ? 1.6f : 1.0f);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 1), player);
		WildmuttAbilities.mark(player, target);
		Fx.sparks(ctx.world(), target.getEyePos(), BLOOD, 0.3f, 8);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_FOX_BITE, 1.0f, 0.5f);
		return true;
	}

	/** Raubtiersprung (hoch in der Luft schleichen): Sturz auf den naechsten Gegner darunter, drueckt ihn zu Boden. */
	private static boolean predatorDrop(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> below = AlienKit.around(world, player, player.getPos().add(0, -4.0, 0), ctx.param("radius", 6.0)).stream()
				.min(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
		float damage = (float) ctx.param("damage", 7.0);
		if (below.isEmpty()) {
			BuiltinAbilities.launch(player, 0.0, -1.6, 0.0);
			return true;
		}
		LivingEntity target = below.get();
		Vec3d to = target.getPos().add(0, target.getHeight(), 0).subtract(player.getPos()).normalize().multiply(1.8);
		BuiltinAbilities.launch(player, to.x, to.y, to.z);
		UUID id = target.getUuid();
		PowerTasks.start(player, 30, 1, (w, p, age) -> {
			p.fallDistance = 0.0f;
			if (!(w.getEntity(id) instanceof LivingEntity t) || !t.isAlive()) {
				return false;
			}
			if (p.squaredDistanceTo(t) < 4.0 || p.isOnGround()) {
				WildmuttAbilities.mark(p, t);
				AlienKit.melee(w, p, t, damage);
				AlienKit.hold(w, t, 30, AlienKit.dust("#9A0A0A", 1.0f), false);
				WildmuttAbilities.bleed(p, t, 100, 1.2f);
				Fx.shockwave(w, t.getPos().add(0, 0.1, 0), ORANGE, 2.5f, 6);
				w.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_WOLF_GROWL, SoundCategory.PLAYERS, 1.4f, 0.6f);
				return false;
			}
			return true;
		});
		return true;
	}

	/** Zerfleischen (Schlag gegen geschwaechtes Ziel): wilder Angriff — hoher Schaden, Heilung, Geheul, naechste Beute. */
	private static boolean ripApart(AbilityContext ctx) {
		LivingEntity target = ctx.target();
		if (target == null) {
			return false;
		}
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float damage = (float) ctx.param("damage", 10.0) * (1.0f + WildmuttAbilities.frenzy(player) * 0.15f);
		AlienKit.melee(world, player, target, damage);
		player.heal(damage * (float) ctx.param("heal", 0.3));
		WildmuttAbilities.setFrenzy(player, WildmuttAbilities.MAX_FRENZY);
		Fx.sparks(world, AlienKit.body(target), BLOOD, 0.6f, 24);
		Fx.flash(world, AlienKit.body(target), ORANGE, 2.0f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_RAVAGER_ROAR, 1.0f, 1.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WOLF_HOWL, 1.0f, 0.8f);
		return true;
	}
}
