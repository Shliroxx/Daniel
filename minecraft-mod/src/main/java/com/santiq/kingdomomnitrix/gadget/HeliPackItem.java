package com.santiq.kingdomomnitrix.gadget;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Clanks Heli-Pack: in der Zweithand gehalten bremst er jeden Fall zu einem Gleitflug ab.
 * Schleichen schaltet ihn aus. Laeuft auf Client und Server, weil die Spielerbewegung clientseitig ist.
 */
public class HeliPackItem extends Item {
	private static final double MAX_FALL_SPEED = -0.12;
	private static final double GLIDE_BOOST = 1.04;
	private static final double MAX_HORIZONTAL_SPEED = 0.6;

	public HeliPackItem(Settings settings) {
		super(settings);
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (!(entity instanceof PlayerEntity player)) {
			return;
		}
		if (player.getOffHandStack() != stack) {
			return;
		}
		if (player.isOnGround() || player.isSneaking() || player.getAbilities().flying || player.isTouchingWater() || player.isFallFlying()) {
			return;
		}
		Vec3d velocity = player.getVelocity();
		if (velocity.y >= MAX_FALL_SPEED) {
			return;
		}
		double boost = velocity.horizontalLength() < MAX_HORIZONTAL_SPEED ? GLIDE_BOOST : 1.0;
		player.setVelocity(velocity.x * boost, MAX_FALL_SPEED, velocity.z * boost);
		player.fallDistance = 0.0f;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.heli_pack.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
