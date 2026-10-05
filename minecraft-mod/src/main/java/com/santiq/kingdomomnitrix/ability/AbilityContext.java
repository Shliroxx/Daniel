package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.alien.AbilitySlot;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/** Alles, was eine Faehigkeit beim Ausloesen braucht, plus Rueckkanal fuer Nebenwirkungen auf den Zustand. */
public final class AbilityContext {
	private final ServerPlayerEntity player;
	private final ServerWorld world;
	private final Identifier alienId;
	private final AlienDefinition alien;
	private final AbilitySlot slot;
	private int invulnerabilityTicks;

	public AbilityContext(ServerPlayerEntity player, ServerWorld world, Identifier alienId, AlienDefinition alien, AbilitySlot slot) {
		this.player = player;
		this.world = world;
		this.alienId = alienId;
		this.alien = alien;
		this.slot = slot;
	}

	public ServerPlayerEntity player() {
		return player;
	}

	public ServerWorld world() {
		return world;
	}

	public Identifier alienId() {
		return alienId;
	}

	public AlienDefinition alien() {
		return alien;
	}

	public AbilitySlot slot() {
		return slot;
	}

	public double param(String key, double fallback) {
		return slot.param(key, fallback);
	}

	/** Macht den Spieler fuer die angegebene Zeit unverwundbar (z. B. Ausweichen). */
	public void grantInvulnerability(int ticks) {
		invulnerabilityTicks = Math.max(invulnerabilityTicks, ticks);
	}

	public int invulnerabilityTicks() {
		return invulnerabilityTicks;
	}
}
