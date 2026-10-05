package com.santiq.kingdomomnitrix.arena;

import com.santiq.kingdomomnitrix.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/** Merkt sich, wo die Kampfflaeche liegt (Mitte und Radius). Selbst gebaut: Mitte = Terminal, Radius 12. */
public class ArenaTerminalBlockEntity extends BlockEntity {
	public static final int DEFAULT_RADIUS = 12;
	private BlockPos center;
	private int radius = DEFAULT_RADIUS;

	public ArenaTerminalBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ARENA_TERMINAL, pos, state);
		this.center = pos;
	}

	public BlockPos center() {
		return center;
	}

	public int radius() {
		return radius;
	}

	public void setArena(BlockPos center, int radius) {
		this.center = center.toImmutable();
		this.radius = MathHelper.clamp(radius, 4, 48);
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("CenterX", center.getX());
		nbt.putInt("CenterY", center.getY());
		nbt.putInt("CenterZ", center.getZ());
		nbt.putInt("Radius", radius);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		if (nbt.contains("CenterX")) {
			center = new BlockPos(nbt.getInt("CenterX"), nbt.getInt("CenterY"), nbt.getInt("CenterZ"));
		}
		radius = nbt.contains("Radius") ? MathHelper.clamp(nbt.getInt("Radius"), 4, 48) : DEFAULT_RADIUS;
	}
}
