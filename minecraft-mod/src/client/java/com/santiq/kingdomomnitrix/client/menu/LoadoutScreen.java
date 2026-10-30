package com.santiq.kingdomomnitrix.client.menu;

import com.santiq.kingdomomnitrix.alien.AbilitySlot;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienLoadouts;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.LoadoutManager;
import com.santiq.kingdomomnitrix.alien.PassiveSlot;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.client.ui.HudGfx;
import com.santiq.kingdomomnitrix.client.ui.Icons;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.networking.SetLoadoutPayload;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.progression.AlienMastery;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Loadout und Kraefte eines Aliens: oben die sechs Tasten (R V B, Shift + R V B), darunter der ganze Pool der
 * Tasten-Faehigkeiten, rechts Kombos (entstehen aus der Bewegung) und passive Kraefte. Ausruesten: Faehigkeit im Pool
 * anklicken, dann eine Taste (oder umgekehrt); liegt sie schon auf einer anderen Taste, tauschen beide. Unten steht die
 * Beschreibung der Kraft unter dem Mauszeiger.
 */
public class LoadoutScreen extends Screen {
	private static final int SLOT = 30;
	private static final int TILE = 24;
	private static final int SMALL = 18;
	private static final int GOLD = 0xFFFFC94A;

	private final Screen parent;
	private final Identifier alienId;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	/** gewaehlte Pool-Faehigkeit (Index) bzw. Taste, die auf ihr Gegenstueck wartet */
	private int pickedPool = -1;
	private int pickedKey = -1;
	private Text notice;
	private long noticeUntil;

	public LoadoutScreen(Screen parent, Identifier alienId) {
		super(Text.translatable("screen.kingdomomnitrix.loadout.title"));
		this.parent = parent;
		this.alienId = alienId;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(width - 8, 400);
		panelHeight = Math.min(height - 8, 250);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
	}

	private Optional<AlienDefinition> alien() {
		return client != null && client.world != null ? AlienRegistry.get(client.world.getRegistryManager(), alienId) : Optional.empty();
	}

	private List<Integer> loadout(AlienDefinition alien) {
		return client != null && client.player != null ? LoadoutManager.get(client.player).editable(alienId, alien) : alien.defaultLoadout();
	}

	private int mastery() {
		return client != null && client.player != null ? AlienMasteryManager.get(client.player).level(alienId) : 1;
	}

	private boolean open(AbilitySlot slot) {
		return slot.unlocked(mastery()) || client != null && client.player != null && OmnitrixCore.state(client.player).masterControl();
	}

	// --- Layout ----------------------------------------------------------------------------------

	private int keyX(int i) {
		return left + 10 + (i % 3) * (SLOT + 8);
	}

	private int keyY(int i) {
		return top + 34 + (i / 3) * (SLOT + 10);
	}

	private int poolX(int n) {
		return left + 10 + (n % 6) * (TILE + 4);
	}

	private int poolY(int n) {
		return top + 132 + (n / 6) * (TILE + 4);
	}

	private int sideX() {
		return left + 186;
	}

	// --- Zeichnen --------------------------------------------------------------------------------

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		Optional<AlienDefinition> found = alien();
		if (found.isEmpty() || client == null || client.player == null) {
			return;
		}
		AlienDefinition alien = found.get();
		int rgb = alien.color() & 0xFFFFFF;
		UiDraw.panel(context, left, top, panelWidth, panelHeight, UiTheme.OMNITRIX);
		context.fillGradient(left + 3, top + 1, left + panelWidth - 3, top + 2, 0xFF000000 | rgb, 0xFF000000 | rgb);
		Text name = TransformationManager.alienName(alienId).withColor(rgb).formatted(Formatting.BOLD);
		context.drawTextWithShadow(textRenderer, name, left + 10, top + 7, UiTheme.TEXT);
		Text sub = title.copy().append(Text.literal("  ★" + mastery()).formatted(Formatting.GOLD));
		context.drawTextWithShadow(textRenderer, sub, left + 14 + textRenderer.getWidth(name), top + 7, UiTheme.TEXT_SOFT);
		Text back = Text.translatable("screen.kingdomomnitrix.loadout.back");
		int bw = textRenderer.getWidth(back) + 10;
		boolean overBack = inside(mouseX, mouseY, left + panelWidth - bw - 8, top + 5, bw, 12);
		HudGfx.rounded(context, left + panelWidth - bw - 8, top + 5, bw, 12, 2, overBack ? 0xC0404850 : 0xA0202428);
		context.drawTextWithShadow(textRenderer, back, left + panelWidth - bw - 3, top + 7, UiTheme.TEXT);

		Object hovered = null;
		List<Integer> loadout = loadout(alien);

		// Tasten
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.loadout.keys"), left + 10, top + 22, UiTheme.OMNITRIX.accent());
		for (int i = 0; i < loadout.size(); i++) {
			int x = keyX(i);
			int y = keyY(i);
			AbilitySlot slot = alien.abilities().get(loadout.get(i));
			boolean over = inside(mouseX, mouseY, x, y, SLOT, SLOT);
			if (over) {
				hovered = slot;
			}
			boolean picked = pickedKey == i;
			HudGfx.roundedGradient(context, x, y, SLOT, SLOT, 3, 0xF0000000 | HudGfx.mul(rgb, picked ? 0.7f : 0.4f), 0xF0080A0C);
			UiDraw.art(context, Icons.alienAbilityArt(slot.type()), Icons.alienAbility(slot.type()), x + 4, y + 3, 22,
					open(slot) ? 0xFFFFFF : 0x50545A, 1.0f);
			int border = picked ? 0xFFFFFFFF : slot.role() == AbilitySlot.Role.ULTIMATE ? GOLD : over ? 0xFFE0E0E0 : 0xFF000000 | HudGfx.mix(rgb, 0xFFFFFF, 0.2f);
			HudGfx.roundedBorder(context, x, y, SLOT, SLOT, border);
			if (picked || pickedPool >= 0) {
				HudGfx.glow(context, x, y, SLOT, SLOT, picked ? 0xFFFFFF : rgb, 0.5f, 2);
			}
			Text base = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.ABILITIES[i % 3]).getLocalizedText();
			Text key = i < 3 ? base : Text.literal("⇧").append(base);
			int kw = Math.max(11, textRenderer.getWidth(key) + 6);
			HudGfx.rounded(context, x + SLOT / 2 - kw / 2, y + SLOT - 4, kw, 10, 2, 0xF00C1014);
			context.drawTextWithShadow(textRenderer, key, x + SLOT / 2 - textRenderer.getWidth(key) / 2, y + SLOT - 3, UiTheme.TEXT);
		}

		// Pool
		List<Integer> pool = alien.keyIndices();
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.loadout.pool", pool.size()), left + 10, top + 120,
				UiTheme.OMNITRIX.accent());
		for (int n = 0; n < pool.size(); n++) {
			int index = pool.get(n);
			AbilitySlot slot = alien.abilities().get(index);
			int x = poolX(n);
			int y = poolY(n);
			boolean over = inside(mouseX, mouseY, x, y, TILE, TILE);
			if (over) {
				hovered = slot;
			}
			boolean equipped = loadout.contains(index);
			boolean picked = pickedPool == index;
			HudGfx.roundedGradient(context, x, y, TILE, TILE, 2, equipped ? 0xF0000000 | HudGfx.mul(rgb, 0.35f) : 0xF0181C20, 0xF0080A0C);
			UiDraw.art(context, Icons.alienAbilityArt(slot.type()), Icons.alienAbility(slot.type()), x + 3, y + 3, TILE - 6,
					open(slot) ? (equipped ? 0xFFFFFF : 0xC8CCD0) : 0x4A4E54, 1.0f);
			int border = picked ? 0xFFFFFFFF : slot.role() == AbilitySlot.Role.ULTIMATE ? GOLD : equipped ? 0xFF000000 | rgb : over ? 0xFFB0B0B0 : 0xFF3A4048;
			HudGfx.roundedBorder(context, x, y, TILE, TILE, border);
			if (!open(slot)) {
				context.getMatrices().push();
				context.getMatrices().translate(0, 0, 200);
				String need = "★" + slot.unlockLevel();
				context.drawTextWithShadow(textRenderer, need, x + TILE / 2 - textRenderer.getWidth(need) / 2, y + 8, 0xFFB08A2E);
				context.getMatrices().pop();
			} else if (equipped) {
				context.getMatrices().push();
				context.getMatrices().translate(0, 0, 200);
				context.drawTextWithShadow(textRenderer, "✔", x + TILE - 7, y + 1, 0xFF7CFF7C);
				context.getMatrices().pop();
			}
		}

		// Kombos
		int sx = sideX();
		int sy = top + 22;
		int sideWidth = left + panelWidth - 10 - sx;
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.loadout.combos"), sx, sy, UiTheme.OMNITRIX.accent());
		sy += 11;
		for (int index : alien.comboIndices()) {
			AbilitySlot slot = alien.abilities().get(index);
			boolean over = inside(mouseX, mouseY, sx, sy, sideWidth, SMALL);
			if (over) {
				hovered = slot;
			}
			row(context, sx, sy, sideWidth, Icons.alienAbilityArt(slot.type()), Icons.alienAbility(slot.type()),
					Text.translatable(slot.translationKey()), Text.translatable(slot.trigger().translationKey()), open(slot), slot.unlockLevel(), over, rgb);
			sy += SMALL + 2;
		}
		if (alien.comboIndices().isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.loadout.none"), sx + 4, sy, UiTheme.TEXT_DISABLED);
			sy += 11;
		}
		sy += 4;
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.loadout.passives"), sx, sy, UiTheme.OMNITRIX.accent());
		sy += 11;
		for (PassiveSlot passive : alien.traits().passives()) {
			boolean over = inside(mouseX, mouseY, sx, sy, sideWidth, SMALL);
			if (over) {
				hovered = passive;
			}
			boolean on = passive.unlocked(mastery()) || OmnitrixCore.state(client.player).masterControl();
			row(context, sx, sy, sideWidth, Icons.alienAbilityArt(passive.type()), Icons.alienAbility(passive.type()),
					Text.translatable(passive.translationKey()), Text.translatable("screen.kingdomomnitrix.loadout.passive"), on, passive.unlockLevel(), over, rgb);
			sy += SMALL + 2;
		}
		if (alien.traits().passives().isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.loadout.none"), sx + 4, sy, UiTheme.TEXT_DISABLED);
		}

		// Beschreibung
		describe(context, alien, hovered, mouseX, mouseY);
		if (notice != null && client.world.getTime() < noticeUntil) {
			context.drawCenteredTextWithShadow(textRenderer, notice, left + panelWidth / 2, top + panelHeight + 2 > height - 10 ? top + panelHeight - 10 : top + panelHeight + 2, 0xFFFF6060);
		}
	}

	private void row(DrawContext context, int x, int y, int w, Identifier art, Identifier fallback, Text name, Text tag, boolean on,
			int unlock, boolean over, int rgb) {
		HudGfx.rounded(context, x, y, w, SMALL, 2, over ? 0x60FFFFFF & 0x50FFFFFF : 0x60000000);
		HudGfx.roundedBorder(context, x, y, SMALL, SMALL, on ? 0xFF000000 | HudGfx.mix(rgb, 0xFFFFFF, 0.2f) : 0xFF3A4048);
		UiDraw.art(context, art, fallback, x + 2, y + 2, SMALL - 4, on ? 0xFFFFFF : 0x4A4E54, 1.0f);
		int textX = x + SMALL + 4;
		String right = on ? "" : "★" + unlock;
		int room = w - SMALL - 8 - textRenderer.getWidth(right);
		context.drawTextWithShadow(textRenderer, UiDraw.trim(textRenderer, name, room), textX, y + 1, on ? UiTheme.TEXT : UiTheme.TEXT_DISABLED);
		context.getMatrices().push();
		context.getMatrices().translate(textX, y + 10, 0);
		context.getMatrices().scale(0.75f, 0.75f, 1.0f);
		context.drawTextWithShadow(textRenderer, UiDraw.trim(textRenderer, tag, (int) (room / 0.75f)), 0, 0, on ? GOLD : 0xFF6E767E);
		context.getMatrices().pop();
		if (!on) {
			context.drawTextWithShadow(textRenderer, right, x + w - 3 - textRenderer.getWidth(right), y + 5, 0xFFB08A2E);
		}
	}

	/**
	 * Beschreibung: ueber einer Kraft als Tooltip am Mauszeiger (Name, Werte, Text), sonst unten links der Bedienhinweis.
	 */
	private void describe(DrawContext context, AlienDefinition alien, Object hovered, int mouseX, int mouseY) {
		if (hovered == null) {
			int x = left + 10;
			int y = top + 132 + 2 * (TILE + 4) + 2;
			int w = sideX() - 10 - x;
			int h = top + panelHeight - 8 - y;
			if (h < 14) {
				return;
			}
			HudGfx.rounded(context, x, y, w, h, 3, 0x60000000);
			int ly = y + 4;
			for (OrderedText line : textRenderer.wrapLines(Text.translatable("screen.kingdomomnitrix.loadout.help_short"), w - 8)) {
				if (ly + 9 > y + h) {
					break;
				}
				context.drawTextWithShadow(textRenderer, line, x + 4, ly, UiTheme.TEXT_SOFT);
				ly += 10;
			}
			return;
		}
		List<OrderedText> lines = new ArrayList<>();
		String descKey;
		if (hovered instanceof AbilitySlot slot) {
			lines.add(Text.translatable(slot.translationKey()).formatted(Formatting.BOLD)
					.withColor(slot.role() == AbilitySlot.Role.ULTIMATE ? GOLD : 0xFFFFFFFF).asOrderedText());
			int level = mastery();
			long cooldown = client != null && client.player != null
					? Math.round(slot.cooldown() * (1.0f - AlienMasteryManager.get(client.player).cooldownReduction(alienId))) : slot.cooldown();
			lines.add(Text.translatable("screen.kingdomomnitrix.loadout.stats", Math.round(AlienMastery.energyCost(slot.energy(), level)),
					String.format(java.util.Locale.ROOT, "%.1f", cooldown / 20.0f), Text.translatable(roleKey(slot.role())),
					slot.isKey() ? Text.translatable("trigger.kingdomomnitrix.key") : Text.translatable(slot.trigger().translationKey()))
					.withColor(GOLD).asOrderedText());
			if (!open(slot)) {
				lines.add(Text.translatable("screen.kingdomomnitrix.aliens.unlock_at", slot.unlockLevel()).withColor(0xFFB08A2E).asOrderedText());
			}
			descKey = slot.translationKey() + ".desc";
		} else if (hovered instanceof PassiveSlot passive) {
			lines.add(Text.translatable(passive.translationKey()).formatted(Formatting.BOLD).asOrderedText());
			lines.add(Text.translatable("screen.kingdomomnitrix.loadout.passive").withColor(GOLD).asOrderedText());
			descKey = passive.translationKey() + ".desc";
		} else {
			return;
		}
		if (I18n.hasTranslation(descKey)) {
			lines.addAll(textRenderer.wrapLines(Text.translatable(descKey).withColor(0xFFC8CCD2), 210));
		}
		context.drawOrderedTooltip(textRenderer, lines, mouseX, mouseY);
	}

	private static String roleKey(AbilitySlot.Role role) {
		return switch (role) {
			case BASIC -> "role.kingdomomnitrix.basic";
			case BASIC_2 -> "role.kingdomomnitrix.basic_2";
			case MOVEMENT -> "role.kingdomomnitrix.movement";
			case SPECIAL -> "role.kingdomomnitrix.special";
			case UTILITY -> "role.kingdomomnitrix.utility";
			case ULTIMATE -> "role.kingdomomnitrix.ultimate";
		};
	}

	// --- Eingabe ---------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		Optional<AlienDefinition> found = alien();
		if (found.isEmpty() || client == null) {
			return false;
		}
		AlienDefinition alien = found.get();
		Text back = Text.translatable("screen.kingdomomnitrix.loadout.back");
		int bw = textRenderer.getWidth(back) + 10;
		if (inside(mouseX, mouseY, left + panelWidth - bw - 8, top + 5, bw, 12)) {
			close();
			return true;
		}
		List<Integer> loadout = loadout(alien);
		for (int i = 0; i < loadout.size(); i++) {
			if (inside(mouseX, mouseY, keyX(i), keyY(i), SLOT, SLOT)) {
				if (button == 1) {
					reset(alien);
				} else if (pickedPool >= 0) {
					assign(alien, loadout, i, pickedPool);
				} else {
					pickedKey = pickedKey == i ? -1 : i;
				}
				click();
				return true;
			}
		}
		List<Integer> pool = alien.keyIndices();
		for (int n = 0; n < pool.size(); n++) {
			if (inside(mouseX, mouseY, poolX(n), poolY(n), TILE, TILE)) {
				int index = pool.get(n);
				if (pickedKey >= 0) {
					assign(alien, loadout, pickedKey, index);
				} else {
					pickedPool = pickedPool == index ? -1 : index;
				}
				click();
				return true;
			}
		}
		pickedPool = -1;
		pickedKey = -1;
		return false;
	}

	/** Faehigkeit {@code index} auf Taste {@code key}; lag sie schon auf einer anderen Taste, tauschen beide. */
	private void assign(AlienDefinition alien, List<Integer> loadout, int key, int index) {
		List<Integer> next = new ArrayList<>(loadout);
		int already = next.indexOf(index);
		if (already >= 0) {
			next.set(already, next.get(key));
		}
		next.set(key, index);
		pickedPool = -1;
		pickedKey = -1;
		if (!AlienLoadouts.valid(alien, next)) {
			notice = Text.translatable("screen.kingdomomnitrix.loadout.one_ultimate");
			noticeUntil = client.world.getTime() + 60;
			return;
		}
		ClientPlayNetworking.send(new SetLoadoutPayload(alienId, next));
	}

	private void reset(AlienDefinition alien) {
		pickedPool = -1;
		pickedKey = -1;
		ClientPlayNetworking.send(new SetLoadoutPayload(alienId, alien.defaultLoadout()));
	}

	private void click() {
		if (client != null) {
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.3f));
		}
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
