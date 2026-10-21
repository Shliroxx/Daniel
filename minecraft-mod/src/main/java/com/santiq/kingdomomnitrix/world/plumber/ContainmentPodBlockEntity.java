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
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Merkt sich, welche Spieler ihr Teil aus dieser Kapsel schon genommen haben, und wann sie zuletzt geoeffnet wurde
 * (an Clients gesendet: der Renderer klappt den Deckel auf und laesst das Teil herausschweben).
 */
public class ContainmentPodBlockEntity extends BlockEntity {
	/** so lange bleibt der Deckel nach dem Oeffnen offen (Ticks) */
	public static final int OPEN_TICKS = 200;
	private final Set<UUID> claimed = new HashSet<>();
	private long openedAt = Long.MIN_VALUE / 2;

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

	/** Deckel aufklappen (jede Benutzung), an Clients senden. */
	public void open() {
		if (world == null) {
			return;
		}
		openedAt = world.getTime();
		markDirty();
		world.updateListeners(pos, getCachedState(), getCachedState(), 3);
	}

	/** Weltzeit des letzten Oeffnens (fuer den Renderer). */
	public long openedAt() {
		return openedAt;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		NbtList list = new NbtList();
		for (UUID id : claimed) {
			list.add(NbtHelper.fromUuid(id));
		}
		nbt.put("Claimed", list);
		nbt.putLong("OpenedAt", openedAt);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		claimed.clear();
		for (NbtElement element : nbt.getList("Claimed", NbtElement.INT_ARRAY_TYPE)) {
			claimed.add(NbtHelper.toUuid(element));
		}
		openedAt = nbt.contains("OpenedAt") ? nbt.getLong("OpenedAt") : Long.MIN_VALUE / 2;
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	/** An Clients nur der Oeffnungszeitpunkt, nicht die Spielerliste. */
	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		NbtCompound nbt = new NbtCompound();
		nbt.putLong("OpenedAt", openedAt);
		return nbt;
	}
}
