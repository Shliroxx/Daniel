package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModComponents;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * DNA-Probe eines Aliens (Belohnung aus Ereignissen, Rissen, Dungeon). Rechtsklick mit einem Omnitrix im Inventar zaehlt
 * als eine Scan-Probe ({@link DnaScan#addSample}); die letzte Probe muss auf der Herkunftswelt gescannt werden.
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
		// Eine Probe zaehlt wie ein Scan; die letzte muss aber auf der Herkunftswelt gescannt werden (Reisen lohnt sich)
		AlienDefinition alien = AlienRegistry.get(world.getRegistryManager(), alienId).orElseThrow();
		if (!alien.dna().starter() && !alien.dna().sources().isEmpty() && DnaScan.progress(player, alienId) + 1 >= alien.dna().samples()) {
			AlienDefinition.DnaSource source = alien.dna().sources().get(0);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.dna_scan.final", TransformationManager.alienName(alienId),
					source.dimension().map(DnaScan::worldName).orElse(Text.translatable("planet.kingdomomnitrix.earth"))).formatted(Formatting.YELLOW), true);
			return TypedActionResult.fail(stack);
		}
		if (DnaScan.addSample(player, alienId)) {
			TransformationManager.select(player, alienId);
		}
		stack.decrementUnlessCreative(1, player);
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.dna_sample.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
