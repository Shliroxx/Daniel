package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.StringIdentifiable;

/**
 * Flugart eines Aliens (Datenfeld {@code traits.flight_style}) — Flug wie in Alien Evolution und den Superhelden-Mods:
 * Fahrt in Blickrichtung (auch steil hoch und runter), Sprinten = Schub, ohne Eingabe Schweben. Die Werte gelten je
 * Tick in Bloecken; Schub ist bewusst weit ueber dem Kreativflug (0,55 mit Sprinten).
 *
 * <ul>
 *   <li>{@code cruise} — Reisetempo ohne Sprinten</li>
 *   <li>{@code boost} — Hoechsttempo mit Sprinten</li>
 *   <li>{@code accel} — Anteil, um den die Fahrt je Tick dem Ziel folgt (klein = viel Schwung, Jetray)</li>
 *   <li>{@code vertical} — Steigen/Sinken mit Sprung- bzw. Schleichtaste</li>
 *   <li>{@code drift} — Restfahrt je Tick ohne Eingabe (Geister gleiten lange aus)</li>
 *   <li>{@code sonicBoom} — beim Erreichen des Hoechsttempos Ueberschallknall</li>
 * </ul>
 */
public enum FlightStyle implements StringIdentifiable {
	/** Insektenfluegel (Stinkfly): wendig, steht in der Luft, mittleres Tempo */
	WINGS(0.75, 1.7, 0.28, 0.6, 0.55, false, ParticleTypes.SPORE_BLOSSOM_AIR, 0x7FD43A),
	/** Duesenflug (Jetray): sehr schnell, viel Schwung, Ueberschallknall */
	JET(0.95, 3.0, 0.11, 0.65, 0.8, true, ParticleTypes.ELECTRIC_SPARK, 0xE0403A),
	/** Frostschwingen (Big Chill): ruhig und schnell, Schneeschleier */
	ICE(0.8, 2.1, 0.2, 0.6, 0.65, false, ParticleTypes.SNOWFLAKE, 0x8FE3FF),
	/** Schweben (Ghostfreak): langsam, gleitet lange aus */
	GHOST(0.55, 1.3, 0.09, 0.45, 0.9, false, ParticleTypes.SOUL, 0xB49BE0),
	/** Feuerstrahl (Heatblast): schnell, Flammenschweif */
	FLAME(0.85, 2.4, 0.16, 0.65, 0.7, true, ParticleTypes.FLAME, 0xFF8A1E),
	/** Energiefeld (Chromastone, Atomix): gleichmaessig, Leuchtspur */
	ENERGY(0.8, 2.2, 0.2, 0.6, 0.65, true, ParticleTypes.END_ROD, 0x9CFF6A),
	/** Kosmisch (Alien X): am schnellsten, fast ohne Traegheit */
	COSMIC(1.1, 3.4, 0.32, 0.8, 0.5, true, ParticleTypes.END_ROD, 0xFFFFFF);

	public static final Codec<FlightStyle> CODEC = StringIdentifiable.createCodec(FlightStyle::values);
	/** ab diesem Anteil des Hoechsttempos gilt der Flug als Schub (Haltung waagerecht, Knall, Schweif) */
	public static final double BOOST_SHARE = 0.85;

	private final double cruise;
	private final double boost;
	private final double accel;
	private final double vertical;
	private final double drift;
	private final boolean sonicBoom;
	private final ParticleEffect trail;
	private final int color;

	FlightStyle(double cruise, double boost, double accel, double vertical, double drift, boolean sonicBoom, ParticleEffect trail,
			int color) {
		this.cruise = cruise;
		this.boost = boost;
		this.accel = accel;
		this.vertical = vertical;
		this.drift = drift;
		this.sonicBoom = sonicBoom;
		this.trail = trail;
		this.color = color;
	}

	public double cruise() {
		return cruise;
	}

	public double boost() {
		return boost;
	}

	public double accel() {
		return accel;
	}

	public double vertical() {
		return vertical;
	}

	public double drift() {
		return drift;
	}

	public boolean sonicBoom() {
		return sonicBoom;
	}

	public ParticleEffect trail() {
		return trail;
	}

	public int color() {
		return color;
	}

	/** Flugart des Aliens, in das der Spieler gerade verwandelt ist (Client und Server; Registry ist synchron). */
	public static Optional<FlightStyle> of(PlayerEntity player) {
		// ein Modus kann fliegen lassen (Grey Matters Jetpack), auch wenn das Alien sonst nicht fliegt
		Optional<FlightStyle> mode = AlienModes.id(player).isEmpty() ? Optional.empty() : AlienModes.active(player).flatMap(AlienMode::flight);
		if (mode.isPresent()) {
			return mode;
		}
		return TransformationManager.get(player).activeAlien()
				.flatMap(id -> AlienRegistry.get(player.getWorld().getRegistryManager(), id))
				.map(AlienDefinition::traits)
				.filter(AlienTraits::flight)
				.map(AlienTraits::flightStyle);
	}

	@Override
	public String asString() {
		return name().toLowerCase(Locale.ROOT);
	}
}
