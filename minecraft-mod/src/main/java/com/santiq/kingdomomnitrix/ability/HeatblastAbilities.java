package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Heatblast (Pyronite). Eigenes Dauer-System „Kernhitze“ (0–100) statt fester Feuerzauber:
 *
 * <ul>
 *   <li>Jede Faehigkeit heizt den Kern auf; Feuer/Lava und der Nether heizen nach, Wasser und Regen kuehlen schnell
 *       (Dampf, kurz geschwaecht). Sonst kuehlt der Kern langsam ab.</li>
 *   <li><b>Gluehend</b> (ab {@link #GLOW}): +30 % Schaden, Feuerstoss feuert drei Kugeln, Hitze-Aura versengt Gegner
 *       in der Naehe.</li>
 *   <li><b>Ueberhitzt</b> ({@link #MAX}): die naechste Faehigkeit entlaedt den ganzen Kern als <i>Blaufeuer</i> —
 *       doppelter Schaden, groessere Reichweite, Seelenflammen. Danach ist der Kern leer.</li>
 * </ul>
 * Die Anzeige laeuft ueber die Aktionsleiste. Kein Blockschaden, kein Feuer in der Welt (nur an Gegnern).
 */
final class HeatblastAbilities {
	static final float MAX = 100.0f;
	static final float GLOW = 50.0f;
	private static final float GLOW_FACTOR = 1.3f;
	private static final float BLUE_FACTOR = 2.0f;
	/** Pruefintervall fuer passive Hitze (Ticks) */
	private static final int PASSIVE_INTERVAL = 5;
	/** Abkuehlen je Pruefintervall ohne Hitzequelle (1 %/s) und Hitze je Treffer */
	private static final float COOLING = 0.25f;
	private static final float HEAT_PER_HIT = 1.0f;
	private static final Identifier HEATBLAST = KingdomOmnitrix.id("heatblast");
	/** Flammenfarben der FX-Engine: normales Feuer, Blaufeuer (ueberhitzt) */
	static final int FIRE = 0xFF6A10;
	static final int BLUE = 0x3A9CFF;

	/** Kernhitze je Spieler */
	private static final Map<UUID, Float> HEAT = new HashMap<>();
	private HeatblastAbilities() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HeatblastAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
		com.santiq.kingdomomnitrix.ability.passive.PassiveRegistry.register(KingdomOmnitrix.id("pyronite_core"),
				new com.santiq.kingdomomnitrix.ability.passive.AlienPassive() {
					@Override
					public void tick(com.santiq.kingdomomnitrix.ability.passive.PassiveContext ctx) {
						if (ctx.now() % PASSIVE_INTERVAL == 0) {
							tickPassive(ctx.world(), ctx.player(), ctx.now());
						}
					}
				});
		HeatblastMoves.register();
		HeatblastPowers.register();
	}

	// --- Kernhitze -------------------------------------------------------------------------------

	static float heat(ServerPlayerEntity player) {
		return HEAT.getOrDefault(player.getUuid(), 0.0f);
	}

	static void addHeat(ServerPlayerEntity player, float amount) {
		float before = heat(player);
		float after = MathHelper.clamp(before + amount, 0.0f, MAX);
		HEAT.put(player.getUuid(), after);
		if (before < MAX && after >= MAX) {
			ServerWorld world = player.getServerWorld();
			Fx.flash(world, player.getPos().add(0, 1.0, 0), BLUE, 2.5f);
			Fx.aura(world, player, BLUE, 0.7f, 30);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.0f, 0.7f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.heatblast_overheat").formatted(Formatting.AQUA), true);
		}
	}

	/** Kern leeren (Supernova entlaedt ihn ganz). */
	static void drain(ServerPlayerEntity player) {
		HEAT.put(player.getUuid(), 0.0f);
	}

	/** Schadensfaktor der aktuellen Stufe; im Ueberhitzt-Zustand wird der Kern entladen (blau). */
	static Power power(ServerPlayerEntity player) {
		float heat = heat(player);
		if (heat >= MAX) {
			HEAT.put(player.getUuid(), 0.0f);
			return new Power(BLUE_FACTOR, true);
		}
		return new Power(heat >= GLOW ? GLOW_FACTOR : 1.0f, false);
	}

	record Power(float factor, boolean blue) {
		int color() {
			return blue ? BLUE : FIRE;
		}
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			boolean heatblast = TransformationManager.get(player).activeAlien().filter(HEATBLAST::equals).isPresent();
			if (!heatblast) {
				if (HEAT.containsKey(player.getUuid())) {
					AlienMeterSync.clear(player, AlienMeterPayload.HEAT);
					forget(player.getUuid());
				}
				continue;
			}
			// Kernhitze (Umgebung, Aura, Anzeige) laeuft als passive Kraft „Pyronit-Kern“ ueber PassiveHooks
		}
	}

	/** Umgebung heizt/kuehlt, Aura ab Gluehend, Anzeige in der Aktionsleiste. */
	private static void tickPassive(ServerWorld world, ServerPlayerEntity player, long now) {
		float delta = -COOLING;
		if (player.isInLava() || world.getBlockState(player.getBlockPos()).isIn(net.minecraft.registry.tag.BlockTags.FIRE)) {
			delta = 5.0f;
		} else if (world.getRegistryKey() == World.NETHER) {
			delta = Math.max(delta, 1.0f);
		}
		if (player.isTouchingWaterOrRain()) {
			delta = player.isSubmergedInWater() ? -12.0f : -4.0f;
			Fx.smoke(world, player.getPos().add(0, 1.2, 0), 0xE8F0F4, 0.6f, 2);
			if (player.isSubmergedInWater()) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, false, false));
			}
		}
		float before = heat(player);
		// Ueberhitzt bleibt, bis eine Kraft den Kern entlaedt — nur Wasser und Regen kuehlen ihn vorher ab
		if (before >= MAX && delta < 0.0f && !player.isTouchingWaterOrRain()) {
			delta = 0.0f;
		}
		if (delta != 0.0f && (before > 0.0f || delta > 0.0f)) {
			addHeat(player, delta);
		}
		float heat = heat(player);
		if (heat >= GLOW) {
			// Hitze-Aura: Gegner direkt daneben fangen Feuer
			double aura = heat >= MAX ? 3.0 : 2.0;
			if (now % 20 == 0) {
				for (LivingEntity target : around(player, aura)) {
					hit(player, target, 1.5f, 3.0f);
				}
			}
			if (now % 10 == 0) {
				Fx.flames(world, player.getPos().add(0, 0.6, 0), new Vec3d(0, 0.08, 0), heat >= MAX ? BLUE : FIRE, 0.6f, heat >= MAX ? 5 : 3);
			}
		}
		// Hitze-Anzeige im HUD und Glut-Aura am Koerper (Client)
		AlienMeterSync.update(player, AlienMeterPayload.HEAT, heat);
	}

	// --- Hilfen ----------------------------------------------------------------------------------

	static List<LivingEntity> around(ServerPlayerEntity player, double radius) {
		double radiusSq = radius * radius;
		return player.getServerWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
				e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= radiusSq && PartyRules.canHarm(player, e));
	}

	static void hit(ServerPlayerEntity player, LivingEntity target, float damage, float fireSeconds) {
		target.timeUntilRegen = 0;
		if (target.damage(player.getServerWorld().getDamageSources().playerAttack(player), damage)) {
			// jeder Treffer heizt den Kern nach — Angriff lohnt sich
			HEAT.computeIfPresent(player.getUuid(), (id, h) -> h >= MAX ? h : Math.min(MAX - 0.01f, h + HEAT_PER_HIT));
		}
		if (fireSeconds > 0.0f) {
			target.setOnFireFor(fireSeconds);
		}
	}

	private static void forget(UUID id) {
		HEAT.remove(id);
		AlienMeterSync.forget(id);
		HeatblastPowers.forget(id);
	}
}
