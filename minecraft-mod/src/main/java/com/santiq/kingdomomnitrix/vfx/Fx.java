package com.santiq.kingdomomnitrix.vfx;

import com.santiq.kingdomomnitrix.networking.FxPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * FX-Engine (Server-Seite): grosse, leuchtende Effekte statt kleiner Vanilla-Partikel. Jeder Aufruf schickt ein
 * {@link FxPayload} an alle Spieler in Sichtweite; gezeichnet wird auf dem Client (Strahlen, Schockwellen, animierte
 * Explosionen und Flammen, Lichtblitze, Brandflecken, Kamerastoss).
 */
public final class Fx {
	/** Sichtweite der Effekte in Bloecken */
	private static final double RANGE = 96.0;

	public enum Kind {
		/** animierter Feuerball, Blitz, Rauch, Funken; size = Radius */
		EXPLOSION,
		/** Ring am Boden, der sich ausbreitet; size = Endradius, duration = Ausbreitungszeit */
		SHOCKWAVE,
		/** Strahl von (x,y,z) nach (ax,ay,az); size = Breite */
		BEAM,
		/** kurzer Lichtblitz; size = Groesse */
		FLASH,
		/** Strom aus Flammenbildern in Richtung (ax,ay,az) (Laenge = Tempo); size = Streuung, count = Anzahl */
		FLAMES,
		/** Funkenstriche in alle Richtungen; size = Tempo, count = Anzahl */
		SPARKS,
		/** Splitter/Kristalle in alle Richtungen; size = Tempo, count = Anzahl */
		SHARDS,
		/** Rauchwolken; size = Groesse, count = Anzahl */
		SMOKE,
		/** Brandfleck/Riss am Boden; size = Radius, duration = Lebensdauer */
		SCORCH,
		/** Leuchten und aufsteigende Flammen um eine Entitaet; size = Radius, duration = Dauer */
		AURA,
		/** Kamerastoss fuer Spieler in der Naehe; size = Staerke 0..1, duration = Abklingzeit, count = Radius */
		SHAKE,
		/** Energiekugel, die sich zusammenzieht (Aufladen); size = Radius, duration = Ladezeit */
		CHARGE
	}

	private Fx() {
	}

	public static void send(ServerWorld world, Kind kind, Vec3d pos, Vec3d vec, int color, float size, int duration, int count,
			Entity attach) {
		FxPayload payload = new FxPayload(kind.ordinal(), pos.x, pos.y, pos.z, vec.x, vec.y, vec.z, color & 0xFFFFFF, size, duration, count,
				attach == null ? -1 : attach.getId());
		for (ServerPlayerEntity player : PlayerLookup.around(world, pos, RANGE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void explosion(ServerWorld world, Vec3d pos, int color, float radius) {
		send(world, Kind.EXPLOSION, pos, Vec3d.ZERO, color, radius, 0, 0, null);
	}

	public static void shockwave(ServerWorld world, Vec3d pos, int color, float radius, int ticks) {
		send(world, Kind.SHOCKWAVE, pos, Vec3d.ZERO, color, radius, ticks, 0, null);
	}

	public static void beam(ServerWorld world, Vec3d from, Vec3d to, int color, float width, int ticks) {
		send(world, Kind.BEAM, from, to, color, width, ticks, 0, null);
	}

	public static void flash(ServerWorld world, Vec3d pos, int color, float size) {
		send(world, Kind.FLASH, pos, Vec3d.ZERO, color, size, 6, 0, null);
	}

	/** Flammen-/Atemstrom: velocity = Richtung mal Tempo (Bloecke pro Tick). */
	public static void flames(ServerWorld world, Vec3d pos, Vec3d velocity, int color, float spread, int count) {
		send(world, Kind.FLAMES, pos, velocity, color, spread, 0, count, null);
	}

	public static void sparks(ServerWorld world, Vec3d pos, int color, float speed, int count) {
		send(world, Kind.SPARKS, pos, Vec3d.ZERO, color, speed, 0, count, null);
	}

	public static void shards(ServerWorld world, Vec3d pos, int color, float speed, int count) {
		send(world, Kind.SHARDS, pos, Vec3d.ZERO, color, speed, 0, count, null);
	}

	public static void smoke(ServerWorld world, Vec3d pos, int color, float size, int count) {
		send(world, Kind.SMOKE, pos, Vec3d.ZERO, color, size, 0, count, null);
	}

	public static void scorch(ServerWorld world, Vec3d pos, float radius, int ticks) {
		send(world, Kind.SCORCH, pos, Vec3d.ZERO, 0x000000, radius, ticks, 0, null);
	}

	public static void aura(ServerWorld world, Entity entity, int color, float radius, int ticks) {
		send(world, Kind.AURA, entity.getPos(), Vec3d.ZERO, color, radius, ticks, 0, entity);
	}

	public static void charge(ServerWorld world, Entity entity, int color, float radius, int ticks) {
		send(world, Kind.CHARGE, entity.getPos(), Vec3d.ZERO, color, radius, ticks, 0, entity);
	}

	public static void shake(ServerWorld world, Vec3d pos, float strength, int ticks, int radius) {
		send(world, Kind.SHAKE, pos, Vec3d.ZERO, 0, strength, ticks, radius, null);
	}
}
