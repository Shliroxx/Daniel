package com.santiq.kingdomomnitrix.enemy;

import com.santiq.kingdomomnitrix.player.HeroData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.Identifier;

/**
 * Ein Dunkelheitsriss aus JSON: {@code data/<namespace>/kingdomomnitrix/rift/<name>.json}.
 * Welche Herzlosen in welcher Welle kommen, ab welcher Spielerstufe der Riss auftaucht und was er gibt.
 *
 * @param minLevel    kleinste Heldenstufe, ab der dieser Riss gewaehlt werden kann
 * @param weight      relative Haeufigkeit unter den passenden Rissen
 * @param eliteChance Chance pro Herzlosem auf die Elite-Variante
 * @param waves       Wellen; jede Welle ist eine Liste von Gruppen
 * @param rewards     Belohnung nach der letzten Welle
 */
public record RiftDefinition(int minLevel, int weight, float eliteChance, List<List<Group>> waves, Rewards rewards) {
	public static final Codec<RiftDefinition> CODEC = RecordCodecBuilder.<RiftDefinition>create(instance -> instance.group(
			Codec.intRange(1, HeroData.MAX_LEVEL).optionalFieldOf("min_level", 1).forGetter(RiftDefinition::minLevel),
			Codec.intRange(1, 1000).optionalFieldOf("weight", 10).forGetter(RiftDefinition::weight),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("elite_chance", 0.05f).forGetter(RiftDefinition::eliteChance),
			Group.CODEC.listOf().listOf().fieldOf("waves").forGetter(RiftDefinition::waves),
			Rewards.CODEC.optionalFieldOf("rewards", Rewards.NONE).forGetter(RiftDefinition::rewards)
	).apply(instance, RiftDefinition::new)).validate(definition -> definition.waves().isEmpty() || definition.waves().stream().anyMatch(List::isEmpty)
			? DataResult.error(() -> "Ein Riss braucht mindestens eine Welle, und keine Welle darf leer sein")
			: DataResult.success(definition));

	public RiftDefinition {
		waves = waves.stream().map(List::copyOf).toList();
	}

	/** Eine Gruppe gleicher Herzloser in einer Welle. */
	public record Group(Identifier entity, int count) {
		public static final Codec<Group> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("entity").forGetter(Group::entity),
				Codec.intRange(1, 32).optionalFieldOf("count", 1).forGetter(Group::count)
		).apply(instance, Group::new));
	}

	/**
	 * @param boltsMin/boltsMax Bolts (als Items)
	 * @param hearts            Herzen
	 * @param experience        Helden-EP fuer jeden beteiligten Spieler
	 * @param crystalChance     Chance auf einen Magie-Kristall
	 * @param dnaChance         Chance auf eine DNA-Probe (zufaelliges Alien)
	 */
	public record Rewards(int boltsMin, int boltsMax, int hearts, int experience, float crystalChance, float dnaChance) {
		public static final Rewards NONE = new Rewards(0, 0, 0, 0, 0.0f, 0.0f);
		public static final Codec<Rewards> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.intRange(0, 10_000).optionalFieldOf("bolts_min", 0).forGetter(Rewards::boltsMin),
				Codec.intRange(0, 10_000).optionalFieldOf("bolts_max", 0).forGetter(Rewards::boltsMax),
				Codec.intRange(0, 64).optionalFieldOf("hearts", 0).forGetter(Rewards::hearts),
				Codec.intRange(0, 100_000).optionalFieldOf("experience", 0).forGetter(Rewards::experience),
				Codec.floatRange(0.0f, 1.0f).optionalFieldOf("crystal_chance", 0.0f).forGetter(Rewards::crystalChance),
				Codec.floatRange(0.0f, 1.0f).optionalFieldOf("dna_chance", 0.0f).forGetter(Rewards::dnaChance)
		).apply(instance, Rewards::new));
	}
}
