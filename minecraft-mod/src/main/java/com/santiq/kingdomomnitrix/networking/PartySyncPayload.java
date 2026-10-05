package com.santiq.kingdomomnitrix.networking;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

/** Server → Client: Mitglieder der eigenen Gruppe (leer = keine Gruppe). */
public record PartySyncPayload(List<Member> members) implements CustomPayload {
	public record Member(UUID uuid, String name, float health, float maxHealth, int level, boolean online, boolean leader) {
		// tuple() kennt hoechstens 6 Felder: online und Anfuehrer teilen sich ein Bit-Feld
		private static final int ONLINE = 1;
		private static final int LEADER = 2;

		public static final PacketCodec<ByteBuf, Member> CODEC = PacketCodec.tuple(
				Uuids.PACKET_CODEC, Member::uuid,
				PacketCodecs.STRING, Member::name,
				PacketCodecs.FLOAT, Member::health,
				PacketCodecs.FLOAT, Member::maxHealth,
				PacketCodecs.VAR_INT, Member::level,
				PacketCodecs.VAR_INT, Member::flags,
				Member::fromFlags);

		private int flags() {
			return (online ? ONLINE : 0) | (leader ? LEADER : 0);
		}

		private static Member fromFlags(UUID uuid, String name, Float health, Float maxHealth, Integer level, Integer flags) {
			return new Member(uuid, name, health, maxHealth, level, (flags & ONLINE) != 0, (flags & LEADER) != 0);
		}
	}

	public static final CustomPayload.Id<PartySyncPayload> ID = new CustomPayload.Id<>(KingdomOmnitrix.id("party_sync"));
	public static final PacketCodec<ByteBuf, PartySyncPayload> CODEC = PacketCodec.tuple(
			Member.CODEC.collect(PacketCodecs.toList()), PartySyncPayload::members,
			PartySyncPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
