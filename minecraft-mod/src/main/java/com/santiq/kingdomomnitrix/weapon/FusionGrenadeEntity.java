package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Fusionsgranate: explodiert beim Aufprall, verletzt und schleudert Gegner, laesst Bloecke stehen. */
public class FusionGrenadeEntity extends ThrownItemEntity {
	private float power = 2.5f;

	public FusionGrenadeEntity(EntityType<? extends FusionGrenadeEntity> entityType, World world) {
		super(entityType, world);
	}

	public FusionGrenadeEntity(World world, LivingEntity owner, float power) {
		super(ModEntities.FUSION_GRENADE, owner, world);
		this.power = power;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.FUSION_GRENADE;
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!getWorld().isClient()) {
			// ExplosionSourceType.NONE: Schaden und Rueckstoss, aber keine Blockzerstoerung
			getWorld().createExplosion(this, getX(), getBodyY(0.0625), getZ(), power, World.ExplosionSourceType.NONE);
			discard();
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putFloat("Power", power);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Power")) {
			power = nbt.getFloat("Power");
		}
	}
}
