package com.santiq.kingdomomnitrix.weapon;

import net.minecraft.server.network.ServerPlayerEntity;

import com.santiq.kingdomomnitrix.party.PartyRules;

import com.santiq.kingdomomnitrix.registry.ModBlocks;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Geworfener OmniWrench: fliegt bis zur Reichweite oder bis zum ersten Block, kehrt dann zum Werfer zurueck.
 * Trifft jedes Ziel einmal pro Richtung. Am Block: Knoepfe/Hebel werden betaetigt, Bolt-Kisten zerschlagen.
 */
public class WrenchProjectileEntity extends ThrownItemEntity {
	public static final int MAX_FLIGHT_TICKS = 60;
	private static final double RETURN_SPEED = 1.4;

	private float damage = 7.0f;
	private double range = 12.0;
	private Vec3d origin = Vec3d.ZERO;
	private boolean returning;
	private final Set<Integer> hitThisPass = new HashSet<>();

	public WrenchProjectileEntity(EntityType<? extends WrenchProjectileEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
	}

	public WrenchProjectileEntity(World world, LivingEntity owner, ItemStack display, float damage, double range) {
		super(ModEntities.WRENCH_PROJECTILE, owner, world);
		setItem(display);
		setNoGravity(true);
		this.damage = damage;
		this.range = range;
		this.origin = owner.getEyePos();
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.OMNIWRENCH;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		Entity owner = getOwner();
		if (owner == null || !owner.isAlive() || age > MAX_FLIGHT_TICKS) {
			discard();
			return;
		}
		if (!returning && getPos().squaredDistanceTo(origin) > range * range) {
			turnAround();
		}
		if (returning) {
			Vec3d toOwner = owner.getEyePos().subtract(getPos());
			if (toOwner.lengthSquared() < 2.25) {
				if (owner instanceof PlayerEntity player) {
					player.getItemCooldownManager().remove(ModItems.OMNIWRENCH);
				}
				discard();
				return;
			}
			setVelocity(toOwner.normalize().multiply(RETURN_SPEED));
		}
		if (age % 2 == 0) {
			world.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
		}
	}

	private void turnAround() {
		returning = true;
		hitThisPass.clear();
		noClip = true;
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		Entity target = entityHitResult.getEntity();
		if (target == getOwner() || !hitThisPass.add(target.getId()) || getWorld().isClient()) {
			return;
		}
		if (getOwner() instanceof ServerPlayerEntity thrower && !PartyRules.canHarm(thrower, target)) {
			return;
		}
		target.damage(getDamageSources().thrown(this, getOwner()), damage);
		playSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 1.4f);
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		if (returning || !(getWorld() instanceof ServerWorld world)) {
			return;
		}
		BlockState state = world.getBlockState(hit.getBlockPos());
		Entity owner = getOwner();
		if (state.isOf(ModBlocks.BOLT_CRATE)) {
			world.breakBlock(hit.getBlockPos(), true, owner);
		} else if (owner instanceof PlayerEntity player && (state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock)) {
			state.onUse(world, player, hit);
		}
		playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.3f, 1.8f);
		turnAround();
	}

	@Override
	protected void onCollision(net.minecraft.util.hit.HitResult hitResult) {
		// Kein discard(): Der Schluessel fliegt weiter bzw. kehrt um.
		if (hitResult instanceof EntityHitResult entityHit) {
			onEntityHit(entityHit);
		} else if (hitResult instanceof BlockHitResult blockHit) {
			onBlockHit(blockHit);
		}
	}
}
