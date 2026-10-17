package com.santiq.kingdomomnitrix.ability.passive;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.Identifier;

/** Registry der Passiv-Typen; Alien-JSONs verweisen in {@code traits.passives} per ID darauf. */
public final class PassiveRegistry {
	private static final Map<Identifier, AlienPassive> PASSIVES = new LinkedHashMap<>();

	private PassiveRegistry() {
	}

	public static void register(Identifier id, AlienPassive passive) {
		if (PASSIVES.putIfAbsent(id, passive) != null) {
			throw new IllegalStateException("Passive Kraft doppelt registriert: " + id);
		}
	}

	public static Optional<AlienPassive> get(Identifier id) {
		return Optional.ofNullable(PASSIVES.get(id));
	}

	public static Set<Identifier> ids() {
		return Collections.unmodifiableSet(PASSIVES.keySet());
	}
}
