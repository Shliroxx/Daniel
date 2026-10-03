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
import org.joml.Vector3f;

import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.OmnitrixPhase;
import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixFeedback;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixStatus;
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

	/** Ego-Haltung: Zifferblatt-Mitte vor der Kamera (Bloecke), Feinkippung (Grad), Winkel von „oben“ auf dem Zifferblatt. */
	private record Pose(float x, float y, float z, float rotateZ, float rotateY, float rotateX, float dialUp) {
		static final Pose DEFAULT = new Pose(-0.45f, -0.14f, -1.1f, 0.0f, 20.0f, -30.0f, -90.0f);
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
				OmnitrixDialDisplay.clearCache();
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
					json.has("dial_up") ? json.get("dial_up").getAsFloat() : Pose.DEFAULT.dialUp());
		} catch (IOException | RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("Omnitrix-Haltung {} unlesbar, nutze Standard", file, e);
		}
		return pose;
	}

	/**
	 * Licht der Sanduhr als Farbe (r, g, b, bereits mit Helligkeit): Farbe und Puls aus dem Geraete-Zustand
	 * ({@link OmnitrixStatus}), dazu Energie beim Oeffnen und der Licht-Puls der Rueckmeldungen.
	 */
	private static float[] glow(AbstractClientPlayerEntity player) {
		boolean local = player == MinecraftClient.getInstance().player;
		OmnitrixStatus status = OmnitrixController.displayStatus(player);
		float seconds = (System.nanoTime() % 1_000_000_000_000L) / 1.0e9f;
		float energy = local ? OmnitrixController.energy() : OmnitrixRemote.energy(player);
		float brightness = Math.max(status.light(seconds), 0.35f + energy * 0.65f);
		if (status == OmnitrixStatus.LOCKED || status == OmnitrixStatus.OVERHEATED || status == OmnitrixStatus.COOLDOWN) {
			brightness = status.light(seconds);
		}
		if (local) {
			brightness = Math.min(1.0f, brightness + OmnitrixFeedback.currentLight() * 0.6f);
		}
		int color = com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors.status(player, status);
		return new float[] {((color >> 16) & 0xFF) / 255.0f * brightness, ((color >> 8) & 0xFF) / 255.0f * brightness,
				(color & 0xFF) / 255.0f * brightness};
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
		float[] glow = glow(player);

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
			renderer.render(matrices.peek(), eyes, null, glowModel, glow[0], glow[1], glow[2], 0xF000F0, OverlayTexture.DEFAULT_UV);
		}
		// Auswahlmodus: Raute mit Alien-Silhouette auf dem Kern (eigener Spieler: gewaehltes Alien; andere: leere Raute)
		float show = local ? OmnitrixController.wheel() : OmnitrixRemote.lift(player);
		OmnitrixDialDisplay.render(matrices, consumers, show, local, pose().dialUp());
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
		// ab hier: Zifferblatt-Raum (x = Blickrichtung des Zifferblatts, y = zur Hand, z = Bildschirm-oben)
		Identifier skin = AlienArms.armTexture(player).orElseGet(() -> player.getSkinTextures().texture());
		firstPerson = true;
		try {
			matrices.push();
			// vom Zifferblatt zurueck zum Armursprung (Armmitte x, Handgelenk 7 Pixel vor dem Handende)
			matrices.translate(-((slim ? 0.5f : 1.0f) + 5.0f) / 16.0f, -7.0f / 16.0f, 0.0f);
			renderPart(matrices, consumers, light, skin, model.leftArm, model.leftSleeve);
			if (wears(player) || OmnitrixController.phase() == OmnitrixPhase.IMPACT) {
				renderOnArm(matrices, consumers, light, model, player, true);
			}
			matrices.pop();
			renderRightHand(matrices, consumers, light, skin, model, slim);
		} finally {
			firstPerson = false;
		}
		matrices.pop();
		consumers.draw();
	}

	/**
	 * Rechte Hand greift im Auswahlmodus ans Zifferblatt (kommt von rechts unten), dreht beim Weiterschalten mit und
	 * drueckt beim Bestaetigen das Zifferblatt herunter. Matrix: Zifferblatt-Raum.
	 */
	private static void renderRightHand(MatrixStack matrices, VertexConsumerProvider consumers, int light, Identifier skin,
			PlayerEntityModel<AbstractClientPlayerEntity> model, boolean slim) {
		OmnitrixPhase phase = OmnitrixController.phase();
		float reach = Math.max(OmnitrixController.wheel(), phase == OmnitrixPhase.IMPACT ? 1.0f : 0.0f);
		if (reach <= 0.01f) {
			return;
		}
		float press = switch (phase) {
			case CONFIRMING -> MathHelper.clamp(OmnitrixController.phaseTime() / OmnitrixController.confirmTime(), 0.0f, 1.0f) * 0.02f;
			case IMPACT -> 0.02f + MathHelper.sin(Math.min(1.0f, OmnitrixController.phaseTime() / 0.18f) * MathHelper.PI) * 0.06f;
			default -> 0.0f;
		};
		float twist = OmnitrixController.twist();
		// Unterarm-Richtung Schulter → Hand im Zifferblatt-Raum: zum Zifferblatt hin, nach links und oben
		Vector3f direction = new Vector3f(-0.3f, -0.45f, 0.85f).normalize();
		// Fingerspitzen am rechten oberen Rand der Fassung, etwas vor dem Zifferblatt
		float hidden = 1.0f - reach;
		Vector3f tip = new Vector3f(0.05f - press, 0.17f + twist * 0.02f, -0.08f)
				.sub(new Vector3f(direction).mul(hidden * 0.6f));
		matrices.push();
		matrices.translate(tip.x(), tip.y(), tip.z());
		matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0f, 1.0f, 0.0f), direction));
		// Handflaeche zum Zifferblatt drehen
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-70.0f + twist * 25.0f));
		// Handende (rechter Arm: Mitte x = -1 bzw. -0,5) an die Fingerspitzen
		matrices.translate((slim ? 0.5f : 1.0f) / 16.0f, -10.0f / 16.0f, 0.0f);
		renderPart(matrices, consumers, light, skin, model.rightArm, model.rightSleeve);
		matrices.pop();
	}

	/** Arm samt Aermel-Schicht ohne eigene Lage zeichnen (die Matrix bestimmt alles). */
	private static void renderPart(MatrixStack matrices, VertexConsumerProvider consumers, int light, Identifier skin, ModelPart arm,
			ModelPart sleeve) {
		ModelTransform armSaved = arm.getTransform();
		ModelTransform sleeveSaved = sleeve.getTransform();
		boolean armVisible = arm.visible;
		try {
			arm.setTransform(ModelTransform.NONE);
			sleeve.setTransform(ModelTransform.NONE);
			arm.visible = true;
			arm.render(matrices, consumers.getBuffer(RenderLayer.getEntitySolid(skin)), light, OverlayTexture.DEFAULT_UV);
			if (sleeve.visible) {
				sleeve.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(skin)), light, OverlayTexture.DEFAULT_UV);
			}
		} finally {
			arm.setTransform(armSaved);
			sleeve.setTransform(sleeveSaved);
			arm.visible = armVisible;
		}
	}
}
