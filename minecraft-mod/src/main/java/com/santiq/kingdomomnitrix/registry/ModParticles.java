package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Eigene Partikel. Texturen und Definitionen erzeugt {@code tools/generate_particles.py};
 * Aussehen und Verhalten (Leuchten, Wachsen, Schwerkraft) legt der Client fest.
 */
public final class ModParticles {
	// Omnitrix
	public static final SimpleParticleType OMNITRIX_FLASH = register("omnitrix_flash");
	public static final SimpleParticleType DNA_HELIX = register("dna_helix");
	public static final SimpleParticleType OMNITRIX_REVERT = register("omnitrix_revert");
	// Kampf
	public static final SimpleParticleType KEYBLADE_SPARK = register("keyblade_spark");
	public static final SimpleParticleType HIT_SPARK = register("hit_spark");
	public static final SimpleParticleType SLASH = register("slash");
	public static final SimpleParticleType FINISHER_RING = register("finisher_ring");
	// Magie
	public static final SimpleParticleType FIRE_EMBER = register("fire_ember");
	public static final SimpleParticleType ICE_SHARD = register("ice_shard");
	public static final SimpleParticleType THUNDER_SPARK = register("thunder_spark");
	public static final SimpleParticleType CURE_LEAF = register("cure_leaf");
	// Technik
	public static final SimpleParticleType PLASMA = register("plasma");
	public static final SimpleParticleType MUZZLE_FLASH = register("muzzle_flash");
	public static final SimpleParticleType ROTOR_WIND = register("rotor_wind");

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		// alwaysSpawn = true: auch bei Partikel-Einstellung „Wenig“ sichtbar (Entscheidung SANTIQ: Effekte immer voll)
		return Registry.register(Registries.PARTICLE_TYPE, KingdomOmnitrix.id(name), FabricParticleTypes.simple(true));
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Partikel registriert");
	}
}
