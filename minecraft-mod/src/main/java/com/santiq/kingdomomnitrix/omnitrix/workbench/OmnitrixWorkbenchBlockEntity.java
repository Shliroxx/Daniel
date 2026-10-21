package com.santiq.kingdomomnitrix.omnitrix.workbench;

import com.santiq.kingdomomnitrix.registry.ModBlockEntities;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Omnitrix-Werkbank: drei Eingaben (Huelle, Konsole, Energiekern), eine Ausgabe. Liegt eine gueltige Belegung
 * ({@link WorkbenchRecipe#plan}) und ist die Ausgabe frei, beginnt der Zusammenbau von selbst; Fortschritt und Belegung
 * gehen an den Client, der daraus die Animation ueber der Werkbank zeichnet. Wird waehrenddessen etwas entnommen,
 * bricht der Vorgang ab.
 */
public class OmnitrixWorkbenchBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(WorkbenchRecipe.SIZE, ItemStack.EMPTY);
	/** laufender Vorgang: verstrichene und gesamte Ticks (0 = keiner) */
	private int progress;
	private int total;
	/** Ticks seit dem letzten fertigen Omnitrix (fuer den Abschluss-Effekt auf dem Client) */
	private int sinceDone = Integer.MAX_VALUE / 2;

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return index == 0 ? progress : total;
		}

		@Override
		public void set(int index, int value) {
			if (index == 0) {
				progress = value;
			} else {
				total = value;
			}
		}

		@Override
		public int size() {
			return 2;
		}
	};

	public OmnitrixWorkbenchBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.OMNITRIX_WORKBENCH, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, OmnitrixWorkbenchBlockEntity bench) {
		if (world.isClient()) {
			bench.clientTick(world, pos);
			return;
		}
		bench.sinceDone++;
		Optional<WorkbenchRecipe.Plan> plan = bench.items.get(WorkbenchRecipe.OUTPUT).isEmpty()
				? WorkbenchRecipe.plan(bench.items.get(WorkbenchRecipe.SHELL), bench.items.get(WorkbenchRecipe.CONSOLE),
				bench.items.get(WorkbenchRecipe.CORE))
				: Optional.empty();
		if (plan.isEmpty()) {
			if (bench.total != 0) {
				bench.progress = 0;
				bench.total = 0;
				bench.sync();
			}
			return;
		}
		if (bench.total == 0) {
			bench.total = plan.get().ticks();
			bench.progress = 0;
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.6f);
			bench.sync();
		}
		bench.progress++;
		if (bench.progress % 20 == 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.8f, 0.8f + bench.progress / (float) bench.total);
		}
		if (bench.progress >= bench.total) {
			bench.finish((ServerWorld) world, pos, plan.get());
		}
	}

	private void finish(ServerWorld world, BlockPos pos, WorkbenchRecipe.Plan plan) {
		items.get(WorkbenchRecipe.CORE).decrement(1);
		items.set(WorkbenchRecipe.SHELL, ItemStack.EMPTY);
		if (plan.kind() == WorkbenchRecipe.Kind.ASSEMBLE) {
			items.get(WorkbenchRecipe.CONSOLE).decrement(1);
		}
		items.set(WorkbenchRecipe.OUTPUT, plan.result());
		progress = 0;
		total = 0;
		sinceDone = 0;
		world.spawnParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 30, 0.4, 0.3, 0.4, 0.1);
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.2f, 1.2f);
		world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.5f, 1.6f);
		sync();
	}

	private void clientTick(World world, BlockPos pos) {
		sinceDone++;
		if (total > 0) {
			progress = Math.min(total, progress + 1);
			if (world.random.nextFloat() < 0.35f) {
				double a = world.random.nextDouble() * Math.PI * 2;
				world.addParticle(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5 + Math.cos(a) * 0.45, pos.getY() + 1.05,
						pos.getZ() + 0.5 + Math.sin(a) * 0.45, 0, 0.05, 0);
			}
		}
	}

	/** Zustand an Clients schicken (Animation) und speichern. */
	private void sync() {
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	// --- Anzeige (Client) -------------------------------------------------------------------------------------------

	public ItemStack stack(int slot) {
		return items.get(slot);
	}

	/** Fortschritt 0..1 des laufenden Vorgangs, -1 wenn keiner laeuft */
	public float progress(float tickDelta) {
		return total <= 0 ? -1.0f : Math.min(1.0f, (progress + tickDelta) / total);
	}

	public int sinceDone() {
		return sinceDone;
	}

	// --- Speichern und Synchronisieren --------------------------------------------------------------------------------

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, true, registries);
		nbt.putInt("Progress", progress);
		nbt.putInt("Total", total);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		items.clear();
		Inventories.readNbt(nbt, items, registries);
		progress = nbt.getInt("Progress");
		int previous = total;
		total = nbt.getInt("Total");
		if (world != null && world.isClient() && previous > 0 && total == 0 && !items.get(WorkbenchRecipe.OUTPUT).isEmpty()) {
			sinceDone = 0;
		}
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}

	// --- Inventar -----------------------------------------------------------------------------------------------------

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
		ItemStack taken = Inventories.splitStack(items, slot, amount);
		if (!taken.isEmpty()) {
			sync();
		}
		return taken;
	}

	@Override
	public ItemStack removeStack(int slot) {
		ItemStack taken = Inventories.removeStack(items, slot);
		sync();
		return taken;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		sync();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return world != null && world.getBlockEntity(pos) == this && player.squaredDistanceTo(pos.toCenterPos()) <= 64.0;
	}

	@Override
	public void clear() {
		items.clear();
		sync();
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return WorkbenchRecipe.accepts(slot, stack);
	}

	@Override
	public int[] getAvailableSlots(Direction side) {
		return side == Direction.DOWN ? new int[]{WorkbenchRecipe.OUTPUT}
				: new int[]{WorkbenchRecipe.SHELL, WorkbenchRecipe.CONSOLE, WorkbenchRecipe.CORE};
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction side) {
		return isValid(slot, stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction side) {
		return slot == WorkbenchRecipe.OUTPUT;
	}

	public DefaultedList<ItemStack> items() {
		return items;
	}

	// --- Menue --------------------------------------------------------------------------------------------------------

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.kingdomomnitrix.omnitrix_workbench");
	}

	@Nullable
	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new OmnitrixWorkbenchScreenHandler(syncId, playerInventory, this, properties);
	}
}
