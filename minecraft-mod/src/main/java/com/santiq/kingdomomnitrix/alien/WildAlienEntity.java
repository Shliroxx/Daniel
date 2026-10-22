package com.santiq.kingdomomnitrix.alien;

import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Wildes Alien auf seiner Heimatwelt (Sonorosianer auf Sonorosia, Petrosapiens auf Petropia …): dasselbe Modell wie die
 * Omnitrix-Form, Werte und Verhalten aus {@link AlienDefinition.Species}. Nur diese Wesen liefern die DNA ihrer Art
 * ({@link DnaScan}).
 *
 * <p>Verhalten je Art: scheu (flieht), neutral (wehrt sich, die Gruppe hilft), feindlich (greift Spieler an), Rudel
 * (greift an und ruft die Gruppe). Treffer tragen die Kraft der Art (Feuer, Frost, Gift, Schall …). Auf „Friedlich“
 * greift keine Art von sich aus an.</p>
 */
public class WildAlienEntity extends PathAwareEntity implements GeoEntity {
	private static final TrackedData<String> ALIEN = DataTracker.registerData(WildAlienEntity.class, TrackedDataHandlerRegistry.STRING);
	/** Erscheinungsbild (Uniform-Index, der Client waehlt aus den vorhandenen Uniformen des Modells) */
	private static final TrackedData<Integer> VARIANT = DataTracker.registerData(WildAlienEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** zuletzt angewendete Art (Attribute nur bei Wechsel neu setzen) */
	private String applied = "";

	public WildAlienEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ALIEN, "");
		builder.add(VARIANT, 0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(1, new FleeEntityGoal<>(this, PlayerEntity.class, 10.0f, 1.0, 1.35) {
			@Override
			public boolean canStart() {
				return behavior() == AlienDefinition.Behavior.SHY && super.canStart();
			}
		});
		goalSelector.add(2, new MeleeAttackGoal(this, 1.2, true) {
			@Override
			public boolean canStart() {
				return behavior() != AlienDefinition.Behavior.SHY && super.canStart();
			}
		});
		goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this) {
			@Override
			public boolean canStart() {
				return behavior() != AlienDefinition.Behavior.SHY && super.canStart();
			}
		}.setGroupRevenge());
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true, player -> !((PlayerEntity) player).isCreative()) {
			@Override
			public boolean canStart() {
				AlienDefinition.Behavior b = behavior();
				return (b == AlienDefinition.Behavior.HOSTILE || b == AlienDefinition.Behavior.PACK)
						&& getWorld().getDifficulty() != Difficulty.PEACEFUL && super.canStart();
			}
		});
	}

	// --- Art --------------------------------------------------------------------------------------------------------

	/** Art festlegen (beim Spawnen); setzt Werte und Groesse. */
	public void setAlien(Identifier alien) {
		dataTracker.set(ALIEN, alien.toString());
		dataTracker.set(VARIANT, getRandom().nextInt(3));
		applySpecies();
		setHealth(getMaxHealth());
	}

	/** Alien-ID der Art (leer, solange keine gesetzt ist). */
	public Optional<Identifier> alien() {
		String raw = dataTracker.get(ALIEN);
		return raw.isEmpty() ? Optional.empty() : Optional.ofNullable(Identifier.tryParse(raw));
	}

	public int variant() {
		return dataTracker.get(VARIANT);
	}

	public Optional<AlienDefinition> definition() {
		return alien().flatMap(id -> AlienRegistry.get(getWorld().getRegistryManager(), id));
	}

	public Optional<AlienDefinition.Species> species() {
		return definition().flatMap(d -> d.dna().species());
	}

	private AlienDefinition.Behavior behavior() {
		return species().map(AlienDefinition.Species::behavior).orElse(AlienDefinition.Behavior.SHY);
	}

	private AlienDefinition.HitEffect effect() {
		return species().map(AlienDefinition.Species::effect).orElse(AlienDefinition.HitEffect.NONE);
	}

	private void applySpecies() {
		String id = dataTracker.get(ALIEN);
		if (id.equals(applied)) {
			return;
		}
		Optional<AlienDefinition> definition = definition();
		if (definition.isEmpty()) {
			return;
		}
		applied = id;
		definition.get().dna().species().ifPresent(species -> {
			base(EntityAttributes.GENERIC_MAX_HEALTH, species.health());
			base(EntityAttributes.GENERIC_ATTACK_DAMAGE, species.damage());
			base(EntityAttributes.GENERIC_MOVEMENT_SPEED, species.speed());
		});
		base(EntityAttributes.GENERIC_SCALE, definition.get().scale());
	}

	private void base(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute, double value) {
		EntityAttributeInstance instance = getAttributeInstance(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (ALIEN.equals(data)) {
			applied = "";
			applySpecies();
			calculateDimensions();
		}
	}

	@Override
	public void tick() {
		super.tick();
		applySpecies();
		if (species().map(AlienDefinition.Species::aquatic).orElse(false)) {
			setAir(getMaxAir()); // Wasserwesen ertrinken nicht
		}
		if (getWorld().isClient() && age % 12 == 0) {
			ParticleEffect ambient = switch (effect()) {
				case FIRE -> ParticleTypes.FLAME;
				case FREEZE -> ParticleTypes.SNOWFLAKE;
				case POISON -> ParticleTypes.SPORE_BLOSSOM_AIR;
				case SHOCK -> ParticleTypes.ELECTRIC_SPARK;
				case CRYSTAL -> ParticleTypes.END_ROD;
				case DRAIN -> ParticleTypes.SOUL;
				default -> null;
			};
			if (ambient != null) {
				getWorld().addParticle(ambient, getParticleX(0.6), getRandomBodyY(), getParticleZ(0.6), 0.0, 0.02, 0.0);
			}
		}
	}

	// --- Kampf ------------------------------------------------------------------------------------------------------

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (!hit || !(target instanceof LivingEntity living) || !(getWorld() instanceof ServerWorld world)) {
			return hit;
		}
		Vec3d at = living.getPos().add(0, living.getHeight() * 0.5, 0);
		switch (effect()) {
			case FIRE -> living.setOnFireFor(4.0f);
			case FREEZE -> {
				living.setFrozenTicks(Math.min(living.getMinFreezeDamageTicks() + 60, living.getFrozenTicks() + 120));
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), this);
			}
			case POISON -> living.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 0), this);
			case KNOCKBACK -> living.takeKnockback(1.6, getX() - living.getX(), getZ() - living.getZ());
			case SHOCK -> {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3), this);
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12, 0.3, 0.4, 0.3, 0.2);
			}
			case SONIC -> {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), this);
				world.spawnParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
				world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 0.4f, 1.8f);
			}
			case CRYSTAL -> {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0), this);
				world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.3, 0.4, 0.3, 0.05);
			}
			case DRAIN -> {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0), this);
				heal(2.0f);
			}
			default -> {
			}
		}
		return hit;
	}

	@Override
	public boolean isFireImmune() {
		return effect() == AlienDefinition.HitEffect.FIRE || super.isFireImmune();
	}

	@Override
	public boolean canFreeze() {
		return effect() != AlienDefinition.HitEffect.FREEZE && super.canFreeze();
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		if (species().map(AlienDefinition.Species::flying).orElse(false)) {
			return false;
		}
		return super.handleFallDamage(fallDistance, damageMultiplier, damageSource);
	}

	@Override
	protected Text getDefaultName() {
		return species().<Text>map(s -> Text.translatable(s.name())).orElseGet(super::getDefaultName);
	}

	// --- Speichern --------------------------------------------------------------------------------------------------

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("Alien", dataTracker.get(ALIEN));
		nbt.putInt("Variant", variant());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Alien")) {
			dataTracker.set(ALIEN, nbt.getString("Alien"));
		}
		dataTracker.set(VARIANT, nbt.getInt("Variant"));
		applied = "";
		applySpecies();
	}

	// --- GeckoLib ---------------------------------------------------------------------------------------------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// Gliederschwung macht der Renderer (wie beim verwandelten Spieler); keine eigenen Animationen
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
