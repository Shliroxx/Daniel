package com.santiq.kingdomomnitrix.omnitrix.workbench;

import com.santiq.kingdomomnitrix.registry.ModBlockEntities;
import com.santiq.kingdomomnitrix.world.content.Ben10Blocks;
import java.util.Arrays;
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
 * Omnitrix-Werkbank als Reaktor: drei Eingaben (Huelle, Konsole, Energiekern), eine Ausgabe, darueber das Reaktorfeld.
 * Das Feld braucht vier Stabilisatoren (je einer in jeder Himmelsrichtung, {@value #STABILIZER_HEIGHT} Bloecke hoeher,
 * {@value #STABILIZER_MIN}–{@value #STABILIZER_MAX} Bloecke entfernt). Liegt eine gueltige Belegung
 * ({@link WorkbenchRecipe#plan}) bei vollstaendigem Reaktor und freier Ausgabe, beginnt der Zusammenbau von selbst;
 * Fortschritt, Belegung und Stabilisatoren gehen an den Client, der daraus die Reaktor-Animation zeichnet. Wird
 * waehrenddessen etwas entnommen oder ein Stabilisator abgebaut, bricht der Vorgang ab.
 */
public class OmnitrixWorkbenchBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
	/** Hoehe der Reaktormitte und der Stabilisatoren ueber der Werkbank (Bloecke) */
	public static final int STABILIZER_HEIGHT = 3;
	public static final int STABILIZER_MIN = 3;
	public static final int STABILIZER_MAX = 7;
	/** Suchrichtungen der Stabilisatoren: Norden, Osten, Sueden, Westen */
	public static final Direction[] STABILIZER_SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(WorkbenchRecipe.SIZE, ItemStack.EMPTY);
	/** laufender Vorgang: verstrichene und gesamte Ticks (0 = keiner) */
	private int progress;
	private int total;
	/** Ticks seit dem letzten fertigen Omnitrix (fuer den Abschluss-Effekt auf dem Client) */
	private int sinceDone = Integer.MAX_VALUE / 2;
	/** Abstand des Stabilisators je {@link #STABILIZER_SIDES Richtung} (0 = keiner) */
	private int[] stabilizers = new int[4];

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> progress;
				case 1 -> total;
				default -> stabilizerCount();
			};
		}

		@Override
		public void set(int index, int value) {
			if (index == 0) {
				progress = value;
			} else if (index == 1) {
				total = value;
			}
		}

		@Override
		public int size() {
			return 3;
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
		if (world.getTime() % 20L == pos.asLong() % 20L) {
			bench.scanStabilizers(world, pos);
		}
		Optional<WorkbenchRecipe.Plan> plan = bench.items.get(WorkbenchRecipe.OUTPUT).isEmpty() && bench.stabilizerCount() == 4
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
		BlockPos center = pos.up(STABILIZER_HEIGHT);
		float t = bench.progress / (float) bench.total;
		if (bench.progress % 20 == 0) {
			world.playSound(null, center, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 1.0f, 0.6f + t);
			world.playSound(null, center, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.8f, 0.8f + t);
		}
		if (bench.progress == (int) (bench.total * 0.7f)) {
			// Kern taucht ins Zifferblatt
			world.playSound(null, center, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.2f, 1.2f);
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
		double cy = pos.getY() + STABILIZER_HEIGHT + 0.5;
		world.spawnParticles(ParticleTypes.FLASH, pos.getX() + 0.5, cy, pos.getZ() + 0.5, 2, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, cy, pos.getZ() + 0.5, 80, 0.2, 0.2, 0.2, 0.35);
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, cy, pos.getZ() + 0.5, 40, 1.2, 1.2, 1.2, 0.1);
		world.playSound(null, pos.up(STABILIZER_HEIGHT), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.6f, 1.5f);
		world.playSound(null, pos.up(STABILIZER_HEIGHT), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.4f, 1.2f);
		world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.5f, 1.6f);
		sync();
	}

	/** Stabilisatoren in den vier Richtungen suchen (naechster zaehlt); bei Aenderung an Clients senden. */
	private void scanStabilizers(World world, BlockPos pos) {
		int[] found = new int[4];
		for (int side = 0; side < 4; side++) {
			Direction dir = STABILIZER_SIDES[side];
			for (int d = STABILIZER_MIN; d <= STABILIZER_MAX; d++) {
				if (world.getBlockState(pos.up(STABILIZER_HEIGHT).offset(dir, d)).isOf(Ben10Blocks.OMNITRIX_STABILIZER)) {
					found[side] = d;
					break;
				}
			}
		}
		if (!Arrays.equals(found, stabilizers)) {
			stabilizers = found;
			sync();
		}
	}

	private void clientTick(World world, BlockPos pos) {
		sinceDone++;
		if (total > 0) {
			progress = Math.min(total, progress + 1);
			// Funken, die von der Feldoberflaeche in die Mitte gesogen werden
			float t = progress / (float) total;
			for (int i = 0; i < 1 + (int) (t * 3); i++) {
				double yaw = world.random.nextDouble() * Math.PI * 2;
				double pitch = (world.random.nextDouble() - 0.5) * Math.PI;
				double r = 1.6;
				double dx = Math.cos(yaw) * Math.cos(pitch) * r;
				double dy = Math.sin(pitch) * r;
				double dz = Math.sin(yaw) * Math.cos(pitch) * r;
				world.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + dx, pos.getY() + STABILIZER_HEIGHT + 0.5 + dy, pos.getZ() + 0.5 + dz,
						-dx * 0.08, -dy * 0.08, -dz * 0.08);
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

	/** Abstand des Stabilisators je {@link #STABILIZER_SIDES Richtung}, 0 = keiner */
	public int stabilizer(int side) {
		return stabilizers[side];
	}

	public int stabilizerCount() {
		int count = 0;
		for (int d : stabilizers) {
			if (d > 0) {
				count++;
			}
		}
		return count;
	}

	// --- Speichern und Synchronisieren --------------------------------------------------------------------------------

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, true, registries);
		nbt.putInt("Progress", progress);
		nbt.putInt("Total", total);
		nbt.putIntArray("Stabilizers", stabilizers);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		items.clear();
		Inventories.readNbt(nbt, items, registries);
		progress = nbt.getInt("Progress");
		int previous = total;
		total = nbt.getInt("Total");
		int[] stored = nbt.getIntArray("Stabilizers");
		stabilizers = stored.length == 4 ? stored : new int[4];
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
