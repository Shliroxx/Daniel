package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.DnaSampleItem;
import com.santiq.kingdomomnitrix.magic.SpellCrystalItem;
import com.santiq.kingdomomnitrix.magic.SpellRegistry;
import java.util.Comparator;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ModItemGroup {
	public static final ItemGroup MAIN = Registry.register(Registries.ITEM_GROUP, KingdomOmnitrix.id("main"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(ModItems.KINGDOM_KEY))
					.displayName(Text.translatable("itemGroup.kingdomomnitrix"))
					.entries((context, entries) -> {
						entries.add(ModItems.KINGDOM_KEY);
						entries.add(ModItems.OATHKEEPER);
						entries.add(ModItems.KEYBLADE_FORGE);
						entries.add(ModItems.HEART);
						context.lookup().getOptionalWrapper(SpellRegistry.KEY).ifPresent(spells ->
								spells.streamKeys()
										.map(key -> key.getValue())
										.sorted(Comparator.comparing(Identifier::toString))
										.forEach(spellId -> entries.add(SpellCrystalItem.create(spellId))));
						entries.add(ModItems.HI_POTION);
						entries.add(ModItems.PAOPU_FRUIT);
						entries.add(ModItems.SHADOW_SPAWN_EGG);
						entries.add(ModItems.OMNITRIX);
						// Eine DNA-Probe pro Alien aus den geladenen Datenpaketen (nur in einer Welt verfuegbar).
						context.lookup().getOptionalWrapper(AlienRegistry.KEY).ifPresent(aliens ->
								aliens.streamKeys()
										.map(key -> key.getValue())
										.sorted(Comparator.comparing(Identifier::toString))
										.forEach(alienId -> entries.add(DnaSampleItem.create(alienId))));
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
