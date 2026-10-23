package com.santiq.kingdomomnitrix.suit.fusion;

import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Fusionskern: haelt das Mittelstueck und die Energiekerne. Startet eine Fusion, wenn ein passendes Rezept vorliegt,
 * genug Injektoren mit der richtigen Platte in Reichweite stehen (bis {@value #RANGE} Bloecke, gleiche Hoehe bis
 * ±{@value #RANGE_Y}) und genug Energiekerne eingelegt sind. Waehrend der Fusion fliessen Strahlen von den Injektoren
 * in den Kern; wird ein Injektor geleert oder abgebaut, bricht sie ab.
 */
public class FusionCoreBlockEntity extends BlockEntity {
	public static final int RANGE = 8;
	public static final int RANGE_Y = 2;

	private ItemStack center = ItemStack.EMPTY;
	private ItemStack cores = ItemStack.EMPTY;
	private int progress;
	private int total;
	/** am Vorgang beteiligte Injektoren (fuer Strahlen auf dem Client) */
	private final List<BlockPos> active = new ArrayList<>();

	public FusionCoreBlockEntity(BlockPos pos, BlockState state) {
		super(SuitFusion.CORE_ENTITY, pos, state);
	}

	public ItemStack center() {
		return center;
	}

	public ItemStack cores() {
		return cores;
	}

	public float progress() {
		return total <= 0 ? 0.0f : progress / (float) total;
	}

	public boolean working() {
		return total > 0;
	}

	public List<BlockPos> active() {
		return active;
	}

	// --- Bedienung ----------------------------------------------------------------------------------------------------

	/** Rechtsklick mit Gegenstand: Energiekerne in den Kernspeicher, sonst als Mittelstueck einlegen. */
	public boolean insert(ItemStack held) {
		if (working() || held.isEmpty()) {
			return false;
		}
		if (Ben10Items.CORE_COLORS.containsKey(held.getItem())) {
			if (cores.isEmpty()) {
				cores = held.split(held.getCount());
			} else if (ItemStack.areItemsAndComponentsEqual(cores, held)) {
				int move = Math.min(held.getCount(), cores.getMaxCount() - cores.getCount());
				cores.increment(move);
				held.decrement(move);
			} else {
				return false;
			}
			sync();
			return true;
		}
		if (!center.isEmpty()) {
			return false;
		}
		center = held.split(1);
		sync();
		return true;
	}

	/** Rechtsklick mit leerer Hand: erst das Mittelstueck, dann die Energiekerne zurueckgeben. */
	public ItemStack take() {
		if (working()) {
			return ItemStack.EMPTY;
		}
		ItemStack out;
		if (!center.isEmpty()) {
			out = center;
			center = ItemStack.EMPTY;
		} else {
			out = cores;
			cores = ItemStack.EMPTY;
		}
		sync();
		return out;
	}

	/** Shift-Rechtsklick: Fusion pruefen und starten; fehlt etwas, sagt der Kern was. */
	public void start(PlayerEntity player) {
		if (working() || world == null) {
			return;
		}
		List<FusionInjectorBlockEntity> injectors = injectors();
		Optional<FusionRecipes.Recipe> found = FusionRecipes.forCenter(center,
				injectors.stream().map(FusionInjectorBlockEntity::stack).toList());
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.no_recipe").formatted(Formatting.RED), true);
			return;
		}
		FusionRecipes.Recipe recipe = found.get();
		List<FusionInjectorBlockEntity> usable = injectors.stream().filter(i -> i.stack().isOf(recipe.plate())).toList();
		if (usable.size() < recipe.plates()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.plates", usable.size(), recipe.plates(),
					recipe.plate().getName()).formatted(Formatting.RED), true);
			return;
		}
		if (cores.getCount() < recipe.cores()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.cores", cores.getCount(), recipe.cores())
					.formatted(Formatting.RED), true);
			return;
		}
		active.clear();
		usable.stream().limit(recipe.plates()).forEach(i -> active.add(i.getPos()));
		total = recipe.ticks();
		progress = 0;
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.5f, 0.6f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.start", recipe.result().getName()).formatted(Formatting.AQUA),
				true);
		sync();
	}

	private List<FusionInjectorBlockEntity> injectors() {
		List<FusionInjectorBlockEntity> found = new ArrayList<>();
		for (BlockPos at : BlockPos.iterate(pos.add(-RANGE, -RANGE_Y, -RANGE), pos.add(RANGE, RANGE_Y, RANGE))) {
			if (world.getBlockEntity(at) instanceof FusionInjectorBlockEntity injector && !injector.stack().isEmpty()) {
				found.add(injector);
			}
		}
		return found;
	}

	// --- Ablauf -------------------------------------------------------------------------------------------------------

	public static void tick(World world, BlockPos pos, BlockState state, FusionCoreBlockEntity core) {
		if (world.isClient() || !core.working()) {
			return;
		}
		ServerWorld server = (ServerWorld) world;
		Optional<FusionRecipes.Recipe> recipe = FusionRecipes.forCenter(core.center, List.of());
		List<FusionInjectorBlockEntity> injectors = new ArrayList<>();
		for (BlockPos at : core.active) {
			if (world.getBlockEntity(at) instanceof FusionInjectorBlockEntity injector && recipe.isPresent()
					&& injector.stack().isOf(recipe.get().plate())) {
				injectors.add(injector);
			}
		}
		if (recipe.isEmpty() || injectors.size() < core.active.size() || core.cores.getCount() < recipe.get().cores()) {
			core.abort(server);
			return;
		}
		core.progress++;
		float t = core.progress / (float) core.total;
		Vec3d target = Vec3d.ofCenter(pos).add(0, 1.2, 0);
		if (core.progress % 2 == 0) {
			for (FusionInjectorBlockEntity injector : injectors) {
				Vec3d from = Vec3d.ofCenter(injector.getPos()).add(0, 1.0, 0);
				Vec3d step = target.subtract(from);
				double along = (core.progress * 0.07) % 1.0;
				Vec3d point = from.add(step.multiply(along));
				server.spawnParticles(new DustParticleEffect(new Vector3f(0.4f, 1.0f, 0.8f), 1.2f), point.x, point.y, point.z, 2, 0.03, 0.03,
						0.03, 0);
			}
		}
		if (core.progress % 20 == 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 1.2f, 0.6f + t);
			server.spawnParticles(ParticleTypes.END_ROD, target.x, target.y, target.z, 6 + (int) (t * 20), 0.3, 0.3, 0.3, 0.05);
		}
		if (core.progress >= core.total) {
			core.finish(server, recipe.get(), injectors);
		}
	}

	private void finish(ServerWorld world, FusionRecipes.Recipe recipe, List<FusionInjectorBlockEntity> injectors) {
		for (FusionInjectorBlockEntity injector : injectors) {
			injector.setStack(ItemStack.EMPTY);
		}
		cores.decrement(recipe.cores());
		ItemStack result = new ItemStack(recipe.result());
		if (center.contains(DataComponentTypes.ENCHANTMENTS)) {
			result.set(DataComponentTypes.ENCHANTMENTS, center.get(DataComponentTypes.ENCHANTMENTS));
		}
		if (center.contains(DataComponentTypes.CUSTOM_NAME)) {
			result.set(DataComponentTypes.CUSTOM_NAME, center.get(DataComponentTypes.CUSTOM_NAME));
		}
		center = result;
		progress = 0;
		total = 0;
		active.clear();
		Vec3d c = Vec3d.ofCenter(pos).add(0, 1.2, 0);
		world.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 0.2, 0.2, 0.2, 0.4);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.8f, 1.4f);
		world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 1.0f, 1.0f);
		sync();
	}

	private void abort(ServerWorld world) {
		progress = 0;
		total = 0;
		active.clear();
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.2f, 0.8f);
		sync();
	}

	void sync() {
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	// --- Speichern ----------------------------------------------------------------------------------------------------

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (!center.isEmpty()) {
			nbt.put("Center", center.encode(registries));
		}
		if (!cores.isEmpty()) {
			nbt.put("Cores", cores.encode(registries));
		}
		nbt.putInt("Progress", progress);
		nbt.putInt("Total", total);
		NbtList list = new NbtList();
		active.forEach(p -> list.add(NbtLong.of(p.asLong())));
		nbt.put("Active", list);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		center = nbt.contains("Center") ? ItemStack.fromNbt(registries, nbt.getCompound("Center")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		cores = nbt.contains("Cores") ? ItemStack.fromNbt(registries, nbt.getCompound("Cores")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		progress = nbt.getInt("Progress");
		total = nbt.getInt("Total");
		active.clear();
		for (NbtElement e : nbt.getList("Active", NbtElement.LONG_TYPE)) {
			active.add(BlockPos.fromLong(((NbtLong) e).longValue()));
		}
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}

	/** Client: Fortschritt zwischen den Updates weiterzaehlen (fuer fluessige Strahlen). */
	public void clientTick() {
		if (total > 0 && progress < total) {
			progress++;
		}
	}
}
