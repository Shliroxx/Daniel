package com.santiq.kingdomomnitrix.mixin;

import com.santiq.kingdomomnitrix.quest.QuestManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Herstellen-Ziele: Vanilla meldet jedes hergestellte Item (Werkbank, Ofen, Schmiedetisch …) ueber onCraftByPlayer. */
@Mixin(ItemStack.class)
public abstract class ItemStackCraftMixin {
	@Inject(method = "onCraftByPlayer", at = @At("HEAD"))
	private void kingdomomnitrix$countCraft(World world, PlayerEntity player, int amount, CallbackInfo ci) {
		if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
			QuestManager.onCraft(serverPlayer, (ItemStack) (Object) this, amount);
		}
	}
}
