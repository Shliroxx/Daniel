package com.santiq.kingdomomnitrix.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Eine Ratchet-&-Clank-Waffe aus JSON: {@code data/<namespace>/kingdomomnitrix/weapon/<name>.json}.
 *
 * @param item       zugehoeriges Item (liefert Modell und Verhalten)
 * @param price      Kaufpreis am Waffen-Terminal in Bolts (0 = nicht kaeuflich)
 * @param ammoPrice  Bolts pro nachgefuelltem Schuss
 * @param sortOrder  Position im Terminal
 * @param levels     Stufen 1…n; Stufe 1 ist der Grundzustand, ab Stufe 2 mit Preis kaufbar
 */
public record WeaponDefinition(Identifier item, int price, int ammoPrice, int sortOrder, List<Level> levels) {
	public static final Codec<WeaponDefinition> CODEC = RecordCodecBuilder.<WeaponDefinition>create(instance -> instance.group(
			Identifier.CODEC.fieldOf("item").forGetter(WeaponDefinition::item),
			Codec.intRange(0, 1_000_000).optionalFieldOf("price", 0).forGetter(WeaponDefinition::price),
			Codec.intRange(0, 10_000).optionalFieldOf("ammo_price", 1).forGetter(WeaponDefinition::ammoPrice),
			Codec.INT.optionalFieldOf("sort_order", 100).forGetter(WeaponDefinition::sortOrder),
			Level.CODEC.listOf().fieldOf("levels").forGetter(WeaponDefinition::levels)
	).apply(instance, WeaponDefinition::new)).validate(definition -> definition.levels().isEmpty()
			? DataResult.error(() -> "Eine Waffe braucht mindestens eine Stufe")
			: DataResult.success(definition));

	public WeaponDefinition {
		levels = List.copyOf(levels);
	}

	public int maxLevel() {
		return levels.size();
	}

	public Level level(int level) {
		return levels.get(Math.max(0, Math.min(levels.size() - 1, level - 1)));
	}

	/**
	 * @param price    Preis fuer den Aufstieg auf diese Stufe (Stufe 1: ignoriert)
	 * @param maxAmmo  Magazin (0 = keine Munition, z. B. Nahkampf)
	 * @param stats    Zahlenwerte fuer das Item (damage, fire_delay, speed, radius, knockback …)
	 * @param items    zusaetzliche Materialien fuer den Aufstieg (z. B. Raritanium), werden aus dem Inventar genommen
	 */
	public record Level(int price, int maxAmmo, Map<String, Double> stats, List<ItemStack> items) {
		public static final Codec<Level> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.intRange(0, 1_000_000).optionalFieldOf("price", 0).forGetter(Level::price),
				Codec.intRange(0, 10_000).optionalFieldOf("max_ammo", 0).forGetter(Level::maxAmmo),
				Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("stats", Map.of()).forGetter(Level::stats),
				ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Level::items)
		).apply(instance, Level::new));

		public Level {
			stats = Map.copyOf(stats);
			items = List.copyOf(items);
		}

		public double stat(String key, double fallback) {
			Double value = stats.get(key);
			return value != null ? value : fallback;
		}
	}
}
