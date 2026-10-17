package com.santiq.kingdomomnitrix.power;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Fliegende Truemmer: echte Blockoptik (Block-Display) mit Schwerkraft, Drehung und Aufprall. Reine Truemmer zerfallen
 * beim Aufprall in Blockkrumen; Wurfgeschosse (Felsbrocken) melden ihren Einschlag an den Werfer.
 */
public final class Debris {
	private static final double GRAVITY = 0.07;
	private static final double DRAG = 0.985;
	private static final List<Piece> PIECES = new ArrayList<>();

	/** Einschlag eines Wurfgeschosses: Welt und Stelle. */
	public interface Impact extends BiConsumer<ServerWorld, Vec3d> {
	}

	private static final class Piece {
		final ServerWorld world;
		final DisplayEntity.BlockDisplayEntity display;
		final BlockState state;
		final float scale;
		final Vector3f axis;
		final float spin;
		final int maxAge;
		@Nullable
		final ServerPlayerEntity owner;
		@Nullable
		final Impact impact;
		Vec3d pos;
		Vec3d velocity;
		float angle;
		int age;

		Piece(ServerWorld world, DisplayEntity.BlockDisplayEntity display, BlockState state, float scale, Vec3d pos, Vec3d velocity,
				Vector3f axis, float spin, int maxAge, @Nullable ServerPlayerEntity owner, @Nullable Impact impact) {
			this.world = world;
			this.display = display;
			this.state = state;
			this.scale = scale;
			this.pos = pos;
			this.velocity = velocity;
			this.axis = axis;
			this.spin = spin;
			this.maxAge = maxAge;
			this.owner = owner;
			this.impact = impact;
		}
	}

	private Debris() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Debris::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PIECES.forEach(p -> p.display.discard());
			PIECES.clear();
		});
	}

	/** Ein Truemmerstueck (rein optisch). */
	public static void chunk(ServerWorld world, BlockState state, Vec3d center, Vec3d velocity, float scale) {
		if (state.isAir() || PIECES.size() >= PowerConfig.get().maxDebris) {
			return;
		}
		spawn(world, state, center, velocity, scale, 80, null, null);
	}

	/** Wurfgeschoss aus Blockoptik; {@code impact} wird beim Aufprall (Block oder Wesen) genau einmal aufgerufen. */
	public static void projectile(ServerWorld world, ServerPlayerEntity owner, BlockState state, Vec3d center, Vec3d velocity, float scale,
			Impact impact) {
		spawn(world, state, center, velocity, scale, 140, owner, impact);
	}

	private static void spawn(ServerWorld world, BlockState state, Vec3d center, Vec3d velocity, float scale, int maxAge,
			@Nullable ServerPlayerEntity owner, @Nullable Impact impact) {
		DisplayEntity.BlockDisplayEntity display = EntityType.BLOCK_DISPLAY.create(world);
		if (display == null) {
			return;
		}
		display.refreshPositionAndAngles(center.x, center.y, center.z, 0.0f, 0.0f);
		Vector3f axis = new Vector3f(world.random.nextFloat() - 0.5f, world.random.nextFloat() - 0.5f, world.random.nextFloat() - 0.5f);
		if (axis.lengthSquared() < 1.0E-4f) {
			axis.set(0.0f, 1.0f, 0.0f);
		}
		axis.normalize();
		float spin = (0.15f + world.random.nextFloat() * 0.35f) * (impact != null ? 0.5f : 1.0f);
		NbtCompound nbt = display.writeNbt(new NbtCompound());
		nbt.put("block_state", NbtHelper.fromBlockState(state));
		nbt.put("transformation", transformation(new Quaternionf(), scale));
		nbt.putInt("teleport_duration", 2);
		nbt.putFloat("view_range", 2.0f);
		display.readNbt(nbt);
		world.spawnEntity(display);
		PIECES.add(new Piece(world, display, state, scale, center, velocity, axis, spin, maxAge, owner, impact));
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Piece> it = PIECES.iterator(); it.hasNext(); ) {
			Piece p = it.next();
			p.age++;
			if (p.display.isRemoved() || p.age > p.maxAge) {
				p.display.discard();
				it.remove();
				continue;
			}
			Vec3d next = p.pos.add(p.velocity);
			boolean hitBlock = solid(p.world, next, p.scale);
			LivingEntity hitEntity = p.impact == null ? null : entityAt(p, next);
			if (hitBlock || hitEntity != null) {
				land(p, hitBlock ? p.pos : next);
				it.remove();
				continue;
			}
			p.pos = next;
			p.velocity = p.velocity.multiply(DRAG).add(0.0, -GRAVITY, 0.0);
			p.angle += p.spin;
			p.display.refreshPositionAndAngles(p.pos.x, p.pos.y, p.pos.z, 0.0f, 0.0f);
			NbtCompound nbt = p.display.writeNbt(new NbtCompound());
			nbt.put("transformation", transformation(new Quaternionf().fromAxisAngleRad(p.axis, p.angle), p.scale));
			nbt.putInt("start_interpolation", 0);
			nbt.putInt("interpolation_duration", 1);
			p.display.readNbt(nbt);
		}
	}

	private static void land(Piece p, Vec3d at) {
		p.display.discard();
		int crumbs = (int) (6 + 10 * p.scale * p.scale);
		p.world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, p.state), at.x, at.y, at.z, crumbs,
				0.25 * p.scale, 0.2 * p.scale, 0.25 * p.scale, 0.15);
		if (p.impact != null) {
			p.impact.accept(p.world, at);
		} else if (p.world.random.nextInt(3) == 0) {
			p.world.playSound(null, BlockPos.ofFloored(at), p.state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 0.5f, 0.8f);
		}
	}

	private static boolean solid(ServerWorld world, Vec3d at, float scale) {
		BlockPos pos = BlockPos.ofFloored(at.x, at.y - scale * 0.4, at.z);
		BlockState state = world.getBlockState(pos);
		return !state.isAir() && !state.getCollisionShape(world, pos).isEmpty();
	}

	@Nullable
	private static LivingEntity entityAt(Piece p, Vec3d at) {
		double r = 0.45 * p.scale + 0.2;
		for (LivingEntity e : p.world.getEntitiesByClass(LivingEntity.class, Box.of(at, r * 2, r * 2, r * 2), LivingEntity::isAlive)) {
			if (e != p.owner && (p.owner == null || com.santiq.kingdomomnitrix.party.PartyRules.canHarm(p.owner, e))) {
				return e;
			}
		}
		return null;
	}

	/** Drehung um die Mitte: Blockmodell (0..1) skalieren, um die Mitte zentrieren, dann drehen. */
	private static NbtCompound transformation(Quaternionf rotation, float scale) {
		Vector3f half = new Vector3f(scale * 0.5f);
		rotation.transform(half);
		NbtCompound t = new NbtCompound();
		t.put("translation", floats(-half.x, -half.y, -half.z));
		t.put("left_rotation", floats(rotation.x, rotation.y, rotation.z, rotation.w));
		t.put("scale", floats(scale, scale, scale));
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
