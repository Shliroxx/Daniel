package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → Server: Helden-Faehigkeit an- oder ablegen. Stufe und AP prueft der Server. */
public record ToggleHeroAbilityPayload(Identifier ability) implements CustomPayload {
	public static final CustomPayload.Id<ToggleHeroAbilityPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("toggle_hero_ability"));
	public static final PacketCodec<ByteBuf, ToggleHeroAbilityPayload> CODEC = PacketCodec.tuple(
			Identifier.PACKET_CODEC, ToggleHeroAbilityPayload::ability,
			ToggleHeroAbilityPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
