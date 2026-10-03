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
		// XLR8
		// Vierarm
		AbilityRegistry.register(KingdomOmnitrix.id("mighty_leap"), BuiltinAbilities::mightyLeap);
		// Diamondhead / Grey Matter (Prototyp)
		AbilityRegistry.register(KingdomOmnitrix.id("crystal_volley"), BuiltinAbilities::crystalVolley);
		AbilityRegistry.register(KingdomOmnitrix.id("scan"), BuiltinAbilities::scan);
		CreatureAbilities.register();
		MasteryAbilities.register();
		CannonboltAbilities.register();
		HeatblastAbilities.register();
		Xlr8Abilities.register();
		FourArmsAbilities.register();
		JetrayAbilities.register();
	}

	// Heatblast: HeatblastAbilities (Kernhitze)

	// XLR8: Xlr8Abilities (Tempo)

	// --- Vierarm: FourArmsAbilities (Wut); mighty_leap nutzt Diamondhead ---------------------------------------------------------------------------------

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
