package com.santiq.kingdomomnitrix.ability.passive;

import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.PassiveSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/** Alles, was eine passive Kraft braucht. */
public record PassiveContext(ServerPlayerEntity player, Identifier alienId, AlienDefinition alien, PassiveSlot slot) {
	public ServerWorld world() {
		return player.getServerWorld();
	}

	public long now() {
		return player.getServerWorld().getTime();
	}

	public double param(String key, double fallback) {
		return slot.param(key, fallback);
	}
}
