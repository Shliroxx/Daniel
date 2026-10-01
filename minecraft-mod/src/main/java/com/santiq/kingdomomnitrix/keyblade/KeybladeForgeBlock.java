package com.santiq.kingdomomnitrix.keyblade;

import com.santiq.kingdomomnitrix.keyblade.KeybladeDefinition.UpgradeCost;
import com.santiq.kingdomomnitrix.registry.ModComponents;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Keyblade-Schmiede: Rechtsklick mit einem Keyblade hebt es um eine Stufe an, wenn Bolts und Materialien
 * im Inventar sind. Danach (und bei fehlendem Material) zeigt sie die Kosten der naechsten Stufe.
 * Im Kreativmodus kostenlos. (Schleichen umgeht in Minecraft die Block-Interaktion, daher kein Schleich-Modus.)
 */
public class KeybladeForgeBlock extends Block {
	public KeybladeForgeBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.forge.hold_keyblade").formatted(Formatting.GRAY), true);
		}
		return ActionResult.success(world.isClient());
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
			Hand hand, BlockHitResult hit) {
		if (!(stack.getItem() instanceof KeybladeItem)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!(world instanceof ServerWorld serverWorld)) {
			return ItemActionResult.success(true);
		}
		Optional<KeybladeDefinition> found = KeybladeRegistry.forStack(world.getRegistryManager(), stack);
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.forge.unknown").formatted(Formatting.RED), true);
			return ItemActionResult.FAIL;
		}
		KeybladeDefinition definition = found.get();
		int level = KeybladeItem.level(stack);
		if (level >= definition.maxLevel()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.forge.max").formatted(Formatting.GOLD), true);
			return ItemActionResult.FAIL;
		}
		UpgradeCost cost = definition.upgrades().get(level - 1);
		boolean free = player.getAbilities().creativeMode;
		List<ItemStack> missing = free ? List.of() : missing(player.getInventory(), cost);
		if (!missing.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.forge.missing", describe(cost)).formatted(Formatting.RED), false);
			world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.4f, 1.6f);
			return ItemActionResult.FAIL;
		}
		if (!free) {
			consume(player.getInventory(), cost);
		}
		stack.set(ModComponents.KEYBLADE_LEVEL, level + 1);
		serverWorld.spawnParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.3, 0.4, 0.5);
		serverWorld.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.05);
		world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 1.2f);
		world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.forge.upgraded", stack.getName(), level + 1)
				.formatted(Formatting.GREEN), false);
		if (level + 1 < definition.maxLevel()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.forge.cost", level + 2,
					describe(definition.upgrades().get(level))).formatted(Formatting.GRAY), false);
		}
		return ItemActionResult.SUCCESS;
	}

	private static List<ItemStack> requirements(UpgradeCost cost) {
		List<ItemStack> needed = new ArrayList<>();
		if (cost.bolts() > 0) {
			needed.add(new ItemStack(ModItems.BOLT, cost.bolts()));
		}
		cost.items().forEach(item -> needed.add(item.copy()));
		return needed;
	}

	private static List<ItemStack> missing(PlayerInventory inventory, UpgradeCost cost) {
		List<ItemStack> missing = new ArrayList<>();
		for (ItemStack needed : requirements(cost)) {
			if (count(inventory, needed) < needed.getCount()) {
				missing.add(needed);
			}
		}
		return missing;
	}

	private static int count(PlayerInventory inventory, ItemStack wanted) {
		int total = 0;
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack candidate = inventory.getStack(slot);
			if (ItemStack.areItemsAndComponentsEqual(candidate, wanted)) {
				total += candidate.getCount();
			}
		}
		return total;
	}

	private static void consume(PlayerInventory inventory, UpgradeCost cost) {
		for (ItemStack needed : requirements(cost)) {
			int remaining = needed.getCount();
			for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
				ItemStack candidate = inventory.getStack(slot);
				if (ItemStack.areItemsAndComponentsEqual(candidate, needed)) {
					int taken = Math.min(remaining, candidate.getCount());
					candidate.decrement(taken);
					remaining -= taken;
				}
			}
		}
		inventory.markDirty();
	}

	private static Text describe(UpgradeCost cost) {
		MutableText text = Text.empty();
		List<ItemStack> needed = requirements(cost);
		for (int i = 0; i < needed.size(); i++) {
			if (i > 0) {
				text.append(", ");
			}
			text.append(needed.get(i).getCount() + "× ").append(needed.get(i).getName());
		}
		return needed.isEmpty() ? Text.translatable("message.kingdomomnitrix.forge.free") : text;
	}
}
