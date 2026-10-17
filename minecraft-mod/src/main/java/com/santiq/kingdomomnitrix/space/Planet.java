package com.santiq.kingdomomnitrix.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Ein Planet der Galaxiekarte aus {@code data/<namespace>/kingdomomnitrix/planet/<name>.json}.
 *
 * <ul>
 *   <li>{@code dimension}: Welt, in der die Aphelion landet</li>
 *   <li>{@code system}: Sternsystem (Uebersetzung {@code system.kingdomomnitrix.<system>}); fernere Systeme brauchen
 *       eine hoehere Warp-Stufe ({@code warp})</li>
 *   <li>{@code map}: Lage auf der Karte (0..1, 0..1), {@code color}: Planetenfarbe, {@code size}: Kreisgroesse</li>
 *   <li>{@code landing}: Landeplatz (x, z) — fehlt er, landet das Schiff am Weltspawn</li>
 *   <li>{@code hero_level}, {@code requires}: Freischaltung (Heldenstufe, vorher besuchter Planet)</li>
 * </ul>
 * Name und Beschreibung: {@code planet.<namespace>.<name>} und {@code planet.<namespace>.<name>.desc}.
 */
public record Planet(Identifier dimension, String system, List<Float> map, int color, float size, int order,
		Optional<List<Integer>> landing, int heroLevel, int warp, Optional<Identifier> requires) {

	public static final Codec<Planet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("dimension").forGetter(Planet::dimension),
			Codec.STRING.fieldOf("system").forGetter(Planet::system),
			Codec.floatRange(0.0f, 1.0f).listOf(2, 2).fieldOf("map").forGetter(Planet::map),
			Codec.INT.optionalFieldOf("color", 0x9FD8FF).forGetter(Planet::color),
			Codec.floatRange(0.5f, 3.0f).optionalFieldOf("size", 1.0f).forGetter(Planet::size),
			Codec.INT.optionalFieldOf("order", 0).forGetter(Planet::order),
			Codec.INT.listOf(2, 2).optionalFieldOf("landing").forGetter(Planet::landing),
			Codec.intRange(1, 50).optionalFieldOf("hero_level", 1).forGetter(Planet::heroLevel),
			Codec.intRange(0, ShipLog.MAX_LEVEL).optionalFieldOf("warp", 0).forGetter(Planet::warp),
			Identifier.CODEC.optionalFieldOf("requires").forGetter(Planet::requires)
	).apply(instance, Planet::new));

	public RegistryKey<World> worldKey() {
		return RegistryKey.of(RegistryKeys.WORLD, dimension);
	}

	public float mapX() {
		return map.get(0);
	}

	public float mapY() {
		return map.get(1);
	}

	public static Text name(Identifier planet) {
		return Text.translatable("planet." + planet.getNamespace() + "." + planet.getPath());
	}

	public static Text description(Identifier planet) {
		return Text.translatable("planet." + planet.getNamespace() + "." + planet.getPath() + ".desc");
	}

	public Text systemName() {
		return Text.translatable("system.kingdomomnitrix." + system);
	}
}
