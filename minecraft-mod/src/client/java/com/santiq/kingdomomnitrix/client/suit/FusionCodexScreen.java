package com.santiq.kingdomomnitrix.client.suit;

import com.santiq.kingdomomnitrix.suit.fusion.FusionAltar;
import com.santiq.kingdomomnitrix.suit.fusion.FusionCodexItem;
import com.santiq.kingdomomnitrix.suit.fusion.SuitFusion;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/**
 * Ansicht des Fusions-Kodex: eine Seite je Altarstufe mit Bauplan (Draufsicht), Bauteilen und Gefahren, dazu eine
 * Seite ueber Instabilitaet und Hitze. Nicht erforschte Stufen zeigen nur, welcher Gegenstand sie aufschliesst.
 */
public class FusionCodexScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int HEIGHT = 232;
	private static final int PANEL = 0xF0141820;
	private static final int ACCENT = 0xFF3AE8FF;
	private static final int TEXT = 0xFFD8E0EA;
	private static final int MUTED = 0xFF8A94A2;
	/** Seiten: 0 Einleitung, 1..4 Altarstufen, 5 Gefahren */
	private static final int PAGES = 6;
	private static final String[] TEXTS = {"codex.kingdomomnitrix.intro.text", "codex.kingdomomnitrix.tier1.text",
			"codex.kingdomomnitrix.tier2.text", "codex.kingdomomnitrix.tier3.text", "codex.kingdomomnitrix.tier4.text",
			"codex.kingdomomnitrix.dangers.text"};
	private static final String[] PAGE_TITLES = {"codex.kingdomomnitrix.intro.title", FusionCodexItem.TITLES[1], FusionCodexItem.TITLES[2],
			FusionCodexItem.TITLES[3], FusionCodexItem.TITLES[4], "codex.kingdomomnitrix.dangers.title"};
	/** Farben der Bauteile im Bauplan */
	private static final int CORE_COLOR = 0xFF3AE8FF;
	private static final int RUNE_COLOR = 0xFF7A6AFF;
	private static final int PYLON_COLOR = 0xFFB8C0CC;
	private static final int RESONANCE_COLOR = 0xFF3AE89A;
	private static final int CELESTIAL_COLOR = 0xFFD8CCFF;
	private static final int CHAOS_COLOR = 0xFFFF3A6A;
	private static final int INJECTOR_COLOR = 0xFF4A6070;

	private final ItemStack codex;
	private int page;

	public FusionCodexScreen(ItemStack codex) {
		super(Text.translatable("item.kingdomomnitrix.fusion_codex"));
		this.codex = codex;
	}

	public static void open(ItemStack codex) {
		MinecraftClient.getInstance().setScreen(new FusionCodexScreen(codex));
	}

	@Override
	protected void init() {
		int x = (width - WIDTH) / 2;
		int y = (height - HEIGHT) / 2;
		addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> page = Math.max(0, page - 1)).dimensions(x + 6, y + HEIGHT - 24, 20, 18)
				.build());
		addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> page = Math.min(PAGES - 1, page + 1))
				.dimensions(x + WIDTH - 26, y + HEIGHT - 24, 20, 18).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int x = (width - WIDTH) / 2;
		int y = (height - HEIGHT) / 2;
		context.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
		border(context, x, y, WIDTH, HEIGHT, ACCENT);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable(PAGE_TITLES[page]), x + WIDTH / 2, y + 7, ACCENT);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + " / " + PAGES), x + WIDTH / 2, y + HEIGHT - 19, MUTED);

		boolean tierPage = page >= 1 && page <= FusionAltar.MAX_TIER;
		if (tierPage && !FusionCodexItem.researched(codex, page)) {
			locked(context, x, y, page);
			return;
		}
		int textX = tierPage ? x + 128 : x + 12;
		int textWidth = tierPage ? WIDTH - 140 : WIDTH - 24;
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(TEXTS[page]), textWidth);
		int ty = y + 24;
		for (OrderedText line : lines) {
			if (ty > y + HEIGHT - 32) {
				break;
			}
			context.drawText(textRenderer, line, textX, ty, TEXT, false);
			ty += 10;
		}
		if (tierPage) {
			plan(context, x + 10, y + 24, page);
		}
	}

	private void locked(DrawContext context, int x, int y, int tier) {
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("codex.kingdomomnitrix.locked"), x + WIDTH / 2, y + 60, 0xFFFF6A6A);
		ItemStack key = new ItemStack(FusionCodexItem.key(tier));
		context.drawItem(key, x + WIDTH / 2 - 8, y + 80);
		context.drawCenteredTextWithShadow(textRenderer, key.getName(), x + WIDTH / 2, y + 102, TEXT);
		List<OrderedText> hint = textRenderer.wrapLines(Text.translatable("codex.kingdomomnitrix.locked.hint"), WIDTH - 40);
		int ty = y + 120;
		for (OrderedText line : hint) {
			context.drawCenteredTextWithShadow(textRenderer, line, x + WIDTH / 2, ty, MUTED);
			ty += 10;
		}
	}

	/** Bauplan in Draufsicht: 13 x 13 Felder um den Kern; Bauteile dieser Stufe hell, Vorstufen gedimmt. */
	private void plan(DrawContext context, int x, int y, int tier) {
		int cell = 8;
		int size = 13 * cell;
		context.fill(x, y, x + size, y + size, 0xFF0A0C10);
		for (int i = 0; i <= 13; i++) {
			context.fill(x + i * cell, y, x + i * cell + 1, y + size, 0x30FFFFFF);
			context.fill(x, y + i * cell, x + size, y + i * cell + 1, 0x30FFFFFF);
		}
		// Vorschlag fuer acht Injektoren (Abstand 4, symmetrisch)
		for (int[] d : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}, {4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
			cellFill(context, x, y, cell, d[0], d[1], INJECTOR_COLOR, 2);
		}
		for (int t = 2; t <= tier; t++) {
			boolean current = t == tier;
			for (FusionAltar.Part part : FusionAltar.parts(t)) {
				int color = color(part.block());
				if (!current) {
					color = (color & 0x00FFFFFF) | 0x70000000;
				}
				// hoechstes Bauteil je Feld gewinnt (Kronen ueber Saeulen)
				int inset = part.block() == SuitFusion.PYLON ? 2 : part.block() == SuitFusion.RUNE ? 1 : 0;
				cellFill(context, x, y, cell, part.offset().getX(), part.offset().getZ(), color, inset);
			}
		}
		cellFill(context, x, y, cell, 0, 0, CORE_COLOR, 0);
		// Legende: eine Zeile je Bauteil dieser und der vorigen Stufen
		int ly = y + size + 5;
		legend(context, x, ly, CORE_COLOR, SuitFusion.CORE.getName());
		legend(context, x, ly += 10, INJECTOR_COLOR, SuitFusion.INJECTOR.getName());
		if (tier >= 2) {
			legend(context, x, ly += 10, RUNE_COLOR, SuitFusion.RUNE.getName());
			legend(context, x, ly += 10, RESONANCE_COLOR, SuitFusion.RESONANCE_CAPSTONE.getName());
		}
		if (tier >= 3) {
			legend(context, x, ly += 10, CELESTIAL_COLOR, SuitFusion.CELESTIAL_CAPSTONE.getName());
		}
		if (tier >= 4) {
			legend(context, x, ly + 10, CHAOS_COLOR, SuitFusion.CHAOS_FOCUS.getName());
		}
	}

	private static int color(Block block) {
		if (block == SuitFusion.RUNE) {
			return RUNE_COLOR;
		}
		if (block == SuitFusion.RESONANCE_CAPSTONE) {
			return RESONANCE_COLOR;
		}
		if (block == SuitFusion.CELESTIAL_CAPSTONE) {
			return CELESTIAL_COLOR;
		}
		if (block == SuitFusion.CHAOS_FOCUS) {
			return CHAOS_COLOR;
		}
		return PYLON_COLOR;
	}

	private static void cellFill(DrawContext context, int x, int y, int cell, int dx, int dz, int color, int inset) {
		if (Math.abs(dx) > 6 || Math.abs(dz) > 6) {
			return;
		}
		int cx = x + (dx + 6) * cell;
		int cy = y + (dz + 6) * cell;
		context.fill(cx + 1 + inset, cy + 1 + inset, cx + cell - inset, cy + cell - inset, color);
	}

	private void legend(DrawContext context, int x, int y, int color, Text name) {
		context.fill(x, y + 1, x + 6, y + 7, color);
		context.drawText(textRenderer, trimmed(name, 104), x + 9, y, MUTED, false);
	}

	private Text trimmed(Text text, int width) {
		String s = text.getString();
		if (textRenderer.getWidth(s) <= width) {
			return text;
		}
		return Text.literal(textRenderer.trimToWidth(s, width - textRenderer.getWidth("…")) + "…");
	}

	private static void border(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x, y, x + w, y + 1, color);
		context.fill(x, y + h - 1, x + w, y + h, color);
		context.fill(x, y, x + 1, y + h, color);
		context.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
