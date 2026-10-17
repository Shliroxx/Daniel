package com.santiq.kingdomomnitrix.galvan;

import com.mojang.serialization.Codec;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Grey Matters Analyse-Datenbank: Wissen je Gegnerart (0–{@link #MAX_LEVEL}), dauerhaft im Spielstand und an den
 * Besitzer synchronisiert (das Galvan-Labor zeigt die Summe). Analysierte Ziele sind fuer kurze Zeit markiert.
 * Genutzt von Grey Matters Faehigkeiten, dem Galvan-Scanner und den Erfindungen (Wissens-Voraussetzung).
 */
@SuppressWarnings("UnstableApiUsage")
public final class GreyMatterKnowledge {
	public static final int MAX_LEVEL = 5;
	public static final float BONUS_PER_LEVEL = 0.08f;
	public static final int MARK_TICKS = 200;
	private static final Codec<Map<Identifier, Integer>> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.intRange(0, MAX_LEVEL));

	public static final AttachmentType<Map<Identifier, Integer>> KNOWLEDGE = AttachmentRegistry.create(KingdomOmnitrix.id("grey_matter_knowledge"),
			builder -> builder.persistent(CODEC).initializer(Map::of).copyOnDeath()
					.syncWith(PacketCodecs.codec(CODEC), AttachmentSyncPredicate.targetOnly()));

	/** markierte Ziele: Ende der Markierung (Welt-Tick) */
	private static final Map<UUID, Long> ANALYZED = new HashMap<>();
	/** schon analysierte Einzelwesen je Spieler (jedes zaehlt nur einmal), begrenzt */
	private static final Map<UUID, Set<UUID>> SCANNED = new HashMap<>();
	private static final int SCANNED_LIMIT = 256;

	private GreyMatterKnowledge() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Attachment registriert: {}", KNOWLEDGE.identifier());
	}

	public static int level(PlayerEntity player, LivingEntity target) {
		return player.getAttachedOrElse(KNOWLEDGE, Map.of()).getOrDefault(Registries.ENTITY_TYPE.getId(target.getType()), 0);
	}

	/** Summe aller Wissensstufen (Voraussetzung fuer Erfindungen). */
	public static int total(PlayerEntity player) {
		int sum = 0;
		for (int level : player.getAttachedOrElse(KNOWLEDGE, Map.of()).values()) {
			sum += level;
		}
		return sum;
	}

	/** Wissen ueber die Art erhoehen (jedes Einzelwesen nur einmal); liefert die neue Stufe. */
	public static int learn(ServerPlayerEntity player, LivingEntity target) {
		Set<UUID> seen = SCANNED.computeIfAbsent(player.getUuid(), id -> new LinkedHashSet<>());
		int level = level(player, target);
		if (!seen.add(target.getUuid()) || level >= MAX_LEVEL) {
			return level;
		}
		if (seen.size() > SCANNED_LIMIT) {
			Iterator<UUID> it = seen.iterator();
			it.next();
			it.remove();
		}
		Map<Identifier, Integer> map = new HashMap<>(player.getAttachedOrElse(KNOWLEDGE, Map.of()));
		map.put(Registries.ENTITY_TYPE.getId(target.getType()), level + 1);
		player.setAttached(KNOWLEDGE, Map.copyOf(map));
		return level + 1;
	}

	/** Ziel fuer die Gruppe markieren (leuchtet, nimmt mehr Schaden). */
	public static void mark(ServerPlayerEntity player, LivingEntity target, int ticks) {
		ANALYZED.put(target.getUuid(), target.getWorld().getTime() + ticks);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ticks, 0, false, false), player);
	}

	public static boolean marked(LivingEntity target) {
		Long until = ANALYZED.get(target.getUuid());
		return until != null && until > target.getWorld().getTime();
	}

	/** Abgelaufene Markierungen verwerfen (gelegentlich aufrufen). */
	public static void prune(long now) {
		ANALYZED.values().removeIf(until -> until < now - 20);
	}

	public static void forget(UUID player) {
		SCANNED.remove(player);
	}
}
