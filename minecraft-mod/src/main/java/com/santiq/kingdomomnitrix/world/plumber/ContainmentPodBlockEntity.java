package com.santiq.kingdomomnitrix.world.plumber;

import com.santiq.kingdomomnitrix.registry.ModBlockEntities;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Containment-Kapsel: merkt sich, wer sein Teil schon hat, und spielt die Oeffnung als Ablauf (Zeit ab {@link #openedAt}):
 *
 * <ol>
 *   <li>0–{@value #BURST}: die Kapsel bebt, die Naehte gluehen, Grollen.</li>
 *   <li>{@value #BURST}: die vier Kuppelsegmente sprengen auf — Knall, Dampf, Funken.</li>
 *   <li>bis {@value #RISE_END}: das Teil steigt im gruenen Strahl auf.</li>
 *   <li>{@value #FLY_START}–{@value #FLY_END}: holt es sich jemand zum ersten Mal, fliegt es ihm an den Arm; erst dann
 *       liegt es im Inventar.</li>
 *   <li>nach {@value #OPEN_TICKS}: die Segmente schliessen sich wieder.</li>
 * </ol>
 * Waehrend des Ablaufs reagiert die Kapsel nicht auf weitere Klicks.
 */
public class ContainmentPodBlockEntity extends BlockEntity {
	public static final int BURST = 12;
	public static final int RISE_END = 42;
	public static final int FLY_START = 46;
	public static final int FLY_END = 58;
	/** so lange bleibt die Kapsel nach dem Oeffnen offen (Ticks) */
	public static final int OPEN_TICKS = 220;
	private final Set<UUID> claimed = new HashSet<>();
	private long openedAt = Long.MIN_VALUE / 2;
	/** wer die Kapsel zuletzt geoeffnet hat (Ziel des Fluges) */
	@Nullable
	private UUID opener;
	/** bekommt der Oeffner sein Teil in diesem Ablauf (erstes Mal)? */
	private boolean delivering;

	public ContainmentPodBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CONTAINMENT_POD, pos, state);
	}

	/** Laeuft gerade ein Oeffnungsablauf (inkl. Offenstehen)? */
	public boolean busy() {
		return world != null && world.getTime() - openedAt < OPEN_TICKS + 20;
	}

	/** Ablauf starten; liefert true, wenn der Spieler sein Teil bekommen wird (erstes Oeffnen). */
	public boolean open(PlayerEntity player) {
		if (world == null) {
			return false;
		}
		openedAt = world.getTime();
		opener = player.getUuid();
		delivering = claimed.add(player.getUuid());
		markDirty();
		world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.2f, 0.5f);
		world.playSound(null, pos, ModSounds.OMNITRIX_MALFUNCTION, SoundCategory.BLOCKS, 0.8f, 0.6f);
		return delivering;
	}

	public static void tick(World world, BlockPos pos, BlockState state, ContainmentPodBlockEntity pod) {
		if (!(world instanceof ServerWorld server)) {
			return;
		}
		long t = world.getTime() - pod.openedAt;
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.6;
		double z = pos.getZ() + 0.5;
		if (t == BURST) {
			world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.7f, 1.6f);
			world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.4f, 0.6f);
			world.playSound(null, pos, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 1.0f, 0.5f);
			server.spawnParticles(ParticleTypes.CLOUD, x, y, z, 40, 0.5, 0.2, 0.5, 0.12);
			server.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, 0.4, 0.3, 0.4, 0.3);
			server.spawnParticles(ParticleTypes.FLASH, x, y + 0.3, z, 1, 0, 0, 0, 0);
		} else if (t == BURST + 6) {
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.4f);
		} else if (t == FLY_START && pod.delivering) {
			world.playSound(null, pos, ModSounds.OMNITRIX_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
		} else if (t >= FLY_END && pod.delivering) {
			// ">=": auch nach einem Serverneustart mitten im Ablauf wird noch uebergeben
			pod.deliver(server);
		}
	}

	/** Teil an den Oeffner uebergeben (Ende des Fluges); ist er weg, liegt es fuer ihn bereit (naechster Klick). */
	private void deliver(ServerWorld server) {
		delivering = false;
		markDirty();
		PlayerEntity player = opener == null ? null : server.getPlayerByUuid(opener);
		if (player == null || !(getCachedState().getBlock() instanceof ContainmentPodBlock block)) {
			if (opener != null) {
				claimed.remove(opener);
			}
			return;
		}
		player.getInventory().offerOrDrop(new ItemStack(block.part().item()));
		player.sendMessage(Text.translatable(block.part() == ContainmentPodBlock.Part.SHELL ? "message.kingdomomnitrix.pod.shell"
				: "message.kingdomomnitrix.pod.console").formatted(Formatting.GREEN), false);
		server.playSound(null, player.getBlockPos(), ModSounds.OMNITRIX_UNLOCK, SoundCategory.PLAYERS, 1.0f, 1.0f);
		server.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.5, 0.4, 0.05);
	}

	/** Weltzeit des letzten Oeffnens (fuer den Renderer). */
	public long openedAt() {
		return openedAt;
	}

	/** Ziel des Fluges, falls in diesem Ablauf jemand sein Teil bekommt; sonst null. */
	@Nullable
	public UUID flightTarget() {
		return delivering ? opener : null;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		NbtList list = new NbtList();
		for (UUID id : claimed) {
			list.add(NbtHelper.fromUuid(id));
		}
		nbt.put("Claimed", list);
		writeShared(nbt);
	}

	private void writeShared(NbtCompound nbt) {
		nbt.putLong("OpenedAt", openedAt);
		nbt.putBoolean("Delivering", delivering);
		if (opener != null) {
			nbt.putUuid("Opener", opener);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		if (nbt.contains("Claimed")) {
			claimed.clear();
			for (NbtElement element : nbt.getList("Claimed", NbtElement.INT_ARRAY_TYPE)) {
				claimed.add(NbtHelper.toUuid(element));
			}
		}
		openedAt = nbt.contains("OpenedAt") ? nbt.getLong("OpenedAt") : Long.MIN_VALUE / 2;
		delivering = nbt.getBoolean("Delivering");
		opener = nbt.containsUuid("Opener") ? nbt.getUuid("Opener") : null;
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	/** An Clients nur der Ablauf (Zeitpunkt, Ziel des Fluges), nicht die Spielerliste. */
	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		NbtCompound nbt = new NbtCompound();
		writeShared(nbt);
		return nbt;
	}
}
