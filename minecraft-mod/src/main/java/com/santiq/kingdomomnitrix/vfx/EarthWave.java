package com.santiq.kingdomomnitrix.vfx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Erdwelle: die echten Bodenbloecke (deren Aussehen) springen ringfoermig hoch und fallen zurueck — als Block-Display,
 * die Welt selbst bleibt unveraendert. Fuer Bodenschlaege (Vierarm, Humungousaur, Way Big ...).
 */
public final class EarthWave {
	private static final int MAX = 600;
	private static final List<Wave> WAVES = new ArrayList<>();
	private static final List<Piece> PIECES = new ArrayList<>();

	private static final class Wave {
		final ServerWorld world;
		final Vec3d center;
		final double radius;
		final int ticks;
		final float height;
		final Vec3d facing;
		final double cone;
		int age;
		final Set<BlockPos> done = new HashSet<>();

		Wave(ServerWorld world, Vec3d center, double radius, int ticks, float height, Vec3d facing, double cone) {
			this.world = world;
			this.center = center;
			this.radius = radius;
			this.ticks = ticks;
			this.height = height;
			this.facing = facing;
			this.cone = cone;
		}
	}

	private record Piece(DisplayEntity.BlockDisplayEntity entity, int[] age, float height, float tilt) {
	}

	private EarthWave() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(EarthWave::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PIECES.forEach(p -> p.entity().discard());
			PIECES.clear();
			WAVES.clear();
		});
	}

	/** Ring nach aussen (cone = 0) oder Kegel in Blickrichtung (cone = Mindest-Skalarprodukt, z. B. 0.6). */
	public static void ring(ServerWorld world, Vec3d center, double radius, int ticks, float height) {
		WAVES.add(new Wave(world, center, radius, Math.max(2, ticks), height, Vec3d.ZERO, 0.0));
	}

	public static void line(ServerWorld world, Vec3d center, Vec3d facing, double length, int ticks, float height, double cone) {
		WAVES.add(new Wave(world, center, length, Math.max(2, ticks), height, facing.multiply(1, 0, 1).normalize(), cone));
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave wave = it.next();
			wave.age++;
			double front = wave.radius * Math.min(1.0, wave.age / (double) wave.ticks);
			double inner = Math.max(0.0, front - 1.6);
			int r = (int) Math.ceil(front);
			BlockPos c = BlockPos.ofFloored(wave.center);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > front || d < inner || d < 1.2) {
						continue;
					}
					if (wave.cone > 0.0 && new Vec3d(dx, 0, dz).normalize().dotProduct(wave.facing) < wave.cone) {
						continue;
					}
					BlockPos column = c.add(dx, 0, dz);
					if (!wave.done.add(column)) {
						continue;
					}
					BlockPos ground = top(wave.world, column);
					if (ground != null && PIECES.size() < MAX) {
						float falloff = (float) (1.0 - 0.5 * d / wave.radius);
						spawn(wave.world, ground, wave.height * falloff * (0.75f + wave.world.random.nextFloat() * 0.5f));
					}
				}
			}
			if (wave.age >= wave.ticks) {
				it.remove();
			}
		}
		for (Iterator<Piece> it = PIECES.iterator(); it.hasNext(); ) {
			Piece piece = it.next();
			int age = ++piece.age()[0];
			if (age == 1) {
				animate(piece.entity(), piece.height(), piece.tilt(), 3);
			} else if (age == 5) {
				animate(piece.entity(), 0.0f, 0.0f, 6);
			} else if (age >= 12 || piece.entity().isRemoved()) {
				piece.entity().discard();
				it.remove();
			}
		}
	}

	/** Oberster fester Block nahe der Hoehe (bis 3 hoch/runter), dessen Oberseite frei ist. */
	private static BlockPos top(ServerWorld world, BlockPos around) {
		for (int dy = 2; dy >= -3; dy--) {
			BlockPos pos = around.up(dy);
			BlockState state = world.getBlockState(pos);
			if (!state.isAir() && state.isSideSolidFullSquare(world, pos, Direction.UP) && world.getBlockState(pos.up()).isAir()) {
				return pos;
			}
		}
		return null;
	}

	private static void spawn(ServerWorld world, BlockPos ground, float height) {
		DisplayEntity.BlockDisplayEntity display = EntityType.BLOCK_DISPLAY.create(world);
		if (display == null) {
			return;
		}
		// Block-Display sitzt mit der Ecke am Ursprung: leicht ueber dem Original, damit kein Flackern entsteht
		display.refreshPositionAndAngles(ground.getX() - 0.001, ground.getY() + 0.002, ground.getZ() - 0.001, 0.0f, 0.0f);
		NbtCompound nbt = display.writeNbt(new NbtCompound());
		nbt.put("block_state", NbtHelper.fromBlockState(world.getBlockState(ground)));
		nbt.put("transformation", transformation(0.0f, 0.0f));
		display.readNbt(nbt);
		world.spawnEntity(display);
		float tilt = (world.random.nextFloat() - 0.5f) * 0.5f;
		PIECES.add(new Piece(display, new int[] {0}, height, tilt));
	}

	private static void animate(DisplayEntity.BlockDisplayEntity display, float height, float tilt, int duration) {
		NbtCompound nbt = display.writeNbt(new NbtCompound());
		nbt.put("transformation", transformation(height, tilt));
		nbt.putInt("start_interpolation", 0);
		nbt.putInt("interpolation_duration", duration);
		display.readNbt(nbt);
	}

	/** Verschiebung nach oben und Kippung um die Blockmitte (Quaternion um x). */
	private static NbtCompound transformation(float height, float tilt) {
		NbtCompound t = new NbtCompound();
		float s = MathHelper.sin(tilt * 0.5f);
		float c = MathHelper.cos(tilt * 0.5f);
		t.put("translation", floats(0.0f, height, 0.0f));
		t.put("left_rotation", floats(s, 0.0f, 0.0f, c));
		t.put("scale", floats(1.0f, 1.0f, 1.0f));
		t.put("right_rotation", floats(0.0f, 0.0f, 0.0f, 1.0f));
		return t;
	}

	private static NbtList floats(float... values) {
		NbtList list = new NbtList();
		for (float v : values) {
			list.add(NbtFloat.of(v));
		}
		return list;
	}
}
