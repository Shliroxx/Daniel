package com.santiq.kingdomomnitrix.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.player.HeroData;
import java.util.Locale;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

/**
 * Eine Helden-Faehigkeit wie in Kingdom Hearts: ab einer Stufe freigeschaltet, kostet beim Ausruesten AP.
 * Name und Beschreibung kommen aus der Sprachdatei: {@code hero_ability.<namespace>.<pfad>} und {@code ….desc}.
 *
 * @param unlockLevel Heldenstufe, ab der die Faehigkeit in der Liste waehlbar ist
 * @param apCost      Kosten in Faehigkeitspunkten
 * @param value       Staerke des Effekts (Bedeutung siehe {@link HeroAbilityEffect})
 */
public record HeroAbilityDefinition(Category category, int unlockLevel, int apCost, int sortOrder, HeroAbilityEffect effect, float value) {
	public static final Codec<HeroAbilityDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Category.CODEC.fieldOf("category").forGetter(HeroAbilityDefinition::category),
			Codec.intRange(HeroData.MIN_LEVEL, HeroData.MAX_LEVEL).optionalFieldOf("unlock_level", 1).forGetter(HeroAbilityDefinition::unlockLevel),
			Codec.intRange(0, 20).fieldOf("ap_cost").forGetter(HeroAbilityDefinition::apCost),
			Codec.INT.optionalFieldOf("sort_order", 0).forGetter(HeroAbilityDefinition::sortOrder),
			HeroAbilityEffect.CODEC.fieldOf("effect").forGetter(HeroAbilityDefinition::effect),
			Codec.floatRange(0.0f, 100.0f).optionalFieldOf("value", 1.0f).forGetter(HeroAbilityDefinition::value)
	).apply(instance, HeroAbilityDefinition::new));

	public static MutableText name(Identifier id) {
		return Text.translatable("hero_ability." + id.getNamespace() + "." + id.getPath());
	}

	public static MutableText description(Identifier id) {
		return Text.translatable("hero_ability." + id.getNamespace() + "." + id.getPath() + ".desc");
	}

	/** Die fuenf Aeste aus der Design-Vorgabe; bestimmt Farbe und Gruppierung in der Liste. */
	public enum Category implements StringIdentifiable {
		COMBAT(0xFFFF6B5A), KEYBLADE(0xFFFFC94A), OMNITRIX(0xFF5CE65C), TECH(0xFF4AB8FF), EXPLORATION(0xFFC48BFF);

		public static final Codec<Category> CODEC = StringIdentifiable.createCodec(Category::values);

		private final int color;

		Category(int color) {
			this.color = color;
		}

		public int color() {
			return color;
		}

		@Override
		public String asString() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String translationKey() {
			return "hero_ability.kingdomomnitrix.category." + asString();
		}
	}
}
