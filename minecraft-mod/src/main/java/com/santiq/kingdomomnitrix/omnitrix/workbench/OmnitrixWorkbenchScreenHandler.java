package com.santiq.kingdomomnitrix.omnitrix.workbench;

import com.santiq.kingdomomnitrix.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** Menue der Omnitrix-Werkbank: Huelle, Konsole, Energiekern, Ausgabe; darunter das Spielerinventar. */
public class OmnitrixWorkbenchScreenHandler extends ScreenHandler {
	public static final int SHELL_X = 30;
	public static final int CONSOLE_X = 66;
	public static final int CORE_X = 48;
	public static final int INPUT_Y = 28;
	public static final int CORE_Y = 50;
	public static final int OUTPUT_X = 140;
	public static final int OUTPUT_Y = 39;

	private final Inventory bench;
	private final PropertyDelegate properties;

	/** Client-Konstruktor: Inhalt und Fortschritt kommen ueber die normale Synchronisierung. */
	public OmnitrixWorkbenchScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(WorkbenchRecipe.SIZE), new ArrayPropertyDelegate(2));
	}

	public OmnitrixWorkbenchScreenHandler(int syncId, PlayerInventory playerInventory, Inventory bench, PropertyDelegate properties) {
		super(ModScreenHandlers.OMNITRIX_WORKBENCH, syncId);
		checkSize(bench, WorkbenchRecipe.SIZE);
		this.bench = bench;
		this.properties = properties;
		bench.onOpen(playerInventory.player);
		addSlot(new InputSlot(bench, WorkbenchRecipe.SHELL, SHELL_X, INPUT_Y));
		addSlot(new InputSlot(bench, WorkbenchRecipe.CONSOLE, CONSOLE_X, INPUT_Y));
		addSlot(new InputSlot(bench, WorkbenchRecipe.CORE, CORE_X, CORE_Y));
		addSlot(new OutputSlot(bench, WorkbenchRecipe.OUTPUT, OUTPUT_X, OUTPUT_Y));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
		}
		addProperties(properties);
	}

	/** Fortschritt 0..1, 0 wenn nichts laeuft */
	public float progress() {
		int total = properties.get(1);
		return total <= 0 ? 0.0f : properties.get(0) / (float) total;
	}

	public boolean working() {
		return properties.get(1) > 0;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		if (index < WorkbenchRecipe.SIZE) {
			if (!insertItem(stack, WorkbenchRecipe.SIZE, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
			if (index == WorkbenchRecipe.OUTPUT) {
				slot.onTakeItem(player, original);
			}
		} else {
			boolean moved = false;
			for (int i = 0; i < WorkbenchRecipe.OUTPUT && !moved; i++) {
				Slot target = slots.get(i);
				if (target.canInsert(stack)) {
					moved = insertItem(stack, i, i + 1, false);
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
		return bench.canPlayerUse(player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		bench.onClose(player);
	}

	/** Eingabe: nimmt nur, was die Werkbank dort annimmt (Huelle/Omnitrix, Konsole, Kern). */
	public static class InputSlot extends Slot {
		public InputSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return WorkbenchRecipe.accepts(getIndex(), stack);
		}

		@Override
		public int getMaxItemCount() {
			return getIndex() == WorkbenchRecipe.CORE ? 16 : 1;
		}
	}

	/** Ausgabe: nur entnehmen; Entnahme zaehlt als Herstellen (Quest-Ziele, Statistik). */
	public static class OutputSlot extends Slot {
		public OutputSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return false;
		}

		@Override
		public void onTakeItem(PlayerEntity player, ItemStack stack) {
			stack.onCraftByPlayer(player.getWorld(), player, stack.getCount());
			super.onTakeItem(player, stack);
		}
	}
}
