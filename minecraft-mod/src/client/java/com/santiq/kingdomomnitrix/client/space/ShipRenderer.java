package com.santiq.kingdomomnitrix.client.space;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.space.ShipEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Zeichnet die Aphelion als glattes Netz ({@link ShipMesh}) in drei Durchgaengen: Lack und Metall (undurchsichtig,
 * Rueckseiten weg), Glaskuppel (durchscheinend) und Triebwerks-/Positionslichter (additiv, volle Helligkeit,
 * pulsierend; heller mit Pilot). Die Nase neigt sich mit dem Steigwinkel, das Schiff schwebt leicht.
 */
public class ShipRenderer extends EntityRenderer<ShipEntity> {
	private static final Identifier TEXTURE = KingdomOmnitrix.id("textures/entity/ship/aphelion.png");
	/** Drehpunkt fuer das Neigen (Hoehe der Rumpfmitte) */
	private static final float PIVOT_Y = 0.8f;

	public ShipRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 1.6f;
	}

	@Override
	public Identifier getTexture(ShipEntity ship) {
		return TEXTURE;
	}

	@Override
	public void render(ShipEntity ship, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		super.render(ship, yaw, tickDelta, matrices, consumers, light);
		ShipMesh mesh = ShipMesh.get().orElse(null);
		if (mesh == null) {
			return;
		}
		float age = ship.age + tickDelta;
		boolean piloted = ship.hasPassengers();
		float pitch = MathHelper.lerp(tickDelta, ship.prevPitch, ship.getPitch());
		float bob = piloted ? 0.0f : MathHelper.sin(age * 0.07f) * 0.05f;

		matrices.push();
		matrices.translate(0.0, bob, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
		matrices.translate(0.0, PIVOT_Y, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		if (ship.hurtWobble() > 0.0f) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(age * 1.7f) * Math.min(8.0f, ship.hurtWobble() * 0.25f)));
		}
		matrices.translate(0.0, -PIVOT_Y, 0.0);

		MatrixStack.Entry entry = matrices.peek();
		draw(mesh, ShipMesh.SOLID, consumers.getBuffer(RenderLayer.getEntityCutout(TEXTURE)), entry, light, 255, 255);
		float pulse = 0.82f + 0.18f * MathHelper.sin(age * (piloted ? 0.9f : 0.15f));
		int glow = Math.round(255 * (piloted ? pulse : 0.45f * pulse));
		draw(mesh, ShipMesh.GLOW, consumers.getBuffer(RenderLayer.getEyes(TEXTURE)), entry, LightmapTextureManager.MAX_LIGHT_COORDINATE,
				glow, 255);
		draw(mesh, ShipMesh.GLASS, consumers.getBuffer(RenderLayer.getItemEntityTranslucentCull(TEXTURE)), entry, light, 255, 255);
		matrices.pop();
	}

	private static void draw(ShipMesh mesh, int layer, VertexConsumer buffer, MatrixStack.Entry entry, int light, int brightness, int alpha) {
		Matrix4f pose = entry.getPositionMatrix();
		for (ShipMesh.Part part : mesh.parts()) {
			if (part.layer() != layer) {
				continue;
			}
			float[] pos = part.positions();
			float[] nrm = part.normals();
			float[] uv = part.uvs();
			int[] tris = part.triangles();
			for (int t = 0; t < tris.length; t += 3) {
				// Dreieck als Viereck mit doppelter letzter Ecke (die Entity-Ebenen zeichnen Vierecke)
				emit(buffer, entry, pose, pos, nrm, uv, tris[t], light, brightness, alpha);
				emit(buffer, entry, pose, pos, nrm, uv, tris[t + 1], light, brightness, alpha);
				emit(buffer, entry, pose, pos, nrm, uv, tris[t + 2], light, brightness, alpha);
				emit(buffer, entry, pose, pos, nrm, uv, tris[t + 2], light, brightness, alpha);
			}
		}
	}

	private static void emit(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f pose, float[] pos, float[] nrm, float[] uv, int i,
			int light, int brightness, int alpha) {
		buffer.vertex(pose, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])
				.color(brightness, brightness, brightness, alpha)
				.texture(uv[i * 2], uv[i * 2 + 1])
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(light)
				.normal(entry, nrm[i * 3], nrm[i * 3 + 1], nrm[i * 3 + 2]);
	}
}
