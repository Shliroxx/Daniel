package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Animations-Traeger fuer einen Alien-Koerper, der das Spielermodell ersetzt.
 * Ein Objekt pro Alien-Modell; GeckoLib fuehrt den Animationszustand pro Spieler (Entity-ID).
 *
 * <p>Zwei Controller, beide nur aus synchronisiertem Zustand abgeleitet (kein eigenes Paket):</p>
 * <ul>
 *   <li>{@code body}: transform → jump/fall → run → walk → idle</li>
 *   <li>{@code action}: hit → ability_N (Faehigkeit gerade benutzt) → attack (Schlag) — ueberlagert nur die Knochen,
 *       die die Aktion bewegt</li>
 * </ul>
 * Jedes Alien-Modell muss den vollen Satz aus {@code tools/generate_alien_models.py} enthalten.
 */
public class AlienBodyAnimatable implements GeoReplacedEntity {
	public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	public static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	public static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
	public static final RawAnimation JUMP = RawAnimation.begin().thenLoop("jump");
	public static final RawAnimation FALL = RawAnimation.begin().thenLoop("fall");
	public static final RawAnimation TRANSFORM = RawAnimation.begin().thenPlayAndHold("transform");
	public static final RawAnimation REVERT = RawAnimation.begin().thenPlayAndHold("revert");
	public static final RawAnimation ATTACK = RawAnimation.begin().thenPlayAndHold("attack");
	public static final RawAnimation HIT = RawAnimation.begin().thenPlayAndHold("hit");
	private static final RawAnimation[] ABILITIES = {
			RawAnimation.begin().thenPlayAndHold("ability_0"),
			RawAnimation.begin().thenPlayAndHold("ability_1"),
			RawAnimation.begin().thenPlayAndHold("ability_2")};

	/** Laenge der Verwandlungs-Animation (1,4 s). */
	public static final int TRANSFORM_TICKS = 28;
	public static final int REVERT_TICKS = 8;
	private static final int ABILITY_TICKS = 12;
	private static final int TRANSITION_TICKS = 3;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** zuletzt gestartete Aktion je Spieler (Entity-ID) */
	private final Map<Integer, Long> lastTrigger = new HashMap<>();

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "body", TRANSITION_TICKS, this::body));
		controllers.add(new AnimationController<>(this, "action", 1, this::action));
	}

	private PlayState body(AnimationState<AlienBodyAnimatable> state) {
		PlayerEntity player = player(state);
		if (player == null) {
			return state.setAndContinue(IDLE);
		}
		if (AlienBodyRenderers.isReverting(player)) {
			return state.setAndContinue(REVERT);
		}
		TransformationState transformation = TransformationManager.get(player);
		long now = player.getWorld().getTime();
		if (transformation.isTransformed() && now - transformation.startTick() < TRANSFORM_TICKS) {
			// beim Wechsel direkt hart umschalten, damit der Koerper sichtbar aus dem Nichts waechst
			if (!state.isCurrentAnimation(TRANSFORM)) {
				state.getController().forceAnimationReset();
			}
			return state.setAndContinue(TRANSFORM);
		}
		if (!player.isOnGround() && !player.isClimbing() && !player.isTouchingWater() && !player.hasVehicle()) {
			return state.setAndContinue(player.getVelocity().y > 0.05 ? JUMP : FALL);
		}
		if (state.isMoving()) {
			return state.setAndContinue(player.isSprinting() ? RUN : WALK);
		}
		return state.setAndContinue(IDLE);
	}

	private PlayState action(AnimationState<AlienBodyAnimatable> state) {
		PlayerEntity player = player(state);
		if (player == null || AlienBodyRenderers.isReverting(player)) {
			return PlayState.STOP;
		}
		TransformationState transformation = TransformationManager.get(player);
		long now = player.getWorld().getTime();
		if (transformation.isTransformed() && now - transformation.startTick() < TRANSFORM_TICKS) {
			return PlayState.STOP;
		}
		// Ausloeser mit eindeutigem Schluessel: jede neue Aktion startet ihre Animation genau einmal von vorn
		RawAnimation wanted = null;
		long key = 0L;
		int slot = recentAbility(transformation, now);
		if (player.hurtTime > 0 && player.hurtTime > player.maxHurtTime - 6) {
			wanted = HIT;
			key = player.age - (player.maxHurtTime - player.hurtTime);
		} else if (slot >= 0) {
			wanted = ABILITIES[Math.min(slot, ABILITIES.length - 1)];
			key = transformation.energyStamp() * 4 + slot;
		} else if (player.handSwinging) {
			wanted = ATTACK;
			key = player.age - player.handSwingTicks;
		}
		long triggerKey = wanted == null ? Long.MIN_VALUE : key * 31 + wanted.hashCode();
		Long last = lastTrigger.get(player.getId());
		if (wanted != null && (last == null || last != triggerKey)) {
			lastTrigger.put(player.getId(), triggerKey);
			state.getController().forceAnimationReset();
			state.setAnimation(wanted);
			return PlayState.CONTINUE;
		}
		if (state.getController().getCurrentRawAnimation() != null && !state.getController().hasAnimationFinished()) {
			return PlayState.CONTINUE;
		}
		state.resetCurrentAnimation();
		return PlayState.STOP;
	}

	/**
	 * Faehigkeit, die gerade benutzt wurde: {@code afterAbility} setzt den Energie-Zeitstempel auf „jetzt“ und die
	 * Bereitschaft des Slots in die Zukunft — der Slot mit der spaetesten Bereitschaft ist der zuletzt benutzte.
	 */
	private static int recentAbility(TransformationState transformation, long now) {
		if (transformation.energyStamp() <= transformation.startTick() || now - transformation.energyStamp() > ABILITY_TICKS) {
			return -1;
		}
		int best = -1;
		long latest = Long.MIN_VALUE;
		for (int i = 0; i < transformation.abilityReadyAt().size(); i++) {
			long ready = transformation.abilityReadyAt().get(i);
			if (ready > latest) {
				latest = ready;
				best = i;
			}
		}
		return best;
	}

	private static PlayerEntity player(AnimationState<AlienBodyAnimatable> state) {
		Entity entity = state.getData(DataTickets.ENTITY);
		return entity instanceof PlayerEntity player ? player : null;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
