package com.santiq.kingdomomnitrix.client.omnitrix;

import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.networking.TransformRequestPayload;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixStatus;
import com.santiq.kingdomomnitrix.omnitrix.ScanRule;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Smart-Wahl in zwei Schritten — das Omnitrix verwandelt nie von selbst:
 * <ol>
 * <li>Taste: Smart-Scan neu, Hologramm „SMART CHOICE · Alien · EMPFOHLEN · Grund · [N] bestaetigen“.</li>
 * <li>Taste erneut innerhalb von {@link #CONFIRM_MS}: genau das angezeigte Alien anfordern (Server prueft alle Regeln).</li>
 * </ol>
 * Aus dem Schnellwahl-Kreis ist die Empfehlung schon sichtbar; dort bestaetigt ein Druck direkt.
 */
public final class SmartChoice {
	private static final long CONFIRM_MS = 4000L;
	private static final int COLOR = 0xFFD84A;

	private static Identifier pending;
	private static OmnitrixHolo.Message prompt;

	private SmartChoice() {
	}

	/** Taste gedrueckt (ausserhalb des Kreises). */
	public static void press(MinecraftClient client, KeyBinding key) {
		if (client.player == null) {
			return;
		}
		if (pending != null && prompt != null && OmnitrixHolo.isShowing(prompt)) {
			confirm(client, pending);
			return;
		}
		Optional<ScanRule.Recommendation> recommendation = SmartScan.rescan();
		if (recommendation.isEmpty()) {
			pending = null;
			OmnitrixHolo.show(Text.translatable("holo.kingdomomnitrix.smart_choice"), Text.translatable("scan.kingdomomnitrix.none"),
					0x9ACFA8, 1600L);
			OmnitrixFeedback.play(OmnitrixCue.ERROR);
			return;
		}
		ScanRule.Recommendation pick = recommendation.get();
		pending = pick.alien();
		int color = client.world == null ? COLOR
				: AlienRegistry.get(client.world.getRegistryManager(), pick.alien()).map(def -> def.color()).orElse(COLOR);
		prompt = new OmnitrixHolo.Message(Text.translatable("holo.kingdomomnitrix.smart_choice"),
				TransformationManager.alienName(pick.alien()).withColor(brighten(color)).formatted(Formatting.BOLD)
						.append(Text.literal("  ")).append(Text.translatable("holo.kingdomomnitrix.recommended",
								Text.translatable(pick.reason())).formatted(Formatting.GRAY)),
				Optional.of(Text.translatable("holo.kingdomomnitrix.confirm", key.getBoundKeyLocalizedText())),
				Optional.of(pick.alien()), COLOR, CONFIRM_MS);
		OmnitrixHolo.show(prompt);
		OmnitrixFeedback.play(OmnitrixCue.SELECT);
	}

	/** Bestaetigt ein Alien sofort (Schnellwahl-Kreis: Empfehlung war bereits sichtbar). */
	public static void confirm(MinecraftClient client, Identifier alien) {
		pending = null;
		prompt = null;
		OmnitrixHolo.clear();
		if (client.player == null) {
			return;
		}
		OmnitrixStatus status = OmnitrixClientState.status(client.player);
		if (status == OmnitrixStatus.LOCKED || status == OmnitrixStatus.OVERHEATED) {
			OmnitrixFeedback.play(OmnitrixCue.ERROR);
			return;
		}
		ClientPlayNetworking.send(new TransformRequestPayload(alien));
		OmnitrixFeedback.play(OmnitrixCue.CONFIRM);
	}

	/** Dunkle Alien-Farben auf dem dunklen Hologramm lesbar machen. */
	private static int brighten(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int max = Math.max(r, Math.max(g, b));
		if (max >= 0xB0 || max == 0) {
			return max == 0 ? 0xB0B0B0 : rgb & 0xFFFFFF;
		}
		float factor = 0xB0 / (float) max;
		return Math.round(r * factor) << 16 | Math.round(g * factor) << 8 | Math.round(b * factor);
	}
}
