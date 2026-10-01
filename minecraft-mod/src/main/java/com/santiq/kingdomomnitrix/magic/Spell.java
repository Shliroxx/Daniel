package com.santiq.kingdomomnitrix.magic;

import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Die vier Grundzauber des Schluesselschwerts. */
public enum Spell {
	FIRE("fire", Formatting.RED, 20),
	BLIZZARD("blizzard", Formatting.AQUA, 30),
	THUNDER("thunder", Formatting.YELLOW, 80),
	CURE("cure", Formatting.GREEN, 200);

	private static final double THUNDER_RANGE = 24.0;
	private static final double THUNDER_RADIUS = 3.0;
	private static final float THUNDER_DAMAGE = 10.0f;
	private static final float CURE_AMOUNT = 8.0f;

	private final String id;
	private final Formatting color;
	private final int cooldownTicks;

	Spell(String id, Formatting color, int cooldownTicks) {
		this.id = id;
		this.color = color;
		this.cooldownTicks = cooldownTicks;
	}

	public int cooldownTicks() {
		return cooldownTicks;
	}

	public Text displayName() {
		return Text.translatable("spell.kingdomomnitrix." + id).formatted(color);
	}

	public Spell next() {
		Spell[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static Spell byIndex(int index) {
		Spell[] all = values();
		return all[Math.floorMod(index, all.length)];
	}

	/** Wirkt den Zauber auf dem Server. Liefert false, wenn er nichts bewirkt hat (z. B. Vita bei voller Gesundheit). */
	public boolean cast(ServerWorld world, PlayerEntity caster) {
		return switch (this) {
			case FIRE -> {
				HeroProjectileEntity.shoot(world, caster, ModItems.FIRE_ORB, 1.8f, 0.5f);
				world.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.8f, 1.2f);
				yield true;
			}
			case BLIZZARD -> {
				for (int i = 0; i < 3; i++) {
					HeroProjectileEntity.shoot(world, caster, ModItems.ICE_ORB, 1.5f, 7.0f);
				}
				world.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 0.8f, 1.4f);
				yield true;
			}
			case THUNDER -> castThunder(world, caster);
			case CURE -> {
				if (caster.getHealth() >= caster.getMaxHealth()) {
					caster.sendMessage(Text.translatable("message.kingdomomnitrix.cure_full").formatted(Formatting.GRAY), true);
					yield false;
				}
				caster.heal(CURE_AMOUNT);
				world.spawnParticles(ParticleTypes.HEART, caster.getX(), caster.getBodyY(0.8), caster.getZ(), 6, 0.5, 0.4, 0.5, 0.0);
				world.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.0f);
				yield true;
			}
		};
	}

	private static boolean castThunder(ServerWorld world, PlayerEntity caster) {
		Vec3d strike = Targeting.findLivingTarget(caster, THUNDER_RANGE)
				.map(LivingEntity::getPos)
				.orElseGet(() -> Targeting.lookEnd(caster, THUNDER_RANGE));

		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
		if (bolt == null) {
			return false;
		}
		// Kosmetischer Blitz: kein Feuer, kein Creeper-Aufladen — den Schaden verteilt der Zauber selbst.
		bolt.setCosmetic(true);
		bolt.refreshPositionAfterTeleport(strike);
		if (caster instanceof ServerPlayerEntity serverPlayer) {
			bolt.setChanneler(serverPlayer);
		}
		world.spawnEntity(bolt);

		Box area = new Box(strike, strike).expand(THUNDER_RADIUS);
		for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, area, e -> e != caster && e.isAlive())) {
			victim.damage(world.getDamageSources().indirectMagic(caster, caster), THUNDER_DAMAGE);
		}
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, strike.x, strike.y + 0.5, strike.z, 30, 1.0, 0.8, 1.0, 0.1);
		return true;
	}
}
