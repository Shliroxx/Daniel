package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.vfx.Vfx;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixMalfunction;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixOs;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import com.santiq.kingdomomnitrix.progression.HeroAbilityEffect;
import com.santiq.kingdomomnitrix.progression.ProgressionManager;
import com.santiq.kingdomomnitrix.progression.ProgressionStats;
import com.santiq.kingdomomnitrix.ability.AbilityContext;
import com.santiq.kingdomomnitrix.ability.AbilityRegistry;
import com.santiq.kingdomomnitrix.ability.AlienAbility;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;

/**
 * Serverseitige Steuerung aller Verwandlungen: verwandeln, zurueckverwandeln, Faehigkeiten, Ablauf,
 * Attribute, Immunitaeten. Der Zustand liegt in {@link #STATE} und wird an alle Clients synchronisiert,
 * damit jeder den Alien-Koerper eines Spielers sehen kann.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TransformationManager {
	public static final AttachmentType<TransformationState> STATE = AttachmentRegistry.create(KingdomOmnitrix.id("transformation"), builder -> builder
			.persistent(TransformationState.CODEC)
			.initializer(() -> TransformationState.EMPTY)
			.copyOnDeath()
			.syncWith(TransformationState.PACKET_CODEC, AttachmentSyncPredicate.all()));

	/** Feste Modifier-IDs, damit Boni beim Zurueckverwandeln sicher entfernt werden, auch wenn sich das JSON geaendert hat. */
	private static final int MAX_ATTRIBUTE_BONUSES = 16;
	private static final Identifier SCALE_MODIFIER = KingdomOmnitrix.id("alien_scale");
	private static final Identifier MASTERY_HEALTH_MODIFIER = KingdomOmnitrix.id("alien_mastery_health");
	private static final int AURA_INTERVAL_TICKS = 5;
	private static final int WARNING_TICKS = 100;

	public enum Result {
		SUCCESS, NO_OMNITRIX, UNKNOWN_ALIEN, LOCKED, RECHARGING, ALREADY_TRANSFORMED, NO_SPACE,
		NOT_TRANSFORMED, NO_SUCH_ABILITY, ON_COOLDOWN, NO_ENERGY, FAILED, DEVICE_REFUSED, ABILITY_LOCKED
	}

	private TransformationManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TransformationManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> AlienTraitHandler.disconnect(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				// Tod beendet jede Verwandlung; die Nachladezeit bleibt bestehen.
				update(newPlayer, state -> state.isTransformed() ? state.reverted(state.rechargeUntil()) : state);
				AlienTraitHandler.clear(newPlayer);
			} else {
				reapplyAttributes(newPlayer);
			}
		});
		// Alien besiegt → DNA-Schock statt Tod; Menschenform mit Omnitrix → Notfall-Verwandlung. Unabwendbarer Schaden
		// (/kill, Leere) bleibt toedlich.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity player) || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			if (get(player).isTransformed()) {
				return !defeatAlien(player);
			}
			return !failsafe(player, source);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity player)) {
				return true;
			}
			TransformationState state = get(player);
			if (!state.isTransformed()) {
				return true;
			}
			if (state.isInvulnerable(player.getWorld().getTime())) {
				return false;
			}
			return activeDefinition(player).map(alien -> !alien.isImmuneTo(source)).orElse(true);
		});
	}

	public static TransformationState get(PlayerEntity player) {
		TransformationState state = player.getAttached(STATE);
		return state != null ? state : TransformationState.EMPTY;
	}

	private static void update(ServerPlayerEntity player, UnaryOperator<TransformationState> change) {
		TransformationState before = get(player);
		TransformationState after = change.apply(before);
		if (!after.equals(before)) {
			player.setAttached(STATE, after);
		}
	}

	public static Optional<AlienDefinition> activeDefinition(PlayerEntity player) {
		return get(player).activeAlien().flatMap(id -> AlienRegistry.get(player.getWorld().getRegistryManager(), id));
	}

	// --- Aktionen -------------------------------------------------------------------------------

	public static Result select(ServerPlayerEntity player, Identifier alienId) {
		if (AlienRegistry.get(player.getWorld().getRegistryManager(), alienId).isEmpty()) {
			return Result.UNKNOWN_ALIEN;
		}
		if (!HeroDataAccess.get(player).hasAlien(alienId)) {
			return Result.LOCKED;
		}
		update(player, state -> state.withSelected(alienId));
		return Result.SUCCESS;
	}

	/**
	 * Verwandelt den Spieler. Mit {@code force} (Admin-Befehl) werden Omnitrix, Freischaltung und Nachladezeit ignoriert,
	 * nicht aber die Platzpruefung — sonst wuerde der Spieler ersticken.
	 */
	public static Result transform(ServerPlayerEntity player, Identifier alienId, boolean force) {
		return transform(player, alienId, force, !force);
	}

	private static Result transform(ServerPlayerEntity player, Identifier alienId, boolean force, boolean mayMalfunction) {
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Optional<AlienDefinition> found = AlienRegistry.get(world.getRegistryManager(), alienId);
		if (found.isEmpty()) {
			return Result.UNKNOWN_ALIEN;
		}
		AlienDefinition alien = found.get();
		TransformationState state = get(player);
		if (!force) {
			if (!OmnitrixItem.hasOmnitrix(player)) {
				return Result.NO_OMNITRIX;
			}
			if (!HeroDataAccess.get(player).hasAlien(alienId)) {
				return Result.LOCKED;
			}
			if (state.isTransformed()) {
				return Result.ALREADY_TRANSFORMED;
			}
			if (state.rechargeRemaining(now) > 0) {
				return Result.RECHARGING;
			}
			Optional<OmnitrixCore.Refusal> refusal = OmnitrixCore.checkTransform(player, alienId);
			if (refusal.isPresent()) {
				OmnitrixCore.refuse(player, refusal.get());
				return Result.DEVICE_REFUSED;
			}
			// Fehlfunktion bei hoher Hitze: anderes freigeschaltetes Alien (laeuft durch alle Regeln, inkl. Platz)
			Optional<Identifier> swapped = mayMalfunction ? OmnitrixMalfunction.wrongAlien(player, alienId) : Optional.empty();
			if (swapped.isPresent()) {
				Result result = transform(player, swapped.get(), false, false);
				if (result == Result.SUCCESS) {
					OmnitrixMalfunction.announceWrongAlien(player, alienId, swapped.get());
					return result;
				}
			}
		} else if (state.isTransformed()) {
			removeAttributes(player);
			AlienTraitHandler.clear(player);
		}
		if (!hasSpaceFor(player, alien.scale())) {
			return Result.NO_SPACE;
		}

		int duration = durationTicks(player, alienId, alien);
		// Menschenform-Lebenspunkte einfrieren; das Alien startet mit vollen eigenen Lebenspunkten
		float human = state.isTransformed() && state.humanHealth() > 0.0f ? state.humanHealth() : player.getHealth();
		update(player, s -> s.transformed(alienId, alien, now, duration, human));
		OmnitrixCore.onTransform(player, alienId);
		applyAttributes(player, alien);
		player.setHealth(player.getMaxHealth());
		playTransformEffects(world, player, alien, true);
		OmnitrixOs.send(player, OmnitrixOs.Event.TRANSFORMED, alienName(alienId).withColor(alien.color()).formatted(Formatting.BOLD), alienId);
		return Result.SUCCESS;
	}

	/** Zurueckverwandeln. {@code timeout} = Zeit abgelaufen (volle Nachladezeit), sonst halbe. */
	public static Result revert(ServerPlayerEntity player, boolean timeout) {
		TransformationState state = get(player);
		if (!state.isTransformed()) {
			return Result.NOT_TRANSFORMED;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Optional<AlienDefinition> alien = activeDefinition(player);
		float quickRecharge = Math.min(0.75f, ProgressionManager.value(player, HeroAbilityEffect.QUICK_RECHARGE));
		int recharge = Math.round(alien.map(AlienDefinition::rechargeTicks).orElse(0) * (1.0f - quickRecharge)
				* OmnitrixCore.cooldownFactor(player));
		long rechargeUntil = now + (timeout ? recharge : Math.round(recharge * OmnitrixCore.profile(player).manualRevertCooldown()));

		if (!hasSpaceFor(player, 1.0f)) {
			// Kleines Alien in engem Gang: Die Rueckverwandlung wuerde den Spieler ersticken lassen.
			return Result.NO_SPACE;
		}
		removeAttributes(player);
		AlienTraitHandler.clear(player);
		OmnitrixCore.onRevert(player);
		update(player, s -> s.reverted(rechargeUntil));
		restoreHumanHealth(player, state);
		alien.ifPresent(a -> playTransformEffects(world, player, a, false));
		OmnitrixOs.send(player, timeout ? OmnitrixOs.Event.TIMEOUT : OmnitrixOs.Event.REVERTED,
				Text.translatable("holo.kingdomomnitrix.os.recharge_in", (rechargeUntil - now + 19) / 20),
				Optional.empty(), state.activeAlien());
		return Result.SUCCESS;
	}

	/** Menschenform-Lebenspunkte von vor der Verwandlung zurueck (Schaden am Alien betrifft sie nicht). */
	private static void restoreHumanHealth(ServerPlayerEntity player, TransformationState before) {
		if (before.humanHealth() > 0.0f && player.isAlive()) {
			player.setHealth(Math.min(player.getMaxHealth(), before.humanHealth()));
		}
	}

	/**
	 * Schnellwechsel: aus einem Alien direkt in ein anderes. Normal mit Hitze-Aufschlag und nur einem Teil der
	 * Restzeit (Profil), unter Master Control sofort, ohne Aufschlag und mit voller Dauer.
	 */
	public static Result quickChange(ServerPlayerEntity player, Identifier alienId) {
		return quickChange(player, alienId, true);
	}

	private static Result quickChange(ServerPlayerEntity player, Identifier alienId, boolean mayMalfunction) {
		TransformationState state = get(player);
		if (!state.isTransformed()) {
			return transform(player, alienId, false);
		}
		if (state.activeAlien().filter(alienId::equals).isPresent()) {
			return revert(player, false);
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Optional<AlienDefinition> found = AlienRegistry.get(world.getRegistryManager(), alienId);
		if (found.isEmpty()) {
			return Result.UNKNOWN_ALIEN;
		}
		if (!HeroDataAccess.get(player).hasAlien(alienId)) {
			return Result.LOCKED;
		}
		Optional<OmnitrixCore.Refusal> refusal = OmnitrixCore.checkQuickChange(player);
		if (refusal.isPresent()) {
			OmnitrixCore.refuse(player, refusal.get());
			return Result.DEVICE_REFUSED;
		}
		Optional<Identifier> swapped = mayMalfunction ? OmnitrixMalfunction.wrongAlien(player, alienId) : Optional.empty();
		if (swapped.isPresent() && state.activeAlien().filter(swapped.get()::equals).isEmpty()) {
			Result result = quickChange(player, swapped.get(), false);
			if (result == Result.SUCCESS) {
				OmnitrixMalfunction.announceWrongAlien(player, alienId, swapped.get());
				return result;
			}
		}
		AlienDefinition alien = found.get();
		if (!hasSpaceFor(player, alien.scale())) {
			return Result.NO_SPACE;
		}
		boolean master = OmnitrixCore.state(player).masterControl();
		int full = durationTicks(player, alienId, alien);
		int duration = master ? full : (int) Math.min(full, Math.max(200L,
				Math.round(state.remainingTicks(now) * OmnitrixCore.profile(player).quickChangeKeep())));
		float healthShare = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
		removeAttributes(player);
		AlienTraitHandler.clear(player);
		update(player, s -> s.quickChanged(alienId, alien, now, duration));
		OmnitrixCore.onQuickChange(player, alienId);
		applyAttributes(player, alien);
		// Anteil der Alien-Lebenspunkte bleibt erhalten (kein Vollheilen durch Wechseln)
		player.setHealth(Math.max(1.0f, player.getMaxHealth() * healthShare));
		playTransformEffects(world, player, alien, true);
		OmnitrixOs.send(player, OmnitrixOs.Event.QUICK_CHANGE, alienName(alienId).withColor(alien.color()).formatted(Formatting.BOLD), alienId);
		AlienMasteryManager.add(player, alienId, AlienMasteryManager.PER_ABILITY);
		return Result.SUCCESS;
	}

	/**
	 * Alien besiegt: statt zu sterben zurueck in Menschenform mit DNA-Schock (kurz verlangsamt, benommen); die
	 * Menschen-Lebenspunkte von vor der Verwandlung bleiben. Liefert false, wenn das nicht moeglich ist (kein Platz).
	 */
	private static boolean defeatAlien(ServerPlayerEntity player) {
		TransformationState before = get(player);
		player.setHealth(1.0f);
		if (revert(player, true) != Result.SUCCESS) {
			return false;
		}
		restoreHumanHealth(player, before);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2, false, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0, false, false, true));
		OmnitrixOs.send(player, OmnitrixOs.Event.DNA_SHOCK, Text.translatable("holo.kingdomomnitrix.os.dna_shock_body"),
				Optional.of(Text.translatable("holo.kingdomomnitrix.os.hint.human_health")), before.activeAlien());
		OmnitrixCore.cue(player, com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue.DNA_SHOCK);
		return true;
	}

	/**
	 * Notfall-Verwandlung: toedlicher Schaden in Menschenform mit getragenem Omnitrix. Gewaehlt wird ein freigeschaltetes
	 * Alien, das die Schadensart gut uebersteht ({@code failsafe_tags}), sonst das mit der hoechsten
	 * {@code failsafe_priority}. Liefert true, wenn der Tod abgewendet wurde.
	 */
	private static boolean failsafe(ServerPlayerEntity player, DamageSource source) {
		if (!OmnitrixItem.hasOmnitrix(player) || !OmnitrixCore.canFailsafe(player)) {
			return false;
		}
		var manager = player.getWorld().getRegistryManager();
		Identifier best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Identifier id : HeroDataAccess.get(player).unlockedAliens()) {
			Optional<AlienDefinition> alien = AlienRegistry.get(manager, id);
			if (alien.isEmpty() || !hasSpaceFor(player, alien.get().scale())) {
				continue;
			}
			int score = (alien.get().protectsAgainst(source) || alien.get().isImmuneTo(source) ? 1000 : 0) + alien.get().failsafePriority();
			if (score > bestScore) {
				best = id;
				bestScore = score;
			}
		}
		if (best == null) {
			return false;
		}
		// knapp ueberlebt: die Menschenform kehrt spaeter mit 2 Herzen zurueck
		player.setHealth(Math.min(player.getMaxHealth(), 4.0f));
		Identifier chosen = best;
		if (transform(player, chosen, true) != Result.SUCCESS) {
			return false;
		}
		OmnitrixCore.onFailsafe(player);
		update(player, s -> s.withInvulnerableUntil(player.getWorld().getTime() + 20));
		OmnitrixOs.send(player, OmnitrixOs.Event.EMERGENCY, alienName(chosen).formatted(Formatting.BOLD),
				Optional.of(Text.translatable("holo.kingdomomnitrix.os.hint.failsafe",
						OmnitrixCore.profile(player).failsafeCooldownSeconds() / 60)), Optional.of(chosen));
		return true;
	}

	public static Result useAbility(ServerPlayerEntity player, int slotIndex) {
		TransformationState state = get(player);
		if (!state.isTransformed()) {
			return Result.NOT_TRANSFORMED;
		}
		Identifier alienId = state.activeAlien().orElseThrow();
		Optional<AlienDefinition> found = activeDefinition(player);
		if (found.isEmpty()) {
			return Result.UNKNOWN_ALIEN;
		}
		AlienDefinition alien = found.get();
		if (slotIndex < 0 || slotIndex >= alien.abilities().size()) {
			return Result.NO_SUCH_ABILITY;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		AbilitySlot slot = alien.abilities().get(slotIndex);
		int mastery = AlienMasteryManager.get(player).level(alienId);
		// Master Control: volle Kontrolle ueber die DNA — alle Faehigkeiten frei, unabhaengig von der Meisterschaft
		if (!slot.unlocked(mastery) && !OmnitrixCore.state(player).masterControl()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.ability_locked", Text.translatable(slot.translationKey()),
					slot.unlockLevel()).formatted(Formatting.GOLD), true);
			return Result.ABILITY_LOCKED;
		}
		if (state.cooldownRemaining(slotIndex, now) > 0) {
			return Result.ON_COOLDOWN;
		}
		float energy = state.currentEnergy(alien, now);
		float cost = com.santiq.kingdomomnitrix.progression.AlienMastery.energyCost(slot.energy(), mastery);
		if (energy < cost) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_energy").formatted(Formatting.RED), true);
			return Result.NO_ENERGY;
		}
		Optional<AlienAbility> ability = AbilityRegistry.get(slot.type());
		if (ability.isEmpty()) {
			KingdomOmnitrix.LOGGER.warn("Alien {} verweist auf unbekannte Faehigkeit {}", alienId, slot.type());
			return Result.NO_SUCH_ABILITY;
		}
		AbilityContext context = new AbilityContext(player, world, alienId, alien, slot);
		boolean worked;
		try {
			worked = ability.get().activate(context);
		} catch (RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("Faehigkeit {} von {} ist abgestuerzt", slot.type(), alienId, e);
			return Result.FAILED;
		}
		if (!worked) {
			return Result.FAILED;
		}
		update(player, s -> {
			long cooldown = Math.round(slot.cooldown() * (1.0f - AlienMasteryManager.get(player).cooldownReduction(alienId)));
			TransformationState next = s.afterAbility(slotIndex, energy - cost, now, cooldown);
			return context.invulnerabilityTicks() > 0 ? next.withInvulnerableUntil(now + context.invulnerabilityTicks()) : next;
		});
		AlienMasteryManager.add(player, alienId, AlienMasteryManager.PER_ABILITY);
		return Result.SUCCESS;
	}

	/** Verwandlungsdauer mit Boni aus Heldenstufe, Faehigkeit „Verlaengerte Verwandlung“ und Alien-Meisterschaft. */
	public static int durationTicks(PlayerEntity player, Identifier alienId, AlienDefinition alien) {
		float bonus = ProgressionStats.omnitrixDurationBonus(HeroDataAccess.get(player).level())
				+ ProgressionManager.value(player, HeroAbilityEffect.EXTENDED_TRANSFORMATION)
				+ AlienMasteryManager.get(player).durationBonus(alienId);
		return Math.round(alien.durationTicks() * (1.0f + bonus) * OmnitrixCore.durationFactor(player));
	}

	// --- Ablauf ---------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			TransformationState state = get(player);
			long now = player.getWorld().getTime();
			if (!state.isTransformed()) {
				announceDeviceReady(player, state, now);
				continue;
			}
			Optional<AlienDefinition> alien = activeDefinition(player);
			if (alien.isEmpty()) {
				// Alien wurde aus dem Datenpaket entfernt: sauber beenden.
				removeAttributes(player);
				AlienTraitHandler.clear(player);
				update(player, s -> s.reverted(now));
				continue;
			}
			// Omnitrix-Hitze: Warnung, bei voller Hitze Zwangs-Rueckverwandlung und Sperre
			if (OmnitrixCore.tickTransformed(player)) {
				if (revert(player, true) == Result.SUCCESS) {
					OmnitrixCore.overheat(player);
				}
				continue;
			}
			if (state.remainingTicks(now) <= 0) {
				// Ohne Platz bleibt die Verwandlung bestehen, bis der Spieler ins Freie geht.
				if (revert(player, true) == Result.NO_SPACE && now % 20 == 0) {
					OmnitrixOs.send(player, OmnitrixOs.Event.NEED_SPACE, Text.translatable("message.kingdomomnitrix.need_space"));
				}
				continue;
			}
			// Omnitrix-Warnpiepen in den letzten 5 Sekunden (wie in der Serie), nur fuer den Traeger
			long remaining = state.remainingTicks(now);
			if (remaining <= WARNING_TICKS && remaining % 20 == 0) {
				player.playSoundToPlayer(ModSounds.OMNITRIX_BEEP, SoundCategory.PLAYERS, 0.6f, remaining <= 40 ? 1.25f : 1.0f);
			}
			if (player.isOnFire() && alien.get().isImmuneTo(player.getDamageSources().onFire())) {
				player.extinguish();
			}
			AlienTraitHandler.tick(player, alien.get(), now);
			long drift = OmnitrixMalfunction.rollDrift(player, state.activeAlien().get(), now);
			if (drift > 0L && remaining > OmnitrixMalfunction.minRemaining()) {
				update(player, s -> s.shortened(now, drift, OmnitrixMalfunction.minRemaining()));
				OmnitrixMalfunction.announceDrift(player, drift, state.activeAlien().get());
			}
			if (now % AURA_INTERVAL_TICKS == 0) {
				spawnAura(player, alien.get());
			}
		}
	}

	/** Omnitrix OS: Nachladezeit vorbei („BEREIT“), Ueberhitzung abgekuehlt („ABGEKUEHLT“) — genau einmal im Tick des Endes. */
	private static void announceDeviceReady(ServerPlayerEntity player, TransformationState state, long now) {
		if (!OmnitrixItem.hasOmnitrix(player)) {
			return;
		}
		if (state.rechargeUntil() == now && now > 0) {
			OmnitrixOs.send(player, OmnitrixOs.Event.READY, Text.translatable("holo.kingdomomnitrix.os.ready_body"));
		}
		if (OmnitrixCore.state(player).overheatedUntil() == now && now > 0) {
			OmnitrixOs.send(player, OmnitrixOs.Event.COOLED, Text.translatable("holo.kingdomomnitrix.os.cooled_body"));
			OmnitrixCore.cue(player, com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue.READY);
		}
	}

	private static void onJoin(ServerPlayerEntity player) {
		com.santiq.kingdomomnitrix.omnitrix.MasterControlProgress.check(player);
		TransformationState state = get(player);
		if (!state.isTransformed()) {
			return;
		}
		Optional<AlienDefinition> alien = activeDefinition(player);
		if (alien.isEmpty() || state.remainingTicks(player.getWorld().getTime()) <= 0) {
			removeAttributes(player);
			AlienTraitHandler.clear(player);
			update(player, s -> s.reverted(s.rechargeUntil()));
			return;
		}
		applyAttributes(player, alien.get());
	}

	private static void reapplyAttributes(ServerPlayerEntity player) {
		activeDefinition(player).ifPresent(alien -> applyAttributes(player, alien));
	}

	// --- Attribute und Platz --------------------------------------------------------------------

	private static void applyAttributes(ServerPlayerEntity player, AlienDefinition alien) {
		removeAttributes(player);
		int count = Math.min(alien.attributes().size(), MAX_ATTRIBUTE_BONUSES);
		for (int i = 0; i < count; i++) {
			AlienDefinition.AttributeBonus bonus = alien.attributes().get(i);
			EntityAttributeInstance instance = player.getAttributeInstance(bonus.attribute());
			if (instance == null) {
				KingdomOmnitrix.LOGGER.warn("Spieler hat kein Attribut {}", bonus.attribute().getIdAsString());
				continue;
			}
			instance.addTemporaryModifier(new EntityAttributeModifier(bonusId(i), bonus.amount(), bonus.operation()));
		}
		float masteryHealth = get(player).activeAlien()
				.map(id -> com.santiq.kingdomomnitrix.progression.AlienMastery.healthBonus(AlienMasteryManager.get(player).level(id)))
				.orElse(0.0f);
		EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (masteryHealth > 0.0f && maxHealth != null) {
			maxHealth.addTemporaryModifier(new EntityAttributeModifier(MASTERY_HEALTH_MODIFIER, masteryHealth,
					EntityAttributeModifier.Operation.ADD_VALUE));
		}
		if (alien.scale() != 1.0f) {
			EntityAttributeInstance scale = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
			if (scale != null) {
				scale.addTemporaryModifier(new EntityAttributeModifier(SCALE_MODIFIER, alien.scale() - 1.0,
						EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
	}

	private static void removeAttributes(ServerPlayerEntity player) {
		for (RegistryEntry<EntityAttribute> entry : Registries.ATTRIBUTE.streamEntries().toList()) {
			EntityAttributeInstance instance = player.getAttributeInstance(entry);
			if (instance == null) {
				continue;
			}
			for (int i = 0; i < MAX_ATTRIBUTE_BONUSES; i++) {
				instance.removeModifier(bonusId(i));
			}
			instance.removeModifier(SCALE_MODIFIER);
			instance.removeModifier(MASTERY_HEALTH_MODIFIER);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static Identifier bonusId(int index) {
		return KingdomOmnitrix.id("alien_bonus_" + index);
	}

	/** Prueft, ob ein Koerper mit der Groesse {@code scale} an der aktuellen Position Platz hat. */
	private static boolean hasSpaceFor(ServerPlayerEntity player, float scale) {
		double width = 0.6 * scale;
		double height = 1.8 * scale;
		Box box = new Box(player.getX() - width / 2, player.getY(), player.getZ() - width / 2,
				player.getX() + width / 2, player.getY() + height, player.getZ() + width / 2);
		return player.getWorld().isSpaceEmpty(player, box.contract(1.0E-4));
	}

	// --- Effekte --------------------------------------------------------------------------------

	private static void playTransformEffects(ServerWorld world, ServerPlayerEntity player, AlienDefinition alien, boolean transforming) {
		Vector3f color = colorVector(alien.color());
		DustParticleEffect green = new DustParticleEffect(new Vector3f(0.22f, 1.0f, 0.08f), 1.6f);
		DustParticleEffect tint = new DustParticleEffect(color, 1.2f);
		for (ServerPlayerEntity viewer : world.getPlayers()) {
			if (viewer == player) {
				// eigene Sicht: Ring auf Fusshoehe statt Wolke vor der Kamera (in der Ego-Sicht sonst bildfuellend)
				world.spawnParticles(viewer, green, false, player.getX(), player.getY() + 0.15, player.getZ(), 14, 0.9, 0.05, 0.9, 0.0);
				world.spawnParticles(viewer, tint, false, player.getX(), player.getY() + 0.15, player.getZ(), 8, 0.8, 0.05, 0.8, 0.0);
			} else {
				world.spawnParticles(viewer, green, false, player.getX(), player.getBodyY(0.5), player.getZ(), 14, 0.6, 0.9, 0.6, 0.0);
				world.spawnParticles(viewer, tint, false, player.getX(), player.getBodyY(0.5), player.getZ(), 8, 0.5, 0.8, 0.5, 0.0);
			}
		}
		if (transforming) {
			Vfx.transform(world, player);
		} else {
			Vfx.revert(world, player);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				transforming ? ModSounds.OMNITRIX_TRANSFORM : ModSounds.OMNITRIX_REVERT,
				SoundCategory.PLAYERS, 1.0f, 1.0f);
	}

	/** Aura nur fuer die anderen: der Spieler selbst saehe die Partikel direkt vor der Kamera. */
	private static void spawnAura(ServerPlayerEntity player, AlienDefinition alien) {
		DustParticleEffect dust = new DustParticleEffect(colorVector(alien.color()), 0.8f);
		for (ServerPlayerEntity viewer : player.getServerWorld().getPlayers()) {
			if (viewer != player) {
				player.getServerWorld().spawnParticles(viewer, dust, false, player.getX(), player.getBodyY(0.5), player.getZ(), 2,
						player.getWidth() * 0.4, player.getHeight() * 0.3, player.getWidth() * 0.4, 0.0);
			}
		}
	}

	private static Vector3f colorVector(int rgb) {
		return new Vector3f(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
	}

	public static MutableText alienName(Identifier alienId) {
		return Text.translatable(AlienDefinition.translationKey(alienId));
	}
}
