package com.santiq.kingdomomnitrix.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Logbuch der Aphelion je Spieler: besuchte Planeten und Ausbaustufen des Schiffs (0–3).
 * Die Stufen gelten fuer jedes Schiff, das der Spieler fliegt (wie Ratchets Aphelion, die mit ihm reist).
 * Synchronisiert an den Spieler selbst (Galaxiekarte, Tempo im Cockpit).
 */
public final class ShipLog {
	public static final int MAX_LEVEL = 3;

	/** Ausbauten: Triebwerk (Tempo), Warp-Antrieb (Reichweite und Warpzeit), Bordkanone (Laser). */
	public enum Upgrade {
		ENGINE, WARP, CANNON;

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public String translationKey() {
			return switch (this) {
				case ENGINE -> "screen.kingdomomnitrix.galaxy.upgrade.engine";
				case WARP -> "screen.kingdomomnitrix.galaxy.upgrade.warp";
				case CANNON -> "screen.kingdomomnitrix.galaxy.upgrade.cannon";
			};
		}
	}

	public record State(Set<Identifier> visited, int engine, int warp, int cannon) {
		public static final State EMPTY = new State(Set.of(), 0, 0, 0);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.listOf().xmap(list -> (Set<Identifier>) Set.copyOf(list), List::copyOf)
						.optionalFieldOf("visited", Set.of()).forGetter(State::visited),
				Codec.intRange(0, MAX_LEVEL).optionalFieldOf("engine", 0).forGetter(State::engine),
				Codec.intRange(0, MAX_LEVEL).optionalFieldOf("warp", 0).forGetter(State::warp),
				Codec.intRange(0, MAX_LEVEL).optionalFieldOf("cannon", 0).forGetter(State::cannon)
		).apply(instance, State::new));

		public int level(Upgrade upgrade) {
			return switch (upgrade) {
				case ENGINE -> engine;
				case WARP -> warp;
				case CANNON -> cannon;
			};
		}

		public State withLevel(Upgrade upgrade, int level) {
			int value = Math.max(0, Math.min(MAX_LEVEL, level));
			return switch (upgrade) {
				case ENGINE -> new State(visited, value, warp, cannon);
				case WARP -> new State(visited, engine, value, cannon);
				case CANNON -> new State(visited, engine, warp, value);
			};
		}

		public State visit(Identifier planet) {
			if (visited.contains(planet)) {
				return this;
			}
			Set<Identifier> copy = new HashSet<>(visited);
			copy.add(planet);
			return new State(Set.copyOf(copy), engine, warp, cannon);
		}

		/** Hoechsttempo-Faktor durch das Triebwerk: +15 % je Stufe. */
		public double speedFactor() {
			return 1.0 + 0.15 * engine;
		}

		/** Warp-Dauer in Ticks: 6 s, je Warp-Stufe 1 s kuerzer. */
		public int warpTicks() {
			return 120 - 20 * warp;
		}
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(KingdomOmnitrix.id("ship_log"), builder -> builder
			.persistent(State.CODEC)
			.initializer(() -> State.EMPTY)
			.copyOnDeath()
			.syncWith(PacketCodecs.codec(State.CODEC), AttachmentSyncPredicate.targetOnly()));

	private ShipLog() {
	}

	/** Klassenladen erzwingen (Anhang registrieren). */
	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Schiffs-Logbuch: {}", STATE.identifier());
	}

	public static State get(PlayerEntity player) {
		return player.getAttachedOrElse(STATE, State.EMPTY);
	}

	public static State update(ServerPlayerEntity player, UnaryOperator<State> change) {
		State next = change.apply(get(player));
		player.setAttached(STATE, next);
		return next;
	}

	/** Preis der naechsten Stufe: Bolts und Raritanium. */
	public static int[] price(int nextLevel) {
		return switch (nextLevel) {
			case 1 -> new int[] {1500, 2};
			case 2 -> new int[] {4000, 5};
			default -> new int[] {9000, 10};
		};
	}
}
