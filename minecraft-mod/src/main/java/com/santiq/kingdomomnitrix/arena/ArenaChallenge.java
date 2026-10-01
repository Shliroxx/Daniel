package com.santiq.kingdomomnitrix.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.enemy.RiftDefinition;
import com.santiq.kingdomomnitrix.quest.QuestDefinition;
import java.util.List;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Eine Arena-Herausforderung (Ratchet & Clank): {@code data/<namespace>/kingdomomnitrix/arena_challenge/<name>.json}.
 * Wellen wie bei den Dunkelheitsrissen, optionales Zeitlimit, Belohnung wie bei Quests. Name: {@code arena.<ns>.<name>}.
 */
public record ArenaChallenge(int minLevel, int sortOrder, float eliteChance, int timeLimit,
		List<List<RiftDefinition.Group>> waves, QuestDefinition.Rewards rewards) {
	public static final Codec<ArenaChallenge> CODEC = RecordCodecBuilder.<ArenaChallenge>create(instance -> instance.group(
			Codec.intRange(1, 99).optionalFieldOf("min_level", 1).forGetter(ArenaChallenge::minLevel),
			Codec.INT.optionalFieldOf("sort_order", 0).forGetter(ArenaChallenge::sortOrder),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("elite_chance", 0.0f).forGetter(ArenaChallenge::eliteChance),
			Codec.intRange(0, 3600).optionalFieldOf("time_limit", 0).forGetter(ArenaChallenge::timeLimit),
			RiftDefinition.Group.CODEC.listOf().listOf().fieldOf("waves").forGetter(ArenaChallenge::waves),
			QuestDefinition.Rewards.CODEC.optionalFieldOf("rewards", QuestDefinition.Rewards.NONE).forGetter(ArenaChallenge::rewards)
	).apply(instance, ArenaChallenge::new)).validate(challenge -> challenge.waves().isEmpty() || challenge.waves().stream().anyMatch(List::isEmpty)
			? DataResult.error(() -> "Eine Arena-Herausforderung braucht Wellen, und keine darf leer sein")
			: DataResult.success(challenge));

	public static Text name(Identifier id) {
		return Text.translatable("arena." + id.getNamespace() + "." + id.getPath());
	}
}
