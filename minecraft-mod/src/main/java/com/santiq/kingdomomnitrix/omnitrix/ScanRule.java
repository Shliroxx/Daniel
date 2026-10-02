package com.santiq.kingdomomnitrix.omnitrix;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

/**
 * Smart-Scan-Regel des Omnitrix (Datenpaket {@code data/<ns>/kingdomomnitrix/omnitrix_scan/<id>.json}, synchronisiert):
 * trifft die Bedingung zu, bekommen die genannten Aliens Punkte; das freigeschaltete Alien mit den meisten Punkten wird
 * empfohlen, der Grund der staerksten Regel steht im Hologramm.
 *
 * <pre>
 * {"condition": {"type": "lava_near", "radius": 6}, "weights": {"kingdomomnitrix:heatblast": 6}, "reason": "scan.kingdomomnitrix.lava"}
 * </pre>
 * Bedingungen: {@code lava_near} (radius), {@code on_fire}, {@code in_water}, {@code submerged} (Kopf unter Wasser),
 * {@code falling} (min = Fallhoehe), {@code drop_near} (radius, min = Tiefe eines Abgrunds neben dem Spieler),
 * {@code tight_space} (max = freie Hoehe), {@code dark} (max = Lichtstufe), {@code target_armor} (min),
 * {@code target_flying}, {@code target_is} (target = Kreaturen-ID oder {@code #tag}; angeschaute Kreatur, sonst
 * naechstes Monster), {@code entity_near} (radius, min, target), {@code block_near} (radius, min = Anzahl, target =
 * Block-ID oder {@code #tag}), {@code enemies_near} (radius, min = Anzahl), {@code ranged_enemies} (radius, min),
 * {@code explosive_near} (radius), {@code low_health} (max = Anteil). Unbekannte Typen lehnt das Datenpaket ab.
 * Ausgewertet wird auf dem Client des Spielers (nur Empfehlung; verwandeln prueft der Server, nie automatisch).
 */
public record ScanRule(Condition condition, Map<Identifier, Float> weights, String reason) {

	public static final RegistryKey<Registry<ScanRule>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("omnitrix_scan"));

	/** Alle Bedingungstypen, die der Client auswerten kann. */
	public static final Set<String> TYPES = Set.of("lava_near", "on_fire", "in_water", "submerged", "falling", "drop_near",
			"tight_space", "dark", "target_armor", "target_flying", "target_is", "entity_near", "block_near", "enemies_near",
			"ranged_enemies", "explosive_near", "low_health");
	/** Typen, die ein {@code target} brauchen. */
	public static final Set<String> NEEDS_TARGET = Set.of("target_is", "entity_near", "block_near");

	/** Bedingung mit ihren Parametern (ungenutzte bleiben auf dem Standard). */
	public record Condition(String type, float radius, float min, float max, String target) {
		private static final Codec<String> TYPE = Codec.STRING.validate(type -> TYPES.contains(type)
				? DataResult.success(type) : DataResult.error(() -> "unbekannte Smart-Scan-Bedingung: " + type));
		private static final Codec<String> TARGET = Codec.STRING.validate(target -> target.isEmpty()
				|| Identifier.tryParse(target.startsWith("#") ? target.substring(1) : target) != null
				? DataResult.success(target) : DataResult.error(() -> "ungueltiges Ziel: " + target));
		public static final Codec<Condition> CODEC = RecordCodecBuilder.<Condition>create(instance -> instance.group(
				TYPE.fieldOf("type").forGetter(Condition::type),
				Codec.floatRange(0.0f, 64.0f).optionalFieldOf("radius", 8.0f).forGetter(Condition::radius),
				Codec.floatRange(0.0f, 1000.0f).optionalFieldOf("min", 1.0f).forGetter(Condition::min),
				Codec.floatRange(0.0f, 1000.0f).optionalFieldOf("max", 1.0f).forGetter(Condition::max),
				TARGET.optionalFieldOf("target", "").forGetter(Condition::target)
		).apply(instance, Condition::new)).validate(condition -> NEEDS_TARGET.contains(condition.type()) && condition.target().isEmpty()
				? DataResult.error(() -> condition.type() + " braucht \"target\"") : DataResult.success(condition));

		public Condition(String type, float radius, float min, float max) {
			this(type, radius, min, max, "");
		}

		/** Ziel als Tag ({@code #ns:pfad})? */
		public boolean targetIsTag() {
			return target.startsWith("#");
		}

		/** ID des Ziels ohne {@code #}. */
		public Identifier targetId() {
			return Identifier.of(targetIsTag() ? target.substring(1) : target);
		}
	}

	public static final Codec<ScanRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Condition.CODEC.fieldOf("condition").forGetter(ScanRule::condition),
			Codec.unboundedMap(Identifier.CODEC, Codec.floatRange(-100.0f, 100.0f)).fieldOf("weights").forGetter(ScanRule::weights),
			Codec.STRING.fieldOf("reason").forGetter(ScanRule::reason)
	).apply(instance, ScanRule::new));

	public ScanRule {
		weights = Map.copyOf(weights);
	}

	/** Ergebnis: empfohlenes Alien, Punkte und Grund (Uebersetzungsschluessel). */
	public record Recommendation(Identifier alien, float score, String reason) {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, CODEC);
	}

	public static List<ScanRule> all(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.stream().toList()).orElse(List.of());
	}

	/**
	 * Punkte zusammenzaehlen: fuer jede zutreffende Regel die Gewichte der freigeschalteten Aliens. Gewinner = hoechste
	 * Summe ueber 0; Grund = die zutreffende Regel mit dem groessten Beitrag zum Gewinner.
	 */
	public static Optional<Recommendation> recommend(List<ScanRule> rules, Predicate<Condition> matches, Predicate<Identifier> unlocked) {
		Map<Identifier, Float> scores = new HashMap<>();
		List<ScanRule> hits = rules.stream().filter(rule -> matches.test(rule.condition())).toList();
		for (ScanRule rule : hits) {
			rule.weights().forEach((alien, weight) -> {
				if (unlocked.test(alien)) {
					scores.merge(alien, weight, Float::sum);
				}
			});
		}
		Identifier best = null;
		float bestScore = 0.0f;
		for (Map.Entry<Identifier, Float> entry : scores.entrySet()) {
			float score = entry.getValue();
			if (score <= 0.0f) {
				continue;
			}
			// gleiche Punkte: alphabetisch, damit die Empfehlung nicht springt
			if (best == null || score > bestScore || (score == bestScore && entry.getKey().toString().compareTo(best.toString()) < 0)) {
				best = entry.getKey();
				bestScore = score;
			}
		}
		if (best == null) {
			return Optional.empty();
		}
		Identifier winner = best;
		String reason = hits.stream()
				.filter(rule -> rule.weights().getOrDefault(winner, 0.0f) > 0.0f)
				.max((a, b) -> Float.compare(a.weights().get(winner), b.weights().get(winner)))
				.map(ScanRule::reason).orElse("scan.kingdomomnitrix.generic");
		return Optional.of(new Recommendation(winner, bestScore, reason));
	}
}
