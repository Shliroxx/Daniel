package com.santiq.kingdomomnitrix.npc;

import com.santiq.kingdomomnitrix.networking.OpenNpcDialogPayload;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Ein Auftraggeber in der Welt. Welcher NPC es ist (Modell, Hitbox, Dialog), bestimmt die NPC-ID aus der
 * NPC-Registry. Steht still, schaut Spieler an, ist unverwundbar und verschwindet nie von selbst.
 * Rechtsklick oeffnet beim Spieler das Dialog-Fenster. Entfernen: Schleichen + Schlag im Kreativmodus oder /kill.
 */
public class NpcEntity extends PathAwareEntity implements GeoEntity {
	public static final String CONTROLLER = "main";
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation TALK = RawAnimation.begin().thenPlay("talk");
	private static final RawAnimation WAVE = RawAnimation.begin().thenPlay("wave");
	private static final TrackedData<String> NPC_ID = DataTracker.registerData(NpcEntity.class, TrackedDataHandlerRegistry.STRING);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int greetCooldown;

	public NpcEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f, 1.0f));
		goalSelector.add(2, new LookAroundGoal(this));
	}

	// --- NPC-ID ---------------------------------------------------------------------------------

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(NPC_ID, "");
	}

	@Nullable
	public Identifier npcId() {
		return Identifier.tryParse(dataTracker.get(NPC_ID));
	}

	public void setNpcId(Identifier id) {
		dataTracker.set(NPC_ID, id.toString());
		setCustomName(NpcDefinition.name(id));
		setCustomNameVisible(true);
		calculateDimensions();
	}

	public Optional<NpcDefinition> definition() {
		Identifier id = npcId();
		return id == null ? Optional.empty() : NpcRegistry.get(getWorld().getRegistryManager(), id);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (NPC_ID.equals(data)) {
			calculateDimensions();
		}
	}

	@Override
	protected EntityDimensions getBaseDimensions(EntityPose pose) {
		return definition().map(def -> EntityDimensions.changing(def.width(), def.height()).withEyeHeight(def.height() * 0.88f))
				.orElseGet(() -> super.getBaseDimensions(pose));
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("Npc", dataTracker.get(NPC_ID));
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		Identifier id = Identifier.tryParse(nbt.getString("Npc"));
		if (id != null) {
			setNpcId(id);
		}
	}

	// --- Verhalten ------------------------------------------------------------------------------

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		Identifier id = npcId();
		if (id == null || definition().isEmpty()) {
			return ActionResult.PASS;
		}
		if (player instanceof ServerPlayerEntity serverPlayer) {
			getLookControl().lookAt(player, 30.0f, 30.0f);
			triggerAnim(CONTROLLER, "talk");
			playSound(SoundEvents.ENTITY_VILLAGER_AMBIENT, 0.6f, 1.2f + random.nextFloat() * 0.2f);
			ServerPlayNetworking.send(serverPlayer, new OpenNpcDialogPayload(getId(), id));
		}
		return ActionResult.success(getWorld().isClient());
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient()) {
			if (greetCooldown > 0) {
				greetCooldown--;
			} else {
				PlayerEntity near = getWorld().getClosestPlayer(this, 4.0);
				if (near != null && !near.isSpectator()) {
					triggerAnim(CONTROLLER, "wave");
					greetCooldown = 600;
				}
			}
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		// Unverwundbar; nur /kill und ein schleichender Kreativspieler entfernen NPCs.
		if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
			return super.damage(source, amount);
		}
		if (source.getAttacker() instanceof PlayerEntity player && player.isCreative() && player.isSneaking()) {
			discard();
			return true;
		}
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}

	@Override
	@Nullable
	protected SoundEvent getAmbientSound() {
		return null;
	}

	// --- GeckoLib -------------------------------------------------------------------------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, CONTROLLER, 5,
				state -> state.setAndContinue(state.isMoving() ? WALK : IDLE))
				.triggerableAnim("talk", TALK)
				.triggerableAnim("wave", WAVE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
