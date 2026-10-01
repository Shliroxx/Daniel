package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.util.Identifier;

/**
 * Eine Faehigkeit in einem Slot des Aliens.
 *
 * @param type     ID eines registrierten Faehigkeits-Typs, z. B. {@code kingdomomnitrix:fire_blast}
 * @param energy   Energiekosten pro Einsatz
 * @param cooldown Abklingzeit in Ticks
 * @param params   freie Zahlenwerte fuer den Typ (Schaden, Radius …); fehlende Werte nehmen die Standards des Typs
 */
public record AbilitySlot(Identifier type, float energy, int cooldown, Map<String, Double> params) {
	public static final Codec<AbilitySlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("type").forGetter(AbilitySlot::type),
			Codec.floatRange(0.0f, 10_000.0f).optionalFieldOf("energy", 0.0f).forGetter(AbilitySlot::energy),
			Codec.intRange(0, 72_000).optionalFieldOf("cooldown", 20).forGetter(AbilitySlot::cooldown),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("params", Map.of()).forGetter(AbilitySlot::params)
	).apply(instance, AbilitySlot::new));

	public AbilitySlot {
		params = Map.copyOf(params);
	}

	public double param(String key, double fallback) {
		Double value = params.get(key);
		return value != null ? value : fallback;
	}

	/** Translation key, z. B. {@code ability.kingdomomnitrix.fire_blast}. */
	public String translationKey() {
		return "ability." + type.getNamespace() + "." + type.getPath();
	}
}
