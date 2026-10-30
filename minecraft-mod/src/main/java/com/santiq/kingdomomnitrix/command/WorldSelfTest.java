package com.santiq.kingdomomnitrix.command;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Selbsttest der Welten und Wesen ({@code /hero selftest worlds}): reist nacheinander in jede Dimension der Mod
 * (Ankunft ueber dem Mittelpunkt, {@link #WORLD_TICKS} Ticks Aufenthalt, damit Gelaende und Wesen entstehen) und
 * beschwoert danach in der Ausgangswelt jedes Wesen der Mod einmal ({@link #ENTITY_TICKS} Ticks). Erfasst werden
 * Fehler und Warnungen im Log, ob die Welt Boden hat und welche Wesen dort erscheinen, ob ein Wesen entsteht und die
 * Zeit ueberlebt. Bericht: {@value #REPORT} im Serverordner. Danach kehrt der Spieler an den Ausgangspunkt zurueck.
 */
public final class WorldSelfTest {
	private static final int WORLD_TICKS = 100;
	private static final int ENTITY_TICKS = 40;
	static final String REPORT = "kingdomomnitrix-selftest-welten.md";
	/** Welten ohne Boden am Mittelpunkt: das All, und die Station, die erst beim Landen mit dem Schiff entsteht */
	private static final java.util.Set<String> VOID_WORLDS = java.util.Set.of("space", "nefarious_station");

	private static Run active;
	private static boolean registered;

	private WorldSelfTest() {
	}

	static boolean running() {
		return active != null;
	}

	private sealed interface Job permits WorldJob, EntityJob {
	}

	private record WorldJob(RegistryKey<World> key) implements Job {
	}

	private record EntityJob(EntityType<?> type) implements Job {
	}

	private record Line(String kind, String name, boolean ok, String details, List<String> log) {
	}

	private static final class Run {
		final ServerPlayerEntity player;
		final ServerWorld home;
		final Vec3d origin;
		final float yaw;
		final Deque<Job> jobs;
		final int total;
		final List<Line> lines = new ArrayList<>();
		Job current;
		Entity spawned;
		int wait;

		Run(ServerPlayerEntity player, Deque<Job> jobs) {
			this.player = player;
			this.home = player.getServerWorld();
			this.origin = player.getPos();
			this.yaw = player.getYaw();
			this.jobs = jobs;
			this.total = jobs.size();
		}
	}

	public static int start(ServerPlayerEntity player) {
		if (active != null || SelfTest.running()) {
			player.sendMessage(Text.translatable("commands.kingdomomnitrix.selftest.busy").formatted(Formatting.RED));
			return 0;
		}
		if (!registered) {
			registered = true;
			ServerTickEvents.END_SERVER_TICK.register(WorldSelfTest::tick);
		}
		MinecraftServer server = player.getServer();
		Deque<Job> jobs = new ArrayDeque<>();
		for (ServerWorld world : server.getWorlds()) {
			if (world.getRegistryKey().getValue().getNamespace().equals(KingdomOmnitrix.MOD_ID)) {
				jobs.add(new WorldJob(world.getRegistryKey()));
			}
		}
		for (EntityType<?> type : Registries.ENTITY_TYPE) {
			if (Registries.ENTITY_TYPE.getId(type).getNamespace().equals(KingdomOmnitrix.MOD_ID)) {
				jobs.add(new EntityJob(type));
			}
		}
		SelfTest.installCapture();
		active = new Run(player, jobs);
		int seconds = Math.round((jobs.stream().filter(j -> j instanceof WorldJob).count() * WORLD_TICKS
				+ jobs.stream().filter(j -> j instanceof EntityJob).count() * ENTITY_TICKS) / 20.0f);
		player.sendMessage(Text.translatable("commands.kingdomomnitrix.selftest.worlds_start", jobs.size(), seconds).formatted(Formatting.AQUA));
		KingdomOmnitrix.LOGGER.info("[Selbsttest Welten] Start: {} Pruefungen", jobs.size());
		return jobs.size();
	}

	private static void tick(MinecraftServer server) {
		Run run = active;
		if (run == null) {
			return;
		}
		if (run.player.isRemoved() || run.player.isDisconnected()) {
			finish(run, true);
			return;
		}
		if (run.wait > 0) {
			run.wait--;
			return;
		}
		if (run.current != null) {
			measure(run);
			run.current = null;
		}
		Job job = run.jobs.poll();
		if (job == null) {
			finish(run, false);
			return;
		}
		run.current = job;
		SelfTest.CAPTURED.clear();
		try {
			if (job instanceof WorldJob world) {
				enter(run, world);
				run.wait = WORLD_TICKS;
			} else if (job instanceof EntityJob entity) {
				summon(run, entity);
				run.wait = ENTITY_TICKS;
			}
		} catch (RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("[Selbsttest Welten] {} ist abgestuerzt", name(job), e);
			record(run, false, "Absturz: " + e);
			run.current = null;
		}
	}

	private static void enter(Run run, WorldJob job) {
		ServerWorld world = run.player.getServer().getWorld(job.key());
		if (world == null) {
			throw new IllegalStateException("Welt nicht geladen");
		}
		world.getChunk(0, 0); // Gelaende am Ankunftspunkt erzeugen
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0);
		double y = top > world.getBottomY() ? top + 1.0 : 128.0;
		run.player.teleport(world, 0.5, y, 0.5, run.yaw, 0.0f);
		run.player.setVelocity(Vec3d.ZERO);
	}

	private static void summon(Run run, EntityJob job) {
		ServerPlayerEntity player = run.player;
		if (player.getServerWorld() != run.home || player.getPos().distanceTo(run.origin) > 1.0) {
			player.teleport(run.home, run.origin.x, run.origin.y, run.origin.z, run.yaw, 0.0f);
		}
		Entity entity = job.type().create(run.home);
		if (entity == null) {
			throw new IllegalStateException("Wesen laesst sich nicht erzeugen");
		}
		float yaw = run.yaw * MathHelper.RADIANS_PER_DEGREE;
		Vec3d pos = run.origin.add(-MathHelper.sin(yaw) * 5.0, 0.0, MathHelper.cos(yaw) * 5.0);
		entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, run.yaw + 180.0f, 0.0f);
		if (entity instanceof MobEntity mob) {
			mob.initialize(run.home, run.home.getLocalDifficulty(mob.getBlockPos()), SpawnReason.COMMAND, null);
			mob.setPersistent();
		}
		if (!run.home.spawnEntity(entity)) {
			throw new IllegalStateException("Welt hat das Wesen abgelehnt");
		}
		run.spawned = entity;
	}

	private static void measure(Run run) {
		List<String> log = new ArrayList<>(SelfTest.CAPTURED);
		SelfTest.CAPTURED.clear();
		boolean errors = log.stream().anyMatch(line -> line.startsWith("ERROR") || line.startsWith("FATAL"));
		if (run.current instanceof WorldJob job) {
			ServerWorld world = run.player.getServerWorld();
			boolean arrived = world.getRegistryKey().equals(job.key());
			BlockPos below = run.player.getBlockPos().down();
			BlockState ground = world.getBlockState(below);
			int creatures = world.getOtherEntities(run.player, run.player.getBoundingBox().expand(96.0), e -> e instanceof LivingEntity).size();
			String details = String.format(Locale.ROOT, "%s, Boden %s bei y %d, %d Lebewesen im Umkreis 96",
					arrived ? "angekommen" : "NICHT angekommen", Registries.BLOCK.getId(ground.getBlock()).getPath(), below.getY(), creatures);
			boolean voidWorld = VOID_WORLDS.contains(job.key().getValue().getPath());
			boolean ok = arrived && !errors && (voidWorld || !ground.isAir());
			if (ground.isAir()) {
				details += voidWorld ? " — erwartet (Leere, Landung nur per Schiff)" : " — kein Boden unter dem Ankunftspunkt";
			}
			record(run, ok, details, log);
		} else if (run.current instanceof EntityJob) {
			Entity entity = run.spawned;
			boolean alive = entity != null && !entity.isRemoved();
			String details = alive ? "lebt nach " + ENTITY_TICKS + " Ticks"
					: "nach " + ENTITY_TICKS + " Ticks verschwunden (bei Geschossen/Effekten erwartet)";
			if (entity instanceof LivingEntity living && alive) {
				details += String.format(Locale.ROOT, ", Leben %.0f/%.0f", living.getHealth(), living.getMaxHealth());
			}
			record(run, !errors, details, log);
			if (entity != null && !entity.isRemoved()) {
				entity.discard();
			}
			run.spawned = null;
		}
	}

	private static void record(Run run, boolean ok, String details) {
		record(run, ok, details, List.of());
	}

	private static void record(Run run, boolean ok, String details, List<String> log) {
		Job job = run.current;
		String kind = job instanceof WorldJob ? "Welt" : "Wesen";
		run.lines.add(new Line(kind, name(job), ok, details, log));
		if (!ok) {
			KingdomOmnitrix.LOGGER.info("[Selbsttest Welten] {} {} → FEHLER {}", kind, name(job), details);
		}
	}

	private static String name(Job job) {
		Identifier id = job instanceof WorldJob world ? world.key().getValue() : Registries.ENTITY_TYPE.getId(((EntityJob) job).type());
		return id.getPath();
	}

	private static void finish(Run run, boolean aborted) {
		active = null;
		if (run.spawned != null && !run.spawned.isRemoved()) {
			run.spawned.discard();
		}
		SelfTest.removeCapture();
		if (!run.player.isDisconnected()) {
			run.player.teleport(run.home, run.origin.x, run.origin.y, run.origin.z, run.yaw, 0.0f);
		}
		long failed = run.lines.stream().filter(line -> !line.ok()).count();
		StringBuilder out = new StringBuilder("# Kingdom Omnitrix — Selbsttest der Welten und Wesen\n\n");
		out.append("Pruefungen: ").append(run.lines.size()).append(" von ").append(run.total).append(aborted ? " (abgebrochen)" : "")
				.append(" · Fehler: ").append(failed).append("\n\n| Art | Name | Ergebnis | Messung |\n|---|---|---|---|\n");
		for (Line line : run.lines) {
			out.append("| ").append(line.kind()).append(" | ").append(line.name()).append(" | ").append(line.ok() ? "OK" : "FEHLER")
					.append(" | ").append(line.details().replace("|", "/")).append(" |\n");
			for (String entry : line.log()) {
				out.append("|  |  | ↳ | ").append(entry.replace("|", "/").replace("\n", " ")).append(" |\n");
			}
		}
		Path file = run.player.getServer().getRunDirectory().resolve(REPORT);
		try {
			Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			KingdomOmnitrix.LOGGER.error("[Selbsttest Welten] Bericht konnte nicht geschrieben werden: {}", file, e);
		}
		KingdomOmnitrix.LOGGER.info("[Selbsttest Welten] Ende{}: {} Pruefungen, {} Fehler — Bericht {}", aborted ? " (abgebrochen)" : "",
				run.lines.size(), failed, file.toAbsolutePath());
		if (!run.player.isDisconnected()) {
			run.player.sendMessage(Text.translatable("commands.kingdomomnitrix.selftest.worlds_done", run.lines.size(), failed, REPORT)
					.formatted(failed == 0 ? Formatting.GREEN : Formatting.GOLD));
		}
	}
}
