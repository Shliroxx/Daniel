package com.santiq.kingdomomnitrix.power;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Blockzerstoerung durch Alien-Kraefte. Dauerhaft wie eine Explosion (Drops anteilig), aber:
 * <ul>
 *   <li>nur wenn {@link PowerConfig#griefingAllowed} (Config, Gamerule, mobGriefing),</li>
 *   <li>nur Bloecke bis zur Haerte = Staerke des Aliens; unzerstoerbare und {@code #kingdomomnitrix:alien_proof} nie,</li>
 *   <li>keine Bloecke mit Block-Entity (Truhen, Oefen ... — kein Verlust von Inventar),</li>
 *   <li>Schutz-Mods und Spawnschutz werden gefragt (wie beim Abbauen durch den Spieler),</li>
 *   <li>grosse Krater wachsen von innen nach aussen und werden auf mehrere Ticks verteilt.</li>
 * </ul>
 * Staerke-Richtwerte (Haerte): Erde 0,5 · Stein 1,5 · Erz 3 · Eisenblock 5 · Obsidian 50.
 */
public final class Destruction {
	public static final TagKey<Block> ALIEN_PROOF = TagKey.of(RegistryKeys.BLOCK, com.santiq.kingdomomnitrix.KingdomOmnitrix.id("alien_proof"));
	private static final PriorityQueue<Job> JOBS = new PriorityQueue<>(Comparator.comparingLong(Job::at));
	private static long clock;

	private record Job(long at, ServerWorld world, BlockPos pos, @Nullable ServerPlayerEntity owner, float strength, Vec3d push) {
	}

	private Destruction() {
	}

	public static void register() {
		PowerConfig.load();
		ServerTickEvents.END_SERVER_TICK.register(Destruction::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> JOBS.clear());
	}

	/**
	 * Krater: Kugel mit unregelmaessigem Rand. {@code delayPerBlock}: Ticks Verzoegerung je Block Abstand (0 = sofort).
	 * Truemmer fliegen von {@code center} weg, nach oben verstaerkt um {@code lift}.
	 *
	 * @return Zahl der eingeplanten Bloecke (0, wenn Zerstoerung aus ist)
	 */
	public static int crater(ServerWorld world, @Nullable ServerPlayerEntity owner, Vec3d center, double radius, float strength,
			double delayPerBlock, double lift) {
		return crater(world, owner, center, radius, 1.0, strength, delayPerBlock, lift);
	}

	/** Wie {@link #crater}, aber senkrecht gestaucht ({@code depth} 0,5 = Schuessel halb so tief wie breit). */
	public static int crater(ServerWorld world, @Nullable ServerPlayerEntity owner, Vec3d center, double radius, double depth, float strength,
			double delayPerBlock, double lift) {
		if (!PowerConfig.griefingAllowed(world) || radius <= 0.0) {
			return 0;
		}
		int r = MathHelper.ceil(radius);
		BlockPos c = BlockPos.ofFloored(center);
		long seed = c.asLong() * 31L + world.getTime();
		int count = 0;
		for (BlockPos pos : BlockPos.iterate(c.add(-r, -r, -r), c.add(r, r, r))) {
			double dx = pos.getX() + 0.5 - center.x;
			double dy = (pos.getY() + 0.5 - center.y) / depth;
			double dz = pos.getZ() + 0.5 - center.z;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double edge = radius * (0.78 + 0.22 * noise(pos, seed));
			if (d > edge) {
				continue;
			}
			Vec3d out = Vec3d.ofCenter(pos).subtract(center);
			Vec3d push = (out.lengthSquared() < 1.0E-4 ? new Vec3d(0, 1, 0) : out.normalize()).multiply(0.35 + 0.25 * (1.0 - d / radius))
					.add(0.0, lift * (1.0 - d / radius * 0.5), 0.0);
			if (queue(world, pos.toImmutable(), owner, strength, push, (long) (d * delayPerBlock))) {
				count++;
			}
		}
		return count;
	}

	/** Schneise: Kugeln entlang einer Strecke (Durchbrechen, Ansturm, Strahl). */
	public static int carve(ServerWorld world, @Nullable ServerPlayerEntity owner, Vec3d from, Vec3d direction, double length, double radius,
			float strength, double delayPerBlock) {
		if (!PowerConfig.griefingAllowed(world) || length <= 0.0) {
			return 0;
		}
		Vec3d dir = direction.normalize();
		Set<BlockPos> seen = new HashSet<>();
		int count = 0;
		int r = MathHelper.ceil(radius);
		for (double t = 0.0; t <= length; t += 0.5) {
			Vec3d p = from.add(dir.multiply(t));
			BlockPos c = BlockPos.ofFloored(p);
			for (BlockPos pos : BlockPos.iterate(c.add(-r, -r, -r), c.add(r, r, r))) {
				if (pos.getSquaredDistanceFromCenter(p.x, p.y, p.z) > radius * radius || !seen.add(pos.toImmutable())) {
					continue;
				}
				Vec3d push = dir.multiply(0.5).add(0.0, 0.25, 0.0);
				if (queue(world, pos.toImmutable(), owner, strength, push, (long) (t * delayPerBlock))) {
					count++;
				}
			}
		}
		return count;
	}

	/** Einzelner Block (Faustschlag gegen eine Wand). */
	public static boolean smash(ServerWorld world, @Nullable ServerPlayerEntity owner, BlockPos pos, float strength, Vec3d push) {
		if (!PowerConfig.griefingAllowed(world) || !breakable(world, pos, world.getBlockState(pos), owner, strength)) {
			return false;
		}
		destroy(world, pos, world.getBlockState(pos), owner, push);
		return true;
	}

	/**
	 * Feuer legen: auf freien Bodenstellen im Radius mit Wahrscheinlichkeit {@code chance} (Heatblast, Explosionen).
	 * Nur wenn Zerstoerung erlaubt ist; Schutz-Mods werden wie beim Platzieren gefragt (ueber {@link #breakable} auf Luft
	 * entfaellt das, darum zusaetzlich Spawnschutz/Abenteuer-Modus).
	 */
	public static int ignite(ServerWorld world, @Nullable ServerPlayerEntity owner, Vec3d center, double radius, double chance) {
		if (!PowerConfig.griefingAllowed(world) || chance <= 0.0) {
			return 0;
		}
		int r = MathHelper.ceil(radius);
		BlockPos c = BlockPos.ofFloored(center);
		int count = 0;
		for (BlockPos pos : BlockPos.iterate(c.add(-r, -2, -r), c.add(r, 2, r))) {
			if (pos.getSquaredDistance(c) > radius * radius || world.random.nextDouble() >= chance || !world.getBlockState(pos).isAir()) {
				continue;
			}
			if (owner != null && (!world.canPlayerModifyAt(owner, pos)
					|| owner.isBlockBreakingRestricted(world, pos, owner.interactionManager.getGameMode()))) {
				continue;
			}
			BlockState fire = net.minecraft.block.AbstractFireBlock.getState(world, pos);
			if (fire.canPlaceAt(world, pos)) {
				world.setBlockState(pos.toImmutable(), fire, Block.NOTIFY_ALL);
				count++;
			}
		}
		return count;
	}

	/**
	 * Block umwandeln statt zerstoeren (Hitze schmilzt Sand zu Glas, Stein gluet zu Magma). Gleiche Regeln wie das
	 * Zerstoeren: Zerstoerung erlaubt, Schutz-Mods und Haerte.
	 */
	public static boolean transmute(ServerWorld world, @Nullable ServerPlayerEntity owner, BlockPos pos, BlockState into, float strength) {
		if (!PowerConfig.griefingAllowed(world)) {
			return false;
		}
		BlockState state = world.getBlockState(pos);
		if (state.equals(into) || !breakable(world, pos, state, owner, strength)) {
			return false;
		}
		world.setBlockState(pos.toImmutable(), into, Block.NOTIFY_ALL);
		return true;
	}

	/** Darf {@code owner} mit {@code strength} diesen Block zerstoeren? */
	public static boolean breakable(ServerWorld world, BlockPos pos, BlockState state, @Nullable ServerPlayerEntity owner, float strength) {
		if (state.isAir() || !state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.block.FluidBlock) {
			return false;
		}
		float hardness = state.getHardness(world, pos);
		if (hardness < 0.0f || hardness > strength * PowerConfig.get().strengthScale || state.isIn(ALIEN_PROOF) || state.hasBlockEntity()) {
			return false;
		}
		if (world.isOutOfHeightLimit(pos) || !world.getWorldBorder().contains(pos)) {
			return false;
		}
		if (owner != null) {
			if (!world.canPlayerModifyAt(owner, pos) || owner.isBlockBreakingRestricted(world, pos, owner.interactionManager.getGameMode())) {
				return false;
			}
			// Schutz-Mods (Claims) haengen an diesem Ereignis
			return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, owner, pos, state, null);
		}
		return true;
	}

	private static boolean queue(ServerWorld world, BlockPos pos, @Nullable ServerPlayerEntity owner, float strength, Vec3d push, long delay) {
		BlockState state = world.getBlockState(pos);
		if (state.isAir() || !breakable(world, pos, state, owner, strength)) {
			return false;
		}
		JOBS.add(new Job(clock + Math.max(0L, delay), world, pos, owner, strength, push));
		return true;
	}

	private static void tick(MinecraftServer server) {
		clock++;
		int budget = PowerConfig.get().blocksPerTick;
		while (budget > 0 && !JOBS.isEmpty() && JOBS.peek().at() <= clock) {
			Job job = JOBS.poll();
			if (!job.world().isChunkLoaded(job.pos().getX() >> 4, job.pos().getZ() >> 4)) {
				continue;
			}
			BlockState state = job.world().getBlockState(job.pos());
			// erneut pruefen: seit dem Einplanen kann sich der Block geaendert haben
			if (state.isAir() || !breakable(job.world(), job.pos(), state, job.owner(), job.strength())) {
				continue;
			}
			destroy(job.world(), job.pos(), state, job.owner(), job.push());
			budget--;
		}
	}

	private static void destroy(ServerWorld world, BlockPos pos, BlockState state, @Nullable ServerPlayerEntity owner, Vec3d push) {
		PowerConfig.Values cfg = PowerConfig.get();
		if (cfg.dropBlocks && world.random.nextDouble() < cfg.dropChance) {
			Block.dropStacks(state, world, pos, null, owner, ItemStack.EMPTY);
		}
		world.setBlockState(pos, state.getFluidState().getBlockState(), Block.NOTIFY_ALL);
		world.emitGameEvent(owner, net.minecraft.world.event.GameEvent.BLOCK_DESTROY, pos);
		if (owner != null) {
			PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(world, owner, pos, state, null);
		}
		if (world.random.nextDouble() < cfg.debrisChance) {
			Vec3d jitter = new Vec3d(world.random.nextGaussian() * 0.12, world.random.nextDouble() * 0.15, world.random.nextGaussian() * 0.12);
			Debris.chunk(world, state, Vec3d.ofCenter(pos), push.add(jitter), 0.45f + world.random.nextFloat() * 0.35f);
		} else if (world.random.nextInt(4) == 0) {
			world.syncWorldEvent(net.minecraft.world.WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
		}
	}

	/** glatte Pseudozufallszahl 0..1 je Block (unregelmaessiger Kraterrand). */
	private static double noise(BlockPos pos, long seed) {
		long h = seed ^ (pos.getX() * 73856093L) ^ (pos.getY() * 19349663L) ^ (pos.getZ() * 83492791L);
		h = (h ^ (h >>> 13)) * 0x5bd1e995L;
		return ((h ^ (h >>> 15)) & 0xFFFF) / 65535.0;
	}
}
