package com.santiq.kingdomomnitrix.progression;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Sammelt Alien-Erfahrung (Schaden als Alien, Faehigkeiten, Zeit in Alienform) und meldet Meisterschafts-Aufstiege. */
@SuppressWarnings("UnstableApiUsage")
public final class AlienMasteryManager {
	public static final AttachmentType<AlienMastery> MASTERY = AttachmentRegistry.create(KingdomOmnitrix.id("alien_mastery"), builder -> builder
			.persistent(AlienMastery.CODEC)
			.initializer(() -> AlienMastery.EMPTY)
			.copyOnDeath()
			.syncWith(AlienMastery.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Hoechstens so viel Erfahrung pro Treffer, damit ein einzelner Riesentreffer nicht alles erledigt. */
	private static final int MAX_PER_HIT = 30;
	public static final int PER_ABILITY = 3;
	private static final int PER_KILL = 5;
	private static final int TIME_INTERVAL_TICKS = 100;
	/** Helden-EP je erreichter Meisterschaftsstufe (Stufe × Wert). */
	private static final int HERO_EXPERIENCE_PER_LEVEL = 25;

	private AlienMasteryManager() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity player && entity != player && damageTaken > 0.0f
					&& !(entity instanceof ArmorStandEntity)) {
				activeAlien(player).ifPresent(alien -> add(player, alien, Math.min(MAX_PER_HIT, (int) Math.ceil(damageTaken))));
			}
		});
		// AFTER_DAMAGE meldet nur Treffer, die das Ziel ueberlebt; der toedliche Schlag zaehlt hier
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity player && entity != player && !(entity instanceof ArmorStandEntity)) {
				activeAlien(player).ifPresent(alien -> add(player, alien,
						Math.min(MAX_PER_HIT, (int) Math.ceil(entity.getMaxHealth() / 2.0f)) + PER_KILL));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(AlienMasteryManager::tick);
	}

	public static AlienMastery get(PlayerEntity player) {
		AlienMastery mastery = player.getAttached(MASTERY);
		return mastery != null ? mastery : AlienMastery.EMPTY;
	}

	private static Optional<Identifier> activeAlien(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien();
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if ((player.getServerWorld().getTime() + player.getId()) % TIME_INTERVAL_TICKS == 0 && player.isAlive()) {
				activeAlien(player).ifPresent(alien -> add(player, alien, 1));
			}
		}
	}

	public static void add(ServerPlayerEntity player, Identifier alien, int amount) {
		AlienMastery before = get(player);
		AlienMastery after = before.add(alien, amount);
		if (after.equals(before)) {
			return;
		}
		player.setAttached(MASTERY, after);
		int oldLevel = before.level(alien);
		int newLevel = after.level(alien);
		if (newLevel > oldLevel) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.mastery_up", TransformationManager.alienName(alien).formatted(Formatting.BOLD),
					newLevel, Math.round(after.durationBonus(alien) * 100), Math.round(after.cooldownReduction(alien) * 100))
					.formatted(Formatting.GREEN), false);
			player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HERO_DISCOVERY,
					SoundCategory.PLAYERS, 0.9f, 1.0f);
			HeroDataAccess.grantExperience(player, HERO_EXPERIENCE_PER_LEVEL * newLevel);
			com.santiq.kingdomomnitrix.omnitrix.MasterControlProgress.check(player);
		}
	}

	/** Setzt die Meisterschaft eines Aliens direkt (Befehl). */
	public static void setLevel(ServerPlayerEntity player, Identifier alien, int level) {
		int clamped = Math.max(1, Math.min(AlienMastery.MAX_LEVEL, level));
		java.util.Map<Identifier, Integer> next = new java.util.HashMap<>(get(player).experience());
		next.put(alien, AlienMastery.experienceFor(clamped));
		player.setAttached(MASTERY, new AlienMastery(next));
		com.santiq.kingdomomnitrix.omnitrix.MasterControlProgress.check(player);
	}
}
