package com.santiq.kingdomomnitrix.client.vfx;

import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Verwandlungs-Blitzkugel wie in Alien Evolution („transform bubble“: rotierende Blitze mit Leuchtsaum um den Kopf,
 * 10 Ticks), hier ausgebaut: Blitze schlagen strahlenfoermig aus der Brust, die Kugel waechst aus dem Omnitrix heraus,
 * eine Leuchthuelle pulsiert, beim Aufprall laeuft ein Ring ueber den Boden. Fuer alle Spieler in Sichtweite.
 *
 * <p>Farben: Verwandeln Omnitrix-Gruen, Zurueckverwandeln Weiss-Gruen, Zeitablauf Rot (wie AEs Timeout).</p>
 */
public final class TransformBubble {
	private static final int TICKS = 18;
	private static final int BOLTS = 18;
	private static final int SEGMENTS = 4;
	/** Tick, an dem der Koerper fertig gewachsen ist (Ring am Boden) — passt zu AlienBodyRenderers.IMPACT_TICK */
	private static final int IMPACT = 6;
	private static final int RING_TICKS = 12;
	private static final float GREEN_R = 0.22f, GREEN_G = 1.0f, GREEN_B = 0.08f;

	private record Burst(int playerId, long start, float r, float g, float b, boolean grow) {
	}

	private static final List<Burst> BURSTS = new ArrayList<>();
	private static final Map<Integer, Optional<Identifier>> LAST = new HashMap<>();
	private static final Map<Integer, Long> LAST_END = new HashMap<>();

	private TransformBubble() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TransformBubble::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client.world == null) {
			BURSTS.clear();
			LAST.clear();
			LAST_END.clear();
			return;
		}
		long now = client.world.getTime();
		Set<Integer> seen = new HashSet<>();
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			int id = player.getId();
			seen.add(id);
			TransformationState state = TransformationManager.get(player);
			Optional<Identifier> alien = state.activeAlien();
			Optional<Identifier> previous = LAST.get(id);
			// erster Stand eines Spielers (Einloggen, in Sichtweite kommen) ist kein Wechsel
			if (previous != null && !previous.equals(alien)) {
				if (alien.isPresent()) {
					// Farbmodul des Spielers (Klassisch: das bisherige Gruen)
					int rgb = com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors.of(player).id().equals("green") ? -1
							: com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors.primary(player);
					BURSTS.add(rgb < 0 ? new Burst(id, now, GREEN_R, GREEN_G, GREEN_B, true)
							: new Burst(id, now, ((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f, true));
				} else {
					boolean timeout = now >= LAST_END.getOrDefault(id, Long.MAX_VALUE) - 2;
					BURSTS.add(timeout ? new Burst(id, now, 1.0f, 0.16f, 0.1f, false)
							: new Burst(id, now, 0.75f, 1.0f, 0.65f, false));
				}
			}
			LAST.put(id, alien);
			if (alien.isPresent()) {
				LAST_END.put(id, state.endTick());
			}
		}
		LAST.keySet().retainAll(seen);
		LAST_END.keySet().retainAll(seen);
		BURSTS.removeIf(b -> now - b.start() > Math.max(TICKS, IMPACT + RING_TICKS) || !seen.contains(b.playerId()));
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (BURSTS.isEmpty() || client.world == null || context.matrixStack() == null || context.consumers() == null) {
			return;
		}
		float tickDelta = context.tickCounter().getTickDelta(false);
		float time = client.world.getTime() + tickDelta;
		Vec3d camera = context.camera().getPos();
		VertexConsumerProvider consumers = context.consumers();
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLightning());
		MatrixStack matrices = context.matrixStack();
		for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
			Burst burst = it.next();
			if (!(client.world.getEntityById(burst.playerId()) instanceof AbstractClientPlayerEntity player)) {
				continue;
			}
			// Ego-Sicht: Kugel nicht ins Gesicht (der Bildschirmblitz uebernimmt), Bodenring bleibt
			boolean self = player == client.player && client.options.getPerspective().isFirstPerson();
			Vec3d pos = player.getLerpedPos(tickDelta);
			float height = player.getHeight();
			float age = time - burst.start();
			matrices.push();
			matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			if (!self && age < TICKS) {
				drawBolts(matrix, buffer, burst, age, height, camera.subtract(pos), (int) Math.floor(age));
			}
			if (burst.grow() && age >= IMPACT && age < IMPACT + RING_TICKS) {
				drawRing(matrix, buffer, burst, (age - IMPACT) / RING_TICKS, player.getWidth());
			}
			matrices.pop();
		}
	}

	/** Blitze aus der Brust nach aussen; Verlauf pro Tick neu gewuerfelt (Flackern), Ausrichtung dreht sich. */
	private static void drawBolts(Matrix4f matrix, VertexConsumer buffer, Burst burst, float age, float height, Vec3d toCamera, int tick) {
		float t = age / TICKS;
		// waechst schnell auf, haelt, klappt am Ende zusammen; Zurueckverwandeln umgekehrt (zieht sich ins Omnitrix)
		float size = burst.grow() ? Math.min(1.0f, age / 3.0f) * (t > 0.75f ? 1.0f - (t - 0.75f) * 2.4f : 1.0f)
				: 1.0f - t * 0.85f;
		float fade = t < 0.1f ? t / 0.1f : (t > 0.7f ? (1.0f - t) / 0.3f : 1.0f);
		if (size <= 0.0f || fade <= 0.0f) {
			return;
		}
		float radius = height * 0.78f * size;
		Vector3f center = new Vector3f(0.0f, height * 0.62f, 0.0f);
		Random random = Random.create(burst.playerId() * 31L + burst.start() * 7L + tick);
		float spin = age * 0.25f;
		Vector3f view = new Vector3f((float) toCamera.x, (float) toCamera.y, (float) toCamera.z).sub(center).normalize();
		for (int i = 0; i < BOLTS; i++) {
			// gleichmaessig verteilt (goldener Winkel), langsam rotierend
			float yNorm = 1.0f - (i + 0.5f) / BOLTS * 2.0f;
			float ring = MathHelper.sqrt(1.0f - yNorm * yNorm);
			float angle = i * 2.39996f + spin;
			Vector3f dir = new Vector3f(MathHelper.cos(angle) * ring, yNorm, MathHelper.sin(angle) * ring);
			Vector3f previous = new Vector3f(center).add(new Vector3f(dir).mul(radius * 0.12f));
			for (int s = 1; s <= SEGMENTS; s++) {
				float along = (float) s / SEGMENTS;
				Vector3f next = new Vector3f(center).add(new Vector3f(dir).mul(radius * along));
				if (s < SEGMENTS) {
					next.add((random.nextFloat() - 0.5f) * radius * 0.35f, (random.nextFloat() - 0.5f) * radius * 0.35f,
							(random.nextFloat() - 0.5f) * radius * 0.35f);
				}
				float taper = 1.0f - along * 0.6f;
				ribbon(matrix, buffer, previous, next, view, 0.17f * taper * size, burst.r(), burst.g(), burst.b(), 0.55f * fade);
				ribbon(matrix, buffer, previous, next, view, 0.045f * taper * size, 1.0f, 1.0f, 1.0f, 1.0f * fade);
				previous = next;
			}
		}
		// Leuchthuelle: weiche Scheibe zur Kamera, pulsiert
		float pulse = 0.75f + 0.25f * MathHelper.sin(age * 1.7f);
		disc(matrix, buffer, center, view, radius * 1.1f * pulse, burst.r(), burst.g(), burst.b(), 0.32f * fade);
	}

	/** Kamera zugewandter Streifen von a nach b. */
	private static void ribbon(Matrix4f matrix, VertexConsumer buffer, Vector3f a, Vector3f b, Vector3f view, float width,
			float r, float g, float bl, float alpha) {
		Vector3f side = new Vector3f(b).sub(a).cross(view);
		if (side.lengthSquared() < 1.0e-8f) {
			return;
		}
		side.normalize().mul(width);
		quad(matrix, buffer, new Vector3f(a).sub(side), new Vector3f(a).add(side), new Vector3f(b).add(side), new Vector3f(b).sub(side),
				r, g, bl, alpha, alpha);
	}

	/** Scheibe aus Dreiecks-Segmenten: Mitte hell, Rand transparent. */
	private static void disc(Matrix4f matrix, VertexConsumer buffer, Vector3f center, Vector3f view, float radius,
			float r, float g, float b, float alpha) {
		Vector3f u = new Vector3f(view).cross(0.0f, 1.0f, 0.0f);
		if (u.lengthSquared() < 1.0e-6f) {
			u.set(1.0f, 0.0f, 0.0f);
		}
		u.normalize();
		Vector3f v = new Vector3f(u).cross(view).normalize();
		int steps = 24;
		for (int i = 0; i < steps; i++) {
			float a0 = MathHelper.TAU * i / steps;
			float a1 = MathHelper.TAU * (i + 1) / steps;
			Vector3f p0 = edge(center, u, v, a0, radius);
			Vector3f p1 = edge(center, u, v, a1, radius);
			// Viereck mit zwei Mittelpunkt-Ecken (die Lightning-Ebene zeichnet Vierecke)
			vertex(matrix, buffer, center, r, g, b, alpha);
			vertex(matrix, buffer, center, r, g, b, alpha);
			vertex(matrix, buffer, p1, r, g, b, 0.0f);
			vertex(matrix, buffer, p0, r, g, b, 0.0f);
		}
	}

	private static Vector3f edge(Vector3f center, Vector3f u, Vector3f v, float angle, float radius) {
		return new Vector3f(center).add(new Vector3f(u).mul(MathHelper.cos(angle) * radius)).add(new Vector3f(v).mul(MathHelper.sin(angle) * radius));
	}

	/** Bodenring beim Aufprall: breitet sich aus und blendet aus. */
	private static void drawRing(Matrix4f matrix, VertexConsumer buffer, Burst burst, float t, float width) {
		float inner = width * 0.6f + t * 2.6f;
		float outer = inner + 0.35f * (1.0f - t) + 0.05f;
		float alpha = (1.0f - t) * (1.0f - t) * 0.8f;
		int steps = 40;
		float y = 0.04f;
		for (int i = 0; i < steps; i++) {
			float a0 = MathHelper.TAU * i / steps;
			float a1 = MathHelper.TAU * (i + 1) / steps;
			Vector3f i0 = new Vector3f(MathHelper.cos(a0) * inner, y, MathHelper.sin(a0) * inner);
			Vector3f i1 = new Vector3f(MathHelper.cos(a1) * inner, y, MathHelper.sin(a1) * inner);
			Vector3f o0 = new Vector3f(MathHelper.cos(a0) * outer, y, MathHelper.sin(a0) * outer);
			Vector3f o1 = new Vector3f(MathHelper.cos(a1) * outer, y, MathHelper.sin(a1) * outer);
			quad(matrix, buffer, i0, i1, o1, o0, burst.r(), burst.g(), burst.b(), alpha, 0.0f);
			// Unterseite ebenfalls (keine Rueckseiten-Kappung abhaengig vom Blickwinkel)
			quad(matrix, buffer, o0, o1, i1, i0, burst.r(), burst.g(), burst.b(), 0.0f, alpha);
		}
	}

	/** Viereck a-b-c-d; a/b bekommen alphaA, c/d alphaB. */
	private static void quad(Matrix4f matrix, VertexConsumer buffer, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
			float r, float g, float bl, float alphaA, float alphaB) {
		vertex(matrix, buffer, a, r, g, bl, alphaA);
		vertex(matrix, buffer, b, r, g, bl, alphaA);
		vertex(matrix, buffer, c, r, g, bl, alphaB);
		vertex(matrix, buffer, d, r, g, bl, alphaB);
	}

	private static void vertex(Matrix4f matrix, VertexConsumer buffer, Vector3f p, float r, float g, float b, float a) {
		buffer.vertex(matrix, p.x, p.y, p.z).color(r, g, b, MathHelper.clamp(a, 0.0f, 1.0f));
	}
}
