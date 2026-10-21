package com.santiq.kingdomomnitrix.world.content;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/** Einfaches Ben-10-Item mit optionaler Hinweiszeile (vollstaendiger Uebersetzungsschluessel aus dem Generator). */
public class Ben10Item extends Item {
	@Nullable
	private final String tooltipKey;

	public Ben10Item(Settings settings, @Nullable String tooltipKey) {
		super(settings);
		this.tooltipKey = tooltipKey;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		if (tooltipKey != null) {
			tooltip.add(Text.translatable(tooltipKey).formatted(Formatting.GRAY));
		}
		super.appendTooltip(stack, context, tooltip, type);
	}
}
