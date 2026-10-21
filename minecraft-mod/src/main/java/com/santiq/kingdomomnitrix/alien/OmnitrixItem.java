package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.networking.OpenOmnitrixPayload;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
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

	/** Farbe des Energiekerns in die (synchronisierte) Kalibrierung des Traegers uebernehmen. */
	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient() || world.getTime() % 20 != 0 || !(entity instanceof ServerPlayerEntity player)) {
			return;
		}
		String core = stack.get(com.santiq.kingdomomnitrix.registry.ModComponents.OMNITRIX_CORE);
		com.santiq.kingdomomnitrix.omnitrix.OmnitrixCalibration calibration = com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore.calibration(player);
		if (core != null && com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors.exists(core) && !core.equals(calibration.color())) {
			player.setAttached(com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore.CALIBRATION, calibration.withColor(core));
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors.ofStack(stack).ifPresent(color -> tooltip.add(Text.translatable(
				"tooltip.kingdomomnitrix.omnitrix.core", Text.translatable(com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors.nameKey(color)))
				.withColor(color.primary())));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.omnitrix.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
