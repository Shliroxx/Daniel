package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.Identifier;

/**
 * Registry der Faehigkeits-Typen. Andere Mods oder spaetere Phasen registrieren hier neue Typen;
 * Alien-JSONs verweisen per ID darauf.
 */
public final class AbilityRegistry {
	private static final Map<Identifier, AlienAbility> ABILITIES = new LinkedHashMap<>();

	private AbilityRegistry() {
	}

	public static void register(Identifier id, AlienAbility ability) {
		if (ABILITIES.putIfAbsent(id, ability) != null) {
			throw new IllegalStateException("Faehigkeit doppelt registriert: " + id);
		}
	}

	public static Optional<AlienAbility> get(Identifier id) {
		return Optional.ofNullable(ABILITIES.get(id));
	}

	public static Set<Identifier> ids() {
		return Collections.unmodifiableSet(ABILITIES.keySet());
	}

	public static void registerBuiltins() {
		BuiltinAbilities.register();
		KingdomOmnitrix.LOGGER.info("{} Alien-Faehigkeiten registriert", ABILITIES.size());
	}
}
