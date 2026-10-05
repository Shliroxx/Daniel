package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: leichter (false) oder schwerer (true) Angriff mit einer Combo-Waffe. */
public record ComboAttackPayload(boolean heavy) implements CustomPayload {
	public static final CustomPayload.Id<ComboAttackPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("combo_attack"));
	public static final PacketCodec<ByteBuf, ComboAttackPayload> CODEC = PacketCodecs.BOOL.xmap(ComboAttackPayload::new, ComboAttackPayload::heavy);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
