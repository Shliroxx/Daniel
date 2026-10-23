package com.santiq.kingdomomnitrix.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;

/**
 * Kristallbuendel wie Diamondheads Kristalle ({@code vfx/CrystalGrowth.cluster}) als echte Bloecke: ein hoher, fast
 * senkrechter Mittelkristall und mehrere nach aussen gekippte Nebenkristalle, jeder ein schlanker, spitz zulaufender
 * Saeulenzug aus durchscheinendem Kristall. Der Fuss steckt im Boden; die Spitze bekommt einen Splitterblock.
 * Reichweite bleibt unter 16 Bloecken (Achse 13 + Versatz der Nebenkristalle), damit das Buendel in der Generierungsregion bleibt.
 */
public class CrystalBundleFeature extends Feature<CrystalBundleFeature.Config> {
	private static final double MAX_REACH = 13.0;

	public record Config(BlockStateProvider crystal, BlockStateProvider tip, IntProvider count, IntProvider height, float width, float lean)
			implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockStateProvider.TYPE_CODEC.fieldOf("crystal").forGetter(Config::crystal),
				BlockStateProvider.TYPE_CODEC.fieldOf("tip").forGetter(Config::tip),
				IntProvider.createValidatingCodec(1, 8).fieldOf("count").forGetter(Config::count),
				IntProvider.createValidatingCodec(2, 14).fieldOf("height").forGetter(Config::height),
				Codec.floatRange(0.5f, 3.0f).fieldOf("width").forGetter(Config::width),
				Codec.floatRange(0.0f, 0.8f).fieldOf("lean").forGetter(Config::lean)).apply(i, Config::new));
	}

	public CrystalBundleFeature(Codec<Config> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();
		Config config = context.getConfig();
		BlockState ground = world.getBlockState(origin.down());
		if (!ground.isOpaqueFullCube(world, origin.down()) || !world.getBlockState(origin).isAir() && !world.getBlockState(origin).isReplaceable()) {
			return false;
		}
		int count = config.count().get(random);
		int mainHeight = config.height().get(random);
		float yaw0 = random.nextFloat() * MathHelper.TAU;
		boolean placed = false;
		for (int i = 0; i < count; i++) {
			boolean center = i == 0;
			float yaw = yaw0 + i * MathHelper.TAU / Math.max(1, count - 1) + (random.nextFloat() - 0.5f) * 0.6f;
			float tilt = center ? config.lean() * 0.3f : config.lean() + 0.2f + random.nextFloat() * 0.3f;
			double height = mainHeight * (center ? 1.0 : 0.4 + random.nextDouble() * 0.4);
			double width = config.width() * (center ? 1.0 : 0.6 + random.nextDouble() * 0.3);
			double offset = center ? 0.0 : config.width() * 0.8;
			double bx = origin.getX() + 0.5 + Math.cos(yaw) * offset;
			double bz = origin.getZ() + 0.5 + Math.sin(yaw) * offset;
			placed |= shard(world, random, config, bx, origin.getY() - 1.0, bz, yaw, tilt, width, height);
		}
		return placed;
	}

	/** Ein Kristall: Achse von (bx, by, bz) aus, gekippt um {@code tilt} in Richtung {@code yaw}, Radius laeuft spitz zu. */
	private boolean shard(StructureWorldAccess world, Random random, Config config, double bx, double by, double bz, float yaw, float tilt,
			double width, double height) {
		double dx = Math.sin(tilt) * Math.cos(yaw);
		double dy = Math.cos(tilt);
		double dz = Math.sin(tilt) * Math.sin(yaw);
		// Laenge so kuerzen, dass die Spitze innerhalb der Reichweite bleibt
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double length = horizontal > 1.0E-3 ? Math.min(height, (MAX_REACH - width) / horizontal) : height;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		BlockPos top = null;
		boolean placed = false;
		for (double t = 0.0; t <= length; t += 0.5) {
			double radius = Math.max(0.35, width * Math.pow(1.0 - t / length, 0.7));
			double cx = bx + dx * t;
			double cy = by + dy * t;
			double cz = bz + dz * t;
			int r = MathHelper.ceil(radius);
			for (int ox = -r; ox <= r; ox++) {
				for (int oz = -r; oz <= r; oz++) {
					if (ox * ox + oz * oz > radius * radius + 0.25) {
						continue;
					}
					pos.set(MathHelper.floor(cx + ox), MathHelper.floor(cy), MathHelper.floor(cz + oz));
					BlockState existing = world.getBlockState(pos);
					if (existing.isAir() || existing.isReplaceable() || existing.isIn(BlockTags.REPLACEABLE_BY_TREES)) {
						world.setBlockState(pos, config.crystal().get(random, pos), Block.NOTIFY_LISTENERS);
						placed = true;
					}
				}
			}
			top = BlockPos.ofFloored(cx, cy, cz);
		}
		if (top != null && world.getBlockState(top.up()).isAir()) {
			BlockState tipState = config.tip().get(random, top.up());
			if (tipState.canPlaceAt(world, top.up())) {
				world.setBlockState(top.up(), tipState, Block.NOTIFY_LISTENERS);
			}
		}
		return placed;
	}
}
