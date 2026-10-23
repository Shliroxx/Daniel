package com.santiq.kingdomomnitrix.suit;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

/** Alle 24 Teile der Endgame-Ruestungen und ihr Kreativ-Reiter. */
public final class SuitItems {
	private static final ArmorItem.Type[] PIECES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS};
	private static final Map<SuitSet, Map<ArmorItem.Type, SuitArmorItem>> ITEMS = new EnumMap<>(SuitSet.class);
	private static final List<SuitArmorItem> ALL = new ArrayList<>();

	static {
		for (SuitSet set : SuitSet.values()) {
			Map<ArmorItem.Type, SuitArmorItem> pieces = new EnumMap<>(ArmorItem.Type.class);
			for (ArmorItem.Type type : PIECES) {
				Item.Settings settings = new Item.Settings().rarity(set.rarity()).fireproof()
						.maxDamage(type.getMaxDamage(set.durabilityFactor()));
				SuitArmorItem item = Registry.register(Registries.ITEM, KingdomOmnitrix.id(set.id() + "_" + type.getName()),
						new SuitArmorItem(set, type, settings));
				pieces.put(type, item);
				ALL.add(item);
			}
			ITEMS.put(set, pieces);
		}
	}

	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, KingdomOmnitrix.id("suits"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(get(SuitSet.INFERNOX, ArmorItem.Type.HELMET)))
					.displayName(Text.translatable("itemGroup.kingdomomnitrix.suits"))
					.entries((context, entries) -> ALL.forEach(entries::add))
					.build());

	private SuitItems() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Endgame-Ruestungen registriert: {} Teile", ALL.size());
	}

	public static SuitArmorItem get(SuitSet set, ArmorItem.Type type) {
		return ITEMS.get(set).get(type);
	}

	public static List<SuitArmorItem> all() {
		return Collections.unmodifiableList(ALL);
	}
}
