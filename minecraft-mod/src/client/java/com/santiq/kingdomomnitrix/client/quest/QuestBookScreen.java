package com.santiq.kingdomomnitrix.client.quest;

import com.santiq.kingdomomnitrix.networking.QuestActionPayload;
import com.santiq.kingdomomnitrix.quest.QuestDefinition;
import com.santiq.kingdomomnitrix.quest.QuestManager;
import com.santiq.kingdomomnitrix.quest.QuestManager.Status;
import com.santiq.kingdomomnitrix.quest.QuestRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Quest-Buch: links Reiter (Tracker, Auftraege, Erledigt) mit Liste, rechts der Auftraggeber mit Dialog,
 * Zielen samt Fortschritt und Belohnung. Alle Daten kommen aus der synchronisierten Quest-Registry und dem
 * Quest-Attachment; Aktionen gehen als Absicht an den Server, der alles prueft.
 */
public class QuestBookScreen extends Screen {
	private static final int BORDER = 0xFF5A3E1B;
	private static final int PAGE = 0xFFEFE4C8;
	private static final int PAGE_DARK = 0xFFE2D3AE;
	private static final int INK = 0xFF3B2A14;
	private static final int INK_SOFT = 0xFF7A6446;
	private static final int SELECTED = 0x40A0702A;
	private static final int BAR_BACK = 0xFFB9A983;
	private static final int BAR_FILL = 0xFF3FA34D;
	private static final int ROW = 22;
	private static final int TAB_HEIGHT = 16;

	private enum Tab { TRACKER, AVAILABLE, COMPLETED }

	private Tab tab = Tab.TRACKER;
	private Identifier selected;
	private int listScroll;
	private int detailScroll;
	private int detailHeight;

	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int listWidth;

	private final List<ButtonWidget> tabButtons = new ArrayList<>();
	private ButtonWidget acceptButton;
	private ButtonWidget abandonButton;
	private ButtonWidget turnInButton;

	public QuestBookScreen() {
		super(Text.translatable("screen.kingdomomnitrix.quest_book"));
	}

	public static void open(MinecraftClient client) {
		client.setScreen(new QuestBookScreen());
	}

	@Override
	protected void init() {
		panelWidth = Math.min(width - 16, 380);
		panelHeight = Math.min(height - 16, 230);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		listWidth = Math.max(110, panelWidth * 38 / 100);

		tabButtons.clear();
		Tab[] tabs = Tab.values();
		int tabWidth = (listWidth - 8) / tabs.length;
		for (int i = 0; i < tabs.length; i++) {
			Tab target = tabs[i];
			ButtonWidget button = ButtonWidget.builder(Text.translatable("quest.kingdomomnitrix.tab." + target.name().toLowerCase(Locale.ROOT)), b -> {
				tab = target;
				listScroll = 0;
				selectFirst();
			}).dimensions(left + 4 + i * tabWidth, top + 4, tabWidth - 2, TAB_HEIGHT).build();
			tabButtons.add(addDrawableChild(button));
		}
		int detailLeft = left + listWidth + 8;
		int detailWidth = panelWidth - listWidth - 12;
		int buttonY = top + panelHeight - 24;
		int buttonWidth = Math.min(100, (detailWidth - 6) / 2);
		acceptButton = addDrawableChild(ButtonWidget.builder(Text.translatable("quest.kingdomomnitrix.button.accept"),
				b -> send(QuestActionPayload.ACCEPT)).dimensions(detailLeft, buttonY, buttonWidth, 20).build());
		turnInButton = addDrawableChild(ButtonWidget.builder(Text.translatable("quest.kingdomomnitrix.button.turn_in"),
				b -> send(QuestActionPayload.TURN_IN)).dimensions(detailLeft, buttonY, buttonWidth, 20).build());
		abandonButton = addDrawableChild(ButtonWidget.builder(Text.translatable("quest.kingdomomnitrix.button.abandon"),
				b -> send(QuestActionPayload.ABANDON)).dimensions(detailLeft + detailWidth - buttonWidth, buttonY, buttonWidth, 20).build());

		if (selected == null) {
			tab = entries(Tab.TRACKER).isEmpty() ? Tab.AVAILABLE : Tab.TRACKER;
			selectFirst();
		}
		updateButtons();
	}

	// --- Daten ----------------------------------------------------------------------------------

	private DynamicRegistryManager registries() {
		return client != null && client.world != null ? client.world.getRegistryManager() : null;
	}

	private List<Identifier> entries(Tab which) {
		ClientPlayerEntity player = client != null ? client.player : null;
		DynamicRegistryManager registries = registries();
		if (player == null || registries == null) {
			return List.of();
		}
		List<Identifier> result = new ArrayList<>();
		for (Identifier id : QuestRegistry.sortedIds(registries)) {
			QuestRegistry.get(registries, id).ifPresent(quest -> {
				Status status = QuestManager.status(player, id, quest);
				boolean include = switch (which) {
					case TRACKER -> status == Status.ACTIVE || status == Status.READY;
					case AVAILABLE -> status == Status.AVAILABLE || status == Status.LOCKED;
					case COMPLETED -> QuestManager.get(player).isCompleted(id);
				};
				if (include) {
					result.add(id);
				}
			});
		}
		// Verfuegbare vor gesperrten
		if (which == Tab.AVAILABLE) {
			result.sort((a, b) -> Boolean.compare(statusOf(a) == Status.LOCKED, statusOf(b) == Status.LOCKED));
		}
		return result;
	}

	private Status statusOf(Identifier id) {
		Optional<QuestDefinition> quest = quest(id);
		return quest.isPresent() && client != null && client.player != null ? QuestManager.status(client.player, id, quest.get()) : Status.LOCKED;
	}

	private Optional<QuestDefinition> quest(Identifier id) {
		DynamicRegistryManager registries = registries();
		return id == null || registries == null ? Optional.empty() : QuestRegistry.get(registries, id);
	}

	private void selectFirst() {
		List<Identifier> list = entries(tab);
		selected = list.isEmpty() ? null : list.get(0);
		detailScroll = 0;
	}

	private void send(int action) {
		if (selected != null) {
			ClientPlayNetworking.send(new QuestActionPayload(action, selected));
		}
	}

	@Override
	public void tick() {
		super.tick();
		updateButtons();
	}

	private void updateButtons() {
		for (int i = 0; i < tabButtons.size(); i++) {
			tabButtons.get(i).active = Tab.values()[i] != tab;
		}
		Status status = selected != null ? statusOf(selected) : Status.LOCKED;
		acceptButton.visible = status == Status.AVAILABLE;
		turnInButton.visible = status == Status.READY;
		abandonButton.visible = status == Status.ACTIVE || status == Status.READY;
	}

	// --- Zeichnen -------------------------------------------------------------------------------

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		context.fill(left - 3, top - 3, left + panelWidth + 3, top + panelHeight + 3, BORDER);
		context.fill(left, top, left + listWidth + 2, top + panelHeight, PAGE_DARK);
		context.fill(left + listWidth + 4, top, left + panelWidth, top + panelHeight, PAGE);
		context.fill(left + listWidth + 2, top, left + listWidth + 4, top + panelHeight, BORDER);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		renderList(context, mouseX, mouseY);
		renderDetails(context, mouseX, mouseY);
	}

	private int listTop() {
		return top + TAB_HEIGHT + 8;
	}

	private int listBottom() {
		return top + panelHeight - 4;
	}

	private void renderList(DrawContext context, int mouseX, int mouseY) {
		List<Identifier> list = entries(tab);
		int x = left + 4;
		int width = listWidth - 6;
		if (list.isEmpty()) {
			Text empty = Text.translatable("quest.kingdomomnitrix.empty." + tab.name().toLowerCase(Locale.ROOT));
			context.drawTextWrapped(textRenderer, empty, x + 2, listTop() + 4, width - 4, INK_SOFT);
			return;
		}
		int visibleRows = Math.max(1, (listBottom() - listTop()) / ROW);
		listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, list.size() - visibleRows));
		context.enableScissor(x, listTop(), x + width, listBottom());
		for (int i = 0; i < visibleRows && i + listScroll < list.size(); i++) {
			Identifier id = list.get(i + listScroll);
			Optional<QuestDefinition> found = quest(id);
			if (found.isEmpty()) {
				continue;
			}
			QuestDefinition quest = found.get();
			int y = listTop() + i * ROW;
			if (id.equals(selected) || (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW)) {
				context.fill(x, y, x + width, y + ROW - 1, SELECTED);
			}
			context.drawItem(quest.giver().iconStack(), x + 2, y + 3);
			Status status = statusOf(id);
			int textX = x + 21;
			int textWidth = width - 23;
			int titleColor = status == Status.LOCKED ? INK_SOFT : INK;
			context.drawText(textRenderer, trim(quest.title(), textWidth), textX, y + 2, titleColor, false);
			if (tab == Tab.TRACKER) {
				drawBar(context, textX, y + 13, textWidth - 2, 4, overallProgress(id, quest), status == Status.READY ? 0xFFE0A81E : BAR_FILL);
			} else {
				context.drawText(textRenderer, trim(statusText(status, quest), textWidth), textX, y + 12, statusColor(status), false);
			}
		}
		context.disableScissor();
	}

	private void renderDetails(DrawContext context, int mouseX, int mouseY) {
		int x = left + listWidth + 10;
		int width = panelWidth - listWidth - 16;
		int clipTop = top + 4;
		int clipBottom = top + panelHeight - 28;
		Optional<QuestDefinition> found = quest(selected);
		if (found.isEmpty() || client == null || client.player == null) {
			context.drawTextWrapped(textRenderer, Text.translatable("quest.kingdomomnitrix.select"), x, clipTop + 6, width, INK_SOFT);
			return;
		}
		QuestDefinition quest = found.get();
		Status status = statusOf(selected);
		int maxScroll = Math.max(0, detailHeight - (clipBottom - clipTop));
		detailScroll = MathHelper.clamp(detailScroll, 0, maxScroll);
		context.enableScissor(x - 2, clipTop, x + width + 2, clipBottom);
		int y = clipTop + 2 - detailScroll;
		int startY = y;

		// Auftraggeber
		context.fill(x, y, x + 20, y + 20, BAR_BACK);
		context.drawItem(quest.giver().iconStack(), x + 2, y + 2);
		context.drawText(textRenderer, quest.giver().name().copy().formatted(Formatting.BOLD), x + 24, y + 1, INK, false);
		Text category = Text.translatable(quest.category().translationKey());
		if (quest.repeatable()) {
			category = category.copy().append(" · ").append(Text.translatable("quest.kingdomomnitrix.repeatable"));
		}
		context.drawText(textRenderer, category, x + 24, y + 11, INK_SOFT, false);
		y += 24;
		context.drawText(textRenderer, quest.title().copy().formatted(Formatting.UNDERLINE), x, y, INK, false);
		y += 12;

		// Dialog
		for (Text line : dialog(quest, status)) {
			List<OrderedText> wrapped = textRenderer.wrapLines(Text.literal("„").append(line).append("“"), width);
			for (OrderedText part : wrapped) {
				context.drawText(textRenderer, part, x, y, INK, false);
				y += 10;
			}
			y += 3;
		}
		if (status == Status.LOCKED) {
			y = drawLockReasons(context, quest, x, y, width);
		}

		// Ziele
		y += 3;
		context.drawText(textRenderer, Text.translatable("quest.kingdomomnitrix.objectives").formatted(Formatting.BOLD), x, y, INK, false);
		y += 11;
		boolean tracked = status == Status.ACTIVE || status == Status.READY;
		for (int i = 0; i < quest.objectives().size(); i++) {
			QuestDefinition.Objective objective = quest.objectives().get(i);
			int done = tracked || objective.type() == QuestDefinition.ObjectiveType.COLLECT
					? QuestManager.progress(client.player, selected, quest, i) : 0;
			boolean complete = done >= objective.count() || status == Status.COMPLETED;
			String counter = (status == Status.COMPLETED ? objective.count() : done) + "/" + objective.count();
			int counterWidth = textRenderer.getWidth(counter);
			Text label = Text.literal(complete ? "✔ " : "• ").append(objective.describe());
			for (OrderedText part : textRenderer.wrapLines(label, width - counterWidth - 6)) {
				context.drawText(textRenderer, part, x, y, complete ? 0xFF2E7D32 : INK, false);
				y += 10;
			}
			context.drawText(textRenderer, counter, x + width - counterWidth, y - 10, complete ? 0xFF2E7D32 : INK_SOFT, false);
			if (tracked) {
				drawBar(context, x, y, width, 3, (float) done / objective.count(), complete ? BAR_FILL : 0xFF4F8FD0);
				y += 6;
			}
		}

		// Belohnung
		y += 4;
		context.drawText(textRenderer, Text.translatable("quest.kingdomomnitrix.rewards").formatted(Formatting.BOLD), x, y, INK, false);
		y += 11;
		QuestDefinition.Rewards rewards = quest.rewards();
		List<Text> currency = new ArrayList<>();
		if (rewards.bolts() > 0) {
			currency.add(Text.translatable("quest.kingdomomnitrix.reward_bolts", rewards.bolts()).formatted(Formatting.GOLD));
		}
		if (rewards.experience() > 0) {
			currency.add(Text.translatable("quest.kingdomomnitrix.reward_xp", rewards.experience()).formatted(Formatting.DARK_AQUA));
		}
		for (Text line : currency) {
			context.drawText(textRenderer, line, x, y, INK, false);
			y += 10;
		}
		int itemX = x;
		ItemStack hovered = ItemStack.EMPTY;
		for (ItemStack item : rewards.items()) {
			if (itemX + 18 > x + width) {
				itemX = x;
				y += 19;
			}
			context.fill(itemX, y, itemX + 18, y + 18, BAR_BACK);
			context.drawItem(item, itemX + 1, y + 1);
			context.drawItemInSlot(textRenderer, item, itemX + 1, y + 1);
			if (mouseX >= itemX && mouseX < itemX + 18 && mouseY >= y && mouseY < y + 18 && mouseY >= clipTop && mouseY < clipBottom) {
				hovered = item;
			}
			itemX += 20;
		}
		if (!rewards.items().isEmpty()) {
			y += 20;
		}
		detailHeight = y - startY + 4;
		context.disableScissor();
		if (!hovered.isEmpty()) {
			context.drawItemTooltip(textRenderer, hovered, mouseX, mouseY);
		}
	}

	private List<Text> dialog(QuestDefinition quest, Status status) {
		QuestDefinition.Dialog dialog = quest.dialog();
		return switch (status) {
			case ACTIVE -> dialog.progress().isEmpty() ? dialog.offer() : dialog.progress();
			case READY, COMPLETED -> dialog.complete().isEmpty() ? dialog.offer() : dialog.complete();
			default -> dialog.offer();
		};
	}

	private int drawLockReasons(DrawContext context, QuestDefinition quest, int x, int y, int width) {
		ClientPlayerEntity player = client.player;
		for (Identifier required : quest.requires()) {
			if (!QuestManager.get(player).isCompleted(required)) {
				Text name = quest(required).map(QuestDefinition::title).orElse(Text.literal(required.toString()));
				for (OrderedText part : textRenderer.wrapLines(Text.translatable("quest.kingdomomnitrix.requires", name), width)) {
					context.drawText(textRenderer, part, x, y, 0xFFB03A2E, false);
					y += 10;
				}
			}
		}
		if (com.santiq.kingdomomnitrix.player.HeroDataAccess.get(player).level() < quest.minLevel()) {
			context.drawText(textRenderer, Text.translatable("quest.kingdomomnitrix.requires_level", quest.minLevel()), x, y, 0xFFB03A2E, false);
			y += 10;
		}
		return y;
	}

	private float overallProgress(Identifier id, QuestDefinition quest) {
		float sum = 0;
		for (int i = 0; i < quest.objectives().size(); i++) {
			sum += (float) QuestManager.progress(client.player, id, quest, i) / quest.objectives().get(i).count();
		}
		return sum / quest.objectives().size();
	}

	private static void drawBar(DrawContext context, int x, int y, int width, int height, float fraction, int color) {
		context.fill(x, y, x + width, y + height, BAR_BACK);
		int filled = Math.round(width * MathHelper.clamp(fraction, 0.0f, 1.0f));
		if (filled > 0) {
			context.fill(x, y, x + filled, y + height, color);
		}
	}

	private Text statusText(Status status, QuestDefinition quest) {
		Text text = Text.translatable("quest.kingdomomnitrix.status." + status.name().toLowerCase(Locale.ROOT));
		if (status == Status.LOCKED && quest.minLevel() > 1) {
			text = text.copy().append(" · ").append(Text.translatable("quest.kingdomomnitrix.requires_level", quest.minLevel()));
		}
		return text;
	}

	private static int statusColor(Status status) {
		return switch (status) {
			case AVAILABLE -> 0xFF2E6DA4;
			case READY -> 0xFFB7791F;
			case COMPLETED -> 0xFF2E7D32;
			case LOCKED -> 0xFF9A8A6A;
			case ACTIVE -> INK_SOFT;
		};
	}

	private OrderedText trim(Text text, int width) {
		if (textRenderer.getWidth(text) <= width) {
			return text.asOrderedText();
		}
		String ellipsis = "…";
		String plain = textRenderer.trimToWidth(text, width - textRenderer.getWidth(ellipsis)).getString();
		return Text.literal(plain + ellipsis).asOrderedText();
	}

	// --- Eingabe --------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int x = left + 4;
		int width = listWidth - 6;
		if (button == 0 && mouseX >= x && mouseX < x + width && mouseY >= listTop() && mouseY < listBottom()) {
			int index = (int) ((mouseY - listTop()) / ROW) + listScroll;
			List<Identifier> list = entries(tab);
			if (index >= 0 && index < list.size()) {
				selected = list.get(index);
				detailScroll = 0;
				updateButtons();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX < left + listWidth) {
			listScroll -= (int) Math.signum(verticalAmount);
		} else {
			detailScroll -= (int) (verticalAmount * 12);
		}
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
