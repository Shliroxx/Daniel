package com.santiq.kingdomomnitrix.magic;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Wirkungen der Zauber. Zahlen kommen aus der Zauberstufe ({@code params} im JSON);
 * {@link SpellContext#power(String, double)} rechnet die Magiekraft des Keyblades ein.
 */
public final class SpellEffects {
	@FunctionalInterface
	public interface SpellEffect {
		/** @return true, wenn der Zauber gewirkt hat (dann werden MP und Abklingzeit verbraucht) */
		boolean cast(SpellContext context);
	}

	/**
	 * @param magicFactor Verstaerkung durch Keyblade-Magiekraft und Magie-Boost (1.0 = keine)
	 */
	public record SpellContext(ServerPlayerEntity player, ServerWorld world, Identifier spellId, int level,
			SpellDefinition.Level data, float magicFactor) {
		public double param(String key, double fallback) {
			return data.param(key, fallback);
		}

		public float power(String key, double fallback) {
			return (float) (data.param(key, fallback) * magicFactor);
		}
	}

	private static final Map<Identifier, SpellEffect> EFFECTS = new LinkedHashMap<>();

	private SpellEffects() {
	}

	public static void register(Identifier id, SpellEffect effect) {
		if (EFFECTS.putIfAbsent(id, effect) != null) {
			throw new IllegalStateException("Zaubereffekt doppelt registriert: " + id);
		}
	}

	public static Optional<SpellEffect> get(Identifier id) {
		return Optional.ofNullable(EFFECTS.get(id));
	}

	public static void registerBuiltins() {
		register(KingdomOmnitrix.id("fire"), SpellEffects::fire);
		register(KingdomOmnitrix.id("blizzard"), SpellEffects::blizzard);
		register(KingdomOmnitrix.id("thunder"), SpellEffects::thunder);
		register(KingdomOmnitrix.id("cure"), SpellEffects::cure);
	}

	private static boolean fire(SpellContext ctx) {
		int count = (int) Math.max(1, ctx.param("count", 1));
		float damage = ctx.power("power", 5.0);
		for (int i = 0; i < count; i++) {
			HeroProjectileEntity.shoot(ctx.world(), ctx.player(), ModItems.FIRE_ORB,
					(float) ctx.param("speed", 1.8), (float) ctx.param("spread", 0.5)).withDamage(damage);
		}
		sound(ctx, SoundEvents.ENTITY_BLAZE_SHOOT, 0.8f, 1.3f - ctx.level() * 0.1f);
		return true;
	}

	private static boolean blizzard(SpellContext ctx) {
		int count = (int) Math.max(1, ctx.param("count", 3));
		float damage = ctx.power("power", 3.0);
		for (int i = 0; i < count; i++) {
			HeroProjectileEntity.shoot(ctx.world(), ctx.player(), ModItems.ICE_ORB,
					(float) ctx.param("speed", 1.5), (float) ctx.param("spread", 7.0)).withDamage(damage);
		}
		sound(ctx, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 1.4f);
		return true;
	}

	/** Blitze auf bis zu {@code strikes} Ziele; ohne Ziel ein Blitz auf den anvisierten Punkt. */
	private static boolean thunder(SpellContext ctx) {
		ServerPlayerEntity caster = ctx.player();
		double range = ctx.param("range", 24.0);
		int strikes = (int) Math.max(1, ctx.param("strikes", 1));
		double radius = ctx.param("radius", 3.0);
		float damage = ctx.power("power", 8.0);

		List<Vec3d> points = new java.util.ArrayList<>();
		Targeting.findLivingTarget(caster, range).ifPresent(target -> points.add(target.getPos()));
		if (strikes > points.size()) {
			ctx.world().getEntitiesByClass(LivingEntity.class, caster.getBoundingBox().expand(range * 0.5),
							e -> e != caster && e.isAlive() && e instanceof net.minecraft.entity.mob.Monster && caster.canSee(e))
					.stream()
					.sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(caster)))
					.map(LivingEntity::getPos)
					.filter(pos -> points.stream().noneMatch(existing -> existing.squaredDistanceTo(pos) < 1.0))
					.limit(strikes - points.size())
					.forEach(points::add);
		}
		if (points.isEmpty()) {
			points.add(Targeting.lookEnd(caster, range));
		}
		for (Vec3d point : points) {
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(ctx.world());
			if (bolt != null) {
				// Kosmetischer Blitz: kein Feuer, kein Creeper-Aufladen — den Schaden verteilt der Zauber.
				bolt.setCosmetic(true);
				bolt.refreshPositionAfterTeleport(point);
				bolt.setChanneler(caster);
				ctx.world().spawnEntity(bolt);
			}
			Box area = new Box(point, point).expand(radius);
			for (LivingEntity victim : ctx.world().getEntitiesByClass(LivingEntity.class, area, e -> e != caster && e.isAlive() && !isFriend(caster, e))) {
				victim.timeUntilRegen = 0;
				victim.damage(ctx.world().getDamageSources().indirectMagic(caster, caster), damage);
			}
			ctx.world().spawnParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y + 0.5, point.z, 30, 1.0, 0.8, 1.0, 0.1);
		}
		return true;
	}

	/** Heilt den Zaubernden; ab Stufe mit {@code radius} auch Mitspieler und eigene Tiere in der Naehe. */
	private static boolean cure(SpellContext ctx) {
		ServerPlayerEntity caster = ctx.player();
		float amount = ctx.power("heal", 6.0);
		double radius = ctx.param("radius", 0.0);
		List<LivingEntity> targets = new java.util.ArrayList<>(List.of(caster));
		if (radius > 0) {
			targets.addAll(ctx.world().getEntitiesByClass(LivingEntity.class, caster.getBoundingBox().expand(radius),
					e -> e != caster && e.isAlive() && isFriend(caster, e)));
		}
		boolean anyHurt = targets.stream().anyMatch(e -> e.getHealth() < e.getMaxHealth());
		if (!anyHurt) {
			caster.sendMessage(Text.translatable("message.kingdomomnitrix.cure_full").formatted(Formatting.GRAY), true);
			return false;
		}
		for (LivingEntity target : targets) {
			target.heal(amount);
			ctx.world().spawnParticles(ParticleTypes.HEART, target.getX(), target.getBodyY(0.8), target.getZ(), 4 + ctx.level() * 2, 0.5, 0.4, 0.5, 0.0);
		}
		ctx.world().spawnParticles(ParticleTypes.HAPPY_VILLAGER, caster.getX(), caster.getBodyY(0.5), caster.getZ(), 20, radius * 0.3 + 0.5, 0.5, radius * 0.3 + 0.5, 0.0);
		sound(ctx, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.9f + ctx.level() * 0.1f);
		return true;
	}

	private static boolean isFriend(ServerPlayerEntity caster, LivingEntity entity) {
		if (entity instanceof PlayerEntity) {
			return !caster.shouldDamagePlayer((PlayerEntity) entity) || entity.isTeammate(caster);
		}
		return entity instanceof TameableEntity tameable && tameable.isTamed() && caster.equals(tameable.getOwner());
	}

	private static void sound(SpellContext ctx, SoundEvent sound, float volume, float pitch) {
		ServerPlayerEntity player = ctx.player();
		ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
