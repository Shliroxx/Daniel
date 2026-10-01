package com.santiq.kingdomomnitrix.client.screen;

import com.santiq.kingdomomnitrix.alien.AbilitySlot;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.networking.RevertRequestPayload;
import com.santiq.kingdomomnitrix.networking.TransformRequestPayload;
import com.santiq.kingdomomnitrix.player.HeroData;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Alien-Rad des Omnitrix.
 * <ul>
 *     <li>Maus auf ein Alien + Linksklick (oder Omnitrix-Taste loslassen): verwandeln</li>
 *     <li>Mitte (nur verwandelt): zurueckverwandeln</li>
 *     <li>Zifferntasten 1–9: Alien direkt waehlen · Esc: schliessen</li>
 * </ul>
 * Gesperrte Aliens erscheinen als dunkle "?"-Felder, damit der Spieler weiss, dass es mehr zu finden gibt.
 */
public class OmnitrixWheelScreen extends Screen {
	private static final int TILE = 34;
	private static final int CENTER_RADIUS = 22;
	private static final int OMNITRIX_GREEN = 0xFF39FF14;
	private static final int LOCKED_COLOR = 0xFF2A2F3A;

	private record Entry(Identifier id, AlienDefinition alien, boolean unlocked) {
	}

	private final List<Entry> entries = new ArrayList<>();
	/** true, solange das Loslassen der Omnitrix-Taste noch als Auswahl zaehlt. */
	private boolean selectOnKeyRelease;
	private int hovered = -1;
	private boolean hoveringCenter;

	private OmnitrixWheelScreen(boolean openedByKey) {
		super(Text.translatable("screen.kingdomomnitrix.omnitrix"));
		this.selectOnKeyRelease = openedByKey;
	}

	public static void open(MinecraftClient client) {
		client.setScreen(new OmnitrixWheelScreen(true));
	}

	public static void openFromItem(MinecraftClient client) {
		client.setScreen(new OmnitrixWheelScreen(false));
	}

	@Override
	protected void init() {
		entries.clear();
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		HeroData data = HeroDataAccess.get(client.player);
		var manager = client.world.getRegistryManager();
		for (Identifier id : AlienRegistry.sortedIds(manager)) {
			AlienRegistry.get(manager, id).ifPresent(alien -> entries.add(new Entry(id, alien, data.hasAlien(id))));
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private int centerX() {
		return width / 2;
	}

	private int centerY() {
		return height / 2 - 10;
	}

	private int radius() {
		return MathHelper.clamp(Math.min(width, height) / 4, 60, 110);
	}

	private int[] tilePosition(int index) {
		double angle = Math.PI * 2 * index / Math.max(1, entries.size()) - Math.PI / 2;
		int x = centerX() + (int) Math.round(Math.cos(angle) * radius());
		int y = centerY() + (int) Math.round(Math.sin(angle) * radius());
		return new int[] {x, y};
	}

	private void updateHover(double mouseX, double mouseY) {
		double dx = mouseX - centerX();
		double dy = mouseY - centerY();
		double distance = Math.sqrt(dx * dx + dy * dy);
		hoveringCenter = distance <= CENTER_RADIUS && isTransformed();
		hovered = -1;
		if (distance <= CENTER_RADIUS || entries.isEmpty()) {
			return;
		}
		double angle = Math.atan2(dy, dx) + Math.PI / 2;
		if (angle < 0) {
			angle += Math.PI * 2;
		}
		double step = Math.PI * 2 / entries.size();
		hovered = (int) Math.round(angle / step) % entries.size();
	}

	private boolean isTransformed() {
		return client != null && client.player != null && TransformationManager.get(client.player).isTransformed();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		updateHover(mouseX, mouseY);
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		TransformationState state = TransformationManager.get(player);
		long now = player.getWorld().getTime();

		context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GREEN, Formatting.BOLD),
				centerX(), centerY() - radius() - TILE, 0xFFFFFFFF);

		if (entries.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("message.kingdomomnitrix.no_dna"), centerX(), centerY(), 0xFFAAAAAA);
			return;
		}

		drawCenter(context, state, now);
		for (int i = 0; i < entries.size(); i++) {
			drawTile(context, i, state);
		}
		drawInfo(context, state, now);
	}

	private void drawCenter(DrawContext context, TransformationState state, long now) {
		int x = centerX();
		int y = centerY();
		int color = hoveringCenter ? 0xFFFFFFFF : OMNITRIX_GREEN;
		context.fill(x - CENTER_RADIUS, y - CENTER_RADIUS, x + CENTER_RADIUS, y + CENTER_RADIUS, 0xE0101010);
		context.drawBorder(x - CENTER_RADIUS, y - CENTER_RADIUS, CENTER_RADIUS * 2, CENTER_RADIUS * 2, color);
		// Sanduhr-Symbol
		for (int row = 0; row < 8; row++) {
			int halfWidth = 1 + (int) (Math.abs(3.5f - row) * 1.5f);
			context.fill(x - halfWidth, y - 8 + row * 2, x + halfWidth, y - 6 + row * 2, color);
		}
		if (state.isTransformed()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.revert"), x, y + CENTER_RADIUS + 3, color);
		} else if (state.rechargeRemaining(now) > 0) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("hud.kingdomomnitrix.recharging", (state.rechargeRemaining(now) + 19) / 20),
					x, y + CENTER_RADIUS + 3, 0xFFFF5555);
		}
	}

	private void drawTile(DrawContext context, int index, TransformationState state) {
		Entry entry = entries.get(index);
		int[] pos = tilePosition(index);
		int x = pos[0] - TILE / 2;
		int y = pos[1] - TILE / 2;
		boolean isHovered = index == hovered;
		boolean isActive = state.activeAlien().map(entry.id()::equals).orElse(false);
		int base = entry.unlocked() ? 0xFF000000 | entry.alien().color() : LOCKED_COLOR;
		int grow = isHovered ? 3 : 0;

		context.fill(x - grow, y - grow, x + TILE + grow, y + TILE + grow, isHovered ? 0xFFFFFFFF : 0xFF101010);
		context.fill(x + 2 - grow, y + 2 - grow, x + TILE - 2 + grow, y + TILE - 2 + grow, base);
		if (isActive) {
			context.drawBorder(x - 5, y - 5, TILE + 10, TILE + 10, OMNITRIX_GREEN);
		}
		Text label = entry.unlocked()
				? Text.literal(initials(TransformationManager.alienName(entry.id()).getString()))
				: Text.literal("?");
		context.drawCenteredTextWithShadow(textRenderer, label, pos[0], pos[1] - 4, 0xFFFFFFFF);
		if (index < 9) {
			context.drawTextWithShadow(textRenderer, String.valueOf(index + 1), x + 3, y + 3, 0xFFDDDDDD);
		}
	}

	private void drawInfo(DrawContext context, TransformationState state, long now) {
		int y = centerY() + radius() + TILE;
		if (hoveringCenter) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.revert_hint"), centerX(), y, 0xFFFFFFFF);
			return;
		}
		if (hovered < 0) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.hint"), centerX(), y, 0xFFAAAAAA);
			return;
		}
		Entry entry = entries.get(hovered);
		if (!entry.unlocked()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.locked"), centerX(), y, 0xFFAAAAAA);
			return;
		}
		Text name = TransformationManager.alienName(entry.id()).withColor(entry.alien().color()).formatted(Formatting.BOLD);
		context.drawCenteredTextWithShadow(textRenderer, name, centerX(), y, 0xFFFFFFFF);
		int line = y + 12;
		for (int i = 0; i < entry.alien().abilities().size(); i++) {
			AbilitySlot slot = entry.alien().abilities().get(i);
			Text key = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.ABILITIES[i]).getLocalizedText();
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("screen.kingdomomnitrix.ability_line", key, Text.translatable(slot.translationKey())),
					centerX(), line, 0xFFDDDDDD);
			line += 10;
		}
		if (entry.alien().prototype()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.prototype"), centerX(), line + 2, 0xFFFFAA00);
		}
	}

	private static String initials(String name) {
		StringBuilder builder = new StringBuilder();
		for (String part : name.split("[\\s-]+")) {
			if (!part.isEmpty() && builder.length() < 3) {
				builder.append(Character.toUpperCase(part.charAt(0)));
			}
		}
		if (builder.length() == 1 && name.length() > 1) {
			builder.append(Character.toLowerCase(name.charAt(1)));
		}
		return builder.toString();
	}

	@Override
	public void tick() {
		super.tick();
		if (!selectOnKeyRelease || client == null) {
			return;
		}
		// Omnitrix-Taste losgelassen: Ist ein Alien markiert, wird direkt verwandelt; sonst bleibt das Rad fuer Klicks offen.
		InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.OPEN_OMNITRIX);
		if (key.getCategory() != InputUtil.Type.KEYSYM) {
			selectOnKeyRelease = false;
			return;
		}
		if (!InputUtil.isKeyPressed(client.getWindow().getHandle(), key.getCode())) {
			selectOnKeyRelease = false;
			if (hovered >= 0 || hoveringCenter) {
				confirm();
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			updateHover(mouseX, mouseY);
			if (hovered >= 0 || hoveringCenter) {
				confirm();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
			int index = keyCode - GLFW.GLFW_KEY_1;
			if (index < entries.size()) {
				hovered = index;
				hoveringCenter = false;
				confirm();
				return true;
			}
		}
		if (!selectOnKeyRelease && ModKeyBindings.OPEN_OMNITRIX.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void confirm() {
		if (hoveringCenter) {
			ClientPlayNetworking.send(RevertRequestPayload.INSTANCE);
			close();
			return;
		}
		Optional<Entry> entry = hovered >= 0 && hovered < entries.size() ? Optional.of(entries.get(hovered)) : Optional.empty();
		if (entry.isEmpty()) {
			return;
		}
		if (!entry.get().unlocked()) {
			if (client != null && client.player != null) {
				client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 0.5f);
			}
			hovered = -1;
			return;
		}
		ClientPlayNetworking.send(new TransformRequestPayload(entry.get().id()));
		close();
	}
}
