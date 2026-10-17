package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.registry.ModComponents;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Basis der Ratchet-&-Clank-Waffen: Stufe und Munition am Stack ({@link WeaponState}), Werte aus der
 * {@link WeaponRegistry}. Munition und Stufen gibt es am Waffen-Terminal.
 */
public abstract class WeaponItem extends Item {
	private static final int AMMO_BAR_COLOR = 0x4FC3FF;

	protected WeaponItem(Settings settings) {
		super(settings);
	}

	public static WeaponState state(ItemStack stack) {
		WeaponState state = stack.get(ModComponents.WEAPON_STATE);
		return state != null ? state : new WeaponState(1, 0, 0);
	}

	public static void setState(ItemStack stack, WeaponState state) {
		stack.set(ModComponents.WEAPON_STATE, state);
	}

	public static Optional<WeaponDefinition> definition(RegistryWrapper.WrapperLookup registries, ItemStack stack) {
		return WeaponRegistry.forStack(registries, stack);
	}

	public static Optional<WeaponDefinition.Level> levelData(RegistryWrapper.WrapperLookup registries, ItemStack stack) {
		return definition(registries, stack).map(definition -> definition.level(state(stack).level()));
	}

	/** Neue Waffe auf Stufe 1 mit vollem Magazin. */
	public static ItemStack create(Item item, WeaponDefinition definition) {
		ItemStack stack = new ItemStack(item);
		int maxAmmo = definition.level(1).maxAmmo();
		setState(stack, new WeaponState(1, maxAmmo, maxAmmo));
		return stack;
	}

	/** Gecraftete Waffen haben noch keinen Zustand: beim ersten Tick im Inventar mit vollem Magazin anlegen. */
	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (!world.isClient() && !stack.contains(ModComponents.WEAPON_STATE)) {
			definition(world.getRegistryManager(), stack).ifPresent(definition -> {
				int maxAmmo = definition.level(1).maxAmmo();
				setState(stack, new WeaponState(1, maxAmmo, maxAmmo));
			});
		}
	}

	/** Verbraucht Munition; im Kreativmodus kostenlos. */
	protected static boolean consumeAmmo(PlayerEntity player, ItemStack stack, int amount) {
		WeaponState state = state(stack);
		if (state.maxAmmo() <= 0 || player.getAbilities().creativeMode) {
			return true;
		}
		if (state.ammo() < amount) {
			return false;
		}
		setState(stack, state.withAmmo(state.ammo() - amount));
		return true;
	}

	protected static boolean hasAmmo(PlayerEntity player, ItemStack stack) {
		WeaponState state = state(stack);
		return state.maxAmmo() <= 0 || player.getAbilities().creativeMode || state.ammo() > 0;
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		return state(stack).maxAmmo() > 0;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		WeaponState state = state(stack);
		return state.maxAmmo() <= 0 ? 0 : MathHelper.clamp(Math.round(13.0f * state.ammo() / state.maxAmmo()), 0, 13);
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		return AMMO_BAR_COLOR;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		WeaponState state = state(stack);
		Optional<WeaponDefinition> definition = definition(context.getRegistryLookup(), stack);
		int maxLevel = definition.map(WeaponDefinition::maxLevel).orElse(state.level());
		Text levelText = state.level() >= maxLevel && maxLevel > 1
				? Text.translatable("tooltip.kingdomomnitrix.weapon.max")
				: Text.translatable("tooltip.kingdomomnitrix.weapon.level", state.level(), maxLevel);
		tooltip.add(levelText.copy().formatted(Formatting.AQUA));
		if (state.maxAmmo() > 0) {
			tooltip.add(Text.translatable("tooltip.kingdomomnitrix.ammo", state.ammo(), state.maxAmmo()).formatted(Formatting.GRAY));
		}
		definition.map(d -> d.level(state.level())).ifPresent(level -> {
			double damage = level.stat("damage", 0);
			if (damage > 0) {
				tooltip.add(Text.translatable("tooltip.kingdomomnitrix.weapon.damage", String.format(Locale.ROOT, "%.1f", damage))
						.formatted(Formatting.GRAY));
			}
		});
		appendUsage(tooltip);
		super.appendTooltip(stack, context, tooltip, type);
	}

	protected abstract void appendUsage(List<Text> tooltip);
}
