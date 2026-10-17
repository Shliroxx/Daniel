package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.networking.OpenOmnitrixPayload;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Das Omnitrix. Rechtsklick (oder die Omnitrix-Taste) oeffnet das Alien-Rad; der eigentliche Zustand
 * liegt beim Spieler ({@link TransformationManager}). Es genuegt, das Omnitrix irgendwo im Inventar zu tragen.
 */
public class OmnitrixItem extends Item {
	public OmnitrixItem(Settings settings) {
		super(settings);
	}

	public static boolean hasOmnitrix(PlayerEntity player) {
		return player.getInventory().contains(stack -> stack.isOf(ModItems.OMNITRIX));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user instanceof ServerPlayerEntity player) {
			if (HeroDataAccess.get(player).unlockedAliens().isEmpty()) {
				player.sendMessage(Text.translatable("message.kingdomomnitrix.no_dna").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}
			ServerPlayNetworking.send(player, OpenOmnitrixPayload.INSTANCE);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.omnitrix.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
