package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/**
 * Evolve-Modus (wie die Ultimatrix): ein verwandelter Spieler entwickelt sein Alien fuer begrenzte Zeit zur
 * Ultimate-Form ({@link AlienUltimate}). Zustand als Anhang am Spieler, an alle synchronisiert (der Renderer zeigt das
 * Ultimate-Modell auch fremden Spielern). Endet nach Ablauf, beim Zurueckverwandeln und beim Alien-Wechsel; die
 * Abklingzeit gilt je Spieler.
 */
public final class Evolution {
	/** Ultimate-Form: welches Alien, bis wann; Abklingzeit bis */
	public record State(Optional<Identifier> alien, long until, long cooldownUntil) {
		public static final State NONE = new State(Optional.empty(), 0L, 0L);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.optionalFieldOf("alien").forGetter(State::alien),
				Codec.LONG.optionalFieldOf("until", 0L).forGetter(State::until),
				Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(State::cooldownUntil)
		).apply(instance, State::new));
	}

	public enum Result {
		SUCCESS, NOT_TRANSFORMED, NO_ULTIMATE, LOCKED, COOLDOWN, ALREADY
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(KingdomOmnitrix.id("evolution"), builder -> builder
			.persistent(State.CODEC)
			.initializer(() -> State.NONE)
			.syncWith(PacketCodecs.codec(State.CODEC), AttachmentSyncPredicate.all()));

	private static final int MAX_BONUSES = 8;
	private static final DustParticleEffect ULTIMATRIX = new DustParticleEffect(new Vector3f(0.3f, 1.0f, 0.2f), 1.6f);

	private Evolution() {
	}

	public static void register() {
		AlienUltimate.register();
		ServerTickEvents.END_SERVER_TICK.register(Evolution::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeBonuses(handler.getPlayer()));
	}

	/** Meldung zum Ergebnis (explizite Schluessel — keine zusammengesetzten Uebersetzungs-IDs). */
	public static net.minecraft.text.MutableText message(Result result) {
		return Text.translatable(switch (result) {
			case SUCCESS -> "message.kingdomomnitrix.evolve_success";
			case NOT_TRANSFORMED -> "message.kingdomomnitrix.evolve_not_transformed";
			case NO_ULTIMATE -> "message.kingdomomnitrix.evolve_no_ultimate";
			case LOCKED -> "message.kingdomomnitrix.evolve_locked";
			case COOLDOWN -> "message.kingdomomnitrix.evolve_cooldown";
			case ALREADY -> "message.kingdomomnitrix.evolve_already";
		});
	}

	public static State get(PlayerEntity player) {
		State state = player.getAttached(STATE);
		return state != null ? state : State.NONE;
	}

	/** Ist der Spieler gerade in der Ultimate-Form seines aktiven Aliens? (Client und Server) */
	public static boolean isUltimate(PlayerEntity player) {
		State state = get(player);
		Optional<Identifier> active = TransformationManager.get(player).activeAlien();
		return active.isPresent() && state.alien().equals(active) && player.getWorld().getTime() < state.until();
	}

	/** Modell der Ultimate-Form, falls der Spieler gerade entwickelt ist. */
	public static Optional<Identifier> ultimateModel(PlayerEntity player) {
		if (!isUltimate(player)) {
			return Optional.empty();
		}
		return TransformationManager.get(player).activeAlien()
				.flatMap(id -> AlienUltimate.of(player.getWorld().getRegistryManager(), id))
				.map(AlienUltimate::model);
	}

	/** Restsekunden der Ultimate-Form (0 = nicht entwickelt). */
	public static long remainingTicks(PlayerEntity player) {
		return isUltimate(player) ? Math.max(0L, get(player).until() - player.getWorld().getTime()) : 0L;
	}

	/** Entwickeln per Taste: alle Regeln (Meisterschaft, Abklingzeit). */
	public static Result evolve(ServerPlayerEntity player) {
		return evolve(player, false);
	}

	/** @param force Admin-Befehl: Meisterschaft und Abklingzeit werden ignoriert */
	public static Result evolve(ServerPlayerEntity player, boolean force) {
		Optional<Identifier> active = TransformationManager.get(player).activeAlien();
		if (active.isEmpty()) {
			return Result.NOT_TRANSFORMED;
		}
		if (isUltimate(player)) {
			return Result.ALREADY;
		}
		Optional<AlienUltimate> ultimate = AlienUltimate.of(player.getServer().getRegistryManager(), active.get());
		if (ultimate.isEmpty()) {
			return Result.NO_ULTIMATE;
		}
		AlienUltimate form = ultimate.get();
		if (!force && AlienMasteryManager.get(player).level(active.get()) < form.unlockLevel()) {
			return Result.LOCKED;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		if (!force && now < get(player).cooldownUntil()) {
			return Result.COOLDOWN;
		}
		player.setAttached(STATE, new State(active, now + form.seconds() * 20L, now + (form.seconds() + form.cooldown()) * 20L));
		applyBonuses(player, form);
		effect(world, player, true);
		TransformationManager.activeDefinition(player).ifPresent(alien -> alien.traits().transformStyle().play(world, player));
		player.sendMessage(Text.translatable("message.kingdomomnitrix.evolve", Text.translatable(AlienDefinition.translationKey(active.get())))
				.formatted(Formatting.GREEN, Formatting.BOLD), true);
		return Result.SUCCESS;
	}

	/** Zurueck zur normalen Form (Ablauf, Rueckverwandlung, Wechsel). Abklingzeit bleibt. */
	public static void devolve(ServerPlayerEntity player, boolean effect) {
		State state = get(player);
		if (state.alien().isEmpty()) {
			return;
		}
		player.setAttached(STATE, new State(Optional.empty(), 0L, state.cooldownUntil()));
		removeBonuses(player);
		if (effect) {
			effect(player.getServerWorld(), player, false);
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % 5 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			State state = get(player);
			if (state.alien().isEmpty()) {
				continue;
			}
			boolean sameAlien = state.alien().equals(TransformationManager.get(player).activeAlien());
			if (!sameAlien || player.getServerWorld().getTime() >= state.until()) {
				devolve(player, sameAlien);
			}
		}
	}

	private static Identifier bonusId(int index) {
		return KingdomOmnitrix.id("ultimate_bonus_" + index);
	}

	private static void applyBonuses(ServerPlayerEntity player, AlienUltimate form) {
		removeBonuses(player);
		List<AlienDefinition.AttributeBonus> bonuses = form.attributes();
		for (int i = 0; i < Math.min(MAX_BONUSES, bonuses.size()); i++) {
			AlienDefinition.AttributeBonus bonus = bonuses.get(i);
			EntityAttributeInstance instance = player.getAttributeInstance(bonus.attribute());
			if (instance != null) {
				instance.addTemporaryModifier(new EntityAttributeModifier(bonusId(i), bonus.amount(), bonus.operation()));
			}
		}
	}

	private static void removeBonuses(ServerPlayerEntity player) {
		for (var entry : net.minecraft.registry.Registries.ATTRIBUTE.streamEntries().toList()) {
			EntityAttributeInstance instance = player.getAttributeInstance(entry);
			if (instance != null) {
				for (int i = 0; i < MAX_BONUSES; i++) {
					instance.removeModifier(bonusId(i));
				}
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Ultimatrix-Effekt: gruene Stacheln schiessen spiralfoermig hoch (Entwickeln) oder fallen zusammen (Zurueck). */
	private static void effect(ServerWorld world, ServerPlayerEntity player, boolean up) {
		double h = player.getHeight();
		for (int i = 0; i < 48; i++) {
			double t = i / 48.0;
			double a = t * MathHelper.TAU * 3;
			double r = up ? 1.2 * (1 - t) + 0.3 : 0.3 + 1.2 * t;
			world.spawnParticles(ULTIMATRIX, player.getX() + Math.cos(a) * r, player.getY() + t * h * 1.2, player.getZ() + Math.sin(a) * r, 1,
					0.0, 0.0, 0.0, 0.0);
		}
		if (up) {
			world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.6, 0.8, 0.6, 0.0);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.4f, 0.7f);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.PLAYERS, 1.0f, 1.4f);
		} else {
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.8f);
		}
	}
}
