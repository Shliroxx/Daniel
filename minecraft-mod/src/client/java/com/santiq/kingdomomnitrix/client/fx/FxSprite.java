package com.santiq.kingdomomnitrix.client.fx;

import net.minecraft.util.math.Vec3d;

/** Ein bewegtes Bild der FX-Engine (Flamme, Funke, Rauch, Ring ...). Reine Daten; Bewegung in {@link #tick()}. */
final class FxSprite {
	enum Shape {
		/** zur Kamera gedreht */
		BILLBOARD,
		/** entlang der Bewegung gestreckt (Funken, Splitter) */
		STRETCHED,
		/** waagerecht am Boden (Ringe, Brandflecken) */
		FLAT
	}

	final FxTexture texture;
	final Shape shape;
	double x;
	double y;
	double z;
	double prevX;
	double prevY;
	double prevZ;
	double vx;
	double vy;
	double vz;
	int age;
	final int life;
	final float startSize;
	final float endSize;
	final int color;
	final float alpha;
	float gravity;
	float drag = 0.96f;
	float rotation;
	float spin;
	/** Bild-Index fest (statt Animation nach Alter); -1 = animieren */
	int frame = -1;
	/** Entitaet, der das Bild folgt (-1 = keine), und Abstand dazu */
	int follow = -1;
	double followX;
	double followY;
	double followZ;
	/** Stauchung fuer gestreckte Bilder (Laenge = Tempo x stretch) */
	float stretch = 3.0f;

	FxSprite(FxTexture texture, Shape shape, Vec3d pos, Vec3d velocity, int life, float startSize, float endSize, int color, float alpha) {
		this.texture = texture;
		this.shape = shape;
		this.x = pos.x;
		this.y = pos.y;
		this.z = pos.z;
		this.prevX = x;
		this.prevY = y;
		this.prevZ = z;
		this.vx = velocity.x;
		this.vy = velocity.y;
		this.vz = velocity.z;
		this.life = Math.max(1, life);
		this.startSize = startSize;
		this.endSize = endSize;
		this.color = color;
		this.alpha = alpha;
	}

	/** @return false, wenn das Bild abgelaufen ist */
	boolean tick() {
		prevX = x;
		prevY = y;
		prevZ = z;
		x += vx;
		y += vy;
		z += vz;
		vx *= drag;
		vy = vy * drag - gravity;
		vz *= drag;
		rotation += spin;
		return ++age < life;
	}

	float progress(float tickDelta) {
		return Math.min(1.0f, (age + tickDelta) / life);
	}

	float size(float tickDelta) {
		float t = progress(tickDelta);
		return startSize + (endSize - startSize) * t;
	}

	/** Deckkraft: kurz einblenden, gegen Ende ausblenden. */
	float fade(float tickDelta) {
		float t = progress(tickDelta);
		float in = Math.min(1.0f, t * 8.0f);
		float out = t < 0.6f ? 1.0f : 1.0f - (t - 0.6f) / 0.4f;
		return alpha * in * Math.max(0.0f, out);
	}
}
