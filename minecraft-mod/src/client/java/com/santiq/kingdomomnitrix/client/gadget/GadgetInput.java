package com.santiq.kingdomomnitrix.client.gadget;

import com.santiq.kingdomomnitrix.progression.HeroAbilityEffect;
import com.santiq.kingdomomnitrix.progression.ProgressionManager;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.gadget.GadgetManager;
import com.santiq.kingdomomnitrix.gadget.HeliPackItem;
import com.santiq.kingdomomnitrix.gadget.PackMode;
import com.santiq.kingdomomnitrix.gadget.SwingshotItem;
import com.santiq.kingdomomnitrix.networking.GadgetActionPayload;
import com.santiq.kingdomomnitrix.networking.SwingshotPayload;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Gadget-Steuerung des eigenen Spielers. Die Bewegung (Doppelsprung, Gleiten, Schub, Swingshot-Zug) rechnet der Client,
 * weil Minecraft die Spielerbewegung clientseitig berechnet; der Server bekommt nur Absichten und prueft sie.
 */
public final class GadgetInput {
	// Heli-Pack
	private static final double DOUBLE_JUMP_VELOCITY = 0.62;
	private static final double HELI_GLIDE_FALL = -0.09;
	private static final double HELI_GLIDE_MAX_SPEED = 0.36;
	// Heli-Jet
	private static final double JET_THRUST_FORWARD = 1.05;
	private static final double JET_THRUST_UP = 0.32;
	private static final int JET_MAX_THRUSTS = 2;
	private static final int JET_THRUST_COOLDOWN = 12;
	private static final double JET_HOVER_FALL = -0.22;
	private static final double JET_HOVER_MAX_SPEED = 0.55;
	private static final double GLIDE_ACCELERATION = 1.05;
	// Gleiter: sehr langsamer Sinkflug, Fahrt in Blickrichtung; Nase runter = schneller, aber steiler
	private static final double GLIDER_FALL = -0.035;
	private static final double GLIDER_DIVE_FALL = -0.30;
	private static final double GLIDER_SPEED = 0.62;
	private static final double GLIDER_DIVE_SPEED = 0.55;
	private static final double GLIDER_STEER = 0.18;
	/** Aufwind ueber Feuer, Lava, Magma und Lagerfeuern bis zu dieser Hoehe */
	private static final int UPDRAFT_RANGE = 14;
	private static final double UPDRAFT_LIFT = 0.16;
	// Jetpack: Steigflug bei gehaltener Sprungtaste, Treibstoff fuer 4 Sekunden, laedt am Boden doppelt so schnell nach
	public static final int JETPACK_FUEL = 80;
	private static final double JETPACK_LIFT = 0.09;
	private static final double JETPACK_MAX_RISE = 0.38;
	private static final double JETPACK_PUSH = 0.045;
	private static final double JETPACK_MAX_SPEED = 0.55;
	// Faehigkeit Gleiten
	private static final double ABILITY_GLIDE_FALL = -0.12;
	private static final double ABILITY_GLIDE_MAX_SPEED = 0.42;
	// Swingshot
	private static final double PULL_SPEED_START = 0.45;
	private static final double PULL_SPEED_MAX = 1.35;
	private static final double PULL_ACCELERATION = 0.12;
	private static final double ARRIVE_DISTANCE = 1.6;
	private static final double HANG_JUMP_VELOCITY = 0.7;

	private static boolean jumpWasDown;
	private static boolean wasAirborne;
	private static boolean doubleJumped;
	private static int thrustsUsed;
	private static int thrustCooldown;
	private static boolean gliding;
	private static int jetpackFuel = JETPACK_FUEL;

	private static Vec3d swingAnchor;
	private static int swingTicks;
	private static int swingCooldown;
	private static Vec3d lastPosition;
	private static int stuckTicks;
	private static Vec3d hangPoint;
	private static int hangTicks;
	private static boolean hangJumpWasDown;

	private GadgetInput() {
	}

	public static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		boolean free = client.currentScreen == null;
		while (ModKeyBindings.GADGET_BELT.wasPressed()) {
			if (free) {
				send(GadgetManager.Action.OPEN_BELT);
			}
		}
		while (ModKeyBindings.PACK_MODE.wasPressed()) {
			if (free) {
				send(GadgetManager.Action.TOGGLE_MODE);
			}
		}
		while (ModKeyBindings.USE_GADGET.wasPressed()) {
			if (free) {
				useTool(client, player);
			}
		}
		if (swingCooldown > 0) {
			swingCooldown--;
		}
		tickSwing(client, player);
		tickPack(client, player);
	}

	public static void reset() {
		jumpWasDown = false;
		wasAirborne = false;
		doubleJumped = false;
		thrustsUsed = 0;
		thrustCooldown = 0;
		gliding = false;
		jetpackFuel = JETPACK_FUEL;
		swingAnchor = null;
		swingTicks = 0;
		swingCooldown = 0;
		lastPosition = null;
		stuckTicks = 0;
		hangPoint = null;
		hangTicks = 0;
		hangJumpWasDown = false;
	}

	public static boolean isSwinging() {
		return swingAnchor != null;
	}

	/** Treibstoff des Jetpacks in Ticks (0 bis {@link #JETPACK_FUEL}); fuer die Anzeige im HUD. */
	public static int jetpackFuel() {
		return jetpackFuel;
	}

	public static boolean isHanging() {
		return hangPoint != null;
	}

	// --- Heli-Pack / Heli-Jet -------------------------------------------------------------------

	private static void tickPack(MinecraftClient client, ClientPlayerEntity player) {
		boolean jumpDown = client.options.jumpKey.isPressed() && client.currentScreen == null;
		boolean jumpPressed = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;
		if (thrustCooldown > 0) {
			thrustCooldown--;
		}

		Optional<ItemStack> pack = GadgetManager.pack(player);
		boolean airborne = !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater() && !player.isInLava()
				&& !player.hasVehicle() && !player.getAbilities().flying && !player.isFallFlying() && !player.isSpectator();
		if (!airborne) {
			doubleJumped = false;
			thrustsUsed = 0;
			if (player.isOnGround()) {
				jetpackFuel = Math.min(JETPACK_FUEL, jetpackFuel + 2);
			}
		}
		// Ein Sprung vom Boden zaehlt nicht als Luftsprung: erst wenn der Spieler schon in der Luft war.
		boolean airJump = jumpPressed && airborne && wasAirborne;
		wasAirborne = airborne;
		if (pack.isEmpty() && airborne && !isSwinging() && ProgressionManager.has(player, HeroAbilityEffect.GLIDE)) {
			// Helden-Faehigkeit „Gleiten“ (wie in Kingdom Hearts): ohne Pack, etwas schneller als das Heli-Pack
			glide(player, jumpDown, ABILITY_GLIDE_FALL, ABILITY_GLIDE_MAX_SPEED);
			return;
		}
		if (pack.isEmpty() || !airborne || isSwinging()) {
			setGliding(false);
			return;
		}
		PackMode mode = HeliPackItem.mode(pack.get());
		if (mode == PackMode.GLIDER) {
			glider(client, player, jumpDown);
			return;
		}
		if (mode == PackMode.JETPACK) {
			jetpack(player, jumpDown);
			return;
		}
		boolean jet = mode == PackMode.THRUSTER;
		Vec3d velocity = player.getVelocity();
		if (airJump) {
			if (!jet && !doubleJumped) {
				doubleJumped = true;
				player.setVelocity(velocity.x, DOUBLE_JUMP_VELOCITY, velocity.z);
				player.fallDistance = 0.0f;
				send(GadgetManager.Action.DOUBLE_JUMP);
				return;
			}
			if (jet && thrustsUsed < JET_MAX_THRUSTS && thrustCooldown == 0) {
				thrustsUsed++;
				thrustCooldown = JET_THRUST_COOLDOWN;
				float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
				Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw)).multiply(JET_THRUST_FORWARD);
				player.setVelocity(forward.x, Math.max(velocity.y, 0.0) + JET_THRUST_UP, forward.z);
				player.fallDistance = 0.0f;
				send(GadgetManager.Action.JET_THRUST);
				return;
			}
		}
		// Gehaltene Sprungtaste beim Fallen: Gleiten (Heli) bzw. gebremster Sinkflug (Jet).
		glide(player, jumpDown, jet ? JET_HOVER_FALL : HELI_GLIDE_FALL, jet ? JET_HOVER_MAX_SPEED : HELI_GLIDE_MAX_SPEED);
	}

	/**
	 * Gleiter: bei gehaltener Sprungtaste im Fall oeffnen sich die Schwingen. Die Fahrt folgt der Blickrichtung (Lenken
	 * mit der Maus), Nase runter tauscht Hoehe gegen Tempo. Ueber Hitzequellen hebt der Aufwind den Spieler an.
	 */
	private static void glider(MinecraftClient client, ClientPlayerEntity player, boolean jumpDown) {
		Vec3d velocity = player.getVelocity();
		if (!jumpDown || (!gliding && velocity.y > 0.0)) {
			if (!jumpDown) {
				setGliding(false);
			}
			return;
		}
		float pitch = MathHelper.clamp(player.getPitch(), -20.0f, 60.0f);
		double dive = Math.max(0.0, pitch) / 60.0;
		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		double speed = GLIDER_SPEED + dive * GLIDER_DIVE_SPEED;
		Vec3d target = new Vec3d(-MathHelper.sin(yaw) * speed, 0.0, MathHelper.cos(yaw) * speed);
		Vec3d horizontal = new Vec3d(velocity.x, 0.0, velocity.z);
		horizontal = horizontal.add(target.subtract(horizontal).multiply(GLIDER_STEER));
		double fall = MathHelper.lerp(dive, GLIDER_FALL, GLIDER_DIVE_FALL);
		double vertical = fall;
		if (updraft(client, player)) {
			vertical = Math.min(velocity.y + UPDRAFT_LIFT, 0.5);
		}
		player.setVelocity(horizontal.x, vertical, horizontal.z);
		player.fallDistance = 0.0f;
		setGliding(true);
	}

	/** Steigt unter dem Spieler Hitze auf (Feuer, Lava, Magma, brennendes Lagerfeuer)? */
	private static boolean updraft(MinecraftClient client, ClientPlayerEntity player) {
		BlockPos.Mutable pos = player.getBlockPos().mutableCopy();
		for (int i = 0; i < UPDRAFT_RANGE; i++) {
			BlockState state = client.world.getBlockState(pos);
			if (state.isIn(BlockTags.FIRE) || state.isOf(Blocks.LAVA) || state.isOf(Blocks.MAGMA_BLOCK)
					|| (state.isIn(BlockTags.CAMPFIRES) && CampfireBlock.isLitCampfire(state))) {
				return true;
			}
			if (!state.isAir() && state.isSolidBlock(client.world, pos)) {
				return false;
			}
			pos.move(0, -1, 0);
		}
		return false;
	}

	/**
	 * Jetpack: gehaltene Sprungtaste in der Luft steigt, solange Treibstoff da ist; Laufen schiebt in Blickrichtung.
	 * Ist der Tank leer, bremsen die Duesen den Fall wie beim Heli-Pack, damit ein leerer Tank kein Absturz ist.
	 */
	private static void jetpack(ClientPlayerEntity player, boolean jumpDown) {
		if (jumpDown && jetpackFuel <= 0) {
			glide(player, true, HELI_GLIDE_FALL, HELI_GLIDE_MAX_SPEED);
			return;
		}
		if (!jumpDown) {
			setGliding(false);
			return;
		}
		jetpackFuel--;
		Vec3d velocity = player.getVelocity();
		double rise = Math.min(JETPACK_MAX_RISE, velocity.y + JETPACK_LIFT + 0.08);
		double x = velocity.x;
		double z = velocity.z;
		float forward = player.input.movementForward;
		float sideways = player.input.movementSideways;
		if (forward != 0.0f || sideways != 0.0f) {
			float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
			double sin = MathHelper.sin(yaw);
			double cos = MathHelper.cos(yaw);
			x += (sideways * cos - forward * sin) * JETPACK_PUSH;
			z += (forward * cos + sideways * sin) * JETPACK_PUSH;
			double length = Math.sqrt(x * x + z * z);
			if (length > JETPACK_MAX_SPEED) {
				x *= JETPACK_MAX_SPEED / length;
				z *= JETPACK_MAX_SPEED / length;
			}
		}
		player.setVelocity(x, rise, z);
		player.fallDistance = 0.0f;
		setGliding(true);
		if (jetpackFuel % 5 == 0) {
			int bars = MathHelper.ceil(jetpackFuel * 10.0f / JETPACK_FUEL);
			player.sendMessage(Text.translatable("hud.kingdomomnitrix.jetpack_fuel", "▮".repeat(bars) + "▯".repeat(10 - bars))
					.formatted(jetpackFuel < JETPACK_FUEL / 4 ? Formatting.RED : Formatting.GOLD), true);
		}
	}

	private static void glide(ClientPlayerEntity player, boolean jumpDown, double fall, double maxSpeed) {
		Vec3d velocity = player.getVelocity();
		if (jumpDown && velocity.y < fall) {
			double horizontal = velocity.horizontalLength();
			double boost = horizontal < maxSpeed ? GLIDE_ACCELERATION : 1.0;
			player.setVelocity(velocity.x * boost, fall, velocity.z * boost);
			player.fallDistance = 0.0f;
			setGliding(true);
		} else if (!jumpDown) {
			setGliding(false);
		}
	}

	private static void setGliding(boolean value) {
		if (gliding == value) {
			return;
		}
		gliding = value;
		send(value ? GadgetManager.Action.GLIDE_START : GadgetManager.Action.GLIDE_STOP);
	}

	// --- Swingshot ------------------------------------------------------------------------------

	private static void useTool(MinecraftClient client, ClientPlayerEntity player) {
		if (isSwinging()) {
			stopSwing(player, false);
			return;
		}
		if (!GadgetManager.hasSwingshot(player)) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_tool_gadget").formatted(Formatting.RED), true);
			return;
		}
		if (swingCooldown > 0) {
			return;
		}
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(SwingshotItem.RANGE));
		BlockHitResult hit = client.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			player.playSound(SoundEvents.BLOCK_DISPENSER_FAIL, 0.5f, 1.6f);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.swingshot_no_target").formatted(Formatting.GRAY), true);
			swingCooldown = 5;
			return;
		}
		swingAnchor = hit.getPos();
		swingTicks = 0;
		stuckTicks = 0;
		lastPosition = player.getPos();
		setGliding(false);
		ClientPlayNetworking.send(new SwingshotPayload(true, swingAnchor.x, swingAnchor.y, swingAnchor.z));
	}

	private static void tickSwing(MinecraftClient client, ClientPlayerEntity player) {
		if (swingAnchor == null) {
			return;
		}
		if (!player.isAlive() || !GadgetManager.hasSwingshot(player) || player.isSneaking()) {
			stopSwing(player, false);
			return;
		}
		if (hangPoint != null) {
			tickHang(client, player);
			return;
		}
		if (swingTicks++ > SwingshotItem.MAX_PULL_TICKS) {
			stopSwing(player, false);
			return;
		}
		Vec3d center = player.getPos().add(0.0, player.getHeight() * 0.5, 0.0);
		Vec3d toAnchor = swingAnchor.subtract(center);
		double distance = toAnchor.length();
		if (distance < ARRIVE_DISTANCE) {
			// Angekommen: am Haken haengen bleiben, bis Springen oder Schleichen loest
			hangPoint = player.getPos();
			hangTicks = 0;
			hangJumpWasDown = client.options.jumpKey.isPressed();
			player.setVelocity(Vec3d.ZERO);
			return;
		}
		// Haengt der Spieler fest (Kante, Decke), loest der Haken nach kurzer Zeit.
		Vec3d position = player.getPos();
		stuckTicks = lastPosition != null && position.squaredDistanceTo(lastPosition) < 0.0025 ? stuckTicks + 1 : 0;
		lastPosition = position;
		if (stuckTicks > 6) {
			stopSwing(player, false);
			return;
		}
		double speed = Math.min(PULL_SPEED_MAX, PULL_SPEED_START + swingTicks * PULL_ACCELERATION);
		player.setVelocity(toAnchor.normalize().multiply(Math.min(speed, distance)));
		player.fallDistance = 0.0f;
		player.setOnGround(false);
	}

	private static void tickHang(MinecraftClient client, ClientPlayerEntity player) {
		boolean jumpDown = client.options.jumpKey.isPressed() && client.currentScreen == null;
		boolean jumpPressed = jumpDown && !hangJumpWasDown;
		hangJumpWasDown = jumpDown;
		if (jumpPressed) {
			stopSwing(player, true);
			return;
		}
		if (++hangTicks > SwingshotItem.MAX_HANG_TICKS || player.isOnGround()) {
			stopSwing(player, false);
			return;
		}
		// Am Haltepunkt halten; die Schwerkraft (0,08 pro Tick) wird ausgeglichen.
		Vec3d correction = hangPoint.subtract(player.getPos()).multiply(0.5);
		player.setVelocity(correction.x, correction.y + 0.08, correction.z);
		player.fallDistance = 0.0f;
	}

	private static void stopSwing(ClientPlayerEntity player, boolean arrived) {
		if (swingAnchor == null) {
			return;
		}
		swingAnchor = null;
		hangPoint = null;
		swingCooldown = SwingshotItem.COOLDOWN_TICKS;
		if (arrived) {
			// Absprung vom Haken, z. B. ueber eine Kante
			Vec3d velocity = player.getVelocity();
			player.setVelocity(velocity.x * 0.4, HANG_JUMP_VELOCITY, velocity.z * 0.4);
		}
		player.fallDistance = 0.0f;
		ClientPlayNetworking.send(new SwingshotPayload(false, 0, 0, 0));
	}

	private static void send(GadgetManager.Action action) {
		ClientPlayNetworking.send(new GadgetActionPayload(action.ordinal()));
	}
}
