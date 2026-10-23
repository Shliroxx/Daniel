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

/** Injektor: haelt genau einen Gegenstand (eine Panzerplatte), der ueber ihm schwebt. */
public class FusionInjectorBlockEntity extends BlockEntity {
	private ItemStack stack = ItemStack.EMPTY;

	public FusionInjectorBlockEntity(BlockPos pos, BlockState state) {
		super(SuitFusion.INJECTOR_ENTITY, pos, state);
	}

	public ItemStack stack() {
		return stack;
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
