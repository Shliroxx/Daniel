package com.santiq.kingdomomnitrix.client.vfx;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;

/**
 * Gemeinsamer Partikel fuer alle Mod-Effekte. Verhalten pro Typ ueber {@link Style}: Lebensdauer, Groesse,
 * Wachsen/Schrumpfen, Schwerkraft, Bremsung, Drehung, Leuchten und ob die Bilder als Animation ablaufen.
 */
public class HeroParticle extends SpriteBillboardParticle {
	/**
	 * @param life      Lebensdauer in Ticks (± 25 % Zufall)
	 * @param size      Startgroesse in Bloecken
	 * @param endSize   Faktor am Lebensende (1 = gleich, 2 = doppelt so gross)
	 * @param gravity   Schwerkraft (negativ = steigt auf)
	 * @param friction  Geschwindigkeits-Faktor pro Tick
	 * @param spin      maximale Drehung pro Tick (Bogenmass)
	 * @param glow      volle Helligkeit auch im Dunkeln
	 * @param animate   Bilder nach Alter abspielen statt zufaellig waehlen
	 */
	public record Style(int life, float size, float endSize, float gravity, float friction, float spin, boolean glow, boolean animate) {
	}

	private final SpriteProvider sprites;
	private final Style style;
	private final float startScale;
	private final float spinSpeed;

	protected HeroParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
			SpriteProvider sprites, Style style) {
		super(world, x, y, z, vx, vy, vz);
		this.sprites = sprites;
		this.style = style;
		// eigene Geschwindigkeit uebernehmen (der Basiskonstruktor wuerfelt sonst eine dazu)
		this.velocityX = vx;
		this.velocityY = vy;
		this.velocityZ = vz;
		this.maxAge = Math.max(2, Math.round(style.life() * (0.75f + random.nextFloat() * 0.5f)));
		this.startScale = style.size() * (0.8f + random.nextFloat() * 0.4f);
		this.scale = startScale;
		this.gravityStrength = style.gravity();
		this.velocityMultiplier = style.friction();
		this.spinSpeed = style.spin() * (random.nextFloat() * 2.0f - 1.0f);
		this.angle = random.nextFloat() * MathHelper.TAU * (style.spin() > 0 ? 1 : 0);
		this.prevAngle = angle;
		this.collidesWithWorld = false;
		if (style.animate()) {
			setSpriteForAge(sprites);
		} else {
			setSprite(sprites);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (dead) {
			return;
		}
		float progress = (float) age / maxAge;
		scale = startScale * MathHelper.lerp(progress, 1.0f, style.endSize());
		// im letzten Drittel ausblenden
		alpha = progress < 0.66f ? 1.0f : Math.max(0.0f, 1.0f - (progress - 0.66f) / 0.34f);
		prevAngle = angle;
		angle += spinSpeed;
		if (style.animate()) {
			setSpriteForAge(sprites);
		}
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	protected int getBrightness(float tint) {
		return style.glow() ? 0xF000F0 : super.getBrightness(tint);
	}

	/** Erzeugt Partikel eines Typs mit festem Stil. */
	public record Factory(SpriteProvider sprites, Style style) implements ParticleFactory<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
				double vx, double vy, double vz) {
			return new HeroParticle(world, x, y, z, vx, vy, vz, sprites, style);
		}
	}
}
