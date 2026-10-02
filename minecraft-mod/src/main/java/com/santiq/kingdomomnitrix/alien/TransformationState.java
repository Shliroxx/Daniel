package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

/**
 * Omnitrix-Zustand eines Spielers. Alle Zeitpunkte sind absolute Weltzeit ({@code World#getTime()}),
 * damit Client und Server Restzeiten ohne Tick-Sync selbst berechnen koennen.
 *
 * <p>Energie wird nicht pro Tick gespeichert, sondern als Stand {@code energy} zum Zeitpunkt
 * {@code energyStamp}; der aktuelle Wert ergibt sich aus der Regeneration seitdem. So entsteht
 * nur bei echten Aenderungen ein Netzwerkpaket.</p>
 */
public record TransformationState(
		Optional<Identifier> activeAlien,
		Optional<Identifier> selectedAlien,
		long startTick,
		long endTick,
		long rechargeUntil,
		float energy,
		long energyStamp,
		List<Long> abilityReadyAt,
		long invulnerableUntil,
		float humanHealth) {

	public static final TransformationState EMPTY = new TransformationState(Optional.empty(), Optional.empty(),
			0L, 0L, 0L, 0.0f, 0L, List.of(), 0L, 0.0f);

	public static final Codec<TransformationState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.optionalFieldOf("active_alien").forGetter(TransformationState::activeAlien),
			Identifier.CODEC.optionalFieldOf("selected_alien").forGetter(TransformationState::selectedAlien),
			Codec.LONG.lenientOptionalFieldOf("start_tick", 0L).forGetter(TransformationState::startTick),
			Codec.LONG.lenientOptionalFieldOf("end_tick", 0L).forGetter(TransformationState::endTick),
			Codec.LONG.lenientOptionalFieldOf("recharge_until", 0L).forGetter(TransformationState::rechargeUntil),
			Codec.FLOAT.lenientOptionalFieldOf("energy", 0.0f).forGetter(TransformationState::energy),
			Codec.LONG.lenientOptionalFieldOf("energy_stamp", 0L).forGetter(TransformationState::energyStamp),
			Codec.LONG.listOf().lenientOptionalFieldOf("ability_ready_at", List.of()).forGetter(TransformationState::abilityReadyAt),
			Codec.LONG.lenientOptionalFieldOf("invulnerable_until", 0L).forGetter(TransformationState::invulnerableUntil),
			Codec.FLOAT.lenientOptionalFieldOf("human_health", 0.0f).forGetter(TransformationState::humanHealth)
	).apply(instance, TransformationState::new));

	public static final PacketCodec<ByteBuf, TransformationState> PACKET_CODEC = PacketCodecs.codec(CODEC);

	public TransformationState {
		abilityReadyAt = List.copyOf(abilityReadyAt);
	}

	public boolean isTransformed() {
		return activeAlien.isPresent();
	}

	public long remainingTicks(long now) {
		return isTransformed() ? Math.max(0L, endTick - now) : 0L;
	}

	/** Gesamtdauer der laufenden Verwandlung (mit Boni). */
	public long totalTicks() {
		return Math.max(1L, endTick - startTick);
	}

	public long rechargeRemaining(long now) {
		return Math.max(0L, rechargeUntil - now);
	}

	public float currentEnergy(AlienDefinition alien, long now) {
		float regenerated = energy + alien.energyRegen() * Math.max(0L, now - energyStamp) / 20.0f;
		return Math.min(alien.maxEnergy(), regenerated);
	}

	public long readyAt(int slot) {
		return slot >= 0 && slot < abilityReadyAt.size() ? abilityReadyAt.get(slot) : 0L;
	}

	public long cooldownRemaining(int slot, long now) {
		return Math.max(0L, readyAt(slot) - now);
	}

	public boolean isInvulnerable(long now) {
		return now < invulnerableUntil;
	}

	public TransformationState withSelected(Identifier alienId) {
		return new TransformationState(activeAlien, Optional.of(alienId), startTick, endTick, rechargeUntil,
				energy, energyStamp, abilityReadyAt, invulnerableUntil, humanHealth);
	}

	/** @param durationTicks Dauer inklusive Boni (Heldenstufe, Faehigkeiten, Meisterschaft) */
	public TransformationState transformed(Identifier alienId, AlienDefinition alien, long now, int durationTicks) {
		return transformed(alienId, alien, now, durationTicks, 0.0f);
	}

	/**
	 * @param humanHealth Lebenspunkte der Menschenform beim Verwandeln — das Alien kaempft mit eigenen Lebenspunkten,
	 *                    beim Zurueckverwandeln kommen diese zurueck (0 = nicht gespeichert)
	 */
	public TransformationState transformed(Identifier alienId, AlienDefinition alien, long now, int durationTicks, float humanHealth) {
		return new TransformationState(Optional.of(alienId), Optional.of(alienId), now, now + durationTicks,
				rechargeUntil, alien.maxEnergy(), now, List.of(), 0L, humanHealth);
	}

	public TransformationState reverted(long rechargeUntilTick) {
		return new TransformationState(Optional.empty(), selectedAlien, 0L, 0L, rechargeUntilTick,
				0.0f, 0L, List.of(), 0L, 0.0f);
	}

	public TransformationState afterAbility(int slot, float energyAfter, long now, long cooldownTicks) {
		List<Long> ready = new ArrayList<>(abilityReadyAt);
		while (ready.size() <= slot) {
			ready.add(0L);
		}
		ready.set(slot, now + cooldownTicks);
		return new TransformationState(activeAlien, selectedAlien, startTick, endTick, rechargeUntil,
				energyAfter, now, ready, invulnerableUntil, humanHealth);
	}

	/**
	 * Schnellwechsel in ein anderes Alien ohne Rueckverwandlung: neue Restzeit, volle Energie des neuen Aliens,
	 * Abklingzeiten zurueckgesetzt; die gespeicherten Menschen-Lebenspunkte bleiben.
	 */
	public TransformationState quickChanged(Identifier alienId, AlienDefinition alien, long now, int durationTicks) {
		return new TransformationState(Optional.of(alienId), Optional.of(alienId), now, now + durationTicks, rechargeUntil,
				alien.maxEnergy(), now, List.of(), invulnerableUntil, humanHealth);
	}

	public TransformationState withInvulnerableUntil(long tick) {
		return new TransformationState(activeAlien, selectedAlien, startTick, endTick, rechargeUntil,
				energy, energyStamp, abilityReadyAt, Math.max(invulnerableUntil, tick), humanHealth);
	}
}
