package com.santiq.kingdomomnitrix.gadget;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Serverseitige Sicht auf den Gadget-Guertel fuer das Guertel-Menue; jede Aenderung landet sofort im Attachment. */
public class GadgetInventory extends SimpleInventory {
	private final ServerPlayerEntity owner;
	private boolean loading;

	public GadgetInventory(ServerPlayerEntity owner) {
		super(GadgetSlot.values().length);
		this.owner = owner;
		GadgetLoadout loadout = GadgetManager.get(owner);
		loading = true;
		for (GadgetSlot slot : GadgetSlot.values()) {
			setStack(slot.ordinal(), loadout.get(slot).copy());
		}
		loading = false;
	}

	public static boolean accepts(GadgetSlot slot, ItemStack stack) {
		return stack.getItem() instanceof Gadget gadget && gadget.gadgetSlot() == slot;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return slot >= 0 && slot < GadgetSlot.values().length && accepts(GadgetSlot.values()[slot], stack);
	}

	@Override
	public int getMaxCountPerStack() {
		return 1;
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return player == owner && owner.isAlive();
	}

	@Override
	public void markDirty() {
		super.markDirty();
		if (loading) {
			return;
		}
		GadgetManager.set(owner, new GadgetLoadout(getStack(GadgetSlot.BACK.ordinal()).copy(), getStack(GadgetSlot.TOOL.ordinal()).copy()));
		if (!GadgetManager.hasSwingshot(owner)) {
			GadgetManager.stopSwing(owner, false);
		}
	}
}
