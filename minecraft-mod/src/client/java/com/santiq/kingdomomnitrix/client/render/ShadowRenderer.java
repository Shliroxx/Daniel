package com.santiq.kingdomomnitrix.client.render;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.Identifier;

/** Zombie-Modell mit eigener Herzlosen-Textur (schwarz, gelbe Augen). */
public class ShadowRenderer extends ZombieEntityRenderer {
	private static final Identifier TEXTURE = KingdomOmnitrix.id("textures/entity/shadow.png");

	public ShadowRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ZombieEntity entity) {
		return TEXTURE;
	}
}
