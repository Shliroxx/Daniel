package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

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
		// Ripjaws
		AbilityRegistry.register(KingdomOmnitrix.id("jaw_bite"), CreatureAbilities::jawBite);
		AbilityRegistry.register(KingdomOmnitrix.id("tidal_dash"), CreatureAbilities::tidalDash);
		AbilityRegistry.register(KingdomOmnitrix.id("whirlpool"), CreatureAbilities::whirlpool);
		// Upgrade
		AbilityRegistry.register(KingdomOmnitrix.id("optic_beam"), CreatureAbilities::opticBeam);
		AbilityRegistry.register(KingdomOmnitrix.id("liquid_form"), CreatureAbilities::liquidForm);
		AbilityRegistry.register(KingdomOmnitrix.id("tech_upgrade"), CreatureAbilities::techUpgrade);
		// Ghostfreak
		AbilityRegistry.register(KingdomOmnitrix.id("phase_shift"), CreatureAbilities::phaseShift);
		AbilityRegistry.register(KingdomOmnitrix.id("haunting_scare"), CreatureAbilities::hauntingScare);
		AbilityRegistry.register(KingdomOmnitrix.id("tentacle_lash"), CreatureAbilities::tentacleLash);
	}

	// --- Ripjaws ---------------------------------------------------------------------------------

	/** Biss auf das Ziel vor dem Spieler; im Wasser staerker, heilt etwas. */
	private static boolean jawBite(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findMeleeTarget(player, ctx.param("range", 4.0), 0.7, e -> PartyRules.canHarm(player, e));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		boolean wet = player.isTouchingWater();
		float damage = (float) (ctx.param("damage", 8.0) * (wet ? ctx.param("water_multiplier", 1.5) : 1.0));
		target.damage(ctx.world().getDamageSources().playerAttack(player), damage);
		player.heal((float) ctx.param("heal", 2.0));
		ctx.world().spawnParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getBodyY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, 1.0f, 0.8f);
		return true;
	}

	/** Schneller Vorstoss; im Wasser dreimal so weit, an Land nur kurz. */
	private static boolean tidalDash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = player.getRotationVec(1.0f);
		boolean wet = player.isTouchingWater();
		double power = ctx.param("power", 1.0) * (wet ? ctx.param("water_multiplier", 3.0) : 1.0);
		BuiltinAbilities.launch(player, look.x * power, wet ? look.y * power : 0.25, look.z * power);
		ctx.world().spawnParticles(wet ? ParticleTypes.BUBBLE_COLUMN_UP : ParticleTypes.SPLASH,
				player.getX(), player.getBodyY(0.5), player.getZ(), 24, 0.4, 0.4, 0.4, 0.1);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_DOLPHIN_SPLASH, 1.0f, 0.9f);
		return true;
	}

	/** Strudel: zieht Gegner im Umkreis heran und schaedigt sie; im Wasser doppelte Reichweite. */
	private static boolean whirlpool(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		boolean wet = player.isTouchingWater();
		double radius = ctx.param("radius", 5.0) * (wet ? 2.0 : 1.0);
		float damage = (float) ctx.param("damage", 4.0);
		List<LivingEntity> targets = BuiltinAbilities.livingAround(ctx, radius);
		for (LivingEntity target : targets) {
			Vec3d pull = player.getPos().subtract(target.getPos()).normalize().multiply(ctx.param("pull", 0.9));
			target.addVelocity(pull.x, 0.15, pull.z);
			target.velocityModified = true;
			target.damage(ctx.world().getDamageSources().drown(), damage);
		}
		for (int i = 0; i < 24; i++) {
			double angle = i * MathHelper.TAU / 24.0;
			ctx.world().spawnParticles(ParticleTypes.SPLASH, player.getX() + Math.cos(angle) * radius * 0.6, player.getY() + 0.3,
					player.getZ() + Math.sin(angle) * radius * 0.6, 3, 0.1, 0.1, 0.1, 0.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.2f, 0.8f);
		return true;
	}

	// --- Upgrade ---------------------------------------------------------------------------------

	/** Optischer Strahl: trifft das erste Wesen in Blickrichtung (bis Reichweite, Waende stoppen). */
	private static boolean opticBeam(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double range = ctx.param("range", 24.0);
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		Optional<LivingEntity> hit = Targeting.findLivingTarget(player, eye.distanceTo(stop));
		if (hit.isPresent() && PartyRules.canHarm(player, hit.get())) {
			stop = hit.get().getPos().add(0.0, hit.get().getHeight() * 0.5, 0.0);
			hit.get().damage(world.getDamageSources().indirectMagic(player, player), (float) ctx.param("damage", 7.0));
		}
		Vec3d step = stop.subtract(eye);
		int points = Math.max(4, (int) (step.length() * 3));
		DustParticleEffect green = new DustParticleEffect(new Vector3f(0.22f, 1.0f, 0.08f), 0.9f);
		for (int i = 1; i <= points; i++) {
			Vec3d p = eye.add(step.multiply(i / (double) points));
			world.spawnParticles(green, p.x, p.y - 0.15, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_GUARDIAN_ATTACK, 0.8f, 1.6f);
		return true;
	}

	/** Fluessige Form: kurz unverwundbar, unsichtbar und schnell — gleitet unter Angriffen weg. */
	private static boolean liquidForm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) ctx.param("ticks", 40);
		ctx.grantInvulnerability(ticks);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, ticks, 0, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 2, false, false));
		ctx.world().spawnParticles(new DustParticleEffect(new Vector3f(0.05f, 0.05f, 0.05f), 1.6f),
				player.getX(), player.getY() + 0.1, player.getZ(), 30, 0.6, 0.05, 0.6, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, 1.0f, 0.6f);
		return true;
	}

	/** Technik verschmelzen: Werkzeug in der Hand wird repariert und kurz verbessert (Eile). */
	private static boolean techUpgrade(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);
		if (stack.isEmpty() || !stack.isDamageable()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.upgrade_nothing").formatted(Formatting.GRAY), true);
			return false;
		}
		int repair = (int) Math.ceil(stack.getMaxDamage() * ctx.param("repair", 0.25));
		stack.setDamage(Math.max(0, stack.getDamage() - repair));
		int seconds = (int) ctx.param("seconds", 20.0);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, seconds * 20, (int) ctx.param("haste_level", 1)));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, seconds * 20, 0));
		ctx.world().spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getBodyY(0.6), player.getZ(), 24, 0.4, 0.4, 0.4, 0.2);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.upgrade_done", stack.getName()).formatted(Formatting.GREEN), true);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
		return true;
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
