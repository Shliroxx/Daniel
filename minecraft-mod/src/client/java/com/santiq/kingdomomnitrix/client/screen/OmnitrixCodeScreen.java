package com.santiq.kingdomomnitrix.client.screen;

import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixFeedback;
import com.santiq.kingdomomnitrix.networking.OmnitrixCodePayload;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCode;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Omnitrix-Code-Tastatur: Hologramm-Ziffernblock (1–9, Loeschen, 0, Bestaetigen). Maus oder Tastatur (Ziffern,
 * Ziffernblock, Ruecktaste, Eingabe, Esc). Der Code geht an den Server — welche Codes es gibt, weiss nur er.
 */
public class OmnitrixCodeScreen extends Screen {
	private static final int KEY = 26;
	private static final int GAP = 4;
	private static final int PAD = 10;
	private static final int DISPLAY = 22;
	private static final String[] LABELS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✔"};
	private static final int GREEN = 0x39FF14;

	private final StringBuilder code = new StringBuilder();
	private final long openedAt = System.nanoTime();
	private int hovered = -1;
	private int pressed = -1;
	private long pressedAt;

	public OmnitrixCodeScreen() {
		super(Text.translatable("screen.kingdomomnitrix.code"));
	}

	public static void open(MinecraftClient client) {
		client.setScreen(new OmnitrixCodeScreen());
		OmnitrixFeedback.play(OmnitrixCue.OPEN);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0x60000000);
	}

	private int panelWidth() {
		return PAD * 2 + KEY * 3 + GAP * 2;
	}

	private int panelHeight() {
		return PAD * 2 + 12 + DISPLAY + GAP + KEY * 4 + GAP * 3 + 12;
	}

	private int left() {
		return (width - panelWidth()) / 2;
	}

	private int top() {
		return (height - panelHeight()) / 2;
	}

	private int keyX(int index) {
		return left() + PAD + (index % 3) * (KEY + GAP);
	}

	private int keyY(int index) {
		return top() + PAD + 12 + DISPLAY + GAP + (index / 3) * (KEY + GAP);
	}

	private int keyAt(double mouseX, double mouseY) {
		for (int i = 0; i < LABELS.length; i++) {
			if (mouseX >= keyX(i) && mouseX < keyX(i) + KEY && mouseY >= keyY(i) && mouseY < keyY(i) + KEY) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		hovered = keyAt(mouseX, mouseY);
		float open = MathHelper.clamp((System.nanoTime() - openedAt) / 1.0e9f / 0.15f, 0.0f, 1.0f);
		int alpha = Math.round(255 * open);
		int x = left();
		int y = top();
		int w = panelWidth();
		int h = panelHeight();
		// Hologramm-Panel: dunkles Glas, gruene Kanten, Eck-Klammern
		context.fill(x, y, x + w, y + h, (Math.round(0xD8 * open) << 24) | 0x06120A);
		context.fill(x, y, x + w, y + 1, alpha << 24 | GREEN);
		context.fill(x, y + h - 1, x + w, y + h, (alpha / 2) << 24 | GREEN);
		context.fill(x, y, x + 1, y + 6, alpha << 24 | GREEN);
		context.fill(x + w - 1, y, x + w, y + 6, alpha << 24 | GREEN);
		context.getMatrices().push();
		context.getMatrices().translate(x + w / 2.0f, y + PAD - 2, 0.0f);
		context.getMatrices().scale(0.75f, 0.75f, 1.0f);
		context.drawCenteredTextWithShadow(textRenderer, title, 0, 0, alpha << 24 | GREEN);
		context.getMatrices().pop();

		// Anzeige: eingegebene Ziffern, blinkender Cursor
		int dy = y + PAD + 12;
		context.fill(x + PAD, dy, x + w - PAD, dy + DISPLAY, (alpha / 2) << 24 | 0x0A2A12);
		context.drawBorder(x + PAD, dy, w - PAD * 2, DISPLAY, (alpha * 3 / 4) << 24 | GREEN);
		boolean cursor = (System.currentTimeMillis() / 400) % 2 == 0 && code.length() < OmnitrixCode.MAX_LENGTH;
		String shown = code + (cursor ? "_" : " ");
		context.drawCenteredTextWithShadow(textRenderer, shown, x + w / 2, dy + (DISPLAY - textRenderer.fontHeight) / 2 + 1,
				alpha << 24 | 0xB8FFB0);

		long now = System.currentTimeMillis();
		for (int i = 0; i < LABELS.length; i++) {
			int kx = keyX(i);
			int ky = keyY(i);
			boolean flash = i == pressed && now - pressedAt < 140;
			int fill = flash ? 0x2E8F1A : i == hovered ? 0x174D12 : 0x0C2410;
			context.fill(kx, ky, kx + KEY, ky + KEY, (Math.round(0xE0 * open) << 24) | fill);
			context.drawBorder(kx, ky, KEY, KEY, (i == hovered ? alpha : alpha * 2 / 3) << 24 | GREEN);
			int color = i == 9 ? 0xFF8A3C : i == 11 ? 0x7DFF9C : 0xCFFFC8;
			context.drawCenteredTextWithShadow(textRenderer, LABELS[i], kx + KEY / 2, ky + (KEY - textRenderer.fontHeight) / 2 + 1,
					alpha << 24 | color);
		}
		context.getMatrices().push();
		context.getMatrices().translate(x + w / 2.0f, y + h - PAD + 1, 0.0f);
		context.getMatrices().scale(0.6f, 0.6f, 1.0f);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.code_hint"), 0, 0, (alpha * 3 / 4) << 24 | 0x9ACFA8);
		context.getMatrices().pop();
	}

	private void press(int index) {
		pressed = index;
		pressedAt = System.currentTimeMillis();
		switch (LABELS[index]) {
			case "⌫" -> {
				if (!code.isEmpty()) {
					code.setLength(code.length() - 1);
				}
				OmnitrixFeedback.play(OmnitrixCue.CANCEL);
			}
			case "✔" -> submit();
			default -> digit(LABELS[index].charAt(0));
		}
	}

	private void digit(char c) {
		if (code.length() < OmnitrixCode.MAX_LENGTH) {
			code.append(c);
			OmnitrixFeedback.play(OmnitrixCue.NAVIGATE);
		} else {
			OmnitrixFeedback.play(OmnitrixCue.ERROR);
		}
	}

	private void submit() {
		if (code.length() < OmnitrixCode.MIN_LENGTH) {
			OmnitrixFeedback.play(OmnitrixCue.ERROR);
			return;
		}
		ClientPlayNetworking.send(new OmnitrixCodePayload(code.toString()));
		OmnitrixFeedback.play(OmnitrixCue.CONFIRM);
		close();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int index = keyAt(mouseX, mouseY);
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && index >= 0) {
			press(index);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
			int digit = keyCode - GLFW.GLFW_KEY_0;
			press(digit == 0 ? 10 : digit - 1);
			return true;
		}
		if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
			int digit = keyCode - GLFW.GLFW_KEY_KP_0;
			press(digit == 0 ? 10 : digit - 1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			press(9);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			press(11);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
