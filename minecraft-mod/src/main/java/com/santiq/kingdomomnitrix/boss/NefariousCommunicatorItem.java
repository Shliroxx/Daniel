package com.santiq.kingdomomnitrix.boss;

import com.santiq.kingdomomnitrix.registry.ModEntities;
import java.util.List;
import net.minecraft.entity.SpawnReason;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Nefarious-Funkgeraet: ruft Dr. Nefarious herbei. Er landet 8 Bloecke vor dem Spieler; dort ist die Kampfflaeche
 * (Radius 14). Nur ein Nefarious im Umkreis von 96 Bloecken.
 */
public class NefariousCommunicatorItem extends Item {
	public NefariousCommunicatorItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(world instanceof ServerWorld server)) {
			return TypedActionResult.success(stack, true);
		}
		if (!server.getEntitiesByClass(NefariousEntity.class, new Box(user.getBlockPos()).expand(96), entity -> true).isEmpty()) {
			user.sendMessage(Text.translatable("message.kingdomomnitrix.boss_already_here").formatted(Formatting.RED), true);
			return TypedActionResult.fail(stack);
		}
		Vec3d ahead = user.getPos().add(Vec3d.fromPolar(0.0f, user.getYaw()).multiply(8.0));
		int x = (int) Math.floor(ahead.x);
		int z = (int) Math.floor(ahead.z);
		BlockPos pos = new BlockPos(x, server.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		NefariousEntity boss = ModEntities.NEFARIOUS.create(server, null, pos, SpawnReason.EVENT, false, false);
		if (boss == null) {
			return TypedActionResult.fail(stack);
		}
		boss.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, user.getYaw() + 180.0f, 0.0f);
		boss.setHome(pos);
		boss.allowInTown();
		server.spawnEntity(boss);
		server.playSound(null, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 1.0f, 1.4f);
		if (!user.isCreative()) {
			stack.decrement(1);
		}
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.nefarious_communicator.usage").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.nefarious_communicator.warning").formatted(Formatting.RED));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
