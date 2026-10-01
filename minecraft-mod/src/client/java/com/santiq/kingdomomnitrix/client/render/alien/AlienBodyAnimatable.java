package com.santiq.kingdomomnitrix.client.render.alien;

import net.minecraft.entity.EntityType;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Animations-Traeger fuer einen Alien-Koerper, der das Spielermodell ersetzt.
 * Ein Objekt pro Alien-Modell; GeckoLib fuehrt den Animationszustand pro Spieler (Entity-ID).
 */
public class AlienBodyAnimatable implements GeoReplacedEntity {
	public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	public static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final int TRANSITION_TICKS = 4;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "body", TRANSITION_TICKS,
				state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
