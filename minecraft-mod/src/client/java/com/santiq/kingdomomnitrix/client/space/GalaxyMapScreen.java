package com.santiq.kingdomomnitrix.client.space;

import com.santiq.kingdomomnitrix.networking.GalaxyActionPayload;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.space.Galaxy;
import com.santiq.kingdomomnitrix.space.Planet;
import com.santiq.kingdomomnitrix.space.PlanetRegistry;
import com.santiq.kingdomomnitrix.space.ShipEntity;
import com.santiq.kingdomomnitrix.space.ShipLog;
import com.santiq.kingdomomnitrix.util.MaterialCost;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Galaxiekarte der Aphelion (Taste P): Sternsysteme mit ihren Planeten, „Du bist hier“, Kurslinie zum gewaehlten
 * Planeten, Infotafel mit Reise-Knopf und darunter der Ausbau des Schiffs (Triebwerk, Warp-Antrieb, Bordkanone).
 * Reisen nur als Pilot; die Karte selbst laesst sich immer ansehen.
 */
public class GalaxyMapScreen extends Screen {
	private static final int PANEL_WIDTH = 190;
	// Zeilen der Infotafel (skaliert auf kleine Fenster: alles muss in ~240 px Hoehe passen)
	private static final int Y_DESC = 38;
	private static final int DESC_LINES = 3;
	private static final int Y_STATUS = 72;
	private static final int Y_TRAVEL = 84;
	private static final int Y_UPGRADES = 112;
	private static final int ROW = 27;
	private static final int STARS = 220;

	private final long seed = 0x5EED_A7E1L;
	@Nullable
	private Identifier selected;
	private int refresh;

	public GalaxyMapScreen() {
		super(Text.translatable("screen.kingdomomnitrix.galaxy"));
	}

	public static void open(MinecraftClient client) {
		client.setScreen(new GalaxyMapScreen());
	}

	// --- Aufbau ---------------------------------------------------------------------------------

	private List<Map.Entry<Identifier, Planet>> planets() {
		return client == null || client.world == null ? List.of() : PlanetRegistry.all(client.world.getRegistryManager());
	}

	private boolean worldExists(Planet planet) {
		return client != null && client.getNetworkHandler() != null && client.getNetworkHandler().getWorldKeys().contains(planet.worldKey());
	}

	private boolean piloting() {
		return client != null && client.player != null && client.player.getVehicle() instanceof ShipEntity ship
				&& ship.getControllingPassenger() == client.player;
	}

	private int mapLeft() {
		return 12;
	}

	private int mapTop() {
		return 28;
	}

	private int mapWidth() {
		return width - PANEL_WIDTH - 36;
	}

	private int mapHeight() {
		return height - 44;
	}

	private int px(Planet planet) {
		return mapLeft() + Math.round(planet.mapX() * mapWidth());
	}

	private int py(Planet planet) {
		return mapTop() + Math.round(planet.mapY() * mapHeight());
	}

	private static int radius(Planet planet, boolean big) {
		return Math.round((big ? 9.0f : 6.0f) * planet.size());
	}

	@Override
	protected void init() {
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		if (selected == null) {
			selected = PlanetRegistry.ofWorld(client.world.getRegistryManager(), client.world.getRegistryKey())
					.orElse(planets().isEmpty() ? null : planets().get(0).getKey());
		}
		int left = width - PANEL_WIDTH - 12;
		Map.Entry<Identifier, Planet> chosen = selectedEntry();
		if (chosen != null) {
			Galaxy.Access access = Galaxy.access(client.player, chosen.getKey(), chosen.getValue(), worldExists(chosen.getValue()));
			ButtonWidget travel = ButtonWidget.builder(Text.translatable("screen.kingdomomnitrix.galaxy.travel"), b -> {
				ClientPlayNetworking.send(new GalaxyActionPayload(Galaxy.Action.TRAVEL.ordinal(), chosen.getKey().toString()));
				close();
			}).dimensions(left + 8, Y_TRAVEL, PANEL_WIDTH - 16, 18).build();
			travel.active = access == Galaxy.Access.OPEN && piloting();
			addDrawableChild(travel);
		}
		ShipLog.State log = ShipLog.get(client.player);
		int bolts = HeroDataAccess.get(client.player).bolts();
		boolean creative = client.player.getAbilities().creativeMode;
		int y = Y_UPGRADES + 12;
		for (ShipLog.Upgrade upgrade : ShipLog.Upgrade.values()) {
			int level = log.level(upgrade);
			boolean max = level >= ShipLog.MAX_LEVEL;
			int[] price = ShipLog.price(level + 1);
			Text label = max ? Text.translatable("tooltip.kingdomomnitrix.weapon.max")
					: Text.translatable("screen.kingdomomnitrix.galaxy.buy", price[0], price[1]);
			ButtonWidget buy = ButtonWidget.builder(label, b -> {
				ClientPlayNetworking.send(new GalaxyActionPayload(Galaxy.Action.UPGRADE.ordinal(), upgrade.key()));
				refresh = 4;
			}).dimensions(left + 8, y + 10, PANEL_WIDTH - 16, 15).build();
			boolean materials = creative || MaterialCost.missing(client.player.getInventory(),
					List.of(new ItemStack(ModItems.RARITANIUM, price[1]))).isEmpty();
			buy.active = !max && (creative || bolts >= price[0]) && materials;
			addDrawableChild(buy);
			y += ROW;
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(left + PANEL_WIDTH / 2 - 40, Math.max(y + 4, height - 24), 80, 16).build());
	}

	@Nullable
	private Map.Entry<Identifier, Planet> selectedEntry() {
		for (Map.Entry<Identifier, Planet> entry : planets()) {
			if (entry.getKey().equals(selected)) {
				return entry;
			}
		}
		return null;
	}

	@Override
	public void tick() {
		super.tick();
		if (refresh > 0 && --refresh == 0) {
			clearAndInit();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Map.Entry<Identifier, Planet> entry : planets()) {
			int dx = (int) mouseX - px(entry.getValue());
			int dy = (int) mouseY - py(entry.getValue());
			int r = radius(entry.getValue(), true) + 4;
			if (dx * dx + dy * dy <= r * r) {
				selected = entry.getKey();
				clearAndInit();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// --- Zeichnen -------------------------------------------------------------------------------

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xFF050716);
		Random random = new Random(seed);
		float time = client == null || client.world == null ? 0 : (client.world.getTime() + delta);
		// Nebel
		int[][] nebula = {{0x14562C9E, 25, 35}, {0x1230709E, 70, 70}, {0x109E3C56, 55, 18}};
		for (int[] n : nebula) {
			int cx = mapLeft() + mapWidth() * n[1] / 100;
			int cy = mapTop() + mapHeight() * n[2] / 100;
			for (int r = 70; r > 0; r -= 10) {
				disc(context, cx, cy, r, n[0]);
			}
		}
		for (int i = 0; i < STARS; i++) {
			int x = random.nextInt(Math.max(1, width));
			int y = random.nextInt(Math.max(1, height));
			float twinkle = 0.55f + 0.45f * MathHelper.sin(time * 0.08f + i);
			int alpha = (int) (twinkle * (120 + random.nextInt(135)));
			context.fill(x, y, x + 1, y + 1, (alpha << 24) | 0xFFFFFF);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		float time = client.world.getTime() + delta;
		context.drawTextWithShadow(textRenderer, title, mapLeft(), 10, UiTheme.EXPLORATION.accent());
		List<Map.Entry<Identifier, Planet>> planets = planets();
		Identifier here = PlanetRegistry.ofWorld(client.world.getRegistryManager(), client.world.getRegistryKey()).orElse(null);
		Map.Entry<Identifier, Planet> chosen = selectedEntry();
		// Sternsystem-Namen: Mitte ihrer Planeten
		Map<String, int[]> systems = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Planet> entry : planets) {
			int[] sum = systems.computeIfAbsent(entry.getValue().system(), s -> new int[] {0, 0, 0});
			sum[0] += px(entry.getValue());
			sum[1] = Math.max(sum[1], py(entry.getValue()));
			sum[2]++;
		}
		for (Map.Entry<String, int[]> system : systems.entrySet()) {
			int[] sum = system.getValue();
			if (sum[2] < 2) {
				continue;   // Einzelplaneten: System steht in der Infotafel
			}
			Text name = Text.translatable("system.kingdomomnitrix." + system.getKey()).formatted(Formatting.ITALIC);
			int x = MathHelper.clamp(sum[0] / sum[2] - textRenderer.getWidth(name) / 2, mapLeft(), mapLeft() + mapWidth() - textRenderer.getWidth(name));
			context.drawText(textRenderer, name, x, sum[1] + 28, 0x999FB4D8, false);
		}
		// Kurslinie
		if (here != null && chosen != null && !chosen.getKey().equals(here)) {
			Planet from = planets.stream().filter(e -> e.getKey().equals(here)).map(Map.Entry::getValue).findFirst().orElse(null);
			if (from != null) {
				dashed(context, px(from), py(from), px(chosen.getValue()), py(chosen.getValue()), time);
			}
		}
		for (Map.Entry<Identifier, Planet> entry : planets) {
			Planet planet = entry.getValue();
			Galaxy.Access access = Galaxy.access(client.player, entry.getKey(), planet, worldExists(planet));
			boolean open = access == Galaxy.Access.OPEN || access == Galaxy.Access.HERE;
			boolean isSelected = entry.getKey().equals(selected);
			int x = px(planet);
			int y = py(planet);
			int r = radius(planet, isSelected);
			int color = open ? 0xFF000000 | planet.color() : 0xFF000000 | dim(planet.color());
			disc(context, x, y, r + 3, 0x40000000 | (planet.color() & 0xFFFFFF));
			disc(context, x, y, r, color);
			disc(context, x - r / 3, y - r / 3, Math.max(1, r / 3), 0x50FFFFFF);   // Glanzlicht
			if (isSelected) {
				ring(context, x, y, r + 5, 0xFFFFFFFF);
			}
			if (entry.getKey().equals(here)) {
				int pulse = r + 7 + Math.round(2 * MathHelper.sin(time * 0.2f));
				ring(context, x, y, pulse, 0xFF5BFF3A);
				Text you = Text.translatable("screen.kingdomomnitrix.galaxy.here");
				context.drawTextWithShadow(textRenderer, you, x - textRenderer.getWidth(you) / 2, y - r - 18, 0xFF5BFF3A);
			}
			Text name = Planet.name(entry.getKey());
			context.drawTextWithShadow(textRenderer, name, x - textRenderer.getWidth(name) / 2, y + r + 5,
					open ? UiTheme.TEXT : UiTheme.TEXT_DISABLED);
			if (!open) {
				String mark = access == Galaxy.Access.NO_WORLD ? "?" : "✖";
				context.drawTextWithShadow(textRenderer, mark, x - textRenderer.getWidth(mark) / 2, y - 4, 0xFFFF6060);
			}
		}
		drawPanel(context, chosen);
	}

	private void drawPanel(DrawContext context, @Nullable Map.Entry<Identifier, Planet> chosen) {
		int left = width - PANEL_WIDTH - 12;
		UiDraw.panel(context, left, 6, PANEL_WIDTH, height - 12, UiTheme.EXPLORATION);
		if (chosen != null && client != null && client.player != null) {
			Planet planet = chosen.getValue();
			context.drawTextWithShadow(textRenderer, Planet.name(chosen.getKey()), left + 8, 14, 0xFF000000 | planet.color());
			context.drawTextWithShadow(textRenderer, planet.systemName(), left + 8, 25, UiTheme.TEXT_SOFT);
			int y = Y_DESC;
			List<OrderedText> lines = textRenderer.wrapLines(Planet.description(chosen.getKey()), PANEL_WIDTH - 16);
			for (int i = 0; i < Math.min(DESC_LINES, lines.size()); i++) {
				context.drawTextWithShadow(textRenderer, lines.get(i), left + 8, y, UiTheme.TEXT);
				y += 10;
			}
			Galaxy.Access access = Galaxy.access(client.player, chosen.getKey(), planet, worldExists(planet));
			Text status = Text.translatable(access.screenKey(),
					planet.heroLevel(), planet.warp(), planet.requires().map(Planet::name).orElse(Text.empty()));
			int color = access == Galaxy.Access.OPEN ? 0xFF5BFF3A : access == Galaxy.Access.HERE ? 0xFF9FD8FF : 0xFFFF8080;
			context.drawTextWithShadow(textRenderer, status, left + 8, Y_STATUS, color);
			if (access == Galaxy.Access.OPEN && !piloting()) {
				context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.galaxy.need_ship"), left + 8, Y_TRAVEL + 20, 0xFFFFC060);
			}
		}
		// Ausbau
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.galaxy.upgrades"), left + 8, Y_UPGRADES,
				UiTheme.EXPLORATION.accent());
		if (client == null || client.player == null) {
			return;
		}
		ShipLog.State log = ShipLog.get(client.player);
		Text bolts = Text.translatable("screen.kingdomomnitrix.galaxy.bolts", HeroDataAccess.get(client.player).bolts());
		context.drawTextWithShadow(textRenderer, bolts, left + PANEL_WIDTH - 8 - textRenderer.getWidth(bolts), Y_UPGRADES, UiTheme.TEXT_SOFT);
		int y = Y_UPGRADES + 12;
		for (ShipLog.Upgrade upgrade : ShipLog.Upgrade.values()) {
			int level = log.level(upgrade);
			Text name = Text.translatable(upgrade.translationKey());
			context.drawTextWithShadow(textRenderer, name, left + 8, y, UiTheme.TEXT);
			String stars = "★".repeat(level) + "☆".repeat(ShipLog.MAX_LEVEL - level);
			context.drawTextWithShadow(textRenderer, stars, left + PANEL_WIDTH - 8 - textRenderer.getWidth(stars), y, 0xFFFFD84A);
			y += ROW;
		}
	}

	private static int dim(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int grey = (r + g + b) / 3;
		return ((grey / 2 + 30) << 16) | ((grey / 2 + 30) << 8) | (grey / 2 + 40);
	}

	private static void disc(DrawContext context, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.sqrt((double) r * r - dy * dy);
			context.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	private static void ring(DrawContext context, int cx, int cy, int r, int color) {
		int steps = Math.max(24, r * 6);
		for (int i = 0; i < steps; i++) {
			double a = Math.PI * 2 * i / steps;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			context.fill(x, y, x + 1, y + 1, color);
		}
	}

	private static void dashed(DrawContext context, int x0, int y0, int x1, int y1, float time) {
		double length = Math.hypot(x1 - x0, y1 - y0);
		int offset = (int) (time * 0.6f) % 8;
		List<int[]> dots = new ArrayList<>();
		for (int d = offset; d < length; d += 8) {
			double t = d / length;
			dots.add(new int[] {(int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t)});
		}
		for (int[] dot : dots) {
			context.fill(dot[0] - 1, dot[1] - 1, dot[0] + 2, dot[1] + 2, 0xCC5BFF3A);
		}
	}
}
