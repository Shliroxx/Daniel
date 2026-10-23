package com.santiq.kingdomomnitrix.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.registry.ModStructures;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

/**
 * Siedlung der Petrosapiens (Diamondheads Volk) auf Petropia: Platz mit Kristall-Obelisk in der Mitte, ringsum
 * Kuppelhuetten aus glattem Fels mit Kristalldach, Kieswege zum Platz, Kristall-Laternen. Als Ruine
 * ({@code ruin = true}): weniger, eingestuerzte Huetten, Schutt, ein umgestuerzter Obelisk — dafuer mehr Beute.
 * Steht nur auf halbwegs ebenem Grund; die Gelaendeanpassung (beard_thin) glaettet den Rest.
 */
public class PetrosapienSettlement extends Structure {
	public static final MapCodec<PetrosapienSettlement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(configCodecBuilder(i),
			Codec.BOOL.fieldOf("ruin").forGetter(s -> s.ruin)).apply(i, PetrosapienSettlement::new));

	/** Groesster erlaubter Hoehenunterschied im Bauplatz (Blöcke). */
	private static final int MAX_SLOPE = 10;

	private final boolean ruin;

	public PetrosapienSettlement(Config config, boolean ruin) {
		super(config);
		this.ruin = ruin;
	}

	@Override
	protected Optional<StructurePosition> getStructurePosition(Context context) {
		int cx = context.chunkPos().getCenterX();
		int cz = context.chunkPos().getCenterZ();
		int cy = ground(context, cx, cz);
		int min = cy;
		int max = cy;
		for (int[] d : new int[][] {{18, 0}, {-18, 0}, {0, 18}, {0, -18}, {13, 13}, {-13, -13}, {13, -13}, {-13, 13}}) {
			int h = ground(context, cx + d[0], cz + d[1]);
			min = Math.min(min, h);
			max = Math.max(max, h);
		}
		if (max - min > MAX_SLOPE || cy < context.world().getBottomY() + 40) {
			return Optional.empty();
		}
		BlockPos center = new BlockPos(cx, cy, cz);
		return Optional.of(new StructurePosition(center, collector -> {
			Random random = context.random();
			List<PetrosapienPiece> pieces = new ArrayList<>();
			pieces.add(PetrosapienPiece.plaza(center, ruin, random.nextLong()));
			int huts = ruin ? 3 + random.nextInt(2) : 5 + random.nextInt(3);
			float angle0 = random.nextFloat() * MathHelper.TAU;
			for (int i = 0; i < huts; i++) {
				float angle = angle0 + i * MathHelper.TAU / huts + (random.nextFloat() - 0.5f) * 0.4f;
				int distance = 15 + random.nextInt(6);
				int hx = cx + Math.round(MathHelper.cos(angle) * distance);
				int hz = cz + Math.round(MathHelper.sin(angle) * distance);
				int hy = MathHelper.clamp(ground(context, hx, hz), cy - 4, cy + 4);
				int radius = 3 + random.nextInt(2);
				pieces.add(PetrosapienPiece.hut(new BlockPos(hx, hy, hz), radius, center, ruin, random.nextLong()));
				pieces.add(PetrosapienPiece.path(center, new BlockPos(hx, hy, hz), radius, random.nextLong()));
			}
			pieces.forEach(collector::addPiece);
		}));
	}

	private static int ground(Context context, int x, int z) {
		return context.chunkGenerator().getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
	}

	@Override
	public StructureType<?> getType() {
		return ModStructures.PETROSAPIEN_SETTLEMENT;
	}
}
