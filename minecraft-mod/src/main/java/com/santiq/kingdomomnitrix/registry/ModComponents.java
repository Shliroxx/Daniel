package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.mojang.serialization.Codec;
import com.santiq.kingdomomnitrix.weapon.WeaponState;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Eigene Item-Komponenten. */
public final class ModComponents {
	/** Alien, dessen DNA eine Probe enthaelt. */
	public static final ComponentType<Identifier> DNA_ALIEN = Registry.register(Registries.DATA_COMPONENT_TYPE,
			KingdomOmnitrix.id("dna_alien"),
			ComponentType.<Identifier>builder().codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC).build());

	/** Upgrade-Stufe eines Keyblades (1 = Grundstufe). */
	public static final ComponentType<Integer> KEYBLADE_LEVEL = Registry.register(Registries.DATA_COMPONENT_TYPE,
			KingdomOmnitrix.id("keyblade_level"),
			ComponentType.<Integer>builder().codec(Codec.intRange(1, 99)).packetCodec(PacketCodecs.VAR_INT).build());

	/** Zauber, den ein Magie-Kristall aufwertet. */
	public static final ComponentType<Identifier> SPELL = Registry.register(Registries.DATA_COMPONENT_TYPE,
			KingdomOmnitrix.id("spell"),
			ComponentType.<Identifier>builder().codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC).build());

	/** Stufe und Munition einer Ratchet-&-Clank-Waffe. */
	public static final ComponentType<WeaponState> WEAPON_STATE = Registry.register(Registries.DATA_COMPONENT_TYPE,
			KingdomOmnitrix.id("weapon_state"),
			ComponentType.<WeaponState>builder().codec(WeaponState.CODEC).packetCodec(WeaponState.PACKET_CODEC).build());

	/** Heli-Pack im Heli-Jet-Modus. */
	public static final ComponentType<Boolean> JET_MODE = Registry.register(Registries.DATA_COMPONENT_TYPE,
			KingdomOmnitrix.id("jet_mode"),
			ComponentType.<Boolean>builder().codec(Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());

	private ModComponents() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Komponenten registriert: {}", Registries.DATA_COMPONENT_TYPE.getId(DNA_ALIEN));
	}
}
