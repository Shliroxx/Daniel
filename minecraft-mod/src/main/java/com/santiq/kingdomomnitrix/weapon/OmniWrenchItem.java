package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import com.santiq.kingdomomnitrix.combat.ComboProfile;
import com.santiq.kingdomomnitrix.combat.ComboWeapon;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * OmniWrench 8000: Nahkampf mit Combo (wie Keyblades), Rechtsklick wirft den Schluessel als Bumerang.
 * Der geworfene Schluessel trifft Gegner auf Hin- und Rueckweg, betaetigt Knoepfe und Hebel und
 * zerschlaegt Bolt-Kisten.
 */
public class OmniWrenchItem extends WeaponItem implements ComboWeapon {
	public OmniWrenchItem(Settings settings) {
		super(settings);
	}

	@Override
	public ComboProfile comboProfile(RegistryWrapper.WrapperLookup registries, ItemStack stack) {
		ComboProfile base = ComboProfile.DEFAULT;
		float damage = levelData(registries, stack).map(level -> (float) level.stat("damage", 5)).orElse(5.0f);
		return new ComboProfile(damage, 3, base.stepMultiplier(), base.finisherMultiplier(), base.heavyMultiplier(),
				0.3, 7, 18, 0.0f, true);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient()) {
			Optional<WeaponDefinition.Level> level = levelData(world.getRegistryManager(), stack);
			float damage = (float) level.map(l -> l.stat("throw_damage", 7)).orElse(7.0).doubleValue();
			double range = level.map(l -> l.stat("range", 12)).orElse(12.0);
			WrenchProjectileEntity wrench = new WrenchProjectileEntity(world, user, stack.copyWithCount(1), damage, range);
			wrench.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, 1.6f, 0.0f);
			world.spawnEntity(wrench);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.WEAPON_THROW, SoundCategory.PLAYERS, 0.8f, 0.5f);
			// Erst wieder werfen, wenn er zurueck ist (oder nach Ablauf der Flugzeit)
			user.getItemCooldownManager().set(this, WrenchProjectileEntity.MAX_FLIGHT_TICKS);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	protected void appendUsage(List<Text> tooltip) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.omniwrench.usage").formatted(Formatting.DARK_GRAY));
	}
}
