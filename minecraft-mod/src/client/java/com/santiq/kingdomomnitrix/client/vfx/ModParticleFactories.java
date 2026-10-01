package com.santiq.kingdomomnitrix.client.vfx;

import com.santiq.kingdomomnitrix.client.vfx.HeroParticle.Style;
import com.santiq.kingdomomnitrix.registry.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.particle.SimpleParticleType;

/** Verhalten der Mod-Partikel (Entscheidung SANTIQ: Effekte immer voll, eigene Texturen). */
public final class ModParticleFactories {
	private ModParticleFactories() {
	}

	private static void register(SimpleParticleType type, Style style) {
		ParticleFactoryRegistry.getInstance().register(type, sprites -> new HeroParticle.Factory(sprites, style));
	}

	public static void register() {
		//                                          life size  end   grav    frict  spin  glow  animate
		register(ModParticles.OMNITRIX_FLASH, new Style(14, 1.4f, 2.2f, 0.0f, 0.80f, 0.05f, true, true));
		register(ModParticles.DNA_HELIX, new Style(26, 0.16f, 0.5f, -0.01f, 0.90f, 0.0f, true, false));
		register(ModParticles.OMNITRIX_REVERT, new Style(16, 0.3f, 0.2f, 0.02f, 0.88f, 0.2f, true, true));
		register(ModParticles.KEYBLADE_SPARK, new Style(14, 0.22f, 0.3f, 0.0f, 0.86f, 0.15f, true, true));
		register(ModParticles.HIT_SPARK, new Style(7, 0.55f, 1.6f, 0.0f, 0.6f, 0.0f, true, true));
		register(ModParticles.SLASH, new Style(6, 0.42f, 0.7f, 0.0f, 0.5f, 0.0f, true, true));
		register(ModParticles.FINISHER_RING, new Style(10, 1.0f, 3.0f, 0.0f, 0.5f, 0.0f, true, true));
		register(ModParticles.FIRE_EMBER, new Style(18, 0.22f, 0.1f, -0.03f, 0.92f, 0.08f, true, true));
		register(ModParticles.ICE_SHARD, new Style(22, 0.2f, 0.6f, 0.04f, 0.92f, 0.12f, true, false));
		register(ModParticles.THUNDER_SPARK, new Style(8, 0.35f, 0.8f, 0.0f, 0.7f, 0.3f, true, true));
		register(ModParticles.CURE_LEAF, new Style(30, 0.2f, 0.6f, -0.015f, 0.94f, 0.1f, true, false));
		register(ModParticles.PLASMA, new Style(9, 0.32f, 0.2f, 0.0f, 0.8f, 0.0f, true, false));
		register(ModParticles.MUZZLE_FLASH, new Style(4, 0.45f, 1.4f, 0.0f, 0.5f, 0.3f, true, true));
		register(ModParticles.ROTOR_WIND, new Style(12, 0.4f, 1.8f, 0.0f, 0.85f, 0.25f, false, true));
	}
}
