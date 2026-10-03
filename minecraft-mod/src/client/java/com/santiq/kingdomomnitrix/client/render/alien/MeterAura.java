package com.santiq.kingdomomnitrix.client.render.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.alien.AlienMeters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

/**
 * Die Alien-Anzeigen (XLR8-Tempo, Heatblast-Kernhitze, Vierarm-Wut, Diamondhead-Resonanz) werden allein ueber die Aura gezeigt — kein HUD.
 * Hier steht, wie die Aura bei einem Wert aussieht; gezeichnet wird sie am Koerper ({@link MeterAuraLayer}) und in der
 * Ich-Perspektive an den Armen (PlayerEntityRendererMixin).
 *
 * <ul>
 *   <li>ab {@link #MIN} %: eine enge Huelle, die mit dem Wert heller, groesser und schneller wird</li>
 *   <li>ab 50 % (erste Stufe): zweite, weitere Huelle</li>
 *   <li>bei 100 %: Farbwechsel (XLR8 weiss-blau, Heatblast blau, Vierarm gluehend gelb, Diamondhead weiss-gruen) und Pulsieren</li>
 * </ul>
 */
public final class MeterAura {
	/** eigene Graustufen-Textur (tools/generate_aura_texture.py) — die Farbe kommt allein aus der Vertex-Farbe */
	static final Identifier SWIRL = KingdomOmnitrix.id("textures/entity/aura_swirl.png");
	/** ab diesem Wert (Prozent) erscheint die Aura */
	private static final float MIN = 5.0f;
	private static final float STAGE = 50.0f;
	/** Farbe je Anzeige: normal, bei 100 % */
	private static final int[][] COLORS = {
			{0x1E6FFF, 0x7FF4FF},
			{0xFF5A10, 0x7FE9FF},
			{0xD81E1E, 0xFFB040},
			{0x2ECC71, 0xD8FFF0},
			{0x39FF14, 0x00E5FF}};

	/** Eine Huelle: Zeichen-Schicht (mit wanderndem Muster), Vergroesserung, additive Farbe. */
	public record Shell(RenderLayer layer, float scale, int color) {
	}

	private MeterAura() {
	}

	/** Huellen fuer diesen Spieler in diesem Bild (leer = keine Aura). */
	public static List<Shell> shells(AbstractClientPlayerEntity player, float tickDelta) {
		Optional<Integer> meter = AlienMeters.meterOf(player);
		if (meter.isEmpty()) {
			return List.of();
		}
		int m = meter.get();
		float value = AlienMeters.value(player.getId(), m, tickDelta);
		if (value < MIN) {
			return List.of();
		}
		float t = MathHelper.clamp((value - MIN) / (100.0f - MIN), 0.0f, 1.0f);
		float age = player.age + tickDelta;
		boolean max = value >= 99.5f;
		// ruhiges Atmen, bei 100 % schnelles Pulsieren
		float pulse = max ? 0.7f + 0.3f * MathHelper.sin(age * 0.6f) : 0.9f + 0.1f * MathHelper.sin(age * 0.15f);
		int rgb = max ? COLORS[m][1] : COLORS[m][0];
		List<Shell> shells = new ArrayList<>(2);
		shells.add(new Shell(layer(age, 0.01f + 0.025f * t), 1.03f + 0.06f * t, scaled(rgb, (0.3f + 0.7f * t) * pulse)));
		if (value >= STAGE) {
			shells.add(new Shell(layer(age * 1.7f, 0.015f + 0.035f * t), 1.12f + 0.08f * t, scaled(rgb, 0.4f * t * pulse)));
		}
		return shells;
	}

	/**
	 * Ich-Perspektive: Leuchten am Bildrand in Aura-Farbe (ARGB, Alpha = Staerke; 0 = keins). Ersetzt die Huelle,
	 * die man um sich selbst nicht sieht — staerker je hoeher der Wert, bei 100 % pulsierend.
	 */
	public static int edgeColor(AbstractClientPlayerEntity player, float tickDelta) {
		Optional<Integer> meter = AlienMeters.meterOf(player);
		if (meter.isEmpty()) {
			return 0;
		}
		int m = meter.get();
		float value = AlienMeters.value(player.getId(), m, tickDelta);
		if (value < MIN) {
			return 0;
		}
		float t = MathHelper.clamp((value - MIN) / (100.0f - MIN), 0.0f, 1.0f);
		float age = player.age + tickDelta;
		boolean max = value >= 99.5f;
		float pulse = max ? 0.75f + 0.25f * MathHelper.sin(age * 0.6f) : 0.85f + 0.15f * MathHelper.sin(age * 0.15f);
		int alpha = (int) (110 * t * t * pulse + (value >= STAGE ? 25 : 0));
		return ColorHelper.Argb.withAlpha(MathHelper.clamp(alpha, 0, 160), max ? COLORS[m][1] : COLORS[m][0]);
	}

	private static RenderLayer layer(float age, float speed) {
		return RenderLayer.getEnergySwirl(SWIRL, (age * speed) % 1.0f, (age * speed * 0.8f) % 1.0f);
	}

	/** Energie-Muster wird additiv gemischt: Helligkeit ueber die Farbe, Alpha voll. */
	private static int scaled(int rgb, float brightness) {
		float b = MathHelper.clamp(brightness, 0.0f, 1.0f);
		return ColorHelper.Argb.getArgb(255, (int) (((rgb >> 16) & 0xFF) * b), (int) (((rgb >> 8) & 0xFF) * b), (int) ((rgb & 0xFF) * b));
	}
}
