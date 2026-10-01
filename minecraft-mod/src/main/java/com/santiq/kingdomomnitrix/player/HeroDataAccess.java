package com.santiq.kingdomomnitrix.player;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Einziger Zugang zu {@link HeroData}. Gespeichert wird mit dem Spieler, beim Tod uebernommen
 * und automatisch nur an den eigenen Client gesendet (Bolts und Story gehen Mitspieler nichts an).
 */
@SuppressWarnings("UnstableApiUsage")
public final class HeroDataAccess {
	public static final AttachmentType<HeroData> HERO_DATA = AttachmentRegistry.create(KingdomOmnitrix.id("hero_data"), builder -> builder
			.persistent(HeroData.CODEC)
			.initializer(() -> HeroData.DEFAULT)
			.copyOnDeath()
			.syncWith(HeroData.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	private HeroDataAccess() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Attachment registriert: {}", HERO_DATA.identifier());
	}

	/** Liest den Zustand; auf dem Client der zuletzt synchronisierte Stand. */
	public static HeroData get(PlayerEntity player) {
		HeroData data = player.getAttached(HERO_DATA);
		return data != null ? data : HeroData.DEFAULT;
	}

	/** Aendert den Zustand auf dem Server. Unveraenderte Ergebnisse loesen weder Speichern noch Sync aus. */
	public static HeroData update(ServerPlayerEntity player, UnaryOperator<HeroData> change) {
		HeroData before = get(player);
		HeroData after = change.apply(before);
		if (after == null) {
			throw new IllegalArgumentException("HeroData-Aenderung darf nicht null liefern");
		}
		if (!after.equals(before)) {
			player.setAttached(HERO_DATA, after);
		}
		return after;
	}
}
