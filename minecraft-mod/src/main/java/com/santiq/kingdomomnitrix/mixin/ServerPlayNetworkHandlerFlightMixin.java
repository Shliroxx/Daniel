package com.santiq.kingdomomnitrix.mixin;

import com.santiq.kingdomomnitrix.alien.FlightStyle;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Alien-Schubflug (bis 3,4 Bloecke je Tick) bekommt bei der „moved too quickly“-Pruefung des Servers dieselbe
 * Toleranz wie der Elytra-Flug — sonst reisst ein kurzer Ruckler des Clients (mehrere Ticks in einem Paket) den
 * fliegenden Spieler zurueck. Nur fuer Spieler, die gerade als Flug-Alien fliegen.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerFlightMixin {
	@Redirect(method = "onPlayerMove", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayerEntity;isFallFlying()Z"))
	private boolean kingdomomnitrix$alienFlightTolerance(ServerPlayerEntity player, PlayerMoveC2SPacket packet) {
		return player.isFallFlying() || player.getAbilities().flying && !player.isCreative() && FlightStyle.of(player).isPresent();
	}
}
