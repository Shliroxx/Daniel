package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.enemy.AirSoldierHeartless;
import com.santiq.kingdomomnitrix.enemy.DarkballHeartless;
import com.santiq.kingdomomnitrix.enemy.DarknessRiftEntity;
import com.santiq.kingdomomnitrix.enemy.LargeBodyHeartless;
import com.santiq.kingdomomnitrix.enemy.ShadowHeartless;
import com.santiq.kingdomomnitrix.enemy.SoldierHeartless;
import com.santiq.kingdomomnitrix.npc.NpcEntity;
import com.santiq.kingdomomnitrix.weapon.FusionGrenadeEntity;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import com.santiq.kingdomomnitrix.weapon.WrenchProjectileEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Alle Entities. Herzlose spawnen nicht natuerlich in der Oberwelt, sondern aus Dunkelheitsrissen
 * ({@link com.santiq.kingdomomnitrix.enemy.RiftSpawner}) und spaeter in den Mod-Dimensionen (Entscheidung SANTIQ).
 */
public final class ModEntities {
	public static final EntityType<ShadowHeartless> SHADOW = register("shadow",
			EntityType.Builder.<ShadowHeartless>create(ShadowHeartless::new, SpawnGroup.MONSTER).dimensions(0.6f, 0.9f).maxTrackingRange(8));
	public static final EntityType<SoldierHeartless> SOLDIER = register("soldier",
			EntityType.Builder.<SoldierHeartless>create(SoldierHeartless::new, SpawnGroup.MONSTER).dimensions(0.6f, 1.8f).maxTrackingRange(8));
	public static final EntityType<LargeBodyHeartless> LARGE_BODY = register("large_body",
			EntityType.Builder.<LargeBodyHeartless>create(LargeBodyHeartless::new, SpawnGroup.MONSTER).dimensions(1.4f, 2.4f).maxTrackingRange(10));
	public static final EntityType<AirSoldierHeartless> AIR_SOLDIER = register("air_soldier",
			EntityType.Builder.<AirSoldierHeartless>create(AirSoldierHeartless::new, SpawnGroup.MONSTER).dimensions(0.6f, 1.4f).maxTrackingRange(10));
	public static final EntityType<DarkballHeartless> DARKBALL = register("darkball",
			EntityType.Builder.<DarkballHeartless>create(DarkballHeartless::new, SpawnGroup.MONSTER).dimensions(0.9f, 0.9f).maxTrackingRange(10));

	public static final EntityType<DarknessRiftEntity> DARKNESS_RIFT = register("darkness_rift",
			EntityType.Builder.<DarknessRiftEntity>create(DarknessRiftEntity::new, SpawnGroup.MISC)
					.dimensions(1.0f, 2.5f).makeFireImmune().maxTrackingRange(10).trackingTickInterval(20));

	public static final EntityType<HeroProjectileEntity> HERO_PROJECTILE = register("hero_projectile",
			EntityType.Builder.<HeroProjectileEntity>create(HeroProjectileEntity::new, SpawnGroup.MISC)
					.dimensions(0.25f, 0.25f).maxTrackingRange(4).trackingTickInterval(10));

	public static final EntityType<WrenchProjectileEntity> WRENCH_PROJECTILE = register("wrench_projectile",
			EntityType.Builder.<WrenchProjectileEntity>create(WrenchProjectileEntity::new, SpawnGroup.MISC)
					.dimensions(0.5f, 0.5f).maxTrackingRange(6).trackingTickInterval(2));

	public static final EntityType<FusionGrenadeEntity> FUSION_GRENADE = register("fusion_grenade",
			EntityType.Builder.<FusionGrenadeEntity>create(FusionGrenadeEntity::new, SpawnGroup.MISC)
					.dimensions(0.25f, 0.25f).maxTrackingRange(4).trackingTickInterval(10));

	public static final EntityType<NpcEntity> NPC = register("npc",
			EntityType.Builder.<NpcEntity>create(NpcEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.95f).maxTrackingRange(10));

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		return Registry.register(Registries.ENTITY_TYPE, KingdomOmnitrix.id(name), builder.build(KingdomOmnitrix.id(name).toString()));
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(SHADOW, ShadowHeartless.createAttributes());
		FabricDefaultAttributeRegistry.register(NPC, NpcEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SOLDIER, SoldierHeartless.createAttributes());
		FabricDefaultAttributeRegistry.register(LARGE_BODY, LargeBodyHeartless.createAttributes());
		FabricDefaultAttributeRegistry.register(AIR_SOLDIER, AirSoldierHeartless.createAttributes());
		FabricDefaultAttributeRegistry.register(DARKBALL, DarkballHeartless.createAttributes());
	}
}
