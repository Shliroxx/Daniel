package com.santiq.kingdomomnitrix.alien;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Wendet {@link AlienTraits} auf verwandelte Spieler an und nimmt beim Zurueckverwandeln genau das zurueck, was hier
 * gegeben wurde (Flug nur, wenn der Spieler ihn nicht ohnehin hat; nur eigene „ambient“-Effekte). Laeuft im
 * bestehenden Verwandlungs-Tick, Effekte nur jede Sekunde.
 */
public final class AlienTraitHandler {
	private static final int EFFECT_TICKS = 400;
	private static final int REFRESH_BELOW = 300;
	private static final int WATER_EFFECT_TICKS = 40;
	private static final int SENSES_INTERVAL = 40;

	private static final Set<UUID> GRANTED_FLIGHT = new HashSet<>();
	private static final Map<UUID, Set<RegistryEntry<StatusEffect>>> GRANTED_EFFECTS = new HashMap<>();
	private static final Map<UUID, Integer> DRY_TICKS = new HashMap<>();

	private AlienTraitHandler() {
	}

	/** Jeden Tick fuer verwandelte Spieler. */
	public static void tick(ServerPlayerEntity player, AlienDefinition alien, long now) {
		AlienTraits traits = alien.traits();
		UUID id = player.getUuid();
		if (traits.flight() && !player.isCreative() && !player.isSpectator()) {
			// auch nach Neustart/Neubetreten als „von uns“ merken, sonst bliebe der Flug nach der Rueckverwandlung
			GRANTED_FLIGHT.add(id);
			if (!player.getAbilities().allowFlying) {
				player.getAbilities().allowFlying = true;
				player.sendAbilitiesUpdate();
			}
		}
		if (traits.dryOutSeconds() > 0) {
			tickDryOut(player, traits, now);
		}
		for (RegistryEntry<StatusEffect> effect : traits.immuneEffects()) {
			if (player.hasStatusEffect(effect)) {
				player.removeStatusEffect(effect);
			}
		}
		if (now % 20 != 0) {
			return;
		}
		for (AlienTraits.Effect effect : traits.effects()) {
			grant(player, effect, EFFECT_TICKS, REFRESH_BELOW);
		}
		// Ripjaws' Gezeitenzonen zaehlen fuer ihren Erzeuger als Wasser
		if (player.isTouchingWater() || com.santiq.kingdomomnitrix.ability.TideZones.isIn(player)) {
			for (AlienTraits.Effect effect : traits.waterEffects()) {
				grant(player, effect, WATER_EFFECT_TICKS, WATER_EFFECT_TICKS);
			}
		}
		if (traits.sensesRadius() > 0.0f && now % SENSES_INTERVAL == 0) {
			double radius = traits.sensesRadius();
			for (LivingEntity target : player.getServerWorld().getEntitiesByClass(LivingEntity.class,
					player.getBoundingBox().expand(radius), e -> e instanceof Monster && e.isAlive() && e.squaredDistanceTo(player) <= radius * radius)) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, SENSES_INTERVAL + 10, 0, true, false), player);
			}
		}
	}

	/** Beim Zurueckverwandeln/Wechseln: gegebene Eigenschaften entfernen. */
	public static void clear(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		if (GRANTED_FLIGHT.remove(id) && !player.isCreative() && !player.isSpectator()) {
			player.getAbilities().allowFlying = false;
			player.getAbilities().flying = false;
			player.sendAbilitiesUpdate();
		}
		Set<RegistryEntry<StatusEffect>> effects = GRANTED_EFFECTS.remove(id);
		if (effects != null) {
			for (RegistryEntry<StatusEffect> effect : effects) {
				StatusEffectInstance active = player.getStatusEffect(effect);
				if (active != null && active.isAmbient()) {
					player.removeStatusEffect(effect);
				}
			}
		}
		DRY_TICKS.remove(id);
	}

	/**
	 * Spieler verlaesst den Server: gewaehrten Flug vor dem Speichern zuruecknehmen (die Spielerdaten speichern
	 * {@code allowFlying} — sonst floege er nach einem Neustart dauerhaft). Ist er beim Betreten noch verwandelt, gibt
	 * der naechste Tick den Flug zurueck.
	 */
	public static void disconnect(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		if (GRANTED_FLIGHT.remove(id) && !player.isCreative() && !player.isSpectator()) {
			player.getAbilities().allowFlying = false;
			player.getAbilities().flying = false;
		}
		GRANTED_EFFECTS.remove(id);
		DRY_TICKS.remove(id);
	}

	private static void grant(ServerPlayerEntity player, AlienTraits.Effect effect, int ticks, int refreshBelow) {
		StatusEffectInstance active = player.getStatusEffect(effect.effect());
		if (active != null && (!active.isAmbient() || active.getAmplifier() > effect.amplifier())) {
			return; // eigener Trank oder staerkerer Effekt: nicht ueberschreiben
		}
		if (active == null || active.getDuration() < refreshBelow) {
			player.addStatusEffect(new StatusEffectInstance(effect.effect(), ticks, effect.amplifier(), true, false, true));
			GRANTED_EFFECTS.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(effect.effect());
		}
	}

	/**
	 * Austrocknen (Ripjaws): an Land zaehlt die Zeit, Wasser oder Regen setzt sie zurueck. Nach der Frist Schwaeche und
	 * Langsamkeit, nach doppelter Frist zusaetzlich alle 3 s ein halbes Herz — mit Hinweis in der Aktionsleiste.
	 */
	private static void tickDryOut(ServerPlayerEntity player, AlienTraits traits, long now) {
		UUID id = player.getUuid();
		if (com.santiq.kingdomomnitrix.ability.TideZones.isWet(player)) {
			if (DRY_TICKS.getOrDefault(id, 0) > traits.dryOutSeconds() * 20) {
				player.sendMessage(Text.translatable("message.kingdomomnitrix.rehydrated").formatted(Formatting.AQUA), true);
			}
			DRY_TICKS.put(id, 0);
			return;
		}
		int dry = DRY_TICKS.merge(id, 1, Integer::sum);
		int limit = traits.dryOutSeconds() * 20;
		if (dry == limit) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.drying_out").formatted(Formatting.GOLD), true);
		}
		if (dry >= limit && now % 20 == 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, true, false, true));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, true, false, true));
			GRANTED_EFFECTS.computeIfAbsent(id, k -> new HashSet<>()).add(StatusEffects.SLOWNESS);
			GRANTED_EFFECTS.get(id).add(StatusEffects.WEAKNESS);
		}
		if (dry >= limit * 2 && now % 60 == 0) {
			player.damage(player.getDamageSources().dryOut(), 1.0f);
		}
	}
}
