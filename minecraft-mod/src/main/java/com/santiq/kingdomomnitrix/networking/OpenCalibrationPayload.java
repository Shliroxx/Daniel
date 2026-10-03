package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Server → Client: Kalibrier-Werkbank an Position {@code pos} oeffnen. */
public record OpenCalibrationPayload(BlockPos pos) implements CustomPayload {
	public static final CustomPayload.Id<OpenCalibrationPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("open_calibration"));
	public static final PacketCodec<ByteBuf, OpenCalibrationPayload> CODEC = BlockPos.PACKET_CODEC.xmap(OpenCalibrationPayload::new,
			OpenCalibrationPayload::pos);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
