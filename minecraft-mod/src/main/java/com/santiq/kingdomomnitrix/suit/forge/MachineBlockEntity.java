package com.santiq.kingdomomnitrix.suit.forge;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Gemeinsame Logik der drei Ruestungs-Maschinen: Brennstoff verbrennen, passendes Rezept abarbeiten, Ergebnis in die
 * Ausgabe legen. Trichter: oben Eingaben, seitlich Brennstoff, unten Ausgabe.
 */
public class MachineBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
	private final MachineKind kind;
	private final DefaultedList<ItemStack> items;
	private int progress;
	private int total;
	private int burn;
	private int burnMax;

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> progress;
				case 1 -> total;
				case 2 -> burn;
				default -> burnMax;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> progress = value;
				case 1 -> total = value;
				case 2 -> burn = value;
				default -> burnMax = value;
			}
		}

		@Override
		public int size() {
			return 4;
		}
	};

	public MachineBlockEntity(BlockPos pos, BlockState state) {
		this(((MachineBlock) state.getBlock()).kind(), pos, state);
	}

	private MachineBlockEntity(MachineKind kind, BlockPos pos, BlockState state) {
		super(SuitForge.MACHINE_ENTITY, pos, state);
		this.kind = kind;
		this.items = DefaultedList.ofSize(kind.size(), ItemStack.EMPTY);
	}

	public MachineKind kind() {
		return kind;
	}

	public static void tick(World world, BlockPos pos, BlockState state, MachineBlockEntity machine) {
		if (world.isClient()) {
			return;
		}
		boolean wasBurning = machine.burn > 0;
		boolean dirty = false;
		if (machine.burn > 0) {
			machine.burn--;
		}
		MachineRecipes.Recipe recipe = MachineRecipes.find(machine.kind, machine.items).orElse(null);
		boolean canWork = recipe != null && machine.fits(recipe);
		if (canWork && machine.burn == 0) {
			ItemStack fuel = machine.items.get(machine.kind.fuelSlot());
			int time = MachineRecipes.burnTime(fuel);
			if (time > 0) {
				machine.burn = time;
				machine.burnMax = time;
				ItemStack remainder = fuel.getItem().getRecipeRemainder() == null ? ItemStack.EMPTY
						: new ItemStack(fuel.getItem().getRecipeRemainder());
				fuel.decrement(1);
				if (fuel.isEmpty() && !remainder.isEmpty()) {
					machine.items.set(machine.kind.fuelSlot(), remainder);
				}
				dirty = true;
			}
		}
		if (canWork && machine.burn > 0) {
			if (machine.total != recipe.ticks()) {
				machine.total = recipe.ticks();
			}
			machine.progress++;
			if (machine.progress >= machine.total) {
				machine.craft(recipe);
				machine.progress = 0;
				world.playSound(null, pos, machine.kind == MachineKind.FORGE ? SoundEvents.BLOCK_ANVIL_USE : SoundEvents.BLOCK_BEACON_POWER_SELECT,
						SoundCategory.BLOCKS, 0.6f, machine.kind == MachineKind.CRUSHER ? 0.7f : 1.3f);
			}
			dirty = true;
		} else if (machine.progress > 0) {
			machine.progress = 0;
			machine.total = 0;
			dirty = true;
		}
		boolean burning = machine.burn > 0;
		if (burning != wasBurning) {
			world.setBlockState(pos, state.with(MachineBlock.LIT, burning), 3);
		}
		if (dirty) {
			machine.markDirty();
		}
	}

	private boolean fits(MachineRecipes.Recipe recipe) {
		ItemStack out = items.get(kind.outputSlot());
		if (out.isEmpty()) {
			return true;
		}
		return out.isOf(recipe.result()) && out.getCount() + recipe.resultCount() <= out.getMaxCount();
	}

	private void craft(MachineRecipes.Recipe recipe) {
		int[] slots = MachineRecipes.assign(recipe, items, kind.inputs());
		if (slots == null) {
			return;
		}
		for (int p = 0; p < slots.length; p++) {
			items.get(slots[p]).decrement(recipe.inputs().get(p).count());
		}
		ItemStack out = items.get(kind.outputSlot());
		if (out.isEmpty()) {
			items.set(kind.outputSlot(), recipe.output());
		} else {
			out.increment(recipe.resultCount());
		}
	}

	// --- Speichern ----------------------------------------------------------------------------------------------------

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, registries);
		nbt.putInt("Progress", progress);
		nbt.putInt("Total", total);
		nbt.putInt("Burn", burn);
		nbt.putInt("BurnMax", burnMax);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		Inventories.readNbt(nbt, items, registries);
		progress = nbt.getInt("Progress");
		total = nbt.getInt("Total");
		burn = nbt.getInt("Burn");
		burnMax = nbt.getInt("BurnMax");
	}

	// --- Menue --------------------------------------------------------------------------------------------------------

	@Override
	public Text getDisplayName() {
		return getCachedState().getBlock().getName();
	}

	@Nullable
	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
		return new MachineScreenHandler(kind, syncId, inventory, this, properties);
	}

	// --- Inventar -----------------------------------------------------------------------------------------------------

	@Override
	public int[] getAvailableSlots(Direction side) {
		if (side == Direction.DOWN) {
			return new int[] {kind.outputSlot()};
		}
		if (side == Direction.UP) {
			int[] inputs = new int[kind.inputs()];
			for (int i = 0; i < inputs.length; i++) {
				inputs[i] = i;
			}
			return inputs;
		}
		return new int[] {kind.fuelSlot()};
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		return isValid(slot, stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return slot == kind.outputSlot();
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		if (slot == kind.outputSlot()) {
			return false;
		}
		if (slot == kind.fuelSlot()) {
			return MachineRecipes.isFuel(stack);
		}
		return true;
	}

	@Override
	public int size() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack result = Inventories.splitStack(items, slot, amount);
		if (!result.isEmpty()) {
			markDirty();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(items, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.capCount(getMaxCountPerStack());
		markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player);
	}

	@Override
	public void clear() {
		items.clear();
	}
}
