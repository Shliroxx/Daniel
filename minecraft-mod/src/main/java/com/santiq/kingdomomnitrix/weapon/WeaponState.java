package com.santiq.kingdomomnitrix.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Item-Komponente einer Waffe: Stufe, aktuelle Munition und Magazingroesse (fuer die Munitionsleiste
 * am Item, die ohne Registry-Zugriff gezeichnet wird).
 */
public record WeaponState(int level, int ammo, int maxAmmo) {
	public static final Codec<WeaponState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.intRange(1, 99).optionalFieldOf("level", 1).forGetter(WeaponState::level),
			Codec.intRange(0, 100_000).optionalFieldOf("ammo", 0).forGetter(WeaponState::ammo),
			Codec.intRange(0, 100_000).optionalFieldOf("max_ammo", 0).forGetter(WeaponState::maxAmmo)
	).apply(instance, WeaponState::new));

	public static final PacketCodec<ByteBuf, WeaponState> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, WeaponState::level,
			PacketCodecs.VAR_INT, WeaponState::ammo,
			PacketCodecs.VAR_INT, WeaponState::maxAmmo,
			WeaponState::new);

	public WeaponState {
		level = Math.max(1, level);
		maxAmmo = Math.max(0, maxAmmo);
		ammo = Math.max(0, Math.min(maxAmmo, ammo));
	}

	public WeaponState withAmmo(int value) {
		return new WeaponState(level, value, maxAmmo);
	}

	public WeaponState withLevel(int value, int newMaxAmmo) {
		return new WeaponState(value, ammo, newMaxAmmo);
	}

	public int missingAmmo() {
		return maxAmmo - ammo;
	}
}
