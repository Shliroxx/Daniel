package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.util.Identifier;

/**
 * Passive Kraft eines Aliens (Datenfeld {@code traits.passives}): wirkt dauerhaft waehrend der Verwandlung, ohne Taste
 * und ohne Energie — z. B. Heatblasts Glutkoerper oder Vierarms Kampfrausch. Das Verhalten liefert
 * {@link com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry}.
 *
 * @param type        ID eines registrierten Passiv-Typs, z. B. {@code kingdomomnitrix:ember_body}
 * @param params      freie Zahlenwerte fuer den Typ; fehlende Werte nehmen die Standards des Typs
 * @param unlockLevel Meisterschaftsstufe, ab der die Kraft wirkt (1 = sofort)
 */
public record PassiveSlot(Identifier type, Map<String, Double> params, int unlockLevel) {
	public static final Codec<PassiveSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("type").forGetter(PassiveSlot::type),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("params", Map.of()).forGetter(PassiveSlot::params),
			Codec.intRange(1, AlienMasteryLevels.MAX).optionalFieldOf("unlock_level", 1).forGetter(PassiveSlot::unlockLevel)
	).apply(instance, PassiveSlot::new));

	public PassiveSlot {
		params = Map.copyOf(params);
	}

	public double param(String key, double fallback) {
		Double value = params.get(key);
		return value != null ? value : fallback;
	}

	public boolean unlocked(int masteryLevel) {
		return masteryLevel >= unlockLevel;
	}

	/** Translation key, z. B. {@code passive.kingdomomnitrix.ember_body}. */
	public String translationKey() {
		return "passive." + type.getNamespace() + "." + type.getPath();
	}
}
