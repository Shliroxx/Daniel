package com.santiq.kingdomomnitrix.client.party;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.hud.HudAnchor;
import com.santiq.kingdomomnitrix.client.hud.HudElement;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.networking.PartySyncPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Gruppen-Anzeige (Standard links in der Mitte, wie die Party-Leisten in Kingdom Hearts): jedes andere Mitglied mit
 * Name, Stufe und Lebensleiste; offline grau. Die Daten schickt der Server alle halbe Sekunde.
 */
public final class PartyHud implements HudElement {
	public static final PartyHud INSTANCE = new PartyHud();
	private static final Identifier ID = KingdomOmnitrix.id("party");
	private static final int WIDTH = 104;
	private static final int ROW = 22;
	private static final int PADDING = 4;
	private static final int HEALTH_COLOR = 0xFF5CE65C;
	private static final int LOW_HEALTH_COLOR = 0xFFFF5555;

	private static List<PartySyncPayload.Member> members = List.of();

	private PartyHud() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PartySyncPayload.ID, (payload, context) -> members = List.copyOf(payload.members()));
	}

	public static void reset() {
		members = List.of();
	}

	/** Mitglieder ohne den eigenen Spieler. */
	private static List<PartySyncPayload.Member> others(MinecraftClient client) {
		if (client.player == null) {
			return List.of();
		}
		return members.stream().filter(member -> !member.uuid().equals(client.player.getUuid())).toList();
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public Text name() {
		return Text.translatable("hud.kingdomomnitrix.element.party");
	}

	@Override
	public HudAnchor defaultAnchor() {
		return HudAnchor.MIDDLE_LEFT;
	}

	@Override
	public int defaultX() {
		return 4;
	}

	@Override
	public int defaultY() {
		return 40;
	}

	@Override
	public boolean isActive(MinecraftClient client) {
		return !others(client).isEmpty();
	}

	@Override
	public int width(MinecraftClient client) {
		return WIDTH;
	}

	@Override
	public int height(MinecraftClient client) {
		return PADDING * 2 + Math.max(1, others(client).size()) * ROW - 2;
	}

	@Override
	public int previewWidth() {
		return WIDTH;
	}

	@Override
	public int previewHeight() {
		return PADDING * 2 + 3 * ROW - 2;
	}

	@Override
	public void render(DrawContext context, MinecraftClient client, float tickDelta) {
		TextRenderer font = client.textRenderer;
		List<PartySyncPayload.Member> shown = others(client);
		UiDraw.hudPanel(context, 0, 0, WIDTH, height(client), UiTheme.HERO);
		for (int i = 0; i < shown.size(); i++) {
			PartySyncPayload.Member member = shown.get(i);
			int y = PADDING + i * ROW;
			int x = PADDING + 2;
			String name = (member.leader() ? "★ " : "") + member.name();
			int nameColor = member.online() ? UiTheme.TEXT : UiTheme.TEXT_DISABLED;
			context.drawTextWithShadow(font, UiDraw.trim(font, Text.literal(name), WIDTH - x - 30), x, y, nameColor);
			if (member.online()) {
				String level = "Lv " + member.level();
				context.drawTextWithShadow(font, level, WIDTH - PADDING - font.getWidth(level), y, UiTheme.HERO.highlight());
				float fraction = member.maxHealth() > 0 ? member.health() / member.maxHealth() : 0.0f;
				UiDraw.bar(context, x, y + font.fontHeight + 2, WIDTH - x - PADDING, 4, fraction,
						fraction <= 0.3f ? LOW_HEALTH_COLOR : HEALTH_COLOR);
			} else {
				context.drawTextWithShadow(font, Text.translatable("hud.kingdomomnitrix.party.offline"), x, y + font.fontHeight + 1, UiTheme.TEXT_DISABLED);
			}
		}
	}
}
