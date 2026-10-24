package com.santiq.kingdomomnitrix.npc;

import com.santiq.kingdomomnitrix.registry.ModComponents;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.List;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Setzt einen NPC auf den angeklickten Block (wie ein Spawn-Ei). Die NPC-ID steht in der Komponente. */
public class NpcSpawnItem extends Item {
	public NpcSpawnItem(Settings settings) {
		super(settings);
	}

	public static ItemStack create(Identifier npcId) {
		ItemStack stack = new ItemStack(ModItems.NPC_SPAWNER);
		stack.set(ModComponents.NPC, npcId);
		return stack;
	}

	@Override
	public Text getName(ItemStack stack) {
		Identifier id = stack.get(ModComponents.NPC);
		return id == null ? super.getName(stack) : Text.translatable("item.kingdomomnitrix.npc_spawner.named", NpcDefinition.name(id));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		Identifier id = context.getStack().get(ModComponents.NPC);
		if (id == null) {
			return ActionResult.FAIL;
		}
		if (!(context.getWorld() instanceof ServerWorld world)) {
			return ActionResult.SUCCESS;
		}
		if (NpcRegistry.get(world.getRegistryManager(), id).isEmpty()) {
			if (context.getPlayer() != null) {
				context.getPlayer().sendMessage(Text.translatable("message.kingdomomnitrix.npc_unknown", id.toString()).formatted(Formatting.RED), true);
			}
			return ActionResult.FAIL;
		}
		BlockPos pos = context.getBlockPos().offset(context.getSide());
		NpcEntity npc = spawn(world, id, pos, context.getPlayerYaw() + 180.0f);
		if (npc == null) {
			return ActionResult.FAIL;
		}
		if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
			context.getStack().decrement(1);
		}
		return ActionResult.CONSUME;
	}

	/** Setzt einen NPC in die Welt; null, wenn die Entity nicht erzeugt werden konnte. */
	public static NpcEntity spawn(ServerWorld world, Identifier id, BlockPos pos, float yaw) {
		NpcEntity npc = ModEntities.NPC.create(world, null, pos, SpawnReason.SPAWN_EGG, false, false);
		if (npc == null) {
			return null;
		}
		npc.setNpcId(id);
		npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
		npc.setHeadYaw(yaw);
		npc.setBodyYaw(yaw);
		world.spawnEntity(npc);
		return npc;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.npc_spawner.usage").formatted(Formatting.GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
