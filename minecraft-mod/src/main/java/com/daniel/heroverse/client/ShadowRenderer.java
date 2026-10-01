package com.daniel.heroverse.client;

import com.daniel.heroverse.Heroverse;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.Identifier;

/** Zombie-Modell mit eigener Herzlosen-Textur (schwarz, gelbe Augen). */
public class ShadowRenderer extends ZombieEntityRenderer {
	private static final Identifier TEXTURE = Heroverse.id("textures/entity/shadow.png");

	public ShadowRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ZombieEntity entity) {
		return TEXTURE;
	}
}
