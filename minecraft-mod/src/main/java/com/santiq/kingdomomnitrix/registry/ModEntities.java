package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.EchoCloneEntity;
import com.santiq.kingdomomnitrix.enemy.AirSoldierHeartless;
import com.santiq.kingdomomnitrix.enemy.DarkballHeartless;
import com.santiq.kingdomomnitrix.enemy.DarknessRiftEntity;
import com.santiq.kingdomomnitrix.enemy.LargeBodyHeartless;
import com.santiq.kingdomomnitrix.enemy.ShadowHeartless;
import com.santiq.kingdomomnitrix.enemy.SoldierHeartless;
import com.santiq.kingdomomnitrix.npc.NpcEntity;
import com.santiq.kingdomomnitrix.boss.NefariousEntity;
import com.santiq.kingdomomnitrix.space.ShipEntity;
import com.santiq.kingdomomnitrix.weapon.FusionGrenadeEntity;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import com.santiq.kingdomomnitrix.weapon.WrenchProjectileEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.world.Heightmap;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Alle Entities. Herzlose spawnen nicht natuerlich in der Oberwelt, sondern aus Dunkelheitsrissen
 * ({@link com.santiq.kingdomomnitrix.enemy.RiftSpawner}) und in den Mod-Welten (Entscheidung SANTIQ).
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

	// Vierarms geworfener Felsbrocken (AE-Modell, GeckoLib)
	public static final EntityType<com.santiq.kingdomomnitrix.ability.BoulderEntity> BOULDER = register("boulder",
			EntityType.Builder.<com.santiq.kingdomomnitrix.ability.BoulderEntity>create(com.santiq.kingdomomnitrix.ability.BoulderEntity::new, SpawnGroup.MISC)
					.dimensions(1.25f, 1.25f).maxTrackingRange(8).trackingTickInterval(1));

	public static final EntityType<FusionGrenadeEntity> FUSION_GRENADE = register("fusion_grenade",
			EntityType.Builder.<FusionGrenadeEntity>create(FusionGrenadeEntity::new, SpawnGroup.MISC)
					.dimensions(0.25f, 0.25f).maxTrackingRange(4).trackingTickInterval(10));

	public static final EntityType<NpcEntity> NPC = register("npc",
			EntityType.Builder.<NpcEntity>create(NpcEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.95f).maxTrackingRange(10));

	public static final EntityType<ShipEntity> SHIP = register("aphelion",
			EntityType.Builder.<ShipEntity>create(ShipEntity::new, SpawnGroup.MISC).dimensions(3.0f, 1.4f).maxTrackingRange(10));

	public static final EntityType<NefariousEntity> NEFARIOUS = register("nefarious",
			EntityType.Builder.<NefariousEntity>create(NefariousEntity::new, SpawnGroup.MONSTER).dimensions(2.4f, 3.9f).makeFireImmune()
					.maxTrackingRange(12));

	// Ben-10-Schurken (GeckoLib-Modelle aus tools/generate_villain_models.py; Vilgax und Kevin rendern groesser)
	public static final EntityType<com.santiq.kingdomomnitrix.villain.VilgaxEntity> VILGAX = register("vilgax",
			EntityType.Builder.<com.santiq.kingdomomnitrix.villain.VilgaxEntity>create(com.santiq.kingdomomnitrix.villain.VilgaxEntity::new,
					SpawnGroup.MONSTER).dimensions(1.3f, 3.0f).makeFireImmune().maxTrackingRange(12));
	public static final EntityType<com.santiq.kingdomomnitrix.villain.KevinEntity> KEVIN = register("kevin",
			EntityType.Builder.<com.santiq.kingdomomnitrix.villain.KevinEntity>create(com.santiq.kingdomomnitrix.villain.KevinEntity::new,
					SpawnGroup.MONSTER).dimensions(0.7f, 2.1f).maxTrackingRange(10));
	public static final EntityType<com.santiq.kingdomomnitrix.villain.ForeverKnightEntity> FOREVER_KNIGHT = register("forever_knight",
			EntityType.Builder.<com.santiq.kingdomomnitrix.villain.ForeverKnightEntity>create(
					com.santiq.kingdomomnitrix.villain.ForeverKnightEntity::new, SpawnGroup.MONSTER).dimensions(0.7f, 2.0f).maxTrackingRange(8));
	public static final EntityType<com.santiq.kingdomomnitrix.villain.VilgaxDroneEntity> VILGAX_DRONE = register("vilgax_drone",
			EntityType.Builder.<com.santiq.kingdomomnitrix.villain.VilgaxDroneEntity>create(
					com.santiq.kingdomomnitrix.villain.VilgaxDroneEntity::new, SpawnGroup.MONSTER).dimensions(0.7f, 0.8f).maxTrackingRange(10));

	private ModEntities() {
	}

	/** Echo-Echo-Klon: halbe Spielergroesse wie AE (Groesse 0,5); wird nie gespeichert */
	/** Wildes Alien auf seiner Heimatwelt (Art per NBT/Spawner, Groesse ueber das Skalierungs-Attribut) */
	public static final EntityType<com.santiq.kingdomomnitrix.alien.WildAlienEntity> WILD_ALIEN = register("wild_alien",
			EntityType.Builder.<com.santiq.kingdomomnitrix.alien.WildAlienEntity>create(com.santiq.kingdomomnitrix.alien.WildAlienEntity::new,
					SpawnGroup.CREATURE).dimensions(0.6f, 1.8f).maxTrackingRange(10));
	public static final EntityType<EchoCloneEntity> ECHO_CLONE = register("echo_clone",
			EntityType.Builder.<EchoCloneEntity>create(EchoCloneEntity::new, SpawnGroup.MISC).dimensions(0.35f, 0.95f).maxTrackingRange(8));

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		return Registry.register(Registries.ENTITY_TYPE, KingdomOmnitrix.id(name), builder.build(KingdomOmnitrix.id(name).toString()));
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(SHADOW, ShadowHeartless.createAttributes());
		FabricDefaultAttributeRegistry.register(NPC, NpcEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ECHO_CLONE, EchoCloneEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WILD_ALIEN, com.santiq.kingdomomnitrix.alien.WildAlienEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(NEFARIOUS, NefariousEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(VILGAX, com.santiq.kingdomomnitrix.villain.VilgaxEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KEVIN, com.santiq.kingdomomnitrix.villain.KevinEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FOREVER_KNIGHT, com.santiq.kingdomomnitrix.villain.ForeverKnightEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(VILGAX_DRONE, com.santiq.kingdomomnitrix.villain.VilgaxDroneEntity.createAttributes());
		// Forever Knights: nachts in der Oberwelt in kleinen Trupps (Ebenen, Waelder, Taiga, Savanne)
		SpawnRestriction.register(FOREVER_KNIGHT, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		net.fabricmc.fabric.api.biome.v1.BiomeModifications.addSpawn(
				net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.registry.tag.BiomeTags.IS_OVERWORLD)
						.and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.registry.tag.BiomeTags.VILLAGE_PLAINS_HAS_STRUCTURE)
								.or(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.registry.tag.BiomeTags.IS_FOREST))
								.or(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.registry.tag.BiomeTags.IS_TAIGA))
								.or(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.registry.tag.BiomeTags.IS_SAVANNA))),
				SpawnGroup.MONSTER, FOREVER_KNIGHT, 4, 1, 3);
		// Natuerliche Spawns (nur in Mod-Welten wie Traverse Town, die Biome listen sie): im Dunkeln, Bodentypen auf festem Grund.
		SpawnRestriction.register(SHADOW, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		SpawnRestriction.register(SOLDIER, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		SpawnRestriction.register(LARGE_BODY, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		SpawnRestriction.register(AIR_SOLDIER, SpawnLocationTypes.UNRESTRICTED, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		SpawnRestriction.register(DARKBALL, SpawnLocationTypes.UNRESTRICTED, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		FabricDefaultAttributeRegistry.register(SOLDIER, SoldierHeartless.createAttributes());
		FabricDefaultAttributeRegistry.register(LARGE_BODY, LargeBodyHeartless.createAttributes());
		FabricDefaultAttributeRegistry.register(AIR_SOLDIER, AirSoldierHeartless.createAttributes());
		FabricDefaultAttributeRegistry.register(DARKBALL, DarkballHeartless.createAttributes());
	}
}
