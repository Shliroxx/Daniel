package com.santiq.kingdomomnitrix.client.command;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import com.santiq.kingdomomnitrix.client.hud.HudAnchor;
import com.santiq.kingdomomnitrix.client.hud.HudElement;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.client.ui.Icons;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.combat.ComboWeapon;
import com.santiq.kingdomomnitrix.item.CommandItems;
import com.santiq.kingdomomnitrix.keyblade.KeybladeItem;
import com.santiq.kingdomomnitrix.magic.MagicManager;
import com.santiq.kingdomomnitrix.magic.MagicState;
import com.santiq.kingdomomnitrix.magic.SpellDefinition;
import com.santiq.kingdomomnitrix.magic.SpellRegistry;
import com.santiq.kingdomomnitrix.networking.ComboAttackPayload;
import com.santiq.kingdomomnitrix.networking.CommandActionPayload;
import com.santiq.kingdomomnitrix.networking.RevertRequestPayload;
import com.santiq.kingdomomnitrix.networking.SelectSpellPayload;
import com.santiq.kingdomomnitrix.networking.TransformRequestPayload;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import com.santiq.kingdomomnitrix.weapon.WeaponItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Kommandomenue wie in Kingdom Hearts (Standard links): Angriff, Magie, Items, Omnitrix. Pfeiltasten ↑/↓ waehlen,
 * → oeffnet ein Untermenue bzw. fuehrt aus, ← geht zurueck. Jede Zeile in der Farbe ihres Systems.
 * Alle Aktionen gehen als Absicht an den Server, der wie bei Tasten und Rechtsklick prueft.
 */
public final class CommandMenu implements HudElement {
	public static final CommandMenu INSTANCE = new CommandMenu();
	private static final Identifier ID = KingdomOmnitrix.id("command_menu");

	private static final int WIDTH = 116;
	private static final int ROW = 18;
	private static final int PADDING = 3;
	private static final int SUB_WIDTH = 132;
	private static final int SUB_ROWS = 6;
	private static final int GAP = 3;
	/** So lange bleibt das Menue nach der letzten Bedienung sichtbar, auch ohne Ausruestung. */
	private static final int SHOW_AFTER_USE_TICKS = 200;
	private static final int FLASH_TICKS = 30;

	private enum Command {
		ATTACK("attack", UiTheme.COMBAT), MAGIC("magic", UiTheme.KEYBLADE), ITEMS("items", UiTheme.HERO), OMNITRIX("omnitrix", UiTheme.OMNITRIX);

		final String key;
		final UiTheme theme;

		Command(String key, UiTheme theme) {
			this.key = key;
			this.theme = theme;
		}
	}

	/** Eintrag eines Untermenues. */
	private record Entry(Identifier id, Text label, Text right, Identifier icon, boolean enabled, Optional<ItemStack> item) {
	}

	private int row;
	private Command submenu;
	private int subIndex;
	private int subScroll;
	private long lastUse = Long.MIN_VALUE / 2;
	private Identifier flashedSpell;
	private long flashUntil;

	private CommandMenu() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tick);
	}

	// --- HudElement -----------------------------------------------------------------------------

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public Text name() {
		return Text.translatable("hud.kingdomomnitrix.element.command_menu");
	}

	@Override
	public HudAnchor defaultAnchor() {
		return HudAnchor.TOP_LEFT;
	}

	@Override
	public int defaultX() {
		return 4;
	}

	@Override
	public int defaultY() {
		return 46;
	}

	@Override
	public boolean isActive(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || player.isSpectator()) {
			return false;
		}
		Item held = player.getMainHandStack().getItem();
		return held instanceof KeybladeItem || held instanceof ComboWeapon || held instanceof WeaponItem
				|| OmnitrixItem.hasOmnitrix(player) || TransformationManager.get(player).isTransformed()
				|| client.world.getTime() - lastUse < SHOW_AFTER_USE_TICKS;
	}

	@Override
	public int width(MinecraftClient client) {
		return submenu != null ? WIDTH + GAP + SUB_WIDTH : WIDTH;
	}

	@Override
	public int height(MinecraftClient client) {
		int menu = PADDING * 2 + Command.values().length * ROW + (recentlyUsed(client) ? client.textRenderer.fontHeight + 2 : 0);
		if (submenu == null) {
			return menu;
		}
		int sub = PADDING * 2 + Math.max(1, Math.min(SUB_ROWS, entries(client, submenu).size())) * ROW;
		return Math.max(menu, sub);
	}

	@Override
	public int previewWidth() {
		return WIDTH;
	}

	@Override
	public int previewHeight() {
		return PADDING * 2 + Command.values().length * ROW;
	}

	private boolean recentlyUsed(MinecraftClient client) {
		return client.world != null && client.world.getTime() - lastUse < SHOW_AFTER_USE_TICKS;
	}

	// --- Eingabe --------------------------------------------------------------------------------

	private void tick(MinecraftClient client) {
		boolean usable = client.player != null && client.currentScreen == null && client.world != null;
		boolean up = drain(ModKeyBindings.COMMAND_UP);
		boolean down = drain(ModKeyBindings.COMMAND_DOWN);
		boolean select = drain(ModKeyBindings.COMMAND_SELECT);
		boolean back = drain(ModKeyBindings.COMMAND_BACK);
		if (!usable) {
			return;
		}
		if (up || down || select || back) {
			lastUse = client.world.getTime();
		}
		if (submenu != null) {
			List<Entry> entries = entries(client, submenu);
			if (up) {
				move(client, entries.size(), -1);
			}
			if (down) {
				move(client, entries.size(), 1);
			}
			if (back) {
				submenu = null;
				click(client, 0.9f);
			} else if (select && !entries.isEmpty()) {
				execute(client, submenu, entries.get(Math.min(subIndex, entries.size() - 1)));
			}
			return;
		}
		int count = Command.values().length;
		if (up) {
			row = Math.floorMod(row - 1, count);
			click(client, 1.4f);
		}
		if (down) {
			row = Math.floorMod(row + 1, count);
			click(client, 1.4f);
		}
		if (select) {
			Command command = Command.values()[row];
			if (command == Command.ATTACK) {
				attack(client);
			} else {
				submenu = command;
				subIndex = 0;
				subScroll = 0;
				click(client, 1.2f);
			}
		}
	}

	private static boolean drain(net.minecraft.client.option.KeyBinding key) {
		boolean pressed = false;
		while (key.wasPressed()) {
			pressed = true;
		}
		return pressed;
	}

	private void move(MinecraftClient client, int size, int direction) {
		if (size == 0) {
			return;
		}
		subIndex = Math.floorMod(subIndex + direction, size);
		if (subIndex < subScroll) {
			subScroll = subIndex;
		} else if (subIndex >= subScroll + SUB_ROWS) {
			subScroll = subIndex - SUB_ROWS + 1;
		}
		click(client, 1.4f);
	}

	private static void click(MinecraftClient client, float pitch) {
		client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
	}

	private void attack(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player != null && player.getMainHandStack().getItem() instanceof ComboWeapon) {
			ClientPlayNetworking.send(new ComboAttackPayload(false));
		} else if (player != null) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.command_need_weapon").formatted(Formatting.GRAY), true);
		}
	}

	private void execute(MinecraftClient client, Command command, Entry entry) {
		if (!entry.enabled()) {
			click(client, 0.6f);
			return;
		}
		switch (command) {
			case MAGIC -> {
				ClientPlayNetworking.send(new SelectSpellPayload(entry.id()));
				ClientPlayNetworking.send(new CommandActionPayload(CommandActionPayload.CAST_SPELL, entry.id()));
				flashSpell(entry.id());
			}
			case ITEMS -> ClientPlayNetworking.send(new CommandActionPayload(CommandActionPayload.USE_ITEM, entry.id()));
			case OMNITRIX -> {
				if (entry.id().equals(REVERT)) {
					ClientPlayNetworking.send(RevertRequestPayload.INSTANCE);
				} else {
					ClientPlayNetworking.send(new TransformRequestPayload(entry.id()));
				}
				submenu = null;
			}
			default -> {
			}
		}
		click(client, 1.0f);
	}

	/** Zauberwahl ueber M + Mausrad: kurz in der Magie-Zeile anzeigen. */
	public void flashSpell(Identifier spell) {
		MinecraftClient client = MinecraftClient.getInstance();
		flashedSpell = spell;
		flashUntil = client.world != null ? client.world.getTime() + FLASH_TICKS : 0;
	}

	public void reset() {
		submenu = null;
		row = 0;
	}

	// --- Inhalte --------------------------------------------------------------------------------

	private static final Identifier REVERT = KingdomOmnitrix.id("revert");

	private List<Entry> entries(MinecraftClient client, Command command) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return List.of();
		}
		return switch (command) {
			case MAGIC -> spellEntries(client, player);
			case ITEMS -> itemEntries(player);
			case OMNITRIX -> alienEntries(client, player);
			default -> List.of();
		};
	}

	private static List<Entry> spellEntries(MinecraftClient client, ClientPlayerEntity player) {
		var registries = client.world.getRegistryManager();
		MagicState state = MagicManager.get(player);
		long now = client.world.getTime();
		float mp = MagicManager.currentMp(player);
		boolean keyblade = player.getMainHandStack().getItem() instanceof KeybladeItem;
		List<Entry> result = new ArrayList<>();
		for (Identifier id : SpellRegistry.sortedIds(registries)) {
			Optional<SpellDefinition> spell = SpellRegistry.get(registries, id);
			if (spell.isEmpty()) {
				continue;
			}
			int level = Math.min(state.level(id), spell.get().maxLevel());
			long cooldown = state.cooldownRemaining(id, now);
			Text right = state.isCharging(now) ? Text.translatable("hud.kingdomomnitrix.command.charging")
					: cooldown > 0 ? Text.literal(String.format("%.1fs", cooldown / 20.0f))
					: Text.literal((int) spell.get().level(level).mpCost() + " MP");
			boolean enabled = keyblade && cooldown == 0 && !state.isCharging(now) && mp >= 1.0f;
			result.add(new Entry(id, Text.translatable(SpellDefinition.translationKey(id, level)).withColor(spell.get().color()),
					right, Icons.spell(id), enabled, Optional.empty()));
		}
		return result;
	}

	private static List<Entry> itemEntries(ClientPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		Map<Item, Integer> counts = new LinkedHashMap<>();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isEmpty() && stack.isIn(CommandItems.TAG)) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		List<Entry> result = new ArrayList<>();
		for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
			ItemStack sample = CommandItems.find(inventory, entry.getKey()).orElse(new ItemStack(entry.getKey()));
			boolean ready = !player.getItemCooldownManager().isCoolingDown(entry.getKey());
			result.add(new Entry(Registries.ITEM.getId(entry.getKey()), sample.getName(), Text.literal("×" + entry.getValue()),
					null, ready, Optional.of(sample)));
		}
		return result;
	}

	private static List<Entry> alienEntries(MinecraftClient client, ClientPlayerEntity player) {
		var registries = client.world.getRegistryManager();
		TransformationState state = TransformationManager.get(player);
		long now = client.world.getTime();
		boolean omnitrix = OmnitrixItem.hasOmnitrix(player);
		List<Entry> result = new ArrayList<>();
		if (state.isTransformed()) {
			result.add(new Entry(REVERT, Text.translatable("hud.kingdomomnitrix.command.revert").formatted(Formatting.GREEN),
					Text.empty(), Icons.command("omnitrix"), true, Optional.empty()));
		}
		long recharge = state.rechargeRemaining(now);
		for (Identifier id : AlienRegistry.sortedIds(registries)) {
			if (!HeroDataAccess.get(player).hasAlien(id)) {
				continue;
			}
			Optional<AlienDefinition> alien = AlienRegistry.get(registries, id);
			if (alien.isEmpty()) {
				continue;
			}
			MutableText label = TransformationManager.alienName(id).withColor(alien.get().color());
			Text right = recharge > 0 ? Text.literal((recharge + 19) / 20 + "s").formatted(Formatting.RED)
					: Text.literal("★" + AlienMasteryManager.get(player).level(id)).formatted(Formatting.GOLD);
			boolean enabled = omnitrix && !state.isTransformed() && recharge == 0;
			result.add(new Entry(id, label, right, Icons.command("omnitrix"), enabled, Optional.empty()));
		}
		return result;
	}

	private Text rowValue(MinecraftClient client, ClientPlayerEntity player, Command command) {
		return switch (command) {
			case ATTACK -> player.getMainHandStack().isEmpty() ? Text.empty() : player.getMainHandStack().getName();
			case MAGIC -> {
				Optional<Identifier> spell = flashedSpell != null && client.world.getTime() < flashUntil
						? Optional.of(flashedSpell) : MagicManager.selectedSpell(player);
				yield spell.flatMap(id -> SpellRegistry.get(client.world.getRegistryManager(), id)
								.map(def -> (Text) Text.translatable(SpellDefinition.translationKey(id,
										Math.min(MagicManager.get(player).level(id), def.maxLevel()))).withColor(def.color())))
						.orElse(Text.empty());
			}
			case ITEMS -> {
				int total = itemEntries(player).size();
				yield total == 0 ? Text.translatable("hud.kingdomomnitrix.command.none") : Text.literal(total + "");
			}
			case OMNITRIX -> {
				TransformationState state = TransformationManager.get(player);
				yield state.activeAlien().or(state::selectedAlien).map(id -> (Text) TransformationManager.alienName(id)).orElse(Text.empty());
			}
		};
	}

	// --- Zeichnen -------------------------------------------------------------------------------

	@Override
	public void render(DrawContext context, MinecraftClient client, float tickDelta) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		TextRenderer font = client.textRenderer;
		boolean recent = recentlyUsed(client);
		int menuHeight = PADDING * 2 + Command.values().length * ROW + (recent ? font.fontHeight + 2 : 0);
		UiDraw.panel(context, 0, 0, WIDTH, menuHeight, UiTheme.HERO);
		Command[] commands = Command.values();
		boolean flash = flashedSpell != null && client.world.getTime() < flashUntil;
		for (int i = 0; i < commands.length; i++) {
			Command command = commands[i];
			int y = PADDING + i * ROW;
			boolean selected = i == row;
			if (selected || (flash && command == Command.MAGIC)) {
				context.fill(2, y, WIDTH - 2, y + ROW - 1, command.theme.accent(selected ? 0x70 : 0x40));
				if (selected) {
					context.fill(2, y, 4, y + ROW - 1, command.theme.accent());
				}
			}
			UiDraw.icon(context, Icons.command(command.key), Icons.FALLBACK, 6, y + 1, UiDraw.ICON_SIZE);
			Text label = Text.translatable("hud.kingdomomnitrix.command." + command.key);
			context.drawTextWithShadow(font, label, 25, y + 5, selected ? UiTheme.TEXT : UiTheme.TEXT_SOFT);
			Text value = rowValue(client, player, command);
			int labelEnd = 25 + font.getWidth(label) + 6;
			String shown = UiDraw.trim(font, value, WIDTH - 5 - labelEnd);
			context.drawTextWithShadow(font, Text.literal(shown).setStyle(value.getStyle()), WIDTH - 5 - font.getWidth(shown), y + 5,
					value.getStyle().getColor() != null ? 0xFFFFFFFF : command.theme.highlight());
		}
		if (recent) {
			// Hinweis verkleinert, damit er auch auf Deutsch in die Menuebreite passt
			Text hint = Text.translatable("hud.kingdomomnitrix.command.hint");
			float hintScale = Math.min(1.0f, (WIDTH - 10) / (float) Math.max(1, font.getWidth(hint)));
			context.getMatrices().push();
			context.getMatrices().translate(5, PADDING + commands.length * ROW + 2, 0);
			context.getMatrices().scale(hintScale, hintScale, 1.0f);
			context.drawTextWithShadow(font, hint, 0, 0, UiTheme.TEXT_SOFT);
			context.getMatrices().pop();
		}
		if (submenu != null) {
			renderSubmenu(context, client, font, WIDTH + GAP);
		}
	}

	private void renderSubmenu(DrawContext context, MinecraftClient client, TextRenderer font, int x) {
		List<Entry> entries = entries(client, submenu);
		int rows = Math.max(1, Math.min(SUB_ROWS, entries.size()));
		UiDraw.panel(context, x, 0, SUB_WIDTH, PADDING * 2 + rows * ROW, submenu.theme);
		if (entries.isEmpty()) {
			context.drawTextWithShadow(font, Text.translatable("hud.kingdomomnitrix.command.empty." + submenu.key), x + 6, PADDING + 5, UiTheme.TEXT_SOFT);
			return;
		}
		subIndex = Math.min(subIndex, entries.size() - 1);
		subScroll = Math.max(0, Math.min(subScroll, entries.size() - SUB_ROWS));
		for (int i = 0; i < rows && i + subScroll < entries.size(); i++) {
			int index = i + subScroll;
			Entry entry = entries.get(index);
			int y = PADDING + i * ROW;
			if (index == subIndex) {
				context.fill(x + 2, y, x + SUB_WIDTH - 2, y + ROW - 1, submenu.theme.accent(0x70));
				context.fill(x + 2, y, x + 4, y + ROW - 1, submenu.theme.accent());
			}
			if (entry.item().isPresent()) {
				context.drawItem(entry.item().get(), x + 6, y + 1);
			} else if (entry.icon() != null) {
				UiDraw.icon(context, entry.icon(), Icons.FALLBACK, x + 6, y + 1, UiDraw.ICON_SIZE);
			}
			int rightWidth = font.getWidth(entry.right());
			int color = entry.enabled() ? UiTheme.TEXT : UiTheme.TEXT_DISABLED;
			String label = UiDraw.trim(font, entry.label(), SUB_WIDTH - 30 - rightWidth - 4);
			context.drawTextWithShadow(font, Text.literal(label).setStyle(entry.enabled() ? entry.label().getStyle() : net.minecraft.text.Style.EMPTY),
					x + 25, y + 5, color);
			context.drawTextWithShadow(font, entry.right(), x + SUB_WIDTH - 5 - rightWidth, y + 5,
					entry.enabled() ? submenu.theme.highlight() : UiTheme.TEXT_DISABLED);
		}
		if (entries.size() > SUB_ROWS) {
			int trackHeight = rows * ROW;
			int thumb = Math.max(6, trackHeight * SUB_ROWS / entries.size());
			int thumbY = PADDING + (trackHeight - thumb) * subScroll / Math.max(1, entries.size() - SUB_ROWS);
			context.fill(x + SUB_WIDTH - 3, thumbY, x + SUB_WIDTH - 2, thumbY + thumb, 0xC0FFFFFF);
		}
	}
}
