package com.santiq.kingdomomnitrix.suit.forge;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** Menue einer Ruestungs-Maschine: Eingaben links, Brennstoff unten links, Ausgabe rechts, darunter das Inventar. */
public class MachineScreenHandler extends ScreenHandler {
	public static final int INPUT_X = 38;
	public static final int INPUT_Y = 22;
	public static final int FUEL_X = 56;
	public static final int FUEL_Y = 58;
	public static final int OUTPUT_X = 124;
	public static final int OUTPUT_Y = 34;

	private final MachineKind kind;
	private final Inventory machine;
	private final PropertyDelegate properties;

	/** Client-Konstruktor. */
	public MachineScreenHandler(MachineKind kind, int syncId, PlayerInventory inventory) {
		this(kind, syncId, inventory, new SimpleInventory(kind.size()), new ArrayPropertyDelegate(4));
	}

	public MachineScreenHandler(MachineKind kind, int syncId, PlayerInventory inventory, Inventory machine, PropertyDelegate properties) {
		super(SuitForge.screenType(kind), syncId);
		checkSize(machine, kind.size());
		this.kind = kind;
		this.machine = machine;
		this.properties = properties;
		machine.onOpen(inventory.player);
		if (kind.inputs() == 1) {
			addSlot(new Slot(machine, 0, INPUT_X + 18, INPUT_Y + 12));
		} else {
			for (int i = 0; i < kind.inputs(); i++) {
				addSlot(new Slot(machine, i, INPUT_X + i * 18, INPUT_Y));
			}
		}
		addSlot(new Slot(machine, kind.fuelSlot(), FUEL_X, FUEL_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return MachineRecipes.isFuel(stack);
			}
		});
		addSlot(new Slot(machine, kind.outputSlot(), OUTPUT_X, OUTPUT_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return false;
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(inventory, column, 8 + column * 18, 142));
		}
		addProperties(properties);
	}

	public MachineKind kind() {
		return kind;
	}

	/** Fortschritt 0..1 */
	public float progress() {
		int total = properties.get(1);
		return total <= 0 ? 0.0f : Math.min(1.0f, properties.get(0) / (float) total);
	}

	/** verbleibender Brennstoff 0..1 */
	public float fuel() {
		int max = properties.get(3);
		return max <= 0 ? 0.0f : properties.get(2) / (float) max;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		int size = kind.size();
		if (index < size) {
			if (!insertItem(stack, size, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickTransfer(stack, original);
		} else if (MachineRecipes.isFuel(stack) && insertItem(stack, kind.fuelSlot(), kind.fuelSlot() + 1, false)) {
			// in den Brennstoff-Slot
		} else if (!insertItem(stack, 0, kind.inputs(), false)) {
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
		return machine.canPlayerUse(player);
	}
}
