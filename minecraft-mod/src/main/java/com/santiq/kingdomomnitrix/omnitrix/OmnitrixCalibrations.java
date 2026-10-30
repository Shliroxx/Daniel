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
		RAISED, LOWERED, TOO_FAR, TRANSFORMED, MAX_LEVEL, NO_CAPACITY, ALREADY_ZERO, MISSING, UNKNOWN, TIER_RAISED, BOOST_REMOVED
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
		if ("tier".equals(module)) {
			return raiseTier(player, calibration);
		}
		if ("boost".equals(module)) {
			try {
				return OmnitrixBoosts.remove(player, Integer.parseInt(value)) ? Result.BOOST_REMOVED : Result.UNKNOWN;
			} catch (NumberFormatException e) {
				return Result.UNKNOWN;
			}
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
		if (!pay(player, OmnitrixCalibration.cost(level + 1))) {
			return Result.MISSING;
		}
		player.setAttached(OmnitrixCore.CALIBRATION, calibration.withLevel(target, level + 1));
		return Result.RAISED;
	}

	/** Omnitrix-Upgrade: naechste Stufe (mehr Punkte, ein Boost-Steckplatz mehr). */
	private static Result raiseTier(ServerPlayerEntity player, OmnitrixCalibration calibration) {
		if (calibration.tier() >= OmnitrixCalibration.MAX_TIER) {
			return Result.MAX_LEVEL;
		}
		if (!pay(player, OmnitrixCalibration.tierCost(calibration.tier() + 1))) {
			return Result.MISSING;
		}
		player.setAttached(OmnitrixCore.CALIBRATION, calibration.withTier(calibration.tier() + 1));
		return Result.TIER_RAISED;
	}

	/** Bolts und Materialien abziehen; im Kreativmodus kostenlos. */
	private static boolean pay(ServerPlayerEntity player, OmnitrixCalibration.Cost cost) {
		if (player.getAbilities().creativeMode) {
			return true;
		}
		PlayerInventory inventory = player.getInventory();
		if (!HeroDataAccess.get(player).canAfford(cost.bolts()) || inventory.count(ModItems.RARITANIUM) < cost.raritanium()
				|| inventory.count(ModItems.MYTHRIL_SHARD) < cost.mythril() || inventory.count(ModItems.ORICHALCUM) < cost.orichalcum()) {
			return false;
		}
		take(inventory, ModItems.RARITANIUM, cost.raritanium());
		take(inventory, ModItems.MYTHRIL_SHARD, cost.mythril());
		take(inventory, ModItems.ORICHALCUM, cost.orichalcum());
		HeroDataAccess.update(player, data -> data.addBolts(-cost.bolts()));
		return true;
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
			case BOOST_REMOVED -> {
				// Meldung kommt aus OmnitrixBoosts.remove
			}
			case TIER_RAISED -> {
				OmnitrixCalibration c = OmnitrixCore.calibration(player);
				OmnitrixOs.send(player, OmnitrixOs.Event.RECALIBRATED, Text.translatable("holo.kingdomomnitrix.tier.raised", c.tier()),
						Optional.of(Text.translatable("holo.kingdomomnitrix.tier.bonus", c.capacity(), c.slots())), Optional.empty());
				OmnitrixCore.cue(player, OmnitrixCue.CONFIRM);
			}
			case RAISED, LOWERED -> {
				OmnitrixCalibration c = OmnitrixCore.calibration(player);
				OmnitrixOs.send(player, OmnitrixOs.Event.RECALIBRATED,
						Text.translatable("holo.kingdomomnitrix.calibration." + (result == Result.RAISED ? "raised" : "lowered"),
								Text.translatable("calibration.kingdomomnitrix.module." + module),
								c.level(OmnitrixCalibration.Module.byKey(module).orElseThrow())),
						Optional.of(Text.translatable("holo.kingdomomnitrix.calibration.points", c.used(), c.capacity())),
						Optional.empty());
				OmnitrixCore.cue(player, result == Result.RAISED ? OmnitrixCue.CONFIRM : OmnitrixCue.CANCEL);
			}
			default -> {
				OmnitrixOs.send(player, OmnitrixOs.Event.REFUSED,
						Text.translatable("holo.kingdomomnitrix.calibration." + result.name().toLowerCase(java.util.Locale.ROOT)));
				OmnitrixCore.cue(player, OmnitrixCue.ERROR);
			}
		}
	}
}
