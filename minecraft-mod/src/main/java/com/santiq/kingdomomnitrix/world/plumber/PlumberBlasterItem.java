package com.santiq.kingdomomnitrix.world.plumber;

import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Klempner-Blaster: Rechtsklick feuert einen gruenen Energiestrahl (sofort, Reichweite {@value #RANGE} Bloecke,
 * {@value #DAMAGE} Schaden), danach kurze Abklingzeit. Jeder Schuss kostet Haltbarkeit; Reparatur mit
 * Klempner-Panzerplatten.
 */
public class PlumberBlasterItem extends Item {
	private static final double RANGE = 32.0;
	private static final float DAMAGE = 7.0f;
	private static final int COOLDOWN = 12;
	private static final int BEAM = 0x39FF14;
	@Nullable
	private final String tooltipKey;

	public PlumberBlasterItem(Settings settings, @Nullable String tooltipKey) {
		super(settings);
		this.tooltipKey = tooltipKey;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world instanceof ServerWorld server) {
			fire(server, user);
			stack.damage(1, user, LivingEntity.getSlotForHand(hand));
		}
		user.getItemCooldownManager().set(this, COOLDOWN);
		return TypedActionResult.success(stack, world.isClient());
	}

	private static void fire(ServerWorld world, PlayerEntity user) {
		Vec3d eye = user.getEyePos();
		Vec3d look = user.getRotationVec(1.0f);
		Vec3d end = eye.add(look.multiply(RANGE));
		BlockHitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
		Vec3d stop = block.getType() == BlockHitResult.Type.MISS ? end : block.getPos();
		Box sweep = user.getBoundingBox().stretch(look.multiply(RANGE)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(user, eye, stop, sweep,
				entity -> entity.isAttackable() && !entity.isSpectator() && entity != user && entity instanceof LivingEntity, RANGE * RANGE);
		if (hit != null) {
			stop = hit.getPos();
			Entity target = hit.getEntity();
			target.damage(world.getDamageSources().playerAttack(user), DAMAGE);
			Fx.sparks(world, stop, BEAM, 0.3f, 14);
		} else if (block.getType() != BlockHitResult.Type.MISS) {
			Fx.sparks(world, stop, BEAM, 0.2f, 8);
		}
		// Strahl aus der Laufmuendung (etwas rechts unterhalb des Auges)
		Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
		Vec3d muzzle = eye.add(look.multiply(0.6)).add(right.multiply(0.25)).add(0, -0.2, 0);
		Fx.beam(world, muzzle, stop, BEAM, 0.12f, 5);
		Fx.flash(world, muzzle, BEAM, 0.4f);
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.6f, 1.9f);
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.4f, 2.0f);
	}

	@Override
	public boolean canRepair(ItemStack stack, ItemStack ingredient) {
		return ingredient.isOf(Ben10Items.PLUMBER_PLATING);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		if (tooltipKey != null) {
			tooltip.add(Text.translatable(tooltipKey).formatted(Formatting.GRAY));
		}
	}
}
