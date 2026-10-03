package com.santiq.kingdomomnitrix.progression;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringIdentifiable;

/**
 * Was eine Helden-Faehigkeit bewirkt. Das JSON waehlt den Effekt, {@code value} ist seine Staerke;
 * mehrere ausgeruestete Faehigkeiten mit demselben Effekt addieren ihre Werte.
 */
public enum HeroAbilityEffect implements StringIdentifiable {
	/** +value Schlaege in der Boden-Combo. */
	COMBO_PLUS,
	/** +value Schlaege in der Luft-Combo. */
	AIR_COMBO_PLUS,
	/** +value zusaetzliche Ausweichrollen pro Sprung. */
	AIR_DODGE_PLUS,
	/** Ueberlebt einen toedlichen Treffer mit 1 Herz; danach value Sekunden Pause. */
	SECOND_CHANCE,
	/** MP-Regeneration und MP-Ladezeit +value (Anteil). */
	MP_HASTE,
	/** Zauberstaerke +value (Anteil). */
	MAGIC_BOOST,
	/** Omnitrix-Verwandlungsdauer +value (Anteil). */
	EXTENDED_TRANSFORMATION,
	/** Omnitrix-Nachladezeit -value (Anteil). */
	QUICK_RECHARGE,
	/** Eingesammelte Bolts +value (Anteil). */
	BOLT_BONUS,
	/** Heilt value Leben alle 3 Sekunden, wenn 10 Sekunden kein Treffer kam. */
	NANOTECH_REGEN,
	/** Laufgeschwindigkeit +value (Anteil). */
	QUICK_RUN,
	/** Sprungkraft +value (Attributwert). */
	HIGH_JUMP,
	/** Gehaltene Sprungtaste beim Fallen: Gleiten ohne Heli-Pack. */
	GLIDE,
	/** Helden-EP +value (Anteil), solange das Leben bei hoechstens der Haelfte ist. */
	EXP_BOOST;

	public static final Codec<HeroAbilityEffect> CODEC = StringIdentifiable.createCodec(HeroAbilityEffect::values);

	@Override
	public String asString() {
		return name().toLowerCase(Locale.ROOT);
	}
}
