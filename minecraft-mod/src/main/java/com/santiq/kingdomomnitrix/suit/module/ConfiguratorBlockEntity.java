package com.santiq.kingdomomnitrix.suit.module;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Anzug-Konfigurator: haelt ein Ruestungsteil, das ueber dem Tisch schwebt; das Menue baut Module ein und aus. */
public class ConfiguratorBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
	private ItemStack piece = ItemStack.EMPTY;

	public ConfiguratorBlockEntity(BlockPos pos, BlockState state) {
		super(SuitConfigurator.ENTITY, pos, state);
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return piece.isEmpty();
	}

	@Override
	public ItemStack getStack(int slot) {
		return piece;
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack out = piece.split(amount);
		markDirty();
		return out;
	}

	@Override
	public ItemStack removeStack(int slot) {
		ItemStack out = piece;
		piece = ItemStack.EMPTY;
		markDirty();
		return out;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		piece = stack;
		markDirty();
	}

	@Override
	public void markDirty() {
		super.markDirty();
		if (world != null && !world.isClient()) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player);
	}

	@Override
	public void clear() {
		piece = ItemStack.EMPTY;
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.kingdomomnitrix.suit_configurator");
	}

	@Nullable
	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
		return new ConfiguratorScreenHandler(syncId, inventory, this);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putBoolean("Filled", !piece.isEmpty());   // nie leer senden: leere Updates verwirft der Client
		if (!piece.isEmpty()) {
			nbt.put("Piece", piece.encode(registries));
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		piece = nbt.contains("Piece") ? ItemStack.fromNbt(registries, nbt.getCompound("Piece")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
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
