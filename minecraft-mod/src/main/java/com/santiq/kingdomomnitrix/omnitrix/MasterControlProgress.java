package com.santiq.kingdomomnitrix.omnitrix;

import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.progression.AlienMastery;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Master Control freispielen: hat der Spieler {@code unlock_aliens} Aliens auf mindestens {@code unlock_mastery}
 * (Profil, Standard 5 × ★5), empfaengt das Omnitrix das Protokoll — Story-Flag {@link #FLAG}, Hologramm mit dem Code.
 * Danach schaltet der Code {@code 10000} (Datenpaket) den Modus an und aus. Admins: {@code /hero flag set
 * master_control_unlocked}.
 */
public final class MasterControlProgress {
	public static final String FLAG = "master_control_unlocked";

	private MasterControlProgress() {
	}

	/** Fortschritt: Aliens, die die Stufe erreicht haben, und wie viele noetig sind. */
	public record Progress(int reached, int needed, int level) {
		public boolean done() {
			return reached >= needed;
		}
	}

	public static boolean unlocked(PlayerEntity player) {
		return HeroDataAccess.get(player).hasFlag(FLAG);
	}

	public static Progress progress(PlayerEntity player) {
		OmnitrixProfile.MasterControl rules = OmnitrixCore.profile(player).masterControl();
		AlienMastery mastery = AlienMasteryManager.get(player);
		int reached = 0;
		for (var alien : mastery.experience().keySet()) {
			if (mastery.level(alien) >= rules.unlockMastery()) {
				reached++;
			}
		}
		return new Progress(reached, rules.unlockAliens(), rules.unlockMastery());
	}

	/** Nach jedem Meisterschafts-Aufstieg (und beim Betreten): Freischaltung pruefen. */
	public static void check(ServerPlayerEntity player) {
		if (unlocked(player) || !progress(player).done()) {
			return;
		}
		HeroDataAccess.update(player, data -> data.withFlag(FLAG, true));
		OmnitrixOs.send(player, OmnitrixOs.Event.MASTER_CONTROL, Text.translatable("holo.kingdomomnitrix.mc.unlocked"),
				Optional.of(Text.translatable("holo.kingdomomnitrix.mc.unlocked_hint")), Optional.empty());
		OmnitrixCore.cue(player, OmnitrixCue.MASTER_CONTROL);
	}
}
