package com.santiq.kingdomomnitrix.client.screen;

import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixFeedback;
import com.santiq.kingdomomnitrix.galvan.GalvanHack;
import com.santiq.kingdomomnitrix.galvan.GalvanInvention;
import com.santiq.kingdomomnitrix.galvan.GalvanLab;
import com.santiq.kingdomomnitrix.galvan.GreyMatterKnowledge;
import com.santiq.kingdomomnitrix.networking.GalvanActionPayload;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Galvan-Labor (nur als Grey Matter): Reiter „Erfindungen“ (Datenpaket {@code galvan_invention}, mit Zutaten, Bolts,
 * Wissens- und Hack-Voraussetzung) und „Omnitrix-Hack“ (Forschungszeit, drei Stufen, Start-Knopf). Der Server prueft
 * jede Aktion; hier wird nur angezeigt.
 */
public class GalvanLabScreen extends Screen {
	private static final int WIDTH = 320;
	private static final int HEIGHT = 224;
	private static final int ROW = 38;
	private static final int VISIBLE = 4;
	private static final int ACCENT = 0x39FF14;
	private static final int OK = 0xFF9CFF8A;
	private static final int MISSING = 0xFFFF7A5A;
	private static final int BUTTON_W = 46;
	private static final int BUTTON_H = 14;

	private static final String[] HACK_NAMES = {"screen.kingdomomnitrix.galvan_lab.hack_name_1",
			"screen.kingdomomnitrix.galvan_lab.hack_name_2", "screen.kingdomomnitrix.galvan_lab.hack_name_3"};
	/** kurze Wirkung unter jedem Knoten (die langen Texte zeigt das Omnitrix OS beim Freischalten) */
	private static final String[] HACK_EFFECTS = {"screen.kingdomomnitrix.galvan_lab.hack_effect_1",
			"screen.kingdomomnitrix.galvan_lab.hack_effect_2", "screen.kingdomomnitrix.galvan_lab.hack_effect_3"};

	private int tab;
	private int scroll;
	private final long openedAt = System.nanoTime();

	public GalvanLabScreen() {
		super(Text.translatable("screen.kingdomomnitrix.galvan_lab"));
	}

	/** Nur als Grey Matter; sonst kurzer Hinweis. */
	public static void open(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		if (!GalvanLab.isGreyMatter(client.player)) {
			client.player.sendMessage(Text.translatable("message.kingdomomnitrix.galvan_only"), true);
			return;
		}
		client.setScreen(new GalvanLabScreen());
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
	public void tick() {
		// zurueckverwandelt: das Labor schliesst sich
		if (client != null && client.player != null && !GalvanLab.isGreyMatter(client.player)) {
			close();
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0x70000000);
	}

	private int left() {
		return (width - WIDTH) / 2;
	}

	private int top() {
		return (height - HEIGHT) / 2;
	}

	private List<Map.Entry<Identifier, GalvanInvention>> inventions() {
		List<Map.Entry<Identifier, GalvanInvention>> list = new ArrayList<>();
		if (client == null || client.world == null) {
			return list;
		}
		client.world.getRegistryManager().getOptional(GalvanInvention.KEY).ifPresent(registry ->
				registry.getEntrySet().forEach(e -> list.add(Map.entry(e.getKey().getValue(), e.getValue()))));
		list.sort(Comparator.comparingInt((Map.Entry<Identifier, GalvanInvention> e) -> e.getValue().sortOrder())
				.thenComparing(e -> e.getKey().toString()));
		return list;
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
		float open = Math.min(1.0f, (System.nanoTime() - openedAt) / 1.0e9f / 0.15f);
		int alpha = Math.round(255 * open);
		int x = left();
		int y = top();
		// Galvan-Technik: dunkles Gruen, Schaltkreis-Linien
		context.fill(x, y, x + WIDTH, y + HEIGHT, (Math.round(0xF2 * open) << 24) | 0x041008);
		for (int i = 0; i < 6; i++) {
			int lx = x + 18 + i * 52;
			context.fill(lx, y + 34, lx + 1, y + HEIGHT - 6, 0x1239FF14);
		}
		context.fill(x, y, x + WIDTH, y + 1, alpha << 24 | ACCENT);
		context.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, (alpha / 2) << 24 | ACCENT);
		context.drawTextWithShadow(textRenderer, title, x + 10, y + 8, alpha << 24 | ACCENT);
		Text knowledge = Text.translatable("screen.kingdomomnitrix.galvan_lab.knowledge", GreyMatterKnowledge.total(player));
		context.drawTextWithShadow(textRenderer, knowledge, x + WIDTH - 10 - textRenderer.getWidth(knowledge), y + 8, 0xFFB8FFB0);

		// Reiter
		String[] tabs = {"screen.kingdomomnitrix.galvan_lab.inventions", "screen.kingdomomnitrix.galvan_lab.hack"};
		for (int i = 0; i < tabs.length; i++) {
			int tx = x + 10 + i * 100;
			boolean active = tab == i;
			boolean hover = inside(mouseX, mouseY, tx, y + 20, 96, 12);
			context.fill(tx, y + 20, tx + 96, y + 32, active ? 0xFF0E3A12 : hover ? 0xFF0A2A0E : 0xFF061A0A);
			context.fill(tx, y + 31, tx + 96, y + 32, active ? 0xFF000000 | ACCENT : 0xFF1A3A1E);
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable(tabs[i]), tx + 48, y + 22, active ? 0xFFFFFFFF : 0xFF8FB09A);
		}

		if (tab == 0) {
			renderInventions(context, player, mouseX, mouseY, x, y + 38);
		} else {
			renderHack(context, player, mouseX, mouseY, x, y + 38);
		}

		com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixHolo.visible().ifPresent(message -> {
			int my = y + HEIGHT - 14;
			context.fill(x + 6, my - 3, x + WIDTH - 6, my + 10, 0x60000000);
			context.drawTextWithShadow(textRenderer, message.title().copy().append(" · ").append(message.body()), x + 10, my, 0xFF000000 | message.color());
		});
	}

	private void renderInventions(DrawContext context, ClientPlayerEntity player, int mouseX, int mouseY, int x, int y) {
		List<Map.Entry<Identifier, GalvanInvention>> list = inventions();
		if (list.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.galvan_lab.none"), x + 10, y + 6, 0xFF8FB09A);
			return;
		}
		scroll = MathHelper.clamp(scroll, 0, Math.max(0, list.size() - VISIBLE));
		int knowledge = GreyMatterKnowledge.total(player);
		int hack = GalvanHack.state(player).level();
		long bolts = HeroDataAccess.get(player).bolts();
		for (int i = 0; i < VISIBLE && scroll + i < list.size(); i++) {
			GalvanInvention invention = list.get(scroll + i).getValue();
			int ry = y + i * ROW;
			context.fill(x + 6, ry, x + WIDTH - 12, ry + ROW - 4, 0x50000000);
			ItemStack result = new ItemStack(invention.result(), invention.count());
			context.drawItem(result, x + 10, ry + 4);
			context.drawItemInSlot(textRenderer, result, x + 10, ry + 4);
			context.drawTextWithShadow(textRenderer, result.getName(), x + 30, ry + 3, 0xFFFFFFFF);
			// Zutaten als kleine Symbole mit Menge (gruen = genug, rot = fehlt)
			boolean ready = true;
			int ix = x + 30;
			for (GalvanInvention.Ingredient ingredient : invention.ingredients()) {
				int have = player.getInventory().count(ingredient.item());
				boolean enough = player.getAbilities().creativeMode || have >= ingredient.count();
				ready &= enough;
				context.getMatrices().push();
				context.getMatrices().translate(ix, ry + 14, 0);
				context.getMatrices().scale(0.75f, 0.75f, 1.0f);
				context.drawItem(new ItemStack(ingredient.item()), 0, 0);
				context.getMatrices().pop();
				String count = "×" + ingredient.count();
				context.drawTextWithShadow(textRenderer, count, ix + 13, ry + 18, enough ? OK : MISSING);
				ix += 16 + textRenderer.getWidth(count);
			}
			// Voraussetzungen: Bolts, Wissen, Hack-Stufe
			List<Text> needs = new ArrayList<>();
			if (invention.bolts() > 0) {
				boolean ok = player.getAbilities().creativeMode || bolts >= invention.bolts();
				ready &= ok;
				needs.add(Text.translatable("screen.kingdomomnitrix.galvan_lab.bolts", invention.bolts()).withColor((ok ? OK : MISSING) & 0xFFFFFF));
			}
			if (invention.knowledge() > 0) {
				boolean ok = knowledge >= invention.knowledge();
				ready &= ok;
				needs.add(Text.translatable("screen.kingdomomnitrix.galvan_lab.needs_knowledge", invention.knowledge()).withColor((ok ? OK : MISSING) & 0xFFFFFF));
			}
			if (invention.hackLevel() > 0) {
				boolean ok = hack >= invention.hackLevel();
				ready &= ok;
				needs.add(Text.translatable("screen.kingdomomnitrix.galvan_lab.needs_hack", invention.hackLevel()).withColor((ok ? OK : MISSING) & 0xFFFFFF));
			}
			int nx = x + 30;
			for (Text need : needs) {
				context.drawTextWithShadow(textRenderer, need, nx, ry + 26, 0xFFFFFFFF);
				nx += textRenderer.getWidth(need) + 8;
			}
			button(context, mouseX, mouseY, buttonX(), ry + 10, Text.translatable("screen.kingdomomnitrix.galvan_lab.build"), ready);
		}
		if (list.size() > VISIBLE) {
			// Laufleiste
			int track = VISIBLE * ROW - 4;
			int bar = Math.max(12, track * VISIBLE / list.size());
			int by = y + (track - bar) * scroll / Math.max(1, list.size() - VISIBLE);
			context.fill(x + WIDTH - 8, y, x + WIDTH - 6, y + track, 0xFF0A2A0E);
			context.fill(x + WIDTH - 8, by, x + WIDTH - 6, by + bar, 0xFF000000 | ACCENT);
		}
	}

	private void renderHack(DrawContext context, ClientPlayerEntity player, int mouseX, int mouseY, int x, int y) {
		GalvanHack.State state = GalvanHack.state(player);
		int minutes = state.ticks() / 1200;
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.galvan_lab.research", minutes), x + 10, y + 4, 0xFFB8FFB0);
		// drei Knoten mit Verbindung
		int nodeY = y + 34;
		for (int level = 1; level <= GalvanHack.MAX_LEVEL; level++) {
			int nx = x + 30 + (level - 1) * 110;
			boolean done = state.level() >= level;
			boolean available = !done && state.available() >= level;
			int color = done ? 0xFF000000 | ACCENT : available ? 0xFFFFD84A : 0xFF33463A;
			if (level < GalvanHack.MAX_LEVEL) {
				context.fill(nx + 24, nodeY + 11, nx + 110, nodeY + 13, done ? 0xFF000000 | ACCENT : 0xFF1A3A1E);
			}
			context.fill(nx, nodeY, nx + 24, nodeY + 24, 0xFF061A0A);
			context.drawBorder(nx, nodeY, 24, 24, color);
			context.drawCenteredTextWithShadow(textRenderer, String.valueOf(level), nx + 12, nodeY + 8, color);
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable(HACK_NAMES[level - 1]), nx + 12, nodeY + 30, 0xFFFFFFFF);
			context.getMatrices().push();
			context.getMatrices().translate(nx + 12, nodeY + 42, 0);
			context.getMatrices().scale(0.7f, 0.7f, 1.0f);
			Text effect = Text.translatable(HACK_EFFECTS[level - 1]);
			context.drawCenteredTextWithShadow(textRenderer, effect, 0, 0, 0xFF9ACFA8);
			Text status = done ? Text.translatable("screen.kingdomomnitrix.galvan_lab.installed")
					: available ? Text.translatable("screen.kingdomomnitrix.galvan_lab.available")
					: Text.translatable("screen.kingdomomnitrix.galvan_lab.locked", Math.max(1, (GalvanHack.THRESHOLDS[level - 1] - state.ticks() + 1199) / 1200));
			context.drawCenteredTextWithShadow(textRenderer, status, 0, 12, color);
			context.getMatrices().pop();
		}
		// Fortschritt zur naechsten Stufe
		int barY = y + 110;
		context.fill(x + 30, barY, x + WIDTH - 30, barY + 5, 0xFF0A2A0E);
		context.fill(x + 30, barY, x + 30 + Math.round((WIDTH - 60) * state.progress()), barY + 5, 0xFF000000 | ACCENT);
		boolean canHack = state.canHack();
		Text hint = Text.translatable(canHack ? "screen.kingdomomnitrix.galvan_lab.hack_hint" : "screen.kingdomomnitrix.galvan_lab.hack_wait");
		context.drawCenteredTextWithShadow(textRenderer, hint, x + WIDTH / 2, barY + 12, 0xFF8FB09A);
		button(context, mouseX, mouseY, x + WIDTH / 2 - 40, barY + 26, Text.translatable("screen.kingdomomnitrix.galvan_lab.start_hack"), canHack, 80);
	}

	private int buttonX() {
		return left() + WIDTH - 16 - BUTTON_W;
	}

	private void button(DrawContext context, int mouseX, int mouseY, int bx, int by, Text label, boolean enabled) {
		button(context, mouseX, mouseY, bx, by, label, enabled, BUTTON_W);
	}

	private void button(DrawContext context, int mouseX, int mouseY, int bx, int by, Text label, boolean enabled, int w) {
		boolean hover = enabled && inside(mouseX, mouseY, bx, by, w, BUTTON_H);
		context.fill(bx, by, bx + w, by + BUTTON_H, hover ? 0xFF1E5D22 : 0xFF0C2A10);
		context.drawBorder(bx, by, w, BUTTON_H, enabled ? 0xFF000000 | ACCENT : 0xFF2E3A30);
		context.drawCenteredTextWithShadow(textRenderer, label, bx + w / 2, by + 3, enabled ? 0xFFFFFFFF : 0xFF4E5A50);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int x = left();
		int y = top();
		for (int i = 0; i < 2; i++) {
			if (inside(mouseX, mouseY, x + 10 + i * 100, y + 20, 96, 12)) {
				tab = i;
				OmnitrixFeedback.play(OmnitrixCue.NAVIGATE);
				return true;
			}
		}
		if (tab == 0) {
			List<Map.Entry<Identifier, GalvanInvention>> list = inventions();
			for (int i = 0; i < VISIBLE && scroll + i < list.size(); i++) {
				int ry = y + 38 + i * ROW;
				if (inside(mouseX, mouseY, buttonX(), ry + 10, BUTTON_W, BUTTON_H)) {
					ClientPlayNetworking.send(new GalvanActionPayload("craft", list.get(scroll + i).getKey()));
					OmnitrixFeedback.play(OmnitrixCue.CONFIRM);
					return true;
				}
			}
		} else if (inside(mouseX, mouseY, x + WIDTH / 2 - 40, y + 38 + 110 + 26, 80, BUTTON_H)) {
			ClientPlayNetworking.send(new GalvanActionPayload("hack", Identifier.ofVanilla("none")));
			OmnitrixFeedback.play(OmnitrixCue.CONFIRM);
			close();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (tab == 0) {
			scroll -= (int) Math.signum(verticalAmount);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}
}
