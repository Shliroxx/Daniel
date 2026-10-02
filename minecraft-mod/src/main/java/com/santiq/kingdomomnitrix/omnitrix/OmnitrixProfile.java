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
		MasterControl masterControl) {

	/** Master Control: Endgame-Modus, freigeschaltet pro Spieler ({@link OmnitrixState#masterControl()}). */
	public record MasterControl(float heatMultiplier, float cooldownMultiplier, float durationMultiplier, float confirmSeconds,
			boolean quickChange) {
		public static final MasterControl DEFAULT = new MasterControl(0.0f, 0.25f, 3.0f, 0.08f, true);

		public static final Codec<MasterControl> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.floatRange(0.0f, 100.0f).optionalFieldOf("heat_multiplier", 0.0f).forGetter(MasterControl::heatMultiplier),
				Codec.floatRange(0.0f, 100.0f).optionalFieldOf("cooldown_multiplier", 0.25f).forGetter(MasterControl::cooldownMultiplier),
				Codecs.POSITIVE_FLOAT.optionalFieldOf("duration_multiplier", 3.0f).forGetter(MasterControl::durationMultiplier),
				Codec.floatRange(0.0f, 100.0f).optionalFieldOf("confirm_seconds", 0.08f).forGetter(MasterControl::confirmSeconds),
				Codec.BOOL.optionalFieldOf("quick_change", true).forGetter(MasterControl::quickChange)
		).apply(instance, MasterControl::new));
	}

	public static final OmnitrixProfile DEFAULT = new OmnitrixProfile(0.22f, 0.004f, 0.02f, 0.75f, 12, 1.0f, 1.0f, 0.5f, 0.42f,
			MasterControl.DEFAULT);

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
			MasterControl.CODEC.optionalFieldOf("master_control", MasterControl.DEFAULT).forGetter(OmnitrixProfile::masterControl)
	).apply(instance, OmnitrixProfile::new));
}
