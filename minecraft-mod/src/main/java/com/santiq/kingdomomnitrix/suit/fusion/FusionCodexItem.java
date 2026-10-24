package com.santiq.kingdomomnitrix.suit.fusion;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Fusions-Kodex (Forschungsbuch wie das Thaumonomicon): zeigt Bauplaene und Gefahren der Altarstufen. Eine Stufe
 * ist erst lesbar, wenn ihr Schluesselgegenstand erforscht wurde: Gegenstand in die Zweithand nehmen und den Kodex
 * benutzen (Stufe 2 Resonanz-Splitter, Stufe 3 Nefarious-Chip, Stufe 4 Chaos-Herz). Der Fortschritt steckt im Buch.
 */
public class FusionCodexItem extends Item {
	/** Client: oeffnet die Kodex-Ansicht (gesetzt vom Client-Einstieg). */
	public static Consumer<ItemStack> open = stack -> {
	};
	private static final String KEY = "Research";
	/** Titel der Altarstufen (Index = Stufe) */
	public static final String[] TITLES = {"", "codex.kingdomomnitrix.tier1.title", "codex.kingdomomnitrix.tier2.title",
			"codex.kingdomomnitrix.tier3.title", "codex.kingdomomnitrix.tier4.title"};

	public FusionCodexItem(Settings settings) {
		super(settings);
	}

	/** Schluesselgegenstand einer Altarstufe (Stufe 1 braucht keinen). */
	public static Item key(int tier) {
		return switch (tier) {
			case 2 -> SuitFusion.RESONANCE_SHARD;
			case 3 -> SuitFusion.NEFARIOUS_CHIP;
			case 4 -> SuitFusion.CHAOS_HEART;
			default -> null;
		};
	}

	public static boolean researched(ItemStack codex, int tier) {
		if (tier <= 1) {
			return true;
		}
		NbtComponent data = codex.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		return (data.copyNbt().getInt(KEY) & (1 << tier)) != 0;
	}

	private static void research(ItemStack codex, int tier) {
		NbtCompound nbt = codex.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putInt(KEY, nbt.getInt(KEY) | (1 << tier));
		codex.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack codex = user.getStackInHand(hand);
		ItemStack other = user.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
		for (int tier = 2; tier <= FusionAltar.MAX_TIER; tier++) {
			if (!other.isEmpty() && other.isOf(key(tier)) && !researched(codex, tier)) {
				if (!world.isClient()) {
					research(codex, tier);
					world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 0.8f);
					user.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.researched", Text.translatable(TITLES[tier]))
							.formatted(Formatting.LIGHT_PURPLE), true);
				}
				return TypedActionResult.success(codex, world.isClient());
			}
		}
		if (world.isClient()) {
			open.accept(codex);
		}
		world.playSound(user, user.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 1.0f);
		return TypedActionResult.success(codex, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		int known = 0;
		for (int tier = 1; tier <= FusionAltar.MAX_TIER; tier++) {
			known += researched(stack, tier) ? 1 : 0;
		}
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.fusion_codex.progress", known, FusionAltar.MAX_TIER).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.fusion_codex.usage").formatted(Formatting.GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
