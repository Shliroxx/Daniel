package com.santiq.kingdomomnitrix.quest;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

/** Datengetriebene Quests, an Clients synchronisiert (Quest-Buch). */
public final class QuestRegistry {
	public static final RegistryKey<Registry<QuestDefinition>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("quest"));

	private QuestRegistry() {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, QuestDefinition.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			DynamicRegistryManager manager = server.getRegistryManager();
			List<Identifier> ids = sortedIds(manager);
			KingdomOmnitrix.LOGGER.info("{} Quests aus Datenpaketen geladen: {}", ids.size(), ids);
			ids.forEach(id -> get(manager, id).ifPresent(quest -> warnAboutReferences(manager, id, quest)));
		});
	}

	/** Tippfehler in Quest-Dateien sollen im Log auffallen, nicht erst im Spiel. */
	private static void warnAboutReferences(DynamicRegistryManager manager, Identifier id, QuestDefinition quest) {
		for (Identifier required : quest.requires()) {
			if (get(manager, required).isEmpty()) {
				KingdomOmnitrix.LOGGER.warn("Quest {} setzt unbekannte Quest {} voraus", id, required);
			}
		}
		for (QuestDefinition.Objective objective : quest.objectives()) {
			if (objective.isTag()) {
				continue;
			}
			boolean known = objective.type() == QuestDefinition.ObjectiveType.KILL
					? Registries.ENTITY_TYPE.containsId(objective.targetId())
					: Registries.ITEM.containsId(objective.targetId());
			if (!known) {
				KingdomOmnitrix.LOGGER.warn("Quest {}: unbekanntes Ziel {}", id, objective.target());
			}
		}
	}

	public static Optional<QuestDefinition> get(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(KEY).flatMap(registry -> registry.getOrEmpty(id));
	}

	/** Quests, die ein bestimmter NPC persoenlich vergibt ({@code giver.npc}), in Anzeige-Reihenfolge. */
	public static List<Identifier> byNpc(DynamicRegistryManager manager, Identifier npcId) {
		return sortedIds(manager).stream()
				.filter(id -> get(manager, id).flatMap(quest -> quest.giver().npc()).filter(npcId::equals).isPresent())
				.toList();
	}

	public static List<Identifier> sortedIds(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.getIds().stream()
				.sorted(Comparator.<Identifier>comparingInt(id -> registry.getOrEmpty(id).map(q -> q.category().ordinal()).orElse(0))
						.thenComparingInt(id -> registry.getOrEmpty(id).map(QuestDefinition::sortOrder).orElse(0))
						.thenComparing(Identifier::toString))
				.toList()).orElse(List.of());
	}
}
