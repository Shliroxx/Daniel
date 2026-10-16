package com.santiq.kingdomomnitrix.world.content;

import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Temporaere Bloecke der Alien-Faehigkeiten (Kristallbruecken, Stacheln, Eiswaende): nur in Luft gesetzt, nach Ablauf
 * mit Splitter-Effekt wieder entfernt — genau das, was gesetzt wurde. Abbauen laesst sie zerspringen (kein Drop), beim
 * Herunterfahren verschwinden alle. So formen Aliens die Welt, ohne sie dauerhaft zu veraendern.
 */
public final class TempBlocks {
	private static final int QUIET = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
	private static final int MAX = 4096;
	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static final Map<RegistryKey<World>, Map<BlockPos, Entry>> INDEX = new HashMap<>();

	private record Entry(RegistryKey<World> world, BlockPos pos, BlockState state, long until, UUID owner, int color) {
	}

	private TempBlocks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TempBlocks::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(TempBlocks::removeAll);
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerWorld server) {
				Entry entry = index(server.getRegistryKey()).get(pos);
				if (entry != null) {
					shatter(server, entry);
					remove(entry);
					return false;
				}
			}
			return true;
		});
	}

	private static Map<BlockPos, Entry> index(RegistryKey<World> world) {
		return INDEX.computeIfAbsent(world, k -> new HashMap<>());
	}

	/**
	 * Setzt einen temporaeren Block, wenn dort Luft (oder Ersetzbares wie Gras) ist und keine Entitaet im Weg steht.
	 *
	 * @param color Farbe der Splitter beim Zerspringen
	 * @return true, wenn gesetzt
	 */
	public static boolean place(ServerWorld world, BlockPos pos, BlockState state, int ticks, LivingEntity owner, int color, boolean allowEntities) {
		if (ENTRIES.size() >= MAX || !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || world.isOutOfHeightLimit(pos)) {
			return false;
		}
		BlockState current = world.getBlockState(pos);
		Entry existing = index(world.getRegistryKey()).get(pos);
		if (existing != null) {
			// eigener Block: nur die Lebensdauer verlaengern
			remove(existing);
			Entry renewed = new Entry(existing.world(), pos, existing.state(), Math.max(existing.until(), world.getTime() + ticks),
					existing.owner(), existing.color());
			ENTRIES.add(renewed);
			index(world.getRegistryKey()).put(pos, renewed);
			return true;
		}
		if (!current.isAir() || !world.getFluidState(pos).isEmpty()) {
			return false;
		}
		if (!allowEntities && !world.getEntitiesByClass(LivingEntity.class, new Box(pos), LivingEntity::isAlive).isEmpty()) {
			return false;
		}
		world.setBlockState(pos, state, Block.NOTIFY_ALL);
		Entry entry = new Entry(world.getRegistryKey(), pos.toImmutable(), state, world.getTime() + ticks, owner == null ? null : owner.getUuid(),
				color);
		ENTRIES.add(entry);
		index(world.getRegistryKey()).put(entry.pos(), entry);
		return true;
	}

	/** Gehoert der temporaere Block an pos dieser Entitaet (sie wird von eigenen Stacheln nicht gestochen)? */
	public static boolean isOwner(World world, BlockPos pos, LivingEntity entity) {
		Entry entry = index(world.getRegistryKey()).get(pos);
		return entry != null && entity.getUuid().equals(entry.owner());
	}

	public static boolean isTemp(World world, BlockPos pos) {
		return index(world.getRegistryKey()).containsKey(pos);
	}

	/** Alle Bloecke in einem Radius sofort zerspringen lassen (z. B. Ende einer Faehigkeit). */
	public static void shatterAround(ServerWorld world, Vec3d center, double radius) {
		double r2 = radius * radius;
		for (Iterator<Entry> it = ENTRIES.iterator(); it.hasNext(); ) {
			Entry entry = it.next();
			if (entry.world() == world.getRegistryKey() && entry.pos().getSquaredDistance(center) <= r2) {
				shatter(world, entry);
				index(entry.world()).remove(entry.pos());
				it.remove();
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (ENTRIES.isEmpty()) {
			return;
		}
		int sounds = 0;
		for (Iterator<Entry> it = ENTRIES.iterator(); it.hasNext(); ) {
			Entry entry = it.next();
			ServerWorld world = server.getWorld(entry.world());
			if (world == null) {
				index(entry.world()).remove(entry.pos());
				it.remove();
				continue;
			}
			if (world.getTime() < entry.until() || !world.isChunkLoaded(entry.pos().getX() >> 4, entry.pos().getZ() >> 4)) {
				continue;
			}
			if (world.getBlockState(entry.pos()) == entry.state()) {
				world.setBlockState(entry.pos(), Blocks.AIR.getDefaultState(), QUIET);
				Fx.shards(world, Vec3d.ofCenter(entry.pos()), entry.color(), 0.25f, 3);
				if (sounds++ < 3 && world.random.nextInt(3) == 0) {
					world.playSound(null, entry.pos(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 0.5f,
							1.1f + world.random.nextFloat() * 0.4f);
				}
			}
			index(entry.world()).remove(entry.pos());
			it.remove();
		}
	}

	private static void shatter(ServerWorld world, Entry entry) {
		if (world.getBlockState(entry.pos()) == entry.state()) {
			world.setBlockState(entry.pos(), Blocks.AIR.getDefaultState(), QUIET);
			Fx.shards(world, Vec3d.ofCenter(entry.pos()), entry.color(), 0.3f, 6);
			world.playSound(null, entry.pos(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 0.8f, 1.2f);
		}
	}

	private static void remove(Entry entry) {
		ENTRIES.remove(entry);
		index(entry.world()).remove(entry.pos());
	}

	private static void removeAll(MinecraftServer server) {
		for (Entry entry : ENTRIES) {
			ServerWorld world = server.getWorld(entry.world());
			if (world != null && world.getBlockState(entry.pos()) == entry.state()) {
				world.setBlockState(entry.pos(), Blocks.AIR.getDefaultState(), QUIET);
			}
		}
		ENTRIES.clear();
		INDEX.clear();
	}
}
