package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * Faehigkeiten der Phase-2-Aliens (Wildmutt, Stinkfly, Ripjaws, Upgrade, Ghostfreak). Wie {@link BuiltinAbilities}:
 * jede liest ihre Zahlen aus den JSON-Parametern und hat hier Standardwerte; Rueckgabe {@code false} = nicht
 * ausgeloest (keine Energie/Abklingzeit verbraucht).
 */
final class CreatureAbilities {
	private CreatureAbilities() {
	}

	static void register() {
		// Wildmutt: WildmuttAbilities (Jagd)
		// Stinkfly: StinkflyAbilities (Toxin-Schichten)
		// Ripjaws: RipjawsAbilities (Gezeiten)
		// Upgrade: UpgradeAbilities (Integration)
		// Ghostfreak
		AbilityRegistry.register(KingdomOmnitrix.id("phase_shift"), CreatureAbilities::phaseShift);
		AbilityRegistry.register(KingdomOmnitrix.id("haunting_scare"), CreatureAbilities::hauntingScare);
		AbilityRegistry.register(KingdomOmnitrix.id("tentacle_lash"), CreatureAbilities::tentacleLash);
	}

	// --- Ghostfreak ------------------------------------------------------------------------------

	/** Phasenverschiebung: unverwundbar und unsichtbar, Gegner verlieren das Ziel. */
	private static boolean phaseShift(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) ctx.param("ticks", 60);
		ctx.grantInvulnerability(ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, ticks, 0, false, false));
		for (MobEntity mob : ctx.world().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(24.0), m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
		ctx.world().spawnParticles(ParticleTypes.SOUL, player.getX(), player.getBodyY(0.5), player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_VEX_CHARGE, 1.0f, 0.6f);
		return true;
	}

	/** Spuk: Gegner im Umkreis fliehen (weggestossen), werden geschwaecht und blind. */
	private static boolean hauntingScare(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double radius = ctx.param("radius", 8.0);
		int seconds = (int) ctx.param("seconds", 6.0);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, radius)) {
			Vec3d away = target.getPos().subtract(player.getPos());
			if (away.horizontalLengthSquared() > 1.0E-4) {
				target.takeKnockback(ctx.param("push", 1.2), -away.x, -away.z);
			}
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, seconds * 20, 1), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, seconds * 20, 0), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, seconds * 20, 0), player);
		}
		ctx.world().spawnParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getBodyY(0.5), player.getZ(), 40, radius * 0.3, 0.6, radius * 0.3, 0.02);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GHAST_SCREAM, 0.9f, 0.7f);
		return true;
	}

	/** Tentakelhieb: zieht ein Ziel heran und schaedigt es (Lebensentzug). */
	private static boolean tentacleLash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 10.0), 0.85, e -> PartyRules.canHarm(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		Vec3d pull = player.getPos().subtract(target.getPos()).normalize().multiply(ctx.param("pull", 1.2));
		target.setVelocity(pull.x, 0.3, pull.z);
		target.velocityModified = true;
		target.damage(ctx.world().getDamageSources().indirectMagic(player, player), (float) ctx.param("damage", 6.0));
		player.heal((float) ctx.param("heal", 2.0));
		Vec3d from = player.getPos().add(0.0, player.getHeight() * 0.5, 0.0);
		Vec3d to = target.getPos().add(0.0, target.getHeight() * 0.5, 0.0);
		for (int i = 1; i <= 10; i++) {
			Vec3d p = from.lerp(to, i / 10.0);
			ctx.world().spawnParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_WARDEN_TENDRIL_CLICKS, 1.0f, 1.0f);
		return true;
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	private static boolean hostileTo(ServerPlayerEntity player, LivingEntity entity) {
		return entity != player && entity.isAlive() && PartyRules.canHarm(player, entity);
	}

}
