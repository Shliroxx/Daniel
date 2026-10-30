package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

/**
 * Modus eines Aliens (Datenfeld {@code traits.modes}) — eine Haltung, in der ein Kraft-Bauteil des Modells ausgefahren
 * ist und andere Werte gelten, wie in Alien Evolution: Diamondheads Kristallschwert (mehr Angriff, Reichweite, Schwung
 * trifft auch Nachbarn) und Kristallschild (immun gegen Geschosse und Explosionen, Stacheln verletzen Nahkaempfer,
 * dafuer langsamer). Ein Alien hat hoechstens einen aktiven Modus; Kraefte schalten ihn ({@link AlienModes}).
 *
 * <pre>
 * "modes": [{
 *   "id": "crystal_blade",                         // auch Name der Modell-Animationen mode_&lt;id&gt;_on/_off
 *   "name": "mode.kingdomomnitrix.crystal_blade",   // Uebersetzungsschluessel fuer die Meldung
 *   "attributes": [{"attribute": "minecraft:generic.attack_damage", "amount": 5}],
 *   "immunities": ["#minecraft:is_projectile"],     // Schadensarten (Tags), die im Modus nicht wirken
 *   "aura_damage": 4, "aura_radius": 1.75,         // Schaden je halbe Sekunde an Gegnern ringsum (Stacheln)
 *   "sweep": 0.6, "sweep_radius": 2.5,             // Nahkampf trifft Gegner vor dem Ziel mit diesem Anteil
 *   "harvest": "#minecraft:mineable/pickaxe",       // Bloecke, die der Modus wie ein Werkzeug abbaut (mit Drops)
 *   "break_speed": 8,                              // Abbautempo fuer diese Bloecke (Diamant-Spitzhacke = 8)
 *   "flight": "jet"                                // Flug im Modus (Grey Matters Galvan-Ruestung mit Jetpack)
 * }]
 * </pre>
 */
public record AlienMode(String id, String name, List<AlienDefinition.AttributeBonus> attributes, List<TagKey<DamageType>> immunities,
		float auraDamage, float auraRadius, float sweep, float sweepRadius, Optional<TagKey<Block>> harvest, float breakSpeed,
		Optional<FlightStyle> flight) {
	/** hoechstens so viele Werte je Modus (feste Modifier-IDs) */
	public static final int MAX_ATTRIBUTES = 8;

	public static final Codec<AlienMode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.string(1, 32).fieldOf("id").forGetter(AlienMode::id),
			Codec.STRING.fieldOf("name").forGetter(AlienMode::name),
			AlienDefinition.AttributeBonus.CODEC.listOf(0, MAX_ATTRIBUTES).optionalFieldOf("attributes", List.of()).forGetter(AlienMode::attributes),
			TagKey.codec(RegistryKeys.DAMAGE_TYPE).listOf().optionalFieldOf("immunities", List.of()).forGetter(AlienMode::immunities),
			Codec.floatRange(0.0f, 100.0f).optionalFieldOf("aura_damage", 0.0f).forGetter(AlienMode::auraDamage),
			Codec.floatRange(0.0f, 16.0f).optionalFieldOf("aura_radius", 0.0f).forGetter(AlienMode::auraRadius),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("sweep", 0.0f).forGetter(AlienMode::sweep),
			Codec.floatRange(0.0f, 8.0f).optionalFieldOf("sweep_radius", 0.0f).forGetter(AlienMode::sweepRadius),
			TagKey.codec(RegistryKeys.BLOCK).optionalFieldOf("harvest").forGetter(AlienMode::harvest),
			Codec.floatRange(1.0f, 64.0f).optionalFieldOf("break_speed", 1.0f).forGetter(AlienMode::breakSpeed),
			FlightStyle.CODEC.optionalFieldOf("flight").forGetter(AlienMode::flight)
	).apply(instance, AlienMode::new));

	public AlienMode {
		attributes = List.copyOf(attributes);
		immunities = List.copyOf(immunities);
	}

	/** Baut der Modus diesen Block wie das passende Werkzeug ab? */
	public boolean harvests(BlockState state) {
		return harvest.map(state::isIn).orElse(false);
	}

	public boolean isImmuneTo(DamageSource source) {
		for (TagKey<DamageType> tag : immunities) {
			if (source.isIn(tag)) {
				return true;
			}
		}
		return false;
	}
}
