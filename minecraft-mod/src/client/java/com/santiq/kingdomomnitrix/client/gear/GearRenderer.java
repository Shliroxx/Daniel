package com.santiq.kingdomomnitrix.client.gear;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.gadget.HeliPackItem;
import com.santiq.kingdomomnitrix.gear.GeoGear;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import com.santiq.kingdomomnitrix.client.render.SafeGlowLayer;

/**
 * 3D-Renderer der aelteren Ausruestung ({@link GeoGear}): Modell, Textur und Animation aus geo|textures|animations/
 * item/gear, die _glowmask leuchtet. Im Inventar zeichnet er das gemalte Symbol (models/item/&lt;id&gt;_icon). Clanks
 * Rucksack zeigt das Modell seines Modus (siehe PackMode).
 */
public class GearRenderer<T extends Item & GeoGear> extends GeoItemRenderer<T> {
	/** Ausruestung mit eigenem 3D-Modell; jede braucht models/item/&lt;id&gt;_icon fuer das Inventar */
	private static final List<String> ICONS = List.of("kingdom_key", "oathkeeper", "omega_key", "omniwrench", "combuster", "swingshot",
			"fusion_grenade", "heli_pack", "heli_jet", "glider_pack", "jet_pack", "blitz_gun", "pyrocitor", "devastator", "morph_o_ray");

	public GearRenderer() {
		super(new Model<>());
		addRenderLayer(new SafeGlowLayer<>(this));
	}

	private static Identifier icon(String id) {
		return KingdomOmnitrix.id("item/" + id + "_icon");
	}

	/** Gemeinsamer Variantenname: Clanks Rucksack nutzt die Dateien seines Modus (heli_pack, heli_jet, glider_pack, jet_pack). */
	static String variant(String id, ItemStack stack) {
		return stack.isOf(ModItems.HELI_PACK) ? HeliPackItem.mode(stack).gear() : id;
	}

	/** Haengt den Renderer an die Ausruestung und laedt die Symbol-Modelle mit (einmal beim Client-Start). */
	public static void install() {
		ModelLoadingPlugin.register(context -> {
			for (String id : ICONS) {
				context.addModels(icon(id));
			}
		});
		GeoGear.Hooks.renderer = item -> new GeoRenderProvider() {
			private GearRenderer<?> renderer;

			@Override
			public BuiltinModelItemRenderer getGeoItemRenderer() {
				if (renderer == null) {
					renderer = new GearRenderer<>();
				}
				return renderer;
			}
		};
	}

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light,
			int overlay) {
		if (mode == ModelTransformationMode.GUI && stack.getItem() instanceof GeoGear gear) {
			MinecraftClient client = MinecraftClient.getInstance();
			BakedModel model = client.getBakedModelManager().getModel(icon(variant(gear.gearId(), stack)));
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

	/** Pfade nach dem Schema geo/item/gear/&lt;id&gt;.geo.json; die Variante haengt am gerade gezeichneten Stack. */
	private static final class Model<T extends Item & GeoGear> extends GeoModel<T> {
		private String name(T animatable, GeoRenderer<T> renderer) {
			ItemStack stack = renderer instanceof GeoItemRenderer<T> items ? items.getCurrentItemStack() : null;
			return stack == null ? animatable.gearId() : variant(animatable.gearId(), stack);
		}

		@Override
		public Identifier getModelResource(T animatable, GeoRenderer<T> renderer) {
			return KingdomOmnitrix.id("geo/item/gear/" + name(animatable, renderer) + ".geo.json");
		}

		@Override
		public Identifier getTextureResource(T animatable, GeoRenderer<T> renderer) {
			return KingdomOmnitrix.id("textures/item/gear/" + name(animatable, renderer) + ".png");
		}

		@Override
		public Identifier getModelResource(T animatable) {
			return KingdomOmnitrix.id("geo/item/gear/" + animatable.gearId() + ".geo.json");
		}

		@Override
		public Identifier getTextureResource(T animatable) {
			return KingdomOmnitrix.id("textures/item/gear/" + animatable.gearId() + ".png");
		}

		@Override
		public Identifier getAnimationResource(T animatable) {
			return KingdomOmnitrix.id("animations/item/gear/" + animatable.gearId() + ".animation.json");
		}
	}
}
