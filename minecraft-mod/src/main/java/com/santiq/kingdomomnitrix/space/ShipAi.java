package com.santiq.kingdomomnitrix.space;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Die Bord-KI der Aphelion: kurze Meldungen an alle an Bord (Chat mit eigenem Praefix und leisem Ton). */
public final class ShipAi {
	private ShipAi() {
	}

	public static void say(ServerPlayerEntity player, String key, Object... args) {
		Text message = Text.translatable("ship.kingdomomnitrix.ai.prefix").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
				.append(Text.literal(" "))
				.append(Text.translatable("ship.kingdomomnitrix.ai." + key, args).formatted(Formatting.WHITE));
		player.sendMessage(message, false);
		player.playSoundToPlayer(ModSounds.SHIP_AI, SoundCategory.NEUTRAL, 0.7f, 1.0f);
	}

	/** Meldung an alle Spieler im Schiff. */
	public static void sayToCrew(ShipEntity ship, String key, Object... args) {
		for (Entity passenger : ship.getPassengerList()) {
			if (passenger instanceof ServerPlayerEntity player) {
				say(player, key, args);
			}
		}
	}
}
