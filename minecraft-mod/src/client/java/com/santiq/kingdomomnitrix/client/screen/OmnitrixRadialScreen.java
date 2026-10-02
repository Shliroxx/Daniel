package com.santiq.kingdomomnitrix.client.screen;

import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixClientState;
import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixFeedback;
import com.santiq.kingdomomnitrix.client.omnitrix.SmartScan;
import com.santiq.kingdomomnitrix.omnitrix.ScanRule;
import com.santiq.kingdomomnitrix.networking.TransformRequestPayload;
import com.santiq.kingdomomnitrix.omnitrix.AlienStatus;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixState;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixStatus;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Omnitrix-Schnellwahl fuer den Kampf: Taste halten → Kreis aus den Aliens des aktiven Favoriten-Sets um die
 * Bildschirmmitte; Maus-Richtung waehlt, Loslassen (oder Klick) verwandelt sofort — ohne Arm-Animation. Als Alien wird
 * daraus ein Schnellwechsel. Ohne Favoriten zeigt der Kreis die freigeschalteten Aliens.
 */
public class OmnitrixRadialScreen extends Screen {
	private static final int INNER = 34;
	private static final int OUTER = 92;
	private static final int DEADZONE = 16;
	private static final int ARC_STEPS = 10;

	private record Slot(Identifier id, AlienDefinition alien) {
	}

	private final List<Slot> slots = new ArrayList<>();
	private final boolean fromFavorites;
	private int hovered = -1;
	private final long openedAt = System.nanoTime();

	private OmnitrixRadialScreen(List<Slot> slots, boolean fromFavorites) {
		super(Text.translatable("key.kingdomomnitrix.quick_select"));
		this.slots.addAll(slots);
		this.fromFavorites = fromFavorites;
	}

	/** Schnellwahl oeffnen (Taste gedrueckt). */
	public static void open(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		var manager = client.world.getRegistryManager();
		OmnitrixState device = OmnitrixCore.state(player);
		List<Slot> slots = new ArrayList<>();
		for (Identifier id : device.activeFavorites()) {
			if (HeroDataAccess.get(player).hasAlien(id)) {
				AlienRegistry.get(manager, id).ifPresent(alien -> slots.add(new Slot(id, alien)));
			}
		}
		boolean favorites = !slots.isEmpty();
		if (!favorites) {
			for (Identifier id : AlienRegistry.sortedIds(manager)) {
				if (slots.size() < OmnitrixState.SET_SIZE && HeroDataAccess.get(player).hasAlien(id)) {
					AlienRegistry.get(manager, id).ifPresent(alien -> slots.add(new Slot(id, alien)));
				}
			}
		}
		if (slots.isEmpty()) {
			OmnitrixFeedback.play(OmnitrixCue.ERROR);
			return;
		}
		OmnitrixFeedback.play(OmnitrixCue.OPEN);
		client.setScreen(new OmnitrixRadialScreen(slots, favorites));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Welt bleibt sichtbar: nur leichte Abdunklung im Kreis
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		if (client == null || client.player == null) {
			return;
		}
		updateHovered(mouseX, mouseY);
		float open = Math.min(1.0f, (System.nanoTime() - openedAt) / 1.0e9f / 0.12f);
		float ease = 1.0f - (1.0f - open) * (1.0f - open);
		int cx = width / 2;
		int cy = height / 2;
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
		int count = slots.size();
		float gap = 0.04f;
		Identifier active = TransformationManager.get(client.player).activeAlien().orElse(null);
		Identifier recommended = SmartScan.recommendation().map(ScanRule.Recommendation::alien).orElse(null);
		float pulse = 0.5f + 0.5f * MathHelper.sin((System.nanoTime() % 1_000_000_000_000L) / 1.0e9f * 6.0f);
		for (int i = 0; i < count; i++) {
			float start = segmentStart(i) + gap;
			float end = segmentStart(i + 1) - gap;
			boolean hover = i == hovered;
			int color = slots.get(i).alien().color();
			float inner = INNER * ease;
			float outer = (hover ? OUTER + 8 : OUTER) * ease;
			int alpha = hover ? 0xD0 : 0x88;
			ring(buffer, matrix, cx, cy, inner, outer, start, end, darken(color, hover ? 0.85f : 0.45f), alpha);
			// leuchtende Aussenkante in Alien-Farbe, aktives Alien weiss
			int edge = slots.get(i).id().equals(active) ? 0xFFFFFF : color;
			ring(buffer, matrix, cx, cy, outer - 2.5f, outer, start, end, edge, hover ? 0xFF : 0xC0);
			// Smart-Scan-Empfehlung: pulsierender goldener Aussenring
			if (slots.get(i).id().equals(recommended)) {
				ring(buffer, matrix, cx, cy, outer + 2.0f, outer + 5.0f, start, end, 0xFFD84A, Math.round(120 + 135 * pulse));
			}
		}
		// Mitte: dunkler Kern mit Omnitrix-Gruen
		ring(buffer, matrix, cx, cy, 0.0f, INNER * ease - 3.0f, 0.0f, MathHelper.TAU, 0x0A140C, 0xC8);
		ring(buffer, matrix, cx, cy, INNER * ease - 4.5f, INNER * ease - 3.0f, 0.0f, MathHelper.TAU, statusColor(), 0xFF);
		context.draw();

		for (int i = 0; i < count; i++) {
			float mid = (segmentStart(i) + segmentStart(i + 1)) / 2.0f;
			float radius = (INNER + OUTER) / 2.0f * ease + (i == hovered ? 4 : 0);
			int tx = cx + Math.round(MathHelper.cos(mid) * radius);
			int ty = cy + Math.round(MathHelper.sin(mid) * radius);
			Text name = TransformationManager.alienName(slots.get(i).id());
			context.getMatrices().push();
			context.getMatrices().translate(tx, ty, 0.0f);
			float scale = i == hovered ? 0.9f : 0.75f;
			context.getMatrices().scale(scale, scale, 1.0f);
			context.drawCenteredTextWithShadow(textRenderer, name, 0, -4, i == hovered ? 0xFFFFFFFF : 0xFFCFE8D4);
			context.getMatrices().pop();
		}
		if (hovered >= 0) {
			Slot slot = slots.get(hovered);
			AlienStatus status = OmnitrixCore.alienStatus(client.player, slot.id());
			context.drawCenteredTextWithShadow(textRenderer, TransformationManager.alienName(slot.id()).formatted(Formatting.BOLD),
					cx, cy - 9, 0xFF000000 | slot.alien().color());
			context.getMatrices().push();
			context.getMatrices().translate(cx, cy + 3, 0.0f);
			context.getMatrices().scale(0.7f, 0.7f, 1.0f);
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("alien_status.kingdomomnitrix." + status.name().toLowerCase(java.util.Locale.ROOT)),
					0, 0, 0xFF9ACFA8);
			context.getMatrices().pop();
		}
		if (hovered < 0) {
			SmartScan.recommendation().ifPresent(pick -> {
				context.getMatrices().push();
				context.getMatrices().translate(cx, cy - 4, 0.0f);
				context.getMatrices().scale(0.7f, 0.7f, 1.0f);
				context.drawCenteredTextWithShadow(textRenderer, Text.translatable("scan.kingdomomnitrix.short"), 0, -6, 0xFFFFD84A);
				context.drawCenteredTextWithShadow(textRenderer, TransformationManager.alienName(pick.alien()).formatted(Formatting.BOLD), 0, 5, 0xFFFFFFFF);
				context.getMatrices().pop();
			});
		}
		if (!fromFavorites) {
			context.getMatrices().push();
			context.getMatrices().translate(cx, cy + OUTER + 16, 0.0f);
			context.getMatrices().scale(0.75f, 0.75f, 1.0f);
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("hud.kingdomomnitrix.radial_empty"), 0, 0, 0xFF9ACFA8);
			context.getMatrices().pop();
		}
	}

	private int statusColor() {
		OmnitrixStatus status = client != null && client.player != null ? OmnitrixClientState.status(client.player) : OmnitrixStatus.READY;
		return status == OmnitrixStatus.IDLE ? 0x39FF14 : status.color();
	}

	/** Segment-Anfang im Bogenmass; erstes Segment oben mittig. */
	private float segmentStart(int index) {
		float size = MathHelper.TAU / slots.size();
		return -MathHelper.HALF_PI - size / 2.0f + index * size;
	}

	private void updateHovered(double mouseX, double mouseY) {
		double dx = mouseX - width / 2.0;
		double dy = mouseY - height / 2.0;
		int next = -1;
		if (dx * dx + dy * dy >= DEADZONE * DEADZONE) {
			float angle = (float) Math.atan2(dy, dx);
			float size = MathHelper.TAU / slots.size();
			float relative = angle - segmentStart(0);
			next = Math.floorMod((int) Math.floor(relative / size), slots.size());
		}
		if (next != hovered && next >= 0) {
			OmnitrixFeedback.play(OmnitrixCue.NAVIGATE);
		}
		hovered = next;
	}

	/** Ringsegment als Vierecke (GUI-Ebene zeichnet Vierecke). */
	private static void ring(VertexConsumer buffer, Matrix4f matrix, int cx, int cy, float inner, float outer, float start, float end,
			int rgb, int alpha) {
		int steps = Math.max(2, Math.round(ARC_STEPS * (end - start) / (MathHelper.TAU / 8.0f)));
		int color = alpha << 24 | (rgb & 0xFFFFFF);
		for (int s = 0; s < steps; s++) {
			float a0 = start + (end - start) * s / steps;
			float a1 = start + (end - start) * (s + 1) / steps;
			float c0 = MathHelper.cos(a0);
			float s0 = MathHelper.sin(a0);
			float c1 = MathHelper.cos(a1);
			float s1 = MathHelper.sin(a1);
			buffer.vertex(matrix, cx + c0 * inner, cy + s0 * inner, 0.0f).color(color);
			buffer.vertex(matrix, cx + c1 * inner, cy + s1 * inner, 0.0f).color(color);
			buffer.vertex(matrix, cx + c1 * outer, cy + s1 * outer, 0.0f).color(color);
			buffer.vertex(matrix, cx + c0 * outer, cy + s0 * outer, 0.0f).color(color);
		}
	}

	private static int darken(int rgb, float factor) {
		int r = Math.round(((rgb >> 16) & 0xFF) * factor);
		int g = Math.round(((rgb >> 8) & 0xFF) * factor);
		int b = Math.round((rgb & 0xFF) * factor);
		return r << 16 | g << 8 | b;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (ModKeyBindings.SMART_SELECT.matchesKey(keyCode, scanCode)) {
			// Smart-Wahl aus dem Kreis: Empfehlung nehmen, auch wenn sie nicht im Set ist
			close();
			ModKeyBindings.smartSelect(MinecraftClient.getInstance());
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (ModKeyBindings.QUICK_SELECT.matchesKey(keyCode, scanCode)) {
			choose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			updateHovered(mouseX, mouseY);
			choose();
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			OmnitrixFeedback.play(OmnitrixCue.CANCEL);
			close();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** Gewaehltes Alien anfordern (Server verwandelt bzw. wechselt schnell) und schliessen. */
	private void choose() {
		Optional<Slot> slot = hovered >= 0 && hovered < slots.size() ? Optional.of(slots.get(hovered)) : Optional.empty();
		close();
		if (slot.isEmpty() || client == null || client.player == null) {
			return;
		}
		OmnitrixStatus status = OmnitrixClientState.status(client.player);
		if (status == OmnitrixStatus.LOCKED || status == OmnitrixStatus.OVERHEATED) {
			OmnitrixFeedback.play(OmnitrixCue.ERROR);
			return;
		}
		ClientPlayNetworking.send(new TransformRequestPayload(slot.get().id()));
		OmnitrixFeedback.play(OmnitrixCue.CONFIRM);
	}
}
