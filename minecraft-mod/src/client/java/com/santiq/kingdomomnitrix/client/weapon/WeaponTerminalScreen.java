package com.santiq.kingdomomnitrix.client.weapon;

import com.santiq.kingdomomnitrix.networking.TerminalActionPayload;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.util.MaterialCost;
import com.santiq.kingdomomnitrix.weapon.ShopOffer;
import com.santiq.kingdomomnitrix.weapon.TerminalService;
import com.santiq.kingdomomnitrix.weapon.WeaponDefinition;
import com.santiq.kingdomomnitrix.weapon.WeaponItem;
import com.santiq.kingdomomnitrix.weapon.WeaponRegistry;
import com.santiq.kingdomomnitrix.weapon.WeaponState;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Waffen-Terminal mit Gadgetron-Katalog in drei Reitern: Waffen (Kaufen/Aufwerten, Munition auffuellen), Ruestung und
 * Gadgets (Angebote aus {@link ShopOffer}). Preise und Konto werden laufend neu gelesen; die eigentliche Pruefung macht
 * der Server.
 */
public class WeaponTerminalScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int WIDTH = 300;
	private static final int REFRESH_TICKS = 10;
	private static final int TAB_HEIGHT = 18;

	private enum Tab {
		WEAPONS("screen.kingdomomnitrix.terminal.tab.weapons", null),
		ARMOR("screen.kingdomomnitrix.terminal.tab.armor", ShopOffer.Category.ARMOR),
		GADGETS("screen.kingdomomnitrix.terminal.tab.gadgets", ShopOffer.Category.GADGET);

		private final String key;
		private final ShopOffer.Category category;

		Tab(String key, ShopOffer.Category category) {
			this.key = key;
			this.category = category;
		}
	}

	/** Zuletzt offener Reiter (bleibt waehrend der Sitzung erhalten) */
	private static Tab lastTab = Tab.WEAPONS;

	private final BlockPos terminal;
	private Tab tab = lastTab;
	private int refreshTimer;

	public WeaponTerminalScreen(BlockPos terminal) {
		super(Text.translatable("screen.kingdomomnitrix.terminal"));
		this.terminal = terminal;
	}

	private int rows(DynamicRegistryManager registries) {
		return tab == Tab.WEAPONS ? WeaponRegistry.sortedIds(registries).size() : ShopOffer.sortedIds(registries, tab.category).size();
	}

	private int top(DynamicRegistryManager registries) {
		// Titel, Konto, Reiter, Zeilen und Fussleiste muessen auch bei 854 x 480 (GUI-Skala 2) ganz sichtbar sein
		return Math.max(10, height / 2 - (rows(registries) * ROW_HEIGHT + 48 + TAB_HEIGHT) / 2);
	}

	@Override
	protected void init() {
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		var registries = client.world.getRegistryManager();
		int left = (width - WIDTH) / 2;
		int top = top(registries);
		int tabWidth = WIDTH / Tab.values().length;
		for (Tab candidate : Tab.values()) {
			ButtonWidget button = ButtonWidget.builder(Text.translatable(candidate.key), b -> {
				tab = candidate;
				lastTab = candidate;
				clearAndInit();
			}).dimensions(left + candidate.ordinal() * tabWidth, top + 20, tabWidth - 2, TAB_HEIGHT).build();
			button.active = candidate != tab;
			addDrawableChild(button);
		}
		int rowsTop = top + 22 + TAB_HEIGHT;
		int rows = tab == Tab.WEAPONS ? initWeapons(registries, left, rowsTop) : initOffers(registries, left, rowsTop);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(left + WIDTH - 80, footer(rowsTop, rows), 80, 20).build());
	}

	private int initWeapons(DynamicRegistryManager registries, int left, int rowsTop) {
		List<Identifier> weapons = WeaponRegistry.sortedIds(registries);
		int bolts = HeroDataAccess.get(client.player).bolts();
		boolean creative = client.player.getAbilities().creativeMode;
		for (int i = 0; i < weapons.size(); i++) {
			Identifier weaponId = weapons.get(i);
			Optional<WeaponDefinition> definition = WeaponRegistry.get(registries, weaponId);
			if (definition.isEmpty()) {
				continue;
			}
			ItemStack owned = TerminalService.findWeapon(client.player.getInventory(), definition.get().item());
			int y = rowsTop + i * ROW_HEIGHT;
			ButtonWidget button;
			if (owned.isEmpty()) {
				int price = definition.get().price();
				button = ButtonWidget.builder(price > 0
								? Text.translatable("screen.kingdomomnitrix.terminal.buy", price)
								: Text.translatable("screen.kingdomomnitrix.terminal.not_for_sale"),
						b -> send(TerminalService.Action.BUY, weaponId)).dimensions(left + WIDTH - 130, y, 130, 20).build();
				button.active = price > 0 && (creative || bolts >= price);
			} else {
				WeaponState state = WeaponItem.state(owned);
				boolean max = state.level() >= definition.get().maxLevel();
				int price = max ? 0 : definition.get().level(state.level() + 1).price();
				button = ButtonWidget.builder(max
								? Text.translatable("tooltip.kingdomomnitrix.weapon.max")
								: Text.translatable("screen.kingdomomnitrix.terminal.upgrade", state.level() + 1, price),
						b -> send(TerminalService.Action.UPGRADE, weaponId)).dimensions(left + WIDTH - 130, y, 130, 20).build();
				List<ItemStack> materials = max ? List.of() : definition.get().level(state.level() + 1).items();
				boolean hasMaterials = creative || MaterialCost.missing(client.player.getInventory(), materials).isEmpty();
				button.active = !max && hasMaterials && (creative || bolts >= price);
				if (!materials.isEmpty()) {
					button.setTooltip(Tooltip.of(Text.translatable("screen.kingdomomnitrix.terminal.materials", MaterialCost.describe(materials))
							.formatted(hasMaterials ? Formatting.GREEN : Formatting.RED)));
				}
			}
			addDrawableChild(button);
		}

		int refill = TerminalService.refillCost(client.player.getInventory(), registries);
		ButtonWidget refillButton = ButtonWidget.builder(Text.translatable("screen.kingdomomnitrix.terminal.refill", refill),
				b -> send(TerminalService.Action.REFILL, Identifier.of("minecraft", "air")))
				.dimensions(left, footer(rowsTop, weapons.size()), WIDTH - 84, 20).build();
		refillButton.active = refill > 0 && (creative || bolts > 0);
		addDrawableChild(refillButton);
		return weapons.size();
	}

	private int initOffers(DynamicRegistryManager registries, int left, int rowsTop) {
		List<Identifier> offers = ShopOffer.sortedIds(registries, tab.category);
		int bolts = HeroDataAccess.get(client.player).bolts();
		boolean creative = client.player.getAbilities().creativeMode;
		for (int i = 0; i < offers.size(); i++) {
			Identifier offerId = offers.get(i);
			Optional<ShopOffer> offer = ShopOffer.get(registries, offerId);
			if (offer.isEmpty()) {
				continue;
			}
			int price = offer.get().price();
			List<ItemStack> materials = offer.get().materials();
			boolean hasMaterials = creative || MaterialCost.missing(client.player.getInventory(), materials).isEmpty();
			ButtonWidget button = ButtonWidget.builder(Text.translatable("screen.kingdomomnitrix.terminal.buy", price),
					b -> send(TerminalService.Action.BUY_OFFER, offerId)).dimensions(left + WIDTH - 130, rowsTop + i * ROW_HEIGHT, 130, 20).build();
			button.active = hasMaterials && (creative || bolts >= price);
			if (!materials.isEmpty()) {
				button.setTooltip(Tooltip.of(Text.translatable("screen.kingdomomnitrix.terminal.materials", MaterialCost.describe(materials))
						.formatted(hasMaterials ? Formatting.GREEN : Formatting.RED)));
			}
			addDrawableChild(button);
		}
		return offers.size();
	}

	/** Fussleiste (Munition auffuellen, Fertig) unter der letzten Zeile */
	private static int footer(int rowsTop, int rows) {
		return rowsTop + rows * ROW_HEIGHT + 4;
	}

	private void send(TerminalService.Action action, Identifier id) {
		ClientPlayNetworking.send(new TerminalActionPayload(action.ordinal(), id, terminal));
		refreshTimer = 3; // Antwort des Servers abwarten, dann neu aufbauen
	}

	@Override
	public void tick() {
		super.tick();
		if (--refreshTimer <= 0) {
			refreshTimer = REFRESH_TICKS;
			clearAndInit();
		}
		if (client != null && client.player != null && client.player.squaredDistanceTo(terminal.toCenterPos()) > 64) {
			close();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		var registries = client.world.getRegistryManager();
		int left = (width - WIDTH) / 2;
		int top = top(registries);
		int rowsTop = top + 22 + TAB_HEIGHT;
		context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.AQUA, Formatting.BOLD), width / 2, top - 4, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("hud.kingdomomnitrix.bolts", String.format("%,d", HeroDataAccess.get(client.player).bolts())),
				width / 2, top + 8, 0xFFE0C060);
		if (tab == Tab.WEAPONS) {
			renderWeapons(context, registries, left, rowsTop);
		} else {
			renderOffers(context, registries, left, rowsTop);
		}
	}

	private void renderWeapons(DrawContext context, DynamicRegistryManager registries, int left, int rowsTop) {
		List<Identifier> weapons = WeaponRegistry.sortedIds(registries);
		for (int i = 0; i < weapons.size(); i++) {
			Optional<WeaponDefinition> definition = WeaponRegistry.get(registries, weapons.get(i));
			Optional<Item> item = definition.flatMap(d -> Registries.ITEM.getOrEmpty(d.item()));
			if (item.isEmpty()) {
				continue;
			}
			int y = rowsTop + i * ROW_HEIGHT;
			context.drawItem(new ItemStack(item.get()), left, y + 2);
			ItemStack owned = TerminalService.findWeapon(client.player.getInventory(), definition.get().item());
			Text status = owned.isEmpty()
					? Text.translatable("screen.kingdomomnitrix.terminal.not_owned").formatted(Formatting.GRAY)
					: Text.translatable("tooltip.kingdomomnitrix.weapon.level", WeaponItem.state(owned).level(), definition.get().maxLevel())
							.formatted(Formatting.AQUA);
			context.drawTextWithShadow(textRenderer, new ItemStack(item.get()).getName(), left + 20, y + 2, 0xFFFFFFFF);
			context.drawTextWithShadow(textRenderer, status, left + 20, y + 12, 0xFFFFFFFF);
		}
	}

	private void renderOffers(DrawContext context, DynamicRegistryManager registries, int left, int rowsTop) {
		List<Identifier> offers = ShopOffer.sortedIds(registries, tab.category);
		for (int i = 0; i < offers.size(); i++) {
			Optional<ShopOffer> offer = ShopOffer.get(registries, offers.get(i));
			if (offer.isEmpty()) {
				continue;
			}
			int y = rowsTop + i * ROW_HEIGHT;
			context.drawItem(offer.get().icon(), left, y + 2);
			context.drawItemInSlot(textRenderer, offer.get().icon(), left, y + 2);
			context.drawTextWithShadow(textRenderer, TerminalService.offerName(offer.get()), left + 20, y + 2, 0xFFFFFFFF);
			Text detail = offer.get().items().size() > 1
					? Text.translatable("screen.kingdomomnitrix.terminal.pieces", offer.get().items().size())
					: Text.translatable(offer.get().materials().isEmpty()
							? "screen.kingdomomnitrix.terminal.bolts_only"
							: "screen.kingdomomnitrix.terminal.needs_materials");
			context.drawTextWithShadow(textRenderer, detail.copy().formatted(Formatting.GRAY), left + 20, y + 12, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
