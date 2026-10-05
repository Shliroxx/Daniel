package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Spidermonkey (Arachnachimmia). Eigenes System „Netzjaeger“ — ohne Aura:
 *
 * <ul>
 *   <li><b>Netze</b> halten Gegner fest (keine Bewegung). Eingewebte Ziele nehmen von Spidermonkey +50 % Schaden — und
 *       sein Nahkampf trifft mit allen vier Armen (drei Zusatzschlaege).</li>
 *   <li><b>Netzschwung</b> zieht ihn zu jedem Punkt, den er anvisiert (Wand, Decke, Boden).</li>
 *   <li><b>Wandlauf</b> (passiv): wer gegen eine Wand laeuft, klettert sie hoch.</li>
 * </ul>
 */
final class SpidermonkeyAbilities {
	private static final Identifier SPIDERMONKEY = KingdomOmnitrix.id("spidermonkey");
	private static final DustParticleEffect WEB = AlienKit.dust("#F2F2F2", 1.0f);
	private static final DustParticleEffect WEB_THICK = AlienKit.dust("#E0E0E0", 1.6f);

	/** Netzschwung: Zielpunkt und Ende */
	private static final Map<UUID, Swing> SWINGS = new HashMap<>();
	/** Affenwirbel: Schlaege uebrig */
	private static final Map<UUID, int[]> FRENZY = new HashMap<>();

	private record Swing(Vec3d anchor, long until) {
	}

	private SpidermonkeyAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("web_shot"), SpidermonkeyAbilities::webShot);
		AbilityRegistry.register(KingdomOmnitrix.id("web_net"), SpidermonkeyAbilities::webNet);
		AbilityRegistry.register(KingdomOmnitrix.id("web_swing"), SpidermonkeyAbilities::webSwing);
		AbilityRegistry.register(KingdomOmnitrix.id("monkey_frenzy"), SpidermonkeyAbilities::monkeyFrenzy);
		AbilityRegistry.register(KingdomOmnitrix.id("web_trap"), SpidermonkeyAbilities::webTrap);
		AbilityRegistry.register(KingdomOmnitrix.id("web_cocoon"), SpidermonkeyAbilities::webCocoon);
		ServerTickEvents.END_SERVER_TICK.register(SpidermonkeyAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SWINGS.remove(handler.getPlayer().getUuid());
			FRENZY.remove(handler.getPlayer().getUuid());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken <= 0.0f || AlienKit.bonusHit || !(source.getAttacker() instanceof ServerPlayerEntity monkey) || monkey == target
					|| !isSpidermonkey(monkey) || !AlienKit.held(target)) {
				return;
			}
			ServerWorld world = monkey.getServerWorld();
			// eingewebt: +50 %, im Nahkampf drei Zusatzschlaege der uebrigen Arme
			AlienKit.melee(world, monkey, target, taken * 0.5f);
			if (source.getSource() == monkey) {
				for (int i = 0; i < 3; i++) {
					AlienKit.melee(world, monkey, target, 1.5f);
				}
				world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
			}
		});
	}

	private static boolean isSpidermonkey(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(SPIDERMONKEY::equals).isPresent();
	}

	private static void webbed(ServerWorld world, LivingEntity target, int ticks) {
		AlienKit.hold(world, target, ticks, WEB, false);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 5));
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Netzschuss: Netzfaden auf das erste Ziel — haelt fest. */
	private static boolean webShot(AbilityContext ctx) {
		ServerPlayerEntity monkey = ctx.player();
		ServerWorld world = ctx.world();
		AlienKit.Beam beam = AlienKit.beam(world, monkey, ctx.param("range", 20.0));
		AlienKit.line(world, AlienKit.muzzle(monkey), beam.end(), WEB, 4.0);
		beam.target().ifPresent(target -> {
			AlienKit.magic(world, monkey, target, (float) ctx.param("damage", 3.0));
			webbed(world, target, (int) (ctx.param("seconds", 3.0) * 20));
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SPIDER_STEP, 1.2f, 1.6f);
		return true;
	}

	/** Netzfaecher: breites Netz nach vorn — alle im Kegel festgehalten. */
	private static boolean webNet(AbilityContext ctx) {
		ServerPlayerEntity monkey = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 8.0);
		for (LivingEntity target : AlienKit.cone(world, monkey, range, 0.6)) {
			webbed(world, target, (int) (ctx.param("seconds", 3.0) * 20));
			AlienKit.line(world, AlienKit.muzzle(monkey), target.getPos().add(0, target.getHeight() * 0.5, 0), WEB, 3.0);
		}
		Vec3d look = monkey.getRotationVec(1.0f);
		for (int i = 1; i <= (int) range; i++) {
			Vec3d p = monkey.getEyePos().add(look.multiply(i));
			world.spawnParticles(WEB_THICK, p.x, p.y, p.z, 4, i * 0.15, i * 0.1, i * 0.15, 0.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SPIDER_AMBIENT, 1.0f, 1.4f);
		return true;
	}

	/** Netzschwung: Faden zum anvisierten Punkt, zieht Spidermonkey in hohem Bogen hin. */
	private static boolean webSwing(AbilityContext ctx) {
		ServerPlayerEntity monkey = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 28.0);
		Vec3d eye = monkey.getEyePos();
		HitResult hit = world.raycast(new RaycastContext(eye, eye.add(monkey.getRotationVec(1.0f).multiply(range)),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, monkey));
		if (hit.getType() == HitResult.Type.MISS) {
			monkey.sendMessage(Text.translatable("message.kingdomomnitrix.spidermonkey_no_anchor").formatted(Formatting.GRAY), true);
			return false;
		}
		SWINGS.put(monkey.getUuid(), new Swing(hit.getPos(), world.getTime() + 25));
		AlienKit.line(world, AlienKit.muzzle(monkey), hit.getPos(), WEB, 2.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.4f);
		return true;
	}

	/** Affenwirbel: acht schnelle Schlaege aller vier Arme auf alles direkt vor Spidermonkey. */
	private static boolean monkeyFrenzy(AbilityContext ctx) {
		FRENZY.put(ctx.player().getUuid(), new int[]{(int) ctx.param("hits", 8)});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
		return true;
	}

	/** Netzfalle: Netzring um Spidermonkey — wer hineinlaeuft (auch spaeter, 10 s), haengt fest. */
	private static boolean webTrap(AbilityContext ctx) {
		ServerPlayerEntity monkey = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 5.0);
		for (LivingEntity target : AlienKit.around(world, monkey, monkey.getPos(), radius)) {
			webbed(world, target, (int) (ctx.param("seconds", 5.0) * 20));
		}
		AlienKit.ring(world, monkey.getPos().add(0, 0.2, 0), radius, WEB_THICK);
		AlienKit.ring(world, monkey.getPos().add(0, 0.2, 0), radius * 0.6, WEB);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_COBWEB_PLACE, 1.4f, 0.8f);
		return true;
	}

	/** Netzkokon: alle Gegner im Umkreis werden eingesponnen und hochgezogen — 6 s haengend, wehrlos. */
	private static boolean webCocoon(AbilityContext ctx) {
		ServerPlayerEntity monkey = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		for (LivingEntity target : AlienKit.around(world, monkey, monkey.getPos(), ctx.param("radius", 10.0))) {
			target.setVelocity(0, 0.6, 0);
			target.velocityModified = true;
			AlienKit.hold(world, target, ticks, WEB_THICK, true);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 2), monkey);
			AlienKit.line(world, target.getPos().add(0, target.getHeight(), 0), target.getPos().add(0, target.getHeight() + 4, 0), WEB, 2.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SPIDER_AMBIENT, 1.4f, 0.8f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity monkey : server.getPlayerManager().getPlayerList()) {
			UUID id = monkey.getUuid();
			if (!isSpidermonkey(monkey)) {
				SWINGS.remove(id);
				FRENZY.remove(id);
				continue;
			}
			ServerWorld world = monkey.getServerWorld();
			// Wandlauf: gegen die Wand gelaufen → klettern (Schleichen = festhalten)
			if (monkey.horizontalCollision && !monkey.isOnGround()) {
				monkey.setVelocity(monkey.getVelocity().x, monkey.isSneaking() ? 0.0 : 0.22, monkey.getVelocity().z);
				monkey.velocityModified = true;
				monkey.fallDistance = 0.0f;
			} else if (monkey.horizontalCollision && monkey.forwardSpeed > 0) {
				monkey.setVelocity(monkey.getVelocity().x, 0.3, monkey.getVelocity().z);
				monkey.velocityModified = true;
			}
			Swing swing = SWINGS.get(id);
			if (swing != null) {
				Vec3d to = swing.anchor().subtract(monkey.getPos().add(0, 1, 0));
				if (world.getTime() >= swing.until() || to.length() < 1.5) {
					SWINGS.remove(id);
				} else {
					Vec3d pull = to.normalize().multiply(Math.min(1.6, 0.5 + to.length() * 0.08));
					monkey.setVelocity(pull.add(0, 0.08, 0));
					monkey.velocityModified = true;
					monkey.fallDistance = 0.0f;
					if (world.getTime() % 2 == 0) {
						AlienKit.line(world, monkey.getPos().add(0, 1.4, 0), swing.anchor(), WEB, 1.0);
					}
				}
			}
			int[] frenzy = FRENZY.get(id);
			if (frenzy != null && world.getTime() % 3 == 0) {
				for (LivingEntity target : AlienKit.cone(world, monkey, 3.5, 0.4)) {
					AlienKit.melee(world, monkey, target, 2.5f);
					world.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.6), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				}
				world.playSound(null, monkey.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.6f);
				if (--frenzy[0] <= 0) {
					FRENZY.remove(id);
				}
			}
		}
	}
}
