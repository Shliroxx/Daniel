package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

/**
 * Ultimate-Form eines Aliens (Datenpaket {@code data/<ns>/kingdomomnitrix/alien_ultimate/<alien>.json}, Dateiname =
 * Alien-ID, an Clients synchronisiert — der Renderer braucht das Modell):
 *
 * <pre>
 * {
 *   "model": "kingdomomnitrix:ultimate_humungousaur",  // Koerper waehrend der Ultimate-Form
 *   "seconds": 60,                                      // Dauer
 *   "cooldown": 300,                                    // Sekunden bis zur naechsten Entwicklung
 *   "unlock_level": 6,                                  // noetige Meisterschaft des Aliens
 *   "attributes": [ … wie im Alien … ]                   // zusaetzlich zu den Alien-Boni
 * }
 * </pre>
 * Wie die Ultimate-Form kaempft, entscheidet die Faehigkeitsklasse des Aliens ({@link Evolution#isUltimate}).
 */
public record AlienUltimate(Identifier model, int seconds, int cooldown, int unlockLevel, List<AlienDefinition.AttributeBonus> attributes) {
	public static final RegistryKey<Registry<AlienUltimate>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("alien_ultimate"));

	public static final Codec<AlienUltimate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("model").forGetter(AlienUltimate::model),
			Codec.intRange(5, 3600).optionalFieldOf("seconds", 60).forGetter(AlienUltimate::seconds),
			Codec.intRange(0, 3600).optionalFieldOf("cooldown", 300).forGetter(AlienUltimate::cooldown),
			Codec.intRange(1, 10).optionalFieldOf("unlock_level", 6).forGetter(AlienUltimate::unlockLevel),
			AlienDefinition.AttributeBonus.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(AlienUltimate::attributes)
	).apply(instance, AlienUltimate::new));

	public AlienUltimate {
		attributes = List.copyOf(attributes);
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, CODEC);
	}

	/** Ultimate-Form eines Aliens, falls es eine hat. */
	public static Optional<AlienUltimate> of(DynamicRegistryManager registries, Identifier alienId) {
		return registries.getOptional(KEY).flatMap(registry -> registry.getOrEmpty(alienId));
	}
}
