package com.santiq.kingdomomnitrix.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/**
 * Ein NPC aus {@code data/<namespace>/kingdomomnitrix/npc/<name>.json}. Der Name kommt aus der Uebersetzung
 * {@code npc.<namespace>.<name>}; das Modell aus {@code geo|animations|textures/entity/npc/<model>}.
 * Welche Quests ein NPC vergibt, steht in den Quests selbst ({@code giver.npc}).
 */
public record NpcDefinition(Optional<String> model, List<Text> greeting, List<Text> farewell, float width, float height) {
	public static final Codec<NpcDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("model").forGetter(NpcDefinition::model),
			TextCodecs.CODEC.listOf().fieldOf("greeting").forGetter(NpcDefinition::greeting),
			TextCodecs.CODEC.listOf().optionalFieldOf("farewell", List.of()).forGetter(NpcDefinition::farewell),
			Codec.floatRange(0.2f, 4.0f).optionalFieldOf("width", 0.6f).forGetter(NpcDefinition::width),
			Codec.floatRange(0.2f, 6.0f).optionalFieldOf("height", 1.95f).forGetter(NpcDefinition::height)
	).apply(instance, NpcDefinition::new));

	public static Text name(Identifier id) {
		return Text.translatable("npc." + id.getNamespace() + "." + id.getPath());
	}

	/** Modellpfad relativ zu entity/npc/ (Standard: der Pfad der NPC-ID). */
	public String modelPath(Identifier id) {
		return model.orElse(id.getPath());
	}
}
