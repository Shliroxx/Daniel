package com.santiq.kingdomomnitrix.client.suit;

import com.santiq.kingdomomnitrix.suit.SuitArmorItem;
import com.santiq.kingdomomnitrix.suit.module.ConfiguratorScreenHandler;
import com.santiq.kingdomomnitrix.suit.module.ModuleInventory;
import com.santiq.kingdomomnitrix.suit.module.SuitConfigurator;
import com.santiq.kingdomomnitrix.suit.module.SuitModule;
import com.santiq.kingdomomnitrix.suit.module.SuitModules;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Menue des Anzug-Konfigurators im Holo-Stil: links das Ruestungsteil, in der Mitte eine drehende 3D-Projektion des
 * Teils an einer Puppe (das echte Anzugmodell mit Leuchtschicht), rechts die Modul-Sockel mit Leuchtleitungen;
 * gesperrte Sockel (ueber der Stufe des Teils) sind abgedunkelt mit Schloss. Ohne Hintergrundtextur gezeichnet.
 */
public class ConfiguratorScreen extends HandledScreen<ConfiguratorScreenHandler> {
	private static final int PANEL = 0xF0101418;
	private static final int PANEL_LIGHT = 0xFF1A2028;
	private static final int SLOT = 0xFF0A0D10;
	private static final int IDLE = 0xFF3AE8FF;
	private static final int HOLO_X = 52;
	private static final int HOLO_Y = 14;
	private static final int HOLO_W = 104;
	private static final int HOLO_H = 98;
	private ArmorStandEntity mannequin;

	public ConfiguratorScreen(ConfiguratorScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 214;
		backgroundHeight = 218;
		titleY = 4;
		playerInventoryTitleX = ConfiguratorScreenHandler.INVENTORY_X;
		playerInventoryTitleY = ConfiguratorScreenHandler.INVENTORY_Y - 10;
	}

	public static void register() {
		HandledScreens.register(SuitConfigurator.SCREEN, ConfiguratorScreen::new);
	}

	private int accent() {
		ItemStack piece = handler.piece();
		return piece.getItem() instanceof SuitArmorItem armor ? 0xFF000000 | armor.set().color() : IDLE;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		int accent = accent();
		context.drawText(textRenderer, title, titleX, titleY, accent, false);
		context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFF9AA0A8, false);
		ItemStack piece = handler.piece();
		int slots = SuitModules.slots(piece);
		List<SuitModule> installed = SuitModules.installed(piece);
		Text status = piece.isEmpty() ? Text.translatable("screen.kingdomomnitrix.configurator.empty")
				: Text.translatable("screen.kingdomomnitrix.configurator.slots", installed.size(), slots);
		context.drawCenteredTextWithShadow(textRenderer, status, HOLO_X + HOLO_W / 2, HOLO_Y + HOLO_H + 2, piece.isEmpty() ? 0xFF7A8490 : accent);
		Text label = Text.translatable("screen.kingdomomnitrix.configurator.piece");
		context.drawText(textRenderer, label, ConfiguratorScreenHandler.PIECE_X + 8 - textRenderer.getWidth(label) / 2,
				ConfiguratorScreenHandler.PIECE_Y - 13, 0xFF9AA0A8, false);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int accent = accent();
		int dim = (accent & 0x00FFFFFF) | 0x50000000;
		float time = (MinecraftClient.getInstance().world != null ? MinecraftClient.getInstance().world.getTime() : 0) + delta;
		context.fill(x, y, x + backgroundWidth, y + backgroundHeight, PANEL);
		border(context, x, y, backgroundWidth, backgroundHeight, dim);
		border(context, x + 1, y + 1, backgroundWidth - 2, backgroundHeight - 2, 0x30FFFFFF);

		// Projektionsfeld: Raster, Lauflinie, Ecken
		int hx = x + HOLO_X;
		int hy = y + HOLO_Y;
		context.fill(hx, hy, hx + HOLO_W, hy + HOLO_H, 0xFF060A0E);
		for (int i = 0; i <= HOLO_W; i += 13) {
			context.fill(hx + i, hy, hx + i + 1, hy + HOLO_H, (accent & 0x00FFFFFF) | 0x18000000);
		}
		for (int j = 0; j <= HOLO_H; j += 14) {
			context.fill(hx, hy + j, hx + HOLO_W, hy + j + 1, (accent & 0x00FFFFFF) | 0x18000000);
		}
		int scan = (int) ((time * 1.4f) % HOLO_H);
		context.fill(hx, hy + scan, hx + HOLO_W, hy + scan + 2, (accent & 0x00FFFFFF) | 0x40000000);
		corners(context, hx, hy, HOLO_W, HOLO_H, accent);
		// Sockel unter der Puppe
		context.fill(hx + HOLO_W / 2 - 26, hy + HOLO_H - 10, hx + HOLO_W / 2 + 26, hy + HOLO_H - 7, (accent & 0x00FFFFFF) | 0x90000000);
		context.fill(hx + HOLO_W / 2 - 18, hy + HOLO_H - 7, hx + HOLO_W / 2 + 18, hy + HOLO_H - 5, (accent & 0x00FFFFFF) | 0x50000000);

		ItemStack piece = handler.piece();
		if (!piece.isEmpty()) {
			mannequin(context, hx + HOLO_W / 2, hy + HOLO_H - 10, piece, time);
		}

		// Inventar: schlichte Rahmen
		for (int row = 0; row < 4; row++) {
			int sy = y + ConfiguratorScreenHandler.INVENTORY_Y + (row < 3 ? row * 18 : 58);
			for (int column = 0; column < 9; column++) {
				int sx = x + ConfiguratorScreenHandler.INVENTORY_X + column * 18;
				context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF2A3038);
				context.fill(sx, sy, sx + 16, sy + 16, SLOT);
			}
		}
		// Teil-Slot
		slotFrame(context, x + ConfiguratorScreenHandler.PIECE_X, y + ConfiguratorScreenHandler.PIECE_Y, accent, true);
		// Modul-Sockel mit Leitungen
		int slots = SuitModules.slots(piece);
		List<SuitModule> sockets = SuitModules.sockets(piece);
		for (int i = 0; i < ModuleInventory.SIZE; i++) {
			int sx = x + ConfiguratorScreenHandler.SOCKET_X;
			int sy = y + ConfiguratorScreenHandler.SOCKET_Y[i];
			boolean open = i < slots;
			boolean filled = open && i < sockets.size() && sockets.get(i) != null;
			int lineColor = filled ? 0xFF000000 | sockets.get(i).color() : open ? (accent & 0x00FFFFFF) | 0x80000000 : 0xFF2A3038;
			int pulse = filled ? (int) (0x40 + 0x3F * (0.5f + 0.5f * MathHelper.sin(time * 0.15f + i))) : 0;
			context.fill(hx + HOLO_W, sy + 7, sx - 1, sy + 9, lineColor);
			if (filled) {
				int px = hx + HOLO_W + (int) (((time * 0.8f) + i * 9) % (sx - hx - HOLO_W));
				context.fill(px, sy + 6, px + 3, sy + 10, 0xFFFFFFFF);
			}
			slotFrame(context, sx, sy, open ? (filled ? lineColor : accent) : 0xFF2A3038, open);
			if (filled) {
				context.fill(sx - 2, sy - 2, sx + 18, sy + 18, (lineColor & 0x00FFFFFF) | (pulse << 24));
			}
			if (!open) {
				lock(context, sx + 4, sy + 3);
			}
		}
	}

	/** Drehende Puppe, die das Teil traegt (zeigt das echte 3D-Modell mit Leuchtschicht). */
	private void mannequin(DrawContext context, int cx, int bottom, ItemStack piece, float time) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || !(piece.getItem() instanceof SuitArmorItem armor)) {
			return;
		}
		if (mannequin == null || mannequin.getWorld() != client.world) {
			mannequin = new ArmorStandEntity(EntityType.ARMOR_STAND, client.world);
			mannequin.setInvisible(true);
			mannequin.setNoGravity(true);
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			mannequin.equipStack(slot, slot == armor.getSlotType() ? piece : ItemStack.EMPTY);
		}
		// Teil gross ins Bild: Helm und Stiefel naeher heran, Brust/Beine ganz
		float size = switch (armor.getType()) {
			case HELMET -> 46.0f;
			case BOOTS -> 56.0f;
			default -> 40.0f;
		};
		float lift = switch (armor.getType()) {
			case HELMET -> -42.0f;
			case CHESTPLATE -> -18.0f;
			default -> 0.0f;
		};
		// Drehung ueber die Koerperausrichtung (wie im Inventar), die Kamera neigt nur leicht nach unten
		Quaternionf camera = new Quaternionf().rotateX(MathHelper.RADIANS_PER_DEGREE * 12.0f);
		Quaternionf rotation = new Quaternionf().rotateZ(MathHelper.PI).mul(camera);
		float yaw = 180.0f + (time * 2.0f) % 360.0f;
		mannequin.bodyYaw = yaw;
		mannequin.prevBodyYaw = yaw;
		mannequin.setYaw(yaw);
		mannequin.prevYaw = yaw;
		mannequin.headYaw = yaw;
		mannequin.prevHeadYaw = yaw;
		context.enableScissor(x + HOLO_X + 1, y + HOLO_Y + 1, x + HOLO_X + HOLO_W - 1, y + HOLO_Y + HOLO_H - 1);
		InventoryScreen.drawEntity(context, cx, bottom - lift, size, new Vector3f(), rotation, camera, mannequin);
		context.disableScissor();
	}

	private static void slotFrame(DrawContext context, int sx, int sy, int color, boolean open) {
		context.fill(sx - 2, sy - 2, sx + 18, sy + 18, color);
		context.fill(sx - 1, sy - 1, sx + 17, sy + 17, open ? SLOT : 0xFF15181C);
		context.fill(sx - 1, sy - 1, sx + 17, sy, PANEL_LIGHT);
	}

	private static void lock(DrawContext context, int lx, int ly) {
		int c = 0xFF4A525C;
		context.fill(lx + 2, ly, lx + 6, ly + 1, c);
		context.fill(lx + 1, ly + 1, lx + 2, ly + 5, c);
		context.fill(lx + 6, ly + 1, lx + 7, ly + 5, c);
		context.fill(lx, ly + 5, lx + 8, ly + 11, c);
		context.fill(lx + 3, ly + 7, lx + 5, ly + 9, 0xFF15181C);
	}

	private static void corners(DrawContext context, int cx, int cy, int w, int h, int color) {
		int l = 8;
		context.fill(cx - 1, cy - 1, cx + l, cy, color);
		context.fill(cx - 1, cy - 1, cx, cy + l, color);
		context.fill(cx + w - l, cy - 1, cx + w + 1, cy, color);
		context.fill(cx + w, cy - 1, cx + w + 1, cy + l, color);
		context.fill(cx - 1, cy + h, cx + l, cy + h + 1, color);
		context.fill(cx - 1, cy + h - l, cx, cy + h + 1, color);
		context.fill(cx + w - l, cy + h, cx + w + 1, cy + h + 1, color);
		context.fill(cx + w, cy + h - l, cx + w + 1, cy + h + 1, color);
	}

	private static void border(DrawContext context, int bx, int by, int w, int h, int color) {
		context.fill(bx, by, bx + w, by + 1, color);
		context.fill(bx, by + h - 1, bx + w, by + h, color);
		context.fill(bx, by, bx + 1, by + h, color);
		context.fill(bx + w - 1, by, bx + w, by + h, color);
	}
}
