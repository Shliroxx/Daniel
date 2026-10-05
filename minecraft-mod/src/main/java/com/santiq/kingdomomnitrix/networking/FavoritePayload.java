package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: Favoriten bearbeiten. Mit Alien: im gewaehlten Set ein-/austragen; ohne Alien: Set {@code set}
 * auswaehlen.
 */
public record FavoritePayload(Optional<Identifier> alien, int set) implements CustomPayload {
	public static final CustomPayload.Id<FavoritePayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("omnitrix_favorite"));
	public static final PacketCodec<ByteBuf, FavoritePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.optional(Identifier.PACKET_CODEC), FavoritePayload::alien,
			PacketCodecs.VAR_INT, FavoritePayload::set,
			FavoritePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
