package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server → Client: Alien-Rad oeffnen (nach Rechtsklick mit dem Omnitrix-Item). */
public record OpenOmnitrixPayload() implements CustomPayload {
	public static final OpenOmnitrixPayload INSTANCE = new OpenOmnitrixPayload();
	public static final CustomPayload.Id<OpenOmnitrixPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("open_omnitrix"));
	public static final PacketCodec<ByteBuf, OpenOmnitrixPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
