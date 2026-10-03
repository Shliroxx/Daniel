package com.santiq.kingdomomnitrix.magic;

import com.santiq.kingdomomnitrix.registry.ModComponents;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.List;
import java.util.Optional;
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

/** Magie-Kristall: Rechtsklick hebt den enthaltenen Zauber um eine Stufe (Feuer → Feura → Feuga). */
public class SpellCrystalItem extends Item {
	public SpellCrystalItem(Settings settings) {
		super(settings);
	}

	public static ItemStack create(Identifier spellId) {
		ItemStack stack = new ItemStack(ModItems.SPELL_CRYSTAL);
		stack.set(ModComponents.SPELL, spellId);
		return stack;
	}

	@Override
	public Text getName(ItemStack stack) {
		Identifier spell = stack.get(ModComponents.SPELL);
		return spell == null ? super.getName(stack)
				: Text.translatable("item.kingdomomnitrix.spell_crystal.named", Text.translatable(SpellDefinition.translationKey(spell, 1)));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.success(stack, true);
		}
		Identifier spellId = stack.get(ModComponents.SPELL);
		Optional<SpellDefinition> spell = spellId == null ? Optional.empty() : SpellRegistry.get(world.getRegistryManager(), spellId);
		if (spell.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.crystal_empty").formatted(Formatting.RED), true);
			return TypedActionResult.fail(stack);
		}
		if (!MagicManager.raiseLevel(player, spellId)) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.spell_max",
					Text.translatable(SpellDefinition.translationKey(spellId, spell.get().maxLevel()))).formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}
		int level = MagicManager.get(player).level(spellId);
		stack.decrementUnlessCreative(1, player);
		player.getServerWorld().spawnParticles(ParticleTypes.ENCHANT, player.getX(), player.getBodyY(0.6), player.getZ(), 40, 0.5, 0.6, 0.5, 0.6);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 1.2f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.spell_learned",
				Text.translatable(SpellDefinition.translationKey(spellId, level))).withColor(spell.get().color()), false);
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.spell_crystal.usage").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
