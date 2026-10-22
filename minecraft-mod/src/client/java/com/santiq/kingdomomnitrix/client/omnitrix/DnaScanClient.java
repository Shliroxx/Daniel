package com.santiq.kingdomomnitrix.client.omnitrix;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.DnaScan;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.networking.DnaScanPayload;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * DNA-Scan auf dem Client: Taste halten → Ziel unter dem Fadenkreuz (bis {@link DnaScan#RANGE} Bloecke, Bloecke
 * verdecken) jeden Tick an den Server; der Server entscheidet und zaehlt. Anzeige: Scan-Ring um das Fadenkreuz mit
 * Fortschritt (lokal mitgezaehlt, gleiche Dauer wie auf dem Server) und dem Namen des Ziels.
 */
public final class DnaScanClient {
	public static final Identifier LAYER_ID = KingdomOmnitrix.id("dna_scan");
	private static final int RING_RADIUS = 14;
	private static final int RING_SEGMENTS = 40;

	/** angepeiltes Ziel (-1 = keins) und wie lange schon */
	private static int targetId = -1;
	private static int ticks;
	/** taugt das Ziel (nach dem synchronisierten Stand)? Sonst bleibt der Ring rot und zaehlt nicht */
	private static boolean usable;
	/** in dieser Sitzung schon gescannte Einzelwesen (der Server zaehlt jedes nur einmal) */
	private static final java.util.Set<java.util.UUID> DONE = new java.util.HashSet<>();
	/** wurde zuletzt ein Scan gesendet (dann beim Loslassen einmal -1 schicken) */
	private static boolean sending;

	private DnaScanClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DnaScanClient::tick);
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.CROSSHAIR, IdentifiedLayer.of(LAYER_ID, DnaScanClient::render)));
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		boolean active = player != null && client.currentScreen == null && held(client)
				&& OmnitrixItem.hasOmnitrix(player) && !TransformationManager.get(player).isTransformed();
		LivingEntity target = active ? findTarget(client, player) : null;
		if (target == null) {
			stop();
			return;
		}
		if (target.getId() != targetId) {
			targetId = target.getId();
			ticks = 0;
			usable = !DONE.contains(target.getUuid()) && AlienRegistry.registry(player.networkHandler.getRegistryManager())
					.map(registry -> DnaScan.match(registry, HeroDataAccess.get(player), Registries.ENTITY_TYPE.getId(target.getType()),
							player.getWorld().getRegistryKey().getValue()).alien() != null)
					.orElse(false);
		}
		if (usable) {
			ticks++;
			if (ticks == DnaScan.SCAN_TICKS) {
				DONE.add(target.getUuid());
			}
		}
		sending = true;
		ClientPlayNetworking.send(new DnaScanPayload(targetId));
	}

	/**
	 * Taste gehalten? Direkt am Fenster abgefragt: Minecraft reicht eine Taste nur an eine Belegung weiter, und C ist in
	 * Vanilla schon „Schnellzugriffsleiste speichern“ — {@code isPressed()} bliebe dann je nach Reihenfolge immer false.
	 */
	private static boolean held(MinecraftClient client) {
		InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.DNA_SCAN);
		if (key.equals(InputUtil.UNKNOWN_KEY)) {
			return false;
		}
		if (key.getCategory() == InputUtil.Type.MOUSE) {
			return ModKeyBindings.DNA_SCAN.isPressed();
		}
		return InputUtil.isKeyPressed(client.getWindow().getHandle(), key.getCode());
	}

	private static void stop() {
		if (sending && ClientPlayNetworking.canSend(DnaScanPayload.ID)) {
			ClientPlayNetworking.send(new DnaScanPayload(-1));
		}
		sending = false;
		targetId = -1;
		ticks = 0;
	}

	/** Lebewesen unter dem Fadenkreuz bis zur Scan-Reichweite; Bloecke dazwischen verdecken. */
	private static LivingEntity findTarget(MinecraftClient client, ClientPlayerEntity player) {
		Vec3d eye = player.getCameraPosVec(1.0f);
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d end = eye.add(look.multiply(DnaScan.RANGE));
		HitResult block = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getPos();
		}
		Box box = player.getBoundingBox().stretch(end.subtract(eye)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box,
				entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator() && entity != player,
				eye.squaredDistanceTo(end));
		Entity entity = hit == null ? null : hit.getEntity();
		return entity instanceof LivingEntity living ? living : null;
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (targetId < 0 || client.player == null || client.options.hudHidden) {
			return;
		}
		Entity target = client.player.getWorld().getEntityById(targetId);
		if (target == null) {
			return;
		}
		int cx = context.getScaledWindowWidth() / 2;
		int cy = context.getScaledWindowHeight() / 2;
		float progress = MathHelper.clamp((ticks + tickCounter.getTickDelta(false)) / DnaScan.SCAN_TICKS, 0.0f, 1.0f);
		int lit = Math.round(progress * RING_SEGMENTS);
		long time = client.player.getWorld().getTime();
		for (int i = 0; i < RING_SEGMENTS; i++) {
			double a = -Math.PI / 2 + i / (double) RING_SEGMENTS * Math.PI * 2;
			int x = cx + (int) Math.round(Math.cos(a) * RING_RADIUS);
			int y = cy + (int) Math.round(Math.sin(a) * RING_RADIUS);
			boolean on = i < lit;
			// noch nicht erfasste Segmente flackern leicht (Abtasten); unbrauchbares Ziel: roter Ring
			int color = !usable ? ((i + time) % 4 == 0 ? 0xC0FF4040 : 0x60802020)
					: on ? 0xFF55FF3A : ((i + time) % 6 == 0 ? 0x9055FF3A : 0x50204020);
			context.fill(x - 1, y - 1, x + 1, y + 1, color);
		}
		Text label = usable ? Text.translatable("hud.kingdomomnitrix.dna_scan", Math.round(progress * 100))
				: Text.translatable("hud.kingdomomnitrix.dna_scan_invalid");
		context.drawCenteredTextWithShadow(client.textRenderer, label, cx, cy + RING_RADIUS + 5, usable ? 0xFF7DFF5A : 0xFFFF6A5A);
		context.drawCenteredTextWithShadow(client.textRenderer, target.getDisplayName(), cx, cy + RING_RADIUS + 15, 0xFFB8E8B0);
	}
}
