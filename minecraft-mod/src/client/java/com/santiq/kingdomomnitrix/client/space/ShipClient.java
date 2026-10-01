package com.santiq.kingdomomnitrix.client.space;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.space.ShipEntity;
import com.santiq.kingdomomnitrix.space.SpaceRoute;
import com.santiq.kingdomomnitrix.space.SpaceRouteRegistry;
import com.santiq.kingdomomnitrix.space.SpaceTravel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Schiff auf dem Client: Kamera beim Einsteigen in die Verfolgeransicht (beim Aussteigen zurueck)
 * und Cockpit-Anzeige mit Tempo, Hoehe, Abstand zum All bzw. Navigation zu den Weltraumrissen.
 */
public final class ShipClient {
	public static final Identifier LAYER_ID = KingdomOmnitrix.id("ship");
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private static boolean wasPiloting;
	private static Perspective previousPerspective;

	private ShipClient() {
	}

	public static void register() {
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.HOTBAR_AND_BARS, IdentifiedLayer.of(LAYER_ID, ShipClient::renderHud)));
	}

	public static void tick(MinecraftClient client) {
		boolean inShip = client.player != null && client.player.getVehicle() instanceof ShipEntity;
		if (inShip && !wasPiloting) {
			previousPerspective = client.options.getPerspective();
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		} else if (!inShip && wasPiloting && previousPerspective != null) {
			client.options.setPerspective(previousPerspective);
			previousPerspective = null;
		}
		wasPiloting = inShip;
	}

	public static void reset() {
		wasPiloting = false;
		previousPerspective = null;
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden
				|| !(client.player.getVehicle() instanceof ShipEntity ship)) {
			return;
		}
		TextRenderer font = client.textRenderer;
		double speed = ship.getPos().subtract(ship.prevX, ship.prevY, ship.prevZ).length() * 20.0;
		int width = 170;
		int x = context.getScaledWindowWidth() / 2 - width / 2;
		int y = 24;
		boolean space = SpaceTravel.isSpace(client.world);
		List<Map.Entry<Identifier, SpaceRoute>> routes = space
				? SpaceRouteRegistry.all(client.world.getRegistryManager()).stream()
						.sorted(Comparator.comparingDouble(e -> e.getValue().position().distanceTo(ship.getPos()))).limit(3).toList()
				: List.of();
		int height = 26 + (space ? routes.size() * 10 + 2 : 10);
		context.fill(x, y, x + width, y + height, 0xB0101420);
		context.fill(x, y, x + width, y + 1, 0xFF6A4FB3);
		context.drawTextWithShadow(font, Text.translatable("hud.kingdomomnitrix.ship.title"), x + 4, y + 3, 0xFFC9B8FF);
		String stats = String.format("%.0f m/s  ·  Y %d", speed, (int) ship.getY());
		context.drawTextWithShadow(font, stats, x + width - 4 - font.getWidth(stats), y + 3, 0xFFFFFFFF);
		int line = y + 15;
		if (!space) {
			int toSpace = (int) (client.world.getTopY() - SpaceTravel.ATMOSPHERE_MARGIN - ship.getY());
			context.drawTextWithShadow(font, Text.translatable("hud.kingdomomnitrix.ship.to_space", Math.max(0, toSpace)), x + 4, line, 0xFF9FD8FF);
		} else {
			context.drawTextWithShadow(font, Text.translatable("hud.kingdomomnitrix.ship.rifts"), x + 4, line, 0xFF9FD8FF);
			line += 11;
			for (Map.Entry<Identifier, SpaceRoute> entry : routes) {
				Vec3d to = entry.getValue().position().subtract(ship.getPos());
				String arrow = arrow(client.player.getYaw(), to);
				String vertical = to.y > 8 ? " ▲" : to.y < -8 ? " ▼" : "";
				Text text = Text.literal(arrow + " ").append(SpaceRoute.name(entry.getKey()))
						.append(Text.literal("  " + Math.round(to.length()) + " m" + vertical));
				context.drawTextWithShadow(font, text, x + 8, line, 0xFF000000 | entry.getValue().color());
				line += 10;
			}
		}
	}

	/** Pfeil relativ zur Blickrichtung (8 Richtungen). */
	private static String arrow(float playerYaw, Vec3d to) {
		double targetYaw = Math.toDegrees(Math.atan2(-to.x, to.z));
		double relative = MathHelper.wrapDegrees(targetYaw - playerYaw);
		int index = (int) Math.floorMod(Math.round(relative / 45.0), 8);
		return ARROWS[index];
	}
}
