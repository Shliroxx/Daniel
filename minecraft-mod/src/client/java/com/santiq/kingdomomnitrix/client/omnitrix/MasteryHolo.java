package com.santiq.kingdomomnitrix.client.omnitrix;

import com.santiq.kingdomomnitrix.alien.AbilitySlot;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue;
import com.santiq.kingdomomnitrix.progression.AlienMastery;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Meisterschafts-Aufstieg als Omnitrix-Hologramm: „MEISTERSCHAFT ★5 · Heatblast · Neu: Flammenschild“ bzw. der neue
 * Bonus. Ausgeloest von den synchronisierten Meisterschaftsdaten (zeigt, was der Server gespeichert hat).
 */
public final class MasteryHolo {
	private static Map<Identifier, Integer> known;

	private MasteryHolo() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(MasteryHolo::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			known = null;
			return;
		}
		AlienMastery mastery = AlienMasteryManager.get(client.player);
		Map<Identifier, Integer> now = new HashMap<>();
		for (Identifier alien : mastery.experience().keySet()) {
			now.put(alien, mastery.level(alien));
		}
		if (known == null) {
			known = now;
			return;
		}
		for (Map.Entry<Identifier, Integer> entry : now.entrySet()) {
			int before = known.getOrDefault(entry.getKey(), 1);
			if (entry.getValue() > before) {
				show(client, entry.getKey(), entry.getValue());
			}
		}
		known = now;
	}

	private static void show(MinecraftClient client, Identifier alienId, int level) {
		Optional<AlienDefinition> alien = AlienRegistry.get(client.world.getRegistryManager(), alienId);
		int color = alien.map(AlienDefinition::color).orElse(0xFFC94A);
		MutableText footer = null;
		if (alien.isPresent()) {
			for (AbilitySlot slot : alien.get().abilities()) {
				if (slot.unlockLevel() == level) {
					footer = Text.translatable("holo.kingdomomnitrix.mastery_ability", Text.translatable(slot.translationKey()));
				}
			}
		}
		if (footer == null) {
			String perk = switch (level) {
				case AlienMastery.LEVEL_ENERGY_DISCOUNT, AlienMastery.LEVEL_TRANSFORM_HEAT, AlienMastery.LEVEL_HEALTH,
						AlienMastery.LEVEL_ACTIVE_HEAT, AlienMastery.LEVEL_MASTERED -> "holo.kingdomomnitrix.mastery_perk." + level;
				default -> "holo.kingdomomnitrix.mastery_perk.other";
			};
			footer = Text.translatable(perk);
		}
		OmnitrixHolo.show(new OmnitrixHolo.Message(Text.translatable("holo.kingdomomnitrix.mastery", level),
				TransformationManager.alienName(alienId).withColor(color).formatted(Formatting.BOLD), Optional.of(footer),
				Optional.of(alienId), 0xFFC94A, 4500L));
		OmnitrixFeedback.play(level >= AlienMastery.LEVEL_MASTERED ? OmnitrixCue.MASTER_CONTROL : OmnitrixCue.UNLOCK);
	}
}
