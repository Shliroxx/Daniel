package com.santiq.kingdomomnitrix.world.plumber;

import com.santiq.kingdomomnitrix.registry.ModBlockEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** Merkt sich, welche Spieler ihr Teil aus dieser Kapsel schon genommen haben. */
public class ContainmentPodBlockEntity extends BlockEntity {
	private final Set<UUID> claimed = new HashSet<>();

	public ContainmentPodBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CONTAINMENT_POD, pos, state);
	}

	/** true, wenn der Spieler sein Teil jetzt bekommt (erstes Oeffnen). */
	public boolean claim(UUID player) {
		if (!claimed.add(player)) {
			return false;
		}
		markDirty();
		return true;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		NbtList list = new NbtList();
		for (UUID id : claimed) {
			list.add(NbtHelper.fromUuid(id));
		}
		nbt.put("Claimed", list);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		claimed.clear();
		for (NbtElement element : nbt.getList("Claimed", NbtElement.INT_ARRAY_TYPE)) {
			claimed.add(NbtHelper.toUuid(element));
		}
	}
}
