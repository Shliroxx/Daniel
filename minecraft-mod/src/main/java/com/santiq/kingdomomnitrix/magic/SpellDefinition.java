package com.santiq.kingdomomnitrix.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.Identifier;

/**
 * Ein Zauber aus JSON: {@code data/<namespace>/kingdomomnitrix/spell/<name>.json}.
 * Jede Stufe (Feuer → Feura → Feuga) hat eigene MP-Kosten, Abklingzeit und Parameter.
 *
 * @param effect    ID eines registrierten Zaubereffekts ({@link SpellEffects})
 * @param color     Farbe fuer HUD und Partikel
 * @param sortOrder Position in der Zauberleiste
 * @param levels    Stufen, mindestens eine
 */
public record SpellDefinition(Identifier effect, int color, int sortOrder, List<Level> levels) {
	private static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(text -> {
		String hex = text.startsWith("#") ? text.substring(1) : text;
		try {
			return hex.length() == 6 ? DataResult.success(Integer.parseInt(hex, 16)) : DataResult.error(() -> "Farbe muss #RRGGBB sein: " + text);
		} catch (NumberFormatException e) {
			return DataResult.error(() -> "Ungueltige Farbe: " + text);
		}
	}, value -> String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF));

	public static final Codec<SpellDefinition> CODEC = RecordCodecBuilder.<SpellDefinition>create(instance -> instance.group(
			Identifier.CODEC.fieldOf("effect").forGetter(SpellDefinition::effect),
			COLOR_CODEC.fieldOf("color").forGetter(SpellDefinition::color),
			Codec.INT.optionalFieldOf("sort_order", 100).forGetter(SpellDefinition::sortOrder),
			Level.CODEC.listOf().fieldOf("levels").forGetter(SpellDefinition::levels)
	).apply(instance, SpellDefinition::new)).validate(definition -> definition.levels().isEmpty()
			? DataResult.error(() -> "Ein Zauber braucht mindestens eine Stufe")
			: DataResult.success(definition));

	public SpellDefinition {
		levels = List.copyOf(levels);
	}

	public int maxLevel() {
		return levels.size();
	}

	/** Stufe 1-basiert, auf den gueltigen Bereich begrenzt. */
	public Level level(int level) {
		return levels.get(Math.max(0, Math.min(levels.size() - 1, level - 1)));
	}

	/** Translation key einer Stufe, z. B. {@code spell.kingdomomnitrix.fire.2} (Feura). */
	public static String translationKey(Identifier spellId, int level) {
		return "spell." + spellId.getNamespace() + "." + spellId.getPath() + "." + level;
	}

	/**
	 * @param mpCost   MP-Kosten
	 * @param cooldown Abklingzeit in Ticks
	 * @param params   Zahlenwerte fuer den Effekt (Kraft, Anzahl, Radius …)
	 */
	public record Level(float mpCost, int cooldown, Map<String, Double> params) {
		public static final Codec<Level> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.floatRange(0.0f, 1000.0f).fieldOf("mp_cost").forGetter(Level::mpCost),
				Codec.intRange(0, 72_000).optionalFieldOf("cooldown", 20).forGetter(Level::cooldown),
				Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("params", Map.of()).forGetter(Level::params)
		).apply(instance, Level::new));

		public Level {
			params = Map.copyOf(params);
		}

		public double param(String key, double fallback) {
			Double value = params.get(key);
			return value != null ? value : fallback;
		}
	}
}
