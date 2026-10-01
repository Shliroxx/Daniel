package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * Ein Alien des Omnitrix, vollstaendig aus JSON geladen:
 * {@code data/<namespace>/kingdomomnitrix/alien/<name>.json}.
 *
 * <p>Ein neues Alien braucht nur eine neue JSON-Datei (plus Uebersetzung und Modell).
 * Neue Faehigkeits-Typen werden in {@link com.santiq.kingdomomnitrix.ability.AbilityRegistry} registriert.</p>
 *
 * @param color          Farbe fuer HUD, Rad und Aura
 * @param sortOrder      Position im Alien-Rad (aufsteigend)
 * @param durationTicks  Dauer einer Verwandlung
 * @param rechargeTicks  Nachladezeit nach Ablauf (bei vorzeitiger Rueckverwandlung die Haelfte)
 * @param maxEnergy      Energie-Vorrat fuer Faehigkeiten
 * @param energyRegen    Energie pro Sekunde
 * @param scale          Koerpergroesse relativ zum Menschen
 * @param attributes     Attribut-Boni waehrend der Verwandlung
 * @param immunities     Schadensarten (Tags), die das Alien nicht verletzen
 * @param abilities      Faehigkeiten in Slot-Reihenfolge (hoechstens {@link #MAX_ABILITIES})
 * @param dnaSources     Gegner, die DNA-Proben dieses Aliens fallen lassen koennen
 * @param model          Modell-ID fuer den Alien-Koerper
 * @param prototype      true = Inhalt noch nicht final (wird im Rad markiert)
 */
public record AlienDefinition(
		int color,
		int sortOrder,
		int durationTicks,
		int rechargeTicks,
		float maxEnergy,
		float energyRegen,
		float scale,
		List<AttributeBonus> attributes,
		List<TagKey<DamageType>> immunities,
		List<AbilitySlot> abilities,
		List<DnaSource> dnaSources,
		Identifier model,
		boolean prototype) {

	public static final int MAX_ABILITIES = 3;
	public static final float MIN_SCALE = 0.25f;
	public static final float MAX_SCALE = 3.0f;

	private static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(AlienDefinition::parseColor,
			value -> String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF));

	public static final Codec<AlienDefinition> CODEC = RecordCodecBuilder.<AlienDefinition>create(instance -> instance.group(
			COLOR_CODEC.fieldOf("color").forGetter(AlienDefinition::color),
			Codec.INT.optionalFieldOf("sort_order", 100).forGetter(AlienDefinition::sortOrder),
			Codec.intRange(20, 72_000).fieldOf("duration").forGetter(AlienDefinition::durationTicks),
			Codec.intRange(0, 72_000).fieldOf("recharge").forGetter(AlienDefinition::rechargeTicks),
			Codec.floatRange(1.0f, 10_000.0f).optionalFieldOf("max_energy", 100.0f).forGetter(AlienDefinition::maxEnergy),
			Codec.floatRange(0.0f, 1_000.0f).optionalFieldOf("energy_regen", 5.0f).forGetter(AlienDefinition::energyRegen),
			Codec.floatRange(MIN_SCALE, MAX_SCALE).optionalFieldOf("scale", 1.0f).forGetter(AlienDefinition::scale),
			AttributeBonus.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(AlienDefinition::attributes),
			TagKey.codec(RegistryKeys.DAMAGE_TYPE).listOf().optionalFieldOf("immunities", List.of()).forGetter(AlienDefinition::immunities),
			AbilitySlot.CODEC.listOf().fieldOf("abilities").forGetter(AlienDefinition::abilities),
			DnaSource.CODEC.listOf().optionalFieldOf("dna_sources", List.of()).forGetter(AlienDefinition::dnaSources),
			Identifier.CODEC.fieldOf("model").forGetter(AlienDefinition::model),
			Codec.BOOL.optionalFieldOf("prototype", false).forGetter(AlienDefinition::prototype)
	).apply(instance, AlienDefinition::new)).validate(AlienDefinition::validate);

	public AlienDefinition {
		attributes = List.copyOf(attributes);
		immunities = List.copyOf(immunities);
		abilities = List.copyOf(abilities);
		dnaSources = List.copyOf(dnaSources);
	}

	private static DataResult<Integer> parseColor(String text) {
		String hex = text.startsWith("#") ? text.substring(1) : text;
		if (hex.length() != 6) {
			return DataResult.error(() -> "Farbe muss #RRGGBB sein: " + text);
		}
		try {
			return DataResult.success(Integer.parseInt(hex, 16));
		} catch (NumberFormatException e) {
			return DataResult.error(() -> "Ungueltige Farbe: " + text);
		}
	}

	private static DataResult<AlienDefinition> validate(AlienDefinition definition) {
		if (definition.abilities.isEmpty() || definition.abilities.size() > MAX_ABILITIES) {
			return DataResult.error(() -> "Ein Alien braucht 1 bis " + MAX_ABILITIES + " Faehigkeiten, hat aber " + definition.abilities.size());
		}
		return DataResult.success(definition);
	}

	/** Translation key des Namens, z. B. {@code alien.kingdomomnitrix.heatblast}. */
	public static String translationKey(Identifier alienId) {
		return "alien." + alienId.getNamespace() + "." + alienId.getPath();
	}

	public boolean isImmuneTo(net.minecraft.entity.damage.DamageSource source) {
		for (TagKey<DamageType> tag : immunities) {
			if (source.isIn(tag)) {
				return true;
			}
		}
		return false;
	}

	/** Ein Attribut-Bonus waehrend der Verwandlung. */
	public record AttributeBonus(RegistryEntry<EntityAttribute> attribute, double amount, EntityAttributeModifier.Operation operation) {
		public static final Codec<AttributeBonus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Registries.ATTRIBUTE.getEntryCodec().fieldOf("attribute").forGetter(AttributeBonus::attribute),
				Codec.DOUBLE.fieldOf("amount").forGetter(AttributeBonus::amount),
				EntityAttributeModifier.Operation.CODEC.optionalFieldOf("operation", EntityAttributeModifier.Operation.ADD_VALUE)
						.forGetter(AttributeBonus::operation)
		).apply(instance, AttributeBonus::new));
	}

	/** Gegner-Typ und Wahrscheinlichkeit fuer einen DNA-Drop (nur bei Toetung durch einen Spieler). */
	public record DnaSource(Identifier entity, float chance) {
		public static final Codec<DnaSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("entity").forGetter(DnaSource::entity),
				Codec.floatRange(0.0f, 1.0f).fieldOf("chance").forGetter(DnaSource::chance)
		).apply(instance, DnaSource::new));
	}
}
