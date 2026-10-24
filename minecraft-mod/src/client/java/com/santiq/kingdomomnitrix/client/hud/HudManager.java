package com.santiq.kingdomomnitrix.client.hud;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Zeichnet alle {@link HudElement}e an ihrer eingestellten Stelle und Groesse. */
public final class HudManager {
	public static final Identifier LAYER_ID = KingdomOmnitrix.id("hud");
	private static final List<HudElement> ELEMENTS = new ArrayList<>();
	private static final java.util.Set<Identifier> FAILED = new java.util.HashSet<>();
	private static boolean editing;

	private HudManager() {
	}

	public static void register(HudElement element) {
		ELEMENTS.add(element);
	}

	public static void registerLayer() {
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.HOTBAR_AND_BARS, IdentifiedLayer.of(LAYER_ID, HudManager::render)));
	}

	public static List<HudElement> elements() {
		return List.copyOf(ELEMENTS);
	}

	/** Waehrend der Editor offen ist, zeichnet er die Elemente selbst (mit Rahmen und Vorschau). */
	public static void setEditing(boolean value) {
		editing = value;
	}

	/** Bildschirmrechteck eines Elements: x, y, Breite, Hoehe (bereits skaliert). */
	public static int[] bounds(HudElement element, HudLayout.Entry entry, int width, int height, int screenWidth, int screenHeight) {
		int scaledWidth = Math.round(width * entry.scale());
		int scaledHeight = Math.round(height * entry.scale());
		int x = entry.anchor().baseX(screenWidth, scaledWidth) + entry.x();
		int y = entry.anchor().baseY(screenHeight, scaledHeight) + entry.y();
		x = MathHelper.clamp(x, 0, Math.max(0, screenWidth - scaledWidth));
		y = MathHelper.clamp(y, 0, Math.max(0, screenHeight - scaledHeight));
		return new int[] {x, y, scaledWidth, scaledHeight};
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (editing || client.player == null || client.world == null || client.options.hudHidden
				|| client.inGameHud.getDebugHud().shouldShowDebugHud()) {
			return;
		}
		// Omnitrix gehoben: das Geraet und sein Hologramm-Rad haben die Buehne fuer sich
		if (OmnitrixController.raise() > 0.2f) {
			return;
		}
		float tickDelta = tickCounter.getTickDelta(false);
		for (HudElement element : ELEMENTS) {
			HudLayout.Entry entry = HudLayout.get(element);
			if (entry.hidden() || !element.isActive(client)) {
				continue;
			}
			draw(context, client, element, entry, tickDelta);
		}
	}

	public static void draw(DrawContext context, MinecraftClient client, HudElement element, HudLayout.Entry entry, float tickDelta) {
		int[] box = bounds(element, entry, element.width(client), element.height(client),
				context.getScaledWindowWidth(), context.getScaledWindowHeight());
		context.getMatrices().push();
		context.getMatrices().translate(box[0], box[1], 0);
		context.getMatrices().scale(entry.scale(), entry.scale(), 1.0f);
		try {
			element.render(context, client, tickDelta);
		} catch (RuntimeException e) {
			// ein fehlerhaftes Element darf das restliche HUD nicht mitreissen
			if (FAILED.add(element.id())) {
				KingdomOmnitrix.LOGGER.error("HUD-Element {} ist abgestuerzt", element.id(), e);
			}
		} finally {
			context.getMatrices().pop();
		}
	}
}
