package com.santiq.kingdomomnitrix.worldevent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.player.HeroData;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.World;

/**
 * Welt-Ereignis (Datenpaket {@code data/<ns>/kingdomomnitrix/world_event/<id>.json}, nur auf dem Server):
 *
 * <pre>
 * {"type": "raritanium_meteor", "weight": 10, "min_level": 1, "duration_seconds": 300,
 *  "dimensions": ["minecraft:overworld"], "rift": "kingdomomnitrix:heavy_assault",
 *  "rewards": {"bolts": 400, "experience": 150, "raritanium": 4, "mythril": 0, "orichalcum": 0, "dna": false}}
 * </pre>
 *
 * @param type          Ablauf ({@link Type})
 * @param weight        Haeufigkeit relativ zu den anderen Ereignissen
 * @param minLevel      erst ab dieser Heldenstufe des ausgewaehlten Spielers
 * @param durationSeconds Hoechstdauer; danach endet das Ereignis ohne Belohnung
 * @param dimensions    erlaubte Welten (leer = alle)
 * @param rift          Riss fuer Invasion und Dunklen Riss (Riss-Datenpaket)
 * @param rewards       Belohnung fuer alle Beteiligten bei Erfolg
 */
public record WorldEventDefinition(Type type, int weight, int minLevel, int durationSeconds, List<Identifier> dimensions,
		Optional<Identifier> rift, Rewards rewards) {

	public enum Type implements StringIdentifiable {
		/** grosse Herzlosen-Welle (Riss mit vielen Wellen), Erfolg: Riss geschlossen */
		HEARTLESS_INVASION,
		/** kleiner Riss mit Ankuendigung */
		DARK_RIFT,
		/** Kapsel stuerzt ab: Krater, Kiste mit DNA und Material, Herzlose als Waechter */
		ALIEN_CRASH,
		/** Meteor schlaegt ein: Krater mit Raritanium-Erz (selten Orichalcum) */
		RARITANIUM_METEOR,
		/** ein Boss erscheint (Dr. Nefarious), Erfolg: besiegt */
		BOSS_SPAWN,
		/** Keyblade-Schrein: in der Naehe halten, waehrend Herzlose angreifen */
		KEYBLADE_SHRINE;

		public static final Codec<Type> CODEC = StringIdentifiable.createCodec(Type::values);

		@Override
		public String asString() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Ablauf lebt von Gegnern (auf „Friedlich“ nicht moeglich: Waechter/Boss verschwinden sofort). */
		public boolean needsMonsters() {
			return this != RARITANIUM_METEOR;
		}

		/** Ablauf braucht einen Riss aus dem Datenpaket. */
		public boolean usesRift() {
			return this == HEARTLESS_INVASION || this == DARK_RIFT;
		}
	}

	public record Rewards(int bolts, int experience, int raritanium, int mythril, int orichalcum, boolean dna) {
		public static final Rewards NONE = new Rewards(0, 0, 0, 0, 0, false);
		public static final Codec<Rewards> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.intRange(0, 100_000).optionalFieldOf("bolts", 0).forGetter(Rewards::bolts),
				Codec.intRange(0, 100_000).optionalFieldOf("experience", 0).forGetter(Rewards::experience),
				Codec.intRange(0, 64).optionalFieldOf("raritanium", 0).forGetter(Rewards::raritanium),
				Codec.intRange(0, 64).optionalFieldOf("mythril", 0).forGetter(Rewards::mythril),
				Codec.intRange(0, 64).optionalFieldOf("orichalcum", 0).forGetter(Rewards::orichalcum),
				Codec.BOOL.optionalFieldOf("dna", false).forGetter(Rewards::dna)
		).apply(instance, Rewards::new));
	}

	public static final Codec<WorldEventDefinition> CODEC = RecordCodecBuilder.<WorldEventDefinition>create(instance -> instance.group(
			Type.CODEC.fieldOf("type").forGetter(WorldEventDefinition::type),
			Codec.intRange(1, 1000).optionalFieldOf("weight", 10).forGetter(WorldEventDefinition::weight),
			Codec.intRange(1, HeroData.MAX_LEVEL).optionalFieldOf("min_level", 1).forGetter(WorldEventDefinition::minLevel),
			Codec.intRange(30, 3600).optionalFieldOf("duration_seconds", 300).forGetter(WorldEventDefinition::durationSeconds),
			Identifier.CODEC.listOf().optionalFieldOf("dimensions", List.of()).forGetter(WorldEventDefinition::dimensions),
			Identifier.CODEC.optionalFieldOf("rift").forGetter(WorldEventDefinition::rift),
			Rewards.CODEC.optionalFieldOf("rewards", Rewards.NONE).forGetter(WorldEventDefinition::rewards)
	).apply(instance, WorldEventDefinition::new)).validate(definition -> definition.type().usesRift() && definition.rift().isEmpty()
			? DataResult.error(() -> definition.type().asString() + " braucht \"rift\"")
			: DataResult.success(definition));

	public WorldEventDefinition {
		dimensions = List.copyOf(dimensions);
	}

	/** Darf das Ereignis in dieser Welt stattfinden? */
	public boolean allows(RegistryKey<World> world) {
		return dimensions.isEmpty() || dimensions.contains(world.getValue());
	}

	static RegistryKey<World> worldKey(Identifier id) {
		return RegistryKey.of(RegistryKeys.WORLD, id);
	}
}
