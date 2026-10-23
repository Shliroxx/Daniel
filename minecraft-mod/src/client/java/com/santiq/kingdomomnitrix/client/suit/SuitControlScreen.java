package com.santiq.kingdomomnitrix.client.suit;

import com.santiq.kingdomomnitrix.suit.SuitSet;
import com.santiq.kingdomomnitrix.suit.power.SuitTogglePayload;
import com.santiq.kingdomomnitrix.suit.power.SuitToggles;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Anzugsteuerung (Taste P): Schild, Fortbewegung und Werte-Boni einzeln an- und abschalten, dazu die Setboni des
 * getragenen Anzugs. Der Zustand liegt in der Brustplatte; der Server bestaetigt ueber das naechste Statuspaket.
 */
public class SuitControlScreen extends Screen {
	private static final int WIDTH = 220;
	private static final int HEIGHT = 170;
	private static final String[] LABELS = {"screen.kingdomomnitrix.suit.shield", "screen.kingdomomnitrix.suit.mobility",
			"screen.kingdomomnitrix.suit.boost"};
	private static final int[] BITS = {SuitToggles.SHIELD, SuitToggles.FLIGHT, SuitToggles.BOOST};
	private final ButtonWidget[] buttons = new ButtonWidget[3];

	public SuitControlScreen() {
		super(Text.translatable("screen.kingdomomnitrix.suit.title"));
	}

	@Override
	protected void init() {
		int x = (width - WIDTH) / 2;
		int y = (height - HEIGHT) / 2;
		for (int i = 0; i < 3; i++) {
			int index = i;
			buttons[i] = addDrawableChild(ButtonWidget.builder(label(i), b -> ClientPlayNetworking.send(new SuitTogglePayload(BITS[index])))
					.dimensions(x + 10, y + 26 + i * 22, WIDTH - 20, 20).build());
		}
	}

	private Text label(int i) {
		boolean on = SuitToggles.on(SuitClient.toggles(), BITS[i]);
		return Text.translatable(LABELS[i]).append(": ").append(Text.translatable(on ? "screen.kingdomomnitrix.suit.on"
				: "screen.kingdomomnitrix.suit.off").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	@Override
	public void tick() {
		if (SuitClient.current() == null) {
			close();
			return;
		}
		for (int i = 0; i < 3; i++) {
			buttons[i].setMessage(label(i));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		SuitSet suit = SuitClient.current();
		if (suit == null) {
			return;
		}
		int x = (width - WIDTH) / 2;
		int y = (height - HEIGHT) / 2;
		int accent = 0xFF000000 | suit.color();
		context.drawBorder(x - 1, y - 1, WIDTH + 2, HEIGHT + 2, accent);
		context.drawCenteredTextWithShadow(textRenderer, title, x + WIDTH / 2, y + 8, accent);
		int ty = y + 98;
		context.drawTextWithShadow(textRenderer, Text.translatable("tooltip.kingdomomnitrix.suit.set_bonus"), x + 10, ty, 0xFFFFD84A);
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(suit.bonusKey()), WIDTH - 20);
		for (OrderedText line : lines) {
			ty += 10;
			context.drawTextWithShadow(textRenderer, line, x + 10, ty, 0xFFD8E0EA);
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		int x = (width - WIDTH) / 2;
		int y = (height - HEIGHT) / 2;
		context.fill(x, y, x + WIDTH, y + HEIGHT, 0xF0141820);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
