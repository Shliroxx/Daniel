package com.santiq.kingdomomnitrix.world.content;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Corrodium: giftgruenes Erz, nur im End (Endstein, alle End-Biome). Wer es ohne Behutsamkeits-Verzauberung abbaut,
 * atmet den Staub ein — kurz Gift und Uebelkeit (ausser mit Klempner-Helm). Aus dem Kristall entstehen die Omnitrix-Werkbank und die Energiekerne.
 */
public final class Corrodium {
	public static final RegistryKey<PlacedFeature> ORE = RegistryKey.of(RegistryKeys.PLACED_FEATURE, KingdomOmnitrix.id("ore_corrodium"));
	/** Dauer der Vergiftung beim Abbau (Ticks) */
	private static final int TOXIC_TICKS = 100;

	private Corrodium() {
	}

	public static void register() {
		BiomeModifications.addFeature(BiomeSelectors.foundInTheEnd(), GenerationStep.Feature.UNDERGROUND_ORES, ORE);
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world.isClient() || !state.isOf(Ben10Blocks.CORRODIUM_ORE) || player.isCreative()) {
				return;
			}
			boolean careful = net.minecraft.enchantment.EnchantmentHelper.getLevel(
					world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(net.minecraft.enchantment.Enchantments.SILK_TOUCH),
					player.getMainHandStack()) > 0;
			// Klempner-Helm filtert den Staub
			if (!careful && !com.santiq.kingdomomnitrix.world.plumber.PlumberArmorItem.hasHelmet(player)) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, TOXIC_TICKS, 0));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, TOXIC_TICKS, 0));
			}
		});
	}
}
