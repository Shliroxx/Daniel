package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import java.util.List;
import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Fusionsgranate: Wurfwaffe mit Munition. Explodiert beim Aufprall mit Flaechenschaden und Rueckstoss,
 * zerstoert aber nie Bloecke. Stufen erhoehen Sprengkraft ({@code power}).
 */
public class FusionGrenadeItem extends WeaponItem {
	public FusionGrenadeItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!hasAmmo(user, stack)) {
			if (!world.isClient()) {
				user.sendMessage(Text.translatable("message.kingdomomnitrix.no_ammo").formatted(Formatting.RED), true);
			}
			return TypedActionResult.fail(stack);
		}
		if (!world.isClient()) {
			Optional<WeaponDefinition.Level> level = levelData(world.getRegistryManager(), stack);
			float power = (float) level.map(l -> l.stat("power", 2.5)).orElse(2.5).doubleValue();
			int cooldown = (int) Math.round(level.map(l -> l.stat("cooldown", 15)).orElse(15.0));
			consumeAmmo(user, stack, 1);
			FusionGrenadeEntity grenade = new FusionGrenadeEntity(world, user, power);
			grenade.setVelocity(user, user.getPitch(), user.getYaw(), -10.0f, 1.2f, 1.0f);
			world.spawnEntity(grenade);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.WEAPON_THROW, SoundCategory.PLAYERS, 0.6f, 0.6f);
			user.getItemCooldownManager().set(this, cooldown);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	protected void appendUsage(List<Text> tooltip) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.fusion_grenade.usage").formatted(Formatting.DARK_GRAY));
	}
}
