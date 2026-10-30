package com.santiq.kingdomomnitrix.gadget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/**
 * Inhalt des Gadget-Guertels. Unveraenderlich: Aenderungen erzeugen eine neue Instanz mit kopierten Stacks.
 * ItemStack vergleicht per Identitaet, darum gibt es {@link #sameAs} fuer inhaltliche Vergleiche.
 */
public record GadgetLoadout(ItemStack back, ItemStack tool) {
	public static final GadgetLoadout EMPTY = new GadgetLoadout(ItemStack.EMPTY, ItemStack.EMPTY);

	public static final Codec<GadgetLoadout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("back", ItemStack.EMPTY).forGetter(GadgetLoadout::back),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("tool", ItemStack.EMPTY).forGetter(GadgetLoadout::tool)
	).apply(instance, GadgetLoadout::new));

	public static final PacketCodec<RegistryByteBuf, GadgetLoadout> PACKET_CODEC = PacketCodec.tuple(
			ItemStack.OPTIONAL_PACKET_CODEC, GadgetLoadout::back,
			ItemStack.OPTIONAL_PACKET_CODEC, GadgetLoadout::tool,
			GadgetLoadout::new);

	public GadgetLoadout {
		back = back == null ? ItemStack.EMPTY : back;
		tool = tool == null ? ItemStack.EMPTY : tool;
	}

	public ItemStack get(GadgetSlot slot) {
		return slot == GadgetSlot.BACK ? back : tool;
	}

	public GadgetLoadout with(GadgetSlot slot, ItemStack stack) {
		ItemStack copy = stack.copy();
		return slot == GadgetSlot.BACK ? new GadgetLoadout(copy, tool.copy()) : new GadgetLoadout(back.copy(), copy);
	}

	public boolean sameAs(GadgetLoadout other) {
		return ItemStack.areEqual(back, other.back) && ItemStack.areEqual(tool, other.tool);
	}

	public boolean isEmpty() {
		return back.isEmpty() && tool.isEmpty();
	}
}
