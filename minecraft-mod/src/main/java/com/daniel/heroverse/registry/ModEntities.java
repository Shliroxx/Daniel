package com.daniel.heroverse.registry;

import com.daniel.heroverse.Heroverse;
import com.daniel.heroverse.entity.FusionGrenadeEntity;
import com.daniel.heroverse.entity.HeroProjectileEntity;
import com.daniel.heroverse.entity.ShadowEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	public static final EntityType<ShadowEntity> SHADOW = Registry.register(Registries.ENTITY_TYPE, Heroverse.id("shadow"),
			EntityType.Builder.<ShadowEntity>create(ShadowEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 1.95f)
					.maxTrackingRange(8)
					.build("heroverse:shadow"));

	public static final EntityType<HeroProjectileEntity> HERO_PROJECTILE = Registry.register(Registries.ENTITY_TYPE, Heroverse.id("hero_projectile"),
			EntityType.Builder.<HeroProjectileEntity>create(HeroProjectileEntity::new, SpawnGroup.MISC)
					.dimensions(0.25f, 0.25f)
					.maxTrackingRange(4)
					.trackingTickInterval(10)
					.build("heroverse:hero_projectile"));

	public static final EntityType<FusionGrenadeEntity> FUSION_GRENADE = Registry.register(Registries.ENTITY_TYPE, Heroverse.id("fusion_grenade"),
			EntityType.Builder.<FusionGrenadeEntity>create(FusionGrenadeEntity::new, SpawnGroup.MISC)
					.dimensions(0.25f, 0.25f)
					.maxTrackingRange(4)
					.trackingTickInterval(10)
					.build("heroverse:fusion_grenade"));

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(SHADOW, ShadowEntity.createShadowAttributes());
		// Herzlose erscheinen nachts in der ganzen Oberwelt; Licht- und Bodenpruefung macht ShadowEntity#canSpawn.
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, SHADOW, 40, 1, 3);
	}
}
