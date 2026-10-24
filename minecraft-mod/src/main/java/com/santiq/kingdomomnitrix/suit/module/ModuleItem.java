package com.santiq.kingdomomnitrix.suit.module;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Ein Upgrade-Modul: in der Werkbank zusammen mit einem Teil eines Endgame-Anzugs einbauen. */
public class ModuleItem extends Item {
	private final SuitModule module;

	public ModuleItem(SuitModule module, Settings settings) {
		super(settings);
		this.module = module;
	}

	public SuitModule module() {
		return module;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable(module.effectKey()).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.module.usage").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.module.limit", SuitModule.MAX_PER_SUIT).formatted(Formatting.DARK_GRAY));
	}
}
