package com.santiq.kingdomomnitrix.omnitrix;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Fehlfunktionen des Omnitrix (Profil-Block {@code malfunctions}). Selten, nachvollziehbar, nie bestrafend:
 * nur ab der Hitze {@code start_heat}, Wahrscheinlichkeit steigt linear bis volle Hitze, Meisterschaft des Ziel-Aliens
 * senkt sie je Stufe, Master Control und ein gemeistertes Alien (★10) schliessen sie aus. Jede Fehlfunktion erklaert sich
 * per Hologramm-Meldung. Abschaltbar ueber die Spielregel {@code kingdomomnitrixOmnitrixMalfunctions}.
 *
 * <pre>
 * "malfunctions": {"start_heat": 0.75, "wrong_alien_chance": 0.35, "drift_chance": 0.25, "drift_seconds": 8,
 *                  "drift_interval_seconds": 10}
 * </pre>
 *
 * @param enabled              im Profil an (Recalibrated-Profile koennen sie abschalten)
 * @param startHeat            ab dieser Hitze moeglich
 * @param wrongAlienChance     Chance bei voller Hitze, dass eine Verwandlung ein anderes freigeschaltetes Alien liefert
 * @param driftChance          Chance je Pruefung bei voller Hitze, dass die Restzeit springt
 * @param driftSeconds         so viele Sekunden Restzeit gehen dabei verloren
 * @param driftIntervalSeconds Abstand der Zeitdrift-Pruefungen
 */
public record Malfunctions(boolean enabled, float startHeat, float wrongAlienChance, float driftChance, int driftSeconds,
		int driftIntervalSeconds) {
	/** Je Meisterschaftsstufe ueber 1 sinkt die Chance um diesen Anteil. */
	public static final float MASTERY_REDUCTION_PER_LEVEL = 0.08f;
	public static final int MASTERED_LEVEL = 10;

	public static final Malfunctions DEFAULT = new Malfunctions(true, 0.75f, 0.35f, 0.25f, 8, 10);

	public static final Codec<Malfunctions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Malfunctions::enabled),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("start_heat", DEFAULT.startHeat()).forGetter(Malfunctions::startHeat),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("wrong_alien_chance", DEFAULT.wrongAlienChance()).forGetter(Malfunctions::wrongAlienChance),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("drift_chance", DEFAULT.driftChance()).forGetter(Malfunctions::driftChance),
			Codec.intRange(0, 600).optionalFieldOf("drift_seconds", DEFAULT.driftSeconds()).forGetter(Malfunctions::driftSeconds),
			Codec.intRange(1, 600).optionalFieldOf("drift_interval_seconds", DEFAULT.driftIntervalSeconds()).forGetter(Malfunctions::driftIntervalSeconds)
	).apply(instance, Malfunctions::new));

	/**
	 * Wahrscheinlichkeit einer Fehlfunktion mit Hoechstwert {@code max} bei dieser Hitze und Meisterschaft; 0 unter der
	 * Schwelle, mit Master Control, bei gemeistertem Alien oder abgeschaltet.
	 */
	public float chance(float max, float heat, int mastery, boolean masterControl) {
		if (!enabled || masterControl || mastery >= MASTERED_LEVEL || heat < startHeat || max <= 0.0f) {
			return 0.0f;
		}
		float span = Math.max(1.0e-4f, 1.0f - startHeat);
		float heatShare = Math.min(1.0f, (heat - startHeat) / span);
		// schon an der Schwelle spuerbar (ein Viertel), voll bei 100 % Hitze
		float base = max * (0.25f + 0.75f * heatShare);
		float masteryFactor = Math.max(0.0f, 1.0f - MASTERY_REDUCTION_PER_LEVEL * (Math.max(1, mastery) - 1));
		return base * masteryFactor;
	}
}
