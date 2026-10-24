package com.santiq.kingdomomnitrix.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

/**
 * Angebot im Gadgetron-Katalog des Waffen-Terminals (Reiter Ruestung und Gadgets), aus JSON:
 * {@code data/<namespace>/kingdomomnitrix/shop_offer/<name>.json}.
 *
 * @param category  Reiter, in dem das Angebot steht
 * @param name      Uebersetzungsschluessel des Angebots (z. B. ein ganzes Set); leer = Name des ersten Items
 * @param items     was der Spieler bekommt (das erste Item ist zugleich das Symbol)
 * @param price     Preis in Bolts
 * @param materials zusaetzliche Materialien, die aus dem Inventar genommen werden
 * @param sortOrder Position im Reiter
 */
public record ShopOffer(Category category, String name, List<ItemStack> items, int price, List<ItemStack> materials, int sortOrder) {
	public static final RegistryKey<Registry<ShopOffer>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("shop_offer"));

	public enum Category implements StringIdentifiable {
		ARMOR("armor"),
		GADGET("gadget");

		public static final Codec<Category> CODEC = StringIdentifiable.createCodec(Category::values);
		private final String id;

		Category(String id) {
			this.id = id;
		}

		@Override
		public String asString() {
			return id;
		}
	}

	public static final Codec<ShopOffer> CODEC = RecordCodecBuilder.<ShopOffer>create(instance -> instance.group(
			Category.CODEC.fieldOf("category").forGetter(ShopOffer::category),
			Codec.STRING.optionalFieldOf("name", "").forGetter(ShopOffer::name),
			ItemStack.CODEC.listOf().fieldOf("items").forGetter(ShopOffer::items),
			Codec.intRange(0, 10_000_000).fieldOf("price").forGetter(ShopOffer::price),
			ItemStack.CODEC.listOf().optionalFieldOf("materials", List.of()).forGetter(ShopOffer::materials),
			Codec.INT.optionalFieldOf("sort_order", 100).forGetter(ShopOffer::sortOrder)
	).apply(instance, ShopOffer::new)).validate(offer -> offer.items().isEmpty()
			? DataResult.error(() -> "Ein Angebot braucht mindestens ein Item")
			: DataResult.success(offer));

	public ShopOffer {
		items = List.copyOf(items);
		materials = List.copyOf(materials);
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, CODEC);
	}

	public static Optional<ShopOffer> get(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(KEY).flatMap(registry -> registry.getOrEmpty(id));
	}

	/** Angebote eines Reiters in Katalog-Reihenfolge. */
	public static List<Identifier> sortedIds(DynamicRegistryManager manager, Category category) {
		return manager.getOptional(KEY).map(registry -> registry.getIds().stream()
				.filter(id -> registry.getOrEmpty(id).map(offer -> offer.category() == category).orElse(false))
				.sorted(Comparator.<Identifier>comparingInt(id -> registry.getOrEmpty(id).map(ShopOffer::sortOrder).orElse(0))
						.thenComparing(Identifier::toString))
				.toList()).orElse(List.of());
	}

	public ItemStack icon() {
		return items.get(0);
	}
}
