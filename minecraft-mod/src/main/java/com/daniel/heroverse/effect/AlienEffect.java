package com.daniel.heroverse.effect;

import com.daniel.heroverse.item.Alien;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/**
 * Laufende Omnitrix-Verwandlung. Tickt alle 5 Ticks fuer die Partikel-Aura der Alien-Form.
 */
public class AlienEffect extends StatusEffect {
	private final Alien alien;

	public AlienEffect(Alien alien, int color) {
		super(StatusEffectCategory.BENEFICIAL, color);
		this.alien = alien;
	}

	public Alien getAlien() {
		return alien;
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return duration % 5 == 0;
	}

	@Override
	public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (!(entity.getWorld() instanceof ServerWorld world)) {
			return true;
		}
		if (alien == Alien.HEATBLAST && entity.isOnFire()) {
			entity.extinguish();
		}
		ParticleEffect particle = switch (alien) {
			case HEATBLAST -> ParticleTypes.FLAME;
			case XLR8 -> ParticleTypes.CLOUD;
			case FOUR_ARMS -> ParticleTypes.CRIT;
			case DIAMONDHEAD -> ParticleTypes.END_ROD;
			case GREY_MATTER -> ParticleTypes.ENCHANT;
		};
		world.spawnParticles(particle, entity.getX(), entity.getBodyY(0.5), entity.getZ(),
				2, entity.getWidth() * 0.4, entity.getHeight() * 0.3, entity.getWidth() * 0.4, 0.01);
		return true;
	}
}
