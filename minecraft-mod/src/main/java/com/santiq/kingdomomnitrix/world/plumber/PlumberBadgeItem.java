package com.santiq.kingdomomnitrix.world.plumber;

import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Klempner-Abzeichen: Rechtsklick scannt die Umgebung ({@value #RADIUS} Bloecke) — Feinde leuchten
 * {@value #GLOW_TICKS} Ticks lang auf. In der Oberwelt meldet es ausserdem Entfernung und Richtung zum Wrack der PROCTOR.
 */
public class PlumberBadgeItem extends Item {
	private static final double RADIUS = 32.0;
	private static final int GLOW_TICKS = 200;
	private static final int COOLDOWN = 200;
	@Nullable
	private final String tooltipKey;

	public PlumberBadgeItem(Settings settings, @Nullable String tooltipKey) {
		super(settings);
		this.tooltipKey = tooltipKey;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world instanceof ServerWorld server) {
			List<LivingEntity> threats = server.getEntitiesByClass(LivingEntity.class, user.getBoundingBox().expand(RADIUS),
					entity -> entity instanceof Monster && entity.isAlive());
			for (LivingEntity threat : threats) {
				threat.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, GLOW_TICKS, 0, false, false));
			}
			Text message = Text.translatable("message.kingdomomnitrix.badge.scan", threats.size());
			if (world.getRegistryKey() == World.OVERWORLD) {
				BlockPos wreck = PlumberCrash.site(server.getServer()).orElse(null);
				if (wreck != null) {
					int distance = (int) Math.sqrt(user.getBlockPos().getSquaredDistance(wreck));
					message = message.copy().append(" · ").append(Text.translatable("message.kingdomomnitrix.badge.wreck", distance,
							Text.translatable(Directions.key(wreck.getX() - user.getBlockX(), wreck.getZ() - user.getBlockZ()))));
				}
			}
			user.sendMessage(message.copy().formatted(Formatting.GREEN), true);
			Fx.shockwave(server, user.getPos().add(0, 0.1, 0), 0x39FF14, 10.0f, 14);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.8f);
		}
		user.getItemCooldownManager().set(this, COOLDOWN);
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		if (tooltipKey != null) {
			tooltip.add(Text.translatable(tooltipKey).formatted(Formatting.GRAY));
		}
	}
}
