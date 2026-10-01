package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.util.ItemData;
import java.util.List;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Omnitrix.
 * <ul>
 *     <li>Normalform, Schleichen + Rechtsklick: naechstes Alien waehlen</li>
 *     <li>Normalform, Rechtsklick: verwandeln (60 s)</li>
 *     <li>Alien-Form, Rechtsklick: Spezialfaehigkeit</li>
 *     <li>Alien-Form, Schleichen + Rechtsklick: zurueckverwandeln</li>
 * </ul>
 * Nach jeder Verwandlung laedt die Uhr 15 s nach.
 */
public class OmnitrixItem extends Item {
	public static final int TRANSFORM_TICKS = 60 * 20;
	public static final int RECHARGE_TICKS = 15 * 20;
	private static final String ALIEN_KEY = "alien";
	private static final String RECHARGE_KEY = "recharge_until";

	public OmnitrixItem(Settings settings) {
		super(settings);
	}

	public static Alien getSelected(ItemStack stack) {
		return Alien.byIndex(ItemData.getInt(stack, ALIEN_KEY));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(world instanceof ServerWorld serverWorld)) {
			return TypedActionResult.success(stack, true);
		}

		Alien active = Alien.activeOn(user);
		if (active != null) {
			if (user.isSneaking()) {
				revert(serverWorld, user, stack, active);
				return TypedActionResult.success(stack, false);
			}
			if (!active.useAbility(serverWorld, user)) {
				return TypedActionResult.fail(stack);
			}
			user.getItemCooldownManager().set(this, active.abilityCooldownTicks());
			return TypedActionResult.success(stack, false);
		}

		if (user.isSneaking()) {
			Alien next = getSelected(stack).next();
			ItemData.putInt(stack, ALIEN_KEY, next.ordinal());
			user.sendMessage(Text.translatable("message.kingdomomnitrix.alien_selected", next.displayName()), true);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.5f, 1.8f);
			return TypedActionResult.success(stack, false);
		}

		long remaining = ItemData.getLong(stack, RECHARGE_KEY) - world.getTime();
		if (remaining > 0) {
			long seconds = (remaining + 19) / 20;
			user.sendMessage(Text.translatable("message.kingdomomnitrix.omnitrix_recharging", seconds).formatted(Formatting.RED), true);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.7f, 0.5f);
			return TypedActionResult.fail(stack);
		}

		transform(serverWorld, user, stack, getSelected(stack));
		return TypedActionResult.success(stack, false);
	}

	private static void transform(ServerWorld world, PlayerEntity user, ItemStack stack, Alien alien) {
		user.addStatusEffect(new StatusEffectInstance(alien.effect(), TRANSFORM_TICKS, 0, false, false, true));
		ItemData.putLong(stack, RECHARGE_KEY, world.getTime() + TRANSFORM_TICKS + RECHARGE_TICKS);
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, user.getX(), user.getBodyY(0.5), user.getZ(), 40, 0.6, 0.9, 0.6, 0.1);
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 1.5f);
		user.sendMessage(Text.translatable("message.kingdomomnitrix.transformed", alien.displayName()), true);
	}

	private static void revert(ServerWorld world, PlayerEntity user, ItemStack stack, Alien alien) {
		user.removeStatusEffect(alien.effect());
		ItemData.putLong(stack, RECHARGE_KEY, world.getTime() + RECHARGE_TICKS);
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, user.getX(), user.getBodyY(0.5), user.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.2f);
		user.sendMessage(Text.translatable("message.kingdomomnitrix.reverted").formatted(Formatting.GREEN), true);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.alien", getSelected(stack).displayName()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.omnitrix.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
