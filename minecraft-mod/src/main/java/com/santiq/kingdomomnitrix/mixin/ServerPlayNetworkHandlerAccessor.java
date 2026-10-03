package com.santiq.kingdomomnitrix.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zugriff auf den Schwebe-Zaehler des Servers. Ohne Zuruecksetzen wuerden Spieler, die am Swingshot haengen
 * oder hochgezogen werden (oder im Raumschiff schweben), auf Servern ohne {@code allow-flight} nach 4 Sekunden
 * wegen "Fliegens" gekickt.
 */
@Mixin(ServerPlayNetworkHandler.class)
public interface ServerPlayNetworkHandlerAccessor {
	@Accessor("floatingTicks")
	void kingdomomnitrix$setFloatingTicks(int ticks);

	/** Gleicher Zaehler fuer Fahrzeuge: ein schwebendes Raumschiff ist gewollt. */
	@Accessor("vehicleFloatingTicks")
	void kingdomomnitrix$setVehicleFloatingTicks(int ticks);
}
