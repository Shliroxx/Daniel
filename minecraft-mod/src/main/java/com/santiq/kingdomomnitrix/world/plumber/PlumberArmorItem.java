package com.santiq.kingdomomnitrix.world.plumber;

import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Klempner-Ruestung. Der Helm filtert Corrodium-Staub ({@link com.santiq.kingdomomnitrix.world.content.Corrodium}),
 * das volle Set macht immun gegen Gift (der Brustpanzer entfernt Gift jede Sekunde).
 */
public class PlumberArmorItem extends ArmorItem {
	@Nullable
	private final String tooltipKey;

	public PlumberArmorItem(Type type, Settings settings, @Nullable String tooltipKey) {
		super(PlumberArmor.MATERIAL, type, settings);
		this.tooltipKey = tooltipKey;
	}

	/** Traegt das Wesen den Klempner-Helm? */
	public static boolean hasHelmet(LivingEntity entity) {
		return entity.getEquippedStack(EquipmentSlot.HEAD).isOf(Ben10Items.PLUMBER_HELMET);
	}

	/** Traegt das Wesen alle vier Teile? */
	public static boolean hasFullSet(LivingEntity entity) {
		return hasHelmet(entity) && entity.getEquippedStack(EquipmentSlot.CHEST).isOf(Ben10Items.PLUMBER_CHESTPLATE)
				&& entity.getEquippedStack(EquipmentSlot.LEGS).isOf(Ben10Items.PLUMBER_LEGGINGS)
				&& entity.getEquippedStack(EquipmentSlot.FEET).isOf(Ben10Items.PLUMBER_BOOTS);
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient() || getType() != Type.CHESTPLATE || world.getTime() % 20L != 0 || !(entity instanceof LivingEntity living)) {
			return;
		}
		if (living.getEquippedStack(EquipmentSlot.CHEST) == stack && hasFullSet(living) && living.hasStatusEffect(StatusEffects.POISON)) {
			living.removeStatusEffect(StatusEffects.POISON);
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		if (tooltipKey != null) {
			tooltip.add(Text.translatable(tooltipKey).formatted(Formatting.GRAY));
		}
	}
}
