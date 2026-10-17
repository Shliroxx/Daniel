package com.santiq.kingdomomnitrix.power;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Koerperkraft je Alien als Blockhaerte, die es zerschlagen kann (Faust = 60 %, Faehigkeiten = 100 % und mehr).
 * Starke Aliens zerschlagen Bloecke mit einem Faustschlag sofort — wie in der Serie, wo Vierarm durch Waende geht.
 */
public final class AlienStrength {
	/** Erde 0,5 · Holz 2 · Stein 1,5 · Erz 3 · Eisenblock 5 · Obsidian 50 */
	private static final Map<String, Float> STRENGTH = Map.ofEntries(
			Map.entry("four_arms", 6.0f),
			Map.entry("humungousaur", 8.0f),
			Map.entry("way_big", 55.0f),
			Map.entry("rath", 5.0f),
			Map.entry("cannonbolt", 5.5f),
			Map.entry("diamondhead", 4.0f),
			Map.entry("chromastone", 4.0f),
			Map.entry("atomix", 55.0f),
			Map.entry("alien_x", 55.0f),
			Map.entry("heatblast", 3.0f),
			Map.entry("swampfire", 2.5f),
			Map.entry("wildmutt", 2.0f),
			Map.entry("spidermonkey", 1.5f),
			Map.entry("upgrade", 3.0f),
			Map.entry("goop", 1.0f),
			Map.entry("ripjaws", 2.5f),
			Map.entry("xlr8", 1.5f),
			Map.entry("jetray", 1.5f),
			Map.entry("big_chill", 1.5f),
			Map.entry("stinkfly", 0.8f),
			Map.entry("ghostfreak", 0.8f),
			Map.entry("echo_echo", 0.8f),
			Map.entry("brainstorm", 1.5f),
			Map.entry("grey_matter", 0.0f));
	private static final float PUNCH = 0.6f;

	private AlienStrength() {
	}

	public static float of(Identifier alien) {
		return alien.getNamespace().equals(KingdomOmnitrix.MOD_ID) ? STRENGTH.getOrDefault(alien.getPath(), 0.0f) : 0.0f;
	}

	public static float of(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().map(AlienStrength::of).orElse(0.0f);
	}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (!(player instanceof ServerPlayerEntity server) || !(world instanceof ServerWorld serverWorld) || player.isSpectator()
					|| player.isCreative() || !TransformationManager.get(server).isTransformed()) {
				return ActionResult.PASS;
			}
			float punch = of(server) * PUNCH;
			BlockState state = world.getBlockState(pos);
			if (punch < 0.5f || state.isAir() || state.getHardness(world, pos) < 0.25f) {
				return ActionResult.PASS;   // weiche Bloecke (Gras, Blumen) baut Minecraft ohnehin sofort ab
			}
			Vec3d look = player.getRotationVector();
			if (!Destruction.smash(serverWorld, server, pos, punch, look.multiply(0.4).add(0.0, 0.15, 0.0))) {
				return ActionResult.PASS;
			}
			player.swingHand(hand, true);
			Vec3d hit = Vec3d.ofCenter(pos).subtract(look.multiply(0.5));
			Fx.flash(serverWorld, hit, 0xFFE8C0, 1.0f);
			Fx.smoke(serverWorld, Vec3d.ofCenter(pos), 0x9A8C78, 0.8f, 2);
			world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.2f, 0.7f);
			world.playSound(null, pos, com.santiq.kingdomomnitrix.registry.ModSounds.ALIEN_SLAM, SoundCategory.PLAYERS, 0.5f, 1.4f);
			return ActionResult.SUCCESS;
		});
	}
}
