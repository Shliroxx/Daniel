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
 * @param abilities      Faehigkeiten: Tasten-Pool ({@link AbilitySlot#isKey()}, hoechstens {@link #MAX_POOL}, davon
 *                       {@link #LOADOUT_SIZE} ausgeruestet) und Kombos (hoechstens {@link #MAX_COMBOS}). Der Index in
 *                       dieser Liste ist die feste Nummer der Faehigkeit (Abklingzeit, Pose, Loadout).
 * @param dna            Freischaltung: Start-Alien oder DNA-Scan (Lebewesen je Welt, Anzahl Proben)
 * @param model          Modell-ID fuer den Alien-Koerper
 * @param prototype      true = Inhalt noch nicht final (wird im Rad markiert)
 * @param failsafeTags   Schadensarten, gegen die dieses Alien bei der Notfall-Verwandlung gewaehlt wird
 * @param failsafePriority Rangfolge ohne passende Schadensart (hoeher = eher gewaehlt, z. B. robuste Aliens)
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
		DnaInfo dna,
		Identifier model,
		boolean prototype,
		List<TagKey<DamageType>> failsafeTags,
		int failsafePriority,
		AlienTraits traits) {

	/** Tasten je Alien (R V B, Shift + R V B) */
	public static final int LOADOUT_SIZE = 6;
	/** Tasten-Faehigkeiten, aus denen das Loadout gewaehlt wird */
	public static final int MAX_POOL = 12;
	/** Kombos (Sprint-, Luft-, Schleich-, Sturz-Angriff, Finisher) */
	public static final int MAX_COMBOS = 6;
	/** alle Faehigkeiten zusammen */
	public static final int MAX_ABILITIES = MAX_POOL + MAX_COMBOS;
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
			DnaInfo.CODEC.optionalFieldOf("dna", DnaInfo.NONE).forGetter(AlienDefinition::dna),
			Identifier.CODEC.fieldOf("model").forGetter(AlienDefinition::model),
			Codec.BOOL.optionalFieldOf("prototype", false).forGetter(AlienDefinition::prototype),
			TagKey.codec(RegistryKeys.DAMAGE_TYPE).listOf().optionalFieldOf("failsafe_tags", List.of()).forGetter(AlienDefinition::failsafeTags),
			Codec.INT.optionalFieldOf("failsafe_priority", 0).forGetter(AlienDefinition::failsafePriority),
			AlienTraits.CODEC.optionalFieldOf("traits", AlienTraits.NONE).forGetter(AlienDefinition::traits)
	).apply(instance, AlienDefinition::new)).validate(AlienDefinition::validate);

	public AlienDefinition {
		attributes = List.copyOf(attributes);
		immunities = List.copyOf(immunities);
		abilities = List.copyOf(abilities);
		failsafeTags = List.copyOf(failsafeTags);
	}

	/** Uebersteht dieses Alien die Schadensart besonders gut (Notfall-Verwandlung)? */
	public boolean protectsAgainst(net.minecraft.entity.damage.DamageSource source) {
		for (TagKey<DamageType> tag : failsafeTags) {
			if (source.isIn(tag)) {
				return true;
			}
		}
		return false;
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
		long keys = definition.abilities.stream().filter(AbilitySlot::isKey).count();
		long combos = definition.abilities.size() - keys;
		if (keys < 1 || keys > MAX_POOL) {
			return DataResult.error(() -> "Ein Alien braucht 1 bis " + MAX_POOL + " Tasten-Faehigkeiten, hat aber " + keys);
		}
		if (combos > MAX_COMBOS) {
			return DataResult.error(() -> "Hoechstens " + MAX_COMBOS + " Kombos erlaubt, gefunden: " + combos);
		}
		if (!definition.abilities.get(0).isKey()) {
			return DataResult.error(() -> "Die erste Faehigkeit muss eine Tasten-Faehigkeit sein");
		}
		if (definition.abilities.get(0).unlockLevel() != 1) {
			return DataResult.error(() -> "Die erste Faehigkeit muss ab Meisterschaft 1 frei sein");
		}
		return DataResult.success(definition);
	}

	/** Indizes aller Tasten-Faehigkeiten (Pool) in Datenreihenfolge. */
	public java.util.List<Integer> keyIndices() {
		java.util.List<Integer> out = new java.util.ArrayList<>();
		for (int i = 0; i < abilities.size(); i++) {
			if (abilities.get(i).isKey()) {
				out.add(i);
			}
		}
		return out;
	}

	/** Indizes aller Kombos in Datenreihenfolge. */
	public java.util.List<Integer> comboIndices() {
		java.util.List<Integer> out = new java.util.ArrayList<>();
		for (int i = 0; i < abilities.size(); i++) {
			if (!abilities.get(i).isKey()) {
				out.add(i);
			}
		}
		return out;
	}

	/** Standard-Loadout: die ersten {@link #LOADOUT_SIZE} Tasten-Faehigkeiten in Datenreihenfolge. */
	public java.util.List<Integer> defaultLoadout() {
		java.util.List<Integer> keys = keyIndices();
		return java.util.List.copyOf(keys.subList(0, Math.min(LOADOUT_SIZE, keys.size())));
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

	/**
	 * Wie das Alien ins Omnitrix kommt: {@code starter} = von Anfang an (Heatblast, Vierarm, XLR8); sonst per DNA-Scan
	 * — {@code samples} verschiedene Exemplare scannen. Mit {@code species} lebt die Art als wilde Kreatur auf ihrer
	 * Heimatwelt und nur sie zaehlt; {@code sources} (Lebewesen je Welt) gilt nur, solange es noch keine Heimatwelt gibt.
	 */
	public record DnaInfo(boolean starter, int samples, List<DnaSource> sources, java.util.Optional<Species> species) {
		public static final DnaInfo NONE = new DnaInfo(false, 3, List.of(), java.util.Optional.empty());
		public static final Codec<DnaInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("starter", false).forGetter(DnaInfo::starter),
				Codec.intRange(1, 20).optionalFieldOf("samples", 3).forGetter(DnaInfo::samples),
				DnaSource.CODEC.listOf().optionalFieldOf("sources", List.of()).forGetter(DnaInfo::sources),
				Species.CODEC.optionalFieldOf("species").forGetter(DnaInfo::species)
		).apply(instance, DnaInfo::new));

		public DnaInfo {
			sources = List.copyOf(sources);
		}
	}

	/** Verhalten der wilden Art gegenueber Spielern. */
	public enum Behavior implements net.minecraft.util.StringIdentifiable {
		/** flieht vor Spielern */
		SHY,
		/** friedlich, wehrt sich mit der ganzen Gruppe */
		NEUTRAL,
		/** greift Spieler in Sichtweite an */
		HOSTILE,
		/** jagt im Rudel: greift an und ruft die Gruppe dazu */
		PACK;

		public static final Codec<Behavior> CODEC = net.minecraft.util.StringIdentifiable.createCodec(Behavior::values);

		@Override
		public String asString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Wirkung eines Treffers der wilden Art. */
	public enum HitEffect implements net.minecraft.util.StringIdentifiable {
		NONE, FIRE, FREEZE, POISON, KNOCKBACK, SHOCK, SONIC, CRYSTAL, DRAIN;

		public static final Codec<HitEffect> CODEC = net.minecraft.util.StringIdentifiable.createCodec(HitEffect::values);

		@Override
		public String asString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * Die Art in freier Wildbahn: lebt auf {@code home}, Gruppen von {@code group} Tieren, hoechstens {@code max} in der
	 * Naehe eines Spielers; {@code name} = Uebersetzungsschluessel des Artnamens (z. B. Sonorosianer).
	 */
	public record Species(Identifier home, String name, Behavior behavior, float health, float damage, float speed, HitEffect effect,
			List<Integer> group, int max, boolean aquatic, boolean flying) {
		public static final Codec<Species> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("home").forGetter(Species::home),
				Codec.STRING.fieldOf("name").forGetter(Species::name),
				Behavior.CODEC.optionalFieldOf("behavior", Behavior.NEUTRAL).forGetter(Species::behavior),
				Codec.floatRange(1.0f, 1000.0f).optionalFieldOf("health", 20.0f).forGetter(Species::health),
				Codec.floatRange(0.0f, 100.0f).optionalFieldOf("damage", 4.0f).forGetter(Species::damage),
				Codec.floatRange(0.05f, 1.0f).optionalFieldOf("speed", 0.28f).forGetter(Species::speed),
				HitEffect.CODEC.optionalFieldOf("effect", HitEffect.NONE).forGetter(Species::effect),
				Codec.intRange(1, 12).listOf(2, 2).optionalFieldOf("group", List.of(1, 3)).forGetter(Species::group),
				Codec.intRange(1, 40).optionalFieldOf("max", 8).forGetter(Species::max),
				Codec.BOOL.optionalFieldOf("aquatic", false).forGetter(Species::aquatic),
				Codec.BOOL.optionalFieldOf("flying", false).forGetter(Species::flying)
		).apply(instance, Species::new));
	}

	/** Scanbares Lebewesen; {@code dimension} leer = in jeder Welt. */
	public record DnaSource(Identifier entity, java.util.Optional<Identifier> dimension) {
		public static final Codec<DnaSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("entity").forGetter(DnaSource::entity),
				Identifier.CODEC.optionalFieldOf("dimension").forGetter(DnaSource::dimension)
		).apply(instance, DnaSource::new));

		public boolean matches(Identifier entityType, Identifier world) {
			return entity.equals(entityType) && dimension.map(world::equals).orElse(true);
		}
	}
}
