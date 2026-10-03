package com.santiq.kingdomomnitrix.galvan;

import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Galvan-Scanner (Erfindung): Grey Matters Analyse fuer jede Form — Ziel markieren und Wissen ueber seine Art sammeln.
 * Nutzt sich ab (Haltbarkeit), damit Grey Matter selbst der schnellere Weg bleibt.
 */
public class GalvanScannerItem extends Item {
	private static final DustParticleEffect TECH = new DustParticleEffect(new Vector3f(0.22f, 1.0f, 0.08f), 1.0f);

	public GalvanScannerItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.success(stack, world.isClient);
		}
		Optional<LivingEntity> found = Targeting.findLivingTarget(player, 20.0);
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}
		LivingEntity target = found.get();
		int before = GreyMatterKnowledge.level(player, target);
		int level = GreyMatterKnowledge.learn(player, target);
		GreyMatterKnowledge.mark(player, target, GreyMatterKnowledge.MARK_TICKS);
		player.sendMessage(Text.translatable(level > before ? "message.kingdomomnitrix.grey_matter_learned" : "message.kingdomomnitrix.grey_matter_known",
				target.getType().getName(), level, GreyMatterKnowledge.MAX_LEVEL, Math.round(level * GreyMatterKnowledge.BONUS_PER_LEVEL * 100))
				.formatted(Formatting.GREEN), true);
		ServerWorld server = player.getServerWorld();
		Vec3d from = player.getEyePos();
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
		for (int i = 1; i <= 10; i++) {
			Vec3d p = from.lerp(to, i / 10.0);
			server.spawnParticles(TECH, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		server.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.8f, 1.6f);
		stack.damage(1, player, hand == Hand.MAIN_HAND ? net.minecraft.entity.EquipmentSlot.MAINHAND : net.minecraft.entity.EquipmentSlot.OFFHAND);
		player.getItemCooldownManager().set(this, 20);
		return TypedActionResult.success(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.kingdomomnitrix.galvan_scanner.tooltip").formatted(Formatting.GRAY));
	}
}
