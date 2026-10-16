package com.santiq.kingdomomnitrix.vfx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Riesenkristalle: schlanke, schraeg stehende Kristall-Saeulen aus echter Blockoptik (Block-Display, gestreckt und
 * gekippt) — wachsen in wenigen Ticks aus dem Boden, stehen und zerspringen dann. Wie Geoden-Kristalle, nicht wie
 * Wuerfel. Rein optisch; Kollision und Schaden kommen von den {@code TempBlocks}-Spitzen am Fuss.
 */
public final class CrystalGrowth {
	private static final int MAX = 500;
	private static final int GROW = 4;
	private static final List<Shard> SHARDS = new ArrayList<>();

	private record Shard(ServerWorld world, DisplayEntity.BlockDisplayEntity display, Vec3d base, Quaternionf tilt, float width, float height,
			int life, int color, int[] age) {
	}

	private CrystalGrowth() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CrystalGrowth::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SHARDS.forEach(s -> s.display().discard());
			SHARDS.clear();
		});
	}

	/**
	 * Buendel aus {@code count} Kristallen um {@code base} (Bodenmitte): der mittlere steht fast senkrecht und ist am
	 * hoechsten, die aeusseren kippen nach aussen. {@code lean} = Grundneigung (0 = senkrecht, 0,6 = stark schraeg).
	 */
	public static void cluster(ServerWorld world, Vec3d base, BlockState crystal, int count, float height, float lean, int life, int color) {
		float yaw0 = world.random.nextFloat() * MathHelper.TAU;
		for (int i = 0; i < count; i++) {
			boolean center = i == 0;
			float yaw = yaw0 + i * MathHelper.TAU / Math.max(1, count - 1) + (world.random.nextFloat() - 0.5f) * 0.6f;
			float tilt = center ? lean * 0.3f : lean + 0.25f + world.random.nextFloat() * 0.35f;
			float h = height * (center ? 1.0f : 0.45f + world.random.nextFloat() * 0.4f);
			float w = Math.max(0.18f, h * (0.16f + world.random.nextFloat() * 0.06f));
			Vec3d offset = center ? Vec3d.ZERO : new Vec3d(Math.cos(yaw), 0, Math.sin(yaw)).multiply(0.15 + w * 0.6);
			shard(world, base.add(offset), crystal, yaw, tilt, w, h, life + world.random.nextInt(10), color);
		}
		world.playSound(null, BlockPos.ofFloored(base), SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.PLAYERS, 1.2f,
				0.6f + world.random.nextFloat() * 0.4f);
	}

	/** Ein einzelner Kristall: Fuss bei {@code base}, Neigung {@code tilt} (Bogenmass) in Richtung {@code yaw}. */
	public static void shard(ServerWorld world, Vec3d base, BlockState crystal, float yaw, float tilt, float width, float height, int life, int color) {
		if (SHARDS.size() >= MAX) {
			return;
		}
		DisplayEntity.BlockDisplayEntity display = EntityType.BLOCK_DISPLAY.create(world);
		if (display == null) {
			return;
		}
		display.refreshPositionAndAngles(base.x, base.y, base.z, 0.0f, 0.0f);
		// Achse der Neigung: waagerecht, quer zur Kipprichtung
		Vector3f axis = new Vector3f((float) -Math.sin(yaw), 0.0f, (float) Math.cos(yaw));
		Quaternionf rotation = new Quaternionf().fromAxisAngleRad(axis, tilt).rotateY(world.random.nextFloat() * MathHelper.TAU);
		NbtCompound nbt = display.writeNbt(new NbtCompound());
		nbt.put("block_state", NbtHelper.fromBlockState(crystal));
		nbt.put("transformation", transformation(rotation, width, 0.01f));
		nbt.putInt("brightness_override", 0);
		NbtCompound brightness = new NbtCompound();
		brightness.putInt("block", 15);
		brightness.putInt("sky", 15);
		nbt.put("brightness", brightness);
		nbt.putFloat("view_range", 2.0f);
		display.readNbt(nbt);
		world.spawnEntity(display);
		SHARDS.add(new Shard(world, display, base, rotation, width, height, Math.max(GROW + 4, life), color, new int[] {0}));
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Shard> it = SHARDS.iterator(); it.hasNext(); ) {
			Shard s = it.next();
			int age = ++s.age()[0];
			if (s.display().isRemoved()) {
				it.remove();
				continue;
			}
			if (age == 1) {
				animate(s, s.width(), s.height(), GROW);
			} else if (age == s.life() - 3) {
				animate(s, s.width() * 0.2f, s.height() * 0.15f, 3);
			} else if (age >= s.life()) {
				Vec3d mid = s.base().add(0, s.height() * 0.4, 0);
				Fx.shards(s.world(), mid, s.color(), 0.35f, 6);
				if (s.world().random.nextInt(3) == 0) {
					s.world().playSound(null, BlockPos.ofFloored(mid), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 0.7f, 1.2f);
				}
				s.display().discard();
				it.remove();
			}
		}
	}

	private static void animate(Shard s, float width, float height, int duration) {
		NbtCompound nbt = s.display().writeNbt(new NbtCompound());
		nbt.put("transformation", transformation(s.tilt(), width, height));
		nbt.putInt("start_interpolation", 0);
		nbt.putInt("interpolation_duration", duration);
		s.display().readNbt(nbt);
	}

	/** Blockmodell (0..1) unten mittig an den Ursprung legen, strecken, dann um den Fuss kippen. */
	private static NbtCompound transformation(Quaternionf rotation, float width, float height) {
		Vector3f corner = new Vector3f(-width * 0.5f, 0.0f, -width * 0.5f);
		rotation.transform(corner);
		NbtCompound t = new NbtCompound();
		t.put("translation", floats(corner.x, corner.y, corner.z));
		t.put("left_rotation", floats(rotation.x, rotation.y, rotation.z, rotation.w));
		t.put("scale", floats(width, height, width));
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
