package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Alien X (Celestialsapien). Eigenes System „Rat der Stimmen“ — ohne Aura:
 *
 * <ul>
 *   <li>Jede Faehigkeit gehoert zu <b>Serena</b> (Erschaffen/Bewahren) oder <b>Bellicus</b> (Zerstoeren).</li>
 *   <li><b>Einklang</b>: wechselt Alien X zwischen beiden Stimmen, steigt der Einklang (0–3). Bei 3 ist die naechste
 *       Faehigkeit <b>Allmacht</b>: doppelt so stark, danach beginnt der Einklang von vorn.</li>
 *   <li><b>Uneinigkeit</b>: zweimal dieselbe Stimme hintereinander — die Stimmen streiten, Alien X erstarrt 1 s
 *       (Einklang weg). So wie in der Serie: Alien X kann nur handeln, wenn sich alle einig sind.</li>
 * </ul>
 */
final class AlienXAbilities {
	private static final Identifier ALIEN_X = KingdomOmnitrix.id("alien_x");
	private static final int SERENA = 1;
	private static final int BELLICUS = 2;
	private static final DustParticleEffect STAR = AlienKit.dust("#FFFFFF", 1.0f);
	private static final DustParticleEffect NEBULA = AlienKit.dust("#9FD8FF", 1.6f);
	private static final DustParticleEffect GOLD = AlienKit.dust("#FFE27A", 1.4f);

	/** [letzte Stimme, Einklang] je Spieler */
	private static final Map<UUID, int[]> COUNCIL = new HashMap<>();
	/** Zeitstillstand: Ende und Zentrum */
	private static final Map<UUID, Object[]> TIME_STOP = new HashMap<>();

	private AlienXAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("reality_bolt"), AlienXAbilities::realityBolt);
		AbilityRegistry.register(KingdomOmnitrix.id("gravity_warp"), AlienXAbilities::gravityWarp);
		AbilityRegistry.register(KingdomOmnitrix.id("blink"), AlienXAbilities::blink);
		AbilityRegistry.register(KingdomOmnitrix.id("time_stop"), AlienXAbilities::timeStop);
		AbilityRegistry.register(KingdomOmnitrix.id("creation"), AlienXAbilities::creation);
		AbilityRegistry.register(KingdomOmnitrix.id("big_bang"), AlienXAbilities::bigBang);
		ServerTickEvents.END_SERVER_TICK.register(AlienXAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			COUNCIL.remove(handler.getPlayer().getUuid());
			TIME_STOP.remove(handler.getPlayer().getUuid());
		});
	}

	private static boolean isAlienX(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(ALIEN_X::equals).isPresent();
	}

	/**
	 * Abstimmung fuer eine Faehigkeit der Stimme {@code voice}. Rueckgabe: Staerkefaktor (2 = Allmacht) oder 0, wenn die
	 * Stimmen uneins sind (Faehigkeit verpufft, Alien X erstarrt).
	 */
	private static float vote(AbilityContext ctx, int voice) {
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		int[] council = COUNCIL.computeIfAbsent(x.getUuid(), id -> new int[2]);
		if (council[0] == voice) {
			council[0] = 0;
			council[1] = 0;
			x.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 9, false, false));
			world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, x.getX(), x.getEyeY() + 0.4, x.getZ(), 3, 0.3, 0.1, 0.3, 0.0);
			x.sendMessage(Text.translatable("message.kingdomomnitrix.alien_x_dispute").formatted(Formatting.RED), true);
			BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
			return 0.0f;
		}
		council[0] = voice;
		if (council[1] >= 3) {
			council[1] = 0;
			x.sendMessage(Text.translatable("message.kingdomomnitrix.alien_x_omnipotence").formatted(Formatting.GOLD, Formatting.BOLD), true);
			world.spawnParticles(GOLD, x.getX(), x.getBodyY(0.6), x.getZ(), 40, 0.6, 0.9, 0.6, 0.0);
			return 2.0f;
		}
		council[1]++;
		x.sendMessage(Text.translatable(voice == SERENA ? "message.kingdomomnitrix.alien_x_serena" : "message.kingdomomnitrix.alien_x_bellicus",
				council[1], 3).formatted(voice == SERENA ? Formatting.AQUA : Formatting.LIGHT_PURPLE), true);
		return 1.0f;
	}

	// --- Bellicus --------------------------------------------------------------------------------

	/** Realitaetsblitz (Bellicus): Riss im Raum entlang der Blicklinie. */
	private static boolean realityBolt(AbilityContext ctx) {
		float power = vote(ctx, BELLICUS);
		if (power == 0.0f) {
			return true;
		}
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		AlienKit.Beam beam = AlienKit.beam(world, x, ctx.param("range", 32.0));
		for (LivingEntity target : AlienKit.along(world, x, AlienKit.muzzle(x), beam.end(), 0.6 * power)) {
			AlienKit.magic(world, x, target, (float) ctx.param("damage", 9.0) * power);
		}
		AlienKit.line(world, AlienKit.muzzle(x), beam.end(), NEBULA, 3.0);
		AlienKit.line(world, AlienKit.muzzle(x), beam.end(), STAR, 2.0);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, 1.2f, 0.7f);
		return true;
	}

	/** Schwerkraftbruch (Bellicus): hebt alles im Umkreis hoch und schmettert es zurueck. */
	private static boolean gravityWarp(AbilityContext ctx) {
		float power = vote(ctx, BELLICUS);
		if (power == 0.0f) {
			return true;
		}
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		for (LivingEntity target : AlienKit.around(world, x, x.getPos(), ctx.param("radius", 8.0) * power)) {
			target.setVelocity(0, 1.2, 0);
			target.velocityModified = true;
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 10, 0), x);
			AlienKit.magic(world, x, target, (float) ctx.param("damage", 6.0) * power);
		}
		AlienKit.ring(world, x.getPos().add(0, 0.5, 0), 6.0, NEBULA);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SHULKER_SHOOT, 1.4f, 0.5f);
		return true;
	}

	/** Urknall (Bellicus, Ultimativ): ein neues Universum im Kleinen — alles im Umkreis, danach Stille. */
	private static boolean bigBang(AbilityContext ctx) {
		float power = vote(ctx, BELLICUS);
		if (power == 0.0f) {
			return true;
		}
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		double radius = ctx.param("radius", 16.0);
		for (LivingEntity target : AlienKit.around(world, x, x.getPos(), radius)) {
			AlienKit.magic(world, x, target, (float) ctx.param("damage", 20.0) * power);
			AlienKit.push(target, x.getPos(), 2.0, 0.8);
		}
		world.spawnParticles(ParticleTypes.FLASH, x.getX(), x.getBodyY(0.5), x.getZ(), 3, 0.5, 0.5, 0.5, 0.0);
		world.spawnParticles(ParticleTypes.END_ROD, x.getX(), x.getBodyY(0.5), x.getZ(), 200, radius * 0.3, radius * 0.2, radius * 0.3, 0.3);
		for (int r = 2; r <= (int) radius; r += 3) {
			AlienKit.ring(world, x.getPos().add(0, 1.0, 0), r, r % 2 == 0 ? NEBULA : STAR);
		}
		ctx.grantInvulnerability(40);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 3.0f, 0.4f);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_END_PORTAL_SPAWN, 1.5f, 1.2f);
		return true;
	}

	// --- Serena ----------------------------------------------------------------------------------

	/** Sprung durch den Raum (Serena): sofort an den anvisierten Punkt (bis 32 Bloecke, Allmacht doppelt). */
	private static boolean blink(AbilityContext ctx) {
		float power = vote(ctx, SERENA);
		if (power == 0.0f) {
			return true;
		}
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 32.0) * power;
		Vec3d eye = x.getEyePos();
		Vec3d look = x.getRotationVec(1.0f);
		HitResult hit = world.raycast(new RaycastContext(eye, eye.add(look.multiply(range)), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, x));
		double reach = hit.getType() == HitResult.Type.MISS ? range : Math.max(0.0, eye.distanceTo(hit.getPos()) - 1.0);
		for (double d = reach; d >= 1.0; d -= 0.5) {
			Vec3d feet = eye.add(look.multiply(d)).subtract(0.0, x.getStandingEyeHeight(), 0.0);
			if (world.isSpaceEmpty(x, x.getBoundingBox().offset(feet.subtract(x.getPos())))) {
				world.spawnParticles(STAR, x.getX(), x.getBodyY(0.5), x.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
				x.requestTeleport(feet.x, feet.y, feet.z);
				x.fallDistance = 0.0f;
				world.spawnParticles(NEBULA, feet.x, feet.y + 1, feet.z, 30, 0.4, 0.8, 0.4, 0.0);
				BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
				return true;
			}
		}
		x.sendMessage(Text.translatable("message.kingdomomnitrix.no_space_step").formatted(Formatting.GRAY), true);
		return false;
	}

	/** Zeitstillstand (Serena bewahrt): alles im Umkreis erstarrt 5 s, Geschosse bleiben in der Luft stehen. */
	private static boolean timeStop(AbilityContext ctx) {
		float power = vote(ctx, SERENA);
		if (power == 0.0f) {
			return true;
		}
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 5.0) * 20 * power);
		double radius = ctx.param("radius", 12.0);
		for (LivingEntity target : AlienKit.around(world, x, x.getPos(), radius)) {
			AlienKit.hold(world, target, ticks, STAR, true);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 4), x);
		}
		TIME_STOP.put(x.getUuid(), new Object[]{world.getTime() + ticks, x.getPos(), radius});
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BELL_RESONATE, 1.5f, 0.5f);
		return true;
	}

	/** Schoepfung (Serena): heilt Alien X und seine Gruppe vollstaendig, loest schaedliche Effekte. */
	private static boolean creation(AbilityContext ctx) {
		float power = vote(ctx, SERENA);
		if (power == 0.0f) {
			return true;
		}
		ServerPlayerEntity x = ctx.player();
		ServerWorld world = ctx.world();
		for (ServerPlayerEntity ally : world.getEntitiesByClass(ServerPlayerEntity.class, x.getBoundingBox().expand(ctx.param("radius", 8.0)),
				p -> p == x || !com.santiq.kingdomomnitrix.party.PartyRules.canHarm(x, p))) {
			ally.heal(ally.getMaxHealth());
			ally.getStatusEffects().stream().filter(e -> !e.getEffectType().value().isBeneficial()).map(StatusEffectInstance::getEffectType).toList()
					.forEach(ally::removeStatusEffect);
			ally.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 400, (int) (2 * power), false, true));
			world.spawnParticles(GOLD, ally.getX(), ally.getBodyY(0.6), ally.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.4f, 1.8f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity x : server.getPlayerManager().getPlayerList()) {
			if (!isAlienX(x)) {
				COUNCIL.remove(x.getUuid());
				TIME_STOP.remove(x.getUuid());
				continue;
			}
			Object[] stop = TIME_STOP.get(x.getUuid());
			if (stop == null) {
				continue;
			}
			ServerWorld world = x.getServerWorld();
			if (world.getTime() >= (long) stop[0]) {
				TIME_STOP.remove(x.getUuid());
				continue;
			}
			Vec3d center = (Vec3d) stop[1];
			double radius = (double) stop[2];
			// Geschosse im Feld stehen still
			for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class,
					new net.minecraft.util.math.Box(center, center).expand(radius), p -> p.getOwner() != x)) {
				projectile.setVelocity(Vec3d.ZERO);
				projectile.velocityModified = true;
			}
			if (world.getTime() % 10 == 0) {
				AlienKit.ring(world, center.add(0, 1.0, 0), radius, STAR);
			}
		}
	}
}
