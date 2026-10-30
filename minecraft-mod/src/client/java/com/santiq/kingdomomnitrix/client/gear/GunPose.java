package com.santiq.kingdomomnitrix.client.gear;

import com.santiq.kingdomomnitrix.gadget.SwingshotItem;
import com.santiq.kingdomomnitrix.suit.weapon.SuitWeaponItem;
import com.santiq.kingdomomnitrix.weapon.CombusterItem;
import com.santiq.kingdomomnitrix.weapon.GadgetronWeaponItem;
import net.minecraft.item.ItemStack;

/** Schusswaffen werden in der dritten Person zweihaendig im Anschlag gehalten (Vanilla-Armbrust-Haltung). */
public final class GunPose {
	private GunPose() {
	}

	public static boolean isGun(ItemStack stack) {
		if (stack.getItem() instanceof SuitWeaponItem weapon) {
			return !weapon.weapon().melee();
		}
		return stack.getItem() instanceof CombusterItem || stack.getItem() instanceof SwingshotItem
				|| stack.getItem() instanceof GadgetronWeaponItem;
	}
}
