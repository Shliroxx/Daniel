package com.santiq.kingdomomnitrix.omnitrix;

import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Code-Eingabe am Omnitrix (Server): prueft den Code gegen das Datenpaket ({@link OmnitrixCode}) und fuehrt die Aktion
 * aus. Schutz gegen Durchprobieren: {@link #MAX_WRONG} falsche Codes in {@link #WRONG_WINDOW_TICKS} sperren das
 * Geraet kurz. Selbstzerstoerung nur mit der Spielregel {@code kingdomomnitrixSelfDestruct} (Standard: aus).
 */
public final class OmnitrixCodes {
	public static final GameRules.Key<GameRules.BooleanRule> SELF_DESTRUCT_ALLOWED = GameRuleRegistry.register(
			"kingdomomnitrixSelfDestruct", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(false));
	/** Story-Flag, das Master Control freischaltet (vergibt Phase K / Befehl). */
	public static final String MASTER_CONTROL_FLAG = MasterControlProgress.FLAG;

	static final int MAX_WRONG = 3;
	static final long WRONG_WINDOW_TICKS = 600L;
	static final long WRONG_LOCK_TICKS = 200L;
	static final long VENT_LOCK_TICKS = 600L;
	static final int SELF_DESTRUCT_SECONDS = 10;
	static final long SELF_DESTRUCT_LOCK_TICKS = 12_000L;
	static final float SELF_DESTRUCT_POWER = 4.0f;

	private static final Map<UUID, Deque<Long>> WRONG = new HashMap<>();
	private static final Map<UUID, Long> SELF_DESTRUCT_AT = new HashMap<>();

	private OmnitrixCodes() {
	}

	static void register() {
		OmnitrixCode.register();
		ServerTickEvents.END_SERVER_TICK.register(OmnitrixCodes::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			WRONG.remove(handler.player.getUuid());
			SELF_DESTRUCT_AT.remove(handler.player.getUuid());
		});
	}

	/** Eingabe vom Client (oder Befehl). */
	public static void enter(ServerPlayerEntity player, String entered) {
		if (!OmnitrixItem.hasOmnitrix(player) && !TransformationManager.get(player).isTransformed()) {
			return;
		}
		long now = player.getWorld().getTime();
		if (OmnitrixCore.state(player).isLocked(now)) {
			OmnitrixCore.refuse(player, OmnitrixCore.Refusal.LOCKED);
			return;
		}
		Optional<OmnitrixCode> code = OmnitrixCode.find(player.getServerWorld().getRegistryManager(), entered);
		if (code.isEmpty()) {
			wrong(player, now);
			return;
		}
		WRONG.remove(player.getUuid());
		switch (code.get().action()) {
			case DIAGNOSTICS -> diagnostics(player, now);
			case EMERGENCY_VENT -> emergencyVent(player);
			case RANDOM -> random(player);
			case RECALIBRATE -> recalibrate(player, code.get().profile().orElse(Identifier.of("kingdomomnitrix", "recalibrated")));
			case MASTER_CONTROL -> masterControl(player);
			case SELF_DESTRUCT -> selfDestruct(player, now);
		}
	}

	private static void wrong(ServerPlayerEntity player, long now) {
		Deque<Long> times = WRONG.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
		while (!times.isEmpty() && now - times.peekFirst() > WRONG_WINDOW_TICKS) {
			times.pollFirst();
		}
		times.addLast(now);
		OmnitrixCore.cue(player, OmnitrixCue.ERROR);
		if (times.size() >= MAX_WRONG) {
			times.clear();
			OmnitrixCore.lock(player, WRONG_LOCK_TICKS, false);
			OmnitrixOs.send(player, OmnitrixOs.Event.LOCKED, Text.translatable("holo.kingdomomnitrix.code.too_many", WRONG_LOCK_TICKS / 20));
			return;
		}
		OmnitrixOs.send(player, OmnitrixOs.Event.REFUSED, Text.translatable("holo.kingdomomnitrix.code.invalid"),
				Optional.of(Text.translatable("holo.kingdomomnitrix.code.tries_left", MAX_WRONG - times.size())), Optional.empty());
	}

	private static void diagnostics(ServerPlayerEntity player, long now) {
		OmnitrixState state = OmnitrixCore.state(player);
		MasterControlProgress.Progress progress = MasterControlProgress.progress(player);
		long failsafe = Math.max(0L, state.failsafeReadyAt() - now);
		Text body = Text.translatable("holo.kingdomomnitrix.code.diagnostics", Math.round(OmnitrixCore.heat(player) * 100),
				Text.translatable("omnitrix_profile." + state.profile().getNamespace() + "." + state.profile().getPath()));
		Text footer = Text.translatable(failsafe > 0 ? "holo.kingdomomnitrix.code.diag_failsafe_wait" : "holo.kingdomomnitrix.code.diag_failsafe_ready",
				(failsafe + 1199) / 1200)
				.append(" · ")
				.append(Text.translatable(state.masterControl() ? "holo.kingdomomnitrix.code.diag_mc_on"
						: HeroDataAccess.get(player).hasFlag(MASTER_CONTROL_FLAG) ? "holo.kingdomomnitrix.code.diag_mc_ready"
						: "holo.kingdomomnitrix.code.diag_mc_progress", Math.min(progress.reached(), progress.needed()),
						progress.needed(), progress.level()));
		OmnitrixOs.send(player, OmnitrixOs.Event.DIAGNOSTICS, body, Optional.of(footer), Optional.empty());
		OmnitrixCore.cue(player, OmnitrixCue.SELECT);
	}

	/** Notkuehlung: Hitze 0, Ueberhitzung aufgehoben, dafuer 30 s gesperrt. Als Alien nicht moeglich. */
	private static void emergencyVent(ServerPlayerEntity player) {
		if (TransformationManager.get(player).isTransformed()) {
			OmnitrixOs.send(player, OmnitrixOs.Event.REFUSED, Text.translatable("holo.kingdomomnitrix.code.vent_transformed"));
			OmnitrixCore.cue(player, OmnitrixCue.ERROR);
			return;
		}
		OmnitrixCore.vent(player);
		OmnitrixCore.lock(player, VENT_LOCK_TICKS, false);
		OmnitrixOs.send(player, OmnitrixOs.Event.COOLED, Text.translatable("holo.kingdomomnitrix.code.vented"),
				Optional.of(Text.translatable("holo.kingdomomnitrix.os.locked_for", VENT_LOCK_TICKS / 20)), Optional.empty());
	}

	/** Zufallsmodus: ein zufaelliges freigeschaltetes Alien ueber den normalen Weg (Hitze, Nachladen, Platz gelten). */
	private static void random(ServerPlayerEntity player) {
		List<Identifier> aliens = new ArrayList<>();
		for (Identifier id : HeroDataAccess.get(player).unlockedAliens()) {
			if (AlienRegistry.get(player.getServerWorld().getRegistryManager(), id).isPresent()
					&& TransformationManager.get(player).activeAlien().filter(id::equals).isEmpty()) {
				aliens.add(id);
			}
		}
		if (aliens.isEmpty()) {
			OmnitrixOs.send(player, OmnitrixOs.Event.REFUSED, Text.translatable("holo.kingdomomnitrix.code.random_none"));
			return;
		}
		aliens.sort(null);
		Identifier pick = aliens.get(player.getRandom().nextInt(aliens.size()));
		OmnitrixCore.cue(player, OmnitrixCue.SELECT);
		if (TransformationManager.get(player).isTransformed()) {
			TransformationManager.quickChange(player, pick);
		} else {
			TransformationManager.transform(player, pick, false);
		}
	}

	private static void recalibrate(ServerPlayerEntity player, Identifier target) {
		Identifier current = OmnitrixCore.state(player).profile();
		Identifier next = current.equals(target) ? OmnitrixState.DEFAULT_PROFILE : target;
		if (OmnitrixCore.profile(player.getServerWorld().getRegistryManager(), next) == OmnitrixProfile.DEFAULT
				&& !next.equals(OmnitrixState.DEFAULT_PROFILE)) {
			OmnitrixOs.send(player, OmnitrixOs.Event.REFUSED, Text.translatable("holo.kingdomomnitrix.code.no_profile"));
			return;
		}
		OmnitrixCore.setProfile(player, next);
		OmnitrixOs.send(player, OmnitrixOs.Event.RECALIBRATED,
				Text.translatable("omnitrix_profile." + next.getNamespace() + "." + next.getPath()));
		OmnitrixCore.cue(player, OmnitrixCue.CONFIRM);
	}

	private static void masterControl(ServerPlayerEntity player) {
		if (!HeroDataAccess.get(player).hasFlag(MASTER_CONTROL_FLAG)) {
			MasterControlProgress.Progress progress = MasterControlProgress.progress(player);
			OmnitrixOs.send(player, OmnitrixOs.Event.REFUSED, Text.translatable("holo.kingdomomnitrix.code.mc_locked"),
					Optional.of(Text.translatable("holo.kingdomomnitrix.code.diag_mc_progress", progress.reached(), progress.needed(),
							progress.level())), Optional.empty());
			OmnitrixCore.cue(player, OmnitrixCue.ERROR);
			return;
		}
		OmnitrixCore.setMasterControl(player, !OmnitrixCore.state(player).masterControl());
	}

	private static void selfDestruct(ServerPlayerEntity player, long now) {
		UUID id = player.getUuid();
		if (SELF_DESTRUCT_AT.remove(id) != null) {
			OmnitrixOs.send(player, OmnitrixOs.Event.UNLOCKED, Text.translatable("holo.kingdomomnitrix.code.sd_aborted"));
			OmnitrixCore.cue(player, OmnitrixCue.READY);
			return;
		}
		if (!player.getServerWorld().getGameRules().getBoolean(SELF_DESTRUCT_ALLOWED)) {
			OmnitrixOs.send(player, OmnitrixOs.Event.REFUSED, Text.translatable("holo.kingdomomnitrix.code.sd_disabled"));
			OmnitrixCore.cue(player, OmnitrixCue.ERROR);
			return;
		}
		SELF_DESTRUCT_AT.put(id, now + SELF_DESTRUCT_SECONDS * 20L);
		announceCountdown(player, SELF_DESTRUCT_SECONDS);
	}

	private static void announceCountdown(ServerPlayerEntity player, int seconds) {
		OmnitrixOs.send(player, OmnitrixOs.Event.SELF_DESTRUCT, Text.translatable("holo.kingdomomnitrix.code.sd_countdown", seconds),
				Optional.of(Text.translatable("holo.kingdomomnitrix.code.sd_abort_hint")), Optional.empty());
		OmnitrixCore.cue(player, OmnitrixCue.WARNING);
	}

	private static void tick(MinecraftServer server) {
		if (SELF_DESTRUCT_AT.isEmpty()) {
			return;
		}
		SELF_DESTRUCT_AT.entrySet().removeIf(entry -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null || !player.isAlive()) {
				return true;
			}
			long now = player.getWorld().getTime();
			long left = entry.getValue() - now;
			if (left > 0) {
				if (left % 20 == 0) {
					announceCountdown(player, (int) (left / 20));
				}
				return false;
			}
			detonate(player);
			return true;
		});
	}

	/** Explosion am Spieler (keine Blockschaeden), danach Zwangs-Rueckverwandlung und lange Sperre. */
	private static void detonate(ServerPlayerEntity player) {
		player.getServerWorld().createExplosion(null, player.getX(), player.getBodyY(0.5), player.getZ(), SELF_DESTRUCT_POWER,
				World.ExplosionSourceType.NONE);
		if (TransformationManager.get(player).isTransformed()) {
			TransformationManager.revert(player, true);
		}
		OmnitrixCore.lock(player, SELF_DESTRUCT_LOCK_TICKS, false);
		OmnitrixOs.send(player, OmnitrixOs.Event.DETONATED, Text.translatable("holo.kingdomomnitrix.code.sd_done", SELF_DESTRUCT_LOCK_TICKS / 1200));
		player.sendMessage(Text.translatable("holo.kingdomomnitrix.code.sd_done", SELF_DESTRUCT_LOCK_TICKS / 1200).formatted(Formatting.RED), false);
	}
}
