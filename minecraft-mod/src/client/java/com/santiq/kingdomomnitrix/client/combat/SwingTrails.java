package com.santiq.kingdomomnitrix.client.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.santiq.kingdomomnitrix.keyblade.KeybladeItem;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Schwung-Spur wie in Kingdom Hearts: ein leuchtendes Band folgt der Klinge durch den Bogen, der Kopf ist hell,
 * der Schweif verblasst. Form je Angriff (Kombo abwechselnd links/rechts, Luftangriff schraeg, schwerer Hieb von oben,
 * Finisher als ganzer Kreis), Farbe und Reichweite je Waffe. Liegt fest in der Welt (wie im Spiel), nur Client.
 */
public final class SwingTrails {
	/** Farben (Kern innen, Kante aussen) und Klingenlaenge einer Waffe. */
	record Style(float[] core, float[] edge, float reach) {
	}

	/** Bogenform: Startwinkel, Endwinkel (Grad, 0 = vorn, + = rechts), Neigung der Ebene (0 = waagerecht, 90 = senkrecht). */
	record Shape(float from, float to, float roll) {
	}

	private record Trail(Vec3d origin, Vec3d forward, Vec3d right, Shape shape, Style style, int[] age) {
	}

	private static final Style KINGDOM_KEY = new Style(new float[]{1.0f, 0.78f, 0.25f}, new float[]{1.0f, 1.0f, 0.85f}, 1.9f);
	private static final Style OATHKEEPER = new Style(new float[]{0.55f, 0.75f, 1.0f}, new float[]{1.0f, 1.0f, 1.0f}, 1.9f);
	private static final Style OMEGA_KEY = new Style(new float[]{0.6f, 0.2f, 1.0f}, new float[]{0.45f, 1.0f, 0.55f}, 2.0f);
	private static final Style OMNIWRENCH = new Style(new float[]{0.2f, 0.5f, 1.0f}, new float[]{1.0f, 0.85f, 0.3f}, 1.7f);
	private static final Style KEYBLADE = KINGDOM_KEY;
	private static final Style PLAIN = new Style(new float[]{0.75f, 0.8f, 0.9f}, new float[]{1.0f, 1.0f, 1.0f}, 1.5f);

	/** Ticks, in denen der Kopf durch den Bogen laeuft, und Nachleuchten danach. */
	private static final float SWEEP = 3.0f;
	private static final float LAG = 2.0f;
	private static final int LIFE = 9;
	private static final int SEGMENTS = 28;
	private static final float INNER = 0.45f;
	private static final int MAX_TRAILS = 32;
	private static final List<Trail> TRAILS = new ArrayList<>();

	private SwingTrails() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TRAILS.clear());
		WorldRenderEvents.AFTER_TRANSLUCENT.register(SwingTrails::render);
	}

	/** Startet die Spur zur gemeldeten Kampfaktion; andere Aktionen (Ausweichen, Block) haben keine. */
	public static void spawn(AbstractClientPlayerEntity player, String animation) {
		Shape shape = shape(animation);
		if (shape == null) {
			return;
		}
		if (TRAILS.size() >= MAX_TRAILS) {
			TRAILS.remove(0);
		}
		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
		Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);
		Vec3d origin = new Vec3d(player.getX(), player.getBodyY(0.62), player.getZ());
		TRAILS.add(new Trail(origin, forward, right, shape, style(player.getMainHandStack()), new int[]{0}));
	}

	/** Bogen je Angriff; null = keine Spur. */
	static Shape shape(String animation) {
		return switch (animation) {
			case "combo_1" -> new Shape(-85f, 85f, 12f);       // von links nach rechts, leicht ansteigend
			case "combo_2" -> new Shape(85f, -85f, -12f);      // zurueck von rechts nach links
			case "combo_finisher", "air_finisher" -> new Shape(-180f, 180f, 8f); // voller Kreis
			case "air_1" -> new Shape(-80f, 80f, 55f);        // schraeg von oben links
			case "air_2" -> new Shape(80f, -80f, -55f);
			case "heavy" -> new Shape(-110f, 40f, 90f);       // senkrecht von oben hinter dem Kopf nach unten vorn
			default -> null;
		};
	}

	static Style style(ItemStack stack) {
		Item item = stack.getItem();
		if (item == ModItems.KINGDOM_KEY) {
			return KINGDOM_KEY;
		}
		if (item == ModItems.OATHKEEPER) {
			return OATHKEEPER;
		}
		if (item == ModItems.OMEGA_KEY) {
			return OMEGA_KEY;
		}
		if (item == ModItems.OMNIWRENCH) {
			return OMNIWRENCH;
		}
		return item instanceof KeybladeItem ? KEYBLADE : PLAIN;
	}

	private static void tick() {
		Iterator<Trail> it = TRAILS.iterator();
		while (it.hasNext()) {
			if (++it.next().age()[0] > LIFE) {
				it.remove();
			}
		}
	}

	private static void render(WorldRenderContext context) {
		if (TRAILS.isEmpty()) {
			return;
		}
		Vec3d cam = context.camera().getPos();
		float tickDelta = context.tickCounter().getTickDelta(false);
		RenderSystem.enableBlend();
		// normale Mischung: auch am hellen Tag deutlich sichtbar (additiv verschwindet sie vor hellem Himmel)
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		boolean any = false;
		for (Trail trail : TRAILS) {
			any |= emit(buffer, trail, trail.age()[0] + tickDelta, cam);
		}
		if (any) {
			BufferRenderer.drawWithGlobalProgram(buffer.end());
		} else {
			buffer.endNullable();
		}
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	/** Haengt die Vierecke einer Spur an; false, wenn sie (noch/schon) nichts zeigt. */
	private static boolean emit(BufferBuilder buffer, Trail trail, float age, Vec3d cam) {
		float head = MathHelper.clamp(age / SWEEP, 0.0f, 1.0f);
		float tail = MathHelper.clamp((age - LAG) / SWEEP, 0.0f, 1.0f);
		float fade = age <= SWEEP ? 1.0f : MathHelper.clamp(1.0f - (age - SWEEP) / (LIFE - SWEEP), 0.0f, 1.0f);
		if (head - tail < 0.01f || fade <= 0.0f) {
			return false;
		}
		Shape shape = trail.shape();
		Style style = trail.style();
		double roll = Math.toRadians(shape.roll());
		// Ebene des Bogens: vorn + seitlich, Seite um die Blickachse gekippt
		Vec3d side = trail.right().multiply(Math.cos(roll)).add(0.0, Math.sin(roll), 0.0);
		Vec3d base = trail.origin().subtract(cam);
		for (int i = 0; i < SEGMENTS; i++) {
			float s0 = MathHelper.lerp((float) i / SEGMENTS, tail, head);
			float s1 = MathHelper.lerp((float) (i + 1) / SEGMENTS, tail, head);
			// Helligkeit: Schweif 0 -> Kopf 1
			float a0 = (float) Math.pow((float) i / SEGMENTS, 1.1) * fade;
			float a1 = (float) Math.pow((float) (i + 1) / SEGMENTS, 1.1) * fade;
			Vec3d dir0 = direction(trail, side, MathHelper.lerp(s0, shape.from(), shape.to()));
			Vec3d dir1 = direction(trail, side, MathHelper.lerp(s1, shape.from(), shape.to()));
			vertex(buffer, base.add(dir0.multiply(INNER)), style.core(), a0 * 0.05f);
			vertex(buffer, base.add(dir1.multiply(INNER)), style.core(), a1 * 0.05f);
			vertex(buffer, base.add(dir1.multiply(style.reach() * 0.78)), style.core(), a1 * 0.7f);
			vertex(buffer, base.add(dir0.multiply(style.reach() * 0.78)), style.core(), a0 * 0.7f);
			// helle Kante aussen
			vertex(buffer, base.add(dir0.multiply(style.reach() * 0.78)), style.edge(), a0 * 0.95f);
			vertex(buffer, base.add(dir1.multiply(style.reach() * 0.78)), style.edge(), a1 * 0.95f);
			vertex(buffer, base.add(dir1.multiply(style.reach())), style.edge(), a1 * 0.35f);
			vertex(buffer, base.add(dir0.multiply(style.reach())), style.edge(), a0 * 0.35f);
		}
		return true;
	}

	private static Vec3d direction(Trail trail, Vec3d side, float degrees) {
		double angle = Math.toRadians(degrees);
		return trail.forward().multiply(Math.cos(angle)).add(side.multiply(Math.sin(angle)));
	}

	private static void vertex(BufferBuilder buffer, Vec3d pos, float[] color, float alpha) {
		buffer.vertex((float) pos.x, (float) pos.y, (float) pos.z).color(color[0], color[1], color[2], MathHelper.clamp(alpha, 0.0f, 1.0f));
	}
}
