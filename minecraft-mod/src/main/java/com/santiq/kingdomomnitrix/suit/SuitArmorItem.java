package com.santiq.kingdomomnitrix.suit;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Ein Teil einer Endgame-Ruestung: echtes 3D-Modell (GeckoLib) mit Leuchtschicht und Leerlauf-Animation
 * (Umhang, Ringe, Rotor, kreisende Kristalle). Der Renderer kommt vom Client ueber {@link #RENDERER} —
 * der gemeinsame Code kennt keine Client-Klassen.
 */
public class SuitArmorItem extends ArmorItem implements GeoItem {
	/** Vom Client gesetzt: baut den Renderer fuer ein Ruestungsteil. Auf dem Server bleibt es leer. */
	@Nullable
	public static Function<SuitArmorItem, GeoRenderProvider> RENDERER;

	private final SuitSet set;
	private final RawAnimation idle;
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public SuitArmorItem(SuitSet set, Type type, Settings settings) {
		super(SuitMaterials.of(set), type, settings);
		this.set = set;
		this.idle = RawAnimation.begin().thenLoop(set.idleAnimation());
	}

	public SuitSet set() {
		return set;
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		if (RENDERER != null) {
			consumer.accept(RENDERER.apply(this));
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(idle)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable(set.tooltipKey()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(set.line() == SuitSet.Line.BEN10 ? "tooltip.kingdomomnitrix.suit.line.ben10"
				: "tooltip.kingdomomnitrix.suit.line.ratchet").formatted(Formatting.DARK_AQUA));
		tooltip.add(Text.translatable("tooltip.kingdomomnitrix.suit.set_bonus").formatted(Formatting.GOLD));
		tooltip.add(Text.translatable(set.bonusKey()).formatted(Formatting.YELLOW));
	}
}
