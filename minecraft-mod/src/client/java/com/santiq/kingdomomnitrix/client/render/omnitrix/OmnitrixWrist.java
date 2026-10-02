package com.santiq.kingdomomnitrix.client.render.omnitrix;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.render.alien.AlienArms;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.OmnitrixPhase;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import java.io.IOException;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Das Omnitrix als 3D-Geraet am linken Handgelenk (Third-Person und Ego-Sicht).
 *
 * <p>Drei Modellteile aus {@code tools/generate_item_models.py} ({@code models/omnitrix/}): Armband mit Gehaeuse,
 * Kern mit Zifferblatt (faehrt beim Oeffnen des Alien-Rads heraus und wird beim Verwandeln heruntergeschlagen) und
 * eine Leuchtschicht (Sanduhr, Eck-Leuchten), die additiv und unabhaengig vom Licht gezeichnet wird.</p>
 *
 * <p>Ego-Sicht: Der linke Arm ist in Minecraft sonst nie zu sehen. Solange das Alien-Rad offen ist oder kurz nach der
 * Auswahl (Schlag aufs Zifferblatt) hebt sich der linke Arm ins Bild — wie in der Serie.</p>
 */
public final class OmnitrixWrist {
	public static final Identifier BASE = KingdomOmnitrix.id("omnitrix/wrist_base");
	public static final Identifier CORE = KingdomOmnitrix.id("omnitrix/wrist_core");
	public static final Identifier GLOW = KingdomOmnitrix.id("omnitrix/wrist_glow");
	/** Hub des Kerns in Modell-Pixeln, wenn das Geraet offen ist */
	private static final float CORE_LIFT = 1.1f;
	/** true, solange der Ego-Arm gezeichnet wird (die Third-Person-Armhaltung gilt dann nicht) */
	private static boolean firstPerson;
	/** Ego-Haltung aus {@code omnitrix/first_person.json}; null = beim naechsten Bild neu laden */
	private static Pose pose;

	/** Ego-Haltung: Zifferblatt-Mitte vor der Kamera (Bloecke), Feinkippung (Grad), Winkel des gewaehlten Segments. */
	private record Pose(float x, float y, float z, float rotateZ, float rotateY, float rotateX, float discAngle) {
		static final Pose DEFAULT = new Pose(0.05f, -0.3f, -1.0f, 0.0f, 0.0f, -25.0f, -90.0f);
	}

	private OmnitrixWrist() {
	}

	public static void register() {
		ModelLoadingPlugin.register(context -> context.addModels(BASE, CORE, GLOW));
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return KingdomOmnitrix.id("omnitrix_pose");
			}

			@Override
			public void reload(ResourceManager manager) {
				pose = null;
				OmnitrixDisc.clearCache();
			}
		});
		WorldRenderEvents.START.register(context -> OmnitrixController.frame());
		OmnitrixRemote.register();
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer playerRenderer) {
				helper.register(new OmnitrixFeatureRenderer(playerRenderer));
			}
		});
	}

	public static boolean isRenderingFirstPerson() {
		return firstPerson;
	}

	/** Ob der Spieler ein Omnitrix traegt. Fremde Spieler: ihr Inventar kennt der Client nicht — wer es je benutzt hat. */
	public static boolean wears(AbstractClientPlayerEntity player) {
		TransformationState state = TransformationManager.get(player);
		if (state.isTransformed()) {
			return false;
		}
		if (player == MinecraftClient.getInstance().player) {
			return OmnitrixItem.hasOmnitrix(player);
		}
		return state.selectedAlien().isPresent() || state.startTick() > 0L;
	}

	private static Pose pose() {
		if (pose != null) {
			return pose;
		}
		Identifier file = KingdomOmnitrix.id("omnitrix/first_person.json");
		pose = Pose.DEFAULT;
		var resource = MinecraftClient.getInstance().getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return pose;
		}
		try (var reader = resource.get().getReader()) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			JsonArray offset = json.getAsJsonArray("offset");
			pose = new Pose(offset.get(0).getAsFloat(), offset.get(1).getAsFloat(), offset.get(2).getAsFloat(),
					json.get("rotate_z").getAsFloat(), json.get("rotate_y").getAsFloat(), json.get("rotate_x").getAsFloat(),
					json.has("disc_angle") ? json.get("disc_angle").getAsFloat() : Pose.DEFAULT.discAngle());
		} catch (IOException | RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("Omnitrix-Haltung {} unlesbar, nutze Standard", file, e);
		}
		return pose;
	}

	/** Leuchtstaerke der Sanduhr: Grundglimmen, offen heller, Schlag voll; Abklingzeit gedimmt und blinkend */
	private static float glow(AbstractClientPlayerEntity player) {
		if (player == MinecraftClient.getInstance().player && OmnitrixController.phase() == OmnitrixPhase.COOLDOWN) {
			return 0.15f + 0.15f * (MathHelper.sin(System.nanoTime() / 1.0e9f * 6.0f) * 0.5f + 0.5f);
		}
		return MathHelper.clamp(0.5f + OmnitrixRemote.energy(player) * 0.5f, 0.0f, 1.0f);
	}

	/**
	 * Zeichnet das Omnitrix am linken Arm. Die Matrix steht im Modellraum des Spielers (wie bei Feature-Renderern);
	 * die Lage des Arms kommt aus {@code model.leftArm}.
	 */
	public static void renderOnArm(MatrixStack matrices, VertexConsumerProvider consumers, int light,
			PlayerEntityModel<AbstractClientPlayerEntity> model, AbstractClientPlayerEntity player, boolean local) {
		MinecraftClient client = MinecraftClient.getInstance();
		BakedModel base = client.getBakedModelManager().getModel(BASE);
		BakedModel core = client.getBakedModelManager().getModel(CORE);
		BakedModel glowModel = client.getBakedModelManager().getModel(GLOW);
		BakedModel missing = client.getBakedModelManager().getMissingModel();
		if (base == null || base == missing) {
			return;
		}
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
		float coreLift = OmnitrixRemote.lift(player) * CORE_LIFT;
		float glow = glow(player);

		matrices.push();
		model.leftArm.rotate(matrices);
		// Armmitte (breit x = 1, schmal x = 0,5) am Handende (y = 10); Modell-y zeigt zur Schulter
		matrices.translate((slim ? 0.5f : 1.0f) / 16.0f, 10.0f / 16.0f, 0.0f);
		if (slim) {
			matrices.scale(0.8f, 1.0f, 1.0f);
		}
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0f));
		matrices.translate(-0.5f, 0.0f, -0.5f);

		BlockModelRenderer renderer = client.getBlockRenderManager().getModelRenderer();
		var solid = consumers.getBuffer(TexturedRenderLayers.getEntityCutout());
		renderer.render(matrices.peek(), solid, null, base, 1.0f, 1.0f, 1.0f, light, OverlayTexture.DEFAULT_UV);
		matrices.push();
		matrices.translate(coreLift / 16.0f, 0.0f, 0.0f);
		renderer.render(matrices.peek(), solid, null, core, 1.0f, 1.0f, 1.0f, light, OverlayTexture.DEFAULT_UV);
		if (glowModel != null && glowModel != missing) {
			var eyes = consumers.getBuffer(RenderLayer.getEyes(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
			// Kern-Leuchten faehrt mit, Eck-Leuchten liegen am Gehaeuse (Hub dort unsichtbar klein)
			renderer.render(matrices.peek(), eyes, null, glowModel, glow, glow, glow, 0xF000F0, OverlayTexture.DEFAULT_UV);
		}
		// Omniverse-Auswahlscheibe klappt aus dem Kern auf (eigener Spieler: Roster; andere: nur die Scheibe)
		float open = local ? OmnitrixController.wheel() : OmnitrixRemote.lift(player);
		OmnitrixDisc.render(matrices, consumers, open, local, pose().discAngle());
		matrices.pop();
		matrices.pop();
	}

	/**
	 * Ego-Sicht: hebt den linken Arm mit dem Omnitrix ins Bild. Aufruf nach dem normalen Hand-Rendern; die Matrix ist
	 * die Kamera-Matrix der Hand (inklusive Wackeln beim Gehen).
	 */
	public static void renderFirstPerson(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, ClientPlayerEntity player,
			int light) {
		float raise = OmnitrixController.raise();
		if (raise <= 0.0f || player.isInvisible()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (!(client.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerEntityRenderer playerRenderer)) {
			return;
		}
		PlayerEntityModel<AbstractClientPlayerEntity> model = playerRenderer.getModel();
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
		// Schlag aufs Zifferblatt: kurzer Ruck nach unten
		float punch = OmnitrixController.phase() == OmnitrixPhase.IMPACT
				? MathHelper.sin(Math.min(1.0f, OmnitrixController.phaseTime() / 0.18f) * MathHelper.PI) * 0.06f : 0.0f;
		float hidden = 1.0f - raise;
		float ease = 1.0f - hidden * hidden;
		Pose p = pose();
		matrices.push();
		// Zifferblatt-Mitte an die gewuenschte Stelle vor der Kamera; beim Heben kommt der Arm von links unten herein
		matrices.translate(p.x() - hidden * 0.35f, p.y() - hidden * 0.45f - punch, p.z());
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.rotateY() + hidden * 35.0f));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotateX() * ease));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotateZ() - hidden * 25.0f));
		// Grundlage: Arm waagerecht von links (Hand zeigt nach rechts), Zifferblatt (+x des Arms) zur Kamera
		matrices.multiply(new Quaternionf().setFromNormalized(new Matrix3f(0, 0, 1, 1, 0, 0, 0, 1, 0)));
		// vom Zifferblatt zurueck zum Armursprung (Armmitte x, Handgelenk 7 Pixel vor dem Handende)
		matrices.translate(-((slim ? 0.5f : 1.0f) + 5.0f) / 16.0f, -7.0f / 16.0f, 0.0f);

		ModelPart arm = model.leftArm;
		ModelPart sleeve = model.leftSleeve;
		ModelTransform armSaved = arm.getTransform();
		ModelTransform sleeveSaved = sleeve.getTransform();
		boolean armVisible = arm.visible;
		firstPerson = true;
		try {
			arm.setTransform(ModelTransform.NONE);
			sleeve.setTransform(ModelTransform.NONE);
			arm.visible = true;
			Identifier skin = AlienArms.armTexture(player).orElseGet(() -> player.getSkinTextures().texture());
			arm.render(matrices, consumers.getBuffer(RenderLayer.getEntitySolid(skin)), light, OverlayTexture.DEFAULT_UV);
			if (sleeve.visible) {
				sleeve.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(skin)), light, OverlayTexture.DEFAULT_UV);
			}
			if (wears(player) || OmnitrixController.phase() == OmnitrixPhase.IMPACT) {
				renderOnArm(matrices, consumers, light, model, player, true);
			}
		} finally {
			arm.setTransform(armSaved);
			sleeve.setTransform(sleeveSaved);
			arm.visible = armVisible;
			firstPerson = false;
		}
		matrices.pop();
		consumers.draw();
	}
}
