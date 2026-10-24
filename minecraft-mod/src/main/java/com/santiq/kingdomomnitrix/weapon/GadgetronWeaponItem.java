package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.gear.GeoGear;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.vfx.Vfx;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Waffen aus dem Gadgetron-Katalog (Ratchet &amp; Clank), Werte je Stufe aus {@code kingdomomnitrix/weapon/<id>.json}:
 * <ul>
 * <li>Blitz Gun: Schrotflinte, {@code pellets} Plasma-Kugeln mit Streuung {@code spread}, Nachladezeit {@code cooldown}.</li>
 * <li>Pyrocitor: Flammenwerfer bei gehaltener Taste, Kegel mit Reichweite {@code range}, setzt Ziele in Brand.</li>
 * <li>Devastator: Rakete mit Sprengkraft {@code explosion}, lenkt auf den Gegner nach, der dem Fadenkreuz am naechsten ist.</li>
 * <li>Morph-O-Ray: Strahl, der Gegner bis {@code max_health} Lebenspunkte in Huehner verwandelt.</li>
 * </ul>
 */
public class GadgetronWeaponItem extends WeaponItem implements GeoGear {
	public enum Style {
		BLITZ("tooltip.kingdomomnitrix.blitz_gun.usage"),
		PYRO("tooltip.kingdomomnitrix.pyrocitor.usage"),
		DEVASTATOR("tooltip.kingdomomnitrix.devastator.usage"),
		MORPH("tooltip.kingdomomnitrix.morph_o_ray.usage");

		private final String usageKey;

		Style(String usageKey) {
			this.usageKey = usageKey;
		}
	}

	private static final int MAX_USE_TICKS = 72_000;
	/** Halber Oeffnungswinkel des Flammenkegels (cos 30 Grad) */
	private static final double PYRO_CONE_COS = 0.866;
	/** Halber Winkel, in dem der Devastator ein Ziel aufschaltet (cos 12 Grad), und Reichweite */
	private static final double LOCK_CONE_COS = 0.978;
	private static final double LOCK_RANGE = 40.0;

	private final Style style;

	public GadgetronWeaponItem(Style style, Settings settings) {
		super(settings);
		this.style = style;
	}

	public Style style() {
		return style;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!hasAmmo(user, stack)) {
			if (!world.isClient()) {
				user.sendMessage(Text.translatable("message.kingdomomnitrix.no_ammo").formatted(Formatting.RED), true);
				world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.WEAPON_EMPTY, SoundCategory.PLAYERS, 0.8f, 1.0f);
			}
			return TypedActionResult.fail(stack);
		}
		if (style == Style.PYRO) {
			user.setCurrentHand(hand);
			return TypedActionResult.consume(stack);
		}
		if (!world.isClient()) {
			Optional<WeaponDefinition.Level> level = levelData(world.getRegistryManager(), stack);
			if (!consumeAmmo(user, stack, 1)) {
				return TypedActionResult.fail(stack);
			}
			switch (style) {
				case BLITZ -> fireBlitz(world, user, level);
				case DEVASTATOR -> fireDevastator(world, user, level);
				case MORPH -> fireMorph(world, user, level);
				default -> {
				}
			}
			int cooldown = (int) Math.round(stat(level, "cooldown", 10));
			user.getItemCooldownManager().set(this, Math.max(1, cooldown));
		} else {
			// Rueckstoss als kurzer Kamera-Kick
			user.setPitch(user.getPitch() - (style == Style.DEVASTATOR ? 4.0f : 2.0f));
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	private static double stat(Optional<WeaponDefinition.Level> level, String key, double fallback) {
		return level.map(l -> l.stat(key, fallback)).orElse(fallback);
	}

	private static void fireBlitz(World world, PlayerEntity user, Optional<WeaponDefinition.Level> level) {
		int pellets = (int) Math.round(stat(level, "pellets", 6));
		float damage = (float) stat(level, "damage", 2.5);
		float spread = (float) stat(level, "spread", 8.0);
		for (int i = 0; i < pellets; i++) {
			HeroProjectileEntity.shoot(world, user, ModItems.PLASMA_SHOT, 2.4f, spread).withDamage(damage).asPellet();
		}
		if (world instanceof ServerWorld serverWorld) {
			Vfx.muzzle(serverWorld, user);
		}
		world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.WEAPON_COMBUSTER, SoundCategory.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.25f, 1.8f);
	}

	private static void fireDevastator(World world, PlayerEntity user, Optional<WeaponDefinition.Level> level) {
		float damage = (float) stat(level, "damage", 12);
		float explosion = (float) stat(level, "explosion", 2.0);
		LivingEntity target = lockOn(world, user);
		HeroProjectileEntity.shoot(world, user, ModItems.FIRE_ORB, 1.5f, 0.0f).withDamage(damage).withExplosion(explosion).withHoming(target);
		if (world instanceof ServerWorld serverWorld) {
			Vfx.muzzle(serverWorld, user);
			Vec3d back = user.getPos().add(0, 1.4, 0).subtract(user.getRotationVec(1.0f).multiply(0.8));
			serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE, back.x, back.y, back.z, 6, 0.15, 0.15, 0.15, 0.02);
			if (target != null) {
				Fx.flash(serverWorld, target.getPos().add(0, target.getHeight() + 0.4, 0), 0xFF3A4A, 0.8f);
			}
		}
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.2f, 0.6f);
	}

	/** Lebender Gegner im schmalen Kegel vor dem Spieler, der der Blickrichtung am naechsten liegt. */
	private static LivingEntity lockOn(World world, PlayerEntity user) {
		Vec3d eye = user.getEyePos();
		Vec3d look = user.getRotationVec(1.0f);
		Box area = user.getBoundingBox().stretch(look.multiply(LOCK_RANGE)).expand(6.0);
		return world.getEntitiesByClass(LivingEntity.class, area, entity -> entity != user && entity.isAlive() && entity instanceof Monster
						&& (!(user instanceof ServerPlayerEntity player) || PartyRules.canHarm(player, entity)))
				.stream()
				.filter(entity -> {
					Vec3d to = entity.getPos().add(0, entity.getHeight() * 0.5, 0).subtract(eye);
					return to.length() <= LOCK_RANGE && to.normalize().dotProduct(look) >= LOCK_CONE_COS && user.canSee(entity);
				})
				.max(Comparator.comparingDouble(entity -> entity.getPos().add(0, entity.getHeight() * 0.5, 0).subtract(eye).normalize().dotProduct(look)))
				.orElse(null);
	}

	private static void fireMorph(World world, PlayerEntity user, Optional<WeaponDefinition.Level> level) {
		float damage = (float) stat(level, "damage", 1.0);
		float maxHealth = (float) stat(level, "max_health", 30);
		HeroProjectileEntity.shoot(world, user, ModItems.CRYSTAL_SHARD, 2.0f, 0.0f).withDamage(damage).withMorph(maxHealth);
		if (world instanceof ServerWorld serverWorld) {
			Fx.flash(serverWorld, user.getEyePos().add(user.getRotationVec(1.0f).multiply(1.2)), 0xD85AFF, 0.7f);
		}
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 2.0f);
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return style == Style.PYRO ? MAX_USE_TICKS : 0;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.NONE;
	}

	/** Pyrocitor: solange die Taste gehalten wird, spruehen Flammen im Kegel vor dem Spieler. */
	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (style != Style.PYRO || !(user instanceof PlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
			return;
		}
		Optional<WeaponDefinition.Level> level = levelData(world.getRegistryManager(), stack);
		int delay = (int) Math.max(1, Math.round(stat(level, "fire_delay", 3)));
		int elapsed = MAX_USE_TICKS - remainingUseTicks;
		double range = stat(level, "range", 6.0);
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		// Flammenstrahl sichtbar bei jedem Tick, Schaden und Munition im Takt der Stufe
		for (int i = 0; i < 4; i++) {
			double distance = 0.8 + world.random.nextDouble() * range;
			Vec3d point = eye.add(look.multiply(distance)).add(0, -0.25, 0);
			double jitter = 0.08 * distance;
			serverWorld.spawnParticles(i % 2 == 0 ? ParticleTypes.FLAME : com.santiq.kingdomomnitrix.registry.ModParticles.FIRE_EMBER,
					point.x, point.y, point.z, 1, jitter, jitter, jitter, 0.01);
		}
		if (elapsed % delay != 0) {
			return;
		}
		if (!consumeAmmo(player, stack, 1)) {
			player.stopUsingItem();
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_ammo").formatted(Formatting.RED), true);
			return;
		}
		float damage = (float) stat(level, "damage", 2.0);
		float burn = (float) stat(level, "burn", 3.0);
		Box area = player.getBoundingBox().stretch(look.multiply(range)).expand(2.0);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, entity -> entity != player && entity.isAlive())) {
			Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(eye);
			if (to.length() > range + target.getWidth() || to.normalize().dotProduct(look) < PYRO_CONE_COS || !player.canSee(target)) {
				continue;
			}
			if (player instanceof ServerPlayerEntity shooter && !PartyRules.canHarm(shooter, target)) {
				continue;
			}
			target.damage(world.getDamageSources().playerAttack(player), damage);
			target.setOnFireFor(burn);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.35f,
				0.7f + world.random.nextFloat() * 0.2f);
	}

	@Override
	protected void appendUsage(List<Text> tooltip) {
		tooltip.add(Text.translatable(style.usageKey).formatted(Formatting.DARK_GRAY));
	}
}
