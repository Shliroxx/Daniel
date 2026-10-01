package com.santiq.kingdomomnitrix.enemy;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/**
 * Herzloser "Schatten": schneller, zaeher Nahkaempfer der Dunkelheit.
 * Nutzt das Zombie-Verhalten, verbrennt aber nicht im Tageslicht und ertrinkt nicht zum Ertrunkenen.
 */
public class ShadowEntity extends ZombieEntity {
	public ShadowEntity(EntityType<? extends ZombieEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createShadowAttributes() {
		return ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0);
	}

	@Override
	protected boolean burnsInDaylight() {
		return false;
	}

	@Override
	protected boolean canConvertInWater() {
		return false;
	}

	@Override
	protected void initEquipment(Random random, LocalDifficulty localDifficulty) {
		// Herzlose tragen keine Ausruestung.
	}

	@Override
	public boolean canSpawn(WorldAccess world, SpawnReason spawnReason) {
		if (spawnReason != SpawnReason.NATURAL && spawnReason != SpawnReason.CHUNK_GENERATION) {
			return super.canSpawn(world, spawnReason);
		}
		BlockPos pos = getBlockPos();
		BlockPos below = pos.down();
		BlockState ground = world.getBlockState(below);
		if (!ground.allowsSpawning(world, below, getType())) {
			return false;
		}
		if (!(world instanceof ServerWorldAccess serverWorld)) {
			return false;
		}
		return HostileEntity.isSpawnDark(serverWorld, pos, getRandom()) && super.canSpawn(world, spawnReason);
	}
}
