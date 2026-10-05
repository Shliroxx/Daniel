package com.santiq.kingdomomnitrix.client.space;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.hud.HudAnchor;
import com.santiq.kingdomomnitrix.client.hud.HudElement;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.space.ShipEntity;
import com.santiq.kingdomomnitrix.space.SpaceRoute;
import com.santiq.kingdomomnitrix.space.SpaceRouteRegistry;
import com.santiq.kingdomomnitrix.space.SpaceTravel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Cockpit-Anzeige der Aphelion (Standard oben mittig): Tempo, Hoehe, Weg ins All bzw. Navigation zu den naechsten Rissen. */
public final class ShipHud implements HudElement {
	public static final ShipHud INSTANCE = new ShipHud();
	private static final Identifier ID = KingdomOmnitrix.id("ship");
	private static final int WIDTH = 170;
	private static final int MAX_ROUTES = 3;

	private ShipHud() {
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public Text name() {
		return Text.translatable("hud.kingdomomnitrix.element.ship");
	}

	@Override
	public HudAnchor defaultAnchor() {
		return HudAnchor.TOP_CENTER;
	}

	@Override
	public int defaultX() {
		return 0;
	}

	@Override
	public int defaultY() {
		return 24;
	}

	@Override
	public boolean isActive(MinecraftClient client) {
		return client.player != null && client.world != null && client.player.getVehicle() instanceof ShipEntity;
	}

	private static List<Map.Entry<Identifier, SpaceRoute>> nearestRoutes(MinecraftClient client, ShipEntity ship) {
		if (client.world == null || !SpaceTravel.isSpace(client.world)) {
			return List.of();
		}
		return SpaceRouteRegistry.all(client.world.getRegistryManager()).stream()
				.sorted(Comparator.comparingDouble(e -> e.getValue().position().distanceTo(ship.getPos())))
				.limit(MAX_ROUTES).toList();
	}

	@Override
	public int width(MinecraftClient client) {
		return WIDTH;
	}

	@Override
	public int height(MinecraftClient client) {
		if (client.player == null || !(client.player.getVehicle() instanceof ShipEntity ship) || client.world == null) {
			return previewHeight();
		}
		return SpaceTravel.isSpace(client.world) ? 36 + nearestRoutes(client, ship).size() * 10 + 2 : 46;
	}

	@Override
	public int previewWidth() {
		return WIDTH;
	}

	@Override
	public int previewHeight() {
		return 46;
	}

	@Override
	public void render(DrawContext context, MinecraftClient client, float tickDelta) {
		if (client.player == null || client.world == null || !(client.player.getVehicle() instanceof ShipEntity ship)) {
			return;
		}
		TextRenderer font = client.textRenderer;
		double speed = ship.getPos().subtract(ship.prevX, ship.prevY, ship.prevZ).length() * 20.0;
		boolean space = SpaceTravel.isSpace(client.world);
		UiDraw.panel(context, 0, 0, WIDTH, height(client), UiTheme.EXPLORATION);
		context.drawTextWithShadow(font, Text.translatable("hud.kingdomomnitrix.ship.title"), 5, 4, UiTheme.EXPLORATION.accent());
		String stats = String.format("%.0f m/s  ·  Y %d", speed, (int) ship.getY());
		context.drawTextWithShadow(font, stats, WIDTH - 5 - font.getWidth(stats), 4, UiTheme.TEXT);
		int line = 16;
		Text hint = ship.isWarping()
				? Text.translatable("hud.kingdomomnitrix.ship.warp", (ship.warpTicksLeft() + 19) / 20)
				: Text.translatable("hud.kingdomomnitrix.ship.map_hint",
						com.santiq.kingdomomnitrix.client.input.ModKeyBindings.GALAXY_MAP.getBoundKeyLocalizedText());
		context.drawTextWithShadow(font, hint, 5, line, ship.isWarping() ? 0xFFFFD84A : UiTheme.TEXT_SOFT);
		line += 11;
		if (!space) {
			int toSpace = (int) (client.world.getTopY() - SpaceTravel.ATMOSPHERE_MARGIN - ship.getY());
			context.drawTextWithShadow(font, Text.translatable("hud.kingdomomnitrix.ship.to_space", Math.max(0, toSpace)), 5, line, 0xFF9FD8FF);
			return;
		}
		context.drawTextWithShadow(font, Text.translatable("hud.kingdomomnitrix.ship.rifts"), 5, line, 0xFF9FD8FF);
		line += 11;
		for (Map.Entry<Identifier, SpaceRoute> entry : nearestRoutes(client, ship)) {
			Vec3d to = entry.getValue().position().subtract(ship.getPos());
			String arrow = ShipClient.arrow(client.player.getYaw(), to);
			String vertical = to.y > 8 ? " ▲" : to.y < -8 ? " ▼" : "";
			Text text = Text.literal(arrow + " ").append(SpaceRoute.name(entry.getKey()))
					.append(Text.literal("  " + Math.round(to.length()) + " m" + vertical));
			context.drawTextWithShadow(font, text, 9, line, 0xFF000000 | entry.getValue().color());
			line += 10;
		}
	}
}
