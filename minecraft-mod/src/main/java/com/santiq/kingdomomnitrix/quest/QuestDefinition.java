package com.santiq.kingdomomnitrix.quest;

import com.santiq.kingdomomnitrix.player.HeroData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

/**
 * Eine Quest aus {@code data/<namespace>/kingdomomnitrix/quest/<name>.json}.
 * Aufgebaut wie ein Auftrag eines NPCs: Auftraggeber mit Symbol, Dialog beim Annehmen und Abschliessen,
 * Ziele (toeten, herstellen, sammeln/abgeben) und Belohnung (Bolts, Helden-EP, Items).
 */
public record QuestDefinition(Text title, Category category, int sortOrder, Giver giver, Dialog dialog,
		List<Identifier> requires, int minLevel, List<Objective> objectives, Rewards rewards, boolean repeatable) {

	public static final int MAX_OBJECTIVES = 6;

	public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.<QuestDefinition>create(instance -> instance.group(
			TextCodecs.CODEC.fieldOf("title").forGetter(QuestDefinition::title),
			Category.CODEC.optionalFieldOf("category", Category.SIDE).forGetter(QuestDefinition::category),
			Codec.INT.optionalFieldOf("sort_order", 0).forGetter(QuestDefinition::sortOrder),
			Giver.CODEC.fieldOf("giver").forGetter(QuestDefinition::giver),
			Dialog.CODEC.fieldOf("dialog").forGetter(QuestDefinition::dialog),
			Identifier.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(QuestDefinition::requires),
			Codec.intRange(1, HeroData.MAX_LEVEL).optionalFieldOf("min_level", 1).forGetter(QuestDefinition::minLevel),
			Objective.CODEC.listOf().fieldOf("objectives").forGetter(QuestDefinition::objectives),
			Rewards.CODEC.optionalFieldOf("rewards", Rewards.NONE).forGetter(QuestDefinition::rewards),
			Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(QuestDefinition::repeatable)
	).apply(instance, QuestDefinition::new)).validate(QuestDefinition::validate);

	private static DataResult<QuestDefinition> validate(QuestDefinition quest) {
		if (quest.objectives.isEmpty() || quest.objectives.size() > MAX_OBJECTIVES) {
			return DataResult.error(() -> "Eine Quest braucht 1 bis " + MAX_OBJECTIVES + " Ziele");
		}
		return DataResult.success(quest);
	}

	public enum Category implements StringIdentifiable {
		STORY, SIDE, BOUNTY;

		public static final Codec<Category> CODEC = StringIdentifiable.createCodec(Category::values);

		@Override
		public String asString() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String translationKey() {
			return "quest.kingdomomnitrix.category." + asString();
		}
	}

	/** Auftraggeber: Name, ein Item als Portraet und optional der NPC, der die Quest auch persoenlich vergibt. */
	public record Giver(Text name, Identifier icon, java.util.Optional<Identifier> npc) {
		public static final Codec<Giver> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				TextCodecs.CODEC.fieldOf("name").forGetter(Giver::name),
				Identifier.CODEC.optionalFieldOf("icon", Identifier.ofVanilla("book")).forGetter(Giver::icon),
				Identifier.CODEC.optionalFieldOf("npc").forGetter(Giver::npc)
		).apply(instance, Giver::new));

		public ItemStack iconStack() {
			Item item = Registries.ITEM.get(icon);
			return new ItemStack(item);
		}
	}

	/** Was der Auftraggeber sagt: beim Anbieten, waehrend die Quest laeuft und beim Abschluss. */
	public record Dialog(List<Text> offer, List<Text> progress, List<Text> complete) {
		public static final Codec<Dialog> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				TextCodecs.CODEC.listOf().fieldOf("offer").forGetter(Dialog::offer),
				TextCodecs.CODEC.listOf().optionalFieldOf("progress", List.of()).forGetter(Dialog::progress),
				TextCodecs.CODEC.listOf().optionalFieldOf("complete", List.of()).forGetter(Dialog::complete)
		).apply(instance, Dialog::new));
	}

	public enum ObjectiveType implements StringIdentifiable {
		/** Gegner besiegen: {@code target} ist eine Entity-ID oder ein Entity-Tag ({@code #ns:tag}). */
		KILL,
		/** Item herstellen (Werkbank, Ofen, Schmiede …): {@code target} ist eine Item-ID oder ein Item-Tag. */
		CRAFT,
		/** Items im Inventar haben und beim Abschluss abgeben: {@code target} ist eine Item-ID oder ein Item-Tag. */
		COLLECT;

		public static final Codec<ObjectiveType> CODEC = StringIdentifiable.createCodec(ObjectiveType::values);

		@Override
		public String asString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Ein Quest-Ziel. Fortschritt fuer KILL/CRAFT wird gezaehlt, COLLECT wird live aus dem Inventar gelesen. */
	public record Objective(ObjectiveType type, String target, int count) {
		public static final Codec<Objective> CODEC = RecordCodecBuilder.<Objective>create(instance -> instance.group(
				ObjectiveType.CODEC.fieldOf("type").forGetter(Objective::type),
				Codec.STRING.fieldOf("target").forGetter(Objective::target),
				Codec.intRange(1, 10_000).optionalFieldOf("count", 1).forGetter(Objective::count)
		).apply(instance, Objective::new)).validate(Objective::validate);

		private static DataResult<Objective> validate(Objective objective) {
			String id = objective.isTag() ? objective.target.substring(1) : objective.target;
			return Identifier.validate(id).map(ignored -> objective);
		}

		public boolean isTag() {
			return target.startsWith("#");
		}

		public Identifier targetId() {
			return Identifier.of(isTag() ? target.substring(1) : target);
		}

		public boolean matches(EntityType<?> type) {
			if (this.type != ObjectiveType.KILL) {
				return false;
			}
			return isTag() ? type.isIn(TagKey.of(RegistryKeys.ENTITY_TYPE, targetId()))
					: Registries.ENTITY_TYPE.getId(type).equals(targetId());
		}

		public boolean matches(ItemStack stack) {
			if (this.type == ObjectiveType.KILL || stack.isEmpty()) {
				return false;
			}
			return isTag() ? stack.isIn(TagKey.of(RegistryKeys.ITEM, targetId()))
					: Registries.ITEM.getId(stack.getItem()).equals(targetId());
		}

		/** Anzeigename des Ziels (Entity- bzw. Item-Name, bei Tags die Tag-ID). */
		public Text targetName() {
			if (isTag()) {
				// Tag-Namen nach Vanilla-Konvention: tag.<registry>.<namespace>.<pfad>
				String registry = type == ObjectiveType.KILL ? "entity_type" : "item";
				return Text.translatableWithFallback("tag." + registry + "." + targetId().getNamespace() + "." + targetId().getPath().replace('/', '.'),
						"#" + targetId());
			}
			if (type == ObjectiveType.KILL) {
				return Registries.ENTITY_TYPE.getOrEmpty(targetId()).map(EntityType::getName).orElse(Text.literal(target));
			}
			return Registries.ITEM.getOrEmpty(targetId()).map(item -> item.getName()).orElse(Text.literal(target));
		}

		public Text describe() {
			return Text.translatable("quest.kingdomomnitrix.objective." + type.asString(), targetName());
		}
	}

	public record Rewards(int bolts, int experience, List<ItemStack> items) {
		public static final Rewards NONE = new Rewards(0, 0, List.of());

		public static final Codec<Rewards> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.intRange(0, 1_000_000).optionalFieldOf("bolts", 0).forGetter(Rewards::bolts),
				Codec.intRange(0, 1_000_000).optionalFieldOf("experience", 0).forGetter(Rewards::experience),
				ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Rewards::items)
		).apply(instance, Rewards::new));
	}
}
