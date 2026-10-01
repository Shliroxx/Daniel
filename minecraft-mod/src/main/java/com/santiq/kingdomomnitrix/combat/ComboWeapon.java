package com.santiq.kingdomomnitrix.combat;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;

/**
 * Waffen mit Action-Kampf: Linksklick-Combo, Halten fuer schweren Angriff, Blocken.
 */
public interface ComboWeapon {
	/**
	 * Kampfwerte fuer diesen Stack.
	 *
	 * @param registries Registry-Zugriff der Welt (fuer datengetriebene Werte)
	 */
	default ComboProfile comboProfile(RegistryWrapper.WrapperLookup registries, ItemStack stack) {
		return ComboProfile.DEFAULT;
	}
}
