package com.santiq.kingdomomnitrix.client.screen;

import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixFeedback;
import com.santiq.kingdomomnitrix.networking.CalibrationActionPayload;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixBoost;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCalibration;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCalibration.Module;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixColors;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Kalibrier-Werkbank: drei Module mit Stufen-Anzeige und +/−, Punkte-Budget, Kosten der naechsten Stufe, Farbmodule und
 * die wirksamen Geraete-Werte; rechts das Omnitrix-Upgrade (Stufe 1–5 und Boost-Steckplaetze mit Ausbau-Knopf).
 * Alle Aenderungen prueft der Server; der Bildschirm zeigt den synchronisierten Stand.
 */
public class OmnitrixCalibrationScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int HEIGHT = 214;
	private static final int ROW = 34;
	private static final int BUTTON = 14;
	private static final int SWATCH = 16;
	private static final int DANGER = 0xFF8A3C;
	/** Seitenpanel Omnitrix-Upgrade */
	private static final int SIDE = 112;
	private static final int GAP = 6;
	private static final int SLOT_ROW = 18;

	private final BlockPos bench;
	private final long openedAt = System.nanoTime();

	public OmnitrixCalibrationScreen(BlockPos bench) {
		super(Text.translatable("screen.kingdomomnitrix.calibration"));
		this.bench = bench;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		OmnitrixFeedback.play(OmnitrixCue.OPEN);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0x70000000);
	}

	private int left() {
		return (width - WIDTH - GAP - SIDE) / 2;
	}

	private int top() {
		return (height - HEIGHT) / 2;
	}

	private int rowY(int index) {
		return top() + 24 + index * ROW;
	}

	private int minusX() {
		return left() + WIDTH - 10 - BUTTON * 2 - 4;
	}

	private int plusX() {
		return left() + WIDTH - 10 - BUTTON;
	}

	private int swatchX(int index) {
		return left() + 10 + index * (SWATCH + 6);
	}

	private int swatchY() {
		return rowY(3) + 12;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		ClientPlayerEntity player = client != null ? client.player : null;
		if (player == null) {
			return;
		}
		OmnitrixCalibration calibration = OmnitrixCore.calibration(player);
		OmnitrixColors.Color color = OmnitrixColors.of(player);
		int accent = color.primary();
		float open = Math.min(1.0f, (System.nanoTime() - openedAt) / 1.0e9f / 0.15f);
		int alpha = Math.round(255 * open);
		int x = left();
		int y = top();

		// Panel: dunkles Glas, Akzentkanten in der Farbe des Farbmoduls
		context.fill(x, y, x + WIDTH, y + HEIGHT, (Math.round(0xF4 * open) << 24) | 0x06120A);
		context.fill(x, y, x + WIDTH, y + 1, alpha << 24 | accent);
		context.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, (alpha / 2) << 24 | accent);
		context.fill(x, y, x + 1, y + 8, alpha << 24 | accent);
		context.fill(x + WIDTH - 1, y, x + WIDTH, y + 8, alpha << 24 | accent);
		context.drawTextWithShadow(textRenderer, title, x + 10, y + 8, alpha << 24 | accent);

		// Punkte-Budget: gefuellte Rauten fuer belegte Punkte
		Text points = Text.translatable("screen.kingdomomnitrix.calibration.points", calibration.used(), calibration.capacity());
		int px = x + WIDTH - 10 - calibration.capacity() * 9;
		context.drawTextWithShadow(textRenderer, points, px - 6 - textRenderer.getWidth(points), y + 8, 0xFFCFFFC8);
		for (int i = 0; i < calibration.capacity(); i++) {
			boolean used = i < calibration.used();
			context.fill(px + i * 9, y + 8, px + i * 9 + 7, y + 15, used ? 0xFF000000 | accent : 0xFF1A2A1E);
			context.drawBorder(px + i * 9, y + 8, 7, 7, 0x80000000 | accent);
		}

		Module[] modules = Module.values();
		for (int i = 0; i < modules.length; i++) {
			renderModule(context, mouseX, mouseY, modules[i], calibration, rowY(i), accent);
		}

		// Farbanzeige: aktive Farbe markiert; geaendert wird sie ueber den Energiekern in der Omnitrix-Werkbank
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.calibration.color"), x + 10, rowY(3), 0xFFCFFFC8);
		for (int i = 0; i < OmnitrixColors.ALL.size(); i++) {
			OmnitrixColors.Color option = OmnitrixColors.ALL.get(i);
			int sx = swatchX(i);
			int sy = swatchY();
			boolean chosen = option.id().equals(color.id());
			boolean hover = inside(mouseX, mouseY, sx, sy, SWATCH, SWATCH);
			context.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, chosen ? 0xFFFFFFFF : 0xFF202820);
			int fill = chosen ? option.primary() : OmnitrixColors.mix(option.primary(), 0x202820, 0.7f);
			context.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | fill);
			if (chosen) {
				context.fill(sx + 4, sy + 4, sx + SWATCH - 4, sy + SWATCH - 4, 0xFF000000 | option.light());
			}
			if (hover) {
				context.drawTooltip(textRenderer, java.util.List.of(Text.translatable(OmnitrixColors.nameKey(option)),
						Text.translatable("screen.kingdomomnitrix.calibration.color_hint").formatted(net.minecraft.util.Formatting.GRAY)), mouseX, mouseY);
			}
		}

		renderStats(context, player, x + 10 + 6 * (SWATCH + 6) + 6, rowY(3));
		renderUpgrade(context, mouseX, mouseY, calibration, accent, alpha, open);

		// Geraete-Meldung (Omnitrix OS) im Panel, da der Bildschirm das Hologramm im HUD verdeckt
		com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixHolo.visible().ifPresent(message -> {
			int my = y + HEIGHT - 14;
			context.fill(x + 6, my - 3, x + WIDTH - 6, my + 10, 0x60000000);
			Text line = message.title().copy().append(" · ").append(message.body());
			context.drawTextWithShadow(textRenderer, line, x + 10, my, 0xFF000000 | message.color());
		});
	}

	private void renderModule(DrawContext context, int mouseX, int mouseY, Module module, OmnitrixCalibration calibration, int y,
			int accent) {
		int x = left() + 10;
		int level = calibration.level(module);
		context.fill(x - 4, y - 3, left() + WIDTH - 6, y + ROW - 5, 0x40000000);
		context.drawTextWithShadow(textRenderer, Text.translatable("calibration.kingdomomnitrix.module." + module.key()), x, y, 0xFFFFFFFF);
		// Stufen-Balken
		int bx = x + 78;
		for (int i = 0; i < OmnitrixCalibration.MAX_LEVEL; i++) {
			context.fill(bx + i * 16, y + 1, bx + i * 16 + 13, y + 7, i < level ? 0xFF000000 | accent : 0xFF1A2A1E);
		}
		// Wirkung der aktuellen Stufe
		context.getMatrices().push();
		context.getMatrices().translate(x, y + 11, 0.0f);
		context.getMatrices().scale(0.75f, 0.75f, 1.0f);
		context.drawTextWithShadow(textRenderer, effect(module, level), 0, 0, 0xFF9ACFA8);
		Text next;
		int nextColor;
		if (level >= OmnitrixCalibration.MAX_LEVEL) {
			next = Text.translatable("screen.kingdomomnitrix.calibration.max");
			nextColor = 0xFFFFC94A;
		} else if (calibration.free() <= 0) {
			next = Text.translatable("screen.kingdomomnitrix.calibration.no_points");
			nextColor = 0xFF000000 | DANGER;
		} else {
			OmnitrixCalibration.Cost cost = OmnitrixCalibration.cost(level + 1);
			next = Text.translatable("screen.kingdomomnitrix.calibration.cost", level + 1, cost(cost));
			nextColor = affordable(cost) ? 0xFFB0B0B0 : 0xFF000000 | DANGER;
		}
		context.drawTextWithShadow(textRenderer, next, 0, 11, nextColor);
		context.getMatrices().pop();

		button(context, mouseX, mouseY, minusX(), y, "−", level > 0, 0xFF8A3C);
		button(context, mouseX, mouseY, plusX(), y, "+", calibration.canRaise(module), accent);
	}

	private int sideX() {
		return left() + WIDTH + GAP;
	}

	private int tierButtonY() {
		return top() + 58;
	}

	private int slotY(int index) {
		return top() + 92 + index * SLOT_ROW;
	}

	private int removeX() {
		return sideX() + SIDE - 8 - 12;
	}

	/** Omnitrix-Upgrade: Stufe mit Balken, Kosten der naechsten Stufe, Knopf „Aufruesten“, Boost-Steckplaetze. */
	private void renderUpgrade(DrawContext context, int mouseX, int mouseY, OmnitrixCalibration calibration, int accent, int alpha, float open) {
		int x = sideX();
		int y = top();
		context.fill(x, y, x + SIDE, y + HEIGHT, (Math.round(0xF4 * open) << 24) | 0x06120A);
		context.fill(x, y, x + SIDE, y + 1, alpha << 24 | accent);
		context.fill(x, y + HEIGHT - 1, x + SIDE, y + HEIGHT, (alpha / 2) << 24 | accent);
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.upgrade.title"), x + 8, y + 8, alpha << 24 | accent);
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.upgrade.tier", calibration.tier(), OmnitrixCalibration.MAX_TIER),
				x + 8, y + 22, 0xFFFFFFFF);
		for (int i = 0; i < OmnitrixCalibration.MAX_TIER; i++) {
			context.fill(x + 8 + i * 19, y + 33, x + 8 + i * 19 + 16, y + 38, i < calibration.tier() ? 0xFF000000 | accent : 0xFF1A2A1E);
		}
		boolean max = calibration.tier() >= OmnitrixCalibration.MAX_TIER;
		Text next = max ? Text.translatable("screen.kingdomomnitrix.calibration.max")
				: cost(OmnitrixCalibration.tierCost(calibration.tier() + 1));
		boolean affordable = !max && affordable(OmnitrixCalibration.tierCost(calibration.tier() + 1));
		context.getMatrices().push();
		context.getMatrices().translate(x + 8, y + 43, 0.0f);
		context.getMatrices().scale(0.6f, 0.6f, 1.0f);
		context.drawTextWithShadow(textRenderer, next, 0, 0, max ? 0xFFFFC94A : affordable ? 0xFFB0B0B0 : 0xFF000000 | DANGER);
		context.getMatrices().pop();
		int bx = x + 8;
		int by = tierButtonY();
		int bw = SIDE - 16;
		boolean hover = !max && inside(mouseX, mouseY, bx, by, bw, BUTTON);
		context.fill(bx, by, bx + bw, by + BUTTON, hover ? 0xFF1E4D22 : 0xFF0C2410);
		context.drawBorder(bx, by, bw, BUTTON, max ? 0xFF3A3A3A : 0xFF000000 | accent);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.upgrade.raise"), bx + bw / 2, by + 3,
				max ? 0xFF4A4A4A : 0xFF000000 | accent);

		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.upgrade.slots", calibration.boosts().size(),
				calibration.slots()), x + 8, y + 79, 0xFFCFFFC8);
		for (int i = 0; i < OmnitrixCalibration.MAX_TIER; i++) {
			int sy = slotY(i);
			boolean unlocked = i < calibration.slots();
			context.fill(x + 6, sy - 2, x + SIDE - 6, sy + SLOT_ROW - 4, unlocked ? 0x50000000 : 0x20000000);
			if (!unlocked) {
				drawSmall(context, Text.translatable("screen.kingdomomnitrix.upgrade.locked", i + 1), x + 10, sy + 2, 0xFF4A5A4E);
				continue;
			}
			if (i >= calibration.boosts().size()) {
				drawSmall(context, Text.translatable("screen.kingdomomnitrix.upgrade.empty"), x + 10, sy + 2, 0xFF6A8A70);
				continue;
			}
			var boost = OmnitrixBoost.byId(calibration.boosts().get(i));
			int color = boost.map(OmnitrixBoost::color).orElse(0xFFFFFF);
			context.fill(x + 10, sy + 1, x + 16, sy + 7, 0xFF000000 | color);
			drawSmall(context, boost.map(b -> Text.translatable(b.nameKey())).orElse(Text.literal("?")), x + 20, sy + 1, 0xFF000000 | color);
			boolean overRemove = inside(mouseX, mouseY, removeX(), sy - 1, 12, 12);
			context.fill(removeX(), sy - 1, removeX() + 12, sy + 11, overRemove ? 0xFF4D1E1E : 0xFF240C0C);
			context.drawBorder(removeX(), sy - 1, 12, 12, 0xFF000000 | DANGER);
			context.drawCenteredTextWithShadow(textRenderer, "×", removeX() + 6, sy + 1, 0xFF000000 | DANGER);
			if (overRemove || inside(mouseX, mouseY, x + 6, sy - 2, SIDE - 30, SLOT_ROW - 2)) {
				boost.ifPresent(b -> context.drawTooltip(textRenderer, java.util.List.of(Text.translatable(b.nameKey()),
						Text.translatable(b.descKey()).formatted(net.minecraft.util.Formatting.GRAY),
						Text.translatable("screen.kingdomomnitrix.upgrade.remove").formatted(net.minecraft.util.Formatting.DARK_GRAY)), mouseX, mouseY));
			}
		}
		drawSmall(context, Text.translatable("screen.kingdomomnitrix.upgrade.hint"), x + 8, y + HEIGHT - 26, 0xFF7A9A80);
	}

	private void drawSmall(DrawContext context, Text text, int x, int y, int color) {
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0.0f);
		context.getMatrices().scale(0.75f, 0.75f, 1.0f);
		context.drawTextWithShadow(textRenderer, text, 0, 0, color);
		context.getMatrices().pop();
	}

	private void button(DrawContext context, int mouseX, int mouseY, int x, int y, String label, boolean enabled, int color) {
		boolean hover = enabled && inside(mouseX, mouseY, x, y, BUTTON, BUTTON);
		context.fill(x, y, x + BUTTON, y + BUTTON, hover ? 0xFF1E4D22 : 0xFF0C2410);
		context.drawBorder(x, y, BUTTON, BUTTON, enabled ? 0xFF000000 | color : 0xFF3A3A3A);
		context.drawCenteredTextWithShadow(textRenderer, label, x + BUTTON / 2, y + 3, enabled ? 0xFF000000 | color : 0xFF4A4A4A);
	}

	/** Reicht es? (Anzeige; der Server prueft selbst) */
	private boolean affordable(OmnitrixCalibration.Cost cost) {
		ClientPlayerEntity player = client != null ? client.player : null;
		if (player == null) {
			return false;
		}
		if (player.getAbilities().creativeMode) {
			return true;
		}
		var inventory = player.getInventory();
		return com.santiq.kingdomomnitrix.player.HeroDataAccess.get(player).canAfford(cost.bolts())
				&& inventory.count(com.santiq.kingdomomnitrix.registry.ModItems.RARITANIUM) >= cost.raritanium()
				&& inventory.count(com.santiq.kingdomomnitrix.registry.ModItems.MYTHRIL_SHARD) >= cost.mythril()
				&& inventory.count(com.santiq.kingdomomnitrix.registry.ModItems.ORICHALCUM) >= cost.orichalcum();
	}

	private static MutableText cost(OmnitrixCalibration.Cost cost) {
		MutableText text = Text.translatable("screen.kingdomomnitrix.calibration.bolts", cost.bolts());
		if (cost.raritanium() > 0) {
			text.append(" · ").append(Text.translatable("screen.kingdomomnitrix.calibration.raritanium", cost.raritanium()));
		}
		if (cost.mythril() > 0) {
			text.append(" · ").append(Text.translatable("screen.kingdomomnitrix.calibration.mythril", cost.mythril()));
		}
		if (cost.orichalcum() > 0) {
			text.append(" · ").append(Text.translatable("screen.kingdomomnitrix.calibration.orichalcum", cost.orichalcum()));
		}
		return text;
	}

	private static Text effect(Module module, int level) {
		if (level == 0) {
			return Text.translatable(switch (module) {
				case COOLING -> "screen.kingdomomnitrix.calibration.effect.none.cooling";
				case CORE -> "screen.kingdomomnitrix.calibration.effect.none.core";
				case BANDWIDTH -> "screen.kingdomomnitrix.calibration.effect.none.bandwidth";
			});
		}
		return switch (module) {
			case COOLING -> Text.translatable("screen.kingdomomnitrix.calibration.effect.cooling",
					pct(OmnitrixCalibration.COOLING_ACTIVE_HEAT * level), pct(OmnitrixCalibration.COOLING_DECAY * level));
			case CORE -> Text.translatable("screen.kingdomomnitrix.calibration.effect.core",
					pct(OmnitrixCalibration.CORE_DURATION * level), pct(OmnitrixCalibration.CORE_TRANSFORM_HEAT * level));
			case BANDWIDTH -> Text.translatable("screen.kingdomomnitrix.calibration.effect.bandwidth",
					pct(OmnitrixCalibration.BANDWIDTH_RECHARGE * level), pct(OmnitrixCalibration.BANDWIDTH_MALFUNCTION * level));
		};
	}

	private static int pct(float value) {
		return Math.round(value * 100);
	}

	/** Wirksame Werte des Geraets (Profil mit Kalibrierung). */
	private void renderStats(DrawContext context, ClientPlayerEntity player, int x, int y) {
		OmnitrixProfile profile = OmnitrixCore.profile(player);
		String[] lines = {
				Text.translatable("screen.kingdomomnitrix.calibration.stat.transform_heat", pct(profile.heatPerTransform())).getString(),
				Text.translatable("screen.kingdomomnitrix.calibration.stat.active_heat", String.format("%.2f", profile.heatPerSecondActive() * 100)).getString(),
				Text.translatable("screen.kingdomomnitrix.calibration.stat.duration", String.format("%.2f", profile.durationMultiplier())).getString(),
				Text.translatable("screen.kingdomomnitrix.calibration.stat.recharge", String.format("%.2f", profile.cooldownMultiplier())).getString(),
				Text.translatable("screen.kingdomomnitrix.calibration.stat.malfunction", pct(profile.malfunctions().wrongAlienChance())).getString()
		};
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0.0f);
		context.getMatrices().scale(0.75f, 0.75f, 1.0f);
		for (int i = 0; i < lines.length; i++) {
			context.drawTextWithShadow(textRenderer, lines[i], 0, i * 10, 0xFFB8FFB0);
		}
		context.getMatrices().pop();
	}

	private void send(String module, String value) {
		ClientPlayNetworking.send(new CalibrationActionPayload(bench, module, value));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || client == null || client.player == null) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		OmnitrixCalibration calibration = OmnitrixCore.calibration(client.player);
		if (inside(mouseX, mouseY, sideX() + 8, tierButtonY(), SIDE - 16, BUTTON)) {
			if (calibration.tier() < OmnitrixCalibration.MAX_TIER) {
				send("tier", "+");
			} else {
				OmnitrixFeedback.play(OmnitrixCue.ERROR);
			}
			return true;
		}
		for (int i = 0; i < calibration.boosts().size(); i++) {
			if (inside(mouseX, mouseY, removeX(), slotY(i) - 1, 12, 12)) {
				send("boost", Integer.toString(i));
				return true;
			}
		}
		Module[] modules = Module.values();
		for (int i = 0; i < modules.length; i++) {
			int y = rowY(i);
			if (inside(mouseX, mouseY, minusX(), y, BUTTON, BUTTON) && calibration.level(modules[i]) > 0) {
				send(modules[i].key(), "-");
				return true;
			}
			if (inside(mouseX, mouseY, plusX(), y, BUTTON, BUTTON)) {
				if (calibration.canRaise(modules[i])) {
					send(modules[i].key(), "+");
				} else {
					OmnitrixFeedback.play(OmnitrixCue.ERROR);
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
}
