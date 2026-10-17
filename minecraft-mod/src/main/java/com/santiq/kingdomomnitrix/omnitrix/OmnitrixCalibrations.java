package com.santiq.kingdomomnitrix.omnitrix;

import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModBlocks;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.Optional;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Server-Seite der Kalibrier-Werkbank. Prueft Naehe zur Werkbank, Menschenform, Punkte-Budget, Hoechststufe und Kosten;
 * Herabstufen ist kostenlos, gibt aber nichts zurueck. Im Kreativmodus kostenlos.
 */
public final class OmnitrixCalibrations {
	/** Hoechstabstand zur Werkbank (Quadrat, Blockmitte). */
	private static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

	public enum Result {
		RAISED, LOWERED, COLOR, TOO_FAR, TRANSFORMED, MAX_LEVEL, NO_CAPACITY, ALREADY_ZERO, MISSING, UNKNOWN
	}

	private OmnitrixCalibrations() {
	}

	public static void handle(ServerPlayerEntity player, BlockPos bench, String module, String value) {
		Result result = apply(player, bench, module, value);
		feedback(player, result, module);
	}

	static Result apply(ServerPlayerEntity player, BlockPos bench, String module, String value) {
		if (!player.getWorld().getBlockState(bench).isOf(ModBlocks.CALIBRATION_BENCH)
				|| player.getPos().squaredDistanceTo(Vec3d.ofCenter(bench)) > MAX_DISTANCE_SQ) {
			return Result.TOO_FAR;
		}
		if (TransformationManager.get(player).isTransformed()) {
			return Result.TRANSFORMED;
		}
		OmnitrixCalibration calibration = OmnitrixCore.calibration(player);
		if ("color".equals(module)) {
			if (!OmnitrixColors.exists(value)) {
				return Result.UNKNOWN;
			}
			player.setAttached(OmnitrixCore.CALIBRATION, calibration.withColor(value));
			return Result.COLOR;
		}
		Optional<OmnitrixCalibration.Module> found = OmnitrixCalibration.Module.byKey(module);
		if (found.isEmpty()) {
			return Result.UNKNOWN;
		}
		OmnitrixCalibration.Module target = found.get();
		int level = calibration.level(target);
		if ("-".equals(value)) {
			if (level == 0) {
				return Result.ALREADY_ZERO;
			}
			player.setAttached(OmnitrixCore.CALIBRATION, calibration.withLevel(target, level - 1));
			return Result.LOWERED;
		}
		if (!"+".equals(value)) {
			return Result.UNKNOWN;
		}
		if (level >= OmnitrixCalibration.MAX_LEVEL) {
			return Result.MAX_LEVEL;
		}
		if (calibration.free() <= 0) {
			return Result.NO_CAPACITY;
		}
		OmnitrixCalibration.Cost cost = OmnitrixCalibration.cost(level + 1);
		if (!player.getAbilities().creativeMode) {
			PlayerInventory inventory = player.getInventory();
			if (!HeroDataAccess.get(player).canAfford(cost.bolts()) || inventory.count(ModItems.RARITANIUM) < cost.raritanium()
					|| inventory.count(ModItems.MYTHRIL_SHARD) < cost.mythril() || inventory.count(ModItems.ORICHALCUM) < cost.orichalcum()) {
				return Result.MISSING;
			}
			take(inventory, ModItems.RARITANIUM, cost.raritanium());
			take(inventory, ModItems.MYTHRIL_SHARD, cost.mythril());
			take(inventory, ModItems.ORICHALCUM, cost.orichalcum());
			HeroDataAccess.update(player, data -> data.addBolts(-cost.bolts()));
		}
		player.setAttached(OmnitrixCore.CALIBRATION, calibration.withLevel(target, level + 1));
		return Result.RAISED;
	}

	private static void take(PlayerInventory inventory, Item item, int amount) {
		for (int slot = 0; slot < inventory.size() && amount > 0; slot++) {
			var stack = inventory.getStack(slot);
			if (stack.isOf(item)) {
				int taken = Math.min(amount, stack.getCount());
				stack.decrement(taken);
				amount -= taken;
			}
		}
		inventory.markDirty();
	}

	private static void feedback(ServerPlayerEntity player, Result result, String module) {
		switch (result) {
			case RAISED, LOWERED -> {
				OmnitrixCalibration c = OmnitrixCore.calibration(player);
				OmnitrixOs.send(player, OmnitrixOs.Event.RECALIBRATED,
						Text.translatable("holo.kingdomomnitrix.calibration." + (result == Result.RAISED ? "raised" : "lowered"),
								Text.translatable("calibration.kingdomomnitrix.module." + module),
								c.level(OmnitrixCalibration.Module.byKey(module).orElseThrow())),
						Optional.of(Text.translatable("holo.kingdomomnitrix.calibration.points", c.used(), OmnitrixCalibration.CAPACITY)),
						Optional.empty());
				OmnitrixCore.cue(player, result == Result.RAISED ? OmnitrixCue.CONFIRM : OmnitrixCue.CANCEL);
			}
			case COLOR -> {
				OmnitrixOs.send(player, OmnitrixOs.Event.RECALIBRATED, Text.translatable("holo.kingdomomnitrix.calibration.color",
						Text.translatable("calibration.kingdomomnitrix.color." + OmnitrixCore.calibration(player).color())));
				OmnitrixCore.cue(player, OmnitrixCue.CONFIRM);
			}
			default -> {
				OmnitrixOs.send(player, OmnitrixOs.Event.REFUSED,
						Text.translatable("holo.kingdomomnitrix.calibration." + result.name().toLowerCase(java.util.Locale.ROOT)));
				OmnitrixCore.cue(player, OmnitrixCue.ERROR);
			}
		}
	}
}
