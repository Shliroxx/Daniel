package com.santiq.kingdomomnitrix.suit.module;

import com.santiq.kingdomomnitrix.suit.SuitArmorItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * Menue des Anzug-Konfigurators: links das Ruestungsteil, rechts drei Modul-Sockel (aktiv = Stufe des Teils), unten
 * das Inventar. Die Sockel schreiben direkt in das Teil.
 */
public class ConfiguratorScreenHandler extends ScreenHandler {
	public static final int PIECE_X = 20;
	public static final int PIECE_Y = 52;
	public static final int SOCKET_X = 170;
	public static final int[] SOCKET_Y = {22, 52, 82};
	public static final int INVENTORY_X = 26;
	public static final int INVENTORY_Y = 136;

	private final Inventory piece;
	private final ModuleInventory modules;

	/** Client-Konstruktor. */
	public ConfiguratorScreenHandler(int syncId, PlayerInventory inventory) {
		this(syncId, inventory, new SimpleInventory(1));
	}

	public ConfiguratorScreenHandler(int syncId, PlayerInventory inventory, Inventory piece) {
		super(SuitConfigurator.SCREEN, syncId);
		checkSize(piece, 1);
		this.piece = piece;
		this.modules = new ModuleInventory(piece);
		piece.onOpen(inventory.player);
		addSlot(new Slot(piece, 0, PIECE_X, PIECE_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof SuitArmorItem;
			}

			@Override
			public int getMaxItemCount() {
				return 1;
			}
		});
		for (int i = 0; i < ModuleInventory.SIZE; i++) {
			int index = i;
			addSlot(new Slot(modules, i, SOCKET_X, SOCKET_Y[i]) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return modules.isValid(index, stack) && !hasStack();
				}

				@Override
				public int getMaxItemCount() {
					return 1;
				}

				@Override
				public boolean isEnabled() {
					return index < SuitModules.slots(piece.getStack(0));
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
		}
	}

	public ItemStack piece() {
		return piece.getStack(0);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		int own = 1 + ModuleInventory.SIZE;
		if (index < own) {
			if (!insertItem(stack, own, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.getItem() instanceof SuitArmorItem) {
			if (!insertItem(stack, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.getItem() instanceof ModuleItem) {
			boolean moved = false;
			for (int i = 1; i < own && !stack.isEmpty(); i++) {
				Slot socket = slots.get(i);
				if (socket.isEnabled() && socket.canInsert(stack)) {
					socket.setStack(stack.split(1));
					moved = true;
					break;
				}
			}
			if (!moved) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTakeItem(player, stack);
		return original;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return piece.canPlayerUse(player);
	}
}
