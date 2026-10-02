package com.santiq.kingdomomnitrix.omnitrix;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Zustand des Omnitrix-Geraets eines Spielers (persistent, an alle synchronisiert — andere sehen Warn- und Sperrlicht).
 * Verwandlung, Nachladezeit und Faehigkeiten bleiben im {@code TransformationState}; hier liegt, was das Geraet selbst
 * betrifft.
 *
 * <p>Hitze wird nicht pro Tick hochgezaehlt: gespeichert sind Wert und Zeitpunkt ({@link #heat}, {@link #heatStamp});
 * der aktuelle Wert ergibt sich aus der verstrichenen Zeit ({@link #heatAt}). Bei jedem Wechsel (Verwandeln,
 * Zurueckverwandeln) wird neu festgeschrieben.</p>
 *
 * @param profile        Geraete-Profil ({@link OmnitrixProfile})
 * @param heat           Hitze zum Zeitpunkt {@code heatStamp} (0..1)
 * @param heatStamp      Welt-Tick der letzten Festschreibung
 * @param warned         Warnung fuer diese Hitzephase schon ausgeloest
 * @param overheatedUntil Ueberhitzt bis (Welt-Tick)
 * @param lockedUntil    gesperrt bis (Welt-Tick, z. B. Sicherheitssperre, Lockdown)
 * @param masterControl  Master Control freigeschaltet und aktiv
 */
public record OmnitrixState(Identifier profile, float heat, long heatStamp, boolean warned, long overheatedUntil,
		long lockedUntil, boolean masterControl) {

	public static final Identifier DEFAULT_PROFILE = Identifier.of("kingdomomnitrix", "prototype");
	public static final OmnitrixState EMPTY = new OmnitrixState(DEFAULT_PROFILE, 0.0f, 0L, false, 0L, 0L, false);

	public static final Codec<OmnitrixState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.optionalFieldOf("profile", DEFAULT_PROFILE).forGetter(OmnitrixState::profile),
			Codec.FLOAT.optionalFieldOf("heat", 0.0f).forGetter(OmnitrixState::heat),
			Codec.LONG.optionalFieldOf("heat_stamp", 0L).forGetter(OmnitrixState::heatStamp),
			Codec.BOOL.optionalFieldOf("warned", false).forGetter(OmnitrixState::warned),
			Codec.LONG.optionalFieldOf("overheated_until", 0L).forGetter(OmnitrixState::overheatedUntil),
			Codec.LONG.optionalFieldOf("locked_until", 0L).forGetter(OmnitrixState::lockedUntil),
			Codec.BOOL.optionalFieldOf("master_control", false).forGetter(OmnitrixState::masterControl)
	).apply(instance, OmnitrixState::new));

	public static final PacketCodec<ByteBuf, OmnitrixState> PACKET_CODEC = PacketCodecs.codec(CODEC);

	public OmnitrixState {
		heat = MathHelper.clamp(heat, 0.0f, 1.0f);
	}

	/** Hitze jetzt: als Alien steigt sie, in Menschenform kuehlt das Geraet ab. */
	public float heatAt(long now, boolean transformed, OmnitrixProfile profile) {
		float seconds = Math.max(0L, now - heatStamp) / 20.0f;
		float rate = transformed ? profile.heatPerSecondActive() * heatFactor(profile) : -profile.heatDecayPerSecond();
		return MathHelper.clamp(heat + rate * seconds, 0.0f, 1.0f);
	}

	/** Master Control erzeugt (je nach Profil) weniger oder keine Hitze. */
	public float heatFactor(OmnitrixProfile profile) {
		return masterControl ? profile.masterControl().heatMultiplier() : 1.0f;
	}

	public boolean isOverheated(long now) {
		return overheatedUntil > now;
	}

	public boolean isLocked(long now) {
		return lockedUntil > now;
	}

	/** Hitze zum Zeitpunkt {@code now} festschreiben und um {@code add} aendern. */
	public OmnitrixState withHeat(long now, boolean transformed, OmnitrixProfile profile, float add) {
		float value = MathHelper.clamp(heatAt(now, transformed, profile) + add, 0.0f, 1.0f);
		boolean stillWarned = warned && value >= profile.heatWarning();
		return new OmnitrixState(this.profile, value, now, stillWarned, overheatedUntil, lockedUntil, masterControl);
	}

	public OmnitrixState withWarned(boolean value) {
		return new OmnitrixState(profile, heat, heatStamp, value, overheatedUntil, lockedUntil, masterControl);
	}

	public OmnitrixState withOverheatedUntil(long tick) {
		return new OmnitrixState(profile, heat, heatStamp, warned, tick, lockedUntil, masterControl);
	}

	public OmnitrixState withLockedUntil(long tick) {
		return new OmnitrixState(profile, heat, heatStamp, warned, overheatedUntil, tick, masterControl);
	}

	public OmnitrixState withMasterControl(boolean value) {
		return new OmnitrixState(profile, heat, heatStamp, warned, overheatedUntil, lockedUntil, value);
	}

	public OmnitrixState withProfile(Identifier id) {
		return new OmnitrixState(id, heat, heatStamp, warned, overheatedUntil, lockedUntil, masterControl);
	}
}
