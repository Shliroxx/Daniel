package com.santiq.kingdomomnitrix.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

/**
 * Quest-Fortschritt eines Spielers: laufende Quests mit Zaehlern je Ziel und abgeschlossene Quests.
 * Unveraenderlich; Aenderungen liefern neue Instanzen.
 */
public record QuestState(Map<Identifier, List<Integer>> active, Set<Identifier> completed) {
	public static final QuestState DEFAULT = new QuestState(Map.of(), Set.of());

	public static final Codec<QuestState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Identifier.CODEC, Codec.INT.listOf()).lenientOptionalFieldOf("active", Map.of()).forGetter(QuestState::active),
			Identifier.CODEC.listOf().<Set<Identifier>>xmap(Set::copyOf, List::copyOf)
					.lenientOptionalFieldOf("completed", Set.of()).forGetter(QuestState::completed)
	).apply(instance, QuestState::new));

	public static final PacketCodec<ByteBuf, QuestState> PACKET_CODEC = PacketCodecs.codec(CODEC);

	public QuestState {
		Map<Identifier, List<Integer>> copy = new HashMap<>();
		active.forEach((id, counts) -> copy.put(id, List.copyOf(counts)));
		active = Map.copyOf(copy);
		completed = Set.copyOf(completed);
	}

	public boolean isActive(Identifier quest) {
		return active.containsKey(quest);
	}

	public boolean isCompleted(Identifier quest) {
		return completed.contains(quest);
	}

	/** Gezaehlter Fortschritt eines Ziels (0, wenn unbekannt). */
	public int count(Identifier quest, int objective) {
		List<Integer> counts = active.get(quest);
		return counts != null && objective < counts.size() ? counts.get(objective) : 0;
	}

	public QuestState start(Identifier quest, int objectives) {
		Map<Identifier, List<Integer>> next = new HashMap<>(active);
		next.put(quest, new ArrayList<>(java.util.Collections.nCopies(objectives, 0)));
		return new QuestState(next, completed);
	}

	public QuestState withCount(Identifier quest, int objective, int value) {
		List<Integer> counts = active.get(quest);
		if (counts == null || objective >= counts.size()) {
			return this;
		}
		List<Integer> changed = new ArrayList<>(counts);
		changed.set(objective, value);
		Map<Identifier, List<Integer>> next = new HashMap<>(active);
		next.put(quest, changed);
		return new QuestState(next, completed);
	}

	public QuestState abandon(Identifier quest) {
		Map<Identifier, List<Integer>> next = new HashMap<>(active);
		next.remove(quest);
		return new QuestState(next, completed);
	}

	public QuestState complete(Identifier quest) {
		Map<Identifier, List<Integer>> next = new HashMap<>(active);
		next.remove(quest);
		Set<Identifier> done = new HashSet<>(completed);
		done.add(quest);
		return new QuestState(next, done);
	}

	public QuestState forget(Identifier quest) {
		Map<Identifier, List<Integer>> next = new HashMap<>(active);
		next.remove(quest);
		Set<Identifier> done = new HashSet<>(completed);
		done.remove(quest);
		return new QuestState(next, done);
	}
}
