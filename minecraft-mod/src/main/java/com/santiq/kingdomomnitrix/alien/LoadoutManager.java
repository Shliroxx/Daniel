package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Speichert die ausgeruesteten Faehigkeiten je Alien (dauerhaft, nach dem Tod erhalten, an den eigenen Client). Die
 * Belegung zeigt per Index in den Kraefte-Pool; als der Pool auf 8 Tasten-Kraefte je Alien gekuerzt wurde, verschoben
 * sich die Indizes — deshalb {@code alien_loadouts_v2}: alte Belegungen werden nicht falsch gedeutet, sondern durch den
 * Standard ersetzt.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LoadoutManager {
	public static final AttachmentType<AlienLoadouts> LOADOUTS = AttachmentRegistry.create(KingdomOmnitrix.id("alien_loadouts_v2"), builder -> builder
			.persistent(AlienLoadouts.CODEC)
			.initializer(() -> AlienLoadouts.EMPTY)
			.copyOnDeath()
			.syncWith(AlienLoadouts.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	private LoadoutManager() {
	}

	/** Laedt die Klasse (Anhang registrieren). */
	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Loadouts: {}", LOADOUTS.identifier());
	}

	public static AlienLoadouts get(PlayerEntity player) {
		AlienLoadouts loadouts = player.getAttached(LOADOUTS);
		return loadouts != null ? loadouts : AlienLoadouts.EMPTY;
	}

	/** Faehigkeits-Index auf Taste {@code keySlot} des aktiven Aliens, oder -1. */
	public static int poolIndex(PlayerEntity player, int keySlot) {
		TransformationState state = TransformationManager.get(player);
		Optional<Identifier> alienId = state.activeAlien();
		Optional<AlienDefinition> alien = alienId.flatMap(id -> AlienRegistry.get(player.getWorld().getRegistryManager(), id));
		return alien.isPresent() ? get(player).poolIndex(alienId.get(), alien.get(), keySlot) : -1;
	}

	/**
	 * Neues Loadout vom Client. Gesperrte Faehigkeiten (Meisterschaft) duerfen ausgeruestet werden — sie bleiben im HUD
	 * gesperrt, bis die Stufe erreicht ist; so laesst sich das Wunsch-Loadout frueh planen.
	 */
	public static boolean set(ServerPlayerEntity player, Identifier alienId, List<Integer> loadout) {
		Optional<AlienDefinition> alien = AlienRegistry.get(player.getServer().getRegistryManager(), alienId);
		if (alien.isEmpty() || !AlienLoadouts.valid(alien.get(), loadout)) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.loadout_invalid").formatted(Formatting.RED), true);
			return false;
		}
		if (com.santiq.kingdomomnitrix.ability.AbilityCharge.charging(player)) {
			return false; // laufende Aufladung zeigt auf die alte Belegung
		}
		player.setAttached(LOADOUTS, get(player).with(alienId, loadout));
		return true;
	}
}
