package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * Zeitplan fuer Kraefte, die ueber mehrere Ticks laufen (Flammenwerfer, Wirbel, Salven, Regen …), damit nicht jede
 * Faehigkeit eigene Listen und Tick-Schleifen braucht. Ein Schritt laeuft alle {@code interval} Ticks, bis die Dauer um
 * ist oder er {@code false} liefert. Endet die Verwandlung, wechselt das Alien oder verlaesst der Spieler den Server,
 * bricht die Aufgabe ab — Kraefte wirken nie ueber die Verwandlung hinaus.
 */
public final class PowerTasks {
	/** Ein Schritt; {@code age} zaehlt ab 0. Rueckgabe {@code false} beendet die Aufgabe. */
	@FunctionalInterface
	public interface Step {
		boolean run(ServerWorld world, ServerPlayerEntity player, int age);
	}

	/** Abschluss (optional): laeuft einmal, wenn die Aufgabe regulaer oder durch Abbruch endet. */
	@FunctionalInterface
	public interface End {
		void run(ServerPlayerEntity player, boolean aborted);
	}

	private record Task(UUID owner, Identifier alien, long start, int duration, int interval, Step step, End end) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	/** Schutz gegen Endlosschleifen durch fehlerhafte Daten */
	private static final int MAX_TASKS = 2048;

	private PowerTasks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PowerTasks::tick);
	}

	/** Startet eine Aufgabe fuer das aktive Alien des Spielers; der erste Schritt laeuft im naechsten Tick (age 0). */
	public static void start(ServerPlayerEntity player, int duration, int interval, Step step) {
		start(player, duration, interval, step, null);
	}

	public static void start(ServerPlayerEntity player, int duration, int interval, Step step, End end) {
		Identifier alien = TransformationManager.get(player).activeAlien().orElse(null);
		if (alien == null || TASKS.size() >= MAX_TASKS) {
			return;
		}
		TASKS.add(new Task(player.getUuid(), alien, player.getServerWorld().getTime() + 1, Math.max(1, duration), Math.max(1, interval), step, end));
	}

	/** Laufende Aufgaben eines Spielers (z. B. damit eine Kraft nicht doppelt laeuft). */
	public static int count(ServerPlayerEntity player) {
		int n = 0;
		for (Task task : TASKS) {
			if (task.owner().equals(player.getUuid())) {
				n++;
			}
		}
		return n;
	}

	private static void tick(MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}
		// Kopie: Schritte duerfen neue Aufgaben starten
		List<Task> snapshot = new ArrayList<>(TASKS);
		List<Task> finished = new ArrayList<>();
		for (Task task : snapshot) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(task.owner());
			if (player == null || !player.isAlive()
					|| !TransformationManager.get(player).activeAlien().filter(task.alien()::equals).isPresent()) {
				finished.add(task);
				end(task, player, true);
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long age = world.getTime() - task.start();
			if (age < 0) {
				continue;
			}
			boolean keep = true;
			if (age % task.interval() == 0) {
				try {
					keep = task.step().run(world, player, (int) age);
				} catch (RuntimeException e) {
					KingdomOmnitrix.LOGGER.error("Kraft-Aufgabe von {} ist abgestuerzt", task.alien(), e);
					keep = false;
				}
			}
			if (!keep || age >= task.duration()) {
				finished.add(task);
				end(task, player, false);
			}
		}
		TASKS.removeAll(finished);
	}

	private static void end(Task task, ServerPlayerEntity player, boolean aborted) {
		if (task.end() != null && player != null) {
			try {
				task.end().run(player, aborted);
			} catch (RuntimeException e) {
				KingdomOmnitrix.LOGGER.error("Kraft-Abschluss von {} ist abgestuerzt", task.alien(), e);
			}
		}
	}

	/** Alle Aufgaben eines Spielers entfernen (ohne Abschluss). */
	public static void clear(UUID owner) {
		Iterator<Task> it = TASKS.iterator();
		while (it.hasNext()) {
			if (it.next().owner().equals(owner)) {
				it.remove();
			}
		}
	}
}
