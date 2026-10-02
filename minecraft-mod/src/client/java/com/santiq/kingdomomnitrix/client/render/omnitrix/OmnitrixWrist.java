package com.santiq.kingdomomnitrix.client.render.omnitrix;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import com.santiq.kingdomomnitrix.client.screen.OmnitrixWheelScreen;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import java.io.IOException;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
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
	/** Hub des Kerns in Modell-Pixeln, wenn das Rad offen ist */
	private static final float CORE_LIFT = 1.1f;
	/** Dauer des Schlags aufs Zifferblatt nach der Auswahl (Sekunden) */
	private static final float SLAM_SECONDS = 0.55f;

	/** Arm-Anhebung (0 = unten/unsichtbar, 1 = vor der Kamera) und Kern-Hub, je Bild nachgefuehrt */
	private static float raise;
	private static float lift;
	private static long lastFrame;
	private static long slamStart = Long.MIN_VALUE;
	/** Ego-Haltung aus {@code omnitrix/first_person.json}; null = beim naechsten Bild neu laden */
	private static Pose pose;

	/** Haltung des gehobenen linken Arms (Versatz in Bloecken, Winkel in Grad). */
	private record Pose(float x, float y, float z, float rotateZ, float rotateY, float rotateX, float roll) {
		static final Pose DEFAULT = new Pose(0.15f, 0.3f, 0.0f, -35.0f, 0.0f, 15.0f, 100.0f);
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
			}
		});
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer playerRenderer) {
				helper.register(new OmnitrixFeatureRenderer(playerRenderer));
			}
		});
	}

	/** Aufruf bei der Alien-Auswahl: Arm bleibt oben, Kern wird heruntergeschlagen, Sanduhr blitzt. */
	public static void slam() {
		slamStart = System.nanoTime();
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

	private static float slamProgress() {
		if (slamStart == Long.MIN_VALUE) {
			return -1.0f;
		}
		float t = (System.nanoTime() - slamStart) / 1.0e9f / SLAM_SECONDS;
		return t > 1.0f ? -1.0f : t;
	}

	/** Fuehrt Anhebung und Kern-Hub weich nach (einmal pro Bild). */
	public static void update() {
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0.0f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		MinecraftClient client = MinecraftClient.getInstance();
		boolean wheel = client.currentScreen instanceof OmnitrixWheelScreen;
		float slam = slamProgress();
		float targetRaise = wheel || slam >= 0.0f ? 1.0f : 0.0f;
		float targetLift = wheel ? 1.0f : 0.0f;
		raise += (targetRaise - raise) * Math.min(1.0f, dt * (targetRaise > raise ? 9.0f : 6.0f));
		lift += (targetLift - lift) * Math.min(1.0f, dt * (slam >= 0.0f ? 30.0f : 10.0f));
		if (raise < 0.002f) {
			raise = 0.0f;
		}
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
					json.has("roll") ? json.get("roll").getAsFloat() : 0.0f);
		} catch (IOException | RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("Omnitrix-Haltung {} unlesbar, nutze Standard", file, e);
		}
		return pose;
	}

	public static float raise() {
		return raise;
	}

	/** Leuchtstaerke: Grundglimmen, beim Oeffnen heller, beim Schlag ein Blitz */
	private static float glow() {
		float slam = slamProgress();
		float flash = slam >= 0.0f ? 1.0f - slam : 0.0f;
		return MathHelper.clamp(0.55f + lift * 0.35f + flash * 0.6f, 0.0f, 1.0f);
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
		float coreLift = local ? lift * CORE_LIFT : 0.0f;
		float glow = local ? glow() : 0.55f;

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
		matrices.pop();
		matrices.pop();
	}

	/**
	 * Ego-Sicht: hebt den linken Arm mit dem Omnitrix ins Bild. Aufruf nach dem normalen Hand-Rendern; die Matrix ist
	 * die Kamera-Matrix der Hand (inklusive Wackeln beim Gehen).
	 */
	public static void renderFirstPerson(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, ClientPlayerEntity player,
			int light) {
		update();
		if (raise <= 0.0f || player.isInvisible()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (!(client.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerEntityRenderer playerRenderer)) {
			return;
		}
		float slam = slamProgress();
		// kurzer Ruck nach unten beim Schlag aufs Zifferblatt
		float punch = slam >= 0.0f ? MathHelper.sin(Math.min(1.0f, slam * 2.5f) * MathHelper.PI) * 0.08f : 0.0f;
		float hidden = 1.0f - raise;
		Pose p = pose();
		matrices.push();
		// Grundlage: vanilla-Armhaltung (gespiegelt fuer links), dann ins Bild gehoben und zur Kamera gedreht
		matrices.translate(-0.64f + p.x() * raise, -0.6f - hidden * 0.9f - punch + p.y() * raise, -0.72f + p.z() * raise);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45.0f));
		matrices.translate(1.0f, 3.6f, 3.5f);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-120.0f));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(200.0f));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(135.0f));
		matrices.translate(-5.6f, 0.0f, 0.0f);
		// Unterarm quer vor die Brust und Handgelenk-Aussenseite zur Kamera
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotateZ() * raise));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.rotateY() * raise));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotateX() * raise));
		// Arm um die eigene Laengsachse drehen (Armmitte x = 6 Pixel, z = 0), damit das Zifferblatt zur Kamera zeigt
		matrices.translate(6.0f / 16.0f, 0.0f, 0.0f);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.roll() * raise));
		matrices.translate(-6.0f / 16.0f, 0.0f, 0.0f);
		playerRenderer.renderLeftArm(matrices, consumers, light, player);
		if (wears(player)) {
			renderOnArm(matrices, consumers, light, playerRenderer.getModel(), player, true);
		}
		matrices.pop();
		consumers.draw();
	}
}
