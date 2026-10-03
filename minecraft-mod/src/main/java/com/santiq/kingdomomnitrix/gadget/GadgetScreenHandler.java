package com.santiq.kingdomomnitrix.gadget;

import com.santiq.kingdomomnitrix.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** Menue des Gadget-Guertels: zwei Gadget-Plaetze ueber dem normalen Spielerinventar. */
public class GadgetScreenHandler extends ScreenHandler {
	public static final int BACK_X = 53;
	public static final int TOOL_X = 107;
	public static final int GADGET_Y = 30;
	private static final int GADGET_SLOTS = GadgetSlot.values().length;

	private final Inventory gadgets;

	/** Client-Konstruktor: Inhalt kommt ueber die normale Slot-Synchronisierung. */
	public GadgetScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(GADGET_SLOTS));
	}

	public GadgetScreenHandler(int syncId, PlayerInventory playerInventory, Inventory gadgets) {
		super(ModScreenHandlers.GADGET_BELT, syncId);
		checkSize(gadgets, GADGET_SLOTS);
		this.gadgets = gadgets;
		gadgets.onOpen(playerInventory.player);
		addSlot(new GadgetSlotView(gadgets, GadgetSlot.BACK, BACK_X, GADGET_Y));
		addSlot(new GadgetSlotView(gadgets, GadgetSlot.TOOL, TOOL_X, GADGET_Y));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		if (index < GADGET_SLOTS) {
			if (!insertItem(stack, GADGET_SLOTS, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean moved = false;
			for (int i = 0; i < GADGET_SLOTS && !moved; i++) {
				Slot target = slots.get(i);
				if (!target.hasStack() && target.canInsert(stack)) {
					target.setStack(stack.split(1));
					moved = true;
				}
			}
			if (!moved) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return original;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return gadgets.canPlayerUse(player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		gadgets.onClose(player);
	}

	/** Gadget-Platz: nimmt nur passende Gadgets an, jeweils eines. */
	public static class GadgetSlotView extends Slot {
		private final GadgetSlot type;

		public GadgetSlotView(Inventory inventory, GadgetSlot type, int x, int y) {
			super(inventory, type.ordinal(), x, y);
			this.type = type;
		}

		public GadgetSlot type() {
			return type;
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return GadgetInventory.accepts(type, stack);
		}

		@Override
		public int getMaxItemCount() {
			return 1;
		}
	}
}
