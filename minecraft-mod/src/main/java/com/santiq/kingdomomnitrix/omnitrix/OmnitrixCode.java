package com.santiq.kingdomomnitrix.omnitrix;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

/**
 * Omnitrix-Code (Datenpaket {@code data/<ns>/kingdomomnitrix/omnitrix_code/<id>.json}, <b>nicht</b> an Clients
 * synchronisiert — Codes lassen sich nicht aus dem Client auslesen):
 *
 * <pre>
 * {"code": "4040", "action": "emergency_vent"}
 * {"code": "1010", "action": "recalibrate", "profile": "kingdomomnitrix:recalibrated"}
 * </pre>
 * Nur Ziffern, 3–8 Stellen. Admins koennen Codes aendern oder eigene hinzufuegen.
 */
public record OmnitrixCode(String code, Action action, Optional<Identifier> profile) {
	public static final RegistryKey<Registry<OmnitrixCode>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("omnitrix_code"));
	public static final int MIN_LENGTH = 3;
	public static final int MAX_LENGTH = 8;

	/** Was ein Code ausloest. */
	public enum Action implements StringIdentifiable {
		/** Geraetezustand als Hologramm (Profil, Hitze, Notfall, Master Control). */
		DIAGNOSTICS,
		/** Notkuehlung: Hitze sofort auf 0, Geraet dafuer kurz gesperrt. */
		EMERGENCY_VENT,
		/** Zufallsmodus: Verwandlung in ein zufaelliges freigeschaltetes Alien (alle Geraete-Regeln gelten). */
		RANDOM,
		/** Kalibrierung: wechselt zwischen Prototyp und dem angegebenen Profil. */
		RECALIBRATE,
		/** Master Control an/aus — nur wenn freigeschaltet. */
		MASTER_CONTROL,
		/** Selbstzerstoerung (Countdown, abbrechbar) — nur mit Spielregel. */
		SELF_DESTRUCT;

		public static final Codec<Action> CODEC = StringIdentifiable.createCodec(Action::values);

		@Override
		public String asString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final Codec<String> DIGITS = Codec.STRING.validate(code -> code.length() >= MIN_LENGTH && code.length() <= MAX_LENGTH
			&& code.chars().allMatch(Character::isDigit)
			? DataResult.success(code)
			: DataResult.error(() -> "Code muss " + MIN_LENGTH + "–" + MAX_LENGTH + " Ziffern haben: " + code));

	public static final Codec<OmnitrixCode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			DIGITS.fieldOf("code").forGetter(OmnitrixCode::code),
			Action.CODEC.fieldOf("action").forGetter(OmnitrixCode::action),
			Identifier.CODEC.optionalFieldOf("profile").forGetter(OmnitrixCode::profile)
	).apply(instance, OmnitrixCode::new));

	/** Nur auf dem Server geladen (keine Synchronisation). */
	public static void register() {
		DynamicRegistries.register(KEY, CODEC);
	}

	public static List<OmnitrixCode> all(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.stream().toList()).orElse(List.of());
	}

	public static Optional<OmnitrixCode> find(DynamicRegistryManager manager, String entered) {
		return all(manager).stream().filter(code -> code.code().equals(entered)).findFirst();
	}
}
