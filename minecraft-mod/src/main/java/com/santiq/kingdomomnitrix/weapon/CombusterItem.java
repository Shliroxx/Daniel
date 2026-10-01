package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * Combuster: automatischer Plasma-Blaster. Rechtsklick halten feuert im Takt der Stufe ({@code fire_delay});
 * jeder Schuss kostet Munition. Auf Hoechststufe explodieren die Schuesse ({@code explosion} &gt; 0, ohne Blockschaden).
 */
public class CombusterItem extends WeaponItem {
	private static final int MAX_USE_TICKS = 72_000;

	public CombusterItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!hasAmmo(user, stack)) {
			if (!world.isClient()) {
				user.sendMessage(Text.translatable("message.kingdomomnitrix.no_ammo").formatted(Formatting.RED), true);
				world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8f, 1.2f);
			}
			return TypedActionResult.fail(stack);
		}
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return MAX_USE_TICKS;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.NONE;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(user instanceof PlayerEntity player)) {
			return;
		}
		Optional<WeaponDefinition.Level> level = levelData(world.getRegistryManager(), stack);
		int delay = (int) Math.max(1, level.map(l -> l.stat("fire_delay", 5)).orElse(5.0));
		int elapsed = MAX_USE_TICKS - remainingUseTicks;
		if (elapsed % delay != 0) {
			return;
		}
		if (world.isClient()) {
			// Rueckstoss: leichter, zufaelliger Kamera-Kick (summiert sich nicht zu einem Hochziehen)
			player.setPitch(player.getPitch() - 0.25f);
			player.setYaw(player.getYaw() + (world.getRandom().nextFloat() - 0.5f) * 0.4f);
			return;
		}
		if (!consumeAmmo(player, stack, 1)) {
			player.stopUsingItem();
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_ammo").formatted(Formatting.RED), true);
			return;
		}
		float damage = (float) level.map(l -> l.stat("damage", 6)).orElse(6.0).doubleValue();
		float speed = (float) level.map(l -> l.stat("speed", 2.6)).orElse(2.6).doubleValue();
		float explosion = (float) level.map(l -> l.stat("explosion", 0)).orElse(0.0).doubleValue();
		HeroProjectileEntity.shoot(world, player, ModItems.PLASMA_SHOT, speed, 1.0f).withDamage(damage).withExplosion(explosion);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.6f, 1.8f);
	}

	@Override
	protected void appendUsage(List<Text> tooltip) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.combuster.usage").formatted(Formatting.DARK_GRAY));
	}
}
