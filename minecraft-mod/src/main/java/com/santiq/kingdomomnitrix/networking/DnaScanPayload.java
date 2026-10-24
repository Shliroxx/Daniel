package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → Server: DNA-Scan auf dieses Ziel (jeden Tick, solange die Taste gehalten wird); -1 = Taste losgelassen. */
public record DnaScanPayload(int entityId) implements CustomPayload {
	public static final CustomPayload.Id<DnaScanPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("dna_scan"));
	public static final PacketCodec<ByteBuf, DnaScanPayload> CODEC = PacketCodecs.VAR_INT.xmap(DnaScanPayload::new, DnaScanPayload::entityId);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
