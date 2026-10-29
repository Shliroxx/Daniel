package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Dauerhafte Eigenschaften eines Aliens waehrend der Verwandlung (Datenfeld {@code traits}):
 *
 * <pre>
 * "traits": {
 *   "flight": true,                                   // Fliegen (Stinkfly)
 *   "flight_style": "jet",                            // Flugart ({@link FlightStyle}); setzt "flight" mit, Standard wings
 *   "effects": [{"effect": "minecraft:night_vision"}],// immer aktiv
 *   "water_effects": [{"effect": "minecraft:dolphins_grace", "amplifier": 0}], // nur im Wasser
 *   "dry_out_seconds": 30,                            // so lange an Land ohne Nachteil, dann Schwaeche (Ripjaws)
 *   "senses_radius": 16,                              // Gegner im Umkreis leuchten (Wildmutt)
 *   "immune_effects": ["minecraft:poison"],           // diese Effekte wirken nicht (Stinkfly: eigener Gestank)
 *   "transform_style": "fire",                        // eigene Verwandlungs-Signatur ({@link TransformStyle})
 *   "passives": [{"type": "kingdomomnitrix:ember_body"}], // passive Kraefte ({@link PassiveSlot})
 *   "modes": [{"id": "crystal_blade", ...}]          // Haltungen mit Modell-Bauteil ({@link AlienMode})
 * }
 * </pre>
 * Effekte laufen „ambient“ (ohne Partikel) und werden beim Zurueckverwandeln entfernt — echte Traenke bleiben.
 */
public record AlienTraits(boolean flight, Optional<FlightStyle> flightStyleOverride, List<Effect> effects, List<Effect> waterEffects, int dryOutSeconds, float sensesRadius,
		List<RegistryEntry<StatusEffect>> immuneEffects, TransformStyle transformStyle, List<PassiveSlot> passives, List<AlienMode> modes) {
	public static final AlienTraits NONE = new AlienTraits(false, Optional.empty(), List.of(), List.of(), 0, 0.0f, List.of(), TransformStyle.STANDARD, List.of(), List.of());
	/** hoechstens so viele passive Kraefte je Alien */
	public static final int MAX_PASSIVES = 4;
	/** hoechstens so viele Modi je Alien ({@link AlienMode}) */
	public static final int MAX_MODES = 4;

	/** Status-Effekt mit Stufe (0 = I). */
	public record Effect(RegistryEntry<StatusEffect> effect, int amplifier) {
		public static final Codec<Effect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Registries.STATUS_EFFECT.getEntryCodec().fieldOf("effect").forGetter(Effect::effect),
				Codec.intRange(0, 9).optionalFieldOf("amplifier", 0).forGetter(Effect::amplifier)
		).apply(instance, Effect::new));
	}

	public static final Codec<AlienTraits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("flight", false).forGetter(AlienTraits::flight),
			FlightStyle.CODEC.optionalFieldOf("flight_style").forGetter(AlienTraits::flightStyleOverride),
			Effect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(AlienTraits::effects),
			Effect.CODEC.listOf().optionalFieldOf("water_effects", List.of()).forGetter(AlienTraits::waterEffects),
			Codec.intRange(0, 3600).optionalFieldOf("dry_out_seconds", 0).forGetter(AlienTraits::dryOutSeconds),
			Codec.floatRange(0.0f, 48.0f).optionalFieldOf("senses_radius", 0.0f).forGetter(AlienTraits::sensesRadius),
			Registries.STATUS_EFFECT.getEntryCodec().listOf().optionalFieldOf("immune_effects", List.of()).forGetter(AlienTraits::immuneEffects),
			TransformStyle.CODEC.optionalFieldOf("transform_style", TransformStyle.STANDARD).forGetter(AlienTraits::transformStyle),
			PassiveSlot.CODEC.listOf(0, MAX_PASSIVES).optionalFieldOf("passives", List.of()).forGetter(AlienTraits::passives),
			AlienMode.CODEC.listOf(0, MAX_MODES).optionalFieldOf("modes", List.of()).forGetter(AlienTraits::modes)
	).apply(instance, AlienTraits::new));

	public AlienTraits {
		flight = flight || flightStyleOverride.isPresent();
		effects = List.copyOf(effects);
		waterEffects = List.copyOf(waterEffects);
		immuneEffects = List.copyOf(immuneEffects);
		passives = List.copyOf(passives);
		modes = List.copyOf(modes);
	}

	/** Modus mit dieser ID, falls das Alien ihn hat. */
	public java.util.Optional<AlienMode> mode(String id) {
		return modes.stream().filter(m -> m.id().equals(id)).findFirst();
	}

	/** Flugart; ohne Angabe Insektenfluegel. Nur sinnvoll, wenn {@link #flight()}. */
	public FlightStyle flightStyle() {
		return flightStyleOverride.orElse(FlightStyle.WINGS);
	}
}
