package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: Gadget-Absicht (Index in {@code GadgetManager.Action}). */
public record GadgetActionPayload(int action) implements CustomPayload {
	public static final CustomPayload.Id<GadgetActionPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("gadget_action"));
	public static final PacketCodec<ByteBuf, GadgetActionPayload> CODEC = PacketCodecs.VAR_INT.xmap(GadgetActionPayload::new, GadgetActionPayload::action);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
