package com.santiq.kingdomomnitrix.galvan;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.Optional;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Galvan-Labor (Server): Erfindungen herstellen und den Omnitrix-Hack starten. Nur als Grey Matter; alle Voraussetzungen
 * (Wissen, Hack-Stufe, Materialien, Bolts) prueft der Server, der Bildschirm zeigt nur an.
 */
public final class GalvanLab {
	private static final Identifier GREY_MATTER = KingdomOmnitrix.id("grey_matter");

	public enum Result { CRAFTED, NOT_GREY_MATTER, UNKNOWN, KNOWLEDGE, HACK, MISSING }

	private GalvanLab() {
	}

	public static boolean isGreyMatter(net.minecraft.entity.player.PlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(GREY_MATTER::equals).isPresent();
	}

	/** Aktion aus dem Labor: {@code craft} (id = Erfindung) oder {@code hack}. */
	public static void handle(ServerPlayerEntity player, String action, Identifier id) {
		if ("hack".equals(action)) {
			GalvanHack.start(player);
			return;
		}
		if (!"craft".equals(action)) {
			return;
		}
		Result result = craft(player, id);
		if (result == Result.CRAFTED) {
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 1.5f);
		} else {
			String key = switch (result) {
				case NOT_GREY_MATTER -> "message.kingdomomnitrix.galvan_not_grey_matter";
				case UNKNOWN -> "message.kingdomomnitrix.galvan_unknown";
				case KNOWLEDGE -> "message.kingdomomnitrix.galvan_knowledge";
				case HACK -> "message.kingdomomnitrix.galvan_hack";
				default -> "message.kingdomomnitrix.galvan_missing";
			};
			player.sendMessage(Text.translatable(key).formatted(Formatting.RED), true);
		}
	}

	public static Result craft(ServerPlayerEntity player, Identifier id) {
		if (!isGreyMatter(player)) {
			return Result.NOT_GREY_MATTER;
		}
		Optional<GalvanInvention> found = player.getServerWorld().getRegistryManager().getOptional(GalvanInvention.KEY)
				.flatMap(registry -> registry.getOrEmpty(id));
		if (found.isEmpty()) {
			return Result.UNKNOWN;
		}
		GalvanInvention invention = found.get();
		if (GreyMatterKnowledge.total(player) < invention.knowledge()) {
			return Result.KNOWLEDGE;
		}
		if (GalvanHack.state(player).level() < invention.hackLevel()) {
			return Result.HACK;
		}
		if (!player.getAbilities().creativeMode) {
			PlayerInventory inventory = player.getInventory();
			if (!HeroDataAccess.get(player).canAfford(invention.bolts())) {
				return Result.MISSING;
			}
			for (GalvanInvention.Ingredient ingredient : invention.ingredients()) {
				if (inventory.count(ingredient.item()) < ingredient.count()) {
					return Result.MISSING;
				}
			}
			for (GalvanInvention.Ingredient ingredient : invention.ingredients()) {
				take(inventory, ingredient.item(), ingredient.count());
			}
			if (invention.bolts() > 0) {
				HeroDataAccess.update(player, data -> data.addBolts(-invention.bolts()));
			}
		}
		ItemStack stack = new ItemStack(invention.result(), invention.count());
		if (!player.getInventory().insertStack(stack)) {
			player.dropItem(stack, false);
		}
		return Result.CRAFTED;
	}

	private static void take(PlayerInventory inventory, Item item, int amount) {
		for (int slot = 0; slot < inventory.size() && amount > 0; slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.isOf(item)) {
				int taken = Math.min(amount, stack.getCount());
				stack.decrement(taken);
				amount -= taken;
			}
		}
		inventory.markDirty();
	}
}
