package com.santiq.kingdomomnitrix.progression;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.enemy.HeartlessEntity;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Helden-EP ausserhalb von Quests: besiegte Herzlose und Entdeckungen (neue Welten, besondere Biome).
 * Bosse, Arena und Dunkelheitsrisse vergeben ihre EP selbst.
 */
public final class ExperienceSources {
	/** Biome, deren erster Besuch EP gibt; Datenpakete koennen weitere eintragen. */
	public static final TagKey<Biome> DISCOVERABLE = TagKey.of(RegistryKeys.BIOME, KingdomOmnitrix.id("discoverable"));

	private static final int DIMENSION_DEFAULT = 100;
	private static final Map<Identifier, Integer> DIMENSIONS = Map.of(
			Identifier.of("minecraft", "the_nether"), 100,
			Identifier.of("minecraft", "the_end"), 150,
			KingdomOmnitrix.id("space"), 150,
			KingdomOmnitrix.id("traverse_town"), 200);
	private static final int BIOME_EXPERIENCE = 40;
	private static final int CHECK_INTERVAL_TICKS = 40;

	/** Herzlose: halbes Grundleben als EP (3–30), Elite ×3, mit der Heldenstufe leicht steigend (+5 % je Stufe). */
	private static final float HEARTLESS_PER_HEALTH = 0.5f;
	private static final int ELITE_FACTOR = 3;

	private ExperienceSources() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof HeartlessEntity heartless && source.getAttacker() instanceof ServerPlayerEntity player) {
				HeroDataAccess.grantExperience(player, heartlessExperience(heartless, HeroDataAccess.get(player).level()));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ExperienceSources::tick);
	}

	public static int heartlessExperience(HeartlessEntity heartless, int playerLevel) {
		double baseHealth = heartless.getAttributeBaseValue(EntityAttributes.GENERIC_MAX_HEALTH);
		int base = MathHelper.clamp(Math.round((float) baseHealth * HEARTLESS_PER_HEALTH), 3, 30);
		float scale = 1.0f + 0.05f * (playerLevel - 1);
		return Math.round(base * scale * (heartless.isElite() ? ELITE_FACTOR : 1));
	}

	// --- Erkunden -------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if ((player.getServerWorld().getTime() + player.getId()) % CHECK_INTERVAL_TICKS != 0 || !player.isAlive() || player.isSpectator()) {
				continue;
			}
			RegistryKey<World> dimension = player.getServerWorld().getRegistryKey();
			if (dimension != World.OVERWORLD) {
				Identifier id = dimension.getValue();
				discover(player, "explored:dimension:" + id, DIMENSIONS.getOrDefault(id, DIMENSION_DEFAULT),
						Text.translatableWithFallback("dimension." + id.getNamespace() + "." + id.getPath(), id.getPath()), true);
			}
			RegistryEntry<Biome> biome = player.getServerWorld().getBiome(player.getBlockPos());
			if (biome.isIn(DISCOVERABLE)) {
				Optional<Identifier> id = biome.getKey().map(RegistryKey::getValue);
				id.ifPresent(value -> discover(player, "explored:biome:" + value, BIOME_EXPERIENCE,
						Text.translatable("biome." + value.getNamespace() + "." + value.getPath().replace('/', '.')), false));
			}
		}
	}

	private static void discover(ServerPlayerEntity player, String flag, int experience, MutableText name, boolean world) {
		if (HeroDataAccess.get(player).hasFlag(flag)) {
			return;
		}
		HeroDataAccess.update(player, data -> data.withFlag(flag, true));
		player.sendMessage(Text.translatable(world ? "message.kingdomomnitrix.discovered_world" : "message.kingdomomnitrix.discovered_biome",
				name.formatted(Formatting.BOLD), experience).formatted(Formatting.LIGHT_PURPLE), false);
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HERO_DISCOVERY,
				SoundCategory.PLAYERS, 1.0f, world ? 0.8f : 1.1f);
		HeroDataAccess.grantExperience(player, experience);
	}
}
