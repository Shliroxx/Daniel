package com.santiq.kingdomomnitrix.client.npc;

import com.santiq.kingdomomnitrix.networking.QuestActionPayload;
import com.santiq.kingdomomnitrix.npc.NpcDefinition;
import com.santiq.kingdomomnitrix.npc.NpcEntity;
import com.santiq.kingdomomnitrix.npc.NpcRegistry;
import com.santiq.kingdomomnitrix.quest.QuestDefinition;
import com.santiq.kingdomomnitrix.quest.QuestManager;
import com.santiq.kingdomomnitrix.quest.QuestManager.Status;
import com.santiq.kingdomomnitrix.quest.QuestRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Gespraech mit einem NPC im Stil eines Rollenspiel-Dialogs: Textkasten unten mit Namensschild, Zeilen laufen
 * Buchstabe fuer Buchstabe ein (Klick/Leertaste: vollstaendig zeigen bzw. weiter). Danach erscheinen
 * Antworten: Auftraege dieses NPCs annehmen, Stand erfragen, abgeben – oder Tschuess.
 * Quest-Aktionen gehen als Absicht an den Server; der prueft alles wie beim Quest-Buch.
 */
public class NpcDialogScreen extends Screen {
	private static final float CHARS_PER_TICK = 1.6f;
	private static final double MAX_DISTANCE = 8.0;
	private static final int BOX_HEIGHT = 64;

	private enum Mode { MENU, OFFER, BYE }

	private final int entityId;
	private final Identifier npcId;
	private final Deque<Text> lines = new ArrayDeque<>();
	private Text current;
	/** Zuletzt gesprochene Zeile; bleibt stehen, waehrend die Antworten angezeigt werden. */
	private Text lastSpoken;
	private float shown;
	private Mode mode = Mode.MENU;
	private Identifier topic;
	private Runnable afterLines;
	private int rebuildTimer;
	private final List<ButtonWidget> choices = new ArrayList<>();

	public NpcDialogScreen(int entityId, Identifier npcId) {
		super(Text.translatable("screen.kingdomomnitrix.npc_dialog"));
		this.entityId = entityId;
		this.npcId = npcId;
	}

	public static void open(MinecraftClient client, int entityId, Identifier npcId) {
		NpcDialogScreen screen = new NpcDialogScreen(entityId, npcId);
		client.setScreen(screen);
		screen.definition().ifPresent(def -> screen.say(def.greeting(), null));
	}

	private Optional<NpcDefinition> definition() {
		DynamicRegistryManager registries = registries();
		return registries == null ? Optional.empty() : NpcRegistry.get(registries, npcId);
	}

	private DynamicRegistryManager registries() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.world != null ? mc.world.getRegistryManager() : null;
	}

	// --- Ablauf ---------------------------------------------------------------------------------

	/** Spielt Zeilen ab; danach laeuft {@code then} (oder es erscheint das Antwort-Menue). */
	private void say(List<Text> text, Runnable then) {
		lines.clear();
		lines.addAll(text);
		afterLines = then;
		nextLine();
	}

	private void nextLine() {
		if (current != null) {
			lastSpoken = current;
		}
		current = lines.poll();
		shown = 0;
		if (current == null) {
			Runnable then = afterLines;
			afterLines = null;
			if (then != null) {
				then.run();
			}
		}
		rebuildChoices();
	}

	private boolean typing() {
		return current != null && shown < current.getString().length();
	}

	/** Klick oder Taste: Zeile sofort ganz zeigen, sonst zur naechsten Zeile. */
	private void advance() {
		if (current == null) {
			return;
		}
		if (typing()) {
			shown = current.getString().length();
		} else {
			if (client != null) {
				client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.3f));
			}
			nextLine();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (client == null || client.player == null || client.world == null) {
			close();
			return;
		}
		Entity entity = client.world.getEntityById(entityId);
		if (!(entity instanceof NpcEntity) || !entity.isAlive() || client.player.squaredDistanceTo(entity) > MAX_DISTANCE * MAX_DISTANCE) {
			close();
			return;
		}
		if (current != null) {
			shown = Math.min(current.getString().length(), shown + CHARS_PER_TICK);
		}
		// Nach Annehmen/Abgeben kommt der neue Quest-Stand vom Server: Menue regelmaessig auffrischen.
		if (current == null && mode == Mode.MENU && ++rebuildTimer >= 10) {
			rebuildTimer = 0;
			rebuildChoices();
		}
	}

	// --- Antworten ------------------------------------------------------------------------------

	@Override
	protected void init() {
		rebuildChoices();
	}

	private void rebuildChoices() {
		for (ButtonWidget button : choices) {
			remove(button);
		}
		choices.clear();
		if (current != null || client == null || client.player == null) {
			return;
		}
		List<ButtonWidget> next = new ArrayList<>();
		if (mode == Mode.OFFER && topic != null) {
			next.add(choice(Text.translatable("dialog.kingdomomnitrix.accept").formatted(Formatting.GREEN), () -> {
				ClientPlayNetworking.send(new QuestActionPayload(QuestActionPayload.ACCEPT, topic));
				backToMenu();
			}));
			next.add(choice(Text.translatable("dialog.kingdomomnitrix.decline"), this::backToMenu));
		} else if (mode == Mode.MENU) {
			DynamicRegistryManager registries = registries();
			if (registries != null) {
				for (Identifier questId : QuestRegistry.byNpc(registries, npcId)) {
					QuestRegistry.get(registries, questId).ifPresent(quest -> {
						ButtonWidget button = questChoice(questId, quest);
						if (button != null) {
							next.add(button);
						}
					});
				}
			}
			next.add(choice(Text.translatable("dialog.kingdomomnitrix.bye"), () -> {
				mode = Mode.BYE;
				List<Text> farewell = definition().map(NpcDefinition::farewell).orElse(List.of());
				if (farewell.isEmpty()) {
					close();
				} else {
					say(farewell, this::close);
				}
			}));
		}
		layoutChoices(next);
	}

	private ButtonWidget questChoice(Identifier questId, QuestDefinition quest) {
		Status status = QuestManager.status(client.player, questId, quest);
		return switch (status) {
			case AVAILABLE -> choice(Text.literal("! ").formatted(Formatting.YELLOW)
					.append(Text.translatable("dialog.kingdomomnitrix.offer", quest.title()).formatted(Formatting.WHITE)), () -> {
				mode = Mode.OFFER;
				topic = questId;
				say(quest.dialog().offer(), null);
			});
			case ACTIVE -> choice(Text.literal("… ").formatted(Formatting.GRAY)
					.append(Text.translatable("dialog.kingdomomnitrix.active", quest.title())), () -> {
				List<Text> text = new ArrayList<>(quest.dialog().progress().isEmpty() ? quest.dialog().offer() : quest.dialog().progress());
				text.add(Text.translatable("dialog.kingdomomnitrix.progress", progressSummary(questId, quest)));
				say(text, null);
			});
			case READY -> choice(Text.literal("? ").formatted(Formatting.GOLD)
					.append(Text.translatable("dialog.kingdomomnitrix.ready", quest.title()).formatted(Formatting.WHITE)), () -> {
				List<Text> text = quest.dialog().complete().isEmpty() ? List.of(quest.title()) : quest.dialog().complete();
				// Erst sprechen lassen, dann abgeben – die Belohnung kommt mit dem letzten Satz.
				say(text, () -> ClientPlayNetworking.send(new QuestActionPayload(QuestActionPayload.TURN_IN, questId)));
			});
			default -> null;
		};
	}

	private Text progressSummary(Identifier questId, QuestDefinition quest) {
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < quest.objectives().size(); i++) {
			QuestDefinition.Objective objective = quest.objectives().get(i);
			parts.add(objective.targetName().getString() + " " + QuestManager.progress(client.player, questId, quest, i) + "/" + objective.count());
		}
		return Text.literal(String.join(", ", parts));
	}

	private void backToMenu() {
		mode = Mode.MENU;
		topic = null;
		rebuildTimer = 0;
		rebuildChoices();
	}

	private ButtonWidget choice(Text label, Runnable action) {
		return ButtonWidget.builder(label, button -> action.run()).build();
	}

	/** Antworten rechts ueber dem Textkasten, von unten nach oben gestapelt. */
	private void layoutChoices(List<ButtonWidget> buttons) {
		int buttonWidth = Math.min(220, width / 2);
		int x = width - buttonWidth - 12;
		int y = height - BOX_HEIGHT - 12 - buttons.size() * 22;
		for (ButtonWidget button : buttons) {
			button.setDimensionsAndPosition(buttonWidth, 20, x, Math.max(4, y));
			y += 22;
			choices.add(addDrawableChild(button));
		}
	}

	// --- Zeichnen und Eingabe -------------------------------------------------------------------

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Welt bleibt sichtbar: kein Weichzeichner, nur der Textkasten.
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int left = 12;
		int right = width - 12;
		int top = height - BOX_HEIGHT - 8;
		int bottom = height - 8;
		context.fill(left, top, right, bottom, 0xF0101420);
		context.fill(left, top, right, top + 1, 0xFF4FC3FF);
		// Namensschild
		Text name = NpcDefinition.name(npcId);
		int plateWidth = textRenderer.getWidth(name) + 12;
		context.fill(left + 8, top - 13, left + 8 + plateWidth, top, 0xE02A3F8F);
		context.drawTextWithShadow(textRenderer, name, left + 14, top - 10, 0xFFFFFFFF);

		if (current != null) {
			String full = current.getString();
			String visible = full.substring(0, Math.min(full.length(), (int) shown));
			int y = top + 8;
			for (OrderedText line : textRenderer.wrapLines(Text.literal(visible), right - left - 20)) {
				context.drawTextWithShadow(textRenderer, line, left + 10, y, 0xFFFFFFFF);
				y += 11;
			}
			if (!typing() && (client == null || client.player == null || client.player.age / 10 % 2 == 0)) {
				Text hint = Text.translatable("dialog.kingdomomnitrix.continue");
				context.drawTextWithShadow(textRenderer, hint, right - 8 - textRenderer.getWidth(hint), bottom - 12, 0xFF9FB4CC);
			}
		} else if (mode == Mode.MENU && choices.size() <= 1) {
			context.drawTextWithShadow(textRenderer, Text.translatable("dialog.kingdomomnitrix.nothing"), left + 10, top + 8, 0xFFB0B8C4);
		} else if (lastSpoken != null) {
			int y = top + 8;
			for (OrderedText line : textRenderer.wrapLines(lastSpoken, right - left - 20)) {
				context.drawTextWithShadow(textRenderer, line, left + 10, y, 0xFFB0B8C4);
				y += 11;
			}
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (current != null) {
			advance();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (current != null && (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			advance();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
