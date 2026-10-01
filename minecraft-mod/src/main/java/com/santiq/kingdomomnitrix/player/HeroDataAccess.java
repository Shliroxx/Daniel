package com.santiq.kingdomomnitrix.player;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

	/** Gibt Helden-EP und meldet jeden Stufenaufstieg mit Nachricht und Klang. */
	public static HeroData grantExperience(ServerPlayerEntity player, int amount) {
		int before = get(player).level();
		HeroData after = update(player, data -> data.addExperience(amount));
		if (after.level() > before) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.level_up", after.level()).formatted(Formatting.GOLD, Formatting.BOLD), false);
			player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
		}
		return after;
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
