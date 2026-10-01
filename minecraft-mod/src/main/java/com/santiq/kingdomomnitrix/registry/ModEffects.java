package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.Alien;
import com.santiq.kingdomomnitrix.alien.AlienEffect;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Ein Statuseffekt pro Alien. Die Attribut-Modifikatoren tragen die koerperlichen Werte der Verwandlung.
 */
public final class ModEffects {
	private static final Map<Alien, RegistryEntry<StatusEffect>> BY_ALIEN = new EnumMap<>(Alien.class);

	private ModEffects() {
	}

	public static void register() {
		put(Alien.HEATBLAST, new AlienEffect(Alien.HEATBLAST, 0xFF6A00)
				.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, KingdomOmnitrix.id("heatblast_damage"), 2.0, Operation.ADD_VALUE));

		put(Alien.XLR8, new AlienEffect(Alien.XLR8, 0x1E90FF)
				.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, KingdomOmnitrix.id("xlr8_speed"), 1.2, Operation.ADD_MULTIPLIED_TOTAL)
				.addAttributeModifier(EntityAttributes.GENERIC_STEP_HEIGHT, KingdomOmnitrix.id("xlr8_step"), 0.65, Operation.ADD_VALUE));

		put(Alien.FOUR_ARMS, new AlienEffect(Alien.FOUR_ARMS, 0xC0392B)
				.addAttributeModifier(EntityAttributes.GENERIC_SCALE, KingdomOmnitrix.id("four_arms_scale"), 0.5, Operation.ADD_MULTIPLIED_BASE)
				.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, KingdomOmnitrix.id("four_arms_damage"), 6.0, Operation.ADD_VALUE)
				.addAttributeModifier(EntityAttributes.GENERIC_MAX_HEALTH, KingdomOmnitrix.id("four_arms_health"), 10.0, Operation.ADD_VALUE)
				.addAttributeModifier(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KingdomOmnitrix.id("four_arms_knockback"), 0.6, Operation.ADD_VALUE));

		put(Alien.DIAMONDHEAD, new AlienEffect(Alien.DIAMONDHEAD, 0x2ECC71)
				.addAttributeModifier(EntityAttributes.GENERIC_ARMOR, KingdomOmnitrix.id("diamondhead_armor"), 12.0, Operation.ADD_VALUE)
				.addAttributeModifier(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, KingdomOmnitrix.id("diamondhead_toughness"), 6.0, Operation.ADD_VALUE));

		put(Alien.GREY_MATTER, new AlienEffect(Alien.GREY_MATTER, 0x95A5A6)
				.addAttributeModifier(EntityAttributes.GENERIC_SCALE, KingdomOmnitrix.id("grey_matter_scale"), -0.7, Operation.ADD_MULTIPLIED_BASE)
				.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, KingdomOmnitrix.id("grey_matter_speed"), 0.25, Operation.ADD_MULTIPLIED_TOTAL)
				.addAttributeModifier(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, KingdomOmnitrix.id("grey_matter_fall"), 6.0, Operation.ADD_VALUE));
	}

	public static RegistryEntry<StatusEffect> forAlien(Alien alien) {
		RegistryEntry<StatusEffect> entry = BY_ALIEN.get(alien);
		if (entry == null) {
			throw new IllegalStateException("Effekt fuer " + alien + " ist nicht registriert");
		}
		return entry;
	}

	private static void put(Alien alien, StatusEffect effect) {
		BY_ALIEN.put(alien, Registry.registerReference(Registries.STATUS_EFFECT, KingdomOmnitrix.id(alien.id()), effect));
	}
}
