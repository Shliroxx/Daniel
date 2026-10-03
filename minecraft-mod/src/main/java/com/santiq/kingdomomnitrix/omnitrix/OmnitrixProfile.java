package com.santiq.kingdomomnitrix.omnitrix;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.Codecs;

/**
 * Geraete-Profil des Omnitrix (Datenpaket: {@code data/<ns>/kingdomomnitrix/omnitrix_profile/<id>.json}, an alle
 * Clients synchronisiert). Alle Zahlen des Kernsystems kommen von hier — Prototyp, rekalibriertes Omnitrix oder spaetere
 * Upgrades sind nur weitere Profile.
 *
 * @param heatPerTransform       Hitze pro Verwandlung (0..1, 1 = Ueberhitzung)
 * @param heatPerSecondActive    Hitzezuwachs je Sekunde als Alien
 * @param heatDecayPerSecond     Abkuehlung je Sekunde in Menschenform
 * @param heatWarning            ab hier Warnung (Licht, Piepen)
 * @param overheatLockSeconds    Sperre nach Ueberhitzung
 * @param cooldownMultiplier     Faktor auf die Nachladezeit der Aliens
 * @param durationMultiplier     Faktor auf die Verwandlungsdauer
 * @param manualRevertCooldown   Anteil der Nachladezeit bei freiwilliger Rueckverwandlung
 * @param confirmSeconds         Energieaufbau zwischen Bestaetigen und Verwandeln (Client-Ablauf)
 * @param masterControl          Werte im Master-Control-Modus
 * @param quickChangeHeat        Hitze-Aufschlag beim Schnellwechsel Alien → Alien (Master Control: keiner)
 * @param quickChangeKeep        Anteil der Restzeit, der beim Schnellwechsel bleibt (Master Control: volle Dauer)
 * @param failsafeCooldownSeconds Abklingzeit der Notfall-Verwandlung
 * @param failsafeHeat           Hitze nach einer Notfall-Verwandlung
 * @param malfunctions           Fehlfunktionen bei hoher Hitze ({@link Malfunctions})
 */
public record OmnitrixProfile(
		float heatPerTransform,
		float heatPerSecondActive,
		float heatDecayPerSecond,
		float heatWarning,
		int overheatLockSeconds,
		float cooldownMultiplier,
		float durationMultiplier,
		float manualRevertCooldown,
		float confirmSeconds,
		MasterControl masterControl,
		float quickChangeHeat,
		float quickChangeKeep,
		int failsafeCooldownSeconds,
		float failsafeHeat,
		Malfunctions malfunctions) {

	/** Master Control: Endgame-Modus, freigeschaltet pro Spieler ({@link OmnitrixState#masterControl()}). */
	public record MasterControl(float heatMultiplier, float cooldownMultiplier, float durationMultiplier, float confirmSeconds,
			boolean quickChange, int unlockAliens, int unlockMastery) {
		/** Freischaltung: so viele Aliens auf mindestens dieser Meisterschaftsstufe. */
		public static final MasterControl DEFAULT = new MasterControl(0.0f, 0.25f, 3.0f, 0.08f, true, 5, 5);

		public static final Codec<MasterControl> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.floatRange(0.0f, 100.0f).optionalFieldOf("heat_multiplier", 0.0f).forGetter(MasterControl::heatMultiplier),
				Codec.floatRange(0.0f, 100.0f).optionalFieldOf("cooldown_multiplier", 0.25f).forGetter(MasterControl::cooldownMultiplier),
				Codecs.POSITIVE_FLOAT.optionalFieldOf("duration_multiplier", 3.0f).forGetter(MasterControl::durationMultiplier),
				Codec.floatRange(0.0f, 100.0f).optionalFieldOf("confirm_seconds", 0.08f).forGetter(MasterControl::confirmSeconds),
				Codec.BOOL.optionalFieldOf("quick_change", true).forGetter(MasterControl::quickChange),
				Codec.intRange(1, 64).optionalFieldOf("unlock_aliens", 5).forGetter(MasterControl::unlockAliens),
				Codec.intRange(1, 10).optionalFieldOf("unlock_mastery", 5).forGetter(MasterControl::unlockMastery)
		).apply(instance, MasterControl::new));
	}

	private static final float DEFAULT_QUICK_CHANGE_HEAT = 0.12f;

	public static final OmnitrixProfile DEFAULT = new OmnitrixProfile(0.22f, 0.004f, 0.02f, 0.75f, 12, 1.0f, 1.0f, 0.5f, 0.42f,
			MasterControl.DEFAULT, 0.12f, 0.5f, 600, 0.9f, Malfunctions.DEFAULT);

	public static final Codec<OmnitrixProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("heat_per_transform", DEFAULT.heatPerTransform()).forGetter(OmnitrixProfile::heatPerTransform),
			Codec.floatRange(0.0f, 100.0f).optionalFieldOf("heat_per_second_active", DEFAULT.heatPerSecondActive()).forGetter(OmnitrixProfile::heatPerSecondActive),
			Codec.floatRange(0.0f, 100.0f).optionalFieldOf("heat_decay_per_second", DEFAULT.heatDecayPerSecond()).forGetter(OmnitrixProfile::heatDecayPerSecond),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("heat_warning", DEFAULT.heatWarning()).forGetter(OmnitrixProfile::heatWarning),
			Codec.intRange(0, 3600).optionalFieldOf("overheat_lock_seconds", DEFAULT.overheatLockSeconds()).forGetter(OmnitrixProfile::overheatLockSeconds),
			Codec.floatRange(0.0f, 100.0f).optionalFieldOf("cooldown_multiplier", 1.0f).forGetter(OmnitrixProfile::cooldownMultiplier),
			Codecs.POSITIVE_FLOAT.optionalFieldOf("duration_multiplier", 1.0f).forGetter(OmnitrixProfile::durationMultiplier),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("manual_revert_cooldown", DEFAULT.manualRevertCooldown()).forGetter(OmnitrixProfile::manualRevertCooldown),
			Codec.floatRange(0.0f, 5.0f).optionalFieldOf("confirm_seconds", DEFAULT.confirmSeconds()).forGetter(OmnitrixProfile::confirmSeconds),
			MasterControl.CODEC.optionalFieldOf("master_control", MasterControl.DEFAULT).forGetter(OmnitrixProfile::masterControl),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("quick_change_heat", DEFAULT_QUICK_CHANGE_HEAT).forGetter(OmnitrixProfile::quickChangeHeat),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("quick_change_keep", 0.5f).forGetter(OmnitrixProfile::quickChangeKeep),
			Codec.intRange(0, 36_000).optionalFieldOf("failsafe_cooldown_seconds", 600).forGetter(OmnitrixProfile::failsafeCooldownSeconds),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("failsafe_heat", 0.9f).forGetter(OmnitrixProfile::failsafeHeat),
			Malfunctions.CODEC.optionalFieldOf("malfunctions", Malfunctions.DEFAULT).forGetter(OmnitrixProfile::malfunctions)
	).apply(instance, OmnitrixProfile::new));
}
