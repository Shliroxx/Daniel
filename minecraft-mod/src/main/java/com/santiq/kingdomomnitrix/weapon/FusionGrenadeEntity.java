package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * Fusionsgranate: explodiert beim Aufprall, verletzt Gegner, laesst Bloecke aber stehen.
 */
public class FusionGrenadeEntity extends ThrownItemEntity {
	public static final float EXPLOSION_POWER = 2.5f;

	public FusionGrenadeEntity(EntityType<? extends FusionGrenadeEntity> entityType, World world) {
		super(entityType, world);
	}

	public FusionGrenadeEntity(World world, LivingEntity owner) {
		super(ModEntities.FUSION_GRENADE, owner, world);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.FUSION_GRENADE;
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!getWorld().isClient()) {
			getWorld().createExplosion(this, getX(), getBodyY(0.0625), getZ(), EXPLOSION_POWER, World.ExplosionSourceType.NONE);
			discard();
		}
	}
}
