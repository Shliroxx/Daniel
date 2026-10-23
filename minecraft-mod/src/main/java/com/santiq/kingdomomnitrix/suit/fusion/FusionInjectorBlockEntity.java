package com.santiq.kingdomomnitrix.suit.fusion;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** Injektor: haelt genau einen Gegenstand (eine Panzerplatte), der ueber ihm schwebt; Stufe und Schaden stehen im Block. */
public class FusionInjectorBlockEntity extends BlockEntity {
	private ItemStack stack = ItemStack.EMPTY;

	public FusionInjectorBlockEntity(BlockPos pos, BlockState state) {
		super(SuitFusion.INJECTOR_ENTITY, pos, state);
	}

	public ItemStack stack() {
		return stack;
	}

	/** Stufe des Injektor-Blocks (1 Grund, 2 Galvan, 3 Sternen). */
	public int tier() {
		return SuitFusion.injectorTier(getCachedState().getBlock());
	}

	public boolean damaged() {
		BlockState state = getCachedState();
		return state.contains(FusionBlocks.Injector.DAMAGED) && state.get(FusionBlocks.Injector.DAMAGED);
	}

	public void setDamaged(boolean damaged) {
		if (world != null && getCachedState().contains(FusionBlocks.Injector.DAMAGED)) {
			world.setBlockState(pos, getCachedState().with(FusionBlocks.Injector.DAMAGED, damaged));
		}
	}

	public void setStack(ItemStack stack) {
		this.stack = stack;
		sync();
	}

	void sync() {
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		// immer etwas schreiben: leere Update-Daten verwirft der Client, dann bliebe die Platte sichtbar
		nbt.putBoolean("Filled", !stack.isEmpty());
		if (!stack.isEmpty()) {
			nbt.put("Item", stack.encode(registries));
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		stack = nbt.contains("Item") ? ItemStack.fromNbt(registries, nbt.getCompound("Item")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}
}
