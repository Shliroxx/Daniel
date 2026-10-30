package com.santiq.kingdomomnitrix.client.menu;

import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.player.HeroData;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.space.SpaceRoute;
import com.santiq.kingdomomnitrix.space.SpaceRouteRegistry;
import com.santiq.kingdomomnitrix.space.SpaceTravel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Weltkarte (Reiter „Karte“) wie die Weltauswahl in Kingdom Hearts: die Galaxie von oben mit allen Weltraumrissen
 * als Planeten. Unentdeckte Welten bleiben „???“, bis man sie einmal betreten hat. Rechts die Liste aller Welten.
 */
public class WorldMapScreen extends Screen {
	private static final int SIDEBAR = 118;
	private static final int PLANET_RADIUS = 7;
	private static final int SPACE_BACK = 0xFF070A14;
	private static final List<Identifier> OTHER_DIMENSIONS = List.of(World.NETHER.getValue(), World.END.getValue());

	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;

	/** Ein Planet auf der Karte. */
	private record Planet(Identifier route, SpaceRoute data, int x, int y, boolean discovered, boolean here) {
	}

	public WorldMapScreen() {
		super(Text.translatable("menu.kingdomomnitrix.tab.map"));
	}

	@Override
	protected void init() {
		panelWidth = Math.min(width - MenuTabs.RESERVED - 8, 380);
		panelHeight = Math.min(height - 12, 224);
		left = MenuTabs.RESERVED + (width - MenuTabs.RESERVED - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		MenuTabs.addTo(this, MenuTab.MAP, this::addDrawableChild);
	}

	private int mapLeft() {
		return left + 6;
	}

	private int mapTop() {
		return top + 20;
	}

	private int mapWidth() {
		return panelWidth - SIDEBAR - 16;
	}

	private int mapHeight() {
		return panelHeight - 26;
	}

	private static boolean discovered(HeroData data, Identifier dimension) {
		return dimension.equals(World.OVERWORLD.getValue()) || data.hasFlag("explored:dimension:" + dimension);
	}

	/** Planeten an ihrer Position im All, eingepasst in die Kartenflaeche (Norden oben). */
	private List<Planet> planets(ClientPlayerEntity player) {
		List<Map.Entry<Identifier, SpaceRoute>> routes = SpaceRouteRegistry.all(player.getWorld().getRegistryManager());
		double extent = extentOf(player);
		HeroData data = HeroDataAccess.get(player);
		Identifier current = player.getWorld().getRegistryKey().getValue();
		List<Planet> result = new ArrayList<>();
		for (Map.Entry<Identifier, SpaceRoute> entry : routes) {
			int[] point = project(entry.getValue().position().x, entry.getValue().position().z, extent);
			Identifier destination = entry.getValue().destination();
			result.add(new Planet(entry.getKey(), entry.getValue(), point[0], point[1], discovered(data, destination), destination.equals(current)));
		}
		return result;
	}

	private int[] project(double x, double z, double extent) {
		double scale = Math.min(mapWidth(), mapHeight()) / 2.0 - PLANET_RADIUS - 10;
		int cx = mapLeft() + mapWidth() / 2;
		int cy = mapTop() + mapHeight() / 2;
		return new int[] {cx + (int) Math.round(x / extent * scale), cy + (int) Math.round(z / extent * scale)};
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		ClientPlayerEntity player = client != null ? client.player : null;
		if (player == null || client.world == null) {
			return;
		}
		UiDraw.panel(context, left, top, panelWidth, panelHeight, UiTheme.EXPLORATION);
		context.drawTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), left + 8, top + 6, UiTheme.EXPLORATION.accent());

		// Weltraum mit festen Sternen
		context.fill(mapLeft(), mapTop(), mapLeft() + mapWidth(), mapTop() + mapHeight(), SPACE_BACK);
		Random stars = new Random(42);
		for (int i = 0; i < 140; i++) {
			int sx = mapLeft() + stars.nextInt(mapWidth());
			int sy = mapTop() + stars.nextInt(mapHeight());
			int brightness = 90 + stars.nextInt(140);
			context.fill(sx, sy, sx + 1, sy + 1, 0xFF000000 | brightness << 16 | brightness << 8 | Math.min(255, brightness + 30));
		}
		context.drawBorder(mapLeft(), mapTop(), mapWidth(), mapHeight(), UiTheme.EXPLORATION.border());

		List<Planet> planets = planets(player);
		double extent = extentOf(player);
		int[] hub = project(0, 0, extent);
		long time = client.world.getTime();
		Planet hovered = null;
		for (Planet planet : planets) {
			dottedLine(context, hub[0], hub[1], planet.x(), planet.y(), planet.discovered() ? 0x80C48BFF : 0x40808080);
		}
		for (Planet planet : planets) {
			int color = planet.discovered() ? 0xFF000000 | planet.data().color() : 0xFF3A3F4A;
			disc(context, planet.x(), planet.y(), PLANET_RADIUS, color);
			disc(context, planet.x() - 2, planet.y() - 2, 2, planet.discovered() ? 0x60FFFFFF : 0x30FFFFFF);
			if (planet.here()) {
				ring(context, planet.x(), planet.y(), PLANET_RADIUS + 3 + (int) (time / 5 % 3), 0xFFFFC94A);
			}
			Text name = planet.discovered() ? SpaceRoute.name(planet.route()) : Text.literal("???");
			context.drawCenteredTextWithShadow(textRenderer, name, planet.x(), planet.y() + PLANET_RADIUS + 3,
					planet.discovered() ? UiTheme.TEXT : UiTheme.TEXT_DISABLED);
			if (Math.abs(mouseX - planet.x()) <= PLANET_RADIUS + 2 && Math.abs(mouseY - planet.y()) <= PLANET_RADIUS + 2) {
				hovered = planet;
			}
		}
		if (SpaceTravel.isSpace(client.world)) {
			int[] me = project(player.getX(), player.getZ(), extent);
			float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
			context.fill(me[0] - 2, me[1] - 2, me[0] + 2, me[1] + 2, 0xFFFFC94A);
			context.fill(me[0] + Math.round(-MathHelper.sin(yaw) * 5) - 1, me[1] + Math.round(MathHelper.cos(yaw) * 5) - 1,
					me[0] + Math.round(-MathHelper.sin(yaw) * 5) + 1, me[1] + Math.round(MathHelper.cos(yaw) * 5) + 1, 0xFFFFFFFF);
		}

		renderSidebar(context, player, planets);
		if (hovered != null) {
			List<Text> lines = new ArrayList<>();
			lines.add(hovered.discovered() ? SpaceRoute.name(hovered.route()).copy().formatted(Formatting.BOLD)
					: Text.literal("???").formatted(Formatting.GRAY));
			lines.add(Text.translatable(hovered.discovered() ? "screen.kingdomomnitrix.map.discovered" : "screen.kingdomomnitrix.map.undiscovered")
					.formatted(hovered.discovered() ? Formatting.GREEN : Formatting.GRAY));
			if (hovered.here()) {
				lines.add(Text.translatable("screen.kingdomomnitrix.map.here").formatted(Formatting.GOLD));
			}
			if (SpaceTravel.isSpace(client.world)) {
				lines.add(Text.translatable("screen.kingdomomnitrix.map.distance",
						Math.round(hovered.data().position().distanceTo(player.getPos()))).formatted(Formatting.AQUA));
			}
			context.drawTooltip(textRenderer, lines, mouseX, mouseY);
		}
	}

	/** Groesster Abstand vom Zentrum des Alls (Risse und Spieler) mit Rand, damit alles auf die Karte passt. */
	private double extentOf(ClientPlayerEntity player) {
		double extent = 64.0;
		for (Map.Entry<Identifier, SpaceRoute> entry : SpaceRouteRegistry.all(player.getWorld().getRegistryManager())) {
			extent = Math.max(extent, Math.max(Math.abs(entry.getValue().position().x), Math.abs(entry.getValue().position().z)));
		}
		if (SpaceTravel.isSpace(player.getWorld())) {
			extent = Math.max(extent, Math.max(Math.abs(player.getX()), Math.abs(player.getZ())));
		}
		return extent * 1.15;
	}

	private void renderSidebar(DrawContext context, ClientPlayerEntity player, List<Planet> planets) {
		int x = left + panelWidth - SIDEBAR - 4;
		int y = mapTop();
		HeroData data = HeroDataAccess.get(player);
		Identifier current = player.getWorld().getRegistryKey().getValue();
		long found = planets.stream().filter(Planet::discovered).count();
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.map.worlds", found, planets.size()), x, y,
				UiTheme.EXPLORATION.accent());
		y += 12;
		for (Planet planet : planets) {
			y = sidebarLine(context, x, y, planet.discovered() ? SpaceRoute.name(planet.route()) : Text.literal("???"),
					planet.discovered(), planet.here(), 0xFF000000 | planet.data().color());
		}
		y += 6;
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.map.dimensions"), x, y, UiTheme.EXPLORATION.accent());
		y += 12;
		for (Identifier dimension : OTHER_DIMENSIONS) {
			boolean known = discovered(data, dimension);
			Text name = known ? Text.translatableWithFallback("dimension." + dimension.getNamespace() + "." + dimension.getPath(), dimension.getPath())
					: Text.literal("???");
			y = sidebarLine(context, x, y, name, known, dimension.equals(current), 0xFFB0B8C4);
		}
		y += 6;
		Text where = SpaceTravel.isSpace(player.getWorld()) ? Text.translatable("screen.kingdomomnitrix.map.in_space")
				: Text.translatable("screen.kingdomomnitrix.map.location",
						Text.translatableWithFallback("dimension." + current.getNamespace() + "." + current.getPath(), current.getPath()));
		context.drawTextWrapped(textRenderer, where, x, y, SIDEBAR - 2, UiTheme.TEXT_SOFT);
	}

	private int sidebarLine(DrawContext context, int x, int y, Text name, boolean known, boolean here, int color) {
		context.fill(x, y + 2, x + 4, y + 6, known ? color : UiTheme.TEXT_DISABLED);
		context.drawTextWithShadow(textRenderer, UiDraw.trim(textRenderer, name, SIDEBAR - 22), x + 8, y, known ? UiTheme.TEXT : UiTheme.TEXT_DISABLED);
		if (here) {
			context.drawTextWithShadow(textRenderer, "◆", x + SIDEBAR - 12, y, 0xFFFFC94A);
		} else if (known) {
			context.drawTextWithShadow(textRenderer, "✔", x + SIDEBAR - 12, y, 0xFF5CE65C);
		}
		return y + 11;
	}

	private static void disc(DrawContext context, int cx, int cy, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			int half = (int) Math.floor(Math.sqrt(radius * radius - dy * dy + 0.5));
			context.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	private static void ring(DrawContext context, int cx, int cy, int radius, int color) {
		int points = radius * 8;
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2 * i / points;
			int px = cx + (int) Math.round(Math.cos(angle) * radius);
			int py = cy + (int) Math.round(Math.sin(angle) * radius);
			context.fill(px, py, px + 1, py + 1, color);
		}
	}

	private static void dottedLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		for (int i = 0; i <= steps; i += 3) {
			int px = x1 + (x2 - x1) * i / Math.max(1, steps);
			int py = y1 + (y2 - y1) * i / Math.max(1, steps);
			context.fill(px, py, px + 1, py + 1, color);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
