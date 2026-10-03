package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.party.PartyRules;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

/**
 * Registriert alle mitgelieferten Faehigkeits-Typen und stellt gemeinsame Hilfen bereit. Jeder Typ liest seine Zahlen
 * aus den JSON-Parametern und faellt auf Standardwerte zurueck.
 */
final class BuiltinAbilities {
	private BuiltinAbilities() {
	}

	static void register() {
		CreatureAbilities.register();
		MasteryAbilities.register();
		CannonboltAbilities.register();
		HeatblastAbilities.register();
		Xlr8Abilities.register();
		FourArmsAbilities.register();
		DiamondheadAbilities.register();
		GreyMatterAbilities.register();
		WildmuttAbilities.register();
		StinkflyAbilities.register();
		JetrayAbilities.register();
	}

	// Alien-Faehigkeiten mit eigenem System stehen je Alien in eigenen Klassen: HeatblastAbilities (Kernhitze),
	// Xlr8Abilities (Tempo), FourArmsAbilities (Wut), DiamondheadAbilities (Resonanz), GreyMatterAbilities
	// (Analyse-Datenbank), CannonboltAbilities (Schwung), JetrayAbilities (Ueberladung),
	// WildmuttAbilities (Jagd), StinkflyAbilities (Toxin-Schichten).

	// --- Hilfen ----------------------------------------------------------------------------------

	static List<LivingEntity> livingAround(AbilityContext ctx, double radius) {
		ServerPlayerEntity player = ctx.player();
		double radiusSq = radius * radius;
		return ctx.world().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
				e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= radiusSq && PartyRules.canHarm(player, e));
	}

	/** Blickrichtung ohne Neigung; faellt bei senkrechtem Blick auf die Koerperausrichtung zurueck. */
	static Vec3d horizontalLook(ServerPlayerEntity player) {
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d flat = new Vec3d(look.x, 0.0, look.z);
		if (flat.lengthSquared() < 1.0E-4) {
			float yaw = player.getBodyYaw() * ((float) Math.PI / 180.0f);
			return new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
		}
		return flat.normalize();
	}

	/** Setzt die Spielergeschwindigkeit serverseitig und schickt sie an den Client (der die Bewegung rechnet). */
	static void launch(ServerPlayerEntity player, double x, double y, double z) {
		player.setVelocity(x, y, z);
		player.velocityModified = true;
		player.fallDistance = 0.0f;
	}

	static void sound(AbilityContext ctx, SoundEvent sound, float volume, float pitch) {
		ServerWorld world = ctx.world();
		ServerPlayerEntity player = ctx.player();
		world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
