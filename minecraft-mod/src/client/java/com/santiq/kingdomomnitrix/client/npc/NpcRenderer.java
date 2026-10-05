package com.santiq.kingdomomnitrix.client.npc;

import com.santiq.kingdomomnitrix.npc.NpcEntity;
import com.santiq.kingdomomnitrix.quest.QuestDefinition;
import com.santiq.kingdomomnitrix.quest.QuestManager;
import com.santiq.kingdomomnitrix.quest.QuestManager.Status;
import com.santiq.kingdomomnitrix.quest.QuestRegistry;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** NPC-Renderer mit Quest-Markierung ueber dem Kopf: gelbes „!“ = neuer Auftrag, goldenes „?“ = Quest abgeben. */
public class NpcRenderer extends GeoEntityRenderer<NpcEntity> {
	private static final int MARK_AVAILABLE = 0xFFFFE14D;
	private static final int MARK_READY = 0xFFFFA726;

	public NpcRenderer(EntityRendererFactory.Context context) {
		super(context, new NpcModel());
		this.shadowRadius = 0.4f;
	}

	@Override
	public void render(NpcEntity npc, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
		super.render(npc, entityYaw, partialTick, poseStack, bufferSource, packedLight);
		String mark = null;
		int color = 0;
		Status status = questMarker(npc);
		if (status == Status.READY) {
			mark = "?";
			color = MARK_READY;
		} else if (status == Status.AVAILABLE) {
			mark = "!";
			color = MARK_AVAILABLE;
		}
		if (mark == null || dispatcher.getSquaredDistanceToCamera(npc) > 32 * 32) {
			return;
		}
		poseStack.push();
		// Ueber dem Namensschild schweben, leicht auf und ab
		float bob = (float) Math.sin((npc.age + partialTick) * 0.1) * 0.05f;
		poseStack.translate(0.0, npc.getHeight() + 0.95 + bob, 0.0);
		poseStack.multiply(dispatcher.getRotation());
		poseStack.scale(0.06f, -0.06f, 0.06f);
		Matrix4f matrix = poseStack.peek().getPositionMatrix();
		TextRenderer font = getTextRenderer();
		float x = -font.getWidth(mark) / 2.0f;
		font.draw(mark, x, 0, color, true, matrix, bufferSource, TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		poseStack.pop();
	}

	/** READY schlaegt AVAILABLE; null, wenn der NPC fuer den eigenen Spieler nichts hat. */
	private static Status questMarker(NpcEntity npc) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		Identifier npcId = npc.npcId();
		if (player == null || client.world == null || npcId == null) {
			return null;
		}
		Status best = null;
		for (Identifier questId : QuestRegistry.byNpc(client.world.getRegistryManager(), npcId)) {
			Optional<QuestDefinition> quest = QuestRegistry.get(client.world.getRegistryManager(), questId);
			if (quest.isEmpty()) {
				continue;
			}
			Status status = QuestManager.status(player, questId, quest.get());
			if (status == Status.READY) {
				return Status.READY;
			}
			if (status == Status.AVAILABLE) {
				best = Status.AVAILABLE;
			}
		}
		return best;
	}
}
