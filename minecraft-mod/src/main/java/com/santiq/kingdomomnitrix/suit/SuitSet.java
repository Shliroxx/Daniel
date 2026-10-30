package com.santiq.kingdomomnitrix.suit;

import net.minecraft.item.ArmorItem;
import net.minecraft.util.Rarity;

/**
 * Die sechs Endgame-Ruestungen in zwei Linien mit eigenen Staerken (Werte hier, Faehigkeiten folgen):
 * <ul>
 *   <li><b>Ben 10</b> (Taydenit, Galvan-Biotech, Celestialsapien): weniger Grundpanzerung, dafuer grosser, schnell
 *       nachladender Energieschild, Tempo, Leben und ab Stufe 2 freier Flug</li>
 *   <li><b>Ratchet &amp; Clank</b> (Magnaplate, Carbonox, Infernox): hohe Panzerung und Haerte, kleiner, langsamer
 *       Schild, dafuer Angriffskraft, Rueckstossfestigkeit und Heli-Gleiten bzw. Schubduesen</li>
 * </ul>
 * Die Faehigkeiten wirken nur mit allen vier Teilen desselben Anzugs ({@code suit.power.SuitPowers}).
 * Modell, Textur, Leuchtmaske und Animation entstehen mit {@code tools/generate_armor_models.py}; der Name ist der
 * Dateiname dort.
 */
public enum SuitSet {
	TAYDENITE("taydenite", Line.BEN10, 1, new int[] {3, 7, 5, 3}, 2.0f, 0.0f, 18, Rarity.UNCOMMON, "tooltip.kingdomomnitrix.suit.taydenite"),
	GALVAN("galvan", Line.BEN10, 2, new int[] {4, 8, 6, 3}, 3.0f, 0.05f, 22, Rarity.RARE, "tooltip.kingdomomnitrix.suit.galvan"),
	CELESTIAL("celestial", Line.BEN10, 3, new int[] {5, 10, 8, 5}, 5.0f, 0.2f, 30, Rarity.EPIC, "tooltip.kingdomomnitrix.suit.celestial"),
	MAGNAPLATE("magnaplate", Line.RATCHET, 1, new int[] {4, 8, 6, 3}, 3.0f, 0.05f, 12, Rarity.UNCOMMON, "tooltip.kingdomomnitrix.suit.magnaplate"),
	CARBONOX("carbonox", Line.RATCHET, 2, new int[] {5, 9, 7, 4}, 4.5f, 0.15f, 14, Rarity.RARE, "tooltip.kingdomomnitrix.suit.carbonox"),
	INFERNOX("infernox", Line.RATCHET, 3, new int[] {6, 12, 9, 6}, 7.0f, 0.3f, 18, Rarity.EPIC, "tooltip.kingdomomnitrix.suit.infernox");

	/** Herkunft der Ruestung; bestimmt Schild, Fortbewegung und Boni. */
	public enum Line { BEN10, RATCHET }

	/** Fortbewegung des vollstaendigen Anzugs. */
	public enum Mobility {
		NONE,
		/** Heli-Rotor: Sprungtaste in der Luft halten = langsam gleiten */
		GLIDE,
		/** Schubduesen: Sprungtaste halten = Schub, Treibstoff laedt am Boden nach */
		JETPACK,
		/** freier Flug wie im Kreativmodus (doppelt springen) */
		FLIGHT
	}

	private final String id;
	private final Line line;
	private final int tier;
	private final int[] defense;   // Helm, Brust, Beine, Stiefel
	private final float toughness;
	private final float knockback;
	private final int enchantability;
	private final Rarity rarity;
	private final String tooltipKey;

	SuitSet(String id, Line line, int tier, int[] defense, float toughness, float knockback, int enchantability, Rarity rarity,
			String tooltipKey) {
		this.id = id;
		this.line = line;
		this.tier = tier;
		this.defense = defense;
		this.toughness = toughness;
		this.knockback = knockback;
		this.enchantability = enchantability;
		this.rarity = rarity;
		this.tooltipKey = tooltipKey;
	}

	public String id() {
		return id;
	}

	public Line line() {
		return line;
	}

	public int tier() {
		return tier;
	}

	public int defense(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> defense[0];
			case CHESTPLATE, BODY -> defense[1];
			case LEGGINGS -> defense[2];
			case BOOTS -> defense[3];
		};
	}

	public float toughness() {
		return toughness;
	}

	public float knockback() {
		return knockback;
	}

	public int enchantability() {
		return enchantability;
	}

	public Rarity rarity() {
		return rarity;
	}

	public String tooltipKey() {
		return tooltipKey;
	}

	/** Haltbarkeits-Faktor (Vanilla: Netherit 37): Stufe 1 = 40, 2 = 55, 3 = 80. */
	public int durabilityFactor() {
		return switch (tier) {
			case 1 -> 40;
			case 2 -> 55;
			default -> 80;
		};
	}

	/** Schildkapazitaet in Lebenspunkten */
	public float shieldCapacity() {
		return line == Line.BEN10 ? new float[] {20, 40, 80}[tier - 1] : new float[] {8, 14, 24}[tier - 1];
	}

	/** Nachladen je Tick, nachdem die Pause abgelaufen ist */
	public float shieldRegen() {
		return line == Line.BEN10 ? new float[] {0.1f, 0.2f, 0.4f}[tier - 1] : new float[] {0.05f, 0.08f, 0.12f}[tier - 1];
	}

	/** Pause nach einem Treffer, bevor der Schild nachlaedt (Ticks) */
	public int shieldDelay() {
		return line == Line.BEN10 ? 60 : 100;
	}

	/** Leuchtfarbe von Schild und Effekten (RGB) */
	public int color() {
		return switch (this) {
			case TAYDENITE -> 0x3AE89A;
			case GALVAN -> 0x4BFF6A;
			case CELESTIAL -> 0xD8CCFF;
			case MAGNAPLATE -> 0xA86AFF;
			case CARBONOX -> 0xFF2A2A;
			case INFERNOX -> 0xFF8A1A;
		};
	}

	public Mobility mobility() {
		return switch (this) {
			case TAYDENITE -> Mobility.NONE;
			case GALVAN, CELESTIAL -> Mobility.FLIGHT;
			case MAGNAPLATE -> Mobility.GLIDE;
			case CARBONOX, INFERNOX -> Mobility.JETPACK;
		};
	}

	/** Fluggeschwindigkeit (Vanilla-Kreativ 0.05) */
	public float flySpeed() {
		return this == CELESTIAL ? 0.075f : 0.035f;
	}

	/** Treibstoff der Schubduesen in Ticks */
	public int jetpackFuel() {
		return this == INFERNOX ? 160 : 60;
	}

	/** Uebersetzungsschluessel der Setboni (Tooltip, Anzugsteuerung) */
	public String bonusKey() {
		return switch (this) {
			case TAYDENITE -> "tooltip.kingdomomnitrix.suit.bonus.taydenite";
			case GALVAN -> "tooltip.kingdomomnitrix.suit.bonus.galvan";
			case CELESTIAL -> "tooltip.kingdomomnitrix.suit.bonus.celestial";
			case MAGNAPLATE -> "tooltip.kingdomomnitrix.suit.bonus.magnaplate";
			case CARBONOX -> "tooltip.kingdomomnitrix.suit.bonus.carbonox";
			case INFERNOX -> "tooltip.kingdomomnitrix.suit.bonus.infernox";
		};
	}

	/** Name der Leerlauf-Animation in animations/armor/&lt;id&gt;.animation.json. */
	public String idleAnimation() {
		return "animation.kingdomomnitrix.armor." + id + ".idle";
	}
}
