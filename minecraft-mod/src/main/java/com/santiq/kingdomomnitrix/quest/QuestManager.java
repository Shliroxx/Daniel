package com.santiq.kingdomomnitrix.quest;

import com.santiq.kingdomomnitrix.party.PartyManager;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.player.HeroData;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.quest.QuestDefinition.Objective;
import com.santiq.kingdomomnitrix.quest.QuestDefinition.ObjectiveType;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Quest-Logik auf dem Server: annehmen, Fortschritt zaehlen (toeten, herstellen), abgeben, Belohnung.
 * Der Fortschritt haengt als Attachment am Spieler (gespeichert, beim Tod behalten, nur an ihn synchronisiert).
 */
@SuppressWarnings("UnstableApiUsage")
public final class QuestManager {
	public static final AttachmentType<QuestState> STATE = AttachmentRegistry.create(KingdomOmnitrix.id("quests"), builder -> builder
			.persistent(QuestState.CODEC)
			.initializer(() -> QuestState.DEFAULT)
			.copyOnDeath()
			.syncWith(QuestState.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Story-Flag: Spieler hat sein Quest-Buch schon bekommen. */
	public static final String BOOK_GIVEN_FLAG = "quest_book_given";
	/** Hoechstens so viele Quests gleichzeitig. */
	public static final int MAX_ACTIVE = 8;

	public enum Status { LOCKED, AVAILABLE, ACTIVE, READY, COMPLETED }

	public enum Result { SUCCESS, UNKNOWN, LOCKED, ALREADY_ACTIVE, ALREADY_DONE, TOO_MANY, NOT_ACTIVE, NOT_READY }

	private QuestManager() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity player) {
				onKill(player, entity);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveBookOnce(handler.getPlayer()));
	}

	// --- Zustand --------------------------------------------------------------------------------

	public static QuestState get(PlayerEntity player) {
		QuestState state = player.getAttached(STATE);
		return state != null ? state : QuestState.DEFAULT;
	}

	private static QuestState update(ServerPlayerEntity player, UnaryOperator<QuestState> change) {
		QuestState before = get(player);
		QuestState after = change.apply(before);
		if (!after.equals(before)) {
			player.setAttached(STATE, after);
		}
		return after;
	}

	/** Fortschritt eines Ziels; Sammelziele zaehlen live, was im Inventar liegt. Gilt auf Client und Server. */
	public static int progress(PlayerEntity player, Identifier questId, QuestDefinition quest, int index) {
		Objective objective = quest.objectives().get(index);
		int value = objective.type() == ObjectiveType.COLLECT
				? countInInventory(player.getInventory(), objective)
				: get(player).count(questId, index);
		return Math.min(value, objective.count());
	}

	public static boolean isReady(PlayerEntity player, Identifier questId, QuestDefinition quest) {
		for (int i = 0; i < quest.objectives().size(); i++) {
			if (progress(player, questId, quest, i) < quest.objectives().get(i).count()) {
				return false;
			}
		}
		return true;
	}

	public static Status status(PlayerEntity player, Identifier questId, QuestDefinition quest) {
		QuestState state = get(player);
		if (state.isActive(questId)) {
			return isReady(player, questId, quest) ? Status.READY : Status.ACTIVE;
		}
		if (state.isCompleted(questId) && !quest.repeatable()) {
			return Status.COMPLETED;
		}
		boolean requirementsMet = quest.requires().stream().allMatch(state::isCompleted);
		return requirementsMet && HeroDataAccess.get(player).level() >= quest.minLevel() ? Status.AVAILABLE : Status.LOCKED;
	}

	private static int countInInventory(PlayerInventory inventory, Objective objective) {
		int total = 0;
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (objective.matches(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	// --- Aktionen -------------------------------------------------------------------------------

	public static Result accept(ServerPlayerEntity player, Identifier questId) {
		Optional<QuestDefinition> quest = QuestRegistry.get(player.getServerWorld().getRegistryManager(), questId);
		if (quest.isEmpty()) {
			return Result.UNKNOWN;
		}
		Status status = status(player, questId, quest.get());
		switch (status) {
			case LOCKED -> {
				return Result.LOCKED;
			}
			case ACTIVE, READY -> {
				return Result.ALREADY_ACTIVE;
			}
			case COMPLETED -> {
				return Result.ALREADY_DONE;
			}
			default -> {
			}
		}
		if (get(player).active().size() >= MAX_ACTIVE) {
			return Result.TOO_MANY;
		}
		update(player, state -> state.start(questId, quest.get().objectives().size()));
		player.sendMessage(Text.translatable("message.kingdomomnitrix.quest_accepted", quest.get().title()).formatted(Formatting.AQUA), false);
		sound(player, SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
		return Result.SUCCESS;
	}

	public static Result abandon(ServerPlayerEntity player, Identifier questId) {
		if (!get(player).isActive(questId)) {
			return Result.NOT_ACTIVE;
		}
		update(player, state -> state.abandon(questId));
		QuestRegistry.get(player.getServerWorld().getRegistryManager(), questId).ifPresent(quest ->
				player.sendMessage(Text.translatable("message.kingdomomnitrix.quest_abandoned", quest.title()).formatted(Formatting.GRAY), false));
		return Result.SUCCESS;
	}

	/** Abgeben: prueft alle Ziele, nimmt Sammel-Items, verteilt die Belohnung. */
	public static Result turnIn(ServerPlayerEntity player, Identifier questId) {
		Optional<QuestDefinition> found = QuestRegistry.get(player.getServerWorld().getRegistryManager(), questId);
		if (found.isEmpty()) {
			return Result.UNKNOWN;
		}
		QuestDefinition quest = found.get();
		if (!get(player).isActive(questId)) {
			return Result.NOT_ACTIVE;
		}
		if (!isReady(player, questId, quest)) {
			return Result.NOT_READY;
		}
		PlayerInventory inventory = player.getInventory();
		for (Objective objective : quest.objectives()) {
			if (objective.type() == ObjectiveType.COLLECT) {
				int removed = inventory.remove(objective::matches, objective.count(), player.playerScreenHandler.getCraftingInput());
				if (removed < objective.count()) {
					// Kann nur passieren, wenn sich das Inventar zwischen Pruefung und Abzug aendert: nichts verschenken.
					KingdomOmnitrix.LOGGER.warn("Quest {}: nur {} von {} {} abgegeben", questId, removed, objective.count(), objective.target());
				}
			}
		}
		inventory.markDirty();
		finish(player, questId, quest);
		return Result.SUCCESS;
	}

	private static void finish(ServerPlayerEntity player, Identifier questId, QuestDefinition quest) {
		update(player, state -> state.complete(questId));
		QuestDefinition.Rewards rewards = quest.rewards();
		if (rewards.bolts() > 0) {
			HeroDataAccess.update(player, data -> data.addBolts(rewards.bolts()));
		}
		if (rewards.experience() > 0) {
			HeroDataAccess.grantExperience(player, rewards.experience());
		}
		for (ItemStack item : rewards.items()) {
			player.getInventory().offerOrDrop(item.copy());
		}
		player.sendMessage(Text.translatable("message.kingdomomnitrix.quest_completed", quest.title()).formatted(Formatting.GOLD), false);
		if (rewards.bolts() > 0 || rewards.experience() > 0) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.quest_reward", rewards.bolts(), rewards.experience())
					.formatted(Formatting.YELLOW), false);
		}
		sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
	}

	/** Befehl: Quest sofort abschliessen (ohne Ziele und ohne Abgabe, mit Belohnung). */
	public static Result forceComplete(ServerPlayerEntity player, Identifier questId) {
		Optional<QuestDefinition> quest = QuestRegistry.get(player.getServerWorld().getRegistryManager(), questId);
		if (quest.isEmpty()) {
			return Result.UNKNOWN;
		}
		finish(player, questId, quest.get());
		return Result.SUCCESS;
	}

	public static void reset(ServerPlayerEntity player, Identifier questId) {
		update(player, state -> state.forget(questId));
	}

	public static void resetAll(ServerPlayerEntity player) {
		update(player, state -> QuestState.DEFAULT);
	}

	// --- Fortschritt ----------------------------------------------------------------------------

	private static void onKill(ServerPlayerEntity player, LivingEntity victim) {
		if (victim == player) {
			return;
		}
		// Gruppe: der Kill zaehlt fuer alle Mitglieder in der Naehe (Sammeln und Herstellen bleiben persoenlich)
		for (ServerPlayerEntity member : PartyManager.nearbyMembers(player, PartyManager.SHARE_RADIUS)) {
			advance(member, ObjectiveType.KILL, objective -> objective.matches(victim.getType()) ? 1 : 0);
		}
	}

	/** Aufgerufen, wenn der Spieler Items herstellt (ItemStack#onCraftByPlayer). */
	public static void onCraft(ServerPlayerEntity player, ItemStack crafted, int amount) {
		if (amount <= 0) {
			return;
		}
		advance(player, ObjectiveType.CRAFT, objective -> objective.matches(crafted) ? amount : 0);
	}

	private interface Gain {
		int of(Objective objective);
	}

	private static void advance(ServerPlayerEntity player, ObjectiveType type, Gain gain) {
		QuestState state = get(player);
		if (state.active().isEmpty()) {
			return;
		}
		DynamicRegistryManager registries = player.getServerWorld().getRegistryManager();
		QuestState next = state;
		for (Map.Entry<Identifier, List<Integer>> entry : state.active().entrySet()) {
			Identifier questId = entry.getKey();
			Optional<QuestDefinition> quest = QuestRegistry.get(registries, questId);
			if (quest.isEmpty()) {
				continue;
			}
			boolean wasReady = isReady(player, questId, quest.get());
			List<Objective> objectives = quest.get().objectives();
			for (int i = 0; i < objectives.size(); i++) {
				Objective objective = objectives.get(i);
				if (objective.type() != type) {
					continue;
				}
				int current = next.count(questId, i);
				int amount = gain.of(objective);
				if (amount <= 0 || current >= objective.count()) {
					continue;
				}
				int value = Math.min(objective.count(), current + amount);
				next = next.withCount(questId, i, value);
				player.sendMessage(Text.translatable("message.kingdomomnitrix.quest_progress", quest.get().title(),
						objective.targetName(), value, objective.count()).formatted(Formatting.AQUA), true);
			}
			QuestState changed = next;
			update(player, s -> changed);
			if (!wasReady && isReady(player, questId, quest.get())) {
				player.sendMessage(Text.translatable("message.kingdomomnitrix.quest_ready", quest.get().title()).formatted(Formatting.GREEN), false);
				sound(player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.7f);
			}
		}
	}

	// --- Quest-Buch -----------------------------------------------------------------------------

	private static void giveBookOnce(ServerPlayerEntity player) {
		HeroData data = HeroDataAccess.get(player);
		if (data.hasFlag(BOOK_GIVEN_FLAG)) {
			return;
		}
		HeroDataAccess.update(player, d -> d.withFlag(BOOK_GIVEN_FLAG, true));
		player.getInventory().offerOrDrop(new ItemStack(ModItems.QUEST_BOOK));
		player.sendMessage(Text.translatable("message.kingdomomnitrix.quest_book_received").formatted(Formatting.GOLD), false);
	}

	private static void sound(ServerPlayerEntity player, net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
