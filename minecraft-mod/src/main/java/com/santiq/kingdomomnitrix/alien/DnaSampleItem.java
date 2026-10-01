package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModComponents;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * DNA-Probe eines Aliens. Rechtsklick mit einem Omnitrix im Inventar schaltet das Alien dauerhaft frei.
 * Welches Alien, steht in der Item-Komponente {@link ModComponents#DNA_ALIEN}.
 */
public class DnaSampleItem extends Item {
	public DnaSampleItem(Settings settings) {
		super(settings);
	}

	public static ItemStack create(Identifier alienId) {
		ItemStack stack = new ItemStack(ModItems.DNA_SAMPLE);
		stack.set(ModComponents.DNA_ALIEN, alienId);
		return stack;
	}

	@Override
	public Text getName(ItemStack stack) {
		Identifier alienId = stack.get(ModComponents.DNA_ALIEN);
		if (alienId == null) {
			return super.getName(stack);
		}
		return Text.translatable("item.kingdomomnitrix.dna_sample.named", TransformationManager.alienName(alienId));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.success(stack, true);
		}
		Identifier alienId = stack.get(ModComponents.DNA_ALIEN);
		if (alienId == null || AlienRegistry.get(world.getRegistryManager(), alienId).isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.dna_corrupt").formatted(Formatting.RED), true);
			return TypedActionResult.fail(stack);
		}
		if (!OmnitrixItem.hasOmnitrix(player)) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.need_omnitrix").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}
		if (HeroDataAccess.get(player).hasAlien(alienId)) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.dna_known", TransformationManager.alienName(alienId))
					.formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}
		HeroDataAccess.update(player, data -> data.unlockAlien(alienId));
		TransformationManager.select(player, alienId);
		stack.decrementUnlessCreative(1, player);
		player.getServerWorld().spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getBodyY(0.5), player.getZ(),
				30, 0.5, 0.7, 0.5, 0.1);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.dna_unlocked", TransformationManager.alienName(alienId))
				.formatted(Formatting.GREEN), false);
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.dna_sample.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
