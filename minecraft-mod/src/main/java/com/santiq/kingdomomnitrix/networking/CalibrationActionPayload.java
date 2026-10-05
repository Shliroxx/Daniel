package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Client → Server: Aenderung an der Kalibrier-Werkbank {@code bench}. {@code module} ist {@code cooling}, {@code core},
 * {@code bandwidth} (Wert {@code +} oder {@code -}) oder {@code color} (Wert = Farbmodul). Der Server prueft alles selbst.
 */
public record CalibrationActionPayload(BlockPos bench, String module, String value) implements CustomPayload {
	public static final CustomPayload.Id<CalibrationActionPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("calibration_action"));
	public static final PacketCodec<ByteBuf, CalibrationActionPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, CalibrationActionPayload::bench,
			PacketCodecs.string(32), CalibrationActionPayload::module,
			PacketCodecs.string(32), CalibrationActionPayload::value,
			CalibrationActionPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
