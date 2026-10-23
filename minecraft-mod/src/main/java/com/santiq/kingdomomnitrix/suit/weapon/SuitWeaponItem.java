package com.santiq.kingdomomnitrix.suit.weapon;

import java.util.List;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Endgame-Waffe: Nahkampfwerte ueber Attribut-Modifikatoren, Rechtsklick loest den Spezialangriff bzw. Schuss aus
 * ({@link SuitWeapons#fire}); die Abklingzeit zeigt Minecraft als Schleier ueber dem Symbol.
 */
public class SuitWeaponItem extends Item {
	private final SuitWeapon weapon;

	public SuitWeaponItem(SuitWeapon weapon, Settings settings) {
		super(settings.attributeModifiers(AttributeModifiersComponent.builder()
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(BASE_ATTACK_DAMAGE_MODIFIER_ID, weapon.damage() - 1.0,
						EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
				.add(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID, weapon.attackSpeed(),
						EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
				.build()));
		this.weapon = weapon;
	}

	public SuitWeapon weapon() {
		return weapon;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.getItemCooldownManager().isCoolingDown(this)) {
			return TypedActionResult.fail(stack);
		}
		if (user instanceof ServerPlayerEntity player && !SuitWeapons.fire(player, weapon)) {
			return TypedActionResult.fail(stack);
		}
		user.getItemCooldownManager().set(this, weapon.cooldown());
		if (!world.isClient()) {
			stack.damage(1, user, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		return true;
	}

	@Override
	public void postDamageEntity(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.damage(1, attacker, EquipmentSlot.MAINHAND);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable(weapon.tooltipKey()).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.weapon.cooldown", weapon.cooldown() / 20.0f).formatted(Formatting.GRAY));
	}
}
