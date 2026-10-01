package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroup {
	public static final ItemGroup MAIN = Registry.register(Registries.ITEM_GROUP, KingdomOmnitrix.id("main"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(ModItems.KINGDOM_KEY))
					.displayName(Text.translatable("itemGroup.kingdomomnitrix"))
					.entries((context, entries) -> {
						entries.add(ModItems.KINGDOM_KEY);
						entries.add(ModItems.HEART);
						entries.add(ModItems.HI_POTION);
						entries.add(ModItems.PAOPU_FRUIT);
						entries.add(ModItems.SHADOW_SPAWN_EGG);
						entries.add(ModItems.OMNITRIX);
						entries.add(ModItems.OMNIWRENCH);
						entries.add(ModItems.COMBUSTER);
						entries.add(ModItems.FUSION_GRENADE);
						entries.add(ModItems.HELI_PACK);
						entries.add(ModItems.BOLT);
						entries.add(ModItems.BOLT_CRATE);
					})
					.build());

	private ModItemGroup() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Kreativ-Tab registriert");
	}
}
