package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.space.AsteroidFeature;
import com.santiq.kingdomomnitrix.world.CrystalSpireFeature;
import com.santiq.kingdomomnitrix.world.LanternPostFeature;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

/** Eigene Weltgenerierungs-Features; Platzierung steht in data/kingdomomnitrix/worldgen/. */
public final class ModFeatures {
	public static final Feature<DefaultFeatureConfig> ASTEROID = Registry.register(Registries.FEATURE,
			KingdomOmnitrix.id("asteroid"), new AsteroidFeature(DefaultFeatureConfig.CODEC));

	public static final Feature<DefaultFeatureConfig> LANTERN_POST = Registry.register(Registries.FEATURE,
			KingdomOmnitrix.id("lantern_post"), new LanternPostFeature(DefaultFeatureConfig.CODEC));
	public static final Feature<DefaultFeatureConfig> CRYSTAL_SPIRE = Registry.register(Registries.FEATURE,
			KingdomOmnitrix.id("crystal_spire"), new CrystalSpireFeature(DefaultFeatureConfig.CODEC));

	private ModFeatures() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Features registriert: {}", Registries.FEATURE.getId(ASTEROID));
	}
}
