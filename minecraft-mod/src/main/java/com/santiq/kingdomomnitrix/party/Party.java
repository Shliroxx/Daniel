package com.santiq.kingdomomnitrix.party;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Eine Gruppe von bis zu {@link #MAX_SIZE} Spielern; der Anfuehrer laedt ein und wirft hinaus. */
public final class Party {
	public static final int MAX_SIZE = 4;

	private final UUID id = UUID.randomUUID();
	private final Set<UUID> members = new LinkedHashSet<>();
	private UUID leader;

	Party(UUID leader) {
		this.leader = leader;
		members.add(leader);
	}

	public UUID id() {
		return id;
	}

	public UUID leader() {
		return leader;
	}

	public List<UUID> members() {
		return List.copyOf(members);
	}

	public int size() {
		return members.size();
	}

	public boolean contains(UUID player) {
		return members.contains(player);
	}

	boolean isFull() {
		return members.size() >= MAX_SIZE;
	}

	void add(UUID player) {
		members.add(player);
	}

	/** Entfernt einen Spieler; geht der Anfuehrer, uebernimmt das naechste Mitglied. */
	void remove(UUID player) {
		members.remove(player);
		if (player.equals(leader) && !members.isEmpty()) {
			leader = members.iterator().next();
		}
	}
}
