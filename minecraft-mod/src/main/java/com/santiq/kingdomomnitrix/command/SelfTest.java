package com.santiq.kingdomomnitrix.command;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.AbilityContext;
import com.santiq.kingdomomnitrix.ability.AbilityRegistry;
import com.santiq.kingdomomnitrix.ability.AlienAbility;
import com.santiq.kingdomomnitrix.ability.PowerTasks;
import com.santiq.kingdomomnitrix.alien.AbilitySlot;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

/**
 * Selbsttest aller Alien-Kraefte ({@code /hero selftest [alien]}): verwandelt den Spieler nacheinander in jedes Alien und
 * loest jede Tasten-Kraft und jede Kombo einmal aus — ohne Freischaltung, Energie und Abklingzeit. Vor dem Spieler
 * stehen drei Testziele (Wuestenzombies ohne KI, 200 Leben). Gemessen wird nach {@link #WINDOW} Ticks:
 * <ul>
 *   <li>Absturz der Kraft (Ausnahme) und Fehler/Warnungen im Log waehrend des Fensters</li>
 *   <li>Schaden an den Zielen und wie viele getroffen wurden, Effekte an den Zielen, Rueckstoss</li>
 *   <li>Bewegung, Leben, Absorption und Effekte des Spielers</li>
 *   <li>neu entstandene Wesen (Klone, Drohnen …) und veraenderte Bloecke im Umkreis</li>
 * </ul>
 * Ergebnis je Kraft: OK, WIRKUNGSLOS (nichts davon), ABGELEHNT (Kraft meldet „nicht moeglich“), FEHLER oder ABSTURZ.
 * Der Bericht landet als Markdown im Serverordner ({@value #REPORT}) und zusammengefasst im Log. Zerstoerung durch
 * Alien-Kraefte ist waehrend des Tests aus, danach wieder wie vorher.
 */
public final class SelfTest {
	/** Ticks zwischen Ausloesen und Messen */
	private static final int WINDOW = 30;
	/** Ruhe-Ticks nach dem Aufraeumen, bevor die naechste Kraft startet */
	private static final int SETTLE = 6;
	private static final float TARGET_HEALTH = 200.0f;
	/** Umkreis fuer Wesen und Bloecke */
	private static final int RADIUS = 10;
	private static final int BLOCK_RADIUS = 6;
	static final String REPORT = "kingdomomnitrix-selftest.md";

	private static Run active;
	private static boolean registered;

	private SelfTest() {
	}

	/** Ergebnis einer Kraft. */
	enum Verdict {
		OK, WIRKUNGSLOS, ABGELEHNT, FEHLER, ABSTURZ
	}

	private record Job(Identifier alienId, int index, AbilitySlot slot) {
	}

	private record Outcome(Identifier alienId, int index, AbilitySlot slot, Verdict verdict, String details, List<String> log) {
	}

	/** Zustand vor dem Ausloesen. */
	private record Snapshot(Vec3d playerPos, float playerHealth, float absorption, int playerEffects, List<Float> targetHealth,
			List<Vec3d> targetPos, List<Integer> targetEffects, Set<UUID> entities, Map<BlockPos, String> blocks) {
	}

	private static final class Run {
		final ServerPlayerEntity player;
		final ServerWorld world;
		final Vec3d origin;
		final float yaw;
		final Deque<Job> jobs;
		final int total;
		final List<Outcome> outcomes = new ArrayList<>();
		final boolean griefing;
		final List<HuskEntity> targets = new ArrayList<>();
		Job current;
		Snapshot before;
		Verdict early;
		String earlyDetails = "";
		int wait;
		boolean measuring;

		Run(ServerPlayerEntity player, Deque<Job> jobs, boolean griefing) {
			this.player = player;
			this.world = player.getServerWorld();
			this.origin = player.getPos();
			this.yaw = player.getYaw();
			this.jobs = jobs;
			this.total = jobs.size();
			this.griefing = griefing;
		}
	}

	/** Fehler und Warnungen aus dem Log, waehrend eine Kraft laeuft. */
	private static final ConcurrentLinkedQueue<String> CAPTURED = new ConcurrentLinkedQueue<>();
	private static LogCapture capture;

	private static final class LogCapture extends AbstractAppender {
		LogCapture() {
			super("KingdomOmnitrixSelfTest", null, null, true, Property.EMPTY_ARRAY);
		}

		@Override
		public void append(LogEvent event) {
			boolean ours = event.getLoggerName() != null && event.getLoggerName().contains(KingdomOmnitrix.MOD_ID);
			if (event.getLevel().isMoreSpecificThan(Level.ERROR) || ours && event.getLevel().isMoreSpecificThan(Level.WARN)) {
				String message = event.getMessage().getFormattedMessage();
				Throwable thrown = event.getThrown();
				CAPTURED.add(event.getLevel() + ": " + message + (thrown != null ? " — " + thrown : ""));
			}
		}
	}

	/**
	 * Test starten.
	 *
	 * @param only nur dieses Alien (leer: alle)
	 * @return Zahl der zu testenden Kraefte, 0 wenn nichts zu tun ist oder schon ein Test laeuft
	 */
	public static int start(ServerPlayerEntity player, Optional<Identifier> only) {
		if (active != null) {
			player.sendMessage(Text.translatable("commands.kingdomomnitrix.selftest.busy").formatted(Formatting.RED));
			return 0;
		}
		register();
		ServerWorld world = player.getServerWorld();
		Deque<Job> jobs = new ArrayDeque<>();
		for (Identifier id : AlienRegistry.sortedIds(world.getRegistryManager())) {
			if (only.isPresent() && !only.get().equals(id)) {
				continue;
			}
			AlienDefinition alien = AlienRegistry.get(world.getRegistryManager(), id).orElseThrow();
			for (int i = 0; i < alien.abilities().size(); i++) {
				jobs.add(new Job(id, i, alien.abilities().get(i)));
			}
		}
		if (jobs.isEmpty()) {
			return 0;
		}
		GameRules.BooleanRule rule = world.getGameRules().get(GameRules.DO_MOB_GRIEFING);
		boolean griefing = rule.get();
		rule.set(false, world.getServer());
		installCapture();
		active = new Run(player, jobs, griefing);
		player.sendMessage(Text.translatable("commands.kingdomomnitrix.selftest.start", jobs.size(),
				Math.round(jobs.size() * (WINDOW + SETTLE) / 20.0f)).formatted(Formatting.AQUA));
		KingdomOmnitrix.LOGGER.info("[Selbsttest] Start: {} Kraefte", jobs.size());
		return jobs.size();
	}

	private static void register() {
		if (!registered) {
			registered = true;
			ServerTickEvents.END_SERVER_TICK.register(SelfTest::tick);
		}
	}

	private static void installCapture() {
		if (capture != null) {
			return;
		}
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration config = context.getConfiguration();
		capture = new LogCapture();
		capture.start();
		config.getRootLogger().addAppender(capture, Level.WARN, null);
		context.updateLoggers();
	}

	private static void removeCapture() {
		if (capture == null) {
			return;
		}
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		context.getConfiguration().getRootLogger().removeAppender(capture.getName());
		context.updateLoggers();
		capture.stop();
		capture = null;
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
		if (run.measuring) {
			measure(run);
			cleanup(run);
			run.measuring = false;
			run.wait = SETTLE;
			return;
		}
		Job job = run.jobs.poll();
		if (job == null) {
			finish(run, false);
			return;
		}
		fire(run, job);
	}

	private static void fire(Run run, Job job) {
		ServerPlayerEntity player = run.player;
		run.current = job;
		run.early = null;
		run.earlyDetails = "";
		Optional<Identifier> transformedAs = TransformationManager.get(player).activeAlien();
		if (!TransformationManager.get(player).isTransformed() || transformedAs.isEmpty() || !transformedAs.get().equals(job.alienId())) {
			TransformationManager.Result result = TransformationManager.transform(player, job.alienId(), true);
			if (result != TransformationManager.Result.SUCCESS) {
				record(run, Verdict.FEHLER, "Verwandlung fehlgeschlagen: " + result, List.of());
				run.wait = SETTLE;
				return;
			}
		}
		player.teleport(run.world, run.origin.x, run.origin.y, run.origin.z, run.yaw, 0.0f);
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		player.setHealth(player.getMaxHealth());
		player.extinguish();
		// der Testspieler soll nicht sterben (ein neuer Spieler nach dem Wiederbeleben braeche den Test ab)
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE,
				WINDOW + SETTLE + 20, 4, false, false));
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE,
				WINDOW + SETTLE + 20, 0, false, false));
		spawnTargets(run);
		run.before = snapshot(run);
		CAPTURED.clear();
		Optional<AlienAbility> ability = AbilityRegistry.get(job.slot().type());
		if (ability.isEmpty()) {
			record(run, Verdict.FEHLER, "unbekannter Typ " + job.slot().type(), List.of());
			cleanup(run);
			run.wait = SETTLE;
			return;
		}
		Optional<AlienDefinition> alien = AlienRegistry.get(run.world.getRegistryManager(), job.alienId());
		AbilityContext context = new AbilityContext(player, run.world, job.alienId(), alien.orElseThrow(), job.slot());
		if (!job.slot().isKey()) {
			// Kombo: das naechste Testziel gilt als getroffen
			context = context.withTarget(run.targets.get(0));
		}
		try {
			if (!ability.get().activate(context)) {
				run.early = Verdict.ABGELEHNT;
			}
		} catch (RuntimeException e) {
			run.early = Verdict.ABSTURZ;
			run.earlyDetails = e.toString();
			KingdomOmnitrix.LOGGER.error("[Selbsttest] {} #{} ({}) ist abgestuerzt", job.alienId(), job.index(), job.slot().type(), e);
		}
		run.measuring = true;
		run.wait = WINDOW;
	}

	private static void spawnTargets(Run run) {
		float yaw = run.yaw * MathHelper.RADIANS_PER_DEGREE;
		Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
		Vec3d side = new Vec3d(-forward.z, 0.0, forward.x);
		Vec3d[] spots = {forward.multiply(3.0), forward.multiply(7.0), forward.multiply(4.0).add(side.multiply(2.5))};
		for (Vec3d spot : spots) {
			HuskEntity husk = EntityType.HUSK.create(run.world);
			if (husk == null) {
				continue;
			}
			Vec3d pos = run.origin.add(spot);
			husk.refreshPositionAndAngles(pos.x, run.origin.y, pos.z, run.yaw + 180.0f, 0.0f);
			husk.initialize(run.world, run.world.getLocalDifficulty(husk.getBlockPos()), SpawnReason.COMMAND, null);
			husk.setAiDisabled(true);
			husk.setPersistent();
			husk.setSilent(true);
			husk.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, net.minecraft.item.ItemStack.EMPTY);
			husk.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
			var health = husk.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(TARGET_HEALTH);
			}
			husk.setHealth(TARGET_HEALTH);
			run.world.spawnEntity(husk);
			run.targets.add(husk);
		}
	}

	private static Snapshot snapshot(Run run) {
		ServerPlayerEntity player = run.player;
		List<Float> health = new ArrayList<>();
		List<Vec3d> positions = new ArrayList<>();
		List<Integer> effects = new ArrayList<>();
		for (HuskEntity target : run.targets) {
			health.add(target.getHealth());
			positions.add(target.getPos());
			effects.add(target.getStatusEffects().size());
		}
		Set<UUID> entities = new HashSet<>();
		for (Entity entity : run.world.getOtherEntities(player, area(run))) {
			entities.add(entity.getUuid());
		}
		return new Snapshot(player.getPos(), player.getHealth(), player.getAbsorptionAmount(), player.getStatusEffects().size(),
				health, positions, effects, entities, blocks(run));
	}

	private static Box area(Run run) {
		return new Box(BlockPos.ofFloored(run.origin)).expand(RADIUS);
	}

	private static Map<BlockPos, String> blocks(Run run) {
		Map<BlockPos, String> blocks = new HashMap<>();
		BlockPos center = BlockPos.ofFloored(run.origin);
		for (BlockPos pos : BlockPos.iterate(center.add(-BLOCK_RADIUS, -2, -BLOCK_RADIUS), center.add(BLOCK_RADIUS, 4, BLOCK_RADIUS))) {
			blocks.put(pos.toImmutable(), run.world.getBlockState(pos).toString());
		}
		return blocks;
	}

	private static void measure(Run run) {
		Snapshot before = run.before;
		ServerPlayerEntity player = run.player;
		List<String> log = new ArrayList<>(CAPTURED);
		CAPTURED.clear();
		float damage = 0.0f;
		int hit = 0;
		int knocked = 0;
		int debuffed = 0;
		for (int i = 0; i < run.targets.size(); i++) {
			HuskEntity target = run.targets.get(i);
			float now = target.isAlive() ? target.getHealth() : 0.0f;
			float lost = before.targetHealth().get(i) - now;
			if (lost > 0.05f) {
				damage += lost;
				hit++;
			}
			if (target.isAlive() && target.getPos().distanceTo(before.targetPos().get(i)) > 0.6) {
				knocked++;
			}
			if (target.isAlive() && target.getStatusEffects().size() > before.targetEffects().get(i)) {
				debuffed++;
			}
		}
		double moved = player.getPos().distanceTo(before.playerPos());
		float healed = player.getHealth() - before.playerHealth() + player.getAbsorptionAmount() - before.absorption();
		int effects = player.getStatusEffects().size() - before.playerEffects();
		int spawned = 0;
		for (Entity entity : run.world.getOtherEntities(player, area(run))) {
			if (!before.entities().contains(entity.getUuid()) && !run.targets.contains(entity)) {
				spawned++;
			}
		}
		int changed = 0;
		for (Map.Entry<BlockPos, String> entry : blocks(run).entrySet()) {
			if (!entry.getValue().equals(before.blocks().get(entry.getKey()))) {
				changed++;
			}
		}
		List<String> parts = new ArrayList<>();
		if (hit > 0) {
			parts.add(String.format(Locale.ROOT, "Schaden %.1f an %d Ziel(en)", damage, hit));
		}
		if (knocked > 0) {
			parts.add(knocked + " weggestossen");
		}
		if (debuffed > 0) {
			parts.add(debuffed + " mit Effekt");
		}
		if (moved > 0.8) {
			parts.add(String.format(Locale.ROOT, "Spieler %.1f Bloecke bewegt", moved));
		}
		if (healed > 0.5f) {
			parts.add(String.format(Locale.ROOT, "+%.1f Leben/Schild", healed));
		}
		if (effects > 0) {
			parts.add("+" + effects + " Effekt(e) am Spieler");
		}
		if (spawned > 0) {
			parts.add(spawned + " neue Wesen/Geschosse");
		}
		if (changed > 0) {
			parts.add(changed + " Bloecke veraendert");
		}
		String details = String.join(", ", parts);
		Verdict verdict;
		if (run.early == Verdict.ABSTURZ) {
			verdict = Verdict.ABSTURZ;
			details = run.earlyDetails;
		} else if (log.stream().anyMatch(line -> line.startsWith("ERROR") || line.startsWith("FATAL"))) {
			verdict = Verdict.FEHLER;
		} else if (run.early == Verdict.ABGELEHNT) {
			verdict = Verdict.ABGELEHNT;
		} else if (parts.isEmpty()) {
			verdict = Verdict.WIRKUNGSLOS;
		} else {
			verdict = Verdict.OK;
		}
		record(run, verdict, details, log);
	}

	private static void record(Run run, Verdict verdict, String details, List<String> log) {
		Job job = run.current;
		run.outcomes.add(new Outcome(job.alienId(), job.index(), job.slot(), verdict, details, log));
		int done = run.outcomes.size();
		if (verdict != Verdict.OK) {
			KingdomOmnitrix.LOGGER.info("[Selbsttest] {} #{} {} → {} {}", job.alienId().getPath(), job.index(), job.slot().type().getPath(),
					verdict, details);
		}
		if (done % 25 == 0) {
			run.player.sendMessage(Text.translatable("commands.kingdomomnitrix.selftest.progress", done, run.total).formatted(Formatting.GRAY),
					true);
		}
	}

	private static void cleanup(Run run) {
		for (HuskEntity target : run.targets) {
			target.discard();
		}
		run.targets.clear();
		Set<UUID> keep = run.before == null ? Set.of() : run.before.entities();
		for (Entity entity : run.world.getOtherEntities(run.player, area(run))) {
			if (!keep.contains(entity.getUuid()) && !(entity instanceof PlayerEntity)) {
				entity.discard();
			}
		}
		PowerTasks.clear(run.player.getUuid());
		run.player.clearStatusEffects();
		run.player.extinguish();
		run.player.setVelocity(Vec3d.ZERO);
		run.player.velocityModified = true;
	}

	private static void finish(Run run, boolean aborted) {
		active = null;
		if (run.current != null && run.measuring) {
			cleanup(run);
		}
		run.world.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(run.griefing, run.world.getServer());
		removeCapture();
		if (!run.player.isDisconnected() && TransformationManager.get(run.player).isTransformed()) {
			TransformationManager.revert(run.player, false);
		}
		Map<Verdict, Integer> counts = new java.util.EnumMap<>(Verdict.class);
		for (Outcome outcome : run.outcomes) {
			counts.merge(outcome.verdict(), 1, Integer::sum);
		}
		Path file = run.world.getServer().getRunDirectory().resolve(REPORT);
		try {
			Files.writeString(file, report(run, counts, aborted), StandardCharsets.UTF_8);
		} catch (IOException e) {
			KingdomOmnitrix.LOGGER.error("[Selbsttest] Bericht konnte nicht geschrieben werden: {}", file, e);
		}
		KingdomOmnitrix.LOGGER.info("[Selbsttest] Ende{}: {} Kraefte, {} — Bericht {}", aborted ? " (abgebrochen)" : "", run.outcomes.size(),
				counts, file.toAbsolutePath());
		if (!run.player.isDisconnected()) {
			run.player.sendMessage(Text.translatable("commands.kingdomomnitrix.selftest.done", run.outcomes.size(),
					counts.getOrDefault(Verdict.OK, 0), counts.getOrDefault(Verdict.WIRKUNGSLOS, 0), counts.getOrDefault(Verdict.ABGELEHNT, 0),
					counts.getOrDefault(Verdict.FEHLER, 0) + counts.getOrDefault(Verdict.ABSTURZ, 0), REPORT).formatted(Formatting.AQUA));
		}
	}

	private static String report(Run run, Map<Verdict, Integer> counts, boolean aborted) {
		StringBuilder out = new StringBuilder();
		out.append("# Kingdom Omnitrix — Selbsttest der Alien-Kraefte\n\n");
		out.append("Kraefte: ").append(run.outcomes.size()).append(" von ").append(run.total).append(aborted ? " (abgebrochen)" : "")
				.append(" · Fenster ").append(WINDOW).append(" Ticks · ").append(counts).append("\n\n");
		Identifier alien = null;
		for (Outcome outcome : run.outcomes) {
			if (!outcome.alienId().equals(alien)) {
				alien = outcome.alienId();
				out.append("\n## ").append(alien).append("\n\n| # | Kraft | Ausloeser | Stufe | Ergebnis | Messung |\n|---|---|---|---|---|---|\n");
			}
			AbilitySlot slot = outcome.slot();
			out.append("| ").append(outcome.index()).append(" | ").append(slot.type().getPath()).append(" | ")
					.append(slot.trigger().asString()).append(" | ").append(slot.unlockLevel()).append(" | ").append(outcome.verdict())
					.append(" | ").append(outcome.details().replace("|", "/")).append(" |\n");
			for (String line : outcome.log()) {
				out.append("|  |  |  |  | ↳ | ").append(line.replace("|", "/").replace("\n", " ")).append(" |\n");
			}
		}
		return out.toString();
	}
}
