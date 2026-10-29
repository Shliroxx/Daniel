package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.alien.AlienMode;
import com.santiq.kingdomomnitrix.alien.AlienModes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Upgrades Morphwaffen als Werkzeug (Abbauen selbst regelt der Modus: {@link AlienMode#harvest()}):
 * <ul>
 *   <li><b>Axt</b>: ein Stamm faellt den ganzen Baum (verbundene Staemme nach oben, hoechstens {@link #MAX_LOGS}).</li>
 *   <li><b>Spitzhacke</b>: baut 3x3 quer zur Blickrichtung ab (nur Bloecke, die sie auch abbaut).</li>
 * </ul>
 * Schleichen schaltet beides ab (nur der eine Block). Abgebaut wird ueber den Spieler (Drops, Schutzgebiete, Statistik).
 */
final class UpgradeTools {
	static final int MAX_LOGS = 96;
	/** gerade laufender Mehrfach-Abbau (die Folge-Abbrueche loesen nichts erneut aus) */
	private static boolean busy;

	private UpgradeTools() {
	}

	static void register() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (busy || !(player instanceof ServerPlayerEntity server) || !(world instanceof ServerWorld serverWorld) || player.isSneaking()) {
				return;
			}
			String mode = AlienModes.id(server);
			if (UpgradeAbilities.AXE_MODE.equals(mode) && state.isIn(BlockTags.LOGS)) {
				fell(serverWorld, server, pos);
			} else if (UpgradeAbilities.PICKAXE_MODE.equals(mode)) {
				AlienModes.active(server).filter(m -> m.harvests(state)).ifPresent(m -> area(serverWorld, server, pos, m));
			}
		});
	}

	/** Baum faellen: Staemme, die (auch schraeg) zusammenhaengen und nicht unter dem ersten Block liegen. */
	private static void fell(ServerWorld world, ServerPlayerEntity player, BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		List<BlockPos> logs = new ArrayList<>();
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos pos = queue.poll();
			for (BlockPos next : BlockPos.iterate(pos.add(-1, 0, -1), pos.add(1, 1, 1))) {
				if (next.getY() < start.getY() || !seen.add(next.toImmutable())) {
					continue;
				}
				if (world.getBlockState(next).isIn(BlockTags.LOGS)) {
					BlockPos found = next.toImmutable();
					logs.add(found);
					queue.add(found);
				}
			}
		}
		breakAll(player, logs);
	}

	/** 3x3-Flaeche um den Block, quer zur Blickachse (Blick nach oben/unten → waagerechte Flaeche). */
	private static void area(ServerWorld world, ServerPlayerEntity player, BlockPos center, AlienMode mode) {
		Direction.Axis axis = Math.abs(player.getPitch()) > 50.0f ? Direction.Axis.Y : player.getHorizontalFacing().getAxis();
		List<BlockPos> targets = new ArrayList<>();
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) {
					continue;
				}
				BlockPos pos = switch (axis) {
					case X -> center.add(0, a, b);
					case Y -> center.add(a, 0, b);
					case Z -> center.add(a, b, 0);
				};
				BlockState state = world.getBlockState(pos);
				float hardness = state.getHardness(world, pos);
				if (!state.isAir() && hardness >= 0.0f && hardness <= 50.0f && mode.harvests(state)) {
					targets.add(pos);
				}
			}
		}
		breakAll(player, targets);
	}

	private static void breakAll(ServerPlayerEntity player, List<BlockPos> positions) {
		busy = true;
		try {
			for (BlockPos pos : positions) {
				player.interactionManager.tryBreakBlock(pos);
			}
		} finally {
			busy = false;
		}
	}

	static Optional<String> weaponAfter(String current, List<String> order) {
		int index = order.indexOf(current);
		return index + 1 < order.size() ? Optional.of(order.get(index + 1)) : Optional.empty();
	}
}
