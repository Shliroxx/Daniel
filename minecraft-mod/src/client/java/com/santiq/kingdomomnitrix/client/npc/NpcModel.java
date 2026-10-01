package com.santiq.kingdomomnitrix.client.npc;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.npc.NpcEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Waehlt Modell, Textur und Animation je NPC-ID (entity/npc/<model>) und dreht den Kopf zum Blickziel.
 * GeckoLib 4.9 markiert die Ein-Parameter-Varianten als veraltet, verlangt sie aber weiterhin als abstrakte Methoden.
 */
@SuppressWarnings("deprecation")
public class NpcModel extends GeoModel<NpcEntity> {
	private static final String FALLBACK = "yen_sid";

	private static String model(NpcEntity npc) {
		Identifier id = npc.npcId();
		if (id == null) {
			return FALLBACK;
		}
		return npc.definition().map(def -> def.modelPath(id)).orElse(id.getPath());
	}

	@Override
	public Identifier getModelResource(NpcEntity npc) {
		return KingdomOmnitrix.id("geo/entity/npc/" + model(npc) + ".geo.json");
	}

	@Override
	public Identifier getTextureResource(NpcEntity npc) {
		return KingdomOmnitrix.id("textures/entity/npc/" + model(npc) + ".png");
	}

	@Override
	public Identifier getAnimationResource(NpcEntity npc) {
		return KingdomOmnitrix.id("animations/entity/npc/" + model(npc) + ".animation.json");
	}

	@Override
	public void setCustomAnimations(NpcEntity npc, long instanceId, AnimationState<NpcEntity> state) {
		GeoBone head = getAnimationProcessor().getBone("head");
		EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
		if (head != null && data != null) {
			head.setRotX(data.headPitch() * MathHelper.RADIANS_PER_DEGREE);
			head.setRotY(data.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);
		}
	}
}
