package com.santiq.kingdomomnitrix.client.suit;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.suit.weapon.SuitWeapon;
import com.santiq.kingdomomnitrix.suit.weapon.SuitWeaponItem;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 3D-Renderer der Endgame-Waffen: Modell, Textur und Leerlauf-Animation aus geo/item/weapon, textures/item/weapon und
 * animations/item/weapon, die _glowmask leuchtet. Im Inventar (GUI) zeichnet er stattdessen das gemalte Symbol
 * (models/item/&lt;waffe&gt;_icon), damit die Waffen dort lesbar bleiben wie in den grossen Schusswaffen-Mods.
 */
public class SuitWeaponRenderer extends GeoItemRenderer<SuitWeaponItem> {
	private final Identifier icon;

	public SuitWeaponRenderer(SuitWeapon weapon) {
		super(new Model(weapon));
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
		this.icon = iconModel(weapon);
	}

	private static Identifier iconModel(SuitWeapon weapon) {
		return KingdomOmnitrix.id("item/" + weapon.id() + "_icon");
	}

	/** Haengt den Renderer an die Waffen und laedt die Symbol-Modelle mit (einmal beim Client-Start). */
	public static void install() {
		ModelLoadingPlugin.register(context -> {
			for (SuitWeapon weapon : SuitWeapon.values()) {
				context.addModels(iconModel(weapon));
			}
		});
		SuitWeaponItem.RENDERER = item -> new GeoRenderProvider() {
			private SuitWeaponRenderer renderer;

			@Override
			public BuiltinModelItemRenderer getGeoItemRenderer() {
				if (renderer == null) {
					renderer = new SuitWeaponRenderer(item.weapon());
				}
				return renderer;
			}
		};
	}

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light,
			int overlay) {
		if (mode == ModelTransformationMode.GUI) {
			MinecraftClient client = MinecraftClient.getInstance();
			BakedModel model = client.getBakedModelManager().getModel(icon);
			if (model != null) {
				// Der Item-Renderer hat schon um -0.5 verschoben; renderItem tut das fuer das Symbol noch einmal
				matrices.push();
				matrices.translate(0.5f, 0.5f, 0.5f);
				client.getItemRenderer().renderItem(stack, mode, false, matrices, consumers, light, overlay, model);
				matrices.pop();
				return;
			}
		}
		super.render(stack, mode, matrices, consumers, light, overlay);
	}

	/** Pfade nach dem Schema geo/item/weapon/&lt;waffe&gt;.geo.json usw. */
	private static final class Model extends DefaultedGeoModel<SuitWeaponItem> {
		Model(SuitWeapon weapon) {
			super(KingdomOmnitrix.id("weapon/" + weapon.id()));
		}

		@Override
		protected String subtype() {
			return "item";
		}
	}
}
