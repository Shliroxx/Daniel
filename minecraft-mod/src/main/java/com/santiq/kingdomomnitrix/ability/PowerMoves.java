package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.util.Targeting;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.world.content.TempBlocks;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Gemeinsame Welt-Wirkungen der Alien-Faehigkeiten auf dem Kraft-Kern: Einschlag am Zielpunkt, Krater, zerspringende
 * Bloecke, Saeure, Netze und Ranken als echte (zeitweise) Bloecke. Staerken richten sich nach {@link AlienStrength}.
 */
final class PowerMoves {
	private PowerMoves() {
	}

	/** Punkt, auf den der Spieler zielt: erster Block bis {@code range}, sonst das Ende der Reichweite. */
	static Vec3d aim(ServerPlayerEntity player, double range) {
		HitResult hit = player.raycast(range, 0.0f, false);
		return hit.getType() == HitResult.Type.MISS ? player.getEyePos().add(player.getRotationVec(1.0f).multiply(range)) : hit.getPos();
	}

	/** Einschlag am Zielpunkt: Krater, Explosion, Druckwelle, Wackeln. */
	static void blastAtAim(AbilityContext ctx, double range, double radius, float factor, int color) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d at = aim(player, range);
		Destruction.crater(world, player, at, radius, Math.max(1.0f, AlienStrength.of(player)) * factor, 0.3, 0.5);
		Fx.explosion(world, at, color, (float) radius + 0.8f);
		Fx.shockwave(world, at, color, (float) radius * 2.0f, 10);
		Fx.shake(world, at, (float) Math.min(0.8, 0.15 * radius), 10, (int) (radius * 8 + 12));
	}

	/** Krater um den Spieler (Bodenschuessel). */
	static void craterHere(AbilityContext ctx, double radius, double depth, float factor, int color) {
		ServerPlayerEntity player = ctx.player();
		Destruction.crater(ctx.world(), player, player.getPos().add(0, -0.4, 0), radius, depth, Math.max(1.0f, AlienStrength.of(player)) * factor,
				0.5, 0.5);
		Fx.shockwave(ctx.world(), player.getPos().add(0, 0.1, 0), color, (float) radius * 1.6f, 12);
		Fx.shake(ctx.world(), player.getPos(), (float) Math.min(1.0, 0.12 * radius), 12, (int) (radius * 6 + 12));
	}

	/** Zerbrechliches (Glas, Laub, Eis, Blumen …) im Umkreis zerspringt. */
	static void shatterAround(AbilityContext ctx, double radius, float strength) {
		Destruction.crater(ctx.world(), ctx.player(), ctx.player().getPos().add(0, 1.0, 0), radius, 0.7, strength, 0.2, 0.15);
	}

	/** Schneise in Blickrichtung (Strahl, Schall, Klauen). */
	static void carveLook(AbilityContext ctx, double length, double radius, float strength) {
		ServerPlayerEntity player = ctx.player();
		Vec3d look = player.getRotationVec(1.0f);
		Destruction.carve(ctx.world(), player, player.getEyePos().add(look.multiply(1.2)), look, length, radius, strength, 0.3);
	}

	/** Saeure: zersetzt den Boden am Zielpunkt (Krater ohne Truemmerwurf, gruener Dampf). */
	static void dissolveAt(AbilityContext ctx, Vec3d at, double radius) {
		ServerPlayerEntity player = ctx.player();
		Destruction.crater(ctx.world(), player, at, radius, 0.55, 3.0f, 1.2, 0.0);
		Fx.smoke(ctx.world(), at, 0x7CE84A, 1.2f, 6);
		Fx.sparks(ctx.world(), at, 0xB4FF6A, 0.25f, 10);
	}

	/** Huelle aus zeitweisen Bloecken um das anvisierte Ziel (Netz, Ranken); ohne Ziel am Zielpunkt. */
	static boolean encase(AbilityContext ctx, double range, BlockState state, int ticks, int color) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Optional<LivingEntity> target = Targeting.findLivingTarget(player, range);
		BlockPos feet;
		int r;
		int h;
		if (target.isPresent()) {
			feet = target.get().getBlockPos();
			r = Math.max(1, (int) Math.ceil(target.get().getWidth() * 0.5));
			h = Math.max(1, (int) Math.ceil(target.get().getHeight()));
		} else {
			feet = BlockPos.ofFloored(aim(player, range));
			r = 1;
			h = 1;
		}
		int placed = 0;
		for (BlockPos pos : BlockPos.iterate(feet.add(-r, 0, -r), feet.add(r, h, r))) {
			if (TempBlocks.place(world, pos.toImmutable(), state, ticks, player, color, true)) {
				placed++;
			}
		}
		Fx.flash(world, Vec3d.ofCenter(feet), color, 1.6f);
		return placed > 0;
	}
}
