package com.santiq.kingdomomnitrix.progression;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

/** Ausgeruestete Helden-Faehigkeiten. Freigeschaltet ist alles bis zur eigenen Stufe; gespeichert wird nur die Auswahl. */
public record HeroAbilities(Set<Identifier> equipped) {
	public static final HeroAbilities EMPTY = new HeroAbilities(Set.of());

	public static final Codec<HeroAbilities> CODEC = Identifier.CODEC.listOf()
			.<HeroAbilities>xmap(list -> new HeroAbilities(Set.copyOf(list)), abilities -> List.copyOf(abilities.equipped()));

	public static final PacketCodec<ByteBuf, HeroAbilities> PACKET_CODEC = PacketCodecs.codec(CODEC);

	public HeroAbilities {
		equipped = Set.copyOf(equipped);
	}

	public boolean isEquipped(Identifier id) {
		return equipped.contains(id);
	}

	public HeroAbilities with(Identifier id, boolean present) {
		if (isEquipped(id) == present) {
			return this;
		}
		Set<Identifier> next = new HashSet<>(equipped);
		if (present) {
			next.add(id);
		} else {
			next.remove(id);
		}
		return new HeroAbilities(next);
	}
}
