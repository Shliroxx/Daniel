package com.santiq.kingdomomnitrix.progression;

import com.santiq.kingdomomnitrix.player.HeroData;
import net.minecraft.util.math.MathHelper;

/**
 * Werte, die automatisch mit der Heldenstufe wachsen. Reine Funktionen der Stufe, damit Server,
 * Client-HUD und Faehigkeiten-Fenster dieselben Zahlen zeigen.
 */
public final class ProgressionStats {
	/** Ein Herz (2 Leben) alle 5 Stufen: Stufe 50 = +10 Herzen. */
	public static final int LEVELS_PER_HEART = 5;
	public static final double ATTACK_PER_LEVEL = 0.04;
	public static final float BASE_MP = 100.0f;
	public static final float MP_PER_LEVEL = 2.0f;
	public static final float OMNITRIX_DURATION_PER_LEVEL = 0.01f;

	private ProgressionStats() {
	}

	private static int clamp(int level) {
		return MathHelper.clamp(level, HeroData.MIN_LEVEL, HeroData.MAX_LEVEL);
	}

	public static int bonusHealth(int level) {
		return 2 * (clamp(level) / LEVELS_PER_HEART);
	}

	public static double bonusAttack(int level) {
		return ATTACK_PER_LEVEL * (clamp(level) - 1);
	}

	public static float maxMp(int level) {
		return BASE_MP + MP_PER_LEVEL * (clamp(level) - 1);
	}

	/** Zusaetzlicher Anteil der Verwandlungsdauer (Stufe 50 = +49 %). */
	public static float omnitrixDurationBonus(int level) {
		return OMNITRIX_DURATION_PER_LEVEL * (clamp(level) - 1);
	}

	/** Faehigkeitspunkte (AP): 2 + eine je zwei Stufen. */
	public static int abilityPoints(int level) {
		return 2 + clamp(level) / 2;
	}
}
