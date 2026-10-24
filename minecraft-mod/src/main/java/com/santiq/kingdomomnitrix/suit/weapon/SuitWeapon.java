package com.santiq.kingdomomnitrix.suit.weapon;

import com.santiq.kingdomomnitrix.suit.SuitSet;

/**
 * Die sechs Endgame-Waffen: je Linie eine pro Stufe, hergestellt im Fusionsaltar. Klingen und Hammer sind
 * Nahkampfwaffen mit Spezialangriff auf Rechtsklick, die Schusswaffen feuern auf Rechtsklick.
 */
public enum SuitWeapon {
	TAYDENITE_BLADE("taydenite_blade", SuitSet.TAYDENITE, true, 9.0f, -2.4f, 80, 0x3AE89A, "tooltip.kingdomomnitrix.weapon.taydenite_blade"),
	GALVAN_RIFLE("galvan_rifle", SuitSet.GALVAN, false, 4.0f, -2.6f, 30, 0x4BFF6A, "tooltip.kingdomomnitrix.weapon.galvan_rifle"),
	STAR_SWORD("star_sword", SuitSet.CELESTIAL, true, 16.0f, -3.0f, 160, 0xD8CCFF, "tooltip.kingdomomnitrix.weapon.star_sword"),
	MAGNAPLATE_HAMMER("magnaplate_hammer", SuitSet.MAGNAPLATE, true, 12.0f, -3.2f, 100, 0xA86AFF, "tooltip.kingdomomnitrix.weapon.magnaplate_hammer"),
	CARBONOX_LAUNCHER("carbonox_launcher", SuitSet.CARBONOX, false, 5.0f, -2.8f, 25, 0xFF2A2A, "tooltip.kingdomomnitrix.weapon.carbonox_launcher"),
	INFERNOX_RYNO("infernox_ryno", SuitSet.INFERNOX, false, 6.0f, -3.0f, 140, 0xFF8A1A, "tooltip.kingdomomnitrix.weapon.infernox_ryno");

	private final String id;
	private final SuitSet set;
	private final boolean melee;
	private final float damage;
	private final float attackSpeed;
	private final int cooldown;
	private final int color;
	private final String tooltipKey;

	SuitWeapon(String id, SuitSet set, boolean melee, float damage, float attackSpeed, int cooldown, int color, String tooltipKey) {
		this.id = id;
		this.set = set;
		this.melee = melee;
		this.damage = damage;
		this.attackSpeed = attackSpeed;
		this.cooldown = cooldown;
		this.color = color;
		this.tooltipKey = tooltipKey;
	}

	public String id() {
		return id;
	}

	/** Anzug, zu dessen Linie und Stufe die Waffe gehoert. */
	public SuitSet set() {
		return set;
	}

	public boolean melee() {
		return melee;
	}

	/** Nahkampfschaden (Gesamtwert wie im Tooltip) */
	public float damage() {
		return damage;
	}

	/** Angriffstempo-Modifikator (Vanilla-Schwert: -2.4) */
	public float attackSpeed() {
		return attackSpeed;
	}

	/** Abklingzeit des Spezialangriffs in Ticks */
	public int cooldown() {
		return cooldown;
	}

	public int color() {
		return color;
	}

	public String tooltipKey() {
		return tooltipKey;
	}
}
