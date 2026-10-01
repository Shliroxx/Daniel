package com.santiq.kingdomomnitrix.client.arena;

import com.santiq.kingdomomnitrix.arena.ArenaChallenge;
import com.santiq.kingdomomnitrix.arena.ArenaManager;
import com.santiq.kingdomomnitrix.arena.ArenaRecords;
import com.santiq.kingdomomnitrix.arena.ArenaRegistry;
import com.santiq.kingdomomnitrix.enemy.RiftDefinition;
import com.santiq.kingdomomnitrix.networking.ArenaStartPayload;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.util.MaterialCost;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Arena-Terminal: alle Herausforderungen mit Wellen, Zeitlimit, Belohnung und eigener Bestzeit.
 * Gesperrte (Stufe zu niedrig) sind sichtbar, aber nicht startbar. Start nur, wenn hier gerade kein Kampf laeuft.
 */
public class ArenaScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int ROW = 36;

	private final BlockPos terminal;
	private final boolean running;

	public ArenaScreen(BlockPos terminal, boolean running) {
		super(Text.translatable("screen.kingdomomnitrix.arena"));
		this.terminal = terminal;
		this.running = running;
	}

	private List<Identifier> challenges() {
		return client == null || client.world == null ? List.of() : ArenaRegistry.sortedIds(client.world.getRegistryManager());
	}

	private int left() {
		return (width - WIDTH) / 2;
	}

	private int top() {
		return Math.max(8, (height - (challenges().size() * ROW + 60)) / 2);
	}

	@Override
	protected void init() {
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		int level = HeroDataAccess.get(client.player).level();
		List<Identifier> ids = challenges();
		for (int i = 0; i < ids.size(); i++) {
			Identifier id = ids.get(i);
			Optional<ArenaChallenge> challenge = ArenaRegistry.get(client.world.getRegistryManager(), id);
			if (challenge.isEmpty()) {
				continue;
			}
			boolean unlocked = level >= challenge.get().minLevel();
			ButtonWidget button = ButtonWidget.builder(Text.translatable(unlocked ? "screen.kingdomomnitrix.arena.start" : "screen.kingdomomnitrix.arena.locked",
							challenge.get().minLevel()),
					b -> {
						ClientPlayNetworking.send(new ArenaStartPayload(terminal, id));
						close();
					}).dimensions(left() + WIDTH - 86, top() + 22 + i * ROW + 6, 80, 20).build();
			button.active = unlocked && !running;
			button.setTooltip(Tooltip.of(rewardText(challenge.get())));
			addDrawableChild(button);
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(width / 2 - 50, top() + 30 + ids.size() * ROW, 100, 20).build());
	}

	private static Text rewardText(ArenaChallenge challenge) {
		MutableText text = Text.translatable("screen.kingdomomnitrix.arena.reward", challenge.rewards().bolts(), challenge.rewards().experience());
		if (!challenge.rewards().items().isEmpty()) {
			text.append("\n").append(MaterialCost.describe(challenge.rewards().items()));
		}
		return text;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		int left = left();
		int top = top();
		context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GOLD, Formatting.BOLD), width / 2, top + 4, 0xFFFFFFFF);
		if (running) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("arena.kingdomomnitrix.busy"), width / 2, top + 14, 0xFFFF6B6B);
		}
		ArenaRecords records = ArenaManager.records(client.player);
		List<Identifier> ids = challenges();
		for (int i = 0; i < ids.size(); i++) {
			Identifier id = ids.get(i);
			Optional<ArenaChallenge> challenge = ArenaRegistry.get(client.world.getRegistryManager(), id);
			if (challenge.isEmpty()) {
				continue;
			}
			int y = top + 22 + i * ROW;
			context.fill(left, y, left + WIDTH, y + ROW - 4, 0xC0101420);
			context.fill(left, y, left + 2, y + ROW - 4, records.cleared(id) ? 0xFFFFC94A : 0xFF6A4FB3);
			context.drawTextWithShadow(textRenderer, ArenaChallenge.name(id), left + 8, y + 4, 0xFFFFFFFF);
			int enemies = challenge.get().waves().stream().flatMap(List::stream).mapToInt(RiftDefinition.Group::count).sum();
			String info = Text.translatable("screen.kingdomomnitrix.arena.info", challenge.get().waves().size(), enemies).getString();
			if (challenge.get().timeLimit() > 0) {
				info += "  ⏱ " + challenge.get().timeLimit() / 60 + ":" + String.format("%02d", challenge.get().timeLimit() % 60);
			}
			context.drawTextWithShadow(textRenderer, info, left + 8, y + 15, 0xFFB0B8C4);
			Integer best = records.bestTicks().get(id);
			Text record = best == null ? Text.translatable("screen.kingdomomnitrix.arena.no_record")
					: Text.translatable("screen.kingdomomnitrix.arena.best", ArenaManager.formatTime(best));
			context.drawTextWithShadow(textRenderer, record, left + WIDTH - 92 - textRenderer.getWidth(record), y + 4,
					best == null ? 0xFF808890 : 0xFFFFC94A);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
