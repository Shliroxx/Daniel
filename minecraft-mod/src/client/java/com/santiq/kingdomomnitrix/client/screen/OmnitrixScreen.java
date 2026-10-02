package com.santiq.kingdomomnitrix.client.screen;

import com.santiq.kingdomomnitrix.alien.AlienUniforms;
import com.santiq.kingdomomnitrix.alien.OmnitrixPhase;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.client.render.alien.AlienBodyRenderers;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.client.omnitrix.SmartScan;
import org.lwjgl.glfw.GLFW;

/**
 * Eingabeschicht des Omnitrix. Das Rad selbst ist 3D und wird mit der Ego-Hand gezeichnet
 * ({@code OmnitrixDialDisplay} auf dem Zifferblatt); dieser Screen faengt nur Maus und Tasten ab und blendet den Namen des gewaehlten
 * Aliens klein ein — kein Menue-Hintergrund, keine Unschaerfe.
 *
 * <ul>
 *     <li>Mausrad, Maus seitwaerts ziehen, A/D oder Pfeiltasten: Rad drehen</li>
 *     <li>Linksklick, Enter, Leertaste oder Omnitrix-Taste: bestaetigen (verwandelt: zurueckverwandeln)</li>
 *     <li>1–9: direkt zu einem Alien drehen · U: Uniform wechseln · Rechtsklick/Esc: schliessen</li>
 * </ul>
 */
public class OmnitrixScreen extends Screen {
	/** Maus-Weg in Pixeln pro Rad-Schritt */
	private static final double DRAG_STEP = 36.0;
	private double dragAccumulator;
	private double lastMouseX = Double.NaN;

	public OmnitrixScreen() {
		super(Text.translatable("screen.kingdomomnitrix.omnitrix"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// nur ein Hauch Abdunklung an den Raendern, das Geraet bleibt die Buehne
		context.fillGradient(0, height * 2 / 3, width, height, 0x00000000, 0x40000000);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		if (client == null || client.player == null) {
			return;
		}
		float wheel = OmnitrixController.wheel();
		int alpha = MathHelper.clamp((int) (wheel * 255.0f), 0, 255);
		if (alpha < 8) {
			return;
		}
		// klein ueber der Hotbar, wie bei Alien Evolution
		int x = width / 2;
		int y = height - 66;
		TransformationState state = TransformationManager.get(client.player);
		long now = client.player.getWorld().getTime();
		if (OmnitrixController.entries().isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("message.kingdomomnitrix.no_dna"), x, y, alpha << 24 | 0xAAAAAA);
			return;
		}
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0.0f);
		context.getMatrices().scale(0.85f, 0.85f, 1.0f);
		boolean revertChoice = state.isTransformed() && OmnitrixController.focused()
				.map(entry -> state.activeAlien().filter(entry.id()::equals).isPresent()).orElse(true);
		if (revertChoice) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.revert"), 0, 0, alpha << 24 | 0x39FF6A);
		} else {
			OmnitrixController.focused().ifPresent(entry -> {
				Text name = entry.unlocked()
						? Text.empty().append(OmnitrixController.isFavorite(entry.id()) ? Text.literal("★ ").formatted(Formatting.GOLD) : Text.empty())
								.append(TransformationManager.alienName(entry.id()).formatted(Formatting.BOLD))
						: Text.literal("? ? ?").formatted(Formatting.BOLD);
				int color = entry.unlocked() ? 0x7DFF9C : 0x4A5A50;
				context.drawCenteredTextWithShadow(textRenderer, name, 0, 0, alpha << 24 | color);
				long recharge = state.isTransformed() ? 0L : state.rechargeRemaining(now);
				if (entry.unlocked() && AlienBodyRenderers.uniformsOf(entry.alien().model()).size() > 1) {
					String uniform = AlienUniforms.get(client.player, entry.id());
					context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.uniform",
							Text.translatable("uniform.kingdomomnitrix." + uniform)), 0, -11, alpha << 24 | 0x9ACFA8);
				}
				if (recharge > 0) {
					context.drawCenteredTextWithShadow(textRenderer,
							Text.translatable("hud.kingdomomnitrix.recharging", (recharge + 19) / 20), 0, 11, alpha << 24 | 0xFF5050);
				} else if (!entry.unlocked()) {
					context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.locked"), 0, 11,
							alpha << 24 | 0x6A7A70);
				}
			});
		}
		context.getMatrices().pop();
		// Smart-Scan: Empfehlung ueber dem Namen, pulsierend; gewaehlt = gruen hervorgehoben
		SmartScan.recommendation().ifPresent(pick -> {
			boolean focusedPick = OmnitrixController.focused().map(entry -> entry.id().equals(pick.alien())).orElse(false);
			float pulse = 0.6f + 0.4f * MathHelper.sin((System.nanoTime() % 1_000_000_000_000L) / 1.0e9f * 5.0f);
			int light = (int) (alpha * (focusedPick ? 1.0f : pulse));
			Text line = Text.translatable("scan.kingdomomnitrix.recommended",
					TransformationManager.alienName(pick.alien()).formatted(Formatting.BOLD), Text.translatable(pick.reason()));
			context.getMatrices().push();
			context.getMatrices().translate(width / 2.0f, height - 92.0f, 0.0f);
			context.getMatrices().scale(0.8f, 0.8f, 1.0f);
			context.drawCenteredTextWithShadow(textRenderer, line, 0, 0, MathHelper.clamp(light, 0, 255) << 24 | (focusedPick ? 0x7DFF9C : 0xFFD84A));
			context.getMatrices().pop();
		});
		if (OmnitrixController.phase() != OmnitrixPhase.CONFIRMING) {
			Text hint = Text.translatable("screen.kingdomomnitrix.omnitrix_hint").append("  ·  ")
					.append(Text.translatable("hud.kingdomomnitrix.favorite_set", OmnitrixCore.state(client.player).activeSet() + 1))
					.append(" · ").append(Text.translatable("hud.kingdomomnitrix.favorite_hint"));
			context.getMatrices().push();
			context.getMatrices().translate(width / 2.0f, height - 44.0f, 0.0f);
			context.getMatrices().scale(0.75f, 0.75f, 1.0f);
			context.drawCenteredTextWithShadow(textRenderer, hint, 0, 0, (alpha * 3 / 4) << 24 | 0x9ACFA8);
			context.getMatrices().pop();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double amount = verticalAmount != 0.0 ? verticalAmount : horizontalAmount;
		if (amount != 0.0) {
			OmnitrixController.rotate(amount > 0 ? -1 : 1);
		}
		return true;
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (!Double.isNaN(lastMouseX)) {
			dragAccumulator += mouseX - lastMouseX;
			while (dragAccumulator >= DRAG_STEP) {
				dragAccumulator -= DRAG_STEP;
				OmnitrixController.rotate(1);
			}
			while (dragAccumulator <= -DRAG_STEP) {
				dragAccumulator += DRAG_STEP;
				OmnitrixController.rotate(-1);
			}
		}
		lastMouseX = mouseX;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			OmnitrixController.confirm();
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			close();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
				OmnitrixController.rotate(-1);
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
				OmnitrixController.rotate(1);
				return true;
			}
			case GLFW.GLFW_KEY_U -> {
				OmnitrixController.cycleUniform();
				return true;
			}
			case GLFW.GLFW_KEY_F -> {
				OmnitrixController.toggleFavorite();
				return true;
			}
			case GLFW.GLFW_KEY_TAB -> {
				OmnitrixController.cycleFavoriteSet();
				return true;
			}
			case GLFW.GLFW_KEY_N -> {
				// Smart-Scan: Rad zur Empfehlung drehen
				SmartScan.rescan().ifPresent(pick -> OmnitrixController.rotateToAlien(pick.alien()));
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
				OmnitrixController.confirm();
				return true;
			}
			default -> {
			}
		}
		if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
			OmnitrixController.rotateTo(keyCode - GLFW.GLFW_KEY_1);
			return true;
		}
		if (ModKeyBindings.OPEN_OMNITRIX.matchesKey(keyCode, scanCode)) {
			OmnitrixController.confirm();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		OmnitrixController.cancel();
		super.close();
	}

	@Override
	public void removed() {
		OmnitrixController.cancel();
	}
}
