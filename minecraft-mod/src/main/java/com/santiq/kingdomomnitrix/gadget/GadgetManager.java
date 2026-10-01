package com.santiq.kingdomomnitrix.gadget;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.mixin.ServerPlayNetworkHandlerAccessor;
import com.santiq.kingdomomnitrix.networking.SwingshotStatePayload;
import com.santiq.kingdomomnitrix.registry.ModComponents;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;

/**
 * Gadget-Guertel: gespeichert am Spieler, an alle Clients synchronisiert (Bewegung rechnet der eigene Client).
 * Der Server prueft jede Absicht (Ausruestung, Modus, Reichweite, Sichtlinie, Abklingzeit), setzt die Fallhoehe
 * waehrend Gleitflug und Swingshot zurueck und verteilt Effekte. Beim Tod fallen die Gadgets wie Inventar-Items.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GadgetManager {
	public static final AttachmentType<GadgetLoadout> LOADOUT = AttachmentRegistry.create(KingdomOmnitrix.id("gadgets"), builder -> builder
			.persistent(GadgetLoadout.CODEC)
			.initializer(() -> GadgetLoadout.EMPTY)
			.copyOnDeath()
			.syncWith(GadgetLoadout.PACKET_CODEC, AttachmentSyncPredicate.all()));

	public enum Action { OPEN_BELT, TOGGLE_MODE, DOUBLE_JUMP, JET_THRUST, GLIDE_START, GLIDE_STOP }

	/** Mindestabstand zwischen zwei Pack-Schueben (gegen Paket-Spam). */
	private static final int PACK_ACTION_SPACING = 4;
	/** Zusaetzliche Sicherheitsspanne, nach der der Server einen nicht geloesten Swingshot selbst beendet. */
	private static final int SWING_GRACE_TICKS = 20;

	private record Swing(Vec3d anchor, long startTick) {
	}

	private static final Map<UUID, Swing> SWINGING = new HashMap<>();
	private static final Map<UUID, Long> SWING_READY_AT = new HashMap<>();
	private static final Map<UUID, Long> LAST_PACK_ACTION = new HashMap<>();
	private static final Set<UUID> GLIDING = new HashSet<>();

	private GadgetManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GadgetManager::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player) {
				onDeath(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
	}

	// --- Zustand --------------------------------------------------------------------------------

	public static GadgetLoadout get(PlayerEntity player) {
		GadgetLoadout loadout = player.getAttached(LOADOUT);
		return loadout != null ? loadout : GadgetLoadout.EMPTY;
	}

	public static void set(ServerPlayerEntity player, GadgetLoadout loadout) {
		if (!loadout.sameAs(get(player))) {
			player.setAttached(LOADOUT, loadout);
		}
	}

	public static Optional<ItemStack> pack(PlayerEntity player) {
		ItemStack back = get(player).back();
		return back.getItem() instanceof HeliPackItem ? Optional.of(back) : Optional.empty();
	}

	public static boolean hasSwingshot(PlayerEntity player) {
		return get(player).tool().getItem() instanceof SwingshotItem;
	}

	// --- Ausruesten -----------------------------------------------------------------------------

	/** Rechtsklick mit einem Gadget: legt es in seinen Guertel-Platz und gibt das bisherige zurueck in die Hand. */
	public static void equipFromHand(ServerPlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (!(stack.getItem() instanceof Gadget gadget)) {
			return;
		}
		GadgetSlot slot = gadget.gadgetSlot();
		GadgetLoadout loadout = get(player);
		ItemStack previous = loadout.get(slot).copy();
		set(player, loadout.with(slot, stack.copyWithCount(1)));
		stack.decrement(1);
		if (stack.isEmpty()) {
			player.setStackInHand(hand, previous);
		} else if (!previous.isEmpty()) {
			player.getInventory().offerOrDrop(previous);
		}
		if (slot == GadgetSlot.TOOL) {
			stopSwing(player, false);
		}
		sound(player, SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), 0.8f, 1.2f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.gadget_equipped", get(player).get(slot).getName())
				.formatted(Formatting.AQUA), true);
	}

	public static void openBelt(ServerPlayerEntity player) {
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, user) -> new GadgetScreenHandler(syncId, inventory, new GadgetInventory(player)),
				Text.translatable("container.kingdomomnitrix.gadget_belt")));
	}

	// --- Pack -----------------------------------------------------------------------------------

	public static void handle(ServerPlayerEntity player, Action action) {
		switch (action) {
			case OPEN_BELT -> openBelt(player);
			case TOGGLE_MODE -> toggleMode(player);
			case DOUBLE_JUMP, JET_THRUST -> packBoost(player, action == Action.JET_THRUST);
			case GLIDE_START -> {
				if (pack(player).isPresent() && !player.isOnGround()) {
					GLIDING.add(player.getUuid());
					player.fallDistance = 0.0f;
				}
			}
			case GLIDE_STOP -> GLIDING.remove(player.getUuid());
		}
	}

	private static void toggleMode(ServerPlayerEntity player) {
		Optional<ItemStack> pack = pack(player);
		if (pack.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_pack").formatted(Formatting.RED), true);
			return;
		}
		ItemStack changed = pack.get().copy();
		boolean jet = !HeliPackItem.isJet(changed);
		changed.set(ModComponents.JET_MODE, jet);
		set(player, get(player).with(GadgetSlot.BACK, changed));
		GLIDING.remove(player.getUuid());
		sound(player, SoundEvents.BLOCK_PISTON_EXTEND, 0.6f, jet ? 1.5f : 1.1f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.pack_mode", changed.getName()).formatted(Formatting.AQUA), true);
	}

	private static void packBoost(ServerPlayerEntity player, boolean jet) {
		Optional<ItemStack> pack = pack(player);
		if (pack.isEmpty() || HeliPackItem.isJet(pack.get()) != jet || player.isOnGround()) {
			return;
		}
		long now = player.getServerWorld().getTime();
		Long last = LAST_PACK_ACTION.get(player.getUuid());
		if (last != null && now - last < PACK_ACTION_SPACING) {
			return;
		}
		LAST_PACK_ACTION.put(player.getUuid(), now);
		player.fallDistance = 0.0f;
		ServerWorld world = player.getServerWorld();
		if (jet) {
			sound(player, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.3f);
			world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.8, player.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
			world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.6, player.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
		} else {
			sound(player, SoundEvents.ENTITY_BREEZE_JUMP, 0.7f, 1.4f);
			world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
		}
	}

	// --- Swingshot ------------------------------------------------------------------------------

	public static void startSwing(ServerPlayerEntity player, Vec3d anchor) {
		if (!hasSwingshot(player) || player.isSpectator()) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Long readyAt = SWING_READY_AT.get(player.getUuid());
		if (readyAt != null && now < readyAt) {
			return;
		}
		Vec3d eye = player.getEyePos();
		double distance = eye.distanceTo(anchor);
		if (distance > SwingshotItem.RANGE + 2.0 || distance < 0.5) {
			return;
		}
		// Sichtlinie: der Strahl zum Anker muss genau dort auf einen festen Block treffen.
		Vec3d beyond = anchor.add(anchor.subtract(eye).normalize().multiply(0.5));
		BlockHitResult hit = world.raycast(new RaycastContext(eye, beyond, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK || hit.getPos().squaredDistanceTo(anchor) > 2.25) {
			return;
		}
		SWINGING.put(player.getUuid(), new Swing(hit.getPos(), now));
		player.fallDistance = 0.0f;
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.9f, 0.7f);
		world.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1.0f, 1.2f);
		broadcast(player, new SwingshotStatePayload(player.getId(), true, hit.getPos().x, hit.getPos().y, hit.getPos().z));
	}

	public static void stopSwing(ServerPlayerEntity player, boolean playSound) {
		if (SWINGING.remove(player.getUuid()) == null) {
			return;
		}
		player.fallDistance = 0.0f;
		SWING_READY_AT.put(player.getUuid(), player.getServerWorld().getTime() + SwingshotItem.COOLDOWN_TICKS);
		if (playSound) {
			sound(player, SoundEvents.BLOCK_CHAIN_BREAK, 0.6f, 1.4f);
		}
		broadcast(player, new SwingshotStatePayload(player.getId(), false, 0, 0, 0));
	}

	private static void broadcast(ServerPlayerEntity player, SwingshotStatePayload payload) {
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
			if (watcher != player) {
				ServerPlayNetworking.send(watcher, payload);
			}
		}
	}

	// --- Ablauf ---------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (SWINGING.isEmpty() && GLIDING.isEmpty()) {
			return;
		}
		for (UUID id : Set.copyOf(SWINGING.keySet())) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null) {
				forget(id);
				continue;
			}
			Swing swing = SWINGING.get(id);
			long age = player.getServerWorld().getTime() - swing.startTick();
			if (!player.isAlive() || !hasSwingshot(player) || age > SwingshotItem.MAX_PULL_TICKS + SwingshotItem.MAX_HANG_TICKS + SWING_GRACE_TICKS) {
				stopSwing(player, true);
			} else {
				player.fallDistance = 0.0f;
				allowFloating(player);
			}
		}
		for (UUID id : Set.copyOf(GLIDING)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null || !player.isAlive() || pack(player).isEmpty() || player.isOnGround() || player.isTouchingWater()) {
				GLIDING.remove(id);
			} else {
				player.fallDistance = 0.0f;
			}
		}
	}

	/** Haengen und Hochziehen sind gewollt: Schwebe-Pruefung des Servers (Anti-Fly-Kick) zuruecksetzen. */
	private static void allowFloating(ServerPlayerEntity player) {
		if (player.networkHandler != null) {
			((ServerPlayNetworkHandlerAccessor) player.networkHandler).kingdomomnitrix$setFloatingTicks(0);
		}
	}

	private static void onDeath(ServerPlayerEntity player) {
		stopSwing(player, false);
		forget(player.getUuid());
		if (player.getServerWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
			return;
		}
		GadgetLoadout loadout = get(player);
		if (loadout.isEmpty()) {
			return;
		}
		for (GadgetSlot slot : GadgetSlot.values()) {
			ItemStack stack = loadout.get(slot);
			if (!stack.isEmpty()) {
				player.dropItem(stack.copy(), true, false);
			}
		}
		player.setAttached(LOADOUT, GadgetLoadout.EMPTY);
	}

	private static void forget(UUID id) {
		SWINGING.remove(id);
		GLIDING.remove(id);
		LAST_PACK_ACTION.remove(id);
		SWING_READY_AT.remove(id);
	}

	private static void sound(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
