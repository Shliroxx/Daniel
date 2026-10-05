package com.santiq.kingdomomnitrix.keyblade;

import com.santiq.kingdomomnitrix.combat.ComboProfile;
import com.santiq.kingdomomnitrix.combat.ComboWeapon;
import com.santiq.kingdomomnitrix.keyblade.KeybladeDefinition.Passive;
import com.santiq.kingdomomnitrix.magic.MagicManager;
import com.santiq.kingdomomnitrix.registry.ModComponents;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Keyblade. Werte kommen aus der {@link KeybladeRegistry} (JSON) und der Stufe am Stack.
 * <ul>
 *     <li>Linksklick: Combo · Halten: schwerer Angriff · Blocken (siehe {@link ComboWeapon})</li>
 *     <li>Rechtsklick: aktiven Zauber wirken · Magie-Taste halten + Mausrad: Zauber wechseln</li>
 * </ul>
 * Keyblades sind unzerstoerbar und werden an der Keyblade-Schmiede aufgewertet.
 */
public class KeybladeItem extends Item implements ComboWeapon {
	private static final int ENCHANTABILITY = 15;

	public KeybladeItem(Settings settings) {
		super(settings);
	}

	public static int level(ItemStack stack) {
		return Math.max(1, stack.getOrDefault(ModComponents.KEYBLADE_LEVEL, 1));
	}

	public static float damage(KeybladeDefinition definition, int level) {
		return definition.damage() + definition.damagePerLevel() * (level - 1);
	}

	public static float magic(KeybladeDefinition definition, int level) {
		return definition.magic() + definition.magicPerLevel() * (level - 1);
	}

	@Override
	public ComboProfile comboProfile(RegistryWrapper.WrapperLookup registries, ItemStack stack) {
		Optional<KeybladeDefinition> found = KeybladeRegistry.forStack(registries, stack);
		if (found.isEmpty()) {
			return ComboProfile.DEFAULT;
		}
		KeybladeDefinition definition = found.get();
		ComboProfile base = ComboProfile.DEFAULT;
		float speed = definition.attackSpeed();
		return new ComboProfile(
				damage(definition, level(stack)),
				definition.comboLength() + Math.round(definition.passive(Passive.COMBO_PLUS)),
				base.stepMultiplier(),
				base.finisherMultiplier() + definition.passive(Passive.FINISHER_PLUS),
				base.heavyMultiplier(),
				definition.reach(),
				Math.round(base.lightDelayTicks() / speed),
				Math.round(base.heavyDelayTicks() / speed),
				definition.passive(Passive.CRITICAL),
				true);
	}

	@Override
	public int getEnchantability() {
		return ENCHANTABILITY;
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return stack.getCount() == 1;
	}

	// --- Magie -------------------------------------------------------------------------------------

	/** Rechtsklick wirkt den aktiven Zauber (Auswahl: Magie-Taste halten + Mausrad). */
	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (hand != Hand.MAIN_HAND) {
			return TypedActionResult.pass(stack);
		}
		if (!(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.success(stack, true);
		}
		return MagicManager.castSelected(player) == MagicManager.Result.SUCCESS
				? TypedActionResult.success(stack, false)
				: TypedActionResult.fail(stack);
	}

	// --- Tooltip --------------------------------------------------------------------------------

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		Optional<KeybladeDefinition> found = KeybladeRegistry.forStack(context.getRegistryLookup(), stack);
		if (found.isPresent()) {
			KeybladeDefinition definition = found.get();
			int level = level(stack);
			tooltip.add(Text.translatable("tooltip.kingdomomnitrix.keyblade.level", level, definition.maxLevel())
					.formatted(definition.rarity().getFormatting()));
			tooltip.add(Text.translatable("tooltip.kingdomomnitrix.keyblade.stats",
					format(damage(definition, level)), format(magic(definition, level)),
					format(definition.attackSpeed()), format(definition.reach())).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.kingdomomnitrix.keyblade.combo",
					definition.comboLength() + Math.round(definition.passive(Passive.COMBO_PLUS))).formatted(Formatting.GRAY));
			for (Passive passive : Passive.values()) {
				Float value = definition.passives().get(passive);
				if (value != null) {
					float shown = passive == Passive.COMBO_PLUS ? value : value * 100.0f;
					tooltip.add(Text.literal(" + ").append(Text.translatable(passive.translationKey(), format(shown)))
							.formatted(Formatting.DARK_AQUA));
				}
			}
		}
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.kingdom_key.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}

	/** Eine Nachkommastelle, ganze Zahlen ohne ".0" (30.000002 → "30"). */
	private static String format(float value) {
		float rounded = Math.round(value * 10.0f) / 10.0f;
		return rounded == Math.rint(rounded) ? String.valueOf((int) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
	}
}
