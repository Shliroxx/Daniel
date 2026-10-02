package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.vfx.CameraShake;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kamera-Stoss: verschiebt nur die gerenderte Kamera, nicht die Blickrichtung des Spielers. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	public abstract float getYaw();

	@Shadow
	public abstract float getPitch();

	@Inject(method = "update", at = @At("TAIL"))
	private void kingdomomnitrix$shake(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
			float tickDelta, CallbackInfo ci) {
		float[] offset = CameraShake.offset(tickDelta);
		if (offset[0] != 0.0f || offset[1] != 0.0f) {
			setRotation(getYaw() + offset[0], getPitch() + offset[1]);
		}
	}
}
