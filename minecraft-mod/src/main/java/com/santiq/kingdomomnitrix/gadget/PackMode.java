package com.santiq.kingdomomnitrix.gadget;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.StringIdentifiable;

/**
 * Modi von Clanks Rucksack (Ratchet &amp; Clank), umschaltbar per Taste in dieser Reihenfolge:
 * Heli (Doppelsprung, Gleiten), Thruster (Schub nach vorn, Sinkflug), Gleiter (Schwingen fuer weite Gleitfluege,
 * Aufwind ueber Feuer und Lava) und Jetpack (Steigflug mit Treibstoff, der am Boden nachlaedt).
 * {@code gear} ist der Dateiname des 3D-Modells (geo/item/gear) und des Inventarsymbols.
 */
public enum PackMode implements StringIdentifiable {
	HELI("heli", "heli_pack", "item.kingdomomnitrix.heli_pack", "tooltip.kingdomomnitrix.heli_pack.usage"),
	THRUSTER("thruster", "heli_jet", "item.kingdomomnitrix.heli_jet", "tooltip.kingdomomnitrix.heli_jet.usage"),
	GLIDER("glider", "glider_pack", "item.kingdomomnitrix.glider_pack", "tooltip.kingdomomnitrix.glider_pack.usage"),
	JETPACK("jetpack", "jet_pack", "item.kingdomomnitrix.jet_pack", "tooltip.kingdomomnitrix.jet_pack.usage");

	public static final Codec<PackMode> CODEC = StringIdentifiable.createCodec(PackMode::values);
	public static final PacketCodec<ByteBuf, PackMode> PACKET_CODEC = PacketCodecs.VAR_INT.xmap(PackMode::byIndex, PackMode::ordinal);

	private final String id;
	private final String gear;
	private final String nameKey;
	private final String usageKey;

	PackMode(String id, String gear, String nameKey, String usageKey) {
		this.id = id;
		this.gear = gear;
		this.nameKey = nameKey;
		this.usageKey = usageKey;
	}

	public static PackMode byIndex(int index) {
		PackMode[] modes = values();
		return index >= 0 && index < modes.length ? modes[index] : HELI;
	}

	public PackMode next() {
		return byIndex((ordinal() + 1) % values().length);
	}

	public String gear() {
		return gear;
	}

	public String nameKey() {
		return nameKey;
	}

	public String usageKey() {
		return usageKey;
	}

	@Override
	public String asString() {
		return id;
	}
}
