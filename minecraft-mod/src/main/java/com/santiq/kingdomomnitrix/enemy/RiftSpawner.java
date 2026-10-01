package com.santiq.kingdomomnitrix.enemy;

import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Oeffnet nachts in der Oberwelt gelegentlich Dunkelheitsrisse in der Naehe von Spielern.
 * Abschaltbar mit {@code /gamerule kingdomomnitrixDarknessRifts false} (Casual-Spielweise).
 */
public final class RiftSpawner {
	public static final GameRules.Key<GameRules.BooleanRule> RIFTS_ENABLED = GameRuleRegistry.register(
			"kingdomomnitrixDarknessRifts", GameRules.Category.SPAWNING, GameRuleFactory.createBooleanRule(true));

	private static final int CHECK_INTERVAL = 600;
	private static final float CHANCE_PER_CHECK = 0.08f;
	private static final double MIN_DISTANCE = 16.0;
	private static final double MAX_DISTANCE = 28.0;
	private static final double NO_OTHER_RIFT_RANGE = 96.0;

	private RiftSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(RiftSpawner::tick);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD || world.getTime() % CHECK_INTERVAL != 0
				|| !world.getGameRules().getBoolean(RIFTS_ENABLED) || !world.isNight()) {
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator() || player.isCreative() || world.getRandom().nextFloat() >= CHANCE_PER_CHECK) {
				continue;
			}
			if (!world.getEntitiesByType(ModEntities.DARKNESS_RIFT, new Box(player.getBlockPos()).expand(NO_OTHER_RIFT_RANGE), rift -> true).isEmpty()) {
				continue;
			}
			openNear(world, player, Optional.empty());
		}
	}

	/** Oeffnet einen Riss nahe dem Spieler. Ohne {@code riftId} wird passend zur Heldenstufe gewaehlt. */
	public static Optional<DarknessRiftEntity> openNear(ServerWorld world, ServerPlayerEntity player, Optional<Identifier> riftId) {
		int level = HeroDataAccess.get(player).level();
		Optional<Identifier> chosen = riftId.isPresent() ? riftId : RiftRegistry.pick(world.getRegistryManager(), level, world.getRandom());
		if (chosen.isEmpty() || RiftRegistry.get(world.getRegistryManager(), chosen.get()).isEmpty()) {
			return Optional.empty();
		}
		double angle = world.getRandom().nextDouble() * Math.PI * 2;
		double distance = MathHelper.lerp(world.getRandom().nextDouble(), MIN_DISTANCE, MAX_DISTANCE);
		BlockPos column = BlockPos.ofFloored(player.getX() + Math.cos(angle) * distance, 0, player.getZ() + Math.sin(angle) * distance);
		BlockPos ground = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
		if (!world.isChunkLoaded(ground.getX() >> 4, ground.getZ() >> 4) || Math.abs(ground.getY() - player.getY()) > 12) {
			ground = player.getBlockPos().add(MathHelper.floor(Math.cos(angle) * MIN_DISTANCE), 0, MathHelper.floor(Math.sin(angle) * MIN_DISTANCE));
		}
		DarknessRiftEntity rift = ModEntities.DARKNESS_RIFT.create(world);
		if (rift == null) {
			return Optional.empty();
		}
		rift.configure(chosen.get(), level);
		rift.refreshPositionAndAngles(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0.0f, 0.0f);
		world.spawnEntity(rift);
		world.playSound(null, ground, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.HOSTILE, 0.6f, 0.7f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.rift_opened").formatted(Formatting.DARK_PURPLE), false);
		return Optional.of(rift);
	}
}
