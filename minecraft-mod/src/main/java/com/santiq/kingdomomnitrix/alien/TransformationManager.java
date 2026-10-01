package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
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
import net.minecraft.particle.DustParticleEffect;
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
	private static final int AURA_INTERVAL_TICKS = 5;

	public enum Result {
		SUCCESS, NO_OMNITRIX, UNKNOWN_ALIEN, LOCKED, RECHARGING, ALREADY_TRANSFORMED, NO_SPACE,
		NOT_TRANSFORMED, NO_SUCH_ABILITY, ON_COOLDOWN, NO_ENERGY, FAILED
	}

	private TransformationManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TransformationManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				// Tod beendet jede Verwandlung; die Nachladezeit bleibt bestehen.
				update(newPlayer, state -> state.isTransformed() ? state.reverted(state.rechargeUntil()) : state);
			} else {
				reapplyAttributes(newPlayer);
			}
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
		} else if (state.isTransformed()) {
			removeAttributes(player);
		}
		if (!hasSpaceFor(player, alien.scale())) {
			return Result.NO_SPACE;
		}

		int duration = durationTicks(player, alienId, alien);
		update(player, s -> s.transformed(alienId, alien, now, duration));
		applyAttributes(player, alien);
		playTransformEffects(world, player, alien, true);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.transformed", alienName(alienId).formatted(Formatting.BOLD))
				.withColor(alien.color()), true);
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
		int recharge = Math.round(alien.map(AlienDefinition::rechargeTicks).orElse(0) * (1.0f - quickRecharge));
		long rechargeUntil = now + (timeout ? recharge : recharge / 2);

		if (!hasSpaceFor(player, 1.0f)) {
			// Kleines Alien in engem Gang: Die Rueckverwandlung wuerde den Spieler ersticken lassen.
			return Result.NO_SPACE;
		}
		removeAttributes(player);
		update(player, s -> s.reverted(rechargeUntil));
		alien.ifPresent(a -> playTransformEffects(world, player, a, false));
		player.sendMessage(Text.translatable(timeout ? "message.kingdomomnitrix.timeout" : "message.kingdomomnitrix.reverted")
				.formatted(timeout ? Formatting.RED : Formatting.GREEN), true);
		return Result.SUCCESS;
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
		if (state.cooldownRemaining(slotIndex, now) > 0) {
			return Result.ON_COOLDOWN;
		}
		float energy = state.currentEnergy(alien, now);
		if (energy < slot.energy()) {
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
			TransformationState next = s.afterAbility(slotIndex, energy - slot.energy(), now, cooldown);
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
		return Math.round(alien.durationTicks() * (1.0f + bonus));
	}

	// --- Ablauf ---------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			TransformationState state = get(player);
			if (!state.isTransformed()) {
				continue;
			}
			long now = player.getWorld().getTime();
			Optional<AlienDefinition> alien = activeDefinition(player);
			if (alien.isEmpty()) {
				// Alien wurde aus dem Datenpaket entfernt: sauber beenden.
				removeAttributes(player);
				update(player, s -> s.reverted(now));
				continue;
			}
			if (state.remainingTicks(now) <= 0) {
				// Ohne Platz bleibt die Verwandlung bestehen, bis der Spieler ins Freie geht.
				if (revert(player, true) == Result.NO_SPACE && now % 20 == 0) {
					player.sendMessage(Text.translatable("message.kingdomomnitrix.need_space").formatted(Formatting.RED), true);
				}
				continue;
			}
			if (player.isOnFire() && alien.get().isImmuneTo(player.getDamageSources().onFire())) {
				player.extinguish();
			}
			if (now % AURA_INTERVAL_TICKS == 0) {
				spawnAura(player, alien.get());
			}
		}
	}

	private static void onJoin(ServerPlayerEntity player) {
		TransformationState state = get(player);
		if (!state.isTransformed()) {
			return;
		}
		Optional<AlienDefinition> alien = activeDefinition(player);
		if (alien.isEmpty() || state.remainingTicks(player.getWorld().getTime()) <= 0) {
			removeAttributes(player);
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
		world.spawnParticles(new DustParticleEffect(new Vector3f(0.22f, 1.0f, 0.08f), 1.6f),
				player.getX(), player.getBodyY(0.5), player.getZ(), 50, 0.6, 0.9, 0.6, 0.0);
		world.spawnParticles(new DustParticleEffect(color, 1.2f),
				player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.5, 0.8, 0.5, 0.0);
		if (transforming) {
			world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0, 0, 0, 0);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				transforming ? SoundEvents.BLOCK_BEACON_POWER_SELECT : SoundEvents.BLOCK_BEACON_DEACTIVATE,
				SoundCategory.PLAYERS, 1.0f, transforming ? 1.5f : 1.2f);
	}

	private static void spawnAura(ServerPlayerEntity player, AlienDefinition alien) {
		player.getServerWorld().spawnParticles(new DustParticleEffect(colorVector(alien.color()), 0.8f),
				player.getX(), player.getBodyY(0.5), player.getZ(), 2,
				player.getWidth() * 0.4, player.getHeight() * 0.3, player.getWidth() * 0.4, 0.0);
	}

	private static Vector3f colorVector(int rgb) {
		return new Vector3f(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
	}

	public static MutableText alienName(Identifier alienId) {
		return Text.translatable(AlienDefinition.translationKey(alienId));
	}
}
