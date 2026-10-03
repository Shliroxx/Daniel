package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Dauerhafte Eigenschaften eines Aliens waehrend der Verwandlung (Datenfeld {@code traits}):
 *
 * <pre>
 * "traits": {
 *   "flight": true,                                   // Fliegen wie im Kreativmodus (Stinkfly)
 *   "effects": [{"effect": "minecraft:night_vision"}],// immer aktiv
 *   "water_effects": [{"effect": "minecraft:dolphins_grace", "amplifier": 0}], // nur im Wasser
 *   "dry_out_seconds": 30,                            // so lange an Land ohne Nachteil, dann Schwaeche (Ripjaws)
 *   "senses_radius": 16,                              // Gegner im Umkreis leuchten (Wildmutt)
 *   "immune_effects": ["minecraft:poison"],           // diese Effekte wirken nicht (Stinkfly: eigener Gestank)
 *   "transform_style": "fire"                         // eigene Verwandlungs-Signatur ({@link TransformStyle})
 * }
 * </pre>
 * Effekte laufen „ambient“ (ohne Partikel) und werden beim Zurueckverwandeln entfernt — echte Traenke bleiben.
 */
public record AlienTraits(boolean flight, List<Effect> effects, List<Effect> waterEffects, int dryOutSeconds, float sensesRadius,
		List<RegistryEntry<StatusEffect>> immuneEffects, TransformStyle transformStyle) {
	public static final AlienTraits NONE = new AlienTraits(false, List.of(), List.of(), 0, 0.0f, List.of(), TransformStyle.STANDARD);

	/** Status-Effekt mit Stufe (0 = I). */
	public record Effect(RegistryEntry<StatusEffect> effect, int amplifier) {
		public static final Codec<Effect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Registries.STATUS_EFFECT.getEntryCodec().fieldOf("effect").forGetter(Effect::effect),
				Codec.intRange(0, 9).optionalFieldOf("amplifier", 0).forGetter(Effect::amplifier)
		).apply(instance, Effect::new));
	}

	public static final Codec<AlienTraits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("flight", false).forGetter(AlienTraits::flight),
			Effect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(AlienTraits::effects),
			Effect.CODEC.listOf().optionalFieldOf("water_effects", List.of()).forGetter(AlienTraits::waterEffects),
			Codec.intRange(0, 3600).optionalFieldOf("dry_out_seconds", 0).forGetter(AlienTraits::dryOutSeconds),
			Codec.floatRange(0.0f, 48.0f).optionalFieldOf("senses_radius", 0.0f).forGetter(AlienTraits::sensesRadius),
			Registries.STATUS_EFFECT.getEntryCodec().listOf().optionalFieldOf("immune_effects", List.of()).forGetter(AlienTraits::immuneEffects),
			TransformStyle.CODEC.optionalFieldOf("transform_style", TransformStyle.STANDARD).forGetter(AlienTraits::transformStyle)
	).apply(instance, AlienTraits::new));

	public AlienTraits {
		effects = List.copyOf(effects);
		waterEffects = List.copyOf(waterEffects);
		immuneEffects = List.copyOf(immuneEffects);
	}
}
