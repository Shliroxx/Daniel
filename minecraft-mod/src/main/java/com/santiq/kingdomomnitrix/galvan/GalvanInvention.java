package com.santiq.kingdomomnitrix.galvan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.List;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;

/**
 * Eine Erfindung im Galvan-Labor (Datenpaket {@code data/<ns>/kingdomomnitrix/galvan_invention/<id>.json}, an Clients
 * synchronisiert, damit das Labor sie ohne eigenes Paket anzeigen kann):
 *
 * <pre>
 * {
 *   "result": "kingdomomnitrix:galvan_cell", "count": 1,
 *   "ingredients": [{"item": "minecraft:redstone", "count": 4}, …],
 *   "bolts": 150,             // optional
 *   "knowledge": 5,           // noetige Summe aller Wissensstufen (Analyse-Datenbank), optional
 *   "hack_level": 0,          // noetige Omnitrix-Hack-Stufe, optional
 *   "sort_order": 10
 * }
 * </pre>
 * Herstellen kann nur, wer gerade Grey Matter ist; der Server prueft alles.
 */
public record GalvanInvention(Item result, int count, List<Ingredient> ingredients, int bolts, int knowledge, int hackLevel, int sortOrder) {
	public static final RegistryKey<Registry<GalvanInvention>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("galvan_invention"));

	public record Ingredient(Item item, int count) {
		public static final Codec<Ingredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Registries.ITEM.getCodec().fieldOf("item").forGetter(Ingredient::item),
				Codec.intRange(1, 64 * 36).optionalFieldOf("count", 1).forGetter(Ingredient::count)
		).apply(instance, Ingredient::new));
	}

	public static final Codec<GalvanInvention> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Registries.ITEM.getCodec().fieldOf("result").forGetter(GalvanInvention::result),
			Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(GalvanInvention::count),
			Ingredient.CODEC.listOf().fieldOf("ingredients").forGetter(GalvanInvention::ingredients),
			Codec.intRange(0, 1_000_000).optionalFieldOf("bolts", 0).forGetter(GalvanInvention::bolts),
			Codec.intRange(0, 10_000).optionalFieldOf("knowledge", 0).forGetter(GalvanInvention::knowledge),
			Codec.intRange(0, GalvanHack.MAX_LEVEL).optionalFieldOf("hack_level", 0).forGetter(GalvanInvention::hackLevel),
			Codec.INT.optionalFieldOf("sort_order", 100).forGetter(GalvanInvention::sortOrder)
	).apply(instance, GalvanInvention::new));

	public static void register() {
		DynamicRegistries.registerSynced(KEY, CODEC);
	}
}
