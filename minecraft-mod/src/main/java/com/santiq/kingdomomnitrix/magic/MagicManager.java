package com.santiq.kingdomomnitrix.magic;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.keyblade.KeybladeDefinition;
import com.santiq.kingdomomnitrix.keyblade.KeybladeDefinition.Passive;
import com.santiq.kingdomomnitrix.keyblade.KeybladeItem;
import com.santiq.kingdomomnitrix.keyblade.KeybladeRegistry;
import com.santiq.kingdomomnitrix.magic.SpellEffects.SpellContext;
import com.santiq.kingdomomnitrix.magic.SpellEffects.SpellEffect;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Magie im Kingdom-Hearts-Stil: nur mit Keyblade, MP-Leiste, MP-Ladezeit nach dem letzten Tropfen
 * (ein Zauber gelingt, solange noch MP da sind, und leert dann die Leiste),
 * Nahkampftreffer laden MP auf, Zauberstufen ueber Magie-Kristalle.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MagicManager {
	public static final AttachmentType<MagicState> STATE = AttachmentRegistry.create(KingdomOmnitrix.id("magic"), builder -> builder
			.persistent(MagicState.CODEC)
			.initializer(() -> MagicState.DEFAULT)
			.copyOnDeath()
			.syncWith(MagicState.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Dauer der MP-Ladezeit (10 s), verkuerzt durch MP-Eile. */
	public static final int CHARGE_TICKS = 200;
	public static final float BASE_REGEN_PER_SECOND = 2.0f;
	public static final float MP_PER_MELEE_HIT = 2.5f;
	private static final float MIN_MP_TO_CAST = 1.0f;
	/** Magiekraft eines Keyblades pro Punkt in Prozent Zauberstaerke. */
	private static final float MAGIC_PER_POINT = 0.08f;

	public enum Result { SUCCESS, NO_KEYBLADE, NO_SPELLS, UNKNOWN_SPELL, CHARGING, NO_MP, ON_COOLDOWN, FAILED }

	private MagicManager() {
	}

	public static void register() {
		SpellEffects.registerBuiltins();
	}

	public static MagicState get(PlayerEntity player) {
		MagicState state = player.getAttached(STATE);
		return state != null ? state : MagicState.DEFAULT;
	}

	private static void update(ServerPlayerEntity player, UnaryOperator<MagicState> change) {
		MagicState before = get(player);
		MagicState after = change.apply(before);
		if (!after.equals(before)) {
			player.setAttached(STATE, after);
		}
	}

	// --- Keyblade-Werte -------------------------------------------------------------------------

	public static Optional<KeybladeDefinition> heldKeyblade(PlayerEntity player, RegistryWrapper.WrapperLookup registries) {
		ItemStack stack = player.getMainHandStack();
		return stack.getItem() instanceof KeybladeItem ? KeybladeRegistry.forStack(registries, stack) : Optional.empty();
	}

	/** MP-Regeneration pro Sekunde mit dem gerade gehaltenen Keyblade (MP-Eile). */
	public static float regenPerSecond(PlayerEntity player) {
		float haste = heldKeyblade(player, player.getWorld().getRegistryManager()).map(def -> def.passive(Passive.MP_HASTE)).orElse(0.0f);
		return BASE_REGEN_PER_SECOND * (1.0f + haste);
	}

	public static int chargeTicks(PlayerEntity player) {
		float haste = heldKeyblade(player, player.getWorld().getRegistryManager()).map(def -> def.passive(Passive.MP_HASTE)).orElse(0.0f);
		return Math.max(40, Math.round(CHARGE_TICKS / (1.0f + haste)));
	}

	private static float magicFactor(ServerPlayerEntity player, KeybladeDefinition keyblade) {
		float magic = KeybladeItem.magic(keyblade, KeybladeItem.level(player.getMainHandStack()));
		return 1.0f + magic * MAGIC_PER_POINT + keyblade.passive(Passive.MAGIC_BOOST);
	}

	// --- Aktionen -------------------------------------------------------------------------------

	/** Der aktive Zauber; ohne Auswahl der erste in der Leiste. */
	public static Optional<Identifier> selectedSpell(PlayerEntity player) {
		List<Identifier> spells = SpellRegistry.sortedIds(player.getWorld().getRegistryManager());
		Optional<Identifier> selected = get(player).selected().filter(spells::contains);
		return selected.isPresent() ? selected : spells.stream().findFirst();
	}

	public static void select(ServerPlayerEntity player, Identifier spellId) {
		if (SpellRegistry.get(player.getWorld().getRegistryManager(), spellId).isPresent()) {
			update(player, state -> state.withSelected(spellId));
		}
	}

	public static Result castSelected(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Optional<KeybladeDefinition> keyblade = heldKeyblade(player, world.getRegistryManager());
		if (keyblade.isEmpty()) {
			return Result.NO_KEYBLADE;
		}
		Optional<Identifier> spellId = selectedSpell(player);
		if (spellId.isEmpty()) {
			return Result.NO_SPELLS;
		}
		Optional<SpellDefinition> spell = SpellRegistry.get(world.getRegistryManager(), spellId.get());
		Optional<SpellEffect> effect = spell.flatMap(definition -> SpellEffects.get(definition.effect()));
		if (spell.isEmpty() || effect.isEmpty()) {
			KingdomOmnitrix.LOGGER.warn("Zauber {} hat keinen bekannten Effekt", spellId.get());
			return Result.UNKNOWN_SPELL;
		}
		MagicState state = get(player);
		long now = world.getTime();
		if (state.isCharging(now)) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.mp_charging").formatted(Formatting.AQUA), true);
			return Result.CHARGING;
		}
		if (state.cooldownRemaining(spellId.get(), now) > 0) {
			return Result.ON_COOLDOWN;
		}
		int level = Math.min(state.level(spellId.get()), spell.get().maxLevel());
		SpellDefinition.Level data = spell.get().level(level);
		float mp = state.currentMp(now, regenPerSecond(player));
		boolean creative = player.getAbilities().creativeMode;
		// Wie in Kingdom Hearts II: Solange noch MP da sind, gelingt der Zauber; der letzte leert die Leiste.
		if (!creative && mp < MIN_MP_TO_CAST) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_mp").formatted(Formatting.RED), true);
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.4f, 1.8f);
			return Result.NO_MP;
		}

		SpellContext context = new SpellContext(player, world, spellId.get(), level, data, magicFactor(player, keyblade.get()));
		boolean worked;
		try {
			worked = effect.get().cast(context);
		} catch (RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("Zauber {} ist abgestuerzt", spellId.get(), e);
			return Result.FAILED;
		}
		if (!worked) {
			return Result.FAILED;
		}
		float remaining = creative ? mp : Math.max(0.0f, mp - data.mpCost());
		int chargeTicks = chargeTicks(player);
		update(player, s -> {
			MagicState next = remaining <= 0.01f ? s.startCharge(now, chargeTicks) : s.withMp(remaining, now);
			return next.withCooldown(spellId.get(), now + data.cooldown());
		});
		if (remaining <= 0.01f) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.mp_charge_start").formatted(Formatting.AQUA), true);
		}
		return Result.SUCCESS;
	}

	/** Nahkampftreffer mit dem Keyblade laden MP auf (nicht waehrend der MP-Ladezeit). */
	public static void onMeleeHit(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		MagicState state = get(player);
		if (state.isCharging(now)) {
			return;
		}
		float mp = state.currentMp(now, regenPerSecond(player));
		if (mp < MagicState.MAX_MP) {
			update(player, s -> s.withMp(Math.min(MagicState.MAX_MP, mp + MP_PER_MELEE_HIT), now));
		}
	}

	/** Hebt einen Zauber um eine Stufe; false, wenn er unbekannt oder schon auf Hoechststufe ist. */
	public static boolean raiseLevel(ServerPlayerEntity player, Identifier spellId) {
		Optional<SpellDefinition> spell = SpellRegistry.get(player.getWorld().getRegistryManager(), spellId);
		if (spell.isEmpty()) {
			return false;
		}
		int level = get(player).level(spellId);
		if (level >= spell.get().maxLevel()) {
			return false;
		}
		update(player, state -> state.withLevel(spellId, level + 1));
		return true;
	}

	public static boolean setLevel(ServerPlayerEntity player, Identifier spellId, int level) {
		Optional<SpellDefinition> spell = SpellRegistry.get(player.getWorld().getRegistryManager(), spellId);
		if (spell.isEmpty()) {
			return false;
		}
		int clamped = Math.max(1, Math.min(spell.get().maxLevel(), level));
		update(player, state -> state.withLevel(spellId, clamped));
		return true;
	}

	public static void refill(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		update(player, state -> new MagicState(MagicState.MAX_MP, now, 0L, state.selected(), state.levels(), java.util.Map.of()));
	}
}
