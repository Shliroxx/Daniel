package com.santiq.kingdomomnitrix.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Ein Weltraumriss aus {@code data/<namespace>/kingdomomnitrix/space_route/<name>.json}: Position im All,
 * Ziel-Dimension, Farbe und Radius. Wer mit dem Raumschiff hineinfliegt, landet im Ziel; wer im Ziel hoch genug
 * fliegt, kommt neben diesem Riss wieder im All heraus. Name: Uebersetzung {@code route.<namespace>.<name>}.
 */
public record SpaceRoute(Identifier destination, Vec3d position, int color, float radius, Optional<List<Integer>> arrival) {
	public static final Codec<Vec3d> VEC_CODEC = Codec.DOUBLE.listOf(3, 3).xmap(
			list -> new Vec3d(list.get(0), list.get(1), list.get(2)), vec -> List.of(vec.x, vec.y, vec.z));

	public static final Codec<SpaceRoute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("destination").forGetter(SpaceRoute::destination),
			VEC_CODEC.fieldOf("position").forGetter(SpaceRoute::position),
			Codec.INT.optionalFieldOf("color", 0x9B6BFF).forGetter(SpaceRoute::color),
			Codec.floatRange(3.0f, 64.0f).optionalFieldOf("radius", 10.0f).forGetter(SpaceRoute::radius),
			Codec.INT.listOf(2, 2).optionalFieldOf("arrival").forGetter(SpaceRoute::arrival)
	).apply(instance, SpaceRoute::new));

	public RegistryKey<World> destinationKey() {
		return RegistryKey.of(RegistryKeys.WORLD, destination);
	}

	public static Text name(Identifier routeId) {
		return Text.translatable("route." + routeId.getNamespace() + "." + routeId.getPath());
	}

	/** Austrittspunkt im All beim Verlassen des Ziels: 40 Bloecke vor dem Riss in Richtung Weltraum-Mitte. */
	public Vec3d exitPoint() {
		Vec3d toCenter = new Vec3d(-position.x, 0, -position.z);
		Vec3d direction = toCenter.lengthSquared() < 1.0 ? new Vec3d(0, 0, 1) : toCenter.normalize();
		return position.add(direction.multiply(radius + 30.0));
	}
}
