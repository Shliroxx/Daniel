package com.santiq.kingdomomnitrix.client.suit;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.suit.SuitSet;
import com.santiq.kingdomomnitrix.suit.power.JetpackPayload;
import com.santiq.kingdomomnitrix.suit.power.ShieldHitPayload;
import com.santiq.kingdomomnitrix.suit.power.SuitStatePayload;
import com.santiq.kingdomomnitrix.suit.power.SuitToggles;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Client-Seite der Anzugfaehigkeiten: Zustand vom Server, Anzeige (Schild- und Treibstoffbalken ueber der
 * Schnellleiste), Heli-Gleiten und Schubduesen (Bewegung ist clientseitig) und die Schildblase bei Treffern.
 */
public final class SuitClient {
	private static final Identifier LAYER = KingdomOmnitrix.id("suit_hud");
	private static final int BUBBLE_TICKS = 12;
	private static final int BAR_W = 80;

	private static int set = -1;
	private static float shield;
	private static float maxShield;
	private static int toggles;
	private static float fuelFactor = 1.0f;
	private static float fuel;
	private static boolean thrusting;
	/** Schildblasen: Entity-ID -> (Startzeit, Farbe, zerbrochen) */
	private static final Map<Integer, long[]> BUBBLES = new HashMap<>();

	private SuitClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SuitStatePayload.ID, (payload, context) -> {
			set = payload.set();
			shield = payload.shield();
			maxShield = payload.maxShield();
			toggles = payload.toggles();
			fuelFactor = payload.fuelFactor();
		});
		ClientPlayNetworking.registerGlobalReceiver(ShieldHitPayload.ID, (payload, context) -> {
			if (context.client().world != null) {
				BUBBLES.put(payload.entity(), new long[] {context.client().world.getTime(), payload.color(), payload.broke() ? 1 : 0});
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(SuitClient::tick);
		HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.attachLayerAfter(IdentifiedLayer.HOTBAR_AND_BARS,
				IdentifiedLayer.of(LAYER, SuitClient::hud)));
		WorldRenderEvents.AFTER_TRANSLUCENT.register(SuitClient::bubbles);
	}

	public static SuitSet current() {
		return set >= 0 && set < SuitSet.values().length ? SuitSet.values()[set] : null;
	}

	public static int toggles() {
		return toggles;
	}

	// --- Gleiten und Schub --------------------------------------------------------------------------------------------

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		SuitSet suit = current();
		boolean want = false;
		if (player != null && suit != null && SuitToggles.on(toggles, SuitToggles.FLIGHT) && !player.getAbilities().flying) {
			boolean jump = client.options.jumpKey.isPressed();
			Vec3d v = player.getVelocity();
			if (suit.mobility() == SuitSet.Mobility.GLIDE) {
				if (jump && !player.isOnGround() && v.y < 0 && !player.isTouchingWater()) {
					player.setVelocity(v.x * 1.02, Math.max(v.y, -0.09), v.z * 1.02);
					player.fallDistance = 0.0f;
					want = true;
				}
			} else if (suit.mobility() == SuitSet.Mobility.JETPACK) {
				if (player.isOnGround()) {
					fuel = Math.min(suit.jetpackFuel() * fuelFactor, fuel + 2.0f);
				}
				if (jump && fuel > 0 && !player.isOnGround()) {
					double lift = suit == SuitSet.INFERNOX ? 0.13 : 0.1;
					double cap = suit == SuitSet.INFERNOX ? 0.6 : 0.42;
					player.setVelocity(v.x, Math.min(cap, v.y + lift), v.z);
					player.fallDistance = 0.0f;
					fuel -= 1.0f;
					want = true;
					localFlames(player, suit);
				}
			}
		}
		if (want != thrusting && player != null) {
			thrusting = want;
			ClientPlayNetworking.send(new JetpackPayload(want));
		}
		if (client.world != null) {
			long now = client.world.getTime();
			for (Iterator<long[]> it = BUBBLES.values().iterator(); it.hasNext();) {
				if (now - it.next()[0] > BUBBLE_TICKS) {
					it.remove();
				}
			}
		}
	}

	private static void localFlames(ClientPlayerEntity player, SuitSet suit) {
		float yaw = (float) Math.toRadians(player.bodyYaw);
		double x = player.getX() + Math.sin(yaw) * 0.3;
		double z = player.getZ() - Math.cos(yaw) * 0.3;
		player.getWorld().addParticle(suit == SuitSet.INFERNOX ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, x, player.getY() + 0.8, z, 0,
				-0.15, 0);
	}

	// --- Anzeige ------------------------------------------------------------------------------------------------------

	private static void hud(DrawContext context, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		SuitSet suit = current();
		if (suit == null || client.player == null || client.options.hudHidden || client.player.isSpectator()) {
			return;
		}
		int color = 0xFF000000 | suit.color();
		int x = context.getScaledWindowWidth() / 2 - BAR_W / 2;
		int y = context.getScaledWindowHeight() - 60;
		if (client.player.isCreative()) {
			y += 18;
		}
		if (SuitToggles.on(toggles, SuitToggles.SHIELD) && maxShield > 0) {
			bar(context, x, y, shield / maxShield, color);
		}
		if (suit.mobility() == SuitSet.Mobility.JETPACK && SuitToggles.on(toggles, SuitToggles.FLIGHT) && fuel < suit.jetpackFuel() * fuelFactor) {
			bar(context, x, y + 5, fuel / (suit.jetpackFuel() * fuelFactor), 0xFFFFB02A);
		}
	}

	private static void bar(DrawContext context, int x, int y, float fraction, int color) {
		context.fill(x - 1, y - 1, x + BAR_W + 1, y + 3, 0xA0000000);
		context.fill(x, y, x + Math.round(BAR_W * MathHelper.clamp(fraction, 0.0f, 1.0f)), y + 2, color);
	}

	// --- Schildblase --------------------------------------------------------------------------------------------------

	private static void bubbles(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (BUBBLES.isEmpty() || client.world == null || context.consumers() == null) {
			return;
		}
		float delta = context.tickCounter().getTickDelta(true);
		Vec3d camera = context.camera().getPos();
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();
		long now = client.world.getTime();
		for (Map.Entry<Integer, long[]> entry : BUBBLES.entrySet()) {
			Entity entity = client.world.getEntityById(entry.getKey());
			if (entity == null || (entity == client.player && client.options.getPerspective().isFirstPerson())) {
				continue;
			}
			long[] data = entry.getValue();
			float age = (now - data[0]) + delta;
			float life = 1.0f - age / BUBBLE_TICKS;
			if (life <= 0) {
				continue;
			}
			int c = (int) data[1];
			float[] rgb = {((c >> 16) & 0xFF) / 255.0f, ((c >> 8) & 0xFF) / 255.0f, (c & 0xFF) / 255.0f};
			boolean broke = data[2] != 0;
			Vec3d pos = entity.getLerpedPos(delta).add(0, entity.getHeight() / 2, 0).subtract(camera);
			float radius = entity.getWidth() * 1.3f + (broke ? age * 0.08f : 0.05f * (1 - life));
			matrices.push();
			matrices.translate(pos.x, pos.y, pos.z);
			matrices.scale(1.0f, entity.getHeight() / (entity.getWidth() * 2.0f) * 0.9f, 1.0f);
			hexSphere(matrices, consumers.getBuffer(RenderLayer.getLightning()), radius, rgb, life * (broke ? 0.9f : 0.55f));
			matrices.pop();
		}
	}

	/** Kugel aus Baendern, deren Kanten heller sind: wirkt wie ein Wabenschild. */
	private static void hexSphere(MatrixStack matrices, VertexConsumer buffer, float r, float[] rgb, float alpha) {
		Matrix4f pose = matrices.peek().getPositionMatrix();
		int lat = 8;
		int lon = 14;
		for (int i = 0; i < lat; i++) {
			float t0 = MathHelper.PI * i / lat - MathHelper.HALF_PI;
			float t1 = MathHelper.PI * (i + 1) / lat - MathHelper.HALF_PI;
			for (int j = 0; j < lon; j++) {
				float p0 = MathHelper.TAU * (j + (i % 2) * 0.5f) / lon;
				float p1 = MathHelper.TAU * (j + 1 + (i % 2) * 0.5f) / lon;
				float a = alpha * (0.35f + 0.65f * ((i + j) % 3 == 0 ? 1.0f : 0.4f));
				float[] v0 = sph(t0, p0, r);
				float[] v1 = sph(t0, p1, r);
				float[] v2 = sph(t1, p1, r);
				float[] v3 = sph(t1, p0, r);
				quad(buffer, pose, v0, v1, v2, v3, rgb, a);
				quad(buffer, pose, v3, v2, v1, v0, rgb, a * 0.5f);
			}
		}
	}

	private static float[] sph(float theta, float phi, float r) {
		return new float[] {MathHelper.cos(theta) * MathHelper.cos(phi) * r, MathHelper.sin(theta) * r, MathHelper.cos(theta) * MathHelper.sin(phi) * r};
	}

	private static void quad(VertexConsumer buffer, Matrix4f pose, float[] a, float[] b, float[] c, float[] d, float[] rgb, float alpha) {
		for (float[] v : new float[][] {a, b, c, d}) {
			buffer.vertex(pose, v[0], v[1], v[2]).color(rgb[0], rgb[1], rgb[2], alpha);
		}
	}
}
