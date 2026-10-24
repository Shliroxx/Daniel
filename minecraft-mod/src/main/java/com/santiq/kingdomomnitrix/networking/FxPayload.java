package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Ein Effekt der FX-Engine (Server → Client). Bedeutung der Felder je Art ({@link com.santiq.kingdomomnitrix.vfx.Fx.Kind}):
 * Position (x, y, z), zweiter Vektor (Richtung oder Endpunkt), Farbe (RGB), Groesse, Dauer in Ticks, Anzahl,
 * Entitaet zum Anhaengen (-1 = keine).
 */
public record FxPayload(int kind, double x, double y, double z, double ax, double ay, double az, int color, float size, int duration,
		int count, int entity) implements CustomPayload {
	public static final Id<FxPayload> ID = new Id<>(KingdomOmnitrix.id("fx"));
	public static final PacketCodec<RegistryByteBuf, FxPayload> CODEC = PacketCodec.of(FxPayload::write, FxPayload::read);

	private void write(RegistryByteBuf buf) {
		buf.writeByte(kind);
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
		buf.writeFloat((float) ax);
		buf.writeFloat((float) ay);
		buf.writeFloat((float) az);
		buf.writeInt(color);
		buf.writeFloat(size);
		buf.writeVarInt(duration);
		buf.writeVarInt(count);
		buf.writeVarInt(entity + 1);
	}

	private static FxPayload read(RegistryByteBuf buf) {
		return new FxPayload(buf.readUnsignedByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
				buf.readFloat(), buf.readInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
