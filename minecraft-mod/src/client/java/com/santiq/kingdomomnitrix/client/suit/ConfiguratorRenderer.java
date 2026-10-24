package com.santiq.kingdomomnitrix.client.suit;

import com.santiq.kingdomomnitrix.suit.SuitArmorItem;
import com.santiq.kingdomomnitrix.suit.module.ConfiguratorBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Anzug-Konfigurator in der Welt: ueber dem Holo-Tisch schwebt das eingelegte Teil an einer unsichtbaren Puppe (echtes
 * 3D-Modell), dreht sich langsam und steht in einem Lichtkegel mit zwei kreisenden Ringen in der Farbe des Anzugs.
 */
public class ConfiguratorRenderer implements BlockEntityRenderer<ConfiguratorBlockEntity> {
	private static final float[] IDLE = {0.23f, 0.91f, 1.0f};
	private ArmorStandEntity mannequin;

	public ConfiguratorRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public void render(ConfiguratorBlockEntity table, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light,
			int overlay) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (table.getWorld() == null || client.world == null) {
			return;
		}
		float time = (table.getWorld().getTime() % 72000L) + tickDelta;
		ItemStack piece = table.getStack(0);
		float[] color = piece.getItem() instanceof SuitArmorItem armor ? FusionAltarRenderer.rgb(armor.set().color()) : IDLE;

		matrices.push();
		matrices.translate(0.5f, 13.0f / 16.0f, 0.5f);
		cone(matrices, consumers, color, piece.isEmpty() ? 0.12f : 0.22f);
		matrices.translate(0.0f, 0.55f, 0.0f);
		FusionAltarRenderer.ring(matrices, consumers, 0.42f, 0.015f, color, 0.6f, time * 2.0f, 90.0f);
		FusionAltarRenderer.ring(matrices, consumers, 0.34f, 0.012f, color, 0.4f, -time * 3.0f, 75.0f);
		matrices.pop();

		if (piece.getItem() instanceof SuitArmorItem armor) {
			if (mannequin == null || mannequin.getWorld() != client.world) {
				mannequin = new ArmorStandEntity(EntityType.ARMOR_STAND, client.world);
				mannequin.setInvisible(true);
			}
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				mannequin.equipStack(slot, slot == armor.getSlotType() ? piece : ItemStack.EMPTY);
			}
			// das Teil soll mittig ueber dem Tisch schweben: Hoehe je nach Teil ausgleichen
			float offset = switch (armor.getType()) {
				case HELMET -> -1.25f;
				case CHESTPLATE -> -0.85f;
				case LEGGINGS -> -0.45f;
				default -> -0.05f;
			};
			matrices.push();
			matrices.translate(0.5f, 1.05f + 0.05f * MathHelper.sin(time * 0.08f), 0.5f);
			matrices.scale(0.6f, 0.6f, 0.6f);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 1.5f));
			matrices.translate(0.0f, offset, 0.0f);
			client.getEntityRenderDispatcher().render(mannequin, 0, 0, 0, 0.0f, tickDelta, matrices, consumers,
					LightmapTextureManager.MAX_LIGHT_COORDINATE);
			matrices.pop();
		}
	}

	/** Lichtkegel vom Projektor nach oben (offen, additiv). */
	private static void cone(MatrixStack matrices, VertexConsumerProvider consumers, float[] color, float alpha) {
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f pose = matrices.peek().getPositionMatrix();
		int n = 16;
		float r0 = 0.18f;
		float r1 = 0.42f;
		float h = 1.1f;
		for (int i = 0; i < n; i++) {
			float a0 = MathHelper.TAU * i / n;
			float a1 = MathHelper.TAU * (i + 1) / n;
			float[][] quad = {{MathHelper.cos(a0) * r0, 0, MathHelper.sin(a0) * r0, alpha}, {MathHelper.cos(a1) * r0, 0, MathHelper.sin(a1) * r0, alpha},
					{MathHelper.cos(a1) * r1, h, MathHelper.sin(a1) * r1, 0.0f}, {MathHelper.cos(a0) * r1, h, MathHelper.sin(a0) * r1, 0.0f}};
			for (float[] v : quad) {
				buffer.vertex(pose, v[0], v[1], v[2]).color(color[0], color[1], color[2], v[3]);
			}
			for (int k = 3; k >= 0; k--) {
				buffer.vertex(pose, quad[k][0], quad[k][1], quad[k][2]).color(color[0], color[1], color[2], quad[k][3]);
			}
		}
	}

	@Override
	public boolean rendersOutsideBoundingBox(ConfiguratorBlockEntity table) {
		return true;
	}
}
