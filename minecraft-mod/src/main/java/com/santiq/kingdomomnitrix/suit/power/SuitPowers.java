package com.santiq.kingdomomnitrix.suit.power;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.suit.SuitArmorItem;
import com.santiq.kingdomomnitrix.suit.SuitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Faehigkeiten der Endgame-Anzuege (nur mit allen vier Teilen desselben Anzugs):
 * <ul>
 *   <li><b>Energieschild</b>: faengt Schaden vor den Lebenspunkten ab und laedt nach einer Pause wieder auf
 *       (Ben 10: gross und schnell, Ratchet: klein und langsam). Bricht er, dauert die Pause doppelt so lange.</li>
 *   <li><b>Fortbewegung</b>: Galvan/Celestial freier Flug, Magnaplate Heli-Gleiten, Carbonox/Infernox Schubduesen
 *       (Gleiten und Schub steuert der Client, siehe {@link JetpackPayload}).</li>
 *   <li><b>Werte-Boni</b>: Ben 10 Tempo, Leben, Stufenhoehe, weiche Landung, Nachtsicht (Celestial);
 *       Ratchet Angriff, Angriffstempo, Rueckstossfestigkeit, weiche Landung, Feuerfestigkeit (Infernox).</li>
 * </ul>
 * Schild, Fortbewegung und Boni lassen sich in der Anzugsteuerung einzeln abschalten ({@link SuitToggles}).
 */
public final class SuitPowers {
	private static final Identifier SPEED = KingdomOmnitrix.id("suit_speed");
	private static final Identifier HEALTH = KingdomOmnitrix.id("suit_health");
	private static final Identifier STEP = KingdomOmnitrix.id("suit_step");
	private static final Identifier FALL = KingdomOmnitrix.id("suit_fall");
	private static final Identifier ATTACK = KingdomOmnitrix.id("suit_attack");
	private static final Identifier ATTACK_SPEED = KingdomOmnitrix.id("suit_attack_speed");
	private static final Identifier KNOCKBACK = KingdomOmnitrix.id("suit_knockback");
	private static final float DEFAULT_FLY_SPEED = 0.05f;

	/** Laufzeitzustand je Spieler */
	private static final class State {
		SuitSet set;
		float shield;
		long lastHit = Long.MIN_VALUE / 2;
		/** Schaden des letzten abgefangenen Treffers (fuer die Unverwundbarkeit) */
		float lastAmount;
		boolean broken;
		boolean flightGranted;
		boolean thrusting;
		int sentSet = -2;
		int sentShield = -1;
		int sentToggles = -1;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** Schutz vor Rekursion beim Weiterreichen des Restschadens */
	private static boolean forwarding;

	private SuitPowers() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SuitStatePayload.ID, SuitStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ShieldHitPayload.ID, ShieldHitPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SuitTogglePayload.ID, SuitTogglePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(JetpackPayload.ID, JetpackPayload.CODEC);

		ServerTickEvents.END_SERVER_TICK.register(SuitPowers::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayerEntity player) || absorb(player, source, amount));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUuid()));
		ServerPlayNetworking.registerGlobalReceiver(SuitTogglePayload.ID, (payload, context) -> {
			ItemStack chest = context.player().getEquippedStack(EquipmentSlot.CHEST);
			if (fullSet(context.player()) != null) {
				SuitToggles.flip(chest, payload.bit());
				context.player().playSoundToPlayer(SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.6f, 1.4f);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(JetpackPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			SuitSet set = fullSet(player);
			State state = state(player);
			state.thrusting = payload.thrusting() && set != null && set.mobility() != SuitSet.Mobility.NONE
					&& set.mobility() != SuitSet.Mobility.FLIGHT && SuitToggles.on(SuitToggles.get(player.getEquippedStack(EquipmentSlot.CHEST)),
					SuitToggles.FLIGHT);
		});
	}

	/** Vollstaendiger Anzug (alle vier Teile desselben Sets) oder null. */
	public static SuitSet fullSet(ServerPlayerEntity player) {
		SuitSet set = null;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!(player.getEquippedStack(slot).getItem() instanceof SuitArmorItem item) || (set != null && item.set() != set)) {
				return null;
			}
			set = item.set();
		}
		return set;
	}

	private static State state(ServerPlayerEntity player) {
		return STATES.computeIfAbsent(player.getUuid(), id -> new State());
	}

	// --- Tick ---------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			State state = state(player);
			SuitSet set = fullSet(player);
			int toggles = set != null ? SuitToggles.get(player.getEquippedStack(EquipmentSlot.CHEST)) : 0;
			if (set != state.set) {
				state.shield = 0.0f;
				state.broken = false;
				state.set = set;
			}
			boolean boost = set != null && SuitToggles.on(toggles, SuitToggles.BOOST);
			bonuses(player, boost ? set : null);
			flight(player, state, set != null && set.mobility() == SuitSet.Mobility.FLIGHT && SuitToggles.on(toggles, SuitToggles.FLIGHT)
					? set : null);
			long now = player.getServerWorld().getTime();
			if (set != null && SuitToggles.on(toggles, SuitToggles.SHIELD)) {
				int delay = set.shieldDelay() * (state.broken ? 2 : 1);
				if (now - state.lastHit > delay && state.shield < set.shieldCapacity()) {
					state.shield = Math.min(set.shieldCapacity(), state.shield + set.shieldRegen());
					if (state.shield >= set.shieldCapacity() && state.broken) {
						state.broken = false;
						player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.5f, 1.6f);
					}
				}
			} else {
				state.shield = 0.0f;
			}
			if (state.thrusting && set != null) {
				player.fallDistance = 0.0f;
				if (now % 2 == 0) {
					exhaust(player, set);
				}
			}
			sync(player, state, toggles);
		}
	}

	private static void bonuses(ServerPlayerEntity player, SuitSet set) {
		double speed = 0;
		double health = 0;
		double step = 0;
		double fall = 0;
		double attack = 0;
		double attackSpeed = 0;
		double knockback = 0;
		if (set != null) {
			int t = set.tier();
			if (set.line() == SuitSet.Line.BEN10) {
				speed = 0.1 * t;
				health = (t - 1) * 4;
				step = t >= 2 ? 0.5 : 0.0;
				fall = t == 3 ? 200 : 4 * t;
			} else {
				attack = new double[] {2, 4, 7}[t - 1];
				fall = 4 << (t - 1);
				attackSpeed = 0.1 * (t - 1);
				knockback = 0.2 * t;
			}
			if (set == SuitSet.CELESTIAL) {
				effect(player, new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, true));
			}
			if (set == SuitSet.INFERNOX) {
				effect(player, new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 60, 0, true, false, true));
			}
		}
		modifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED, speed, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(player, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH, health, EntityAttributeModifier.Operation.ADD_VALUE);
		modifier(player, EntityAttributes.GENERIC_STEP_HEIGHT, STEP, step, EntityAttributeModifier.Operation.ADD_VALUE);
		modifier(player, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, FALL, fall, EntityAttributeModifier.Operation.ADD_VALUE);
		modifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, ATTACK, attack, EntityAttributeModifier.Operation.ADD_VALUE);
		modifier(player, EntityAttributes.GENERIC_ATTACK_SPEED, ATTACK_SPEED, attackSpeed, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK, knockback, EntityAttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Effekt nur auffrischen, wenn er bald auslaeuft (sonst flackert die Nachtsicht). */
	private static void effect(ServerPlayerEntity player, StatusEffectInstance effect) {
		StatusEffectInstance current = player.getStatusEffect(effect.getEffectType());
		if (current == null || current.getDuration() < effect.getDuration() / 2) {
			player.addStatusEffect(effect);
		}
	}

	private static void modifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double value,
			EntityAttributeModifier.Operation operation) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		EntityAttributeModifier current = instance.getModifier(id);
		if (value == 0.0) {
			if (current != null) {
				instance.removeModifier(id);
			}
		} else if (current == null || current.value() != value) {
			instance.removeModifier(id);
			instance.addTemporaryModifier(new EntityAttributeModifier(id, value, operation));
		}
	}

	private static void flight(ServerPlayerEntity player, State state, SuitSet set) {
		boolean own = !player.isCreative() && !player.isSpectator();
		if (set != null && own) {
			if (!state.flightGranted || player.getAbilities().getFlySpeed() != set.flySpeed()) {
				player.getAbilities().allowFlying = true;
				player.getAbilities().setFlySpeed(set.flySpeed());
				player.sendAbilitiesUpdate();
				state.flightGranted = true;
			}
			if (player.getAbilities().flying && player.age % 3 == 0) {
				trail(player, set);
			}
		} else if (state.flightGranted) {
			state.flightGranted = false;
			if (own) {
				player.getAbilities().allowFlying = false;
				player.getAbilities().flying = false;
			}
			player.getAbilities().setFlySpeed(DEFAULT_FLY_SPEED);
			player.sendAbilitiesUpdate();
		}
	}

	// --- Schild -------------------------------------------------------------------------------------------------------

	/** ALLOW_DAMAGE: Schild faengt ab; Restschaden wird mit Schutz vor Rekursion weitergereicht. */
	private static boolean absorb(ServerPlayerEntity player, DamageSource source, float amount) {
		if (forwarding || amount <= 0.0f || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.isIn(DamageTypeTags.BYPASSES_SHIELD)
				&& source.isIn(DamageTypeTags.BYPASSES_ARMOR)) {
			return true;
		}
		State state = STATES.get(player.getUuid());
		if (state == null || state.set == null || state.shield <= 0.0f) {
			return true;
		}
		long now = player.getServerWorld().getTime();
		// eigene Unverwundbarkeit wie Vanilla: innerhalb von 10 Ticks zaehlt nur, was ueber den letzten Treffer hinausgeht
		float incoming = amount;
		if (now - state.lastHit < 10) {
			if (amount <= state.lastAmount) {
				return false;
			}
			incoming = amount - state.lastAmount;
		}
		state.lastHit = now;
		state.lastAmount = amount;
		float absorbed = Math.min(state.shield, incoming);
		state.shield -= absorbed;
		boolean broke = state.shield <= 0.01f;
		if (broke) {
			state.shield = 0.0f;
			state.broken = true;
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
		} else {
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.0f, 1.3f);
		}
		ShieldHitPayload hit = new ShieldHitPayload(player.getId(), state.set.color(), broke);
		for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, hit);
		}
		ServerPlayNetworking.send(player, hit);
		float rest = incoming - absorbed;
		if (rest > 0.01f) {
			forwarding = true;
			try {
				player.damage(source, rest);
			} finally {
				forwarding = false;
			}
		}
		return false;
	}

	// --- Effekte ------------------------------------------------------------------------------------------------------

	private static void trail(ServerPlayerEntity player, SuitSet set) {
		Vec3d at = player.getPos().add(0, 0.2, 0);
		player.getServerWorld().spawnParticles(new DustParticleEffect(rgb(set.color()), 1.0f), at.x, at.y, at.z, 2, 0.15, 0.05, 0.15, 0.0);
		if (set == SuitSet.CELESTIAL) {
			player.getServerWorld().spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.2, 0.1, 0.2, 0.01);
		}
	}

	/** Duesenflammen am Ruecken (fuer alle sichtbar). */
	private static void exhaust(ServerPlayerEntity player, SuitSet set) {
		float yaw = (float) Math.toRadians(player.bodyYaw);
		double bx = player.getX() + Math.sin(yaw) * 0.3;
		double bz = player.getZ() - Math.cos(yaw) * 0.3;
		double by = player.getY() + 0.8;
		if (set.mobility() == SuitSet.Mobility.GLIDE) {
			player.getServerWorld().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 2.1, player.getZ(), 1, 0.3, 0.0, 0.3, 0.0);
			return;
		}
		for (int side = -1; side <= 1; side += 2) {
			double sx = bx + Math.cos(yaw) * 0.18 * side;
			double sz = bz + Math.sin(yaw) * 0.18 * side;
			player.getServerWorld().spawnParticles(set == SuitSet.INFERNOX ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, sx, by, sz, 2, 0.03,
					0.0, 0.03, 0.01);
			player.getServerWorld().spawnParticles(ParticleTypes.SMOKE, sx, by - 0.2, sz, 1, 0.03, 0.0, 0.03, 0.01);
		}
	}

	private static Vector3f rgb(int color) {
		return new Vector3f(((color >> 16) & 0xFF) / 255.0f, ((color >> 8) & 0xFF) / 255.0f, (color & 0xFF) / 255.0f);
	}

	private static void sync(ServerPlayerEntity player, State state, int toggles) {
		int set = state.set == null ? -1 : state.set.ordinal();
		int shield = (int) (state.shield * 4);
		if (set == state.sentSet && shield == state.sentShield && toggles == state.sentToggles) {
			return;
		}
		state.sentSet = set;
		state.sentShield = shield;
		state.sentToggles = toggles;
		ServerPlayNetworking.send(player, new SuitStatePayload(set, state.shield, state.set == null ? 0.0f : state.set.shieldCapacity(), toggles));
	}
}
