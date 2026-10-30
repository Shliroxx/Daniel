package com.santiq.kingdomomnitrix.client.fx;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/** Texturen der FX-Engine ({@code tools/generate_fx_textures.py}); Bildbogen mit 4x4 Bildern werden nach Alter abgespielt. */
enum FxTexture {
	GLOW("glow", 1, true),
	FLARE("flare", 1, true),
	RING("ring", 1, true),
	BEAM("beam", 1, true),
	SPARK("spark", 1, true),
	SHARD("shard", 1, true),
	FLAME("flame", 4, false),
	/** Flamme in echten Feuerfarben (fuer warme Farben, ohne Einfaerben) */
	FLAME_HOT("flame_hot", 4, false),
	EXPLOSION("explosion", 4, false),
	/** Feuerball in echten Feuerfarben (Weiss → Gelb → Orange → Glut → Russ) */
	EXPLOSION_HOT("explosion_hot", 4, false),
	SMOKE("smoke", 4, false),
	SCORCH("scorch", 1, false);

	final Identifier id;
	/** Bilder je Zeile/Spalte (1 = Einzelbild, 4 = 16 Bilder) */
	final int grid;
	/** true = additiv leuchtend, false = normal mit Alpha (Rauch, Brandfleck) */
	final boolean additive;

	FxTexture(String name, int grid, boolean additive) {
		this.id = KingdomOmnitrix.id("textures/fx/" + name + ".png");
		this.grid = grid;
		this.additive = additive;
	}

	RenderLayer layer() {
		return additive ? RenderLayer.getEyes(id) : RenderLayer.getEntityTranslucent(id);
	}

	int frames() {
		return grid * grid;
	}
}
