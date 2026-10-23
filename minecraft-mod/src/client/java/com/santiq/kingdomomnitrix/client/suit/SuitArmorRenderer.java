package com.santiq.kingdomomnitrix.client.suit;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.suit.SuitArmorItem;
import com.santiq.kingdomomnitrix.suit.SuitSet;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 3D-Renderer der Endgame-Ruestungen: Modell, Textur und Animation aus geo/armor, textures/armor und animations/armor;
 * die _glowmask-Textur leuchtet im Dunkeln (Augen, Kerne, Risse, Sterne).
 */
public class SuitArmorRenderer extends GeoArmorRenderer<SuitArmorItem> {
	public SuitArmorRenderer(SuitSet set) {
		super(new Model(set));
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	/** Haengt den Renderer an die Ruestungsteile (einmal beim Client-Start). */
	public static void install() {
		SuitArmorItem.RENDERER = item -> new GeoRenderProvider() {
			private SuitArmorRenderer renderer;

			@Override
			public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
					@Nullable EquipmentSlot slot, @Nullable BipedEntityModel<T> original) {
				if (renderer == null) {
					renderer = new SuitArmorRenderer(item.set());
				}
				return renderer;
			}
		};
	}

	/** Pfade nach dem Schema geo/armor/&lt;set&gt;.geo.json usw. */
	private static final class Model extends DefaultedGeoModel<SuitArmorItem> {
		Model(SuitSet set) {
			super(KingdomOmnitrix.id(set.id()));
		}

		@Override
		protected String subtype() {
			return "armor";
		}
	}
}
