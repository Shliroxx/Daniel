package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.party.PartyRules;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Die mitgelieferten Faehigkeits-Typen. Jeder Typ liest seine Zahlen aus den JSON-Parametern
 * und faellt auf die hier stehenden Standardwerte zurueck.
 */
final class BuiltinAbilities {
	private BuiltinAbilities() {
	}

	static void register() {
		// Heatblast
		AbilityRegistry.register(KingdomOmnitrix.id("fire_blast"), BuiltinAbilities::fireBlast);
		AbilityRegistry.register(KingdomOmnitrix.id("fire_burst"), BuiltinAbilities::fireBurst);
		AbilityRegistry.register(KingdomOmnitrix.id("flame_boost"), BuiltinAbilities::flameBoost);
		// XLR8
		AbilityRegistry.register(KingdomOmnitrix.id("dash_strike"), BuiltinAbilities::dashStrike);
		AbilityRegistry.register(KingdomOmnitrix.id("blur_dodge"), BuiltinAbilities::blurDodge);
		AbilityRegistry.register(KingdomOmnitrix.id("rapid_strikes"), BuiltinAbilities::rapidStrikes);
		// Vierarm
		AbilityRegistry.register(KingdomOmnitrix.id("ground_slam"), BuiltinAbilities::groundSlam);
		AbilityRegistry.register(KingdomOmnitrix.id("throw"), BuiltinAbilities::throwTarget);
		AbilityRegistry.register(KingdomOmnitrix.id("mighty_leap"), BuiltinAbilities::mightyLeap);
		// Diamondhead / Grey Matter (Prototyp)
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_volley"), BuiltinAbilities::crystalVolley);
		AbilityRegistry.register(KingdomOmnitrix.id("scan"), BuiltinAbilities::scan);
		CreatureAbilities.register();
		MasteryAbilities.register();
	}

	// --- Heatblast -------------------------------------------------------------------------------

	private static boolean fireBlast(AbilityContext ctx) {
		int count = (int) Math.max(1, ctx.param("count", 1));
		float speed = (float) ctx.param("speed", 2.2);
		float spread = (float) ctx.param("spread", 0.0);
		for (int i = 0; i < count; i++) {
			HeroProjectileEntity.shoot(ctx.world(), ctx.player(), ModItems.FIRE_ORB, speed, spread);
		}
		sound(ctx, ModSounds.ALIEN_FIRE, 1.0f, 1.0f);
		return true;
	}

	private static boolean fireBurst(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double radius = ctx.param("radius", 4.0);
		float damage = (float) ctx.param("damage", 6.0);
		float fireSeconds = (float) ctx.param("fire_seconds", 4.0);
		for (LivingEntity target : livingAround(ctx, radius)) {
			target.damage(ctx.world().getDamageSources().playerAttack(player), damage);
			target.setOnFireFor(fireSeconds);
		}
		ctx.world().spawnParticles(ParticleTypes.FLAME, player.getX(), player.getBodyY(0.5), player.getZ(),
				60, radius * 0.4, 0.6, radius * 0.4, 0.15);
		sound(ctx, ModSounds.ALIEN_FIRE, 1.2f, 0.75f);
		return true;
	}

	private static boolean flameBoost(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = horizontalLook(player);
		double up = ctx.param("up", 1.2);
		double forward = ctx.param("forward", 0.6);
		launch(player, look.x * forward, up, look.z * forward);
		ctx.world().spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 30, 0.3, 0.1, 0.3, 0.08);
		sound(ctx, ModSounds.ALIEN_FIRE, 0.8f, 1.3f);
		return true;
	}

	// --- XLR8 ------------------------------------------------------------------------------------

	private static boolean dashStrike(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = horizontalLook(player);
		double distance = ctx.param("distance", 8.0);
		float damage = (float) ctx.param("damage", 5.0);
		double speed = ctx.param("speed", 2.6);

		Box path = player.getBoundingBox().stretch(look.multiply(distance)).expand(0.6);
		for (LivingEntity target : ctx.world().getEntitiesByClass(LivingEntity.class, path, e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
			target.damage(ctx.world().getDamageSources().playerAttack(player), damage);
			target.takeKnockback(0.6, -look.x, -look.z);
		}
		launch(player, look.x * speed, 0.1, look.z * speed);
		ctx.world().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 16, 0.3, 0.1, 0.3, 0.05);
		sound(ctx, ModSounds.ALIEN_DASH, 0.9f, 1.1f);
		return true;
	}

	private static boolean blurDodge(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = horizontalLook(player);
		double power = ctx.param("power", 1.6);
		launch(player, -look.x * power, 0.25, -look.z * power);
		ctx.grantInvulnerability((int) ctx.param("invulnerable_ticks", 12));
		ctx.world().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getBodyY(0.5), player.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
		sound(ctx, ModSounds.ALIEN_DASH, 0.8f, 1.4f);
		return true;
	}

	private static boolean rapidStrikes(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = horizontalLook(player);
		double range = ctx.param("range", 3.0);
		float damage = (float) ctx.param("damage", 8.0);
		Box area = player.getBoundingBox().stretch(look.multiply(range)).expand(1.0, 0.5, 1.0);
		List<LivingEntity> targets = ctx.world().getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive() && PartyRules.canHarm(player, e));
		if (targets.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		for (LivingEntity target : targets) {
			target.damage(ctx.world().getDamageSources().playerAttack(player), damage);
			ctx.world().spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.5), target.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
		}
		sound(ctx, ModSounds.COMBAT_SWING, 1.0f, 1.5f);
		return true;
	}

	// --- Vierarm ---------------------------------------------------------------------------------

	private static boolean groundSlam(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double radius = ctx.param("radius", 5.0);
		float damage = (float) ctx.param("damage", 9.0);
		double launch = ctx.param("launch", 0.5);
		for (LivingEntity target : livingAround(ctx, radius)) {
			target.damage(ctx.world().getDamageSources().playerAttack(player), damage);
			Vec3d push = target.getPos().subtract(player.getPos());
			if (push.horizontalLengthSquared() > 1.0E-4) {
				target.takeKnockback(1.4, -push.x, -push.z);
			}
			target.addVelocity(0.0, launch, 0.0);
			target.velocityModified = true;
		}
		ctx.world().spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 6, radius * 0.4, 0.2, radius * 0.4, 0.0);
		sound(ctx, ModSounds.ALIEN_SLAM, 1.2f, 0.9f);
		return true;
	}

	private static boolean throwTarget(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double range = ctx.param("range", 5.0);
		Optional<LivingEntity> found = Targeting.findLivingTarget(player, range);
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		if (!PartyRules.canHarm(player, target)) {
			return false;
		}
		double maxWidth = ctx.param("max_width", 2.0);
		if (target.getWidth() > maxWidth) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.too_heavy").formatted(Formatting.GRAY), true);
			return false;
		}
		Vec3d look = player.getRotationVec(1.0f);
		double power = ctx.param("power", 2.2);
		target.damage(ctx.world().getDamageSources().playerAttack(player), (float) ctx.param("damage", 4.0));
		target.setVelocity(look.x * power, Math.max(0.4, look.y * power + 0.4), look.z * power);
		target.velocityModified = true;
		sound(ctx, ModSounds.WEAPON_THROW, 1.0f, 0.7f);
		return true;
	}

	private static boolean mightyLeap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = horizontalLook(player);
		double up = ctx.param("up", 1.3);
		double forward = ctx.param("forward", 1.2);
		launch(player, look.x * forward, up, look.z * forward);
		ctx.world().spawnParticles(ParticleTypes.POOF, player.getX(), player.getY(), player.getZ(), 15, 0.4, 0.05, 0.4, 0.02);
		sound(ctx, ModSounds.ALIEN_SLAM, 1.0f, 1.2f);
		return true;
	}

	// --- Diamondhead / Grey Matter ---------------------------------------------------------------

	private static boolean crystalVolley(AbilityContext ctx) {
		int count = (int) Math.max(1, ctx.param("count", 5));
		float speed = (float) ctx.param("speed", 2.0);
		float spread = (float) ctx.param("spread", 6.0);
		for (int i = 0; i < count; i++) {
			HeroProjectileEntity.shoot(ctx.world(), ctx.player(), ModItems.CRYSTAL_SHARD, speed, spread);
		}
		sound(ctx, ModSounds.ALIEN_CRYSTAL, 1.0f, 1.0f);
		return true;
	}

	private static boolean scan(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findLivingTarget(player, ctx.param("range", 24.0));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		double attack = target.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE)
				? target.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) : 0.0;
		player.sendMessage(Text.translatable("message.kingdomomnitrix.scan",
				target.getDisplayName(),
				String.format("%.1f", target.getHealth()),
				String.format("%.1f", target.getMaxHealth()),
				String.format("%.0f", target.getAttributeValue(EntityAttributes.GENERIC_ARMOR)),
				String.format("%.1f", attack)).formatted(Formatting.GRAY), false);
		sound(ctx, ModSounds.SHIP_AI, 0.7f, 1.2f);
		return true;
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	static List<LivingEntity> livingAround(AbilityContext ctx, double radius) {
		ServerPlayerEntity player = ctx.player();
		double radiusSq = radius * radius;
		return ctx.world().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
				e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= radiusSq && PartyRules.canHarm(player, e));
	}

	/** Blickrichtung ohne Neigung; faellt bei senkrechtem Blick auf die Koerperausrichtung zurueck. */
	static Vec3d horizontalLook(ServerPlayerEntity player) {
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d flat = new Vec3d(look.x, 0.0, look.z);
		if (flat.lengthSquared() < 1.0E-4) {
			float yaw = player.getBodyYaw() * ((float) Math.PI / 180.0f);
			return new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
		}
		return flat.normalize();
	}

	/** Setzt die Spielergeschwindigkeit serverseitig und schickt sie an den Client (der die Bewegung rechnet). */
	static void launch(ServerPlayerEntity player, double x, double y, double z) {
		player.setVelocity(x, y, z);
		player.velocityModified = true;
		player.fallDistance = 0.0f;
	}

	static void sound(AbilityContext ctx, SoundEvent sound, float volume, float pitch) {
		ServerWorld world = ctx.world();
		ServerPlayerEntity player = ctx.player();
		world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
