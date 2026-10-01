package com.santiq.kingdomomnitrix.quest;

import com.santiq.kingdomomnitrix.networking.OpenQuestBookPayload;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Quest-Buch: Rechtsklick oeffnet Auftraege, Dialoge der Auftraggeber und den Quest-Tracker. */
public class QuestBookItem extends Item {
	public QuestBookItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (user instanceof ServerPlayerEntity player) {
			ServerPlayNetworking.send(player, new OpenQuestBookPayload());
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 1.0f);
		}
		return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.quest_book.usage").formatted(Formatting.GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
