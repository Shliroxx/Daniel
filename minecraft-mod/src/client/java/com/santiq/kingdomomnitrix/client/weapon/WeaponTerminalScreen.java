package com.santiq.kingdomomnitrix.client.weapon;

import com.santiq.kingdomomnitrix.networking.TerminalActionPayload;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Waffen-Terminal: Liste aller Waffen mit Kaufen/Aufwerten und eine Schaltflaeche zum Auffuellen der Munition.
 * Preise und Konto werden laufend neu gelesen; die eigentliche Pruefung macht der Server.
 */
public class WeaponTerminalScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int WIDTH = 300;
	private static final int REFRESH_TICKS = 10;

	private final BlockPos terminal;
	private int refreshTimer;

	public WeaponTerminalScreen(BlockPos terminal) {
		super(Text.translatable("screen.kingdomomnitrix.terminal"));
		this.terminal = terminal;
	}

	@Override
	protected void init() {
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		var registries = client.world.getRegistryManager();
		List<Identifier> weapons = WeaponRegistry.sortedIds(registries);
		int left = (width - WIDTH) / 2;
		int top = Math.max(30, height / 2 - (weapons.size() * ROW_HEIGHT + 60) / 2);
		int bolts = HeroDataAccess.get(client.player).bolts();
		boolean creative = client.player.getAbilities().creativeMode;

		for (int i = 0; i < weapons.size(); i++) {
			Identifier weaponId = weapons.get(i);
			Optional<WeaponDefinition> definition = WeaponRegistry.get(registries, weaponId);
			if (definition.isEmpty()) {
				continue;
			}
			ItemStack owned = TerminalService.findWeapon(client.player.getInventory(), definition.get().item());
			int y = top + 20 + i * ROW_HEIGHT;
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
				button.active = !max && (creative || bolts >= price);
			}
			addDrawableChild(button);
		}

		int refill = TerminalService.refillCost(client.player.getInventory(), registries);
		ButtonWidget refillButton = ButtonWidget.builder(Text.translatable("screen.kingdomomnitrix.terminal.refill", refill),
				b -> send(TerminalService.Action.REFILL, Identifier.of("minecraft", "air")))
				.dimensions(left, top + 30 + weapons.size() * ROW_HEIGHT, WIDTH, 20).build();
		refillButton.active = refill > 0 && (creative || bolts > 0);
		addDrawableChild(refillButton);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(width / 2 - 50, top + 56 + weapons.size() * ROW_HEIGHT, 100, 20).build());
	}

	private void send(TerminalService.Action action, Identifier weaponId) {
		ClientPlayNetworking.send(new TerminalActionPayload(action.ordinal(), weaponId, terminal));
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
		List<Identifier> weapons = WeaponRegistry.sortedIds(registries);
		int left = (width - WIDTH) / 2;
		int top = Math.max(30, height / 2 - (weapons.size() * ROW_HEIGHT + 60) / 2);
		context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.AQUA, Formatting.BOLD), width / 2, top - 4, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("hud.kingdomomnitrix.bolts", String.format("%,d", HeroDataAccess.get(client.player).bolts())),
				width / 2, top + 8, 0xFFE0C060);
		for (int i = 0; i < weapons.size(); i++) {
			Optional<WeaponDefinition> definition = WeaponRegistry.get(registries, weapons.get(i));
			Optional<Item> item = definition.flatMap(d -> Registries.ITEM.getOrEmpty(d.item()));
			if (item.isEmpty()) {
				continue;
			}
			int y = top + 20 + i * ROW_HEIGHT;
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

	@Override
	public boolean shouldPause() {
		return false;
	}
}
