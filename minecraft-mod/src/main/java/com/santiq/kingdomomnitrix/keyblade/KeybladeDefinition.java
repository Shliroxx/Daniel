package com.santiq.kingdomomnitrix.keyblade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.StringIdentifiable;

/**
 * Werte eines Keyblades aus JSON: {@code data/<namespace>/kingdomomnitrix/keyblade/<name>.json}.
 * Das zugehoerige Item ({@code item}) liefert Modell und Textur; alle Kampf- und Magiewerte stehen hier.
 *
 * @param damage          Schaden auf Stufe 1 (zusaetzlich zum Angriffswert des Spielers)
 * @param damagePerLevel  Schaden pro weiterer Stufe
 * @param magic           Magiekraft auf Stufe 1 (verstaerkt Zauber, Phase 6)
 * @param magicPerLevel   Magiekraft pro weiterer Stufe
 * @param attackSpeed     Tempo-Faktor (2.0 = doppelt so schnelle Combos)
 * @param reach           zusaetzliche Reichweite in Bloecken
 * @param comboLength     Schlaege einer Combo inklusive Finisher
 * @param passives        passive Eigenschaften mit Staerke
 * @param upgrades        Kosten fuer Stufe 2, 3, … (Anzahl = Hoechststufe − 1)
 */
public record KeybladeDefinition(
		Identifier item,
		Rarity rarity,
		float damage,
		float damagePerLevel,
		float magic,
		float magicPerLevel,
		float attackSpeed,
		float reach,
		int comboLength,
		Map<Passive, Float> passives,
		List<UpgradeCost> upgrades) {

	public static final Codec<KeybladeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("item").forGetter(KeybladeDefinition::item),
			Rarity.CODEC.optionalFieldOf("rarity", Rarity.RARE).forGetter(KeybladeDefinition::rarity),
			Codec.floatRange(0.0f, 1000.0f).fieldOf("damage").forGetter(KeybladeDefinition::damage),
			Codec.floatRange(0.0f, 1000.0f).optionalFieldOf("damage_per_level", 1.0f).forGetter(KeybladeDefinition::damagePerLevel),
			Codec.floatRange(0.0f, 1000.0f).optionalFieldOf("magic", 0.0f).forGetter(KeybladeDefinition::magic),
			Codec.floatRange(0.0f, 1000.0f).optionalFieldOf("magic_per_level", 0.5f).forGetter(KeybladeDefinition::magicPerLevel),
			Codec.floatRange(0.25f, 4.0f).optionalFieldOf("attack_speed", 1.0f).forGetter(KeybladeDefinition::attackSpeed),
			Codec.floatRange(0.0f, 4.0f).optionalFieldOf("reach", 0.5f).forGetter(KeybladeDefinition::reach),
			Codec.intRange(2, 8).optionalFieldOf("combo_length", 3).forGetter(KeybladeDefinition::comboLength),
			Codec.unboundedMap(Passive.CODEC, Codec.FLOAT).optionalFieldOf("passives", Map.of()).forGetter(KeybladeDefinition::passives),
			UpgradeCost.CODEC.listOf().optionalFieldOf("upgrades", List.of()).forGetter(KeybladeDefinition::upgrades)
	).apply(instance, KeybladeDefinition::new));

	public KeybladeDefinition {
		passives = Map.copyOf(passives);
		upgrades = List.copyOf(upgrades);
	}

	public int maxLevel() {
		return upgrades.size() + 1;
	}

	public float passive(Passive passive) {
		return passives.getOrDefault(passive, 0.0f);
	}

	/** Passive Eigenschaften. Neue Eintraege brauchen eine Wirkung im Code und eine Uebersetzung. */
	public enum Passive implements StringIdentifiable {
		/** Zusaetzliche Schlaege pro Combo. */
		COMBO_PLUS("combo_plus"),
		/** Chance auf kritische Treffer (0–1). */
		CRITICAL("critical"),
		/** Faktor-Bonus auf den Finisher (0.5 = +50 %). */
		FINISHER_PLUS("finisher_plus"),
		/** Zauberstaerke in Prozent (0.2 = +20 %, Phase 6). */
		MAGIC_BOOST("magic_boost"),
		/** Schnellere MP-Regeneration (0.3 = +30 %, Phase 6). */
		MP_HASTE("mp_haste");

		public static final Codec<Passive> CODEC = StringIdentifiable.createCodec(Passive::values);

		private final String id;

		Passive(String id) {
			this.id = id;
		}

		@Override
		public String asString() {
			return id;
		}

		public String translationKey() {
			return "passive.kingdomomnitrix." + id;
		}
	}

	/** Kosten einer Stufe: Bolts (als Items im Inventar, ab Phase 8 vom Bolt-Konto) und Materialien. */
	public record UpgradeCost(int bolts, List<ItemStack> items) {
		public static final Codec<UpgradeCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.intRange(0, 100_000).optionalFieldOf("bolts", 0).forGetter(UpgradeCost::bolts),
				ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(UpgradeCost::items)
		).apply(instance, UpgradeCost::new));

		public UpgradeCost {
			items = items.stream().map(ItemStack::copy).toList();
		}
	}
}
