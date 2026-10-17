package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

/**
 * Ausgeruestete Tasten-Faehigkeiten je Alien: fuer jede der {@link AlienDefinition#LOADOUT_SIZE} Tasten der Index der
 * Faehigkeit in {@link AlienDefinition#abilities()}. Aliens ohne Eintrag nutzen {@link AlienDefinition#defaultLoadout()}.
 */
public record AlienLoadouts(Map<Identifier, List<Integer>> loadouts) {
	public static final AlienLoadouts EMPTY = new AlienLoadouts(Map.of());
	public static final Codec<AlienLoadouts> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT.listOf())
			.xmap(AlienLoadouts::new, AlienLoadouts::loadouts);
	public static final PacketCodec<ByteBuf, AlienLoadouts> PACKET_CODEC = PacketCodecs.codec(CODEC);

	public AlienLoadouts {
		Map<Identifier, List<Integer>> copy = new HashMap<>();
		loadouts.forEach((id, list) -> copy.put(id, List.copyOf(list)));
		loadouts = Map.copyOf(copy);
	}

	/**
	 * Gueltiges Loadout fuer dieses Alien: gespeicherte Auswahl, sofern sie noch zu den Daten passt (Datenpakete koennen
	 * sich aendern), sonst der Standard.
	 */
	public List<Integer> resolve(Identifier alienId, AlienDefinition alien) {
		List<Integer> stored = loadouts.get(alienId);
		return stored != null && valid(alien, stored) ? stored : alien.defaultLoadout();
	}

	/** Index der Faehigkeit auf Taste {@code keySlot} (0..5), oder -1. */
	public int poolIndex(Identifier alienId, AlienDefinition alien, int keySlot) {
		List<Integer> loadout = resolve(alienId, alien);
		return keySlot >= 0 && keySlot < loadout.size() ? loadout.get(keySlot) : -1;
	}

	public AlienLoadouts with(Identifier alienId, List<Integer> loadout) {
		Map<Identifier, List<Integer>> next = new HashMap<>(loadouts);
		next.put(alienId, List.copyOf(loadout));
		return new AlienLoadouts(next);
	}

	/**
	 * Regeln: nur Tasten-Faehigkeiten, keine doppelt, so viele Tasten wie der Pool hergibt (hoechstens 6) und hoechstens
	 * eine ULTIMATE — sonst liesse sich die staerkste Stufe mehrfach ausruesten.
	 */
	public static boolean valid(AlienDefinition alien, List<Integer> loadout) {
		int expected = Math.min(AlienDefinition.LOADOUT_SIZE, alien.keyIndices().size());
		if (loadout.size() != expected) {
			return false;
		}
		Set<Integer> seen = new HashSet<>();
		int ultimates = 0;
		for (int index : loadout) {
			if (index < 0 || index >= alien.abilities().size() || !alien.abilities().get(index).isKey() || !seen.add(index)) {
				return false;
			}
			if (alien.abilities().get(index).role() == AbilitySlot.Role.ULTIMATE) {
				ultimates++;
			}
		}
		return ultimates <= 1;
	}

	/** Kopie als veraenderbare Liste (Menue). */
	public List<Integer> editable(Identifier alienId, AlienDefinition alien) {
		return new ArrayList<>(resolve(alienId, alien));
	}
}
