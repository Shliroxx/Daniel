package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Gewaehlte Uniform je Alien (wie bei Alien Evolution): {@code classic} (Original-Serie), {@code evo} (eigener Look)
 * und {@code ultimate}. Wird mit dem Spieler gespeichert und an alle Clients synchronisiert, damit jeder den
 * verwandelten Spieler in der richtigen Uniform sieht. Welche Uniformen ein Alien hat, steht in
 * {@code assets/<ns>/alien_render/<modell>.json}; fehlt eine Textur, zeichnet der Client die classic-Uniform.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AlienUniforms {
	public static final String CLASSIC = "classic";
	public static final List<String> ALL = List.of(CLASSIC, "evo", "ultimate");
	private static final Codec<Map<Identifier, String>> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.STRING);
	public static final AttachmentType<Map<Identifier, String>> UNIFORMS = AttachmentRegistry.create(KingdomOmnitrix.id("alien_uniforms"),
			builder -> builder
					.persistent(CODEC)
					.initializer(Map::of)
					.copyOnDeath()
					.syncWith(PacketCodecs.codec(CODEC), AttachmentSyncPredicate.all()));

	private AlienUniforms() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Attachment registriert: {}", UNIFORMS.identifier());
	}

	public static String get(PlayerEntity player, Identifier alien) {
		Map<Identifier, String> map = player.getAttached(UNIFORMS);
		String uniform = map == null ? null : map.get(alien);
		return uniform != null && ALL.contains(uniform) ? uniform : CLASSIC;
	}

	/** Setzt die Uniform eines Aliens (nur bekannte IDs; classic entfernt den Eintrag). */
	public static void set(ServerPlayerEntity player, Identifier alien, String uniform) {
		if (!ALL.contains(uniform)) {
			return;
		}
		Map<Identifier, String> map = new HashMap<>(player.getAttachedOrElse(UNIFORMS, Map.of()));
		if (CLASSIC.equals(uniform)) {
			map.remove(alien);
		} else {
			map.put(alien, uniform);
		}
		player.setAttached(UNIFORMS, Map.copyOf(map));
	}
}
