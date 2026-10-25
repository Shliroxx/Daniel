package com.santiq.kingdomomnitrix.omnitrix;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Boost-Module im Omnitrix: Einsetzen per Rechtsklick mit dem Modul (in Menschenform, freier Steckplatz), Ausbauen an der
 * Kalibrier-Werkbank (das Modul kommt zurueck). Die Attribut-Boosts (Kraft, Panzer, Tempo) gelten nur, solange der
 * Spieler verwandelt ist; sie werden alle 10 Ticks abgeglichen.
 */
public final class OmnitrixBoosts {
	private static final Identifier POWER_ID = KingdomOmnitrix.id("boost_power");
	private static final Identifier GUARD_ID = KingdomOmnitrix.id("boost_guard");
	private static final Identifier SWIFT_ID = KingdomOmnitrix.id("boost_swift");

	public enum Result { INSTALLED, TRANSFORMED, NO_SLOT }

	private OmnitrixBoosts() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 10 != 0) {
				return;
			}
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				apply(player);
			}
		});
	}

	public static Item item(OmnitrixBoost boost) {
		return switch (boost) {
			case POWER -> ModItems.BOOST_POWER;
			case GUARD -> ModItems.BOOST_GUARD;
			case SWIFT -> ModItems.BOOST_SWIFT;
			case DURATION -> ModItems.BOOST_DURATION;
			case COOLING -> ModItems.BOOST_COOLING;
			case RECHARGE -> ModItems.BOOST_RECHARGE;
		};
	}

	/** Modul einsetzen; verbraucht ein Exemplar des Stacks. */
	public static Result install(ServerPlayerEntity player, OmnitrixBoost boost, ItemStack stack) {
		if (TransformationManager.get(player).isTransformed()) {
			refuse(player, "holo.kingdomomnitrix.boost.transformed");
			return Result.TRANSFORMED;
		}
		OmnitrixCalibration calibration = OmnitrixCore.calibration(player);
		if (calibration.boosts().size() >= calibration.slots()) {
			refuse(player, "holo.kingdomomnitrix.boost.no_slot");
			return Result.NO_SLOT;
		}
		List<String> boosts = new ArrayList<>(calibration.boosts());
		boosts.add(boost.id());
		player.setAttached(OmnitrixCore.CALIBRATION, calibration.withBoosts(boosts));
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		OmnitrixOs.send(player, OmnitrixOs.Event.RECALIBRATED,
				Text.translatable("holo.kingdomomnitrix.boost.installed", Text.translatable(boost.nameKey()), boosts.size(), calibration.slots()));
		OmnitrixCore.cue(player, OmnitrixCue.CONFIRM);
		return Result.INSTALLED;
	}

	/** Modul an Platz {@code index} ausbauen und zurueckgeben. */
	public static boolean remove(ServerPlayerEntity player, int index) {
		OmnitrixCalibration calibration = OmnitrixCore.calibration(player);
		if (index < 0 || index >= calibration.boosts().size()) {
			return false;
		}
		List<String> boosts = new ArrayList<>(calibration.boosts());
		String removed = boosts.remove(index);
		player.setAttached(OmnitrixCore.CALIBRATION, calibration.withBoosts(boosts));
		OmnitrixBoost.byId(removed).ifPresent(boost -> {
			player.getInventory().offerOrDrop(new ItemStack(item(boost)));
			OmnitrixOs.send(player, OmnitrixOs.Event.RECALIBRATED,
					Text.translatable("holo.kingdomomnitrix.boost.removed", Text.translatable(boost.nameKey())));
		});
		OmnitrixCore.cue(player, OmnitrixCue.CANCEL);
		apply(player);
		return true;
	}

	private static void refuse(ServerPlayerEntity player, String key) {
		OmnitrixOs.send(player, OmnitrixOs.Event.REFUSED, Text.translatable(key));
		OmnitrixCore.cue(player, OmnitrixCue.ERROR);
	}

	/** Attribut-Boosts setzen bzw. entfernen (nur verwandelt). */
	static void apply(ServerPlayerEntity player) {
		OmnitrixCalibration calibration = OmnitrixCore.calibration(player);
		boolean active = TransformationManager.get(player).isTransformed();
		set(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, POWER_ID, active ? OmnitrixBoost.POWER_DAMAGE * calibration.count(OmnitrixBoost.POWER) : 0.0,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, EntityAttributes.GENERIC_ARMOR, GUARD_ID, active ? OmnitrixBoost.GUARD_ARMOR * calibration.count(OmnitrixBoost.GUARD) : 0.0,
				EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SWIFT_ID, active ? OmnitrixBoost.SWIFT_SPEED * calibration.count(OmnitrixBoost.SWIFT) : 0.0,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void set(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double value,
			EntityAttributeModifier.Operation operation) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		EntityAttributeModifier current = instance.getModifier(id);
		if (value <= 0.0) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current == null || current.value() != value) {
			instance.removeModifier(id);
			instance.addTemporaryModifier(new EntityAttributeModifier(id, value, operation));
		}
	}
}
