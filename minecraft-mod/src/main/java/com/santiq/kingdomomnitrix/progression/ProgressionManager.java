package com.santiq.kingdomomnitrix.progression;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Progression: Werte pro Heldenstufe (als Attribut-Modifikatoren) und die KH-Faehigkeitenliste mit AP.
 *
 * <p>Alles ist optional (Design-Vorgabe „Minecraft bleibt Minecraft“): Grundfunktionen wie Combo,
 * Ausweichen und Blocken gibt es ohne Faehigkeiten; Faehigkeiten vertiefen sie nur.</p>
 */
@SuppressWarnings("UnstableApiUsage")
public final class ProgressionManager {
	public static final AttachmentType<HeroAbilities> ABILITIES = AttachmentRegistry.create(KingdomOmnitrix.id("hero_abilities"), builder -> builder
			.persistent(HeroAbilities.CODEC)
			.initializer(() -> HeroAbilities.EMPTY)
			.copyOnDeath()
			.syncWith(HeroAbilities.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	private static final Identifier HEALTH_MODIFIER = KingdomOmnitrix.id("hero_level_health");
	private static final Identifier ATTACK_MODIFIER = KingdomOmnitrix.id("hero_level_attack");
	private static final Identifier SPEED_MODIFIER = KingdomOmnitrix.id("ability_quick_run");
	private static final Identifier JUMP_MODIFIER = KingdomOmnitrix.id("ability_high_jump");
	private static final Identifier SAFE_FALL_MODIFIER = KingdomOmnitrix.id("ability_high_jump_fall");

	private static final int REGEN_INTERVAL_TICKS = 60;
	private static final int REGEN_QUIET_TICKS = 200;

	private static final Map<UUID, Long> SECOND_CHANCE_READY = new HashMap<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
	private static final Map<UUID, Boolean> PENDING_RESPAWN = new HashMap<>();

	public enum Result { EQUIPPED, UNEQUIPPED, UNKNOWN, LOCKED, NO_AP }

	private ProgressionManager() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refresh(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SECOND_CHANCE_READY.remove(handler.player.getUuid());
			LAST_HURT.remove(handler.player.getUuid());
			PENDING_RESPAWN.remove(handler.player.getUuid());
		});
		// Die gespeicherten Attachments (Stufe!) sind beim Respawn-Ereignis noch nicht uebertragen:
		// darum Werte und volles Leben erst im naechsten Tick.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> PENDING_RESPAWN.put(newPlayer.getUuid(), !alive));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				!(entity instanceof ServerPlayerEntity player) || !trySecondChance(player, source));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayerEntity player && damageTaken > 0.0f) {
				LAST_HURT.put(player.getUuid(), player.getServerWorld().getTime());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ProgressionManager::tick);
	}

	// --- Abfragen (Server und Client) -----------------------------------------------------------

	public static HeroAbilities get(PlayerEntity player) {
		HeroAbilities abilities = player.getAttached(ABILITIES);
		return abilities != null ? abilities : HeroAbilities.EMPTY;
	}

	/** Summe der Werte aller ausgeruesteten, freigeschalteten Faehigkeiten mit diesem Effekt (0 = keine). */
	public static float value(PlayerEntity player, HeroAbilityEffect effect) {
		DynamicRegistryManager registries = player.getWorld().getRegistryManager();
		int level = HeroDataAccess.get(player).level();
		float sum = 0.0f;
		for (Identifier id : get(player).equipped()) {
			Optional<HeroAbilityDefinition> definition = HeroAbilityRegistry.get(registries, id);
			if (definition.isPresent() && definition.get().effect() == effect && definition.get().unlockLevel() <= level) {
				sum += definition.get().value();
			}
		}
		return sum;
	}

	public static boolean has(PlayerEntity player, HeroAbilityEffect effect) {
		return value(player, effect) > 0.0f;
	}

	public static int usedAp(PlayerEntity player) {
		DynamicRegistryManager registries = player.getWorld().getRegistryManager();
		return get(player).equipped().stream()
				.mapToInt(id -> HeroAbilityRegistry.get(registries, id).map(HeroAbilityDefinition::apCost).orElse(0))
				.sum();
	}

	public static int totalAp(PlayerEntity player) {
		return ProgressionStats.abilityPoints(HeroDataAccess.get(player).level());
	}

	public static float maxMp(PlayerEntity player) {
		return ProgressionStats.maxMp(HeroDataAccess.get(player).level());
	}

	// --- Aenderungen ----------------------------------------------------------------------------

	private static void update(ServerPlayerEntity player, UnaryOperator<HeroAbilities> change) {
		HeroAbilities before = get(player);
		HeroAbilities after = change.apply(before);
		if (!after.equals(before)) {
			player.setAttached(ABILITIES, after);
		}
	}

	/** An- oder abschalten; der Server prueft Stufe und AP. */
	public static Result toggle(ServerPlayerEntity player, Identifier id) {
		Optional<HeroAbilityDefinition> definition = HeroAbilityRegistry.get(player.getWorld().getRegistryManager(), id);
		if (definition.isEmpty()) {
			return Result.UNKNOWN;
		}
		if (get(player).isEquipped(id)) {
			update(player, abilities -> abilities.with(id, false));
			refreshAttributes(player);
			return Result.UNEQUIPPED;
		}
		if (definition.get().unlockLevel() > HeroDataAccess.get(player).level()) {
			return Result.LOCKED;
		}
		if (usedAp(player) + definition.get().apCost() > totalAp(player)) {
			return Result.NO_AP;
		}
		update(player, abilities -> abilities.with(id, true));
		refreshAttributes(player);
		return Result.EQUIPPED;
	}

	public static void unequipAll(ServerPlayerEntity player) {
		update(player, abilities -> HeroAbilities.EMPTY);
		refreshAttributes(player);
	}

	/** Stufe hat sich geaendert (Aufstieg, Befehl): Werte neu setzen, Unzulaessiges ablegen. */
	public static void onLevelChanged(ServerPlayerEntity player, int before, int after) {
		refresh(player);
	}

	/** Legt Faehigkeiten ab, die unbekannt, (nach einem Zuruecksetzen) gesperrt oder ueber dem AP-Budget sind. */
	private static void revalidate(ServerPlayerEntity player) {
		DynamicRegistryManager registries = player.getWorld().getRegistryManager();
		int level = HeroDataAccess.get(player).level();
		int budget = ProgressionStats.abilityPoints(level);
		List<Identifier> keep = new ArrayList<>();
		int used = 0;
		for (Identifier id : HeroAbilityRegistry.sortedIds(registries)) {
			if (!get(player).isEquipped(id)) {
				continue;
			}
			HeroAbilityDefinition definition = HeroAbilityRegistry.get(registries, id).orElseThrow();
			if (definition.unlockLevel() <= level && used + definition.apCost() <= budget) {
				keep.add(id);
				used += definition.apCost();
			}
		}
		HeroAbilities next = new HeroAbilities(java.util.Set.copyOf(keep));
		if (!next.equals(get(player))) {
			player.setAttached(ABILITIES, next);
		}
	}

	/** Meldet Faehigkeiten, die zwischen den beiden Stufen frei wurden (nach der Aufstiegsmeldung). */
	public static void announceUnlocks(ServerPlayerEntity player, int before, int after) {
		DynamicRegistryManager registries = player.getWorld().getRegistryManager();
		for (Identifier id : HeroAbilityRegistry.sortedIds(registries)) {
			int unlock = HeroAbilityRegistry.get(registries, id).map(HeroAbilityDefinition::unlockLevel).orElse(0);
			if (unlock > before && unlock <= after) {
				MutableText name = HeroAbilityDefinition.name(id).formatted(Formatting.AQUA, Formatting.BOLD);
				player.sendMessage(Text.translatable("message.kingdomomnitrix.ability_unlocked", name).formatted(Formatting.GRAY), false);
			}
		}
	}

	// --- Attribute ------------------------------------------------------------------------------

	public static void refresh(ServerPlayerEntity player) {
		revalidate(player);
		refreshAttributes(player);
	}

	private static void refreshAttributes(ServerPlayerEntity player) {
		int level = HeroDataAccess.get(player).level();
		// dauerhaft gespeichert: so laedt das Leben beim Einloggen nicht gekappt auf 20
		setModifier(player, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_MODIFIER, ProgressionStats.bonusHealth(level),
				EntityAttributeModifier.Operation.ADD_VALUE);
		setModifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, ATTACK_MODIFIER, ProgressionStats.bonusAttack(level),
				EntityAttributeModifier.Operation.ADD_VALUE);
		setModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_MODIFIER, value(player, HeroAbilityEffect.QUICK_RUN),
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		float jump = value(player, HeroAbilityEffect.HIGH_JUMP);
		setModifier(player, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_MODIFIER, jump, EntityAttributeModifier.Operation.ADD_VALUE);
		// hoeher springen soll beim Landen nicht weh tun
		setModifier(player, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, SAFE_FALL_MODIFIER, jump > 0 ? 1.5 : 0.0,
				EntityAttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void setModifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double amount,
			EntityAttributeModifier.Operation operation) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		EntityAttributeModifier current = instance.getModifier(id);
		if (amount == 0.0) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current == null || current.value() != amount || current.operation() != operation) {
			instance.overwritePersistentModifier(new EntityAttributeModifier(id, amount, operation));
		}
	}

	// --- Faehigkeiten mit eigenem Ablauf --------------------------------------------------------

	/** Zweite Chance: toedlicher Treffer laesst 1 Herz uebrig, danach Pause. Nicht gegen /kill oder die Leere. */
	private static boolean trySecondChance(ServerPlayerEntity player, DamageSource source) {
		float cooldownSeconds = value(player, HeroAbilityEffect.SECOND_CHANCE);
		if (cooldownSeconds <= 0.0f || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		long now = player.getServerWorld().getTime();
		Long ready = SECOND_CHANCE_READY.get(player.getUuid());
		if (ready != null && now < ready) {
			return false;
		}
		SECOND_CHANCE_READY.put(player.getUuid(), now + Math.round(cooldownSeconds * 20));
		player.setHealth(2.0f);
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
				SoundCategory.PLAYERS, 1.0f, 1.6f);
		player.getServerWorld().spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getBodyY(0.5), player.getZ(),
				30, 0.4, 0.6, 0.4, 0.3);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.second_chance").formatted(Formatting.GOLD, Formatting.BOLD), true);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (!PENDING_RESPAWN.isEmpty()) {
			for (Map.Entry<UUID, Boolean> entry : Map.copyOf(PENDING_RESPAWN).entrySet()) {
				PENDING_RESPAWN.remove(entry.getKey());
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
				if (player != null) {
					refresh(player);
					if (entry.getValue()) {
						player.setHealth(player.getMaxHealth()); // sonst startet man mit 20 von z. B. 40 Leben
					}
				}
			}
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			long now = player.getServerWorld().getTime();
			if ((now + player.getId()) % REGEN_INTERVAL_TICKS != 0 || !player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
				continue;
			}
			float heal = value(player, HeroAbilityEffect.NANOTECH_REGEN);
			Long hurt = LAST_HURT.get(player.getUuid());
			if (heal > 0.0f && (hurt == null || now - hurt >= REGEN_QUIET_TICKS)) {
				player.heal(heal);
				player.getServerWorld().spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getBodyY(0.6), player.getZ(),
						3, 0.3, 0.4, 0.3, 0.0);
			}
		}
	}

	/** Helden-EP mit EP-Bonus (Faehigkeit wirkt bei hoechstens halbem Leben). */
	public static int boostedExperience(ServerPlayerEntity player, int amount) {
		float boost = value(player, HeroAbilityEffect.EXP_BOOST);
		if (boost > 0.0f && player.getHealth() <= player.getMaxHealth() / 2.0f) {
			return Math.round(amount * (1.0f + boost));
		}
		return amount;
	}

	/** Nur fuer Befehle/Tests: Pause der Zweiten Chance aufheben. */
	public static void resetCooldowns(ServerPlayerEntity player) {
		SECOND_CHANCE_READY.remove(player.getUuid());
	}
}
