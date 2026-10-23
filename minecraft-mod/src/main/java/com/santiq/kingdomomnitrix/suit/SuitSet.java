package com.santiq.kingdomomnitrix.suit;

import net.minecraft.item.ArmorItem;
import net.minecraft.util.Rarity;

/**
 * Die sechs Endgame-Ruestungen in zwei Linien mit eigenen Staerken (Werte hier, Faehigkeiten folgen):
 * <ul>
 *   <li><b>Ben 10</b> (Taydenit, Galvan-Biotech, Celestialsapien): weniger Grundpanzerung, dafuer grosser Energieschild</li>
 *   <li><b>Ratchet &amp; Clank</b> (Magnaplate, Carbonox, Infernox): hohe Panzerung und Haerte, kleiner Schild</li>
 * </ul>
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

	/** Herkunft der Ruestung; bestimmt spaeter Schild und Boni. */
	public enum Line { BEN10, RATCHET }

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

	/** Name der Leerlauf-Animation in animations/armor/&lt;id&gt;.animation.json. */
	public String idleAnimation() {
		return "animation.kingdomomnitrix.armor." + id + ".idle";
	}
}
