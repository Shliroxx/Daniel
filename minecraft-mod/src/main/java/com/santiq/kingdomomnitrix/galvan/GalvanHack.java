package com.santiq.kingdomomnitrix.galvan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.omnitrix.MasterControlProgress;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixOs;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Omnitrix-Hack durch Grey Matter. Zeit als Grey Matter ist Forschungszeit ({@link State#ticks}, dauerhaft); nach
 * {@link #THRESHOLDS} wird die naechste Hack-Stufe <i>verfuegbar</i> und im Galvan-Labor gestartet: 10 s still stehen
 * (Bewegung oder Treffer brechen ab), dann ist sie dauerhaft eingespielt.
 *
 * <ol>
 *   <li>Timer-Hack: Verwandlungen dauern {@link #DURATION_BONUS} laenger.</li>
 *   <li>Lade-Hack: Nachladen {@link #RECHARGE_BONUS} kuerzer.</li>
 *   <li>Zugangs-Hack: Master Control wird freigeschaltet (Code 10000), dazu +10 % Dauer.</li>
 * </ol>
 * Die Wirkung liest {@link OmnitrixCore#durationFactor}/{@link OmnitrixCore#cooldownFactor} ueber {@link #durationFactor}
 * und {@link #cooldownFactor}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GalvanHack {
	public static final int MAX_LEVEL = 3;
	/** Forschungszeit (Ticks als Grey Matter) fuer Stufe 1, 2, 3: 5, 15, 30 Minuten */
	public static final int[] THRESHOLDS = {5 * 60 * 20, 15 * 60 * 20, 30 * 60 * 20};
	public static final int HACK_TICKS = 200;
	/** Wirkung je Stufe (Omnitrix-OS-Meldung) */
	private static final String[] EFFECT_KEYS = {"holo.kingdomomnitrix.galvan.hack_1", "holo.kingdomomnitrix.galvan.hack_2",
			"holo.kingdomomnitrix.galvan.hack_3"};
	public static final float DURATION_BONUS = 0.2f;
	public static final float RECHARGE_BONUS = 0.2f;
	public static final float ACCESS_DURATION_BONUS = 0.1f;
	/** Forschungszeit wird in diesen Schritten gutgeschrieben (weniger Sync-Verkehr) */
	private static final int STEP = 20;
	private static final double MAX_MOVE = 0.6;
	private static final Identifier GREY_MATTER = KingdomOmnitrix.id("grey_matter");

	/** Forschungszeit und eingespielte Stufe */
	public record State(int ticks, int level) {
		public static final State EMPTY = new State(0, 0);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("ticks", 0).forGetter(State::ticks),
				Codec.intRange(0, MAX_LEVEL).optionalFieldOf("level", 0).forGetter(State::level)
		).apply(instance, State::new));

		/** Stufen, die die Forschungszeit schon erlaubt (eingespielt oder nicht). */
		public int available() {
			int n = 0;
			while (n < MAX_LEVEL && ticks >= THRESHOLDS[n]) {
				n++;
			}
			return n;
		}

		public boolean canHack() {
			return level < available();
		}

		/** Fortschritt 0..1 zur naechsten Stufe. */
		public float progress() {
			if (level >= MAX_LEVEL) {
				return 1.0f;
			}
			int from = level == 0 ? 0 : THRESHOLDS[level - 1];
			int to = THRESHOLDS[level];
			return Math.min(1.0f, Math.max(0.0f, (ticks - from) / (float) (to - from)));
		}
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(KingdomOmnitrix.id("galvan_hack"),
			builder -> builder.persistent(State.CODEC).initializer(() -> State.EMPTY).copyOnDeath()
					.syncWith(PacketCodecs.codec(State.CODEC), AttachmentSyncPredicate.targetOnly()));

	/** laufende Hacks: Start-Tick und Standort */
	private record Running(long start, Vec3d pos) {
	}

	private static final Map<UUID, Running> RUNNING = new HashMap<>();

	private GalvanHack() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GalvanHack::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RUNNING.remove(handler.getPlayer().getUuid());
		});
		// Treffer brechen den Hack ab
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayerEntity player && taken > 0.0f && RUNNING.remove(player.getUuid()) != null) {
				abort(player);
			}
		});
	}

	public static State state(PlayerEntity player) {
		return player.getAttachedOrElse(STATE, State.EMPTY);
	}

	public static float durationFactor(PlayerEntity player) {
		int level = state(player).level();
		return 1.0f + (level >= 1 ? DURATION_BONUS : 0.0f) + (level >= 3 ? ACCESS_DURATION_BONUS : 0.0f);
	}

	public static float cooldownFactor(PlayerEntity player) {
		return state(player).level() >= 2 ? 1.0f - RECHARGE_BONUS : 1.0f;
	}

	public static boolean isRunning(PlayerEntity player) {
		return RUNNING.containsKey(player.getUuid());
	}

	private static boolean isGreyMatter(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(GREY_MATTER::equals).isPresent();
	}

	/** Hack starten (aus dem Labor); false mit Meldung, wenn es nicht geht. */
	public static boolean start(ServerPlayerEntity player) {
		if (!isGreyMatter(player)) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.galvan_only").formatted(Formatting.RED), true);
			return false;
		}
		if (!state(player).canHack()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.galvan_hack_locked").formatted(Formatting.GRAY), true);
			return false;
		}
		RUNNING.put(player.getUuid(), new Running(player.getServerWorld().getTime(), player.getPos()));
		player.sendMessage(Text.translatable("message.kingdomomnitrix.galvan_hack_started").formatted(Formatting.GREEN), true);
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.6f);
		return true;
	}

	private static void tick(MinecraftServer server) {
		long tick = server.getTicks();
		if (tick % STEP == 0) {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (isGreyMatter(player)) {
					research(player);
				}
			}
		}
		if (RUNNING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Running>> it = RUNNING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Running> entry = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			Running running = entry.getValue();
			if (!isGreyMatter(player) || player.getPos().squaredDistanceTo(running.pos()) > MAX_MOVE * MAX_MOVE) {
				it.remove();
				abort(player);
				continue;
			}
			ServerWorld world = player.getServerWorld();
			long age = world.getTime() - running.start();
			// Hack-Funken am Handgelenk, immer dichter
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getBodyY(0.5), player.getZ(),
					1 + (int) (age / 40), 0.15, 0.15, 0.15, 0.05);
			if (age % 40 == 0) {
				int percent = (int) Math.min(100, age * 100 / HACK_TICKS);
				player.sendMessage(Text.translatable("message.kingdomomnitrix.galvan_hack_progress", percent).formatted(Formatting.GREEN), true);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.7f, 0.8f + age / 200.0f);
			}
			if (age >= HACK_TICKS) {
				it.remove();
				complete(player);
			}
		}
	}

	/** Forschungszeit gutschreiben; neue verfuegbare Stufe einmal melden. */
	private static void research(ServerPlayerEntity player) {
		State before = state(player);
		if (before.level() >= MAX_LEVEL && before.ticks() >= THRESHOLDS[MAX_LEVEL - 1]) {
			return;
		}
		State after = new State(Math.min(THRESHOLDS[MAX_LEVEL - 1], before.ticks() + STEP), before.level());
		player.setAttached(STATE, after);
		if (after.available() > before.available()) {
			OmnitrixOs.send(player, OmnitrixOs.Event.MASTER_CONTROL, Text.translatable("holo.kingdomomnitrix.galvan.hack_ready"),
					Optional.of(Text.translatable(EFFECT_KEYS[after.available() - 1])), Optional.empty());
		}
	}

	private static void abort(ServerPlayerEntity player) {
		player.sendMessage(Text.translatable("message.kingdomomnitrix.galvan_hack_aborted").formatted(Formatting.RED), true);
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0f, 0.5f);
	}

	private static void complete(ServerPlayerEntity player) {
		State state = state(player);
		int level = Math.min(MAX_LEVEL, state.level() + 1);
		player.setAttached(STATE, new State(state.ticks(), level));
		if (level >= 3 && !MasterControlProgress.unlocked(player)) {
			HeroDataAccess.update(player, data -> data.withFlag(MasterControlProgress.FLAG, true));
		}
		OmnitrixOs.send(player, OmnitrixOs.Event.MASTER_CONTROL, Text.translatable("holo.kingdomomnitrix.galvan.hack_done"),
				Optional.of(Text.translatable(EFFECT_KEYS[level - 1])), Optional.empty());
		OmnitrixCore.cue(player, OmnitrixCue.MASTER_CONTROL);
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getBodyY(0.5), player.getZ(), 60, 0.4, 0.4, 0.4, 0.3);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.5f);
	}
}
