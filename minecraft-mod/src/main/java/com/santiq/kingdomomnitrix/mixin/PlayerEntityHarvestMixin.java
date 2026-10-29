package com.santiq.kingdomomnitrix.mixin;

import com.santiq.kingdomomnitrix.alien.AlienMode;
import com.santiq.kingdomomnitrix.alien.AlienModes;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Werkzeug-Modi ({@link AlienMode#harvest()}, z. B. Upgrades Spitzhacken-Arme): der Spieler baut die passenden Bloecke
 * ab, als haelte er das Werkzeug — mit Drops und mit dem Tempo des Modus. Laeuft auf Client und Server (der Modus ist
 * synchronisiert), damit Abbaufortschritt und Server-Pruefung uebereinstimmen.
 */
@Mixin(PlayerEntity.class)
abstract class PlayerEntityHarvestMixin {
	@Inject(method = "canHarvest", at = @At("HEAD"), cancellable = true)
	private void kingdomomnitrix$modeHarvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (mode(state).isPresent()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
	private void kingdomomnitrix$modeSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
		mode(state).ifPresent(mode -> cir.setReturnValue(cir.getReturnValueF() * mode.breakSpeed()));
	}

	private Optional<AlienMode> mode(BlockState state) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (AlienModes.id(self).isEmpty()) {
			return Optional.empty();
		}
		return AlienModes.active(self).filter(mode -> mode.harvests(state));
	}
}
