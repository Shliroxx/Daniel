package com.santiq.kingdomomnitrix.world.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.TransparentBlock;

/** Panzerglas: durchsichtig mit Metallrahmen; benachbarte Scheiben verdecken sich nicht. */
public class TechGlassBlock extends TransparentBlock {
	public static final MapCodec<TechGlassBlock> CODEC = createCodec(TechGlassBlock::new);

	public TechGlassBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends TransparentBlock> getCodec() {
		return CODEC;
	}
}
